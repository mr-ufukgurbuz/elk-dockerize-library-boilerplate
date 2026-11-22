#!/usr/bin/env bash
# =====================================================================
#  ELK ortam yonetim araci.  Kullanim:  ./bin/elk.sh <komut> [argumanlar]
#  Tum komutlar icin:                    ./bin/elk.sh help
# =====================================================================
set -euo pipefail

STACK_DIR="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
cd "$STACK_DIR"

c_red=$'\e[31m'; c_grn=$'\e[32m'; c_ylw=$'\e[33m'; c_rst=$'\e[0m'
info() { echo "${c_grn}[elk]${c_rst} $*"; }
warn() { echo "${c_ylw}[elk] UYARI:${c_rst} $*" >&2; }
die()  { echo "${c_red}[elk] HATA:${c_rst} $*" >&2; exit 1; }

compose() {
  if docker compose version >/dev/null 2>&1; then docker compose "$@";
  elif command -v docker-compose >/dev/null 2>&1; then docker-compose "$@";
  else die "docker compose bulunamadi. offline-bundle/install.sh ile kurun."; fi
}

load_env() {
  [ -f .env ] || die ".env yok. Once: ./bin/elk.sh init-secrets"
  set -a; # shellcheck disable=SC1091
  . ./.env; set +a
  ES="http://127.0.0.1:${ES_PORT:-9200}"
  KB="http://127.0.0.1:${KIBANA_PORT:-5601}"
  AUTH="elastic:${ELASTIC_PASSWORD}"
}

rand() { tr -dc 'A-Za-z0-9' </dev/urandom 2>/dev/null | head -c "${1:-24}" || true; }

cmd_init_secrets() {
  if [ ! -f .env ]; then cp .env.example .env; info ".env olusturuldu (.env.example'dan)"; fi
  local changed=0
  for key in ELASTIC_PASSWORD KIBANA_SYSTEM_PASSWORD LOGSTASH_SYSTEM_PASSWORD LOGSTASH_WRITER_PASSWORD; do
    if grep -q "^${key}=CHANGE_ME" .env; then
      sed -i "s|^${key}=.*|${key}=$(rand 24)|" .env; changed=1
    fi
  done
  if grep -q "^KIBANA_ENCRYPTION_KEY=CHANGE_ME" .env; then
    sed -i "s|^KIBANA_ENCRYPTION_KEY=.*|KIBANA_ENCRYPTION_KEY=$(rand 48)|" .env; changed=1
  fi
  chmod 600 .env
  [ "$changed" = 1 ] && info "Rastgele sifreler uretildi -> $STACK_DIR/.env (chmod 600)" || info "Sifreler zaten tanimli, degistirilmedi."
}

check_prereqs() {
  command -v docker >/dev/null || die "docker kurulu degil. offline-bundle/install.sh ile kurun."
  docker info >/dev/null 2>&1 || die "docker servisi calismiyor: sudo systemctl start docker"
  local mmc; mmc=$(cat /proc/sys/vm/max_map_count 2>/dev/null || echo 0)
  if [ "$mmc" -lt 262144 ]; then
    warn "vm.max_map_count=$mmc (<262144). Elasticsearch baslamayabilir. Duzeltme:"
    warn "  sudo sysctl -w vm.max_map_count=262144 && echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elk.conf"
  fi
  for img in elasticsearch kibana logstash; do
    docker image inspect "$img:${ELK_VERSION}" >/dev/null 2>&1 || \
      die "Imaj yok: $img:${ELK_VERSION}. Offline paketten yukleyin: docker load -i images/elk-images-${ELK_VERSION}.tar"
  done
}

wait_healthy() {
  local timeout="${1:-600}" start; start=$(date +%s)
  info "Servislerin hazir olmasi bekleniyor (en fazla ${timeout}s)..."
  while true; do
    local es ls kb ks
    es=$(docker inspect -f '{{.State.Health.Status}}' elk-elasticsearch 2>/dev/null || echo "-")
    ls=$(docker inspect -f '{{.State.Health.Status}}' elk-logstash 2>/dev/null || echo "-")
    kb=$(docker inspect -f '{{.State.Health.Status}}' elk-kibana 2>/dev/null || echo "-")
    ks=$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' elk-kibana-setup 2>/dev/null || echo "-")
    printf '\r  elasticsearch=%-10s logstash=%-10s kibana=%-10s kibana-setup=%-10s' "$es" "$ls" "$kb" "$ks"
    if [ "$es" = healthy ] && [ "$ls" = healthy ] && [ "$kb" = healthy ] && [ "$ks" = "exited/0" ]; then echo; return 0; fi
    if [[ "$ks" == exited/[1-9]* ]] || [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' elk-setup 2>/dev/null)" == exited/[1-9]* ]]; then
      echo; die "Kurulum container'i hata verdi. Loglar: ./bin/elk.sh logs setup  /  ./bin/elk.sh logs kibana-setup"
    fi
    if [ $(( $(date +%s) - start )) -gt "$timeout" ]; then echo; die "Zaman asimi. Durum: ./bin/elk.sh status, loglar: ./bin/elk.sh logs"; fi
    sleep 5
  done
}

cmd_up() {
  [ -f .env ] || cmd_init_secrets
  load_env; check_prereqs
  info "ELK ${ELK_VERSION} baslatiliyor..."
  # Tek seferlik (idempotent) kurulum container'lari her 'up'ta yeniden calissin:
  # sifre/ILM/template/dashboard degisiklikleri boylece uygulanir.
  compose rm -f -s setup kibana-setup >/dev/null 2>&1 || true
  compose up -d
  wait_healthy "${1:-900}"
  cmd_summary
}

cmd_summary() {
  load_env
  local ip; ip=$(hostname -I 2>/dev/null | awk '{print $1}'); ip="${ip:-<sunucu-ip>}"
  cat <<SUMMARY

${c_grn}=============================== ELK HAZIR ===============================${c_rst}
 Kibana         : http://${ip}:${KIBANA_PORT}     (kullanici: elastic  sifre: .env -> ELASTIC_PASSWORD)
 Elasticsearch  : http://${ip}:${ES_PORT}
 Logstash TCP   : ${ip}:${LOGSTASH_TCP_PORT}   (elk.transport=tcp)
 Logstash HTTP  : http://${ip}:${LOGSTASH_HTTP_PORT}  (elk.transport=http)

 Java projenizde elk-logger.properties:
   elk.project.name=<proje-adi>
   elk.logstash.host=${ip}
   elk.logstash.tcp.port=${LOGSTASH_TCP_PORT}

 Test logu gonder : ./bin/elk.sh test-log demo
 Proje alani ac   : ./bin/elk.sh project create <proje-adi>
${c_grn}==========================================================================${c_rst}
SUMMARY
}

cmd_status() {
  compose ps -a
  load_env
  echo
  info "Elasticsearch cluster sagligi:"
  curl -s -u "$AUTH" "$ES/_cluster/health?pretty" | grep -E '"(status|number_of_nodes|active_shards|unassigned_shards)"' || warn "ES'ye ulasilamadi"
  echo
  info "Proje index'leri (applogs-*):"
  curl -s -u "$AUTH" "$ES/_cat/indices/applogs-*?v&s=index&h=index,health,docs.count,store.size" || true
}

cmd_test_log() {
  load_env
  local project="${1:-demo}" ts; ts=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
  local json="{\"@timestamp\":\"$ts\",\"project\":\"$project\",\"application\":\"elk.sh\",\"environment\":\"test\",\"hostname\":\"$(hostname)\",\"level\":\"INFO\",\"level_value\":20,\"logger\":\"elk.sh\",\"thread\":\"bash\",\"message\":\"elk.sh test mesaji\"}"
  if exec 3<>"/dev/tcp/127.0.0.1/${LOGSTASH_TCP_PORT}"; then
    printf '%s\n' "$json" >&3; exec 3>&-
    info "Gonderildi -> project=$project. ~5-10 sn sonra kontrol:"
    echo "  curl -s -u elastic:\$ELASTIC_PASSWORD '$ES/applogs-$project-*/_search?q=message:test&pretty'"
  else
    die "Logstash TCP portuna baglanilamadi (127.0.0.1:${LOGSTASH_TCP_PORT})"
  fi
}

# ---------------------------------------------------------------- project
sanitize() { echo "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9_-]/-/g; s/^[-_]+//'; }

kb_api() { # method path body
  local out code; out=$(mktemp)
  code=$(curl -s -o "$out" -w '%{http_code}' -u "$AUTH" -X "$1" "$KB$2" -H 'kbn-xsrf: true' -H 'Content-Type: application/json' ${3:+--data-binary "$3"})
  if [ "${code:0:1}" != 2 ] && [ "$code" != 409 ]; then cat "$out" >&2; echo >&2; rm -f "$out"; die "Kibana API $1 $2 -> HTTP $code"; fi
  rm -f "$out"
}

cmd_project() {
  local sub="${1:-help}"; shift || true
  case "$sub" in
    create)  project_create "$@" ;;
    list)    load_env; curl -s -u "$AUTH" "$ES/_cat/indices/applogs-*?h=index,docs.count,store.size&s=index" | \
               awk '{split($1,a,"-"); p=$1; sub(/^applogs-/,"",p); sub(/-[0-9]{4}\.[0-9]{2}\.[0-9]{2}$/,"",p); docs[p]+=$2; idx[p]++} END {printf "%-30s %8s %12s\n","PROJE","INDEX","DOKUMAN"; for (k in docs) printf "%-30s %8d %12d\n",k,idx[k],docs[k]}' ;;
    delete-data)
      load_env; local p; p=$(sanitize "${1:?proje adi}")
      read -r -p "applogs-${p}-* index'leri KALICI olarak silinecek. Emin misiniz? (evet/hayir) " ans
      [ "$ans" = evet ] || die "iptal edildi"
      curl -s -u "$AUTH" -X DELETE "$ES/applogs-${p}-*?expand_wildcards=all" ; echo ;;
    *) cat <<H
Kullanim:
  ./bin/elk.sh project create <proje> [--user <kullanici> --password <sifre>] [--editor]
      Kibana'da <proje> adinda ayri bir Space, yalnizca applogs-<proje>-* goren data view + dashboard,
      ve 'project_<proje>_reader' rolu olusturur. --user verilirse sadece bu projeyi gorebilen kullanici acar.
      --editor: kullanici kendi space'inde dashboard/visualization olusturabilir.
  ./bin/elk.sh project list                 Projeleri ve dokuman sayilarini listeler
  ./bin/elk.sh project delete-data <proje>  Projenin TUM loglarini siler
H
    ;;
  esac
}

project_create() {
  load_env
  local raw="${1:?proje adi gerekli}"; shift
  local p user="" pass="" base="read"
  p=$(sanitize "$raw")
  while [ $# -gt 0 ]; do
    case "$1" in
      --user) user="$2"; shift 2 ;;
      --password) pass="$2"; shift 2 ;;
      --editor) base="all"; shift ;;
      *) die "bilinmeyen arguman: $1" ;;
    esac
  done
  info "Kibana space: $p"
  kb_api POST /api/spaces/space "{\"id\":\"$p\",\"name\":\"$raw\",\"description\":\"$raw projesinin loglari (applogs-$p-*)\",\"disabledFeatures\":[]}"

  info "Data view + dashboard -> space '$p'"
  local nd; nd=$(mktemp --suffix=.ndjson)
  sed -e "s/applogs-all/applogs-$p/g" -e "s/applogs-\*/applogs-$p-*/g" -e "s/(Tum Projeler)/($raw)/g" \
      setup/kibana/applogs-saved-objects.ndjson > "$nd"
  local res; res=$(curl -s -u "$AUTH" -X POST "$KB/s/$p/api/saved_objects/_import?overwrite=true" -H 'kbn-xsrf: true' --form file=@"$nd")
  rm -f "$nd"
  echo "$res" | grep -q '"success":true' || die "import basarisiz: $res"
  kb_api POST "/s/$p/api/data_views/default" "{\"data_view_id\":\"applogs-$p\",\"force\":true}"

  info "Rol: project_${p}_reader (sadece applogs-$p-* okuma, sadece '$p' space'i)"
  kb_api PUT "/api/security/role/project_${p}_reader" "{
    \"elasticsearch\": {\"cluster\": [], \"indices\": [{\"names\": [\"applogs-$p-*\"], \"privileges\": [\"read\", \"view_index_metadata\"]}]},
    \"kibana\": [{\"base\": [\"$base\"], \"feature\": {}, \"spaces\": [\"$p\"]}]
  }"

  if [ -n "$user" ]; then
    [ -n "$pass" ] || pass=$(rand 16)
    local code; code=$(curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" -X PUT "$ES/_security/user/$user" \
      -H 'Content-Type: application/json' -d "{\"password\":\"$pass\",\"roles\":[\"project_${p}_reader\"],\"full_name\":\"$raw log kullanicisi\"}")
    [ "${code:0:1}" = 2 ] || die "kullanici olusturulamadi (HTTP $code)"
    info "Kullanici: $user / $pass  (sadece '$raw' loglarini gorur)"
  fi
  info "Hazir: $KB/s/$p/app/dashboards"
}

# ---------------------------------------------------------------- snapshot
cmd_snapshot() {
  load_env
  local sub="${1:-list}"; shift || true
  curl -s -o /dev/null -u "$AUTH" -X PUT "$ES/_snapshot/local_fs" -H 'Content-Type: application/json' \
    -d '{"type":"fs","settings":{"location":"/usr/share/elasticsearch/snapshots","compress":true}}'
  case "$sub" in
    create)  local n="${1:-snap-$(date +%Y%m%d-%H%M%S)}"
             curl -s -u "$AUTH" -X PUT "$ES/_snapshot/local_fs/$n?wait_for_completion=true" -H 'Content-Type: application/json' \
               -d '{"indices":"applogs-*","include_global_state":false}'; echo
             info "Snapshot volume'u disari almak icin: docker run --rm -v elk_es-snapshots:/s -v \$PWD:/b elasticsearch:${ELK_VERSION} tar czf /b/$n.tgz -C /s ." ;;
    list)    curl -s -u "$AUTH" "$ES/_cat/snapshots/local_fs?v" ;;
    restore) local n="${1:?snapshot adi}"
             curl -s -u "$AUTH" -X POST "$ES/_snapshot/local_fs/$n/_restore" -H 'Content-Type: application/json' \
               -d '{"indices":"applogs-*","include_global_state":false,"rename_pattern":"(.+)","rename_replacement":"restored-$1"}'; echo
             info "Geri yuklenen index'ler 'restored-applogs-*' adini alir." ;;
    *) die "snapshot create [ad] | list | restore <ad>" ;;
  esac
}

cmd_help() {
  cat <<H
ELK ortam yonetimi -- $STACK_DIR

  init-secrets            .env olusturur, CHANGE_ME sifrelerini rastgele uretir
  up [timeout_sn]         Ortami baslatir ve hazir olmasini bekler
  down                    Container'lari durdurur ve siler (VERI KORUNUR)
  stop | start | restart  Container'lari durdurur / baslatir
  status                  Container durumu, cluster sagligi, index listesi
  summary                 Baglanti bilgilerini yazar
  logs [servis]           Container loglari (elasticsearch|logstash|kibana|setup|kibana-setup)
  test-log [proje]        Logstash'e Java'siz bir test olayi gonderir
  project ...             Proje bazli Kibana space/rol/kullanici (./bin/elk.sh project help)
  snapshot ...            Yedekleme: create [ad] | list | restore <ad>
  destroy                 Container'lari VE TUM VERIYI siler (geri alinamaz)
H
}

main() {
  local cmd="${1:-help}"; shift || true
  case "$cmd" in
    init-secrets) cmd_init_secrets ;;
    up)       cmd_up "$@" ;;
    down)     compose down ;;
    stop)     compose stop ;;
    start)    compose start ;;
    restart)  compose restart "$@" ;;
    status|ps) cmd_status ;;
    summary)  cmd_summary ;;
    logs)     compose logs --tail=200 -f "$@" ;;
    test-log) cmd_test_log "$@" ;;
    project)  cmd_project "$@" ;;
    snapshot) cmd_snapshot "$@" ;;
    destroy)  read -r -p "TUM LOG VERISI SILINECEK. Emin misiniz? (evet/hayir) " a; [ "$a" = evet ] && compose down -v ;;
    help|-h|--help) cmd_help ;;
    *) cmd_help; exit 1 ;;
  esac
}
main "$@"
