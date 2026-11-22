package io.github.elkboilerplate.logger;

import java.io.Closeable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Mapped Diagnostic Context: thread'e bağlı anahtar/değer çiftleri. Burada konulan her değer
 * aynı thread'de yazılan tüm loglara {@code mdc.<anahtar>} olarak eklenir (ör. requestId, userId).
 * SLF4J {@code MDC} sınıfının karşılığıdır.
 *
 * <pre>{@code
 * try (ElkMDC.MDCCloseable c = ElkMDC.putCloseable("requestId", id)) {
 *     log.info("İstek işleniyor");
 * }
 * executor.submit(ElkMDC.wrap(() -> log.info("MDC başka thread'e taşındı")));
 * }</pre>
 */
public final class ElkMDC {

    private static final ThreadLocal<Map<String, String>> CONTEXT = new ThreadLocal<>();

    private ElkMDC() {
    }

    public static void put(String key, String value) {
        if (key == null) {
            return;
        }
        Map<String, String> map = CONTEXT.get();
        if (map == null) {
            map = new HashMap<>();
            CONTEXT.set(map);
        }
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    public static String get(String key) {
        Map<String, String> map = CONTEXT.get();
        return map == null || key == null ? null : map.get(key);
    }

    public static void remove(String key) {
        Map<String, String> map = CONTEXT.get();
        if (map != null && key != null) {
            map.remove(key);
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /** Mevcut thread'in MDC kopyası (asla null değildir). */
    public static Map<String, String> getCopyOfContextMap() {
        Map<String, String> map = CONTEXT.get();
        return map == null || map.isEmpty() ? Collections.emptyMap() : new HashMap<>(map);
    }

    /** MDC'yi verilen içerikle değiştirir; null veya boşsa temizler. */
    public static void setContextMap(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(new HashMap<>(contextMap));
        }
    }

    /** try-with-resources ile kullanılabilen, kapanınca anahtarı silen put. */
    public static MDCCloseable putCloseable(String key, String value) {
        put(key, value);
        return new MDCCloseable(key);
    }

    /** Çağıran thread'in MDC'sini, görevin çalışacağı thread'e taşıyan sarmalayıcı. */
    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = getCopyOfContextMap();
            setContextMap(captured);
            try {
                task.run();
            } finally {
                setContextMap(previous);
            }
        };
    }

    /** {@link #wrap(Runnable)} için Callable karşılığı. */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> captured = getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = getCopyOfContextMap();
            setContextMap(captured);
            try {
                return task.call();
            } finally {
                setContextMap(previous);
            }
        };
    }

    /** {@link #putCloseable(String, String)} dönüş tipi. */
    public static final class MDCCloseable implements Closeable {
        private final String key;

        private MDCCloseable(String key) {
            this.key = key;
        }

        @Override
        public void close() {
            remove(key);
        }
    }
}
