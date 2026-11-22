#!/usr/bin/env bash
# =====================================================================
#  OFFLINE PAKET OLUSTURUCU  —  INTERNETE ERISIMI OLAN bir makinede calisir.
#
#  Gereksinim: bash, curl, tar, sha256sum ve 'docker' VEYA 'podman'.
#              (Java kutuphanesi icin: JDK 11+ ve tercihen Maven)
#  Cikti    : dist/elk-offline-bundle-<ELK_VERSION>-<ARCH>.tar.gz
#             Bu tek dosyayi USB/scp ile offline RHEL sunucusuna tasiyin.
#
#  Kullanim : ./offline-bundle/build-bundle.sh [secenekler]
#    --elk-version 8.19.25     Elastic Stack surumu
#    --docker-version 29.10.0  Statik Docker surumu (download.docker.com/linux/static/stable)
#    --compose-version v5.7.1  Docker Compose plugin surumu (github.com/docker/compose/releases)
#    --arch x86_64|aarch64     Hedef sunucu mimarisi (varsayilan x86_64)
#    --output DIR              Cikti dizini (varsayilan: <repo>/dist)
#    --skip-images             Imajlari indirme (sadece guncel script/doc/jar paketi)
#    --skip-docker             Docker binary'lerini indirme
# =====================================================================
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ELK_VERSION="$(grep -E '^ELK_VERSION=' "$REPO_DIR/elk-stack/.env.example" | cut -d= -f2)"
DOCKER_VERSION="29.10.0"
COMPOSE_VERSION="v5.7.1"
ARCH="x86_64"
OUTPUT="$REPO_DIR/dist"
SKIP_IMAGES=0
SKIP_DOCKER=0

while [ $# -gt 0 ]; do
  case "$1" in
    --elk-version) ELK_VERSION="$2"; shift 2 ;;
    --docker-version) DOCKER_VERSION="$2"; shift 2 ;;
    --compose-version) COMPOSE_VERSION="$2"; shift 2 ;;
    --arch) ARCH="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    --skip-images) SKIP_IMAGES=1; shift ;;
    --skip-docker) SKIP_DOCKER=1; shift ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "Bilinmeyen secenek: $1" >&2; exit 1 ;;
  esac
done

case "$ARCH" in
  x86_64)  PLATFORM="linux/amd64" ;;
  aarch64) PLATFORM="linux/arm64" ;;
  *) echo "Desteklenmeyen mimari: $ARCH" >&2; exit 1 ;;
esac

log() { echo "[bundle] $*"; }
NAME="elk-offline-bundle-${ELK_VERSION}-${ARCH}"
WORK="$OUTPUT/$NAME"
rm -rf "$WORK"; mkdir -p "$WORK"/{docker,images,libs,examples}

# ------------------------------------------------------------ 1) Docker
if [ "$SKIP_DOCKER" = 0 ]; then
  log "Statik Docker ${DOCKER_VERSION} (${ARCH}) indiriliyor"
  curl -fSL --retry 3 -o "$WORK/docker/docker-${DOCKER_VERSION}.tgz" \
    "https://download.docker.com/linux/static/stable/${ARCH}/docker-${DOCKER_VERSION}.tgz"
  log "Docker Compose ${COMPOSE_VERSION} indiriliyor"
  curl -fSL --retry 3 -o "$WORK/docker/docker-compose" \
    "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  chmod +x "$WORK/docker/docker-compose"
fi
cp "$REPO_DIR/offline-bundle/docker-files/docker.service" "$REPO_DIR/offline-bundle/docker-files/daemon.json" "$WORK/docker/"

# ------------------------------------------------------------ 2) Imajlar
if [ "$SKIP_IMAGES" = 0 ]; then
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then CT=docker
  elif command -v podman >/dev/null 2>&1; then CT=podman
  else echo "docker veya podman gerekli (imajlari indirmek icin)" >&2; exit 1; fi
  IMAGES=("elasticsearch:${ELK_VERSION}" "kibana:${ELK_VERSION}" "logstash:${ELK_VERSION}")
  for img in "${IMAGES[@]}"; do
    log "$CT pull --platform $PLATFORM docker.io/library/$img"
    ok=0
    for attempt in 1 2 3; do
      if $CT pull --platform "$PLATFORM" "docker.io/library/$img"; then ok=1; break; fi
      log "pull basarisiz (deneme $attempt/3), 15 sn sonra tekrar"; sleep 15
    done
    if [ "$ok" = 0 ]; then
      if $CT image inspect "$img" >/dev/null 2>&1; then
        log "UYARI: pull yapilamadi, yereldeki $img kullanilacak (mimarisini kontrol edin)"
      else
        echo "Imaj indirilemedi: $img" >&2; exit 1
      fi
    fi
    [ "$CT" = podman ] && $CT tag "docker.io/library/$img" "$img" || true
  done
  log "Imajlar tar'a kaydediliyor (birkac dakika surebilir)"
  if [ "$CT" = podman ]; then
    podman save --format docker-archive -m -o "$WORK/images/elk-images-${ELK_VERSION}.tar" "${IMAGES[@]}"
  else
    docker save -o "$WORK/images/elk-images-${ELK_VERSION}.tar" "${IMAGES[@]}"
  fi
fi

# ------------------------------------------------------------ 3) Java kutuphanesi
log "Java kutuphanesi derleniyor"
if command -v mvn >/dev/null 2>&1; then
  (cd "$REPO_DIR" && mvn -q -B -DskipTests=false install)
  cp "$REPO_DIR"/libs/elk-logger/target/elk-logger-*.jar "$WORK/libs/"
  cp "$REPO_DIR"/libs/elk-logger-slf4j/target/elk-logger-slf4j-*.jar "$WORK/libs/"
  cp "$REPO_DIR"/libs/elk-logger/pom.xml "$WORK/libs/elk-logger.pom"
  cp "$REPO_DIR"/libs/elk-logger-slf4j/pom.xml "$WORK/libs/elk-logger-slf4j.pom"
  cp "$REPO_DIR"/pom.xml "$WORK/libs/elk-logger-parent.pom"
  (cd "$REPO_DIR" && mvn -q -B -pl libs/elk-logger-slf4j dependency:copy -Dartifact=org.slf4j:slf4j-api:2.0.13 -DoutputDirectory="$WORK/libs")
  for ex in order-service-sample payment-service-slf4j-sample; do
    mkdir -p "$WORK/examples/$ex"
    cp -r "$REPO_DIR/examples/$ex/src" "$REPO_DIR/examples/$ex/pom.xml" "$WORK/examples/$ex/"
    cp "$REPO_DIR/examples/$ex/target/$ex-1.0.0.jar" "$WORK/examples/$ex/"
  done
else
  log "Maven yok -> javac ile sadece elk-logger derleniyor (SLF4J modulu atlandi)"
  "$REPO_DIR/libs/build-without-maven.sh" "$WORK/libs"
  cp -r "$REPO_DIR/examples/order-service-sample" "$WORK/examples/"
fi
cp "$REPO_DIR/examples/run-sample.sh" "$WORK/examples/"
cp "$REPO_DIR/libs/install-to-local-maven-repo.sh" "$WORK/libs/"

# ------------------------------------------------------------ 4) Stack, scriptler, dokumanlar
log "elk-stack, scriptler ve dokumanlar kopyalaniyor"
mkdir -p "$WORK/elk-stack"
(cd "$REPO_DIR/elk-stack" && tar cf - --exclude=.env . ) | (cd "$WORK/elk-stack" && tar xf -)
cp "$REPO_DIR/offline-bundle/install.sh" "$REPO_DIR/offline-bundle/uninstall.sh" "$WORK/"
cp -r "$REPO_DIR/docs" "$WORK/docs"
cp "$REPO_DIR/README.md" "$REPO_DIR/LICENSE" "$WORK/"
echo "$ELK_VERSION" > "$WORK/ELK_VERSION"
chmod +x "$WORK"/*.sh "$WORK"/elk-stack/bin/*.sh "$WORK"/examples/*.sh

(cd "$WORK" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)

log "Arsivleniyor"
tar -C "$OUTPUT" -czf "$OUTPUT/$NAME.tar.gz" "$NAME"
(cd "$OUTPUT" && sha256sum "$NAME.tar.gz" > "$NAME.tar.gz.sha256")
log "HAZIR: $OUTPUT/$NAME.tar.gz ($(du -h "$OUTPUT/$NAME.tar.gz" | cut -f1))"
log "Offline sunucuda:  tar xzf $NAME.tar.gz && cd $NAME && sudo ./install.sh"
