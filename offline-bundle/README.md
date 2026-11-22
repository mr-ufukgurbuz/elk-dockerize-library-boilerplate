# offline-bundle — İnternetsiz Kurulum Paketi

İnternete erişimi olmayan RHEL 8/9 sunucusuna, Docker dahil hiçbir şey kurulu değilken ELK ortamını
kurmak için **tek dosyalık paket**.

## Hazır paketi indirin

Paket her yayında GitHub Actions tarafından üretilip **[Releases](https://github.com/mr-ufukgurbuz/elk-dockerize-library-boilerplate/releases)** sayfasına yüklenir:

| Dosya | Hedef |
|---|---|
| `elk-offline-bundle-8.19.25-x86_64.tar.gz` (+ `.sha256`) | Intel/AMD sunucular |
| `elk-offline-bundle-8.19.25-aarch64.tar.gz` (+ `.sha256`) | ARM sunucular |

> Paket ~1.6 GB olduğu için git deposunun içine konmaz (GitHub dosya başına 100 MB sınırı);
> Release asset olarak yayınlanır (2 GB sınırı).

## Kurulum

```bash
sha256sum -c elk-offline-bundle-8.19.25-x86_64.tar.gz.sha256
tar xzf elk-offline-bundle-8.19.25-x86_64.tar.gz
cd elk-offline-bundle-8.19.25-x86_64
sudo ./install.sh --open-firewall
```

Kurulum bitince:

![elk summary](../docs/images/terminal-elk-summary.png)

Seçenekler ve adım adım ne yaptığı: [docs/02-KURULUM-RHEL.md](../docs/02-KURULUM-RHEL.md).

## Paketi kendiniz üretmek

İnternetli bir makinede (Docker veya Podman + JDK 11 + Maven):

```bash
./offline-bundle/build-bundle.sh                 # x86_64
./offline-bundle/build-bundle.sh --arch aarch64  # ARM
```

Ya da GitHub'da yeni bir Release yayınlamak için [`RELEASE`](RELEASE) dosyasındaki `BUNDLE_TAG`'i
artırıp (ör. `bundle-8.19.25-r2`) push'layın; `.github/workflows/offline-bundle.yml` paketi üretip Release'e yükler.
Elle tetikleme: **Actions → Offline Bundle → Run workflow**.

Detay: [docs/01-OFFLINE-PAKET-HAZIRLAMA.md](../docs/01-OFFLINE-PAKET-HAZIRLAMA.md).

## Dosyalar

| Dosya | Görev |
|---|---|
| `build-bundle.sh` | Paketi üretir: statik Docker + Compose indirir, ELK imajlarını `docker save` eder, jar'ları derler, SHA256SUMS yazar |
| `install.sh` | Offline sunucuda: bütünlük kontrolü, Docker kurulumu (systemd), `vm.max_map_count`, `docker load`, `/opt/elk-stack`, firewall, başlatma |
| `uninstall.sh` | Kaldırma (`--purge-data`, `--remove-docker`) |
| `docker-files/` | `docker.service`, `daemon.json` |
| `RELEASE` | Yayınlanacak paketin etiketi ve sürümleri |
