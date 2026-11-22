# 07 — Konfigürasyon Referansı (Java Kütüphanesi)

Kütüphanenin **tüm** davranışı `elk.*` anahtarlarıyla yönetilir. Kopyalanabilir tam şablon:
[`libs/elk-logger/elk-logger.properties.example`](../libs/elk-logger/elk-logger.properties.example).

## 1. Konfigürasyon nereden okunur? (öncelik sırası)

Sonraki katman öncekini ezer:

| # | Kaynak | Örnek |
|---|---|---|
| 1 | Kütüphane varsayılanları | — |
| 2 | Classpath'teki `elk-logger.properties` | Maven/Gradle: `src/main/resources/`; düz Java: classpath'e eklenen dizin (`-cp "...:config"`) |
| 3 | Çalışma dizini (ilk bulunan) | `./elk-logger.properties`, `./config/elk-logger.properties`, `./conf/elk-logger.properties` — build aracı olmayan projeler için |
| 4 | Açıkça verilen dosya | `-Delk.config.file=/etc/myapp/elk-logger.properties`, ortam değişkeni `ELK_CONFIG_FILE`, veya `-Delk.config.file=classpath:ozel.properties` |
| 5 | Ortam değişkenleri `ELK_*` | `ELK_LOGSTASH_HOST=10.0.0.5` → `elk.logstash.host` (büyük harf, `_` → `.`) |
| 6 | JVM system property `elk.*` | `-Delk.level=DEBUG` |
| 7 | Kod | `ElkLoggerFactory.configure(props)` |

**Placeholder:** Değerlerde `${AD}` veya `${AD:varsayılan}` kullanılabilir. Önce system property, sonra ortam değişkeni aranır:

```properties
elk.environment=${APP_ENV:dev}
elk.logstash.host=${ELK_HOST:localhost}
```

> Tipik kullanım: jar içinde geliştirme varsayılanları (`localhost`), prod'da
> `-Delk.config.file=/etc/app/elk-logger.properties` veya sadece `ELK_LOGSTASH_HOST` ortam değişkeni.

Hangi dosyaların okunduğunu görmek için `-Delk.debug=true` (veya `ELK_DEBUG=true`): `[elk-logger] DEBUG konfigürasyon okundu: ...`.

Geçersiz bir değer (ör. port=`abc`) **uygulamayı durdurmaz**; varsayılana döner. `elk.debug=true` ile
etkin konfigürasyon stderr'e yazdırılabilir. Kodda: `ElkLoggerFactory.getConfig()`.

## 2. Tüm anahtarlar

### Kimlik

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.project.name` | `unknown` | **Zorunlu.** Loglama alanı. Index adı `applogs-<proje>-YYYY.MM.dd`. Küçük harfe çevrilir, `[a-z0-9_-]` dışı karakterler `-` olur (`Order Service` → `order-service`). Tanımlanmazsa uyarı basılır ve `applogs-unknown-*`'a yazılır. |
| `elk.application.name` | proje adının ham hali | `application` alanı. Aynı projede birden fazla uygulama (ör. api + batch) varsa ayırt etmek için. |
| `elk.environment` | `default` | `environment` alanı (dev/test/prod). Kibana'da filtrelemek için. |
| `elk.hostname` | otomatik | `hostname` alanı. Boşsa `HOSTNAME`/`COMPUTERNAME` env veya `InetAddress.getLocalHost()`. |

### Bağlantı

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.enabled` | `true` | `false` → ağa hiçbir şey gönderilmez (konsol çalışmaya devam eder). |
| `elk.transport` | `tcp` | `tcp`: Logstash TCP json_lines (hızlı, onaysız). `http`: Logstash HTTP input (her batch HTTP 200 ile onaylanır). `none`: sadece konsol/fallback. |
| `elk.logstash.host` | `localhost` | ELK sunucusunun adresi. |
| `elk.logstash.tcp.port` | `5000` | TCP input portu (`.env` → `LOGSTASH_TCP_PORT`). |
| `elk.logstash.http.port` | `8080` | HTTP input portu (`.env` → `LOGSTASH_HTTP_PORT`). |
| `elk.logstash.http.path` | `/` | HTTP yolu. |
| `elk.logstash.http.username` / `.password` | — | Logstash HTTP input'unda basic auth açıldıysa. |
| `elk.connect.timeout.ms` | `3000` | Bağlantı kurma zaman aşımı. |
| `elk.request.timeout.ms` | `10000` | HTTP istek zaman aşımı / TCP soket timeout. |

### TLS

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.tls.enabled` | `false` | Logstash input'unda SSL açıldıysa `true` (TCP ve HTTP için). Bkz. [10-GUVENLIK](10-GUVENLIK.md). |
| `elk.tls.truststore.path` | — | Logstash sertifikasını imzalayan CA'yı içeren truststore. Boşsa JVM varsayılan truststore'u. |
| `elk.tls.truststore.password` | — | |
| `elk.tls.truststore.type` | `PKCS12` | `PKCS12` veya `JKS`. |
| `elk.tls.verify.hostname` | `true` | Sertifikadaki host adı doğrulaması. |

### Seviyeler

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.level` | `INFO` | Kök seviye: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`. (`WARNING`, `FATAL`, `SEVERE` de kabul edilir.) |
| `elk.level.<önek>` | — | Paket/sınıf bazlı seviye. En uzun eşleşen önek kazanır. Örn. `elk.level.com.acme.dao=DEBUG`, `elk.level.com.acme.dao.AuditDao=OFF`. |

Çalışma zamanında: `ElkLoggerFactory.setLevel("com.acme.dao", Level.DEBUG)`; kökü değiştirmek için önek `null`.

### Asenkron gönderim ve dayanıklılık

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.queue.capacity` | `10000` | Bellek içi kuyruk (olay sayısı). Logstash erişilemezken burada birikir. |
| `elk.batch.size` | `200` | Tek gönderimdeki en fazla olay. |
| `elk.flush.interval.ms` | `1000` | Kuyruk boşken bekleme süresi (gecikmenin üst sınırı ~ bu değer). |
| `elk.overflow.policy` | `DROP_NEWEST` | Kuyruk doluysa: `DROP_NEWEST` (yeni olay atılır), `DROP_OLDEST` (en eski atılır), `BLOCK` (uygulama thread'i bekler). |
| `elk.overflow.block.timeout.ms` | `1000` | `BLOCK` politikasında en fazla bekleme; sonra olay atılır. |
| `elk.retry.initial.backoff.ms` | `500` | Başarısız gönderimden sonra ilk bekleme. Her denemede 2 katına çıkar. |
| `elk.retry.max.backoff.ms` | `30000` | Bekleme üst sınırı. |
| `elk.retry.max.attempts` | `0` | `0` = sınırsız (batch kuyrukta bekler). `>0` ise bu kadar denemeden sonra batch fallback dosyasına yazılır (yoksa atılır). |
| `elk.shutdown.timeout.ms` | `5000` | JVM kapanırken kuyruğu boşaltmak için beklenecek süre. Kalanlar fallback dosyasına. |

Atılan (kaybolan) olaylar `ElkLoggerFactory.getStats().getDropped()` ile izlenebilir.

### Konsol

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.console.enabled` | `true` | Olayları okunabilir satır olarak konsola da yaz. Prod'da `false` yapılabilir. |
| `elk.console.level` | `TRACE` | Konsol için ek eşik (logger seviyesini geçen olaylardan bu seviye ve üstü yazılır). |
| `elk.console.stream` | `stdout` | `stdout` veya `stderr`. |

### Fallback dosyası

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.fallback.file.enabled` | `false` | Gönderilemeyen / kuyruktan taşan olayları JSON-lines olarak dosyaya yaz. |
| `elk.fallback.file.path` | `logs/elk-fallback.log` | Dosya yolu (çalışma dizinine göre). |
| `elk.fallback.file.max.size.mb` | `100` | Aşılırsa dosya `.1` uzantısıyla döndürülür (tek yedek). |

Dosya sonradan ELK'ya aktarılabilir: `nc <elk-sunucu> 5000 < logs/elk-fallback.log`
(veya `cat ... > /dev/tcp/<elk-sunucu>/5000`).

### İçerik

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.include.mdc` | `true` | MDC içeriğini `mdc.*` alanlarına yaz. |
| `elk.include.caller` | `false` | `caller.class/method/file/line` ekle. Her logda stack taraması yapar (~1-5 µs); yoğun loglamada maliyetli. |
| `elk.message.max.length` | `32000` | Mesaj bu uzunlukta kesilir. |
| `elk.stacktrace.max.length` | `32000` | Stack trace bu uzunlukta kesilir. |
| `elk.labels.<ad>` | — | Her loga eklenen sabit etiket: `elk.labels.team=payments` → `labels.team`. |

### Teşhis

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `elk.debug` | `false` | Kütüphanenin kendi DEBUG mesajları (bağlantı kuruldu, etkin konfig...) stderr'e. Hata mesajları `debug=false` iken de basılır (60 sn'de bir tekrar sınırlı). |

## 3. Senaryolara göre hazır ayarlar

**Geliştirici makinesi (ELK yok):**
```properties
elk.project.name=my-app
elk.transport=none
elk.level=DEBUG
```

**Prod, log kaybı istenmiyor:**
```properties
elk.transport=http
elk.queue.capacity=50000
elk.retry.max.attempts=10
elk.fallback.file.enabled=true
elk.fallback.file.path=/var/log/my-app/elk-fallback.log
elk.console.enabled=false
```

**Çok yüksek hacim (performans öncelikli):**
```properties
elk.transport=tcp
elk.batch.size=1000
elk.queue.capacity=100000
elk.overflow.policy=DROP_OLDEST
elk.include.caller=false
```

**Her şeyi ortam değişkeniyle (container/k8s):**
```bash
ELK_PROJECT_NAME=my-app ELK_LOGSTASH_HOST=10.0.0.5 ELK_LEVEL=WARN java -jar app.jar
```
