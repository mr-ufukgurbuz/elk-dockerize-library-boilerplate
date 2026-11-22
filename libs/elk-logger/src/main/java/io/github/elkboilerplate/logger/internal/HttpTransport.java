package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.config.ElkConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Logstash {@code http} input'una gönderir: batch tek bir JSON dizi olarak POST edilir,
 * Logstash diziyi ayrı olaylara böler. 2xx dışındaki her yanıt hata sayılır ve batch yeniden denenir.
 * Java 11 {@link HttpClient} kullanır; harici bağımlılık yoktur.
 */
final class HttpTransport implements Transport {

    private final ElkConfig config;
    private final HttpClient client;
    private final URI uri;
    private final String authHeader;

    HttpTransport(ElkConfig config) {
        this.config = config;
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1);
        if (config.isTlsEnabled()) {
            b.sslContext(Tls.sslContext(config));
        }
        this.client = b.build();
        this.uri = URI.create((config.isTlsEnabled() ? "https" : "http") + "://" + config.getHost() + ':'
                + config.getHttpPort() + config.getHttpPath());
        if (config.getHttpUsername() != null) {
            String cred = config.getHttpUsername() + ':' + (config.getHttpPassword() == null ? "" : config.getHttpPassword());
            this.authHeader = "Basic " + Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8));
        } else {
            this.authHeader = null;
        }
    }

    @Override
    public void send(List<String> jsonEvents) throws IOException {
        StringBuilder body = new StringBuilder(jsonEvents.size() * 512);
        body.append('[');
        for (int i = 0; i < jsonEvents.size(); i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append(jsonEvents.get(i));
        }
        body.append(']');
        HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (authHeader != null) {
            rb.header("Authorization", authHeader);
        }
        HttpResponse<String> resp;
        try {
            resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP gönderimi kesildi", e);
        }
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Logstash HTTP " + code + " döndü: " + EventSerializer.truncate(resp.body(), 200));
        }
    }

    @Override
    public String describe() {
        return uri.toString();
    }

    @Override
    public void close() {
        // HttpClient Java 11'de kapatılamaz; GC'ye bırakılır.
    }
}
