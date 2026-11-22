# 00 — Genel Bakış ve Mimari

## 1. Amaç

- İnternet erişimi olmayan, Docker dahil hiçbir altyapının kurulu olmadığı **RHEL 8/9** sunucusunda
  tek paketle merkezi log ortamı (Elasticsearch + Logstash + Kibana) kurmak.
- Java 11 projelerinin, ortamın detaylarını bilmeden, **SLF4J benzeri bir API** ile bu ortama log göndermesi.
- Her projenin **kendi loglama alanı** olması (A projesi → A alanı, B projesi → B alanı).
- Loglar üzerinde seviye, metin, sınıf, exception, MDC ve yapısal alanlarla **filtreleme**; Kibana ile **izleme**.

## 2. Bileşenler

| Bileşen | Görev | Nerede |
|---|---|---|
| **elk-logger** (Java jar) | Uygulama içinde log olaylarını JSON'a çevirir, asenkron olarak Logstash'e yollar | Her uygulamanın classpath'i |
| **elk-logger-slf4j** (opsiyonel) | SLF4J API çağrılarını elk-logger'a yönlendirir | SLF4J kullanan uygulamalar |
| **Logstash** | TCP 5000 / HTTP 8080'den olayları alır, normalize eder, proje bazlı index'e yazar | Docker container |
| **Elasticsearch** | Logları saklar, indeksler, arar | Docker container |
| **Kibana** | Web arayüzü: arama (Discover), dashboard, Space'ler, Stack Monitoring | Docker container |
| **setup / kibana-setup** | Tek seferlik: kullanıcı/rol/ILM/template, data view/dashboard kurulumu | Docker container (işini yapıp çıkar) |
| **bin/elk.sh** | Yönetim aracı: up/down/status/project/snapshot | Sunucu |
| **install.sh** | Offline Docker + ELK kurulumu | Sunucu |

## 3. Veri akışı

```
 Uygulama thread'i                    elk-logger (aynı JVM)                         Sunucu (Docker)
 ─────────────────                    ─────────────────────                         ───────────────
 log.info("x {}", 1) ──► seviye kontrolü (ucuz, önbellekli)
                         │ (seviye kapalıysa burada biter)
                         ▼
                         mesaj biçimleme + MDC kopyası + JSON
                         │
                         ├──► konsol (opsiyonel, senkron)
                         ▼
                         [bellek içi kuyruk, 10.000 olay] ── uygulama ASLA beklemez
                                         │
                                         ▼  worker thread, 200'lük batch'ler
                                  TCP json_lines ─────────────► Logstash :5000 ┐
                                  veya HTTP JSON dizi ────────► Logstash :8080 ┤
                                         │ hata → backoff + tekrar               │ filter: project sanitize,
                                         │ limit/kapanış → fallback dosyası      │ client_ip, transport
                                                                                 ▼
                                                              Elasticsearch: applogs-<project>-YYYY.MM.dd
                                                                                 │  (index template + ILM)
                                                                                 ▼
                                                              Kibana: data view applogs-* / applogs-<p>-*
```

## 4. "Her projenin kendi loglama alanı" nasıl sağlanıyor?

1. Uygulama `elk.project.name=order-service` ile yapılandırılır; her olayın `project` alanına yazılır.
2. Logstash olayı `applogs-order-service-2026.01.31` index'ine yazar (günlük index).
3. Kibana'da varsayılan Space'te tüm projeleri gören **"Uygulama Logları (Tüm Projeler)"** dashboard'u vardır.
4. `elk project create order-service` komutu:
   - `order-service` adında ayrı bir **Kibana Space** açar,
   - sadece `applogs-order-service-*` gören data view + dashboard kurar,
   - sadece bu index'leri okuyabilen `project_order-service_reader` **rolünü** oluşturur,
   - `--user` verilirse sadece bu projeyi görebilen **kullanıcı** açar.

Böylece projeler hem **veri seviyesinde** (ayrı index'ler, ayrı saklama/silme) hem de
**erişim seviyesinde** (rol + Space) birbirinden ayrılır.

## 5. Tasarım kararları ve gerekçeleri

| Karar | Gerekçe |
|---|---|
| Kütüphane **sıfır bağımlılık** | Offline ortamda tek jar kopyalamak yeterli; Jackson/Logback sürüm çakışması yok. JSON üretimi kütüphane içinde. |
| Logstash üzerinden gönderim (ES'ye doğrudan değil) | Uygulamalar ES şifresini bilmez; normalize/zenginleştirme tek yerde; Logstash kalıcı kuyruğu ES kesintilerini tamponlar. |
| TCP varsayılan, HTTP alternatif | TCP en düşük gecikme/maliyet. HTTP her batch için onay verir (log kaybına daha az tolerans). |
| Asenkron kuyruk + overflow politikası | Loglama, ELK çökse bile uygulamayı yavaşlatmamalı/çökertmemeli. |
| Günlük index + ILM ile silme | Basit, anlaşılır; eski günleri silmek ucuz; proje bazlı silme kolay. |
| Docker statik binary ile kurulum | RHEL 8 ve 9'da RPM bağımlılığı (container-selinux, vb.) olmadan aynı paket çalışır. |
| ES güvenliği açık, HTTP TLS kapalı | Kullanıcı/rol izolasyonu için güvenlik şart; kapalı ağda TLS opsiyonel (bkz. 10-GUVENLIK). |
| Tek node | Hedef: tek sunucu. Replica 0. Ölçekleme notları 09-OPERASYON'da. |

## 6. Dizin yapısı

```
├── elk-stack/                    # Sunucuya kopyalanan ortam (install.sh -> /opt/elk-stack)
│   ├── docker-compose.yml
│   ├── .env.example              # sürüm, portlar, heap, saklama süresi, şifreler
│   ├── bin/elk.sh                # yönetim aracı
│   ├── elasticsearch/elasticsearch.yml
│   ├── logstash/{config,pipeline}/
│   ├── kibana/kibana.yml
│   └── setup/
│       ├── setup-elasticsearch.sh, es/*.json        # roller, ILM, index template
│       └── setup-kibana.sh, kibana/*.ndjson|.py     # data view, dashboard
├── offline-bundle/               # build-bundle.sh, install.sh, uninstall.sh, docker-files/
├── libs/elk-logger/              # Java kütüphanesi
├── libs/elk-logger-slf4j/        # SLF4J köprüsü
├── examples/                     # Örnek Proje A ve B
├── tests/e2e.sh                  # uçtan uca test
├── dist/                         # hazır jar'lar
└── docs/                         # bu dokümanlar (+ docs/ai)
```

## 7. Sistem gereksinimleri (sunucu)

| Kaynak | En az | Önerilen |
|---|---|---|
| CPU | 2 çekirdek | 4+ |
| RAM | 4 GB | 8 GB+ (ES heap 2-4g) |
| Disk | 20 GB | Günlük log hacmi × saklama günü × ~1.2 + 10 GB |
| OS | RHEL 8.x / 9.x (x86_64 veya aarch64) | |
| Ağ portları | 5601 (Kibana), 5000 & 8080 (uygulamalardan), 9200 (opsiyonel, API) | |
