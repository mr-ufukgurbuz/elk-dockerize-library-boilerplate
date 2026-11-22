package io.github.elkboilerplate.logger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sahte Logstash sunucularıyla uçtan uca davranış testi (gerçek ağ soketleri). */
class TransportIntegrationTest {

    @TempDir
    Path tmp;

    @AfterEach
    void tearDown() {
        ElkLoggerFactory.shutdown();
        ElkMDC.clear();
    }

    private static Properties base(String project) {
        Properties p = new Properties();
        p.setProperty("elk.project.name", project);
        p.setProperty("elk.console.enabled", "false");
        p.setProperty("elk.flush.interval.ms", "50");
        p.setProperty("elk.level", "DEBUG");
        p.setProperty("elk.labels.team", "core");
        return p;
    }

    @Test
    void tcpJsonLines() throws Exception {
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
                    // test bitti
                }
            });
            t.setDaemon(true);
            t.start();

            Properties p = base("Proje-A");
            p.setProperty("elk.transport", "tcp");
            p.setProperty("elk.logstash.host", "127.0.0.1");
            p.setProperty("elk.logstash.tcp.port", String.valueOf(server.getLocalPort()));
            p.setProperty("elk.include.caller", "true");
            ElkLoggerFactory.configure(p);

            ElkLogger log = ElkLoggerFactory.getLogger(TransportIntegrationTest.class);
            ElkMDC.put("requestId", "req-1");
            log.info("merhaba {}", "dünya");
            log.debug("debug görünür");
            log.trace("trace görünmez");
            log.error("hata oldu id={}", 42, new IllegalStateException("bozuk", new RuntimeException("kök")));
            log.atWarn().field("orderId", 99).field("amount", 12.5).tag("checkout").log("yapısal");

            assertTrue(ElkLoggerFactory.flush(5000));
            long deadline = System.currentTimeMillis() + 5000;
            while (received.size() < 4 && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            assertEquals(4, received.size(), received.toString());
            String info = received.get(0);
            assertTrue(info.contains("\"project\":\"proje-a\""), info);
            assertTrue(info.contains("\"application\":\"Proje-A\""), info);
            assertTrue(info.contains("\"message\":\"merhaba dünya\""), info);
            assertTrue(info.contains("\"message_template\":\"merhaba {}\""), info);
            assertTrue(info.contains("\"level\":\"INFO\",\"level_value\":20"), info);
            assertTrue(info.contains("\"mdc\":{\"requestId\":\"req-1\"}"), info);
            assertTrue(info.contains("\"labels\":{\"team\":\"core\"}"), info);
            assertTrue(info.contains("\"caller\":{\"class\":\"" + TransportIntegrationTest.class.getName()), info);
            String err = received.get(2);
            assertTrue(err.contains("\"exception\":{\"class\":\"java.lang.IllegalStateException\""), err);
            assertTrue(err.contains("\"root_class\":\"java.lang.RuntimeException\""), err);
            assertTrue(err.contains("\"message\":\"hata oldu id=42\""), err);
            String warn = received.get(3);
            assertTrue(warn.contains("\"fields\":{\"orderId\":99,\"amount\":12.5}"), warn);
            assertTrue(warn.contains("\"tags\":[\"checkout\"]"), warn);
            assertEquals(4, ElkLoggerFactory.getStats().getSent());
        }
    }

    @Test
    void httpJsonArray() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> auth = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            ex.sendResponseHeaders(200, 2);
            ex.getResponseBody().write("ok".getBytes(StandardCharsets.UTF_8));
            ex.close();
        });
        server.start();
        try {
            Properties p = base("proje-b");
            p.setProperty("elk.transport", "http");
            p.setProperty("elk.logstash.host", "127.0.0.1");
            p.setProperty("elk.logstash.http.port", String.valueOf(server.getAddress().getPort()));
            p.setProperty("elk.logstash.http.username", "u");
            p.setProperty("elk.logstash.http.password", "p");
            ElkLoggerFactory.configure(p);
            ElkLogger log = ElkLoggerFactory.getLogger("http.test");
            for (int i = 0; i < 10; i++) {
                log.info("olay {}", i);
            }
            assertTrue(ElkLoggerFactory.flush(5000));
            String all = String.join("", bodies);
            assertTrue(all.startsWith("[{"), all);
            for (int i = 0; i < 10; i++) {
                assertTrue(all.contains("\"message\":\"olay " + i + "\""), all);
            }
            assertEquals("Basic dTpw", auth.get(0));
            assertEquals(10, ElkLoggerFactory.getStats().getSent());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableLogstashGoesToFallbackFileAndNeverThrows() throws Exception {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        }
        Path fallback = tmp.resolve("fb/elk-fallback.log");
        Properties p = base("proje-c");
        p.setProperty("elk.logstash.host", "127.0.0.1");
        p.setProperty("elk.logstash.tcp.port", String.valueOf(deadPort));
        p.setProperty("elk.connect.timeout.ms", "200");
        p.setProperty("elk.retry.initial.backoff.ms", "10");
        p.setProperty("elk.retry.max.attempts", "2");
        p.setProperty("elk.fallback.file.enabled", "true");
        p.setProperty("elk.fallback.file.path", fallback.toString());
        ElkLoggerFactory.configure(p);
        ElkLogger log = ElkLoggerFactory.getLogger("fallback.test");
        log.info("kaybolmamalı");
        long deadline = System.currentTimeMillis() + 5000;
        while (ElkLoggerFactory.getStats().getWrittenToFallback() < 1 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertEquals(1, ElkLoggerFactory.getStats().getWrittenToFallback());
        assertFalse(ElkLoggerFactory.getStats().isConnected());
        String content = new String(Files.readAllBytes(fallback), StandardCharsets.UTF_8);
        assertTrue(content.contains("kaybolmamalı"), content);
    }

    @Test
    void queueOverflowDropsWithoutBlocking() {
        Properties p = base("proje-d");
        p.setProperty("elk.logstash.host", "127.0.0.1");
        p.setProperty("elk.logstash.tcp.port", "1");
        p.setProperty("elk.connect.timeout.ms", "200");
        p.setProperty("elk.queue.capacity", "10");
        p.setProperty("elk.shutdown.timeout.ms", "200");
        ElkLoggerFactory.configure(p);
        ElkLogger log = ElkLoggerFactory.getLogger("overflow.test");
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            log.info("olay {}", i);
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(ms < 2000, "loglama bloklamamalı, süre=" + ms);
        assertTrue(ElkLoggerFactory.getStats().getDropped() >= 980, ElkLoggerFactory.getStats().toString());
    }

    @Test
    void runtimeLevelChange() {
        Properties p = base("proje-e");
        p.setProperty("elk.transport", "none");
        p.setProperty("elk.level", "INFO");
        ElkLoggerFactory.configure(p);
        ElkLogger log = ElkLoggerFactory.getLogger("com.acme.dao.UserDao");
        assertFalse(log.isDebugEnabled());
        ElkLoggerFactory.setLevel("com.acme.dao", Level.DEBUG);
        assertTrue(log.isDebugEnabled());
        ElkLoggerFactory.setLevel("com.acme.dao", null);
        assertFalse(log.isDebugEnabled());
    }
}
