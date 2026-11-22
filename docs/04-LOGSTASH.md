# 04 — Logstash

## 1. Rolü

Uygulamalardan gelen olayları alır, normalize eder ve proje adına göre doğru Elasticsearch index'ine yazar.
Uygulamalar ES şifresini bilmez; yalnızca Logstash portlarını bilir.

Dosyalar:

| Dosya | İçerik |
|---|---|
| [`logstash/config/logstash.yml`](../elk-stack/logstash/config/logstash.yml) | Genel ayarlar: kalıcı kuyruk, DLQ, monitoring |
| [`logstash/config/pipelines.yml`](../elk-stack/logstash/config/pipelines.yml) | Pipeline listesi (worker, batch) |
| [`logstash/pipeline/applogs.conf`](../elk-stack/logstash/pipeline/applogs.conf) | Input → filter → output |

## 2. Pipeline: `applogs`

### Input'lar

| Input | Port | Codec | Kütüphane ayarı |
|---|---|---|---|
| `tcp` | 5000 | `json_lines` — her satır bir JSON olay | `elk.transport=tcp` |
| `http` | 8080 | `json` — gövde JSON dizi ise her eleman ayrı olay | `elk.transport=http` |

Her iki input olaya `transport` alanını ekler.

### Filter adımları

1. TCP input'un eklediği `host` (gönderen IP) → `client_ip`; HTTP için `@metadata` üzerinden `client_ip`.
2. `port`, `@version` alanları silinir.
3. JSON parse edilemeyen satırlar → `project=invalid` (index `applogs-invalid-*`), böylece kaybolmaz.
4. `project` yoksa `unknown`; küçük harf + `[^a-z0-9_-]` → `-` (kütüphaneyle aynı kural).
5. `level` yoksa `INFO`, büyük harfe çevrilir.
6. `[@metadata][index] = applogs-<project>`.

### Output

```
elasticsearch {
  index => "%{[@metadata][index]}-%{+YYYY.MM.dd}"   # applogs-<project>-YYYY.MM.dd (UTC, @timestamp'e göre)
  user  => "logstash_writer"                         # sadece applogs-*'a yazabilir
  action => "create"
  data_stream => "false", manage_template => false, ilm_enabled => false   # template/ILM setup'ta
}
```

## 3. Dayanıklılık

| Özellik | Ayar | Açıklama |
|---|---|---|
| Kalıcı kuyruk | `queue.type: persisted`, `queue.max_bytes: 1gb` | Logstash/ES yeniden başlasa da alınmış olaylar diskte (`ls-data` volume) bekler. ES kapalıyken Logstash 1 GB'a kadar tamponlar, sonra input'ları yavaşlatır (backpressure) → kütüphanenin kuyruğu dolmaya başlar. |
| Dead Letter Queue | `dead_letter_queue.enable: true` | ES'nin reddettiği olaylar (mapping çakışması vb.) `/usr/share/logstash/data/dead_letter_queue/applogs` altında saklanır. |
| Backpressure | otomatik | HTTP input 429 döner; kütüphane bunu hata sayıp yeniden dener. |

DLQ'yu incelemek:
```bash
docker exec elk-logstash ls -la /usr/share/logstash/data/dead_letter_queue/applogs
```
DLQ'yu yeniden işlemek için `dead_letter_queue` input'lu ikinci bir pipeline eklenebilir (bkz. §5).

## 4. İzleme

```bash
curl -s localhost:9600/_node/stats/pipelines/applogs?pretty   # in/out/filtered olay sayıları, kuyruk
curl -s localhost:9600/_node/hot_threads?human=true
sudo elk logs logstash
```
Kibana → **Stack Monitoring → Logstash**: olay hızı, gecikme, JVM, kuyruk (internal collection; 8.x'te
çalışır, "deprecated" uyarısı normaldir).

## 5. Değişiklik örnekleri

**HTTP input'a basic auth eklemek** (`applogs.conf`):
```
http { port => 8080 codec => json user => "applogs" password => "GucluSifre" ... }
```
Kütüphanede: `elk.logstash.http.username=applogs`, `elk.logstash.http.password=GucluSifre`.

**Belirli bir logger'ı hiç yazmamak:**
```
filter { if [logger] == "com.acme.NoisyHealthCheck" { drop {} } }
```

**Hassas veri maskeleme (ör. kart numarası):**
```
filter { mutate { gsub => [ "message", "\b(\d{6})\d{6}(\d{4})\b", "\1******\2" ] } }
```

**Yeni bir pipeline** (ör. syslog): `pipeline/syslog.conf` yazın, `pipelines.yml`'e ekleyin,
portu compose'a ekleyin, index adını `applogs-` **dışında** bir önekle verin (veya `applogs-syslog` projesi gibi).

Her değişiklikten sonra: `sudo elk restart logstash` ve `sudo elk logs logstash` ile hata kontrolü.
Sözdizimi testi:
```bash
docker exec elk-logstash bin/logstash --config.test_and_exit -f /usr/share/logstash/pipeline/applogs.conf --path.data /tmp/lstest
```

## 6. Performans

| Ayar | Varsayılan | Ne zaman artırılır |
|---|---|---|
| `LS_HEAP` (.env) | 512m | > 5.000 olay/sn veya büyük mesajlar |
| `pipeline.workers` | 2 | CPU çekirdeği sayısına kadar |
| `pipeline.batch.size` | 250 | Yüksek hacimde 500-1000 |

Tek Logstash, tipik log olaylarında (~1 KB) saniyede birkaç bin olayı rahatça işler.
