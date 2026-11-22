package com.example.orders;

import com.example.orders.repository.OrderRepository;
import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.ElkLoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/** İş mantığı: farklı senaryolarda farklı seviyelerde log üretir. */
public final class OrderService {

    private static final ElkLogger log = ElkLoggerFactory.getLogger(OrderService.class);
    private static final String[] PRODUCTS = {"laptop", "telefon", "kulaklik", "monitor", "klavye"};

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public void placeOrder(String customerId) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String product = PRODUCTS[rnd.nextInt(PRODUCTS.length)];
        int quantity = 1 + rnd.nextInt(5);
        BigDecimal amount = BigDecimal.valueOf(50 + rnd.nextDouble() * 5000).setScale(2, RoundingMode.HALF_UP);
        long started = System.nanoTime();

        try {
            long orderId = repository.save(customerId, product, quantity);
            int stock = rnd.nextInt(100);
            if (stock < 10) {
                // Yapısal log: alanlar Kibana'da fields.product, fields.stock olarak filtrelenebilir
                log.atWarn()
                        .field("product", product)
                        .field("stock", stock)
                        .tag("stock")
                        .log("Stok kritik seviyede: {} için kalan {}", product, stock);
            }
            chargePayment(orderId, amount);
            long tookMs = (System.nanoTime() - started) / 1_000_000;
            log.atInfo()
                    .field("orderId", orderId)
                    .field("product", product)
                    .field("quantity", quantity)
                    .field("amount", amount.doubleValue())
                    .field("durationMs", tookMs)
                    .tag("order")
                    .log("Sipariş oluşturuldu: {} x{} tutar={} TL", product, quantity, amount);
        } catch (PaymentException e) {
            // Sondaki exception otomatik olarak exception.* alanlarına yazılır
            log.error("Ödeme başarısız, müşteri={} tutar={}", customerId, amount, e);
        } catch (RuntimeException e) {
            log.atError().cause(e).field("product", product).tag("unexpected").log("Beklenmeyen hata");
        }
    }

    private void chargePayment(long orderId, BigDecimal amount) throws PaymentException {
        int r = ThreadLocalRandom.current().nextInt(100);
        if (r < 7) {
            throw new PaymentException("Banka yanıt vermedi (orderId=" + orderId + ")",
                    new java.net.SocketTimeoutException("connect timed out"));
        }
        if (r < 9) {
            throw new IllegalStateException("Kart bilgisi geçersiz");
        }
        log.debug("Ödeme alındı orderId={} amount={}", orderId, amount);
    }

    /** Örnek checked exception. */
    public static final class PaymentException extends Exception {
        private static final long serialVersionUID = 1L;

        public PaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
