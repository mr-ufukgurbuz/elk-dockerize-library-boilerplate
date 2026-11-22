package io.github.elkboilerplate.logger.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Konfigürasyonu katmanlı olarak yükler. Öncelik (sonraki öncekini ezer):
 * <ol>
 *   <li>Kütüphane varsayılanları ({@link ElkConfig} içinde)</li>
 *   <li>Classpath'teki {@code elk-logger.properties}</li>
 *   <li>Harici dosya: {@code -Delk.config.file=/yol/dosya.properties} veya ortam değişkeni {@code ELK_CONFIG_FILE}</li>
 *   <li>Ortam değişkenleri: {@code ELK_LOGSTASH_HOST} -> {@code elk.logstash.host} (büyük harf, '_' -> '.')</li>
 *   <li>JVM system property'leri: {@code -Delk.logstash.host=...}</li>
 * </ol>
 * Değerler içinde {@code ${DEGISKEN}} veya {@code ${DEGISKEN:varsayilan}} kullanılabilir; önce system property, sonra
 * ortam değişkeni aranır.
 */
public final class ConfigLoader {

    public static final String CLASSPATH_RESOURCE = "elk-logger.properties";
    public static final String CONFIG_FILE_PROPERTY = "elk.config.file";
    public static final String CONFIG_FILE_ENV = "ELK_CONFIG_FILE";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private ConfigLoader() {
    }

    /** Gerçek JVM ortamından (system properties + env) yükler. */
    public static ElkConfig load() {
        return ElkConfig.fromProperties(loadProperties(System.getProperties(), System.getenv(), Thread.currentThread().getContextClassLoader()));
    }

    /**
     * Test edilebilir çekirdek: verilen system properties, ortam değişkenleri ve classloader ile çözümlenmiş properties üretir.
     */
    public static Properties loadProperties(Properties sysProps, Map<String, String> env, ClassLoader classLoader) {
        Properties result = new Properties();

        // 2) classpath
        ClassLoader cl = classLoader != null ? classLoader : ConfigLoader.class.getClassLoader();
        if (cl != null) {
            try (InputStream in = cl.getResourceAsStream(CLASSPATH_RESOURCE)) {
                if (in != null) {
                    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        result.load(r);
                    }
                }
            } catch (IOException e) {
                System.err.println("[elk-logger] classpath " + CLASSPATH_RESOURCE + " okunamadı: " + e);
            }
        }

        // 3) harici dosya
        String externalFile = sysProps.getProperty(CONFIG_FILE_PROPERTY);
        if (externalFile == null || externalFile.trim().isEmpty()) {
            externalFile = env.get(CONFIG_FILE_ENV);
        }
        if (externalFile != null && !externalFile.trim().isEmpty()) {
            Path path = Paths.get(externalFile.trim());
            if (Files.isReadable(path)) {
                try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Properties ext = new Properties();
                    ext.load(r);
                    result.putAll(ext);
                } catch (IOException e) {
                    System.err.println("[elk-logger] konfigürasyon dosyası okunamadı: " + path + " -> " + e);
                }
            } else {
                System.err.println("[elk-logger] konfigürasyon dosyası bulunamadı/okunamıyor: " + path.toAbsolutePath());
            }
        }

        // 4) ortam değişkenleri ELK_*
        for (Map.Entry<String, String> e : env.entrySet()) {
            String name = e.getKey();
            if (name.startsWith("ELK_") && !name.equals(CONFIG_FILE_ENV) && e.getValue() != null) {
                result.setProperty(envToKey(name), e.getValue());
            }
        }

        // 5) system properties elk.*
        for (String name : sysProps.stringPropertyNames()) {
            if (name.startsWith("elk.") && !name.equals(CONFIG_FILE_PROPERTY)) {
                result.setProperty(name, sysProps.getProperty(name));
            }
        }

        // placeholder çözümü
        Function<String, String> lookup = name -> {
            String v = sysProps.getProperty(name);
            return v != null ? v : env.get(name);
        };
        Properties resolved = new Properties();
        for (String key : result.stringPropertyNames()) {
            resolved.setProperty(key, resolvePlaceholders(result.getProperty(key), lookup));
        }
        return resolved;
    }

    /** {@code ELK_LOGSTASH_TCP_PORT} -> {@code elk.logstash.tcp.port}. */
    static String envToKey(String envName) {
        return envName.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    static String resolvePlaceholders(String value, Function<String, String> lookup) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher m = PLACEHOLDER.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String found = lookup.apply(m.group(1));
            String replacement = found != null ? found : (m.group(2) != null ? m.group(2) : "");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
