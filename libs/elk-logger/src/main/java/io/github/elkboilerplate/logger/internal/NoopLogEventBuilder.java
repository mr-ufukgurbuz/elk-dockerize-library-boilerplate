package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.LogEventBuilder;

import java.util.Map;

/** Seviye kapalıyken dönen, hiçbir şey yapmayan builder (allocation yok). */
final class NoopLogEventBuilder implements LogEventBuilder {
    static final NoopLogEventBuilder INSTANCE = new NoopLogEventBuilder();

    private NoopLogEventBuilder() {
    }

    @Override public LogEventBuilder field(String key, Object value) { return this; }
    @Override public LogEventBuilder fields(Map<String, ?> fields) { return this; }
    @Override public LogEventBuilder tag(String tag) { return this; }
    @Override public LogEventBuilder cause(Throwable throwable) { return this; }
    @Override public void log(String message) { }
    @Override public void log(String format, Object... args) { }
}
