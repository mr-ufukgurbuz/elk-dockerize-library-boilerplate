package io.github.elkboilerplate.logger;

/**
 * Kütüphanenin çalışma istatistiklerinin anlık görüntüsü. {@link ElkLoggerFactory#getStats()} ile alınır.
 * Uygulamanın kendi health-check'inde "log kaybı var mı" kontrolü için kullanılabilir.
 */
public final class ElkStats {
    private final long enqueued;
    private final long sent;
    private final long dropped;
    private final long failedAttempts;
    private final long writtenToFallback;
    private final int queueSize;
    private final boolean connected;

    public ElkStats(long enqueued, long sent, long dropped, long failedAttempts,
                    long writtenToFallback, int queueSize, boolean connected) {
        this.enqueued = enqueued;
        this.sent = sent;
        this.dropped = dropped;
        this.failedAttempts = failedAttempts;
        this.writtenToFallback = writtenToFallback;
        this.queueSize = queueSize;
        this.connected = connected;
    }

    /** Kuyruğa alınan toplam olay. */
    public long getEnqueued() { return enqueued; }
    /** Logstash'e başarıyla gönderilen toplam olay. */
    public long getSent() { return sent; }
    /** Kuyruk dolu olduğu veya yeniden deneme limiti aşıldığı için kaybedilen olay (fallback dosyasına yazılanlar hariç). */
    public long getDropped() { return dropped; }
    /** Başarısız gönderim denemesi sayısı (batch bazında). */
    public long getFailedAttempts() { return failedAttempts; }
    /** Gönderilemeyip yerel fallback dosyasına yazılan olay. */
    public long getWrittenToFallback() { return writtenToFallback; }
    /** Şu an kuyrukta bekleyen olay. */
    public int getQueueSize() { return queueSize; }
    /** Son gönderim denemesi başarılı mıydı? */
    public boolean isConnected() { return connected; }

    @Override
    public String toString() {
        return "ElkStats{enqueued=" + enqueued + ", sent=" + sent + ", dropped=" + dropped
                + ", failedAttempts=" + failedAttempts + ", writtenToFallback=" + writtenToFallback
                + ", queueSize=" + queueSize + ", connected=" + connected + '}';
    }
}
