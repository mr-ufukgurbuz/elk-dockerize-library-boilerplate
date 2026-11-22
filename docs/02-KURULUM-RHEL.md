# 02 — RHEL 8 / 9 Offline Kurulum

Bu bölüm, paketi ([01](01-OFFLINE-PAKET-HAZIRLAMA.md)) offline sunucuya taşıdıktan sonra yapılacakları anlatır.
**Hiçbir adım internet gerektirmez.**

## 1. Otomatik kurulum (önerilen)

```bash
sudo mkdir -p /opt/install && sudo tar xzf elk-offline-bundle-8.19.25-x86_64.tar.gz -C /opt/install
cd /opt/install/elk-offline-bundle-8.19.25-x86_64
sudo ./install.sh --open-firewall
```

Sık kullanılan seçenekler:

```bash
# Logları büyük bir diske koy, ES'ye 4 GB heap ver, 60 gün sakla
sudo ./install.sh --docker-data-root /data/docker --es-heap 4g --retention-days 60 --open-firewall
```

| Seçenek | Açıklama |
|---|---|
| `--prefix DIR` | ELK ortam dosyaları (varsayılan `/opt/elk-stack`) |
| `--docker-data-root DIR` | Docker imaj + **log verisi** dizini (varsayılan `/var/lib/docker`). Sadece Docker ilk kez kurulurken uygulanır. |
| `--es-heap 2g` / `--ls-heap 1g` | JVM heap. ES heap'i RAM'in yarısını ve 31g'yi geçmemeli. |
| `--retention-days N` | Logların saklanma günü (ILM). |
| `--open-firewall` | firewalld'de 5601, 9200, 5000, 8080 portlarını açar. |
| `--skip-docker` | Docker kurulumunu atla. |
| `--no-start` | Kur ama başlatma. |

### install.sh ne yapar?

1. **Ön kontroller:** root, OS sürümü, RAM, paket bütünlüğü (SHA256).
2. **Docker kurulumu** (yoksa):
   - `docker-*.tgz` → `/usr/local/bin/` (dockerd, containerd, runc, docker-proxy...), `/usr/bin/docker` symlink
   - Compose plugin → `/usr/local/lib/docker/cli-plugins/docker-compose`
   - `/etc/systemd/system/docker.service`, `/etc/docker/daemon.json` (log rotasyonu, nofile limiti)
   - `docker` grubu, SELinux etiketleri (`restorecon`), `systemctl enable --now docker`
   - `iptables` yoksa Docker'ı `"iptables": false` ile yapılandırır (aşağıya bakın)
3. **Kernel:** `vm.max_map_count=262144` (`/etc/sysctl.d/99-elk.conf`, kalıcı).
4. **İmajlar:** `docker load -i images/elk-images-*.tar`.
5. **Ortam dosyaları:** `elk-stack/` → `/opt/elk-stack`, rastgele şifrelerle `.env` (chmod 600), mevcut `.env` varsa korunur.
6. **Kısayol:** `/usr/local/bin/elk` → `/opt/elk-stack/bin/elk.sh`.
7. **Firewall** (istenirse) ve **başlatma**: `elk up` → tüm servisler sağlıklı olana kadar bekler.

Sonuç ekranı:

```
=============================== ELK HAZIR ===============================
 Kibana         : http://10.0.0.5:5601     (kullanici: elastic  sifre: .env -> ELASTIC_PASSWORD)
 Elasticsearch  : http://10.0.0.5:9200
 Logstash TCP   : 10.0.0.5:5000   (elk.transport=tcp)
 Logstash HTTP  : http://10.0.0.5:8080  (elk.transport=http)
```

Şifre: `sudo grep ELASTIC_PASSWORD /opt/elk-stack/.env`

## 2. Kurulum sonrası doğrulama

```bash
sudo elk status                 # container'lar, cluster sağlığı, index listesi
sudo elk test-log demo          # Java'sız test olayı gönder
# 10 sn sonra:
sudo elk status | grep applogs-demo
```

Tarayıcıdan `http://<sunucu>:5601` → `elastic` / şifre → **Dashboards → Uygulama Logları (Tüm Projeler)**.

## 3. Servis yönetimi

| Komut | Açıklama |
|---|---|
| `sudo elk up` | Başlat ve hazır olmasını bekle |
| `sudo elk stop` / `sudo elk start` | Durdur / başlat (container'lar silinmez) |
| `sudo elk restart logstash` | Tek servisi yeniden başlat (konfig değişikliği sonrası) |
| `sudo elk down` | Container'ları sil (**veri korunur**) |
| `sudo elk logs kibana` | Container logları |
| `sudo elk destroy` | Container + **tüm veri** silinir |

**Sunucu yeniden başladığında** Docker `enabled` olduğu ve container'lar `restart: unless-stopped`
olduğu için ELK otomatik açılır (elle `stop` edilmediyse).

## 4. Elle kurulum (script kullanmadan)

Script'in yaptıklarını adım adım uygulamak isterseniz:

```bash
cd /opt/install/elk-offline-bundle-8.19.25-x86_64

# 4.1 Docker
sudo tar xzf docker/docker-29.10.0.tgz -C /tmp
sudo install -m 0755 /tmp/docker/* /usr/local/bin/
sudo ln -sf /usr/local/bin/docker /usr/bin/docker
sudo mkdir -p /usr/local/lib/docker/cli-plugins /etc/docker
sudo install -m 0755 docker/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
sudo cp docker/daemon.json /etc/docker/daemon.json
sudo cp docker/docker.service /etc/systemd/system/docker.service
sudo groupadd --system docker 2>/dev/null || true
sudo restorecon -R /usr/local/bin /usr/local/lib/docker 2>/dev/null || true
sudo systemctl daemon-reload && sudo systemctl enable --now docker
docker version && docker compose version

# 4.2 Kernel
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elk.conf
sudo sysctl -w vm.max_map_count=262144

# 4.3 İmajlar
sudo docker load -i images/elk-images-8.19.25.tar

# 4.4 Ortam
sudo cp -r elk-stack /opt/elk-stack
cd /opt/elk-stack
sudo ./bin/elk.sh init-secrets     # .env + rastgele şifreler
sudo vi .env                       # heap, port, saklama süresi
sudo ./bin/elk.sh up
```

Docker olmayan kullanıcının `docker` komutunu sudo'suz kullanması için: `sudo usermod -aG docker <kullanici>` (yeniden login).

## 5. RHEL'e özgü notlar

### SELinux
Statik Docker binary'leri `/usr/local/bin` altında `bin_t` etiketiyle, systemd'den `unconfined_service_t`
olarak çalışır; `container-selinux` politikası gerekmez. Compose dosyasındaki bind mount'lar `:z` ile
işaretlidir; Docker'ı RPM ile (container-selinux dahil) kurduysanız da çalışır. SELinux'u **kapatmanız gerekmez**.

### firewalld
Docker 20.10+ firewalld ile entegredir (`docker` zone'u oluşturur). Diğer makinelerin erişimi için:
```bash
sudo firewall-cmd --permanent --add-port={5601,5000,8080}/tcp   # 9200 sadece gerekirse
sudo firewall-cmd --reload
```

### iptables yoksa
RHEL 9 minimal kurulumlarda `iptables` komutu olmayabilir. `install.sh` bu durumda Docker'ı
`"iptables": false` ile başlatır: port yayınlama (userland proxy) ve container'lar arası iletişim çalışır,
container'ların **dışarıya** çıkışı çalışmaz (ELK için gerekmez). Tercih edilen: RHEL ISO'sundan veya yerel
repodan `sudo dnf install -y iptables-nft` kurup `/etc/docker/daemon.json`'dan iki satırı silmek ve
`sudo systemctl restart docker`.

### podman / podman-docker
RHEL'de `podman-docker` paketi `docker` komutunu podman'a yönlendirir ve çakışır. `install.sh` bunu tespit eder:
`sudo dnf remove -y podman-docker`. Podman'ın kendisi kalabilir.

### cgroups v1 (RHEL 8) / v2 (RHEL 9)
Docker her ikisini de destekler, ek ayar gerekmez. RHEL 8'de çok yeni bir Docker sürümüyle sorun yaşarsanız
paketi `--docker-version 27.5.1` gibi daha eski bir statik sürümle üretin.

### Proxy / kurumsal ayna
Gerekmez — tüm imajlar paketin içindedir ve compose `pull_policy: never` kullanır.

## 6. Portlar ve ağ

| Port | Servis | Kim erişir |
|---|---|---|
| 5601 | Kibana | Kullanıcı tarayıcıları |
| 5000/tcp | Logstash TCP | Java uygulamaları (`elk.transport=tcp`) |
| 8080/tcp | Logstash HTTP | Java uygulamaları (`elk.transport=http`) |
| 9200 | Elasticsearch API | Yöneticiler/araçlar (opsiyonel; kapatılabilir) |
| 9600 | Logstash API | İzleme (opsiyonel) |

Sadece yerel erişim için `.env` → `BIND_ADDRESS=127.0.0.1`. Tek tek portları kapatmak için ilgili
`ports:` satırını compose'dan kaldırın.

## 7. Kaldırma

```bash
sudo ./uninstall.sh                          # container'ları siler, veri kalır
sudo ./uninstall.sh --purge-data             # veri dahil
sudo ./uninstall.sh --purge-data --remove-docker
```
