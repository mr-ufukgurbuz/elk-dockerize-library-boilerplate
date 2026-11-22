package io.github.elkboilerplate.logger;

/**
 * Uygulamaların kullandığı loglama arayüzü. SLF4J {@code Logger} arayüzüne bilinçli olarak
 * benzer tasarlanmıştır: {@code {}} yer tutucuları, sondaki {@link Throwable} argümanının
 * otomatik olarak exception kabul edilmesi ve {@code isXxxEnabled()} kontrolleri aynıdır.
 *
 * <pre>{@code
 * private static final ElkLogger log = ElkLoggerFactory.getLogger(OrderService.class);
 *
 * log.info("Sipariş oluşturuldu: {}", orderId);
 * log.error("Ödeme alınamadı, sipariş={}", orderId, exception);
 * log.atWarn().field("orderId", orderId).field("amount", 125.5).log("Stok azaldı");
 * }</pre>
 *
 * Tüm metotlar thread-safe'tir ve hiçbir koşulda uygulamaya exception fırlatmaz.
 */
public interface ElkLogger {

    /** Logger adı (genellikle sınıfın tam adı). */
    String getName();

    /** Verilen seviye bu logger için aktif mi? */
    boolean isEnabled(Level level);

    /**
     * Genel loglama metodu. {@code args} içindeki son eleman {@link Throwable} ise ve
     * mesajda ona karşılık gelen {@code {}} yoksa exception olarak işlenir.
     */
    void log(Level level, String message, Object... args);

    /** Yapısal (structured) alan eklemeye izin veren akıcı (fluent) API. */
    LogEventBuilder atLevel(Level level);

    // ------------------------------------------------------------------ TRACE
    default boolean isTraceEnabled() { return isEnabled(Level.TRACE); }
    default void trace(String msg) { log(Level.TRACE, msg); }
    default void trace(String format, Object arg) { log(Level.TRACE, format, arg); }
    default void trace(String format, Object arg1, Object arg2) { log(Level.TRACE, format, arg1, arg2); }
    default void trace(String format, Object... args) { log(Level.TRACE, format, args); }
    default void trace(String msg, Throwable t) { log(Level.TRACE, msg, t); }
    default LogEventBuilder atTrace() { return atLevel(Level.TRACE); }

    // ------------------------------------------------------------------ DEBUG
    default boolean isDebugEnabled() { return isEnabled(Level.DEBUG); }
    default void debug(String msg) { log(Level.DEBUG, msg); }
    default void debug(String format, Object arg) { log(Level.DEBUG, format, arg); }
    default void debug(String format, Object arg1, Object arg2) { log(Level.DEBUG, format, arg1, arg2); }
    default void debug(String format, Object... args) { log(Level.DEBUG, format, args); }
    default void debug(String msg, Throwable t) { log(Level.DEBUG, msg, t); }
    default LogEventBuilder atDebug() { return atLevel(Level.DEBUG); }

    // ------------------------------------------------------------------ INFO
    default boolean isInfoEnabled() { return isEnabled(Level.INFO); }
    default void info(String msg) { log(Level.INFO, msg); }
    default void info(String format, Object arg) { log(Level.INFO, format, arg); }
    default void info(String format, Object arg1, Object arg2) { log(Level.INFO, format, arg1, arg2); }
    default void info(String format, Object... args) { log(Level.INFO, format, args); }
    default void info(String msg, Throwable t) { log(Level.INFO, msg, t); }
    default LogEventBuilder atInfo() { return atLevel(Level.INFO); }

    // ------------------------------------------------------------------ WARN
    default boolean isWarnEnabled() { return isEnabled(Level.WARN); }
    default void warn(String msg) { log(Level.WARN, msg); }
    default void warn(String format, Object arg) { log(Level.WARN, format, arg); }
    default void warn(String format, Object arg1, Object arg2) { log(Level.WARN, format, arg1, arg2); }
    default void warn(String format, Object... args) { log(Level.WARN, format, args); }
    default void warn(String msg, Throwable t) { log(Level.WARN, msg, t); }
    default LogEventBuilder atWarn() { return atLevel(Level.WARN); }

    // ------------------------------------------------------------------ ERROR
    default boolean isErrorEnabled() { return isEnabled(Level.ERROR); }
    default void error(String msg) { log(Level.ERROR, msg); }
    default void error(String format, Object arg) { log(Level.ERROR, format, arg); }
    default void error(String format, Object arg1, Object arg2) { log(Level.ERROR, format, arg1, arg2); }
    default void error(String format, Object... args) { log(Level.ERROR, format, args); }
    default void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }
    default LogEventBuilder atError() { return atLevel(Level.ERROR); }
}
