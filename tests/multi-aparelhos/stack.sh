#!/usr/bin/env bash
# Stack local do roteiro multi-aparelho (ver README.md desta pasta):
# Postgres e Mailpit em Docker, Firebase Auth Emulator (projeto saqz-local) e o backend em 127.0.0.1:18080.
# Nada sai da máquina: e-mail cai no Mailpit, push não existe e WhatsApp fica desligado.
#
#   tests/multi-aparelhos/stack.sh up      # sobe tudo; constrói o bootJar do backend se faltar
#   tests/multi-aparelhos/stack.sh status
#   tests/multi-aparelhos/stack.sh down    # derruba os serviços; banco e contas do Auth Emulator ficam
#   tests/multi-aparelhos/stack.sh reset   # down + apaga banco e contas: próxima subida começa do zero
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOME_DIR="${SAQZ_MULTIDEV_HOME:-$HOME/.saqz-multidev}"
RUN="$HOME_DIR/run"; LOGS="$HOME_DIR/logs"; AUTH_DATA="$HOME_DIR/auth-emulator"
FIREBASE_BIN="$HOME_DIR/tools/node_modules/firebase-tools/lib/bin/firebase.js"
JDK21="${SAQZ_JDK21:-$(ls -d "$HOME"/.gradle/jdks/*21*/jdk-21*/Contents/Home 2>/dev/null | head -1)}"

API_PORT=18080; AUTH_PORT=9099; DB_PORT=15432; SMTP_PORT=11025; MAIL_UI_PORT=18025
DB=saqz-multidev-db; MAIL=saqz-multidev-mailpit; VOLUME=saqz-multidev-postgres

mkdir -p "$RUN" "$LOGS" "$AUTH_DATA"

healthy() { curl -fsS -o /dev/null "$1"; }
wait_for() { local url=$1 what=$2 i; for i in $(seq 1 90); do healthy "$url" && return 0; sleep 2; done; echo "$what não respondeu em $url" >&2; return 1; }
running() { [ -f "$RUN/$1.pid" ] && kill -0 "$(cat "$RUN/$1.pid")" 2>/dev/null; }

ensure_tools() {
  [ -f "$FIREBASE_BIN" ] || { mkdir -p "$HOME_DIR/tools"; npm install --prefix "$HOME_DIR/tools" --no-audit --no-fund firebase-tools@15.25.1; }
  [ -n "$JDK21" ] || { echo "JDK 21 não encontrado em ~/.gradle/jdks; defina SAQZ_JDK21" >&2; exit 1; }
}

bootjar() {
  local jar
  jar=$(ls "$ROOT"/backend/bootstrap/build/libs/*.jar 2>/dev/null | grep -v -- '-plain.jar' | head -1 || true)
  if [ -z "$jar" ]; then
    JAVA_HOME="$JDK21" "$ROOT/backend/gradlew" -p "$ROOT/backend" :bootstrap:bootJar --console=plain -q
    jar=$(ls "$ROOT"/backend/bootstrap/build/libs/*.jar | grep -v -- '-plain.jar' | head -1)
  fi
  echo "$jar"
}

up() {
  ensure_tools
  docker info >/dev/null 2>&1 || { echo "Docker não está rodando" >&2; exit 1; }

  if ! docker ps --format '{{.Names}}' | grep -qx "$DB"; then
    docker rm -f "$DB" >/dev/null 2>&1 || true
    docker run -d --name "$DB" --label saqz.multidev=1 -p "127.0.0.1:$DB_PORT:5432" \
      -e POSTGRES_DB=saqz -e POSTGRES_USER=saqz -e POSTGRES_PASSWORD=saqz-multidev-local-only \
      -v "$VOLUME:/var/lib/postgresql/data" postgres:16-alpine >/dev/null
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx "$MAIL"; then
    docker rm -f "$MAIL" >/dev/null 2>&1 || true
    docker run -d --name "$MAIL" --label saqz.multidev=1 -p "127.0.0.1:$MAIL_UI_PORT:8025" -p "127.0.0.1:$SMTP_PORT:1025" axllent/mailpit:latest >/dev/null
  fi
  for _ in $(seq 1 30); do docker exec "$DB" pg_isready -U saqz -d saqz >/dev/null 2>&1 && break; sleep 1; done

  if ! running firebase; then
    local cfg="$RUN/firebase.json"
    printf '{"emulators":{"auth":{"host":"127.0.0.1","port":%s},"ui":{"enabled":false}}}' "$AUTH_PORT" > "$cfg"
    local import=(); [ -d "$AUTH_DATA/auth_export" ] && import=(--import "$AUTH_DATA")
    (cd "$RUN" && nohup node "$FIREBASE_BIN" emulators:start --only auth --project saqz-local --config "$cfg" \
      --export-on-exit "$AUTH_DATA" ${import[@]+"${import[@]}"} > "$LOGS/firebase.log" 2>&1 & echo $! > "$RUN/firebase.pid")
  fi
  wait_for "http://127.0.0.1:$AUTH_PORT/emulator/v1/projects/saqz-local/config" "Firebase Auth Emulator"

  if ! running backend; then
    local jar; jar=$(bootjar)
    (nohup env SPRING_PROFILES_ACTIVE=local SERVER_PORT="$API_PORT" SERVER_ADDRESS=127.0.0.1 \
      FIREBASE_AUTH_EMULATOR_HOST="127.0.0.1:$AUTH_PORT" \
      SAQZ_LINKS_DOMAIN=https://links.saqz.app \
      SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$DB_PORT/saqz" \
      SPRING_DATASOURCE_USERNAME=saqz SPRING_DATASOURCE_PASSWORD=saqz-multidev-local-only \
      SAQZ_PASSWORD_RESET_SECRET=apenas-para-o-stack-multi-aparelho-local \
      SAQZ_ASAAS_API_KEY= SAQZ_ASAAS_WEBHOOK_TOKEN= \
      SAQZ_APP_STORE_ENVIRONMENTS=SANDBOX,XCODE \
      SAQZ_MAIL_HOST=127.0.0.1 SAQZ_MAIL_PORT="$SMTP_PORT" SAQZ_MAIL_STARTTLS=false SAQZ_MAIL_FROM=nao-responda@saqz.local \
      "$JDK21/bin/java" -jar "$jar" > "$LOGS/backend.log" 2>&1 & echo $! > "$RUN/backend.pid")
  fi
  wait_for "http://127.0.0.1:$API_PORT/actuator/health" "Backend"
  status
}

down() {
  for svc in backend firebase; do
    if running "$svc"; then kill "$(cat "$RUN/$svc.pid")" 2>/dev/null || true; fi
    rm -f "$RUN/$svc.pid"
  done
  sleep 2
  docker rm -f "$DB" "$MAIL" >/dev/null 2>&1 || true
  echo "serviços parados; banco guardado no volume $VOLUME, contas em $AUTH_DATA"
}

reset() {
  down
  docker volume rm "$VOLUME" >/dev/null 2>&1 || true
  rm -rf "$AUTH_DATA"
  echo "banco e contas apagados"
}

status() {
  printf '%-22s %s\n' "API (emulador Android)" "http://10.0.2.2:$API_PORT"
  printf '%-22s %s\n' "API (simulador iOS)"    "http://127.0.0.1:$API_PORT"
  printf '%-22s %s\n' "Auth Emulator"          "http://127.0.0.1:$AUTH_PORT  (contas: $AUTH_DATA)"
  printf '%-22s %s\n' "Mailpit"                "http://127.0.0.1:$MAIL_UI_PORT  (API: /api/v1/messages)"
  printf '%-22s %s\n' "Postgres"               "psql -h 127.0.0.1 -p $DB_PORT -U saqz saqz"
  printf '%-22s %s\n' "Logs"                   "$LOGS"
  for svc in backend firebase; do running "$svc" && echo "$svc: rodando (pid $(cat "$RUN/$svc.pid"))" || echo "$svc: parado"; done
  docker ps --filter label=saqz.multidev=1 --format '{{.Names}}: {{.Status}}'
}

case "${1:-}" in
  up) up ;; down) down ;; reset) reset ;; status) status ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
