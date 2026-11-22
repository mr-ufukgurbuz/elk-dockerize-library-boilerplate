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
# Proje D: Maven/Gradle olmayan duz Java (javac + java -cp)
cp libs/elk-logger/target/elk-logger-1.0.0.jar examples/plain-java-sample/lib/
(cd examples/plain-java-sample && ./build.sh >/dev/null && ./run.sh cp 15 0 >/dev/null && ./run.sh jar 5 0 >/dev/null)
# Proje C: Gradle (kuruluysa)
HAS_GRADLE=0
if command -v gradle >/dev/null 2>&1; then
  HAS_GRADLE=1
  (cd examples/gradle-sample && gradle --offline -q run --args="15 0" -PelkLoggerJar="$ROOT/libs/elk-logger/target/elk-logger-1.0.0.jar" >/dev/null)
fi

ES="http://127.0.0.1:${ES_PORT}"
count() { curl -s -u "elastic:$ELASTIC_PASSWORD" "$ES/$1/_count" -H 'Content-Type: application/json' \
  -d "{\"query\":{\"term\":{\"environment\":\"$RUN_ID\"}}}" | sed -E 's/.*"count":([0-9]+).*/\1/'; }

# index'in en az $2 dokumana ulasmasini bekle (refresh_interval 5 sn), sayiyi yaz
wait_count() {
  local n=0
  for _ in $(seq 1 30); do
    n=$(count "$1"); [ "${n:-0}" -ge "$2" ] && break; sleep 2
  done
  echo "${n:-0}"
}
a=$(wait_count 'applogs-order-service-*' 40)
b=$(wait_count 'applogs-payment-service-*' 20)
c=$(wait_count 'applogs-billing-batch-*' 20)
echo "order-service dokuman: $a  payment-service dokuman: $b  billing-batch (duz Java) dokuman: $c"
[ "$a" -ge 40 ] || fail "order-service loglari eksik ($a)"
[ "$b" -ge 20 ] || fail "payment-service loglari eksik ($b)"
[ "$c" -ge 20 ] || fail "billing-batch (duz Java) loglari eksik ($c)"
if [ "$HAS_GRADLE" = 1 ]; then
  g=$(wait_count 'applogs-inventory-service-*' 15); echo "inventory-service (Gradle) dokuman: $g"
  [ "$g" -ge 15 ] || fail "inventory-service (Gradle) loglari eksik ($g)"
fi
cross=$(curl -s -u "elastic:$ELASTIC_PASSWORD" "$ES/applogs-order-service-*/_count?q=project:payment-service" | sed -E 's/.*"count":([0-9]+).*/\1/')
[ "$cross" = 0 ] || fail "proje izolasyonu bozuk: order index'inde payment logu var"
errs=$(curl -s -u "elastic:$ELASTIC_PASSWORD" "$ES/applogs-*/_count" -H 'Content-Type: application/json' \
  -d "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"environment\":\"$RUN_ID\"}},{\"range\":{\"level_value\":{\"gte\":40}}},{\"exists\":{\"field\":\"exception.stacktrace\"}}]}}}" | sed -E 's/.*"count":([0-9]+).*/\1/')
echo "exception iceren ERROR loglari: $errs"
curl -s -o /dev/null -w '%{http_code}' -u "elastic:$ELASTIC_PASSWORD" "http://127.0.0.1:${KIBANA_PORT}/api/saved_objects/dashboard/applogs-all-dashboard" | grep -q 200 || fail "Kibana dashboard yok"
echo "E2E BASARILI"

if [ "${1:-}" = "--down" ]; then (cd "$STACK" && docker compose down -v); fi
