package io.github.elkboilerplate.logger.internal;

/**
 * Kütüphanenin kendi teşhis mesajları (stderr). Kütüphane kendini loglamak için ELK'yı kullanmaz;
 * aksi halde bağlantı hatası sonsuz döngüye girerdi. Hata mesajları tekrarı önlemek için oran sınırlıdır.
 */
public final class InternalLog {

    private static volatile boolean debugEnabled = Boolean.getBoolean("elk.debug");
    private static volatile long lastErrorAt;
    private static volatile String lastError;
    private static final long ERROR_REPEAT_INTERVAL_MS = 60_000;

    private InternalLog() {
    }

    public static void setDebug(boolean debug) {
        debugEnabled = debug;
    }

    public static boolean isDebug() {
        return debugEnabled;
    }

    public static void debug(String msg) {
        if (debugEnabled) {
            System.err.println("[elk-logger] DEBUG " + msg);
        }
    }

    public static void info(String msg) {
        System.err.println("[elk-logger] " + msg);
    }

    public static void warn(String msg) {
        System.err.println("[elk-logger] WARN " + msg);
    }

    /** Aynı mesaj 60 sn içinde tekrar gelirse bastırılır (debug modunda her zaman basılır). */
    public static void errorRateLimited(String msg) {
        long now = System.currentTimeMillis();
        if (debugEnabled || !msg.equals(lastError) || now - lastErrorAt > ERROR_REPEAT_INTERVAL_MS) {
            lastError = msg;
            lastErrorAt = now;
            System.err.println("[elk-logger] ERROR " + msg);
        }
    }
}
