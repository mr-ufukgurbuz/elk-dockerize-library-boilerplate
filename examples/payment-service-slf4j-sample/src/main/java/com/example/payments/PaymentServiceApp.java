package com.example.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * ÖRNEK PROJE B: Mevcut SLF4J kodunu HİÇ DEĞİŞTİRMEDEN ELK'ya loglama.
 * Kod yalnızca {@code org.slf4j.*} kullanır; classpath'teki {@code elk-logger-slf4j} provider'ı
 * logları ELK'ya yönlendirir.
 *
 * <p>Kullanım: {@code java -cp ... com.example.payments.PaymentServiceApp [islemSayisi] [beklemeMs]}</p>
 */
public final class PaymentServiceApp {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceApp.class);
    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    private PaymentServiceApp() {
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        long sleepMs = args.length > 1 ? Long.parseLong(args[1]) : 100;
        log.info("Payment Service başlıyor (SLF4J -> ELK)");

        for (int i = 1; count == 0 || i <= count; i++) {
            MDC.put("transactionId", UUID.randomUUID().toString().substring(0, 8));
            try {
                processPayment(i);
            } finally {
                MDC.clear();
            }
            if (sleepMs > 0) {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            }
        }
        log.info("Payment Service tamamlandı");
        // Shutdown hook kalan logları gönderir; ek bir şey yapmaya gerek yok.
    }

    private static void processPayment(int i) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double amount = Math.round(rnd.nextDouble(10, 20_000) * 100) / 100.0;
        String bank = rnd.nextBoolean() ? "BANKA-A" : "BANKA-B";

        log.debug("Ödeme işleniyor #{} banka={}", i, bank);

        // SLF4J 2 fluent API: addKeyValue -> Elasticsearch'te fields.amount, fields.bank
        log.atInfo()
                .addKeyValue("amount", amount)
                .addKeyValue("bank", bank)
                .addMarker(AUDIT)
                .log("Ödeme onaylandı: {} TL", amount);

        if (amount > 15_000) {
            log.warn("Yüksek tutarlı işlem, manuel kontrol gerekebilir: {} TL", amount);
        }
        if (rnd.nextInt(100) < 8) {
            try {
                throw new IllegalStateException("Fraud servisi zaman aşımı");
            } catch (IllegalStateException e) {
                log.error("Fraud kontrolü yapılamadı, işlem #{}", i, e);
            }
        }
    }
}
