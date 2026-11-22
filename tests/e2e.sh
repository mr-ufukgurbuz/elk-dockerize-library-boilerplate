#!/usr/bin/env bash
# =====================================================================
#  UCTAN UCA TEST: ELK ortamini (elk-stack/) baslatir, iki ornek projeyi
#  calistirir ve loglarin AYRI index'lere dustugunu dogrular.
#  Gereksinim: docker + compose, ELK imajlari (yerelde veya pull edilebilir), JDK 11+, Maven.
#    tests/e2e.sh            -> calistir, ortam acik kalsin
#    tests/e2e.sh --down     -> sonunda ortami sil (veri dahil)
# =====================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STACK="$ROOT/elk-stack"
fail() { echo "E2E HATA: $*" >&2; exit 1; }

cd "$STACK"
[ -f .env ] || ./bin/elk.sh init-secrets
set -a; . ./.env; set +a
for img in elasticsearch kibana logstash; do
  docker image inspect "$img:$ELK_VERSION" >/dev/null 2>&1 || docker pull "$img:$ELK_VERSION"
done
./bin/elk.sh up 900

cd "$ROOT"
mvn -q -B install -DskipTests
mvn -q -B -pl examples/order-service-sample,examples/payment-service-slf4j-sample dependency:copy-dependencies -DoutputDirectory=target/lib

RUN_ID="e2e$(date +%s)"
export APP_ENV="$RUN_ID"
(cd examples/order-service-sample && java -cp "target/order-service-sample-1.0.0.jar:target/lib/*" com.example.orders.OrderServiceApp 40 0 >/dev/null)
(cd examples/payment-service-slf4j-sample && java -cp "target/payment-service-slf4j-sample-1.0.0.jar:target/lib/*" com.example.payments.PaymentServiceApp 20 0 >/dev/null)

ES="http://127.0.0.1:${ES_PORT}"
count() { curl -s -u "elastic:$ELASTIC_PASSWORD" "$ES/$1/_count" -H 'Content-Type: application/json' \
  -d "{\"query\":{\"term\":{\"environment\":\"$RUN_ID\"}}}" | sed -E 's/.*"count":([0-9]+).*/\1/'; }

for i in $(seq 1 30); do
  a=$(count 'applogs-order-service-*'); b=$(count 'applogs-payment-service-*')
  [ "${a:-0}" -gt 0 ] && [ "${b:-0}" -gt 0 ] && break; sleep 2
done
echo "order-service dokuman: $a  payment-service dokuman: $b"
[ "$a" -ge 40 ] || fail "order-service loglari eksik ($a)"
[ "$b" -ge 20 ] || fail "payment-service loglari eksik ($b)"
cross=$(curl -s -u "elastic:$ELASTIC_PASSWORD" "$ES/applogs-order-service-*/_count?q=project:payment-service" | sed -E 's/.*"count":([0-9]+).*/\1/')
[ "$cross" = 0 ] || fail "proje izolasyonu bozuk: order index'inde payment logu var"
errs=$(curl -s -u "elastic:$ELASTIC_PASSWORD" "$ES/applogs-*/_count" -H 'Content-Type: application/json' \
  -d "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"environment\":\"$RUN_ID\"}},{\"range\":{\"level_value\":{\"gte\":40}}},{\"exists\":{\"field\":\"exception.stacktrace\"}}]}}}" | sed -E 's/.*"count":([0-9]+).*/\1/')
echo "exception iceren ERROR loglari: $errs"
curl -s -o /dev/null -w '%{http_code}' -u "elastic:$ELASTIC_PASSWORD" "http://127.0.0.1:${KIBANA_PORT}/api/saved_objects/dashboard/applogs-all-dashboard" | grep -q 200 || fail "Kibana dashboard yok"
echo "E2E BASARILI"

if [ "${1:-}" = "--down" ]; then (cd "$STACK" && docker compose down -v); fi
