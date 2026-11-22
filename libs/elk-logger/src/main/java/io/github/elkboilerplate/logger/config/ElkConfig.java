package io.github.elkboilerplate.logger.config;

import io.github.elkboilerplate.logger.Level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Kütüphanenin değişmez (immutable) konfigürasyonu. Genellikle {@link ConfigLoader} tarafından
 * {@code elk-logger.properties} dosyasından oluşturulur. Tüm anahtarlar ve varsayılanları
 * {@code docs/07-KONFIGURASYON-REFERANSI.md} dosyasında listelenmiştir.
 *
 * <p>YENİ BİR AYAR EKLERKEN: (1) KEY_ sabiti ekle, (2) alan + getter ekle, (3) {@link #fromProperties}
 * içinde oku, (4) {@code elk-logger.properties.example} ve referans dokümana ekle.</p>
 */
public final class ElkConfig {

    public static final String KEY_ENABLED = "elk.enabled";
    public static final String KEY_PROJECT = "elk.project.name";
    public static final String KEY_APPLICATION = "elk.application.name";
    public static final String KEY_ENVIRONMENT = "elk.environment";
    public static final String KEY_HOSTNAME = "elk.hostname";
    public static final String KEY_TRANSPORT = "elk.transport";
    public static final String KEY_HOST = "elk.logstash.host";
    public static final String KEY_TCP_PORT = "elk.logstash.tcp.port";
    public static final String KEY_HTTP_PORT = "elk.logstash.http.port";
    public static final String KEY_HTTP_PATH = "elk.logstash.http.path";
    public static final String KEY_HTTP_USERNAME = "elk.logstash.http.username";
    public static final String KEY_HTTP_PASSWORD = "elk.logstash.http.password";
    public static final String KEY_TLS_ENABLED = "elk.tls.enabled";
    public static final String KEY_TLS_TRUSTSTORE_PATH = "elk.tls.truststore.path";
    public static final String KEY_TLS_TRUSTSTORE_PASSWORD = "elk.tls.truststore.password";
    public static final String KEY_TLS_TRUSTSTORE_TYPE = "elk.tls.truststore.type";
    public static final String KEY_TLS_VERIFY_HOSTNAME = "elk.tls.verify.hostname";
    public static final String KEY_CONNECT_TIMEOUT = "elk.connect.timeout.ms";
    public static final String KEY_REQUEST_TIMEOUT = "elk.request.timeout.ms";
    public static final String KEY_QUEUE_CAPACITY = "elk.queue.capacity";
    public static final String KEY_BATCH_SIZE = "elk.batch.size";
    public static final String KEY_FLUSH_INTERVAL = "elk.flush.interval.ms";
    public static final String KEY_OVERFLOW_POLICY = "elk.overflow.policy";
    public static final String KEY_OVERFLOW_BLOCK_TIMEOUT = "elk.overflow.block.timeout.ms";
    public static final String KEY_RETRY_INITIAL_BACKOFF = "elk.retry.initial.backoff.ms";
    public static final String KEY_RETRY_MAX_BACKOFF = "elk.retry.max.backoff.ms";
    public static final String KEY_RETRY_MAX_ATTEMPTS = "elk.retry.max.attempts";
    public static final String KEY_SHUTDOWN_TIMEOUT = "elk.shutdown.timeout.ms";
    public static final String KEY_LEVEL = "elk.level";
    public static final String KEY_LEVEL_PREFIX = "elk.level.";
    public static final String KEY_CONSOLE_ENABLED = "elk.console.enabled";
    public static final String KEY_CONSOLE_LEVEL = "elk.console.level";
    public static final String KEY_CONSOLE_STREAM = "elk.console.stream";
    public static final String KEY_FALLBACK_ENABLED = "elk.fallback.file.enabled";
    public static final String KEY_FALLBACK_PATH = "elk.fallback.file.path";
    public static final String KEY_FALLBACK_MAX_SIZE_MB = "elk.fallback.file.max.size.mb";
    public static final String KEY_INCLUDE_CALLER = "elk.include.caller";
    public static final String KEY_INCLUDE_MDC = "elk.include.mdc";
    public static final String KEY_LABEL_PREFIX = "elk.labels.";
    public static final String KEY_MESSAGE_MAX_LENGTH = "elk.message.max.length";
    public static final String KEY_STACKTRACE_MAX_LENGTH = "elk.stacktrace.max.length";
    public static final String KEY_DEBUG = "elk.debug";

    private final boolean enabled;
    private final String projectName;
    private final String applicationName;
    private final String environment;
    private final String hostname;
    private final TransportType transport;
    private final String host;
    private final int tcpPort;
    private final int httpPort;
    private final String httpPath;
    private final String httpUsername;
    private final String httpPassword;
    private final boolean tlsEnabled;
    private final String tlsTruststorePath;
    private final String tlsTruststorePassword;
    private final String tlsTruststoreType;
    private final boolean tlsVerifyHostname;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int queueCapacity;
    private final int batchSize;
    private final long flushIntervalMs;
    private final OverflowPolicy overflowPolicy;
    private final long overflowBlockTimeoutMs;
    private final long retryInitialBackoffMs;
    private final long retryMaxBackoffMs;
    private final int retryMaxAttempts;
    private final long shutdownTimeoutMs;
    private final Level rootLevel;
    private final Map<String, Level> loggerLevels;
    private final boolean consoleEnabled;
    private final Level consoleLevel;
    private final boolean consoleStderr;
    private final boolean fallbackEnabled;
    private final String fallbackPath;
    private final long fallbackMaxBytes;
    private final boolean includeCaller;
    private final boolean includeMdc;
    private final Map<String, String> labels;
    private final int messageMaxLength;
    private final int stacktraceMaxLength;
    private final boolean debug;
    private final Properties source;

    private ElkConfig(Properties p) {
        this.source = p;
        this.enabled = bool(p, KEY_ENABLED, true);
        String rawProject = str(p, KEY_PROJECT, null);
        this.projectName = sanitizeProject(rawProject);
        this.applicationName = str(p, KEY_APPLICATION, rawProject == null ? projectName : rawProject);
        this.environment = str(p, KEY_ENVIRONMENT, "default");
        this.hostname = str(p, KEY_HOSTNAME, null);
        this.transport = enumVal(p, KEY_TRANSPORT, TransportType.class, TransportType.TCP);
        this.host = str(p, KEY_HOST, "localhost");
        this.tcpPort = intVal(p, KEY_TCP_PORT, 5000, 1, 65535);
        this.httpPort = intVal(p, KEY_HTTP_PORT, 8080, 1, 65535);
        String path = str(p, KEY_HTTP_PATH, "/");
        this.httpPath = path.startsWith("/") ? path : "/" + path;
        this.httpUsername = str(p, KEY_HTTP_USERNAME, null);
        this.httpPassword = str(p, KEY_HTTP_PASSWORD, null);
        this.tlsEnabled = bool(p, KEY_TLS_ENABLED, false);
        this.tlsTruststorePath = str(p, KEY_TLS_TRUSTSTORE_PATH, null);
        this.tlsTruststorePassword = str(p, KEY_TLS_TRUSTSTORE_PASSWORD, null);
        this.tlsTruststoreType = str(p, KEY_TLS_TRUSTSTORE_TYPE, "PKCS12");
        this.tlsVerifyHostname = bool(p, KEY_TLS_VERIFY_HOSTNAME, true);
        this.connectTimeoutMs = intVal(p, KEY_CONNECT_TIMEOUT, 3000, 100, 600_000);
        this.requestTimeoutMs = intVal(p, KEY_REQUEST_TIMEOUT, 10_000, 100, 600_000);
        this.queueCapacity = intVal(p, KEY_QUEUE_CAPACITY, 10_000, 1, 10_000_000);
        this.batchSize = intVal(p, KEY_BATCH_SIZE, 200, 1, 100_000);
        this.flushIntervalMs = longVal(p, KEY_FLUSH_INTERVAL, 1000, 10, 3_600_000);
        this.overflowPolicy = enumVal(p, KEY_OVERFLOW_POLICY, OverflowPolicy.class, OverflowPolicy.DROP_NEWEST);
        this.overflowBlockTimeoutMs = longVal(p, KEY_OVERFLOW_BLOCK_TIMEOUT, 1000, 0, 3_600_000);
        this.retryInitialBackoffMs = longVal(p, KEY_RETRY_INITIAL_BACKOFF, 500, 10, 3_600_000);
        this.retryMaxBackoffMs = Math.max(retryInitialBackoffMs, longVal(p, KEY_RETRY_MAX_BACKOFF, 30_000, 10, 3_600_000));
        this.retryMaxAttempts = intVal(p, KEY_RETRY_MAX_ATTEMPTS, 0, 0, Integer.MAX_VALUE);
        this.shutdownTimeoutMs = longVal(p, KEY_SHUTDOWN_TIMEOUT, 5000, 0, 600_000);
        this.rootLevel = Level.parse(str(p, KEY_LEVEL, "INFO"), Level.INFO);
        Map<String, Level> levels = new TreeMap<>();
        Map<String, String> lbls = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith(KEY_LEVEL_PREFIX) && key.length() > KEY_LEVEL_PREFIX.length()) {
                Level l = Level.parse(p.getProperty(key), null);
                if (l != null) {
                    levels.put(key.substring(KEY_LEVEL_PREFIX.length()), l);
                }
            } else if (key.startsWith(KEY_LABEL_PREFIX) && key.length() > KEY_LABEL_PREFIX.length()) {
                lbls.put(key.substring(KEY_LABEL_PREFIX.length()), p.getProperty(key).trim());
            }
        }
        this.loggerLevels = Collections.unmodifiableMap(levels);
        this.labels = Collections.unmodifiableMap(lbls);
        this.consoleEnabled = bool(p, KEY_CONSOLE_ENABLED, true);
        this.consoleLevel = Level.parse(str(p, KEY_CONSOLE_LEVEL, "TRACE"), Level.TRACE);
        this.consoleStderr = "stderr".equalsIgnoreCase(str(p, KEY_CONSOLE_STREAM, "stdout"));
        this.fallbackEnabled = bool(p, KEY_FALLBACK_ENABLED, false);
        this.fallbackPath = str(p, KEY_FALLBACK_PATH, "logs/elk-fallback.log");
        this.fallbackMaxBytes = longVal(p, KEY_FALLBACK_MAX_SIZE_MB, 100, 1, 1_000_000) * 1024L * 1024L;
        this.includeCaller = bool(p, KEY_INCLUDE_CALLER, false);
        this.includeMdc = bool(p, KEY_INCLUDE_MDC, true);
        this.messageMaxLength = intVal(p, KEY_MESSAGE_MAX_LENGTH, 32_000, 100, 10_000_000);
        this.stacktraceMaxLength = intVal(p, KEY_STACKTRACE_MAX_LENGTH, 32_000, 100, 10_000_000);
        this.debug = bool(p, KEY_DEBUG, false);
    }

    /**
     * Çözümlenmiş (placeholder'ları açılmış, override'ları uygulanmış) properties'ten konfigürasyon oluşturur.
     * Geçersiz değerler sessizce varsayılana döner; kütüphane asla konfigürasyon yüzünden uygulamayı durdurmaz.
     */
    public static ElkConfig fromProperties(Properties properties) {
        Properties copy = new Properties();
        if (properties != null) {
            for (String k : properties.stringPropertyNames()) {
                copy.setProperty(k, properties.getProperty(k));
            }
        }
        return new ElkConfig(copy);
    }

    /**
     * Proje adını Elasticsearch index adına uygun hale getirir: küçük harf, yalnızca [a-z0-9_-].
     * Logstash pipeline'ı aynı kuralı tekrar uygular (bkz. elk-stack/logstash/pipeline/applogs.conf).
     */
    public static String sanitizeProject(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "unknown";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        s = s.replaceAll("^[-_]+", "");
        if (s.isEmpty()) {
            return "unknown";
        }
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    // ----------------------------------------------------------------- parsing helpers

    private static String str(Properties p, String key, String def) {
        String v = p.getProperty(key);
        if (v == null) {
            return def;
        }
        v = v.trim();
        return v.isEmpty() ? def : v;
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = str(p, key, null);
        if (v == null) {
            return def;
        }
        return "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "1".equals(v) || "on".equalsIgnoreCase(v);
    }

    private static int intVal(Properties p, String key, int def, int min, int max) {
        return (int) longVal(p, key, def, min, max);
    }

    private static long longVal(Properties p, String key, long def, long min, long max) {
        String v = str(p, key, null);
        if (v == null) {
            return def;
        }
        try {
            long l = Long.parseLong(v);
            return Math.max(min, Math.min(max, l));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static <E extends Enum<E>> E enumVal(Properties p, String key, Class<E> type, E def) {
        String v = str(p, key, null);
        if (v == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, v.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    // ----------------------------------------------------------------- getters

    public boolean isEnabled() { return enabled; }
    /** Sanitize edilmiş proje adı; index adı {@code applogs-<projectName>-YYYY.MM.dd} olur. */
    public String getProjectName() { return projectName; }
    public String getApplicationName() { return applicationName; }
    public String getEnvironment() { return environment; }
    /** Konfigürasyonda verilmişse hostname; yoksa null (otomatik bulunur). */
    public String getHostname() { return hostname; }
    public TransportType getTransport() { return transport; }
    public String getHost() { return host; }
    public int getTcpPort() { return tcpPort; }
    public int getHttpPort() { return httpPort; }
    public String getHttpPath() { return httpPath; }
    public String getHttpUsername() { return httpUsername; }
    public String getHttpPassword() { return httpPassword; }
    public boolean isTlsEnabled() { return tlsEnabled; }
    public String getTlsTruststorePath() { return tlsTruststorePath; }
    public String getTlsTruststorePassword() { return tlsTruststorePassword; }
    public String getTlsTruststoreType() { return tlsTruststoreType; }
    public boolean isTlsVerifyHostname() { return tlsVerifyHostname; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public int getQueueCapacity() { return queueCapacity; }
    public int getBatchSize() { return batchSize; }
    public long getFlushIntervalMs() { return flushIntervalMs; }
    public OverflowPolicy getOverflowPolicy() { return overflowPolicy; }
    public long getOverflowBlockTimeoutMs() { return overflowBlockTimeoutMs; }
    public long getRetryInitialBackoffMs() { return retryInitialBackoffMs; }
    public long getRetryMaxBackoffMs() { return retryMaxBackoffMs; }
    /** 0 = sınırsız deneme. */
    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public long getShutdownTimeoutMs() { return shutdownTimeoutMs; }
    public Level getRootLevel() { return rootLevel; }
    /** Logger adı öneki -> seviye. Örn. {@code elk.level.com.acme.dao=DEBUG}. */
    public Map<String, Level> getLoggerLevels() { return loggerLevels; }
    public boolean isConsoleEnabled() { return consoleEnabled; }
    public Level getConsoleLevel() { return consoleLevel; }
    public boolean isConsoleStderr() { return consoleStderr; }
    public boolean isFallbackEnabled() { return fallbackEnabled; }
    public String getFallbackPath() { return fallbackPath; }
    public long getFallbackMaxBytes() { return fallbackMaxBytes; }
    public boolean isIncludeCaller() { return includeCaller; }
    public boolean isIncludeMdc() { return includeMdc; }
    /** Her loga eklenen sabit etiketler ({@code labels.<ad>}). Örn. {@code elk.labels.team=payments}. */
    public Map<String, String> getLabels() { return labels; }
    public int getMessageMaxLength() { return messageMaxLength; }
    public int getStacktraceMaxLength() { return stacktraceMaxLength; }
    public boolean isDebug() { return debug; }

    /** Konfigürasyonun oluşturulduğu (çözümlenmiş) properties'in kopyası. */
    public Properties toProperties() {
        Properties copy = new Properties();
        for (String k : source.stringPropertyNames()) {
            copy.setProperty(k, source.getProperty(k));
        }
        return copy;
    }

    @Override
    public String toString() {
        return "ElkConfig{project=" + projectName + ", application=" + applicationName + ", environment=" + environment
                + ", transport=" + transport + ", target=" + host + ':' + (transport == TransportType.HTTP ? httpPort : tcpPort)
                + ", tls=" + tlsEnabled + ", level=" + rootLevel + ", loggerLevels=" + loggerLevels
                + ", queueCapacity=" + queueCapacity + ", batchSize=" + batchSize + ", overflow=" + overflowPolicy
                + ", console=" + consoleEnabled + ", fallback=" + fallbackEnabled + ", enabled=" + enabled + '}';
    }
}
