# 01 — Offline Paket Hazırlama

Offline sunucuya taşınacak **tek dosyalık** paket, internete erişimi olan herhangi bir Linux/macOS
makinede (veya GitHub Actions'ta) üretilir.

## 1. Paketin içeriği

```
elk-offline-bundle-8.19.25-x86_64/
├── install.sh / uninstall.sh       # offline kurulum / kaldırma
├── ELK_VERSION
├── SHA256SUMS                      # install.sh bütünlük kontrolü yapar
├── docker/
│   ├── docker-29.10.0.tgz          # statik Docker binary'leri (dockerd, containerd, runc...)
│   ├── docker-compose              # compose v2 plugin
│   ├── docker.service              # systemd unit
│   └── daemon.json
├── images/elk-images-8.19.25.tar   # elasticsearch + logstash + kibana imajları (docker save)
├── elk-stack/                      # compose + konfig + scriptler
├── libs/                           # elk-logger jar'ları, slf4j-api, pom'lar, maven kurulum scripti
├── examples/                       # örnek projeler (kaynak + derlenmiş jar) + run-sample.sh
└── docs/
```

Boyut: ~1.6 GB (sıkıştırılmış).

## 2. Yöntem A — Kendi makinenizde

Gereksinimler: `bash`, `curl`, `tar`, `sha256sum`, **Docker veya Podman**, JDK 11+ ve (tercihen) Maven.

```bash
git clone <bu-repo> && cd elk-dockerize-library-boilerplate
./offline-bundle/build-bundle.sh
```

Çıktı: `dist/elk-offline-bundle-8.19.25-x86_64.tar.gz` ve `.sha256` dosyası.

Seçenekler:

| Seçenek | Varsayılan | Açıklama |
|---|---|---|
| `--elk-version` | `.env.example`'daki `ELK_VERSION` | Elastic Stack sürümü |
| `--docker-version` | `29.10.0` | https://download.docker.com/linux/static/stable/ altındaki sürümler |
| `--compose-version` | `v5.7.1` | https://github.com/docker/compose/releases |
| `--arch` | `x86_64` | `aarch64` (ARM sunucular) |
| `--output` | `dist/` | |
| `--skip-images` / `--skip-docker` | — | Sadece script/doküman güncellemesi için hafif paket |

> **Maven yoksa** script `libs/build-without-maven.sh` ile sadece `elk-logger.jar`'ı `javac` kullanarak üretir
> (SLF4J modülü ve Proje B örneği atlanır).

> **Docker Hub rate-limit (HTTP 429)**: script 3 kez dener; yine olmazsa yereldeki imajı kullanır.
> `docker login` yapmak limiti artırır.

## 3. Yöntem B — GitHub Actions

Repo GitHub'daysa: **Actions → Offline Bundle → Run workflow**. Sürüm ve mimari seçilir; bittiğinde
iş özetinden `elk-offline-bundle-*.tar.gz` artifact'ı indirilir (7 gün saklanır).

## 4. Paketi taşıma ve doğrulama

```bash
# internetli makinede
sha256sum -c elk-offline-bundle-8.19.25-x86_64.tar.gz.sha256
scp elk-offline-bundle-8.19.25-x86_64.tar.gz* user@offline-sunucu:/tmp/
# veya USB disk
```

Offline sunucuda tekrar `sha256sum -c ...` çalıştırın. `install.sh` ayrıca paket içindeki her dosyayı
`SHA256SUMS` ile doğrular.

## 5. Sürüm değiştirme / yükseltme paketi

1. `elk-stack/.env.example` içindeki `ELK_VERSION` değerini değiştirin (veya `--elk-version` verin).
2. Paketi yeniden üretin.
3. Offline sunucuda yeni paketle `sudo ./install.sh` çalıştırın: mevcut `.env` (şifreler) korunur,
   `ELK_VERSION` güncellenir, container'lar yeni imajla yeniden oluşturulur, veri volume'larda kalır.

> Elastic sürüm atlama kuralları: 8.x içinde ileri yükseltme serbesttir. **9.x'e geçiş** öncesi
> Kibana'daki *Upgrade Assistant* ile uyumsuzlukları kontrol edin ve snapshot alın
> (bkz. [09-OPERASYON](09-OPERASYON-VE-SORUN-GIDERME.md)). 9.x'te Logstash/ES "internal collection"
> monitoring kaldırılmıştır; monitoring için Metricbeat/Elastic Agent gerekir.

## 6. Sadece kütüphane/doküman güncellemesi

```bash
./offline-bundle/build-bundle.sh --skip-images --skip-docker
```

Bu hafif paketteki `libs/` ve `elk-stack/` dosyalarını sunucuya kopyalamanız yeterli
(imajlar zaten yüklü). `install.sh` bu pakette imaj tar'ı bulamayacağı için kullanılamaz;
dosyaları elle kopyalayıp `elk restart` yapın.
