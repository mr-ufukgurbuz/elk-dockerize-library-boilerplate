package com.example.orders;

import com.example.orders.repository.OrderRepository;
import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.ElkLoggerFactory;
import io.github.elkboilerplate.logger.ElkMDC;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ÖRNEK PROJE A: Sipariş servisi simülasyonu.
 *
 * <p>Kullanım: {@code java -jar order-service-sample.jar [siparisSayisi] [beklemeMs]}<br>
 * {@code siparisSayisi=0} verilirse durdurulana kadar (Ctrl+C) sürekli log üretir; Kibana dashboard'unu
 * canlı izlemek için idealdir.</p>
 *
 * <p>Gösterilen özellikler: seviye bazlı loglama, {} yer tutucular, exception loglama, MDC (requestId,
 * customerId), thread havuzuna MDC taşıma, yapısal alanlar (fields.*), etiketler (tags), istatistikler.</p>
 */
public final class OrderServiceApp {

    private static final ElkLogger log = ElkLoggerFactory.getLogger(OrderServiceApp.class);

    private OrderServiceApp() {
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        long sleepMs = args.length > 1 ? Long.parseLong(args[1]) : 100;

        log.info("Order Service başlıyor. Proje alanı: applogs-{}-*, hedef: {}:{}",
                ElkLoggerFactory.getConfig().getProjectName(),
                ElkLoggerFactory.getConfig().getHost(),
                ElkLoggerFactory.getConfig().getTcpPort());

        OrderService service = new OrderService(new OrderRepository());
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (int i = 1; count == 0 || i <= count; i++) {
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            String customerId = "C" + (1000 + (i % 25));
            // MDC: bu thread'deki tüm loglara mdc.requestId / mdc.customerId eklenir
            try (ElkMDC.MDCCloseable r = ElkMDC.putCloseable("requestId", requestId);
                 ElkMDC.MDCCloseable c = ElkMDC.putCloseable("customerId", customerId)) {
                log.debug("İstek alındı #{}", i);
                // ElkMDC.wrap: MDC'yi havuz thread'ine taşır
                pool.submit(ElkMDC.wrap(() -> service.placeOrder(customerId)));
            }
            if (sleepMs > 0) {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            }
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        log.info("Order Service tamamlandı.");

        // İsteğe bağlı: JVM kapanırken shutdown hook zaten gönderir; burada istatistik görmek için flush ediyoruz.
        ElkLoggerFactory.flush(10_000);
        System.out.println("ELK istatistikleri: " + ElkLoggerFactory.getStats());
    }
}
