package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.config.ElkConfig;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Logstash {@code tcp} input'una {@code json_lines} codec'i ile gönderir: her olay tek satır JSON + '\n'.
 * Bağlantı kalıcıdır; hata olursa kapatılır ve bir sonraki denemede yeniden açılır.
 *
 * <p>Not: TCP'de uygulama seviyesinde onay (ack) yoktur. Bağlantı yazma sırasında koparsa batch
 * yeniden gönderilir; bu nedenle nadiren mükerrer olay oluşabilir (at-least-once). Onaylı gönderim
 * gerekiyorsa {@code elk.transport=http} kullanın.</p>
 */
final class TcpTransport implements Transport {

    private final ElkConfig config;
    private Socket socket;
    private OutputStream out;

    TcpTransport(ElkConfig config) {
        this.config = config;
    }

    @Override
    public void send(List<String> jsonEvents) throws IOException {
        try {
            ensureConnected();
            for (String json : jsonEvents) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            }
            out.flush();
        } catch (IOException | RuntimeException e) {
            close();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        Socket s;
        if (config.isTlsEnabled()) {
            s = Tls.socketFactory(config).createSocket();
        } else {
            s = new Socket();
        }
        try {
            s.setKeepAlive(true);
            s.setTcpNoDelay(true);
            s.setSoTimeout(config.getRequestTimeoutMs());
            s.connect(new InetSocketAddress(config.getHost(), config.getTcpPort()), config.getConnectTimeoutMs());
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                if (config.isTlsVerifyHostname()) {
                    SSLParameters params = ssl.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    ssl.setSSLParameters(params);
                }
                ssl.startHandshake();
            }
        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignored) {
                // yoksay
            }
            throw e;
        }
        socket = s;
        out = new BufferedOutputStream(s.getOutputStream(), 64 * 1024);
        InternalLog.debug("TCP bağlantısı kuruldu: " + describe());
    }

    @Override
    public String describe() {
        return (config.isTlsEnabled() ? "tcp+tls://" : "tcp://") + config.getHost() + ':' + config.getTcpPort();
    }

    @Override
    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // yoksay
            }
        }
        socket = null;
        out = null;
    }
}
