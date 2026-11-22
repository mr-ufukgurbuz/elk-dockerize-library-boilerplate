# 09 — Operasyon ve Sorun Giderme

## 1. Günlük kontroller

```bash
sudo elk status          # container'lar, cluster health (green/yellow normal), index listesi
df -h /var/lib/docker    # veya --docker-data-root dizini
```
Tek node'da replica olmadığı için cluster durumu **green**'dir; `.monitoring` vb. bazı sistem index'leri
nedeniyle **yellow** görülebilir — sorun değildir. **red** ise §5'e bakın.

## 2. Veri nerede?

| Veri | Docker volume | Yol (varsayılan) |
|---|---|---|
| Elasticsearch index'leri | `elk_es-data` | `/var/lib/docker/volumes/elk_es-data/_data` |
| Snapshot'lar | `elk_es-snapshots` | `/var/lib/docker/volumes/elk_es-snapshots/_data` |
| Logstash kalıcı kuyruk + DLQ | `elk_ls-data` | `/var/lib/docker/volumes/elk_ls-data/_data` |
| Kibana | `elk_kibana-data` | |
| Konfig + şifreler | — | `/opt/elk-stack` (`.env` dahil) |

## 3. Yedekleme ve geri yükleme

```bash
sudo elk snapshot create                 # applogs-* index'lerinin snapshot'ı (snap-YYYYMMDD-HHMMSS)
sudo elk snapshot list
sudo elk snapshot restore snap-20260131-020000   # restored-applogs-* adıyla geri yükler
```
Snapshot'ı sunucu dışına almak:
```bash
sudo tar czf /backup/es-snapshots-$(date +%F).tgz -C /var/lib/docker/volumes/elk_es-snapshots/_data .
sudo cp /opt/elk-stack/.env /backup/elk-env-$(date +%F)     # şifreler!
```
Otomatik günlük yedek (cron):
```bash
echo '0 2 * * * root /usr/local/bin/elk snapshot create >> /var/log/elk-snapshot.log 2>&1' | sudo tee /etc/cron.d/elk-snapshot
```
Kibana nesneleri için: [05 §7](05-KIBANA.md#7-saved-object-yedekleme--taşıma).

## 4. Kapasite planlama

Kabaca: **disk = günlük olay sayısı × ortalama olay boyutu (~0.5-1 KB, sıkıştırılmış) × saklama günü × 1.2**

Örnek: 10 uygulama × günde 2 milyon log × 0.7 KB × 30 gün × 1.2 ≈ **500 GB**.

| Hacim | ES heap | RAM | CPU |
|---|---|---|---|
| < 5 M olay/gün | 1-2g | 4-8 GB | 2-4 |
| 5-50 M olay/gün | 4-8g | 16 GB | 4-8 |
| > 50 M olay/gün | çok node'lu cluster düşünün | | |

Heap değiştirmek: `.env` → `ES_HEAP=4g` → `sudo elk up`.

## 5. Sorun giderme

### Elasticsearch başlamıyor
```bash
sudo elk logs elasticsearch
```
| Belirti | Çözüm |
|---|---|
| `max virtual memory areas vm.max_map_count [65530] is too low` | `sudo sysctl -w vm.max_map_count=262144` (install.sh kalıcı yapar) |
| `OutOfMemoryError` / container sürekli restart | `ES_HEAP`'i düşürün veya RAM ekleyin; `dmesg | grep -i oom` |
| `AccessDeniedException ... /usr/share/elasticsearch/data` | Volume yerine bind mount kullandıysanız: `chown -R 1000:0 <dizin>` |
| `flood stage disk watermark [95%] exceeded` → index'ler read-only | Disk açın (eski index sil / saklamayı kısalt), sonra: `curl -u elastic:$P -X PUT localhost:9200/applogs-*/_settings -H 'Content-Type: application/json' -d '{"index.blocks.read_only_allow_delete":null}'` |

### `setup` veya `kibana-setup` hata verdi
```bash
sudo elk logs setup
sudo elk logs kibana-setup
```
Genellikle `.env`'deki şifre ES volume'u ilk oluşturulduğundaki şifreden farklıdır (`ELASTIC_PASSWORD` sadece
**ilk** açılışta uygulanır). Çözüm: eski şifreyi `.env`'e geri yazın veya şifreyi sıfırlayın:
```bash
docker exec -it elk-elasticsearch bin/elasticsearch-reset-password -u elastic -i
```
ve yeni şifreyi `.env`'e yazıp `sudo elk up`.

### Kibana "Kibana server is not ready yet"
İlk açılışta 1-3 dk normaldir. Sürerse: `sudo elk logs kibana` — `kibana_system` kimlik hatası varsa `sudo elk up`
(setup şifreyi tekrar ayarlar).

### Uygulamanın logları görünmüyor
Kontrol listesi:
1. Uygulama stderr'inde `[elk-logger] ERROR Logstash'e gönderilemedi` var mı? → ağ/port/firewall.
   Uygulama makinesinden: `nc -vz <elk-sunucu> 5000` (veya `timeout 3 bash -c '</dev/tcp/<elk>/5000' && echo açık`).
2. `[elk-logger] WARN 'elk.project.name' tanımlı değil` → properties dosyası classpath'te değil.
3. `elk.debug=true` ile çalıştırıp etkin konfigürasyonu görün.
4. `ElkLoggerFactory.getStats()` → `sent` artıyor mu?
5. Sunucuda `sudo elk test-log demo` çalışıyor mu? → Logstash sağlam mı?
6. Kibana'da zaman aralığı doğru mu? (Sunucu/uygulama saatleri farklıysa loglar "gelecekte/geçmişte" kalabilir; `@timestamp` uygulama saatine göredir — NTP/chrony kullanın.)
7. `curl -s localhost:9600/_node/stats/pipelines/applogs?pretty` → `events.out` artıyor mu? Artıyorsa ES'ye yazılıyordur; `failures` varsa `sudo elk logs logstash`.
8. Mapping çakışması → olay DLQ'ya düşer (bkz. [04 §3](04-LOGSTASH.md#3-dayanıklılık)).

### Logstash `Connection refused` / `429`
ES kapalı/dolu → Logstash kuyruğu doluyor. ES sağlığını düzeltin; kütüphane otomatik yeniden dener.

### Docker başlamıyor (statik kurulum)
```bash
sudo journalctl -u docker -n 100 --no-pager
```
| Belirti | Çözüm |
|---|---|
| `iptables: command not found` / `failed to create NAT chain` | `dnf install iptables-nft` veya daemon.json'a `"iptables": false` (install.sh otomatik yapar) |
| `podman-docker` çakışması | `dnf remove podman-docker` |
| cgroup hataları (RHEL 8) | Daha eski statik Docker sürümüyle paket üretin (`--docker-version 27.5.1`) |
| `overlay2` / `xfs ftype=0` | `xfs_info / | grep ftype` → 0 ise Docker data-root'u ftype=1 bir diske taşıyın (`--docker-data-root`) |

## 6. Güncelleme

**ELK sürümü:** yeni paket üretip `sudo ./install.sh` ([01 §5](01-OFFLINE-PAKET-HAZIRLAMA.md#5-sürüm-değiştirme--yükseltme-paketi)). Öncesinde `sudo elk snapshot create`.

**Konfigürasyon değişikliği:**
| Değişen | Uygulama |
|---|---|
| `.env` (heap, port, saklama) | `sudo elk up` |
| `logstash/pipeline/*.conf` | `sudo elk restart logstash` |
| `elasticsearch.yml` | `sudo elk restart elasticsearch` |
| `kibana.yml` | `sudo elk restart kibana` |
| `setup/es/*.json` | `sudo elk up` (setup tekrar çalışır; template yeni index'lere — ertesi gün — uygulanır) |
| `setup/kibana/*.ndjson` | `sudo elk up` (kibana-setup tekrar çalışır) |

**Kütüphane:** yeni jar'ı uygulamalara dağıtın. Olay şeması geriye uyumlu tutulur (yeni alanlar eklenir, var olanlar değişmez).

## 7. Ölçekleme notları

- Daha fazla disk: Docker data-root'u büyük diske taşıyın (`systemctl stop docker`, `rsync -a /var/lib/docker/ /data/docker/`, daemon.json `data-root`, `systemctl start docker`).
- Daha fazla hacim: Logstash `pipeline.workers`, `LS_HEAP`; ES heap; `elk.batch.size`.
- Yüksek erişilebilirlik / çok node: bu boilerplate tek node içindir. Çok node için `discovery.type` kaldırılır,
  `discovery.seed_hosts`/`cluster.initial_master_nodes` tanımlanır, transport TLS zorunlu olur ve
  index template'te `number_of_replicas: 1` yapılır.
