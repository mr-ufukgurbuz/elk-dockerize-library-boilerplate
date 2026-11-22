# 06 — Java Kütüphanesi (elk-logger)

Java 11+ için, **hiçbir bağımlılığı olmayan** tek jar. SLF4J'ye bilinçli olarak benzer bir API sunar;
arka planda logları asenkron olarak Logstash'e gönderir. Uygulamanın ELK ortamını bilmesine gerek yoktur:
sadece `elk-logger.properties` dosyasında proje adı ve sunucu adresi verilir.

## 1. Projeye ekleme

Kütüphane **tek jar**dır ve hiçbir bağımlılığı yoktur; projeniz Maven, Gradle veya hiçbir build aracı kullanmayan
düz Java olabilir. Her durumda yapılacak iki şey aynıdır:

1. `elk-logger-1.0.0.jar` uygulamanın **classpath**'inde olmalı.
2. `elk-logger.properties` bulunabilir olmalı (classpath'te, çalışma dizininde veya `-Delk.config.file` ile).

| Proje tipi | Jar nasıl eklenir | Konfigürasyon nereye | Örnek |
|---|---|---|---|
| **Maven** | `<dependency>` (Nexus veya `~/.m2`) | `src/main/resources/elk-logger.properties` | [Proje A](../examples/order-service-sample), [B](../examples/payment-service-slf4j-sample) |
| **Gradle** | `implementation files('libs/elk-logger-1.0.0.jar')` veya koordinat | `src/main/resources/elk-logger.properties` | [Proje C](../examples/gradle-sample) |
| **Düz Java** (build aracı yok) | `java -cp "...:lib/elk-logger-1.0.0.jar:config"`, `CLASSPATH`, MANIFEST `Class-Path`, `java @application.classpath`, IDE | `config/` (classpath'e eklenen dizin) veya çalışma dizini | [Proje D](../examples/plain-java-sample) |
| **WAR** (Tomcat vb.) | `WEB-INF/lib/` | `WEB-INF/classes/` | — |

### 1.a Maven

Kurumsal Nexus/Artifactory varsa jar'ı bir kez depoya yükleyin:
```bash
mvn deploy:deploy-file -Dfile=elk-logger-1.0.0.jar -DgroupId=io.github.elkboilerplate \
  -DartifactId=elk-logger -Dversion=1.0.0 -Dpackaging=jar \
  -Durl=http://nexus.local/repository/maven-releases -DrepositoryId=nexus
```
Depo yoksa (offline makine) yerel `~/.m2`'ye kurun:
```bash
cd elk-offline-bundle-*/libs && ./install-to-local-maven-repo.sh
```
`pom.xml`:
```xml
<dependency>
  <groupId>io.github.elkboilerplate</groupId>
  <artifactId>elk-logger</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 1.b Gradle

Offline'da en kolayı jar'ı projeye koyup dosya bağımlılığı olarak eklemektir (Groovy DSL):
```groovy
dependencies {
    implementation files('libs/elk-logger-1.0.0.jar')
    // veya: implementation fileTree(dir: 'libs', include: ['*.jar'])
    // veya (mavenLocal()/Nexus ile): implementation 'io.github.elkboilerplate:elk-logger:1.0.0'
}
```
Kotlin DSL: `implementation(files("libs/elk-logger-1.0.0.jar"))`.
Gradle wrapper internetten Gradle indirdiği için offline makinede kurulu Gradle kullanın: `gradle --offline build`.
Detay ve `installDist` ile dağıtım: [examples/gradle-sample/README.md](../examples/gradle-sample/README.md).

### 1.c Düz Java — Maven/Gradle yok

Jar'ı `lib/`, konfigürasyonu `config/` dizinine koyun ve uygulamanın classpath'ine ekleyin. Hepsi
[Proje D](../examples/plain-java-sample)'de test edilmiş yöntemler (Windows'ta ayraç `;`):

```bash
# derleme
javac --release 11 -cp "lib/*" -d build/classes $(find src -name '*.java')

# 1) -cp ile (config/ dizini classpath'te -> elk-logger.properties bulunur)
java -cp "build/classes:lib/*:config" com.acme.Main

# 2) classpath bir dosyada (Java 9+ argüman dosyası) — application.classpath içeriği: -cp build/classes:lib/*:config
java @application.classpath com.acme.Main

# 3) CLASSPATH ortam değişkeni
export CLASSPATH="build/classes:lib/*:config"; java com.acme.Main

# 4) çalıştırılabilir jar: MANIFEST'te  Class-Path: lib/elk-logger-1.0.0.jar config/
java -jar app.jar

# 5) config classpath'te değil: kütüphane çalışma dizininde ./elk-logger.properties,
#    ./config/elk-logger.properties, ./conf/elk-logger.properties dosyalarını kendisi arar
java -cp "build/classes:lib/*" com.acme.Main

# 6) konfigürasyon başka yerde
java -Delk.config.file=/etc/app/elk-logger.properties -cp "build/classes:lib/*" com.acme.Main
```

> `java -jar` kullanıldığında `-cp` ve `CLASSPATH` yok sayılır; jar'lar MANIFEST `Class-Path` ile verilmelidir
> (yollar jar'ın bulunduğu dizine göre, dizinler `/` ile biter).

**IDE:** Eclipse → *Build Path → Add External Archives* (+ `config` için *Add Class Folder*); IntelliJ →
*Project Structure → Modules → Dependencies → + JARs or Directories*; NetBeans → *Properties → Libraries → Add JAR/Folder*.
Proje D'deki `.classpath`/`.project` Eclipse'te doğrudan açılır.

### 1.d WAR / uygulama sunucusu

`WEB-INF/lib/elk-logger-1.0.0.jar` ve `WEB-INF/classes/elk-logger.properties`. Aynı sunucudaki her uygulama
kendi jar'ını ve kendi `elk.project.name`'ini taşımalıdır (jar'ı sunucunun ortak `lib/` dizinine koymayın).

### 1.e SLF4J kullanan projeler (her proje tipi için)

Kod `org.slf4j.Logger` kullanıyorsa classpath'e üç jar: `slf4j-api-2.0.13.jar`, `elk-logger-slf4j-1.0.0.jar`,
`elk-logger-1.0.0.jar` (bkz. §5). Gradle: `implementation files('libs/slf4j-api-2.0.13.jar')`,
`runtimeOnly files('libs/elk-logger-slf4j-1.0.0.jar', 'libs/elk-logger-1.0.0.jar')`.
Düz Java: üçünü de `lib/`'e koyup `-cp "...:lib/*"`.

## 2. Konfigürasyon

En az şu iki satır:
```properties
elk.project.name=order-service
elk.logstash.host=10.0.0.5
```

Dosyanın arandığı yerler (sonraki öncekini ezer):

| # | Yer | Tipik kullanım |
|---|---|---|
| 1 | Classpath'te `elk-logger.properties` | Maven/Gradle `src/main/resources`; düz Java'da classpath'e eklenen `config/` dizini |
| 2 | Çalışma dizininde `elk-logger.properties`, `config/elk-logger.properties`, `conf/elk-logger.properties` (ilk bulunan) | Düz Java, script ile başlatılan uygulamalar |
| 3 | `-Delk.config.file=/yol/dosya.properties` veya `ELK_CONFIG_FILE` (`classpath:ozel.properties` biçimi de olur) | Prod'da uygulamadan ayrı konfig |
| 4 | `ELK_*` ortam değişkenleri, `-Delk.*` system property'leri | Tek tek değer ezme |

Tüm ayarlar: [07-KONFIGURASYON-REFERANSI](07-KONFIGURASYON-REFERANSI.md). Hangi dosyanın okunduğunu görmek için `-Delk.debug=true`.

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

Konsol çıktısı ve Kibana'daki karşılığı:

![Konsol](images/terminal-sample-app.png)

![Kibana doküman](images/kibana-discover-document.png)

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
