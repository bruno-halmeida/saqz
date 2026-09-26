#!/usr/bin/env bash
set -euo pipefail
set +x

fail() { echo "$*" >&2; exit 1; }
target="${1:-}"
version="${2:-}"
[[ "$target" == dev || "$target" == prod ]] || fail 'Ambiente inválido: use dev ou prod.'
[[ "$version" =~ ^v\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail 'Versão inválida: use v.0.0.1.'
[[ "${DEPLOY_HOST:-}" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]] || fail 'Configure DEPLOY_HOST no Environment.'
[[ "${DEPLOY_USER:-}" =~ ^[a-z_][a-z0-9_-]*$ ]] || fail 'Configure DEPLOY_USER no Environment.'
deploy_port="${DEPLOY_PORT:-22}"
[[ "$deploy_port" =~ ^[0-9]{1,5}$ ]] || fail 'DEPLOY_PORT deve ser uma porta válida.'
(( 10#$deploy_port >= 1 && 10#$deploy_port <= 65535 )) || fail 'DEPLOY_PORT deve ser uma porta válida.'
[[ -n "${DEPLOY_SSH_KEY:-}" && -n "${DEPLOY_KNOWN_HOSTS:-}" ]] ||
  fail 'Configure os secrets DEPLOY_SSH_KEY e DEPLOY_KNOWN_HOSTS no Environment.'

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp -d)"
remote_dir=''
ssh_args=(-i "$work_dir/key" -o "UserKnownHostsFile=$work_dir/known_hosts"
  -o StrictHostKeyChecking=yes -o BatchMode=yes -o IdentitiesOnly=yes -o ConnectTimeout=15
  -o ServerAliveInterval=15 -o ServerAliveCountMax=3)
server="$DEPLOY_USER@$DEPLOY_HOST"
cleanup() {
  if [[ -n "$remote_dir" ]]; then
    ssh "${ssh_args[@]}" -p "$deploy_port" "$server" "rm -rf -- '$remote_dir'" || true
  fi
  rm -rf -- "$work_dir"
}
trap cleanup EXIT
umask 077
printf '%s\n' "$DEPLOY_SSH_KEY" > "$work_dir/key"
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$work_dir/known_hosts"
unset DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS

# Só arquivos de implantação; .env e service accounts permanecem no servidor.
tar -czf "$work_dir/deploy.tar.gz" -C "$repo_dir" \
  compose.yaml compose.server.yaml deploy/k8s deploy/server/setup.sh \
  deploy/server/compose.image.yaml deploy/server/deploy-backend.sh
candidate="$(ssh "${ssh_args[@]}" -p "$deploy_port" "$server" 'mktemp -d /tmp/saqz-deploy.XXXXXXXX')"
[[ "$candidate" =~ ^/tmp/saqz-deploy\.[a-zA-Z0-9]+$ ]] || fail 'Diretório temporário remoto inválido.'
remote_dir="$candidate"
scp "${ssh_args[@]}" -P "$deploy_port" "$work_dir/deploy.tar.gz" "$server:$remote_dir/deploy.tar.gz"
ssh "${ssh_args[@]}" -p "$deploy_port" "$server" \
  "tar -xzf '$remote_dir/deploy.tar.gz' -C '$remote_dir' && bash '$remote_dir/deploy/server/deploy-backend.sh' '$target' '$version'"
