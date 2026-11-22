# 06 — Java Kütüphanesi (elk-logger)

Java 11+ için, **hiçbir bağımlılığı olmayan** tek jar. SLF4J'ye bilinçli olarak benzer bir API sunar;
arka planda logları asenkron olarak Logstash'e gönderir. Uygulamanın ELK ortamını bilmesine gerek yoktur:
sadece `elk-logger.properties` dosyasında proje adı ve sunucu adresi verilir.

## 1. Projeye ekleme

### 1.a Maven — kurumsal Nexus/Artifactory varsa
Jar'ı bir kez depoya yükleyin:
```bash
mvn deploy:deploy-file -Dfile=elk-logger-1.0.0.jar -DgroupId=io.github.elkboilerplate \
  -DartifactId=elk-logger -Dversion=1.0.0 -Dpackaging=jar \
  -Durl=http://nexus.local/repository/maven-releases -DrepositoryId=nexus
```

### 1.b Maven — depo yok (offline makine)
```bash
cd elk-offline-bundle-*/libs && ./install-to-local-maven-repo.sh     # ~/.m2'ye kurar
```

Ardından `pom.xml`:
```xml
<dependency>
  <groupId>io.github.elkboilerplate</groupId>
  <artifactId>elk-logger</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 1.c Gradle
```groovy
dependencies { implementation files('libs/elk-logger-1.0.0.jar') }   // veya mavenLocal() + koordinat
```

### 1.d Build aracı yok
```bash
javac -cp elk-logger-1.0.0.jar -d out $(find src -name '*.java')
java  -cp out:elk-logger-1.0.0.jar com.acme.Main
```

## 2. Konfigürasyon

`src/main/resources/elk-logger.properties` (en az):
```properties
elk.project.name=order-service
elk.logstash.host=10.0.0.5
```
Tüm ayarlar: [07-KONFIGURASYON-REFERANSI](07-KONFIGURASYON-REFERANSI.md). Çalışma zamanında farklı dosya:
`java -Delk.config.file=/etc/order-service/elk-logger.properties -jar app.jar`

## 3. API

### 3.1 Logger edinme ve temel kullanım
```java
import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.ElkLoggerFactory;

public class OrderService {
    private static final ElkLogger log = ElkLoggerFactory.getLogger(OrderService.class);

    void place(long orderId, String customer) {
        log.trace("detay");                                   // seviye kapalıysa maliyet ~0
        log.debug("Sipariş hazırlanıyor id={}", orderId);
        log.info("Sipariş {} müşteri {} için oluşturuldu", orderId, customer);
        log.warn("Stok az: {}", 3);
        try { ... } catch (Exception e) {
            log.error("Sipariş kaydedilemedi id={}", orderId, e); // son argüman Throwable -> exception.*
            log.error("Sade mesaj + exception", e);
        }
        if (log.isDebugEnabled()) {                           // pahalı hesaplama öncesi kontrol
            log.debug("Sepet: {}", cart.toDetailedString());
        }
    }
}
```

`{}` kuralları (SLF4J ile aynı):
- Argümanlar sırayla yerleştirilir; fazla `{}` olduğu gibi kalır.
- `\\{}` (Java string'inde) → literal `{}`.
- **Son argüman `Throwable` ise** ve karşılığında `{}` yoksa exception olarak işlenir (stack trace ES'de `exception.stacktrace`).
- Diziler `[1, 2, 3]` şeklinde yazılır; `toString()` hata verirse log bozulmaz.

### 3.2 Yapısal (structured) log — filtrelenebilir alanlar
```java
log.atInfo()
   .field("orderId", 42)              // sayı -> ES'de long, aralık sorgusu yapılabilir
   .field("amount", 1250.75)
   .field("product", "laptop")        // metin -> keyword (+ .text)
   .field("items", List.of("a", "b")) // dizi
   .fields(Map.of("channel", "web"))
   .tag("checkout")                   // tags dizisine
   .log("Sipariş tamamlandı: {}", 42);

log.atError().cause(ex).field("orderId", 42).log("Ödeme hatası");
```
Kibana'da: `fields.amount > 1000 and fields.product : laptop`. Seviye kapalıysa `atXxx()` no-op döner (allocation yok).

### 3.3 MDC — isteğe/işleme bağlı bağlam
```java
import io.github.elkboilerplate.logger.ElkMDC;

// Bir web isteğinin başında (filter/interceptor):
ElkMDC.put("requestId", requestId);
ElkMDC.put("userId", userId);
try {
    chain.doFilter(req, res);       // bu thread'deki tüm loglar mdc.requestId / mdc.userId içerir
} finally {
    ElkMDC.clear();                 // thread havuzlarında MUTLAKA temizleyin
}

// try-with-resources:
try (ElkMDC.MDCCloseable c = ElkMDC.putCloseable("jobId", "nightly-42")) {
    log.info("Job başladı");
}

// MDC'yi başka thread'e taşımak:
executor.submit(ElkMDC.wrap(() -> log.info("havuz thread'inde ama requestId var")));
CompletableFuture.supplyAsync(() -> ..., executor)   // -> ElkMDC.wrap(callable) kullanın
```

### 3.4 Yönetim API'si
```java
ElkLoggerFactory.setLevel("com.acme.dao", Level.DEBUG);  // çalışma zamanında seviye (ör. admin endpoint'inden)
ElkLoggerFactory.setLevel(null, Level.WARN);             // kök seviye
ElkStats s = ElkLoggerFactory.getStats();                // sent, dropped, queueSize, connected...
ElkLoggerFactory.flush(5000);                            // kuyruğun gönderilmesini bekle
ElkLoggerFactory.configure(props);                       // programatik (yeniden) yapılandırma
ElkLoggerFactory.shutdown();                             // manuel kapatma (normalde shutdown hook yapar)
```

Health-check örneği:
```java
ElkStats s = ElkLoggerFactory.getStats();
boolean healthy = s.isConnected() && s.getQueueSize() < 0.8 * ElkLoggerFactory.getConfig().getQueueCapacity();
```

## 4. Davranış garantileri

| Garanti | Açıklama |
|---|---|
| **Asla exception fırlatmaz** | Konfig hatası, ağ hatası, bozuk `toString()` — hiçbiri uygulamaya yansımaz. |
| **Uygulamayı bloklamaz** | Log çağrısı sadece kuyruğa ekler (µs). Ağ işlemi ayrı daemon thread'de (`elk-logger-dispatcher`). İstisna: `elk.overflow.policy=BLOCK`. |
| **ELK yokken çalışır** | Bağlantı hatası → üstel bekleme ile yeniden deneme; kuyruk dolarsa politika; istenirse fallback dosyası. ELK geri gelince kaldığı yerden gönderir. Hata mesajı stderr'e en fazla dakikada bir. |
| **Kapanışta boşaltma** | JVM shutdown hook kuyruğu `elk.shutdown.timeout.ms` kadar göndermeye çalışır; kalanlar fallback dosyasına. `kill -9`'da bellek içi kuyruk kaybolur. |
| **Sıralama** | Tek JVM içinde olaylar oluşturulma sırasıyla gönderilir. Kibana'da `@timestamp` ile sıralanır (ms hassasiyet). |
| **Teslim semantiği** | TCP: en az bir kez (bağlantı koparsa batch tekrar gönderilir → nadiren mükerrer). HTTP: Logstash 200 dönene kadar tekrar. |
| **Thread-safe** | Tüm API thread-safe; `LogEventBuilder` tek thread'de kullanılmalı. |

## 5. SLF4J ile kullanım (mevcut kodu değiştirmeden)

Projeniz zaten `org.slf4j.Logger` kullanıyorsa kod değişmez; sadece bağımlılıklar:

```xml
<dependency>
  <groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.13</version>
</dependency>
<dependency>
  <groupId>io.github.elkboilerplate</groupId><artifactId>elk-logger-slf4j</artifactId><version>1.0.0</version>
  <scope>runtime</scope>
</dependency>
```

- Classpath'te **başka SLF4J provider olmamalı** (logback-classic, slf4j-simple, slf4j-reload4j...). Varsa `<exclusions>` ile çıkarın.
- `org.slf4j.MDC` → `mdc.*`; `Marker` → `tags`; SLF4J 2 fluent `addKeyValue("k", v)` → `fields.k`.
- Konfigürasyon yine `elk-logger.properties` ile.
- Log4j/JUL kullanan üçüncü parti kütüphaneler için standart köprüler (`log4j-to-slf4j`, `jul-to-slf4j`) eklenebilir.
- SLF4J 1.7.x kullanan projeler: SLF4J 2.x API'si 1.7 ile kaynak uyumludur; `slf4j-api`'yi 2.0.x'e yükseltmeniz yeterli.

## 6. Ortamlara göre öneriler

| Ortam | Öneri |
|---|---|
| Geliştirici | `elk.transport=none` veya `localhost`, `elk.console.enabled=true`, `elk.level=DEBUG` |
| Test | ELK test sunucusu, `elk.environment=test` |
| Prod | `-Delk.config.file=/etc/app/elk-logger.properties`, `elk.console.enabled=false`, `elk.fallback.file.enabled=true` |
| Docker/K8s içindeki uygulama | `ELK_LOGSTASH_HOST`, `ELK_PROJECT_NAME` ortam değişkenleri |
| Web uygulaması (servlet) | Filter'da `ElkMDC.put("requestId", ...)` + `finally { ElkMDC.clear(); }` |

## 7. Performans

- Seviye kapalı log: bir volatile okuma + karşılaştırma (ns).
- Seviye açık log: mesaj biçimleme + JSON (~1-3 µs) + kuyruğa ekleme. Ağ işlemi asenkron.
- `elk.include.caller=true` her loga ~1-5 µs ekler.
- Bellek: kuyruk dolu iken ~`queue.capacity × ortalama olay boyutu` (10.000 × 1 KB ≈ 10 MB).

## 8. Kaynak kod haritası

```
io.github.elkboilerplate.logger
├── ElkLoggerFactory      giriş noktası, context yaşam döngüsü, shutdown hook
├── ElkLogger             API (interface, default metotlar)
├── LogEventBuilder       fluent/structured API
├── ElkMDC, Level, ElkStats
├── config/  ElkConfig (tüm anahtarlar), ConfigLoader (katmanlı yükleme), TransportType, OverflowPolicy
└── internal/
    ├── LoggerContext       konfigürasyonun çalışan hali; dispatch() ana yol
    ├── DefaultElkLogger    seviye önbelleği
    ├── MessageFormatter    {} biçimleme
    ├── EventSerializer     olay -> JSON (ŞEMA SÖZLEŞMESİ)
    ├── JsonWriter          bağımlılıksız JSON
    ├── AsyncDispatcher     kuyruk + worker + retry + fallback
    ├── TcpTransport / HttpTransport / Tls / Transports
    ├── ConsoleSink, FallbackFileSink, LevelResolver, InternalLog
```
Derleme: `mvn -B verify` (testlerle) veya Maven'sız `./libs/build-without-maven.sh`.
