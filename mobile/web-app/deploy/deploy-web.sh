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

# O Cloudflare guarda .js por 4 h no navegador, mesmo com no-cache na origem; o index.html ele não guarda.
# Uma versão na URL dos scripts faz cada publicação chegar na hora (o webpack ignora a query ao achar o .wasm).
version="$(cat "$stage/dist/saqz-web.js" "$stage/dist/saqz-config.js" "$stage/dist/saqz-bridge.js" "$stage/dist/saqz-loader.js" "$stage/dist/saqz-sw.js" | shasum | cut -c1-10)"
for script in saqz-web.js saqz-config.js saqz-bridge.js saqz-loader.js saqz-sw.js; do
  sed -i '' "s#\"$script\"#\"$script?v=$version\"#g" "$stage/dist/index.html"
done
grep -q "saqz-web.js?v=$version" "$stage/dist/index.html" || { echo "index.html sem a versão dos scripts" >&2; exit 1; }

# O servidor não tem rsync: vai um tar pelo ssh. O build novo é extraído ao lado (incoming) e trocado
# de uma vez; o container é recriado e passa a montar a pasta nova. O build anterior fica em dist.old
# até a próxima publicação, para voltar rápido se precisar.
COPYFILE_DISABLE=1 tar --no-mac-metadata -C "$stage" -czf - dist nginx.conf compose.yaml | ssh "$remote" "set -e
  mkdir -p $remote_dir && cd $remote_dir
  rm -rf incoming && mkdir incoming && tar -xzf - -C incoming
  rm -rf dist.old
  if [ -d dist ]; then mv dist dist.old; fi
  mv incoming/dist dist && mv incoming/nginx.conf incoming/compose.yaml . && rmdir incoming
  # Quem está com o app aberto ainda pede o .wasm da versão anterior: ele continua servido por mais uma publicação.
  if [ -f dist.old/saqz-web.js ]; then
    for wasm in \$(grep -o '[0-9a-f]\{20\}\.wasm' dist.old/saqz-web.js | sort -u); do
      [ -f dist/\$wasm ] || cp dist.old/\$wasm dist/
    done
  fi
  docker compose up -d --force-recreate
  docker compose ps"
echo "Publicado: https://saqz.app/app/"
