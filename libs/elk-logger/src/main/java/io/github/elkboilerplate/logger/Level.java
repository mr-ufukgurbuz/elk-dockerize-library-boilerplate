package io.github.elkboilerplate.logger;

import java.util.Locale;

/**
 * Log seviyeleri. {@link #value()} Elasticsearch'te {@code level_value} alanına yazılır;
 * böylece "WARN ve üstü" gibi aralık sorguları yapılabilir ({@code level_value >= 30}).
 */
public enum Level {
    TRACE(0),
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40),
    /** Hiçbir şey loglanmaz. Sadece konfigürasyonda kullanılır. */
    OFF(Integer.MAX_VALUE);

    private final int value;

    Level(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** Bu seviyedeki bir olay, {@code threshold} eşiğinde loglanır mı? */
    public boolean isAtLeast(Level threshold) {
        return this != OFF && threshold != OFF && this.value >= threshold.value;
    }

    /**
     * Esnek parse: büyük/küçük harf duyarsız, "WARNING" -> WARN, "FATAL"/"SEVERE" -> ERROR.
     *
     * @param text     parse edilecek metin
     * @param fallback metin geçersizse dönülecek değer
     */
    public static Level parse(String text, Level fallback) {
        if (text == null) {
            return fallback;
        }
        String t = text.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "TRACE":
            case "ALL":
            case "FINEST":
                return TRACE;
            case "DEBUG":
            case "FINE":
                return DEBUG;
            case "INFO":
                return INFO;
            case "WARN":
            case "WARNING":
                return WARN;
            case "ERROR":
            case "FATAL":
            case "SEVERE":
                return ERROR;
            case "OFF":
            case "NONE":
                return OFF;
            default:
                return fallback;
        }
    }
}
