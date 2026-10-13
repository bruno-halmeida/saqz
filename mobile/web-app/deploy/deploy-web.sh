#!/usr/bin/env bash
# Publica o web app em https://saqz.app/app/: build de produção local, config de produção,
# rsync para o saqz-server e (re)criação do container. Rodar da raiz do repositório.
#   mobile/web-app/deploy/deploy-web.sh            # build + publica
#   mobile/web-app/deploy/deploy-web.sh --no-build # publica o último build
set -euo pipefail
repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
deploy_dir="$repo/mobile/web-app/deploy"
dist="$repo/mobile/web-app/build/dist/wasmJs/productionExecutable"
remote=saqz-server
remote_dir=/root/applications/saqz-web

if [[ "${1:-}" != "--no-build" ]]; then
  export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.7+6/Contents/Home}"
  (cd "$repo/mobile" && ./gradlew :web-app:wasmJsBrowserDistribution --console=plain)
fi
[[ -f "$dist/index.html" && -f "$dist/saqz-web.js" ]] || { echo "Build de produção não encontrado em $dist" >&2; exit 1; }

stage="$(mktemp -d)"
trap 'rm -rf -- "$stage"' EXIT
cp -R "$dist" "$stage/dist"
cp "$deploy_dir/saqz-config.prod.js" "$stage/dist/saqz-config.js"
cp "$deploy_dir/nginx.conf" "$deploy_dir/compose.yaml" "$stage/"

ssh "$remote" "mkdir -p $remote_dir/dist"
# --delete-after: os .wasm antigos só somem depois que os novos chegaram.
rsync -az --delete-after "$stage/dist/" "$remote:$remote_dir/dist/"
rsync -az "$stage/nginx.conf" "$stage/compose.yaml" "$remote:$remote_dir/"
ssh "$remote" "cd $remote_dir && docker compose up -d --force-recreate && docker compose ps"
echo "Publicado: https://saqz.app/app/"
