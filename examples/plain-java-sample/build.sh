#!/usr/bin/env bash
# =====================================================================
#  Maven/Gradle OLMADAN derleme (sadece JDK 11+).
#    ./build.sh
#  Cikti:
#    build/classes/        derlenmis siniflar
#    billing-batch.jar     calistirilabilir jar (MANIFEST Class-Path: lib/elk-logger-1.0.0.jar config/)
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"

ELK_JAR=lib/elk-logger-1.0.0.jar
if [ ! -f "$ELK_JAR" ]; then
  # repo icinde calisirken dist/'ten, offline pakette ../../libs'ten kopyala
  for c in ../../dist/elk-logger-1.0.0.jar ../../libs/elk-logger-1.0.0.jar; do
    if [ -f "$c" ]; then cp "$c" "$ELK_JAR"; echo "lib/ <- $c"; break; fi
  done
fi
[ -f "$ELK_JAR" ] || { echo "HATA: $ELK_JAR yok. elk-logger jar'ini lib/ dizinine kopyalayin."; exit 1; }

mkdir -p build/classes
javac --release 11 -encoding UTF-8 -cp "lib/*" -d build/classes $(find src -name '*.java')

# Calistirilabilir jar: Class-Path yollari JAR'IN BULUNDUGU DIZINE goredir; dizinler '/' ile biter.
printf 'Main-Class: com.example.billing.BillingBatchApp\nClass-Path: lib/elk-logger-1.0.0.jar config/\n' > build/MANIFEST.MF
jar cfm billing-batch.jar build/MANIFEST.MF -C build/classes .
echo "Derlendi: build/classes ve billing-batch.jar"
