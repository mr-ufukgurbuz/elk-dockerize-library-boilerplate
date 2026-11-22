package io.github.elkboilerplate.logger;

import io.github.elkboilerplate.logger.config.ConfigLoader;
import io.github.elkboilerplate.logger.config.ElkConfig;
import io.github.elkboilerplate.logger.internal.DefaultElkLogger;
import io.github.elkboilerplate.logger.internal.InternalLog;
import io.github.elkboilerplate.logger.internal.LoggerContext;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kütüphanenin giriş noktası. SLF4J {@code LoggerFactory} karşılığıdır.
 *
 * <pre>{@code
 * private static final ElkLogger log = ElkLoggerFactory.getLogger(MyService.class);
 * }</pre>
 *
 * İlk {@link #getLogger} çağrısında konfigürasyon otomatik yüklenir ({@link ConfigLoader}).
 * JVM kapanırken kuyruktaki loglar otomatik gönderilir (shutdown hook). İsteğe bağlı olarak
 * {@link #configure(Properties)} ile programatik konfigürasyon yapılabilir.
 */
public final class ElkLoggerFactory {

    private static final ConcurrentMap<String, ElkLogger> LOGGERS = new ConcurrentHashMap<>();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final Object LOCK = new Object();
    private static volatile LoggerContext context;
    private static volatile boolean hookRegistered;

    private ElkLoggerFactory() {
    }

    public static ElkLogger getLogger(Class<?> type) {
        return getLogger(type == null ? "ROOT" : type.getName());
    }

    public static ElkLogger getLogger(String name) {
        String n = name == null ? "ROOT" : name;
        ElkLogger existing = LOGGERS.get(n);
        if (existing != null) {
            return existing;
        }
        return LOGGERS.computeIfAbsent(n, k -> new DefaultElkLogger(k, ElkLoggerFactory::context, GENERATION::get));
    }

    /** Aktif context; yoksa konfigürasyonu yükleyerek oluşturur. */
    static LoggerContext context() {
        LoggerContext ctx = context;
        if (ctx == null) {
            synchronized (LOCK) {
                ctx = context;
                if (ctx == null) {
                    ctx = new LoggerContext(safeLoad());
                    context = ctx;
                    registerShutdownHook();
                    warnIfMisconfigured(ctx.getConfig());
                }
            }
        }
        return ctx;
    }

    private static ElkConfig safeLoad() {
        try {
            return ConfigLoader.load();
        } catch (Throwable t) {
            InternalLog.warn("konfigürasyon yüklenemedi, varsayılanlar kullanılıyor: " + t);
            return ElkConfig.fromProperties(new Properties());
        }
    }

    private static void warnIfMisconfigured(ElkConfig cfg) {
        if ("unknown".equals(cfg.getProjectName())) {
            InternalLog.warn("'elk.project.name' tanımlı değil! Loglar 'applogs-unknown-*' index'ine gider. "
                    + "elk-logger.properties dosyasını classpath'e ekleyin veya -Delk.config.file=... verin.");
        }
    }

    /**
     * Programatik konfigürasyon. Verilen değerler, dosya/ortam değişkeni/system property'lerden
     * gelen değerlerin ÜZERİNE yazılır. Eski context düzgünce kapatılır (kuyruğu boşaltılır).
     */
    public static void configure(Properties overrides) {
        Properties base = ConfigLoader.loadProperties(System.getProperties(), System.getenv(),
                Thread.currentThread().getContextClassLoader());
        if (overrides != null) {
            for (String k : overrides.stringPropertyNames()) {
                base.setProperty(k, overrides.getProperty(k));
            }
        }
        configure(ElkConfig.fromProperties(base));
    }

    /** Hazır bir {@link ElkConfig} ile yeniden yapılandırır (dosya vb. okunmaz). */
    public static void configure(ElkConfig config) {
        LoggerContext old;
        synchronized (LOCK) {
            old = context;
            context = new LoggerContext(config);
            GENERATION.incrementAndGet();
            registerShutdownHook();
        }
        if (old != null) {
            old.shutdown();
        }
        warnIfMisconfigured(config);
    }

    /**
     * Çalışma zamanında seviye değiştirir. {@code loggerPrefix} null/boş ise ana (root) seviye değişir.
     * Örn. {@code ElkLoggerFactory.setLevel("com.acme.dao", Level.DEBUG)}.
     */
    public static void setLevel(String loggerPrefix, Level level) {
        context().setLevel(loggerPrefix, level);
        GENERATION.incrementAndGet();
    }

    /** Kuyruktaki olayların gönderilmesini bekler. */
    public static boolean flush(long timeoutMillis) {
        LoggerContext ctx = context;
        return ctx == null || ctx.flush(timeoutMillis);
    }

    /** Kalan olayları gönderir ve gönderici thread'i durdurur. Sonrasında yapılan loglar yeni context açar. */
    public static void shutdown() {
        LoggerContext ctx;
        synchronized (LOCK) {
            ctx = context;
            context = null;
            GENERATION.incrementAndGet();
        }
        if (ctx != null) {
            ctx.shutdown();
        }
    }

    /** Çalışma istatistikleri (gönderilen, atılan, kuyruk boyu...). */
    public static ElkStats getStats() {
        LoggerContext ctx = context;
        return ctx == null ? new ElkStats(0, 0, 0, 0, 0, 0, false) : ctx.stats();
    }

    /** Aktif konfigürasyon (henüz başlatılmadıysa yükler). */
    public static ElkConfig getConfig() {
        return context().getConfig();
    }

    private static void registerShutdownHook() {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LoggerContext ctx = context;
                if (ctx != null) {
                    ctx.shutdown();
                }
            }, "elk-logger-shutdown"));
        } catch (IllegalStateException ignored) {
            // JVM zaten kapanıyor
        }
    }
}
