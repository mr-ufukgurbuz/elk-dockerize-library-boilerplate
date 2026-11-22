package io.github.elkboilerplate.logger.internal;

import java.io.IOException;
import java.util.List;

/**
 * Logstash'e olay gönderen katman. Yalnızca dispatcher'ın tek worker thread'i tarafından çağrılır,
 * bu yüzden implementasyonların thread-safe olması gerekmez.
 *
 * <p>YENİ TRANSPORT EKLEMEK: bu arayüzü uygula, {@code TransportType}'a değer ekle,
 * {@link Transports#create} içinde eşle. Bkz. docs/ai/RECIPES.md.</p>
 */
public interface Transport extends AutoCloseable {

    /**
     * Batch'i gönderir. Başarılıysa normal döner; herhangi bir hata durumunda IOException fırlatmalıdır
     * (dispatcher batch'i bekletip yeniden dener).
     *
     * @param jsonEvents her biri tek satır JSON olan olaylar
     */
    void send(List<String> jsonEvents) throws IOException;

    /** Hedefin insan tarafından okunabilir tanımı (log mesajları için). */
    String describe();

    @Override
    void close();
}
