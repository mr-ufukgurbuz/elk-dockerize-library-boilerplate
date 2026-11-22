#!/usr/bin/env bash
# =====================================================================
#  ELK ortamini (ve istege bagli Docker'i) kaldirir.
#    sudo ./uninstall.sh                 container'lari siler, VERI KORUNUR
#    sudo ./uninstall.sh --purge-data    log verisi dahil her seyi siler
#    sudo ./uninstall.sh --remove-docker statik Docker kurulumunu da kaldirir
# =====================================================================
set -euo pipefail
PREFIX=/opt/elk-stack; PURGE=0; RM_DOCKER=0
while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX="$2"; shift 2 ;;
    --purge-data) PURGE=1; shift ;;
    --remove-docker) RM_DOCKER=1; shift ;;
    *) echo "Bilinmeyen secenek: $1"; exit 1 ;;
  esac
done
[ "$(id -u)" = 0 ] || { echo "root olarak calistirin"; exit 1; }

if [ -f "$PREFIX/docker-compose.yml" ] && command -v docker >/dev/null; then
  if [ "$PURGE" = 1 ]; then
    read -r -p "TUM LOG VERISI SILINECEK. Emin misiniz? (evet/hayir) " a; [ "$a" = evet ] || exit 1
    (cd "$PREFIX" && docker compose down -v)
  else
    (cd "$PREFIX" && docker compose down)
  fi
fi
rm -f /usr/local/bin/elk /etc/sysctl.d/99-elk.conf
[ "$PURGE" = 1 ] && rm -rf "$PREFIX"

if [ "$RM_DOCKER" = 1 ]; then
  systemctl disable --now docker 2>/dev/null || true
  rm -f /etc/systemd/system/docker.service /usr/bin/docker
  rm -f /usr/local/bin/{docker,dockerd,docker-init,docker-proxy,containerd,containerd-shim-runc-v2,ctr,runc}
  rm -rf /usr/local/lib/docker
  systemctl daemon-reload
  echo "Docker kaldirildi. /var/lib/docker (veya data-root) ve /etc/docker elle silinebilir."
fi
echo "Tamam."
