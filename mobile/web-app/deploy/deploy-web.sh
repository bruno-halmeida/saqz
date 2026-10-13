#!/usr/bin/env bash
# Publica o web app em https://saqz.app/app/: build de produção local, config de produção,
# envio por tar/ssh para o saqz-server e (re)criação do container. Rodar da raiz do repositório.
#   mobile/web-app/deploy/deploy-web.sh            # build + publica
#   mobile/web-app/deploy/deploy-web.sh --no-build # publica o último build
# Para tirar do ar: ssh saqz-server 'cd /root/applications/saqz-web && docker compose down'.
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

# O servidor não tem rsync: vai um tar pelo ssh. O build novo é extraído ao lado (dist.new) e trocado
# de uma vez; o container é recriado e passa a montar a pasta nova. O build anterior fica em dist.old
# até a próxima publicação, para voltar rápido se precisar.
COPYFILE_DISABLE=1 tar --no-mac-metadata -C "$stage" -czf - dist nginx.conf compose.yaml | ssh "$remote" "set -e
  mkdir -p $remote_dir && cd $remote_dir
  rm -rf incoming && mkdir incoming && tar -xzf - -C incoming
  rm -rf dist.old
  if [ -d dist ]; then mv dist dist.old; fi
  mv incoming/dist dist && mv incoming/nginx.conf incoming/compose.yaml . && rmdir incoming
  docker compose up -d --force-recreate
  docker compose ps"
echo "Publicado: https://saqz.app/app/"
