package com.example.billing;

import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.ElkLoggerFactory;
import io.github.elkboilerplate.logger.ElkMDC;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * ÖRNEK PROJE D: Maven/Gradle KULLANMAYAN düz Java batch uygulaması.
 * elk-logger.jar classpath'e eklenir; konfigürasyon config/elk-logger.properties dosyasından okunur.
 * Çalıştırma yöntemleri için run.sh / run.bat ve README.md'ye bakın.
 */
public final class BillingBatchApp {

    private static final ElkLogger log = ElkLoggerFactory.getLogger(BillingBatchApp.class);

    private BillingBatchApp() {
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 25;
        long sleepMs = args.length > 1 ? Long.parseLong(args[1]) : 100;
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        try (ElkMDC.MDCCloseable b = ElkMDC.putCloseable("batchId", batchId)) {
            log.info("Fatura batch'i başladı ({} fatura) -> applogs-{}-*", count, ElkLoggerFactory.getConfig().getProjectName());
            InvoiceCalculator calculator = new InvoiceCalculator();
            int ok = 0;
            int failed = 0;
            for (int i = 1; count == 0 || i <= count; i++) {
                String invoiceNo = "INV-" + (20000 + i);
                try (ElkMDC.MDCCloseable inv = ElkMDC.putCloseable("invoiceNo", invoiceNo)) {
                    BigDecimal total = calculator.calculate(invoiceNo);
                    log.atInfo().field("total", total.doubleValue()).field("currency", "TRY")
                            .log("Fatura hesaplandı: {} = {} TL", invoiceNo, total);
                    ok++;
                } catch (InvoiceCalculator.TaxRateException e) {
                    log.error("Fatura hesaplanamadı: {}", invoiceNo, e);
                    failed++;
                }
                if (sleepMs > 0) {
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                }
            }
            log.atInfo().field("success", ok).field("failed", failed).tag("batch-summary")
                    .log("Batch bitti: {} başarılı, {} hatalı", ok, failed);
        }
        ElkLoggerFactory.flush(10_000);
        System.out.println("ELK istatistikleri: " + ElkLoggerFactory.getStats());
    }

    /** Basit iş mantığı. */
    static final class InvoiceCalculator {
        private static final ElkLogger log = ElkLoggerFactory.getLogger(InvoiceCalculator.class);

        BigDecimal calculate(String invoiceNo) throws TaxRateException {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            BigDecimal net = BigDecimal.valueOf(100 + rnd.nextDouble() * 9900).setScale(2, RoundingMode.HALF_UP);
            log.debug("Net tutar {} için {}", invoiceNo, net);
            if (rnd.nextInt(100) < 8) {
                throw new TaxRateException("KDV oranı bulunamadı: " + invoiceNo);
            }
            if (net.compareTo(BigDecimal.valueOf(9000)) > 0) {
                log.warn("Yüksek tutarlı fatura {}: {} TL (onay gerekir)", invoiceNo, net);
            }
            return net.multiply(BigDecimal.valueOf(1.20)).setScale(2, RoundingMode.HALF_UP);
        }

        /** Örnek checked exception. */
        static final class TaxRateException extends Exception {
            private static final long serialVersionUID = 1L;

            TaxRateException(String message) {
                super(message);
            }
        }
    }
}
