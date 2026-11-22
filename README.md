# ELK Dockerize + Java Logger Boilerplate (Offline / RHEL 8-9)

İnternet erişimi **olmayan** bir RHEL 8/9 sunucusunda, üzerinde Docker bile kurulu değilken,
**tek bir paketle** Elasticsearch + Logstash + Kibana ortamını ayağa kaldırmak; ardından Java 11
projelerinden **SLF4J kullanır gibi** bu ortama log göndermek için hazır boilerplate.

```
 ┌──────────── Java 11 uygulamaları (birbirinden tamamen bağımsız) ───────────┐
 │  Proje A (order-service)   Proje B (payment-service)   Proje C ...          │
 │  elk-logger.jar            elk-logger.jar (+ slf4j)                         │
 │  elk.project.name=A        elk.project.name=B                               │
 └──────────┬─────────────────────────┬───────────────────────────────────────┘
            │ TCP 5000 (json_lines)    │ HTTP 8080 (JSON dizi)
            ▼                          ▼
 ┌─────────────────────── RHEL 8/9 sunucu (Docker, offline) ──────────────────┐
 │  Logstash ──► Elasticsearch ──► Kibana (5601)                               │
 │               applogs-a-2026.01.31   → Space "a" (sadece A'yı görür)        │
 │               applogs-b-2026.01.31   → Space "b" (sadece B'yi görür)        │
 └────────────────────────────────────────────────────────────────────────────┘
```

## İçerik

| Dizin | Ne var? |
|---|---|
| [`elk-stack/`](elk-stack) | `docker-compose.yml`, ES/Logstash/Kibana konfigürasyonları, otomatik kurulum (kullanıcı/rol/ILM/template/dashboard), `bin/elk.sh` yönetim aracı |
| [`offline-bundle/`](offline-bundle) | `build-bundle.sh` (internetli makinede paket üretir), `install.sh` (offline RHEL'e Docker + ELK kurar), `uninstall.sh` |
| [`libs/elk-logger/`](libs/elk-logger) | **Bağımlılıksız** Java 11 loglama kütüphanesi (tek jar) |
| [`libs/elk-logger-slf4j/`](libs/elk-logger-slf4j) | Opsiyonel SLF4J 2.x provider: mevcut SLF4J kodunu değiştirmeden ELK'ya loglama |
| [`examples/`](examples) | Örnek Proje A (native API, TCP) ve Örnek Proje B (SLF4J, HTTP) |
| [`dist/`](dist) | Hazır derlenmiş jar'lar |
| [`docs/`](docs) | Türkçe kapsamlı doküman seti |
| [`docs/ai/`](docs/ai) | Bu projeyi refactor edecek yapay zeka ajanları için rehber (İngilizce) |

## Hızlı Başlangıç (3 adım)

**1) İnternetli bir makinede paketi üret** (Docker veya Podman + JDK 11 + Maven gerekir):

```bash
./offline-bundle/build-bundle.sh
```

→ `dist/elk-offline-bundle-8.19.25-x86_64.tar.gz` (~1.6 GB). Bu dosyayı USB/scp ile offline sunucuya taşıyın.
(GitHub Actions'taki **"Offline Bundle"** workflow'u da aynı paketi artifact olarak üretir.)

**2) Offline RHEL sunucusunda kur ve başlat** (Docker yoksa kendisi kurar):

```bash
tar xzf elk-offline-bundle-8.19.25-x86_64.tar.gz
cd elk-offline-bundle-8.19.25-x86_64
sudo ./install.sh --open-firewall
```

Kurulum sonunda Kibana adresi ve `elastic` kullanıcısının şifresi (`/opt/elk-stack/.env`) gösterilir.

**3) Java projene ekle ve logla:**

```xml
<dependency>
  <groupId>io.github.elkboilerplate</groupId>
  <artifactId>elk-logger</artifactId>
  <version>1.0.0</version>
</dependency>
```

`src/main/resources/elk-logger.properties`:

```properties
elk.project.name=order-service      # -> index: applogs-order-service-YYYY.MM.dd
elk.logstash.host=10.0.0.5          # ELK sunucusu
elk.logstash.tcp.port=5000
elk.level=INFO
```

```java
private static final ElkLogger log = ElkLoggerFactory.getLogger(OrderService.class);

log.info("Sipariş oluşturuldu: {}", orderId);
log.error("Ödeme alınamadı, sipariş={}", orderId, exception);
log.atWarn().field("orderId", orderId).field("stock", 3).log("Stok azaldı");
```

Kibana → **Dashboards → "Uygulama Logları"**. Projeye özel alan + kullanıcı için:

```bash
sudo elk project create order-service --user order_viewer
```

## Dokümanlar

| # | Doküman | Konu |
|---|---|---|
| 00 | [Genel Bakış ve Mimari](docs/00-GENEL-BAKIS.md) | Bileşenler, veri akışı, tasarım kararları |
| 01 | [Offline Paket Hazırlama](docs/01-OFFLINE-PAKET-HAZIRLAMA.md) | İnternetli makinede paket üretimi, sürüm değiştirme |
| 02 | [RHEL Kurulum](docs/02-KURULUM-RHEL.md) | Offline Docker + ELK kurulumu, adım adım, elle kurulum |
| 03 | [Elasticsearch](docs/03-ELASTICSEARCH.md) | Index yapısı, mapping, sorgu/filtreleme örnekleri, ILM |
| 04 | [Logstash](docs/04-LOGSTASH.md) | Pipeline, input'lar, kalıcı kuyruk, DLQ |
| 05 | [Kibana](docs/05-KIBANA.md) | Discover/KQL filtreleme, dashboard, Space'ler, Stack Monitoring, alarmlar |
| 06 | [Java Kütüphanesi](docs/06-JAVA-KUTUPHANE.md) | API, MDC, yapısal alanlar, SLF4J köprüsü, projeye ekleme |
| 07 | [Konfigürasyon Referansı](docs/07-KONFIGURASYON-REFERANSI.md) | Tüm `elk.*` ayarları |
| 08 | [Örnek Projeler](docs/08-ORNEK-PROJELER.md) | Proje A / Proje B çalıştırma |
| 09 | [Operasyon ve Sorun Giderme](docs/09-OPERASYON-VE-SORUN-GIDERME.md) | Yedekleme, güncelleme, disk, kapasite, hata çözümleri |
| 10 | [Güvenlik](docs/10-GUVENLIK.md) | Kullanıcılar, roller, TLS, ağ |
| AI | [AI Refactor Rehberi](docs/ai/README.md) | Ajanlar için mimari harita, sözleşmeler, tarifler |

## Doğrulanmış Sürümler

| Bileşen | Sürüm |
|---|---|
| Elasticsearch / Logstash / Kibana | 8.19.25 |
| Docker (statik binary) | 29.10.0 |
| Docker Compose plugin | v5.7.1 |
| Java (kütüphane) | 11+ (bağımlılık yok) |
| SLF4J (opsiyonel) | 2.0.x |

## Lisans

MIT — bkz. [LICENSE](LICENSE).
