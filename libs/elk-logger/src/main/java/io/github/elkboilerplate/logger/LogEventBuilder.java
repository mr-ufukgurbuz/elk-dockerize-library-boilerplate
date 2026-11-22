package io.github.elkboilerplate.logger;

import java.util.Map;

/**
 * Yapısal log olayı oluşturucu. {@link ElkLogger#atInfo()} vb. ile elde edilir.
 * Seviye kapalıysa no-op bir örnek döner; zincirleme çağrılar maliyetsizdir.
 *
 * <pre>{@code
 * log.atInfo()
 *    .field("orderId", 42)
 *    .field("amount", 99.90)
 *    .tag("checkout")
 *    .log("Sipariş {} tamamlandı", 42);
 * }</pre>
 *
 * Alanlar Elasticsearch'te {@code fields.<ad>} altında saklanır ve filtrelenebilir.
 * Bir builder yalnızca bir kez {@code log(...)} edilmelidir ve thread'ler arasında paylaşılmamalıdır.
 */
public interface LogEventBuilder {

    /** {@code fields.<key>} olarak saklanacak yapısal alan. Değer: String, Number, Boolean, Map, Collection, dizi veya herhangi bir nesne (toString). */
    LogEventBuilder field(String key, Object value);

    /** Birden fazla alanı tek seferde ekler. */
    LogEventBuilder fields(Map<String, ?> fields);

    /** {@code tags} dizisine etiket ekler. */
    LogEventBuilder tag(String tag);

    /** Olaya exception bağlar. */
    LogEventBuilder cause(Throwable throwable);

    /** Olayı gönderir. */
    void log(String message);

    /** Olayı {@code {}} yer tutucularıyla biçimlendirip gönderir. */
    void log(String format, Object... args);
}
