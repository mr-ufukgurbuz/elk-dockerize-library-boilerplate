package io.github.elkboilerplate.logger.slf4j;

import io.github.elkboilerplate.logger.ElkLoggerFactory;
import io.github.elkboilerplate.logger.ElkMDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Slf4jBridgeTest {

    @AfterEach
    void tearDown() {
        ElkLoggerFactory.shutdown();
        MDC.clear();
    }

    @Test
    void slf4jIsBoundToElkProvider() {
        assertTrue(LoggerFactory.getILoggerFactory() instanceof ElkSlf4jLoggerFactory);
    }

    @Test
    void mdcIsSharedWithElkMdc() {
        MDC.put("k", "v");
        assertEquals("v", ElkMDC.get("k"));
    }

    @Test
    void slf4jEventsReachLogstashWithFieldsTagsAndException() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread t = new Thread(() -> {
                try (Socket s = server.accept();
                     BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        received.add(line);
                    }
                } catch (Exception ignored) {
                    // bitti
                }
            });
            t.setDaemon(true);
            t.start();

            Properties p = new Properties();
            p.setProperty("elk.project.name", "slf4j-test");
            p.setProperty("elk.console.enabled", "false");
            p.setProperty("elk.flush.interval.ms", "50");
            p.setProperty("elk.logstash.host", "127.0.0.1");
            p.setProperty("elk.logstash.tcp.port", String.valueOf(server.getLocalPort()));
            ElkLoggerFactory.configure(p);

            Logger log = LoggerFactory.getLogger("slf4j.bridge");
            MDC.put("tx", "t-1");
            log.info("merhaba {}", "slf4j");
            log.atWarn().addKeyValue("amount", 42).addMarker(MarkerFactory.getMarker("AUDIT")).log("akıcı {}", 1);
            log.error("hata", new IllegalStateException("x"));
            log.debug("görünmemeli");

            ElkLoggerFactory.flush(5000);
            long deadline = System.currentTimeMillis() + 5000;
            while (received.size() < 3 && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            assertEquals(3, received.size(), received.toString());
            assertTrue(received.get(0).contains("\"message\":\"merhaba slf4j\""), received.get(0));
            assertTrue(received.get(0).contains("\"mdc\":{\"tx\":\"t-1\"}"), received.get(0));
            assertTrue(received.get(1).contains("\"fields\":{\"amount\":42}"), received.get(1));
            assertTrue(received.get(1).contains("\"tags\":[\"AUDIT\"]"), received.get(1));
            assertTrue(received.get(1).contains("\"message\":\"akıcı 1\""), received.get(1));
            assertTrue(received.get(2).contains("\"exception\":{\"class\":\"java.lang.IllegalStateException\""), received.get(2));
        }
    }
}
