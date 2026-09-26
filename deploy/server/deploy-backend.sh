#!/usr/bin/env bash
# Executado no servidor pelo Actions, a partir de um pacote temporário de deploy.
set -euo pipefail
set +x

fail() { echo "$*" >&2; exit 1; }
target="${1:-}"
version="${2:-}"
[[ "$target" == dev || "$target" == prod ]] || fail 'Ambiente inválido: use dev ou prod.'
[[ "$version" =~ ^v\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail 'Versão inválida: use v.0.0.1.'
[[ "$EUID" -eq 0 ]] || fail 'Execute no servidor como root.'
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
bundle_dir="$(cd -- "$script_dir/../.." && pwd)"
repo_dir=/root/applications/saqz
k() { /usr/local/bin/k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml "$@"; }

if [[ "$target" == dev ]]; then
  [[ -f "$repo_dir/.env" ]] || fail 'Arquivo .env de dev não encontrado no servidor.'
  export SAQZ_BACKEND_VERSION="$version"
  compose=(docker compose --project-directory "$repo_dir" --env-file "$repo_dir/.env"
    -f "$bundle_dir/compose.yaml" -f "$bundle_dir/compose.server.yaml" -f "$script_dir/compose.image.yaml")
  "${compose[@]}" config --quiet
  "${compose[@]}" pull backend
  "${compose[@]}" up -d --no-deps --no-build --force-recreate --wait --wait-timeout 300 backend
else
  [[ -x /usr/local/bin/k3s ]] || fail 'k3s não encontrado.'
  work_dir="$(mktemp -d)"
  trap 'rm -rf -- "$work_dir"' EXIT
  cp -R "$bundle_dir/deploy/k8s" "$work_dir/k8s"
  python3 - "$work_dir" "$version" <<'PY'
import json, re, sys, time
from pathlib import Path
root = Path(sys.argv[1])
path = root / 'k8s/overlays/prod/kustomization.yaml'
content, count = re.subn(r'(?m)^(\s*)newTag:.*$', lambda m: m[1] + 'newTag: ' + sys.argv[2], path.read_text())
if count != 1:
    sys.exit('Esperada exatamente uma versão no overlay de produção.')
path.write_text(content)
# A anotação faz o pod reler os Secrets mesmo ao reimplantar a mesma versão.
patch = {'spec': {'template': {'metadata': {'annotations': {'saqz.app/deployment-id': str(time.time_ns())}}}}}
(root / 'restart.json').write_text(json.dumps(patch))
PY
  k patch --local -f "$work_dir/k8s/base/deployment.yaml" --type merge \
    --patch-file "$work_dir/restart.json" -o yaml > "$work_dir/deployment.yaml"
  mv "$work_dir/deployment.yaml" "$work_dir/k8s/base/deployment.yaml"
  # O acionamento do workflow autoriza importar os segredos e implantar esta versão.
  printf 'sim\n' | bash "$script_dir/setup.sh" segredos
  k apply --dry-run=server -k "$work_dir/k8s/overlays/prod"
  k apply -k "$work_dir/k8s/overlays/prod"
  k -n saqz-prod rollout status deployment/backend --timeout=600s
fi
echo "Backend $target implantado: $version; health validado."
