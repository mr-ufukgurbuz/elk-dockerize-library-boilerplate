package io.github.elkboilerplate.logger;

import io.github.elkboilerplate.logger.config.ConfigLoader;
import io.github.elkboilerplate.logger.config.ElkConfig;
import io.github.elkboilerplate.logger.config.OverflowPolicy;
import io.github.elkboilerplate.logger.config.TransportType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigLoaderTest {

    @TempDir
    Path tmp;

    @Test
    void defaultsWhenNothingConfigured() {
        ElkConfig c = ElkConfig.fromProperties(new Properties());
        assertEquals("unknown", c.getProjectName());
        assertEquals(TransportType.TCP, c.getTransport());
        assertEquals("localhost", c.getHost());
        assertEquals(5000, c.getTcpPort());
        assertEquals(Level.INFO, c.getRootLevel());
        assertEquals(OverflowPolicy.DROP_NEWEST, c.getOverflowPolicy());
    }

    @Test
    void layeringFileThenEnvThenSystemProperty() throws Exception {
        Path f = tmp.resolve("elk.properties");
        Files.write(f, String.join("\n",
                "elk.project.name=Order Service",
                "elk.logstash.host=file-host",
                "elk.logstash.tcp.port=6000",
                "elk.level=DEBUG",
                "elk.level.com.acme.dao=TRACE",
                "elk.labels.team=payments",
                "elk.environment=${APP_ENV:dev}",
                "elk.transport=http").getBytes("UTF-8"));
        Properties sys = new Properties();
        sys.setProperty("elk.config.file", f.toString());
        sys.setProperty("elk.logstash.tcp.port", "7000");
        Map<String, String> env = new HashMap<>();
        env.put("ELK_LOGSTASH_HOST", "env-host");
        env.put("APP_ENV", "prod");

        ElkConfig c = ElkConfig.fromProperties(ConfigLoader.loadProperties(sys, env, null));
        assertEquals("order-service", c.getProjectName());
        assertEquals("Order Service", c.getApplicationName());
        assertEquals("env-host", c.getHost());
        assertEquals(7000, c.getTcpPort());
        assertEquals(Level.DEBUG, c.getRootLevel());
        assertEquals(Level.TRACE, c.getLoggerLevels().get("com.acme.dao"));
        assertEquals("payments", c.getLabels().get("team"));
        assertEquals("prod", c.getEnvironment());
        assertEquals(TransportType.HTTP, c.getTransport());
    }

    @Test
    void placeholderDefaultUsedWhenMissing() {
        Properties sys = new Properties();
        sys.setProperty("elk.environment", "${NOPE_NOT_SET:fallback}");
        ElkConfig c = ElkConfig.fromProperties(ConfigLoader.loadProperties(sys, new HashMap<>(), null));
        assertEquals("fallback", c.getEnvironment());
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        Properties p = new Properties();
        p.setProperty("elk.logstash.tcp.port", "abc");
        p.setProperty("elk.transport", "carrier-pigeon");
        p.setProperty("elk.enabled", "false");
        ElkConfig c = ElkConfig.fromProperties(p);
        assertEquals(5000, c.getTcpPort());
        assertEquals(TransportType.TCP, c.getTransport());
        assertFalse(c.isEnabled());
    }

    @Test
    void projectNameSanitization() {
        assertEquals("my-app_1", ElkConfig.sanitizeProject("  My App_1 "));
        assertEquals("a-b-c", ElkConfig.sanitizeProject("A.B/C"));
        assertEquals("unknown", ElkConfig.sanitizeProject("___"));
    }
}
