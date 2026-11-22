package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.ElkMDC;
import io.github.elkboilerplate.logger.ElkStats;
import io.github.elkboilerplate.logger.Level;
import io.github.elkboilerplate.logger.config.ElkConfig;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bir konfigürasyonun çalışma zamanı karşılığı: seviye çözücü, serializer, konsol ve dispatcher.
 * {@code ElkLoggerFactory.configure(...)} her çağrıldığında yeni bir context oluşturulur, eskisi kapatılır.
 */
public final class LoggerContext {

    private static final StackWalker WALKER = StackWalker.getInstance();

    private final ElkConfig config;
    private volatile LevelResolver levels;
    private final EventSerializer serializer;
    private final ConsoleSink console;
    private final AsyncDispatcher dispatcher;
    private final FallbackFileSink fallback;

    public LoggerContext(ElkConfig config) {
        this.config = config;
        InternalLog.setDebug(config.isDebug() || Boolean.getBoolean("elk.debug"));
        this.levels = new LevelResolver(config.getRootLevel(), config.getLoggerLevels());
        this.serializer = new EventSerializer(config);
        this.console = config.isConsoleEnabled() ? new ConsoleSink(config.getConsoleLevel(), config.isConsoleStderr()) : null;
        this.fallback = config.isFallbackEnabled() ? new FallbackFileSink(config.getFallbackPath(), config.getFallbackMaxBytes()) : null;
        Transport transport = config.isEnabled() ? createTransportSafely(config) : null;
        this.dispatcher = transport != null ? new AsyncDispatcher(config, transport, fallback) : null;
        InternalLog.debug("başlatıldı: " + config + " hostname=" + serializer.getHostname());
    }

    private static Transport createTransportSafely(ElkConfig config) {
        try {
            return Transports.create(config);
        } catch (RuntimeException e) {
            InternalLog.warn("transport oluşturulamadı, yalnızca konsol kullanılacak: " + e.getMessage());
            return null;
        }
    }

    public ElkConfig getConfig() {
        return config;
    }

    public Level effectiveLevel(String loggerName) {
        return levels.resolve(loggerName);
    }

    public void setLevel(String prefix, Level level) {
        this.levels = levels.with(prefix, level);
    }

    /** Ana loglama yolu. Asla exception fırlatmaz. */
    public void dispatch(Level level, String loggerName, String template, Object[] args, Throwable explicitThrowable,
                         Map<String, Object> fields, List<String> tags) {
        try {
            FormattedMessage fm = MessageFormatter.format(template, args);
            Throwable t = explicitThrowable != null ? explicitThrowable : fm.getThrowable();
            Map<String, String> mdc = config.isIncludeMdc() ? ElkMDC.getCopyOfContextMap() : Collections.emptyMap();
            StackTraceElement caller = config.isIncludeCaller() ? findCaller() : null;
            boolean hasArgs = args != null && args.length > 0 && !(args.length == 1 && args[0] instanceof Throwable);
            LogEvent event = new LogEvent(Instant.now(), level, loggerName, Thread.currentThread().getName(),
                    fm.getMessage(), hasArgs ? template : null, t, mdc, fields, tags, caller);
            if (console != null) {
                console.write(event);
            }
            if (dispatcher != null) {
                dispatcher.enqueue(serializer.toJson(event));
            } else if (fallback != null && config.isEnabled() && config.getTransport() != io.github.elkboilerplate.logger.config.TransportType.NONE) {
                fallback.write(Collections.singletonList(serializer.toJson(event)));
            }
        } catch (Throwable e) {
            InternalLog.errorRateLimited("log olayı işlenemedi: " + e);
        }
    }

    private static StackTraceElement findCaller() {
        Optional<StackWalker.StackFrame> frame = WALKER.walk(s -> s
                .filter(f -> !isInfrastructure(f.getClassName()))
                .findFirst());
        return frame.map(StackWalker.StackFrame::toStackTraceElement).orElse(null);
    }

    static boolean isInfrastructure(String className) {
        return className.startsWith("io.github.elkboilerplate.logger.internal.")
                || className.equals("io.github.elkboilerplate.logger.ElkLogger")
                || className.startsWith("io.github.elkboilerplate.logger.slf4j.")
                || className.startsWith("org.slf4j.");
    }

    public boolean flush(long timeoutMs) {
        return dispatcher == null || dispatcher.flush(timeoutMs);
    }

    public void shutdown() {
        if (dispatcher != null) {
            dispatcher.shutdown(config.getShutdownTimeoutMs());
        }
    }

    public ElkStats stats() {
        return dispatcher != null ? dispatcher.stats() : new ElkStats(0, 0, 0, 0, 0, 0, false);
    }
}
