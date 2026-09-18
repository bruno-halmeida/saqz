#!/usr/bin/env bash
# Publica o overlay do servidor: puxa a main, espera o git concluir, e só então
# sobe backend, adm-web, landing e mailpit.
#
#   ./deploy.sh
#
# O compose não começa se o pull falhar (divergência, working tree suja, rede).
# Só fast-forward: merge local na main tem que ir para a origin antes.

set -euo pipefail

cd "$(dirname "$0")"

command -v git >/dev/null || { echo "git não encontrado" >&2; exit 69; }
command -v docker >/dev/null || { echo "docker não encontrado" >&2; exit 69; }
command -v curl >/dev/null || { echo "curl não encontrado" >&2; exit 69; }

if [[ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]]; then
  echo "rode na branch main (está em $(git rev-parse --abbrev-ref HEAD))" >&2
  exit 64
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "working tree suja: commite ou descarte antes do deploy" >&2
  git status -sb >&2
  exit 64
fi

echo "==> pull origin/main"
git fetch origin
git pull --ff-only origin main
echo "==> HEAD $(git log -1 --oneline)"

echo "==> compose up (backend, adm-web, landing, mailpit)"
docker compose -f compose.yaml -f compose.server.yaml up -d --build --remove-orphans

echo "==> waiting backend health"
healthy=0
for _ in $(seq 1 36); do
  if curl -sf "http://127.0.0.1:${SAQZ_MANAGEMENT_PORT:-9090}/actuator/health" | grep -q '"status":"UP"'; then
    healthy=1
    break
  fi
  sleep 5
done
if [[ "$healthy" -ne 1 ]]; then
  echo "backend não ficou healthy a tempo" >&2
  docker compose -f compose.yaml -f compose.server.yaml ps >&2
  exit 1
fi

echo "==> ok $(git rev-parse --short HEAD) API UP"
