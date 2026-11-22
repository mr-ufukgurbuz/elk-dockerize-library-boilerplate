package io.github.elkboilerplate.logger.slf4j;

import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.Level;
import io.github.elkboilerplate.logger.LogEventBuilder;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.spi.LoggingEventAware;

import java.util.Iterator;
import java.util.List;

/**
 * SLF4J {@link org.slf4j.Logger} -> {@link ElkLogger} köprüsü.
 * <ul>
 *   <li>Marker isimleri {@code tags} alanına yazılır.</li>
 *   <li>SLF4J 2 fluent API key/value çiftleri ({@code log.atInfo().addKeyValue("orderId", 1).log("...")})
 *       {@code fields.<anahtar>} alanına yazılır.</li>
 * </ul>
 */
final class ElkSlf4jLogger extends AbstractLogger implements LoggingEventAware {

    private static final long serialVersionUID = 1L;

    private final transient ElkLogger delegate;

    ElkSlf4jLogger(String name, ElkLogger delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    static Level map(org.slf4j.event.Level level) {
        switch (level) {
            case TRACE: return Level.TRACE;
            case DEBUG: return Level.DEBUG;
            case INFO: return Level.INFO;
            case WARN: return Level.WARN;
            case ERROR:
            default: return Level.ERROR;
        }
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(org.slf4j.event.Level level, Marker marker, String messagePattern,
                                               Object[] arguments, Throwable throwable) {
        LogEventBuilder b = delegate.atLevel(map(level));
        addMarker(b, marker);
        if (throwable != null) {
            b.cause(throwable);
        }
        b.log(messagePattern, arguments == null ? new Object[0] : arguments);
    }

    /** SLF4J 2 fluent API buraya gelir (key/value çiftleri ile). */
    @Override
    public void log(LoggingEvent event) {
        Level level = map(event.getLevel());
        if (!delegate.isEnabled(level)) {
            return;
        }
        LogEventBuilder b = delegate.atLevel(level);
        List<Marker> markers = event.getMarkers();
        if (markers != null) {
            for (Marker m : markers) {
                addMarker(b, m);
            }
        }
        List<KeyValuePair> kvs = event.getKeyValuePairs();
        if (kvs != null) {
            for (KeyValuePair kv : kvs) {
                b.field(kv.key, kv.value);
            }
        }
        if (event.getThrowable() != null) {
            b.cause(event.getThrowable());
        }
        Object[] args = event.getArgumentArray();
        b.log(event.getMessage(), args == null ? new Object[0] : args);
    }

    private static void addMarker(LogEventBuilder b, Marker marker) {
        if (marker == null) {
            return;
        }
        b.tag(marker.getName());
        Iterator<Marker> it = marker.iterator();
        while (it.hasNext()) {
            b.tag(it.next().getName());
        }
    }

    @Override public boolean isTraceEnabled() { return delegate.isTraceEnabled(); }
    @Override public boolean isTraceEnabled(Marker marker) { return delegate.isTraceEnabled(); }
    @Override public boolean isDebugEnabled() { return delegate.isDebugEnabled(); }
    @Override public boolean isDebugEnabled(Marker marker) { return delegate.isDebugEnabled(); }
    @Override public boolean isInfoEnabled() { return delegate.isInfoEnabled(); }
    @Override public boolean isInfoEnabled(Marker marker) { return delegate.isInfoEnabled(); }
    @Override public boolean isWarnEnabled() { return delegate.isWarnEnabled(); }
    @Override public boolean isWarnEnabled(Marker marker) { return delegate.isWarnEnabled(); }
    @Override public boolean isErrorEnabled() { return delegate.isErrorEnabled(); }
    @Override public boolean isErrorEnabled(Marker marker) { return delegate.isErrorEnabled(); }
}
