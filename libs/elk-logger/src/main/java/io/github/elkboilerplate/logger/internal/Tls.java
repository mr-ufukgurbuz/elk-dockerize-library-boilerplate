package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.config.ElkConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

/** TLS yardımcıları. Truststore verilmemişse JVM'in varsayılan truststore'u kullanılır. */
final class Tls {

    private Tls() {
    }

    static SSLContext sslContext(ElkConfig config) {
        try {
            if (config.getTlsTruststorePath() == null) {
                return SSLContext.getDefault();
            }
            KeyStore ks = KeyStore.getInstance(config.getTlsTruststoreType());
            char[] pwd = config.getTlsTruststorePassword() == null ? null : config.getTlsTruststorePassword().toCharArray();
            try (InputStream in = Files.newInputStream(Paths.get(config.getTlsTruststorePath()))) {
                ks.load(in, pwd);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("TLS yapılandırılamadı (truststore=" + config.getTlsTruststorePath() + "): " + e, e);
        }
    }

    static SSLSocketFactory socketFactory(ElkConfig config) {
        return sslContext(config).getSocketFactory();
    }
}
