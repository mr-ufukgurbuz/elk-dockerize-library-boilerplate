#!/usr/bin/env bash
# =====================================================================
#  OFFLINE KURULUM — RHEL 8 / RHEL 9 (Rocky/Alma/Oracle Linux 8-9 de olur)
#  INTERNET GEREKMEZ. Paketin icinden calistirin:
#
#     tar xzf elk-offline-bundle-*.tar.gz && cd elk-offline-bundle-*/
#     sudo ./install.sh
#
#  Secenekler:
#    --prefix DIR            ELK ortaminin kurulacagi dizin (varsayilan /opt/elk-stack)
#    --docker-data-root DIR  Docker verisi (imajlar + log verisi) icin dizin (varsayilan /var/lib/docker)
#                            Loglar buyuyecegi icin genis bir diske yonlendirin, ornegin /data/docker
#    --es-heap 2g            Elasticsearch heap (varsayilan 1g; RAM'in yarisini gecmeyin)
#    --ls-heap 1g            Logstash heap (varsayilan 512m)
#    --retention-days 30     Loglarin saklanacagi gun
#    --open-firewall         firewalld calisiyorsa 5601,9200,5000,8080 portlarini acar
#    --skip-docker           Docker kurulumunu atla (zaten kuruluysa)
#    --no-start              Kur ama ortami baslatma
# =====================================================================
set -euo pipefail

BUNDLE_DIR="$(cd "$(dirname "$0")" && pwd)"
PREFIX=/opt/elk-stack
DATA_ROOT=""
ES_HEAP=""; LS_HEAP=""; RETENTION=""
OPEN_FW=0; SKIP_DOCKER=0; NO_START=0

while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX="$2"; shift 2 ;;
    --docker-data-root) DATA_ROOT="$2"; shift 2 ;;
    --es-heap) ES_HEAP="$2"; shift 2 ;;
    --ls-heap) LS_HEAP="$2"; shift 2 ;;
    --retention-days) RETENTION="$2"; shift 2 ;;
    --open-firewall) OPEN_FW=1; shift ;;
    --skip-docker) SKIP_DOCKER=1; shift ;;
    --no-start) NO_START=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "Bilinmeyen secenek: $1" >&2; exit 1 ;;
  esac
done

c_red=$'\e[31m'; c_grn=$'\e[32m'; c_ylw=$'\e[33m'; c_rst=$'\e[0m'
step() { echo; echo "${c_grn}==> $*${c_rst}"; }
warn() { echo "${c_ylw}UYARI: $*${c_rst}" >&2; }
die()  { echo "${c_red}HATA: $*${c_rst}" >&2; exit 1; }

[ "$(id -u)" = 0 ] || die "root olarak calistirin: sudo ./install.sh"
ELK_VERSION="$(cat "$BUNDLE_DIR/ELK_VERSION")"

# ------------------------------------------------------------ 0) On kontroller
step "On kontroller"
if [ -f /etc/os-release ]; then . /etc/os-release; echo "Isletim sistemi: ${PRETTY_NAME:-?}"; fi
case "${VERSION_ID:-}" in 8*|9*) ;; *) warn "RHEL 8/9 disinda bir sistem. Devam ediliyor ama test edilmedi." ;; esac
ARCH="$(uname -m)"
[[ "$BUNDLE_DIR" == *"$ARCH"* ]] || warn "Paket mimarisi ($BUNDLE_DIR) ile sunucu mimarisi ($ARCH) uyusmuyor olabilir."
MEM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
echo "RAM: ${MEM_MB} MB"
[ "$MEM_MB" -ge 3500 ] || warn "En az 4 GB RAM onerilir (ES 1g + Logstash 512m + Kibana ~1g + OS)."
for f in "$BUNDLE_DIR/images/elk-images-${ELK_VERSION}.tar" "$BUNDLE_DIR/elk-stack/docker-compose.yml"; do
  [ -f "$f" ] || die "Paket eksik: $f"
done
if [ -f "$BUNDLE_DIR/SHA256SUMS" ]; then
  echo "Paket butunlugu kontrol ediliyor (SHA256)..."
  (cd "$BUNDLE_DIR" && sha256sum --quiet -c SHA256SUMS) || die "SHA256 dogrulamasi basarisiz: paket bozuk kopyalanmis."
fi

# ------------------------------------------------------------ 1) Docker
install_docker() {
  step "Docker (statik binary) kuruluyor"
  local tgz; tgz=$(ls "$BUNDLE_DIR"/docker/docker-*.tgz 2>/dev/null | head -1)
  [ -n "$tgz" ] || die "docker/docker-*.tgz bulunamadi"
  if rpm -q podman-docker >/dev/null 2>&1; then
    die "'podman-docker' paketi kurulu (docker komutunu podman'a yonlendirir). Kaldirin: dnf remove -y podman-docker  (offline calisir)"
  fi
  local tmp; tmp=$(mktemp -d)
  tar xzf "$tgz" -C "$tmp"
  install -m 0755 "$tmp"/docker/* /usr/local/bin/
  rm -rf "$tmp"
  ln -sf /usr/local/bin/docker /usr/bin/docker   # sudo secure_path /usr/local/bin icermez
  mkdir -p /usr/local/lib/docker/cli-plugins
  install -m 0755 "$BUNDLE_DIR/docker/docker-compose" /usr/local/lib/docker/cli-plugins/docker-compose
  getent group docker >/dev/null || groupadd --system docker

  mkdir -p /etc/docker
  if [ ! -f /etc/docker/daemon.json ]; then
    cp "$BUNDLE_DIR/docker/daemon.json" /etc/docker/daemon.json
  fi
  if [ -n "$DATA_ROOT" ]; then
    mkdir -p "$DATA_ROOT"
    python3 - "$DATA_ROOT" <<'PY' 2>/dev/null || sed -i "s|^{|{\n  \"data-root\": \"$DATA_ROOT\",|" /etc/docker/daemon.json
import json,sys
p="/etc/docker/daemon.json"; d=json.load(open(p)); d["data-root"]=sys.argv[1]; json.dump(d,open(p,"w"),indent=2)
PY
  fi
  if ! command -v iptables >/dev/null 2>&1; then
    warn "iptables bulunamadi. Docker iptables olmadan calistirilacak (port yayinlama userland-proxy ile calisir)."
    warn "Mumkunse RHEL ISO/yerel repodan kurun: dnf install -y iptables-nft  ve sonra install.sh'i tekrar calistirin."
    grep -q '"iptables"' /etc/docker/daemon.json || sed -i 's|^{|{\n  "iptables": false,\n  "ip6tables": false,|' /etc/docker/daemon.json
  fi
  cp "$BUNDLE_DIR/docker/docker.service" /etc/systemd/system/docker.service
  command -v restorecon >/dev/null && restorecon -R /usr/local/bin /etc/systemd/system/docker.service /usr/local/lib/docker 2>/dev/null || true
  systemctl daemon-reload
  systemctl enable --now docker
  for i in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
  docker info >/dev/null 2>&1 || die "Docker baslamadi: journalctl -u docker -n 100"
  echo "Docker $(docker version --format '{{.Server.Version}}') ve Compose $(docker compose version --short) hazir."
}

if [ "$SKIP_DOCKER" = 1 ]; then
  step "Docker kurulumu atlandi (--skip-docker)"
elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  step "Docker zaten kurulu ve calisiyor: $(docker version --format '{{.Server.Version}}')"
else
  install_docker
fi
command -v docker >/dev/null || die "docker yok"

# ------------------------------------------------------------ 2) Kernel ayari
step "Kernel ayari: vm.max_map_count=262144 (Elasticsearch gereksinimi)"
echo "vm.max_map_count=262144" > /etc/sysctl.d/99-elk.conf
sysctl -q -w vm.max_map_count=262144 || warn "sysctl uygulanamadi"

# ------------------------------------------------------------ 3) Imajlar
step "Docker imajlari yukleniyor (internet kullanilmaz)"
docker load -i "$BUNDLE_DIR/images/elk-images-${ELK_VERSION}.tar"
for img in elasticsearch kibana logstash; do
  docker image inspect "$img:$ELK_VERSION" >/dev/null || die "Imaj yuklenemedi: $img:$ELK_VERSION"
done

# ------------------------------------------------------------ 4) Stack dosyalari
step "ELK ortami $PREFIX dizinine kopyalaniyor"
mkdir -p "$PREFIX"
if [ -f "$PREFIX/.env" ]; then
  cp "$PREFIX/.env" "/tmp/elk-env-backup-$$"
  echo "Mevcut .env korunuyor (sifreler degismez)."
fi
(cd "$BUNDLE_DIR/elk-stack" && tar cf - .) | (cd "$PREFIX" && tar xf -)
[ -f "/tmp/elk-env-backup-$$" ] && mv "/tmp/elk-env-backup-$$" "$PREFIX/.env"
chmod +x "$PREFIX"/bin/*.sh "$PREFIX"/setup/*.sh
"$PREFIX/bin/elk.sh" init-secrets
set_env() { grep -q "^$1=" "$PREFIX/.env" && sed -i "s|^$1=.*|$1=$2|" "$PREFIX/.env" || echo "$1=$2" >> "$PREFIX/.env"; }
set_env ELK_VERSION "$ELK_VERSION"
[ -n "$ES_HEAP" ] && set_env ES_HEAP "$ES_HEAP"
[ -n "$LS_HEAP" ] && set_env LS_HEAP "$LS_HEAP"
[ -n "$RETENTION" ] && set_env LOG_RETENTION_DAYS "$RETENTION"
IP=$(hostname -I 2>/dev/null | awk '{print $1}')
set_env KIBANA_PUBLIC_URL "http://${IP:-localhost}:$(grep '^KIBANA_PORT=' "$PREFIX/.env" | cut -d= -f2)"
# setup scriptlerinin container icinden okunabilmesi icin (uid 1000)
chmod -R o+rX "$PREFIX/setup" "$PREFIX/elasticsearch" "$PREFIX/logstash" "$PREFIX/kibana"
ln -sf "$PREFIX/bin/elk.sh" /usr/local/bin/elk

# ------------------------------------------------------------ 5) Firewall
if [ "$OPEN_FW" = 1 ] && systemctl is-active --quiet firewalld; then
  step "firewalld portlari aciliyor"
  set -a; . "$PREFIX/.env"; set +a
  for p in "$KIBANA_PORT" "$ES_PORT" "$LOGSTASH_TCP_PORT" "$LOGSTASH_HTTP_PORT"; do
    firewall-cmd --permanent --add-port="${p}/tcp" >/dev/null
  done
  firewall-cmd --reload >/dev/null
  echo "Acilan portlar: $KIBANA_PORT $ES_PORT $LOGSTASH_TCP_PORT $LOGSTASH_HTTP_PORT"
elif systemctl is-active --quiet firewalld 2>/dev/null; then
  warn "firewalld aktif. Diger sunuculardan erisim icin portlari acin (veya --open-firewall ile tekrar calistirin):"
  warn "  firewall-cmd --permanent --add-port={5601,9200,5000,8080}/tcp && firewall-cmd --reload"
fi

# ------------------------------------------------------------ 6) Baslat
if [ "$NO_START" = 1 ]; then
  step "Kurulum tamam. Baslatmak icin: sudo elk up"
  exit 0
fi
step "ELK ortami baslatiliyor (ilk acilis 2-5 dakika surebilir)"
"$PREFIX/bin/elk.sh" up 900
echo
echo "Yonetim komutu: sudo elk help        (kisayol: /usr/local/bin/elk -> $PREFIX/bin/elk.sh)"
echo "Sifreler      : $PREFIX/.env"
echo "Java kutuphanesi: $BUNDLE_DIR/libs/   Dokumanlar: $BUNDLE_DIR/docs/"
