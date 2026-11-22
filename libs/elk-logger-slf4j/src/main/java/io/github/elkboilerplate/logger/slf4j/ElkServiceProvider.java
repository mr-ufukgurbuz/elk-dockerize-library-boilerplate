package io.github.elkboilerplate.logger.slf4j;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * SLF4J 2.x provider. Bu jar classpath'te olduğunda SLF4J kendiliğinden bu provider'ı bulur
 * ({@code META-INF/services/org.slf4j.spi.SLF4JServiceProvider}) ve tüm {@code LoggerFactory.getLogger(...)}
 * çağrıları ELK'ya yönlenir. Classpath'te başka bir provider (logback, slf4j-simple...) OLMAMALIDIR.
 */
public final class ElkServiceProvider implements SLF4JServiceProvider {

    /** Desteklenen SLF4J API sürümü. */
    public static final String REQUESTED_API_VERSION = "2.0.99";

    private ILoggerFactory loggerFactory;
    private IMarkerFactory markerFactory;
    private MDCAdapter mdcAdapter;

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return REQUESTED_API_VERSION;
    }

    @Override
    public void initialize() {
        loggerFactory = new ElkSlf4jLoggerFactory();
        markerFactory = new BasicMarkerFactory();
        mdcAdapter = new ElkMDCAdapter();
    }
}
