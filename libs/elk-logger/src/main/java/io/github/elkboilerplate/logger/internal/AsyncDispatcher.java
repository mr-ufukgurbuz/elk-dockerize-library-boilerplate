package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.ElkStats;
import io.github.elkboilerplate.logger.config.ElkConfig;
import io.github.elkboilerplate.logger.config.OverflowPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Uygulama thread'lerini ağ gecikmesinden yalıtan asenkron gönderici.
 *
 * <pre>
 *  uygulama thread'leri --offer()--> [ArrayBlockingQueue(capacity)] --drain(batchSize)--> worker thread --> Transport
 *                                                                                          |  hata: exponential backoff + retry
 *                                                                                          |  limit aşılırsa / kapanışta: FallbackFileSink
 * </pre>
 *
 * Değişmezler (invariants):
 * <ul>
 *   <li>{@link #enqueue} asla exception fırlatmaz ve (BLOCK politikası dışında) asla bloklamaz.</li>
 *   <li>Transport yalnızca tek worker thread'den çağrılır.</li>
 *   <li>Gönderilemeyen batch kaybolmaz: ya yeniden denenir, ya fallback dosyasına yazılır, ya da dropped sayacına eklenir.</li>
 * </ul>
 */
public final class AsyncDispatcher {

    private final ElkConfig config;
    private final Transport transport;
    private final FallbackFileSink fallback;
    private final ArrayBlockingQueue<String> queue;
    private final Thread worker;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final Object flushLock = new Object();

    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failedAttempts = new AtomicLong();
    private final AtomicLong toFallback = new AtomicLong();
    private volatile boolean connected;
    private volatile boolean running = true;
    private volatile long flushRequested;
    private volatile long flushCompleted;

    public AsyncDispatcher(ElkConfig config, Transport transport, FallbackFileSink fallback) {
        this.config = config;
        this.transport = transport;
        this.fallback = fallback;
        this.queue = new ArrayBlockingQueue<>(config.getQueueCapacity());
        this.worker = new Thread(this::runLoop, "elk-logger-dispatcher");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Olayı kuyruğa ekler. Kuyruk doluysa {@link OverflowPolicy}'ye göre davranır. */
    public void enqueue(String json) {
        if (!running) {
            dropOrFallback(java.util.Collections.singletonList(json));
            return;
        }
        if (queue.offer(json)) {
            enqueued.incrementAndGet();
            return;
        }
        OverflowPolicy policy = config.getOverflowPolicy();
        if (policy == OverflowPolicy.DROP_OLDEST) {
            String old = queue.poll();
            if (old != null) {
                dropOrFallback(java.util.Collections.singletonList(old));
            }
            if (queue.offer(json)) {
                enqueued.incrementAndGet();
                return;
            }
        } else if (policy == OverflowPolicy.BLOCK) {
            try {
                if (queue.offer(json, config.getOverflowBlockTimeoutMs(), TimeUnit.MILLISECONDS)) {
                    enqueued.incrementAndGet();
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        dropOrFallback(java.util.Collections.singletonList(json));
        InternalLog.errorRateLimited("kuyruk dolu (" + config.getQueueCapacity() + "), olaylar atılıyor. Logstash erişilebilir mi? "
                + (transport == null ? "" : transport.describe()));
    }

    private void runLoop() {
        List<String> batch = new ArrayList<>(config.getBatchSize());
        long backoff = config.getRetryInitialBackoffMs();
        int attempts = 0;
        try {
            while (running || !queue.isEmpty() || !batch.isEmpty()) {
                if (batch.isEmpty()) {
                    long flushTarget = flushRequested;
                    String first = queue.poll(config.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
                    if (first == null) {
                        markFlushed(flushTarget);
                        if (!running) {
                            break;
                        }
                        continue;
                    }
                    batch.add(first);
                    queue.drainTo(batch, config.getBatchSize() - 1);
                }
                try {
                    transport.send(batch);
                    sent.addAndGet(batch.size());
                    if (!connected) {
                        InternalLog.debug("gönderim başarılı: " + transport.describe());
                    }
                    connected = true;
                    batch.clear();
                    backoff = config.getRetryInitialBackoffMs();
                    attempts = 0;
                    if (queue.isEmpty()) {
                        markFlushed(flushRequested);
                    }
                } catch (Exception e) {
                    connected = false;
                    failedAttempts.incrementAndGet();
                    attempts++;
                    InternalLog.errorRateLimited("Logstash'e gönderilemedi (" + transport.describe() + "): " + e
                            + " — kuyrukta " + queue.size() + " olay bekliyor, yeniden denenecek.");
                    boolean giveUp = !running || (config.getRetryMaxAttempts() > 0 && attempts >= config.getRetryMaxAttempts());
                    if (giveUp) {
                        dropOrFallback(new ArrayList<>(batch));
                        batch.clear();
                        attempts = 0;
                        if (!running) {
                            // kapanışta kalan her şeyi tek seferde dosyaya/drop'a aktar
                            List<String> rest = new ArrayList<>();
                            queue.drainTo(rest);
                            dropOrFallback(rest);
                            break;
                        }
                    } else {
                        sleepQuietly(backoff);
                        backoff = Math.min(config.getRetryMaxBackoffMs(), backoff * 2);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            InternalLog.errorRateLimited("dispatcher beklenmeyen hata ile durdu: " + t);
        } finally {
            List<String> rest = new ArrayList<>(batch);
            queue.drainTo(rest);
            dropOrFallback(rest);
            transport.close();
            markFlushed(Long.MAX_VALUE);
            stopped.countDown();
        }
    }

    private void markFlushed(long target) {
        synchronized (flushLock) {
            if (target > flushCompleted) {
                flushCompleted = target;
            }
            flushLock.notifyAll();
        }
    }

    /**
     * Kuyruktaki olayların gönderilmesini en fazla {@code timeoutMs} kadar bekler.
     *
     * @return süre içinde kuyruk boşaldıysa true
     */
    public boolean flush(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long target;
        synchronized (flushLock) {
            target = ++flushRequested;
        }
        synchronized (flushLock) {
            while (flushCompleted < target) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                try {
                    flushLock.wait(Math.min(left, 50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /** Kalan olayları göndermeye çalışır, süre dolarsa kalanları fallback dosyasına yazar ve durur. */
    public void shutdown(long timeoutMs) {
        if (!running) {
            return;
        }
        flush(timeoutMs);
        running = false;
        worker.interrupt();
        try {
            stopped.await(Math.max(500, timeoutMs / 2), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dropOrFallback(List<String> events) {
        if (events.isEmpty()) {
            return;
        }
        if (fallback != null && fallback.write(events)) {
            toFallback.addAndGet(events.size());
        } else {
            dropped.addAndGet(events.size());
        }
    }

    private static void sleepQuietly(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    public ElkStats stats() {
        return new ElkStats(enqueued.get(), sent.get(), dropped.get(), failedAttempts.get(), toFallback.get(), queue.size(), connected);
    }
}
