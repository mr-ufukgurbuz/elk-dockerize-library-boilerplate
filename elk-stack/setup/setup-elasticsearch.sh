#!/usr/bin/env bash
# =====================================================================
#  Tek seferlik Elasticsearch kurulumu ('setup' servisi calistirir).
#  IDEMPOTENT: her 'docker compose up' calismasinda guvenle tekrar calisir.
#   1) kibana_system / logstash_system sifrelerini ayarlar
#   2) applogs_writer rolu + logstash_writer kullanicisi
#   3) applogs_reader_all rolu
#   4) ILM politikasi (LOG_RETENTION_DAYS)
#   5) applogs-* index template
# =====================================================================
set -euo pipefail

ES_URL="${ES_URL:-http://elasticsearch:9200}"
AUTH="elastic:${ELASTIC_PASSWORD:?ELASTIC_PASSWORD tanimli degil}"
DIR="$(cd "$(dirname "$0")" && pwd)/es"
RETENTION="${LOG_RETENTION_DAYS:-30}"

log() { echo "[setup-es] $*"; }

# $1=method $2=path $3=json-body (string) -> HTTP 2xx degilse hata
es() {
  local method="$1" path="$2" body="${3:-}"
  local out code
  out="$(mktemp)"
  if [ -n "$body" ]; then
    code=$(curl -s -o "$out" -w '%{http_code}' -u "$AUTH" -X "$method" "$ES_URL$path" \
      -H 'Content-Type: application/json' --data-binary "$body")
  else
    code=$(curl -s -o "$out" -w '%{http_code}' -u "$AUTH" -X "$method" "$ES_URL$path")
  fi
  if [ "${code:0:1}" != "2" ]; then
    log "HATA: $method $path -> HTTP $code"; cat "$out"; echo; rm -f "$out"; return 1
  fi
  rm -f "$out"
}

log "Elasticsearch bekleniyor: $ES_URL"
for i in $(seq 1 120); do
  if curl -s -u "$AUTH" "$ES_URL/_cluster/health" | grep -Eq '"status":"(green|yellow)"'; then break; fi
  sleep 2
  [ "$i" = 120 ] && { log "Elasticsearch hazir olmadi"; exit 1; }
done

log "kibana_system sifresi ayarlaniyor"
es POST /_security/user/kibana_system/_password "{\"password\":\"${KIBANA_SYSTEM_PASSWORD:?}\"}"

log "logstash_system sifresi ayarlaniyor"
es POST /_security/user/logstash_system/_password "{\"password\":\"${LOGSTASH_SYSTEM_PASSWORD:?}\"}"

log "applogs_writer rolu"
es PUT /_security/role/applogs_writer "$(cat "$DIR/role-applogs-writer.json")"

log "logstash_writer kullanicisi"
es PUT /_security/user/logstash_writer "{\"password\":\"${LOGSTASH_WRITER_PASSWORD:?}\",\"roles\":[\"applogs_writer\"],\"full_name\":\"Logstash applogs writer\"}"

log "applogs_reader_all rolu"
es PUT /_security/role/applogs_reader_all "$(cat "$DIR/role-applogs-reader-all.json")"

log "ILM politikasi: applogs-retention (${RETENTION} gun)"
es PUT /_ilm/policy/applogs-retention "$(sed "s/__RETENTION_DAYS__/${RETENTION}/g" "$DIR/ilm-policy.json")"

log "Index template: applogs"
es PUT /_index_template/applogs "$(cat "$DIR/index-template.json")"

log "TAMAMLANDI"
