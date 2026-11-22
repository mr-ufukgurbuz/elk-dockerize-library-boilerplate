package io.github.elkboilerplate.logger.slf4j;

import io.github.elkboilerplate.logger.ElkLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** SLF4J {@link ILoggerFactory}: her isim için bir {@link ElkSlf4jLogger} (ElkLogger sarmalayıcısı) üretir. */
final class ElkSlf4jLoggerFactory implements ILoggerFactory {

    private final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, n -> new ElkSlf4jLogger(n, ElkLoggerFactory.getLogger(n)));
    }
}
