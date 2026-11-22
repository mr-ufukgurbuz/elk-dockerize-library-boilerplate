package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.Level;
import io.github.elkboilerplate.logger.LogEventBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class DefaultLogEventBuilder implements LogEventBuilder {

    private final Level level;
    private final String loggerName;
    private final Supplier<LoggerContext> contextSupplier;
    private Map<String, Object> fields;
    private List<String> tags;
    private Throwable cause;

    DefaultLogEventBuilder(Level level, String loggerName, Supplier<LoggerContext> contextSupplier) {
        this.level = level;
        this.loggerName = loggerName;
        this.contextSupplier = contextSupplier;
    }

    @Override
    public LogEventBuilder field(String key, Object value) {
        if (key != null) {
            if (fields == null) {
                fields = new LinkedHashMap<>();
            }
            fields.put(key, value);
        }
        return this;
    }

    @Override
    public LogEventBuilder fields(Map<String, ?> map) {
        if (map != null) {
            map.forEach(this::field);
        }
        return this;
    }

    @Override
    public LogEventBuilder tag(String tag) {
        if (tag != null) {
            if (tags == null) {
                tags = new ArrayList<>(2);
            }
            tags.add(tag);
        }
        return this;
    }

    @Override
    public LogEventBuilder cause(Throwable throwable) {
        this.cause = throwable;
        return this;
    }

    @Override
    public void log(String message) {
        contextSupplier.get().dispatch(level, loggerName, message, null, cause, fields, tags);
    }

    @Override
    public void log(String format, Object... args) {
        contextSupplier.get().dispatch(level, loggerName, format, args, cause, fields, tags);
    }
}
