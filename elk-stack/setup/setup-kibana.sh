#!/usr/bin/env bash
# =====================================================================
#  Tek seferlik Kibana kurulumu ('kibana-setup' servisi calistirir).
#  IDEMPOTENT. Default space'e su nesneleri import eder (overwrite):
#   - Data view  "applogs-*"  (Tum projeler)
#   - Kayitli aramalar, gorsellestirmeler ve "Uygulama Loglari" dashboard'u
#  Proje bazli space'ler icin: ./bin/elk.sh project create <proje>
# =====================================================================
set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://kibana:5601}"
AUTH="elastic:${ELASTIC_PASSWORD:?ELASTIC_PASSWORD tanimli degil}"
DIR="$(cd "$(dirname "$0")" && pwd)/kibana"

log() { echo "[setup-kibana] $*"; }

log "Kibana bekleniyor: $KIBANA_URL"
for i in $(seq 1 180); do
  if curl -s -u "$AUTH" "$KIBANA_URL/api/status" | grep -q '"level":"available"'; then break; fi
  sleep 2
  [ "$i" = 180 ] && { log "Kibana hazir olmadi"; exit 1; }
done

log "Saved objects import ediliyor (data view + dashboard)"
out="$(mktemp)"
code=$(curl -s -o "$out" -w '%{http_code}' -u "$AUTH" -X POST \
  "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' --form file=@"$DIR/applogs-saved-objects.ndjson")
cat "$out"; echo
if [ "$code" != "200" ] || ! grep -q '"success":true' "$out"; then
  log "HATA: import basarisiz (HTTP $code)"; exit 1
fi

log "Varsayilan data view ayarlaniyor"
curl -s -o /dev/null -u "$AUTH" -X POST "$KIBANA_URL/api/data_views/default" \
  -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"data_view_id":"applogs-all","force":true}' || true

log "TAMAMLANDI"
