#!/usr/bin/env bash
# =====================================================================
#  elk-logger'i classpath'e eklemenin 6 yolu. Hepsi ayni sonucu verir.
#    ./run.sh [mod] [adet] [bekleme_ms]
#
#  mod:
#    cp       (varsayilan) java -cp "build/classes:lib/*:config"  -> config/ classpath'te
#    argfile  java @application.classpath ...  (classpath bir dosyada tutulur)
#    env      CLASSPATH ortam degiskeni ile
#    jar      java -jar billing-batch.jar     (MANIFEST Class-Path)
#    workdir  config classpath'te DEGIL; kutuphane ./config/elk-logger.properties'i kendisi bulur
#    file     -Delk.config.file=config/elk-logger.properties
#
#  ELK baska sunucudaysa:  ELK_HOST=10.0.0.5 ./run.sh
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"
[ -d build/classes ] || ./build.sh

MODE="${1:-cp}"; [ $# -gt 0 ] && shift
MAIN=com.example.billing.BillingBatchApp
SEP=":"   # Windows'ta ';' (run.bat)

case "$MODE" in
  cp)      exec java -cp "build/classes${SEP}lib/*${SEP}config" "$MAIN" "$@" ;;
  argfile) exec java @application.classpath "$MAIN" "$@" ;;
  env)     export CLASSPATH="build/classes${SEP}lib/*${SEP}config"; exec java "$MAIN" "$@" ;;
  jar)     exec java -jar billing-batch.jar "$@" ;;
  workdir) exec java -cp "build/classes${SEP}lib/*" "$MAIN" "$@" ;;
  file)    exec java -Delk.config.file=config/elk-logger.properties -cp "build/classes${SEP}lib/*" "$MAIN" "$@" ;;
  *) sed -n '2,16p' "$0"; exit 1 ;;
esac
