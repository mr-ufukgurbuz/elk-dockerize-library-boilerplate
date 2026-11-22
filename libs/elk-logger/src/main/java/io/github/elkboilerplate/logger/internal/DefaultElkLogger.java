package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.Level;
import io.github.elkboilerplate.logger.LogEventBuilder;

import java.util.function.Supplier;

/**
 * {@link ElkLogger} implementasyonu. Etkin seviye context değişince yeniden hesaplanır
 * (context sürüm numarası ile tembel önbellek).
 */
public final class DefaultElkLogger implements ElkLogger {

    private final String name;
    private final Supplier<LoggerContext> contextSupplier;
    private volatile CachedLevel cache;
    private final GenerationSource generation;

    /** Seviye değişikliklerini logger'lara bildirmek için sayaç. */
    public interface GenerationSource {
        int current();
    }

    public DefaultElkLogger(String name, Supplier<LoggerContext> contextSupplier, GenerationSource generation) {
        this.name = name;
        this.contextSupplier = contextSupplier;
        this.generation = generation;
    }

    @Override
    public String getName() {
        return name;
    }

    private Level effective(LoggerContext ctx) {
        int gen = generation.current();
        CachedLevel c = cache;
        if (c == null || c.context != ctx || c.generation != gen) {
            c = new CachedLevel(ctx, gen, ctx.effectiveLevel(name));
            cache = c;
        }
        return c.level;
    }

    /** Değişmez önbellek kaydı: context + sürüm + hesaplanmış seviye atomik olarak birlikte okunur. */
    private static final class CachedLevel {
        final LoggerContext context;
        final int generation;
        final Level level;

        CachedLevel(LoggerContext context, int generation, Level level) {
            this.context = context;
            this.generation = generation;
            this.level = level;
        }
    }

    @Override
    public boolean isEnabled(Level level) {
        if (level == null || level == Level.OFF) {
            return false;
        }
        LoggerContext ctx = contextSupplier.get();
        return level.isAtLeast(effective(ctx));
    }

    @Override
    public void log(Level level, String message, Object... args) {
        if (level == null || level == Level.OFF) {
            return;
        }
        LoggerContext ctx = contextSupplier.get();
        if (level.isAtLeast(effective(ctx))) {
            ctx.dispatch(level, name, message, args, null, null, null);
        }
    }

    @Override
    public LogEventBuilder atLevel(Level level) {
        if (!isEnabled(level)) {
            return NoopLogEventBuilder.INSTANCE;
        }
        return new DefaultLogEventBuilder(level, name, contextSupplier);
    }

    @Override
    public String toString() {
        return "ElkLogger[" + name + "]";
    }
}
