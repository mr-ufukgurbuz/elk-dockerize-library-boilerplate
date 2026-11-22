package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.Level;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tek bir log olayının, loglama anında (çağıran thread'de) yakalanmış değişmez anlık görüntüsü.
 * Mesaj biçimlendirmesi ve MDC kopyası çağıran thread'de yapılır; böylece asenkron gönderim
 * sırasında argüman nesnelerindeki değişiklikler loga yansımaz.
 */
public final class LogEvent {
    final Instant timestamp;
    final Level level;
    final String loggerName;
    final String threadName;
    final String message;
    final String messageTemplate;
    final Throwable throwable;
    final Map<String, String> mdc;
    final Map<String, Object> fields;
    final List<String> tags;
    final StackTraceElement caller;

    public LogEvent(Instant timestamp, Level level, String loggerName, String threadName, String message,
                    String messageTemplate, Throwable throwable, Map<String, String> mdc,
                    Map<String, Object> fields, List<String> tags, StackTraceElement caller) {
        this.timestamp = timestamp;
        this.level = level;
        this.loggerName = loggerName;
        this.threadName = threadName;
        this.message = message;
        this.messageTemplate = messageTemplate;
        this.throwable = throwable;
        this.mdc = mdc;
        this.fields = fields;
        this.tags = tags;
        this.caller = caller;
    }

    public Instant getTimestamp() { return timestamp; }
    public Level getLevel() { return level; }
    public String getLoggerName() { return loggerName; }
    public String getThreadName() { return threadName; }
    public String getMessage() { return message; }
    public Throwable getThrowable() { return throwable; }
}
