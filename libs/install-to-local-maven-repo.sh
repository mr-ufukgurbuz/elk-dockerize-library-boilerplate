#!/usr/bin/env bash
# Offline makinede jar'lari yerel Maven deposuna (~/.m2) kurar. Sonra projenizde normal <dependency> kullanabilirsiniz.
#   ./install-to-local-maven-repo.sh            (bu scriptin bulundugu libs/ dizinindeki jar'lar)
# Kurumsal Nexus/Artifactory varsa bunun yerine jar'lari oraya 'deploy' edin (docs/06-JAVA-KUTUPHANE.md).
set -euo pipefail
D="$(cd "$(dirname "$0")" && pwd)"
command -v mvn >/dev/null || { echo "mvn bulunamadi. Maven kullanmiyorsaniz jar'i dogrudan classpath'e ekleyin."; exit 1; }
V=1.0.0
mvn -q install:install-file -Dfile="$D/elk-logger-$V.jar" -DgroupId=io.github.elkboilerplate -DartifactId=elk-logger -Dversion=$V -Dpackaging=jar
if [ -f "$D/elk-logger-slf4j-$V.jar" ]; then
  mvn -q install:install-file -Dfile="$D/elk-logger-slf4j-$V.jar" -DgroupId=io.github.elkboilerplate -DartifactId=elk-logger-slf4j -Dversion=$V -Dpackaging=jar
fi
SLF=$(ls "$D"/slf4j-api-*.jar 2>/dev/null | head -1 || true)
if [ -n "$SLF" ]; then
  SV=$(basename "$SLF" .jar | sed 's/slf4j-api-//')
  mvn -q install:install-file -Dfile="$SLF" -DgroupId=org.slf4j -DartifactId=slf4j-api -Dversion="$SV" -Dpackaging=jar
fi
echo "Kuruldu. pom.xml:  <dependency><groupId>io.github.elkboilerplate</groupId><artifactId>elk-logger</artifactId><version>$V</version></dependency>"
