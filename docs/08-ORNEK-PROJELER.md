# 08 — Örnek Projeler

İki bağımsız örnek, iki ayrı loglama alanına yazar ve kütüphanenin iki kullanım biçimini gösterir.

| | Proje A — `order-service-sample` | Proje B — `payment-service-slf4j-sample` |
|---|---|---|
| API | elk-logger **native** (`ElkLogger`) | **SLF4J** (`org.slf4j.Logger`) + `elk-logger-slf4j` |
| Transport | TCP 5000 | HTTP 8080 |
| Index | `applogs-order-service-*` | `applogs-payment-service-*` |
| Gösterilenler | seviyeler, `{}`, exception + cause, MDC (+thread havuzuna taşıma), yapısal alanlar, tag, paket bazlı seviye (`repository=DEBUG`), caller bilgisi, labels, fallback dosyası, istatistik | SLF4J MDC, Marker → tags, fluent `addKeyValue` → fields, exception |
| Konfig | [`elk-logger.properties`](../examples/order-service-sample/src/main/resources/elk-logger.properties) | [`elk-logger.properties`](../examples/payment-service-slf4j-sample/src/main/resources/elk-logger.properties) |

## 1. Çalıştırma

### Offline paketten (Maven gerekmez)
```bash
cd elk-offline-bundle-*/
./examples/run-sample.sh order 200 50       # 200 sipariş, 50 ms arayla
./examples/run-sample.sh payment 100 50
./examples/run-sample.sh order 0 200        # durdurana kadar (Ctrl+C) -> dashboard'u canlı izleyin
```
ELK başka sunucudaysa: `ELK_HOST=10.0.0.5 ./examples/run-sample.sh order`
(properties'te `elk.logstash.host=${ELK_HOST:localhost}` olduğu için).

![Örnek uygulama](images/terminal-sample-app.png)

### Repo içinden (Maven ile)
```bash
mvn -B install
mvn -B -pl examples/order-service-sample,examples/payment-service-slf4j-sample dependency:copy-dependencies -DoutputDirectory=target/lib
cd examples/order-service-sample
java -cp "target/order-service-sample-1.0.0.jar:target/lib/*" com.example.orders.OrderServiceApp 100 50
```

## 2. Sonuçları görme

1. Kibana → Dashboards → **Uygulama Logları (Tüm Projeler)** — iki proje birlikte.
2. Proje alanlarını açın:
   ```bash
   sudo elk project create order-service --user order_viewer --password OrderView123
   sudo elk project create payment-service --user payment_viewer --password PayView123
   ```
   `order_viewer` ile giriş yapınca sadece order-service loglarını görürsünüz.

   ![Proje dashboard](images/kibana-dashboard-project-space.png)
3. Discover'da deneyin:
   - `project : order-service and level : ERROR`
   - `fields.stock < 10`
   - `mdc.customerId : C1007`
   - `tags : AUDIT and fields.amount > 15000`
   - `exception.root_class : "java.net.SocketTimeoutException"`

Örnek bir olay (Proje A, WARN):
```json
{
  "@timestamp": "2026-11-22T20:32:55.917Z",
  "project": "order-service", "application": "Order Service", "environment": "dev",
  "hostname": "vm", "pid": 5898, "client_ip": "172.18.0.1", "transport": "tcp",
  "level": "WARN", "level_value": 30,
  "logger": "com.example.orders.OrderService", "thread": "pool-1-thread-4",
  "message": "Stok kritik seviyede: laptop için kalan 0",
  "message_template": "Stok kritik seviyede: {} için kalan {}",
  "labels": { "team": "siparis-ekibi", "region": "istanbul" },
  "mdc": { "requestId": "eee319ee", "customerId": "C1004" },
  "fields": { "product": "laptop", "stock": 0 },
  "tags": ["stock"],
  "caller": { "class": "com.example.orders.OrderService", "method": "placeOrder", "file": "OrderService.java", "line": 39 },
  "lib_version": "1.0.0"
}
```

## 3. Kendi projenize uyarlama

1. `elk-logger` bağımlılığını ekleyin ([06](06-JAVA-KUTUPHANE.md#1-projeye-ekleme)).
2. Örnek `elk-logger.properties`'i kopyalayıp `elk.project.name`'i değiştirin.
3. `ElkLoggerFactory.getLogger(...)` ile loglayın.
4. `sudo elk project create <proje>`.

## 4. Fallback denemesi

ELK'yı durdurun (`sudo elk stop`) ve Proje A'yı çalıştırın: uygulama yavaşlamaz, stderr'de dakikada bir
bağlantı hatası görünür; kapanışta gönderilemeyenler `logs/order-service-elk-fallback.log` dosyasına yazılır.
ELK'yı başlatıp (`sudo elk start`) dosyayı aktarın:
```bash
cat logs/order-service-elk-fallback.log > /dev/tcp/<elk-sunucu>/5000
```
