package io.github.elkboilerplate.logger.config;

/** Logstash'e gönderim yöntemi ({@code elk.transport}). */
public enum TransportType {
    /** Logstash TCP input, json_lines (varsayılan, en hızlı). Port: {@code elk.logstash.tcp.port}. */
    TCP,
    /** Logstash HTTP input, JSON dizi (her batch için HTTP 200 onayı alınır). Port: {@code elk.logstash.http.port}. */
    HTTP,
    /** Ağ gönderimi kapalı; yalnızca konsol / fallback dosyası. Test ve geliştirme için. */
    NONE
}
