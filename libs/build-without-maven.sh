#!/usr/bin/env bash
# Maven olmadan (sadece JDK 11+ ile) elk-logger jar'ini derler. Offline ortam icin.
#   ./libs/build-without-maven.sh [cikti_dizini]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/../dist}"
VERSION="$(grep -m1 'VERSION = ' "$HERE/elk-logger/src/main/java/io/github/elkboilerplate/logger/internal/Version.java" | sed -E 's/.*"(.*)".*/\1/')"
TMP="$(mktemp -d)"
mkdir -p "$OUT" "$TMP/classes"
javac --release 11 -encoding UTF-8 -d "$TMP/classes" $(find "$HERE/elk-logger/src/main/java" -name '*.java')
printf 'Manifest-Version: 1.0\nAutomatic-Module-Name: io.github.elkboilerplate.logger\nImplementation-Title: elk-logger\nImplementation-Version: %s\n' "$VERSION" > "$TMP/MANIFEST.MF"
jar cfm "$OUT/elk-logger-$VERSION.jar" "$TMP/MANIFEST.MF" -C "$TMP/classes" .
rm -rf "$TMP"
echo "Olusturuldu: $OUT/elk-logger-$VERSION.jar"
