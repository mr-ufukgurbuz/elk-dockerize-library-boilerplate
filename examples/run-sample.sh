#!/usr/bin/env bash
# Ornek projeleri Maven OLMADAN calistirir (offline paket icinden).
#   ./examples/run-sample.sh order   [adet] [bekleme_ms]   -> Proje A (TCP, native API)
#   ./examples/run-sample.sh payment [adet] [bekleme_ms]   -> Proje B (HTTP, SLF4J)
#   ./examples/run-sample.sh plain   [adet] [bekleme_ms]   -> Proje D (Maven/Gradle yok, duz Java)
#   Proje C (Gradle): cd examples/gradle-sample && gradle --offline run
# ELK sunucusu baska makinedeyse:  ELK_HOST=10.0.0.5 ./examples/run-sample.sh order
# adet=0 -> Ctrl+C'ye kadar surekli log uretir.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LIBS="$HERE/../libs"
[ -d "$LIBS" ] || LIBS="$HERE/../dist"
which="${1:-order}"; shift || true
case "$which" in
  plain)   exec "$HERE/plain-java-sample/run.sh" cp "$@" ;;
  order)   APP="$HERE/order-service-sample"; MAIN=com.example.orders.OrderServiceApp; CP_EXTRA="$LIBS/elk-logger-1.0.0.jar" ;;
  payment) APP="$HERE/payment-service-slf4j-sample"; MAIN=com.example.payments.PaymentServiceApp
           CP_EXTRA="$LIBS/elk-logger-1.0.0.jar:$LIBS/elk-logger-slf4j-1.0.0.jar:$(ls "$LIBS"/slf4j-api-*.jar | head -1)" ;;
  *) echo "kullanim: $0 order|payment|plain [adet] [bekleme_ms]"; exit 1 ;;
esac
JAR=$(ls "$APP"/*.jar "$APP"/target/*.jar 2>/dev/null | head -1 || true)
if [ -z "$JAR" ]; then
  echo "Derlenmis ornek yok, javac ile derleniyor..."
  OUTD="$(mktemp -d)"
  javac --release 11 -encoding UTF-8 -cp "$CP_EXTRA" -d "$OUTD" $(find "$APP/src/main/java" -name '*.java')
  cp -r "$APP/src/main/resources/." "$OUTD/"
  JAR="$OUTD"
fi
exec java -cp "$JAR:$CP_EXTRA" "$MAIN" "$@"
