package com.example.inventory;

import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.ElkLoggerFactory;
import io.github.elkboilerplate.logger.ElkMDC;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * ÖRNEK PROJE C: Gradle ile derlenen stok servisi simülasyonu.
 * Kullanım: {@code gradle run --args="[adet] [beklemeMs]"} (adet=0: Ctrl+C'ye kadar).
 */
public final class InventoryServiceApp {

    private static final ElkLogger log = ElkLoggerFactory.getLogger(InventoryServiceApp.class);
    private static final String[] WAREHOUSES = {"IST-01", "ANK-02", "IZM-03"};
    private static final String[] SKUS = {"SKU-LAPTOP", "SKU-PHONE", "SKU-MONITOR", "SKU-KEYBOARD"};

    private InventoryServiceApp() {
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        long sleepMs = args.length > 1 ? Long.parseLong(args[1]) : 100;
        log.info("Inventory Service (Gradle) başlıyor -> applogs-{}-*", ElkLoggerFactory.getConfig().getProjectName());

        for (int i = 1; count == 0 || i <= count; i++) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            String warehouse = WAREHOUSES[rnd.nextInt(WAREHOUSES.length)];
            String sku = SKUS[rnd.nextInt(SKUS.length)];
            int qty = rnd.nextInt(-5, 50);
            try (ElkMDC.MDCCloseable w = ElkMDC.putCloseable("warehouse", warehouse)) {
                if (qty < 0) {
                    log.atError().field("sku", sku).field("requested", -qty)
                            .cause(new IllegalStateException("Negatif stok: " + qty))
                            .log("Stok düşülemedi: {} deposunda {} yetersiz", warehouse, sku);
                } else if (qty < 5) {
                    log.atWarn().field("sku", sku).field("quantity", qty).tag("reorder")
                            .log("Yeniden sipariş eşiği: {} adet {}", qty, sku);
                } else {
                    log.atInfo().field("sku", sku).field("quantity", qty).log("Stok güncellendi: {} = {}", sku, qty);
                }
            }
            if (sleepMs > 0) {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            }
        }
        log.info("Inventory Service tamamlandı");
        ElkLoggerFactory.flush(10_000);
        System.out.println("ELK istatistikleri: " + ElkLoggerFactory.getStats());
    }
}
