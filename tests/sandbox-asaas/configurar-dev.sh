#!/usr/bin/env bash
# Liga o recebimento pelo app (DEB-140) no backend de dev, contra o sandbox do Asaas.
#
# Uso (no Mac, com o ssh saqz-server configurado):
#   tests/sandbox-asaas/configurar-dev.sh            # cria /root/applications/saqz/receivables.env no servidor
#   tests/sandbox-asaas/configurar-dev.sh --copiar-chave   # também grava a chave do sandbox em ~/.saqz-multidev/asaas-sandbox.key
#
# - Usa a chave do sandbox que o dev já tem (SAQZ_ASAAS_API_KEY no .env do servidor) e para se ela não for de sandbox.
# - A chave do Asaas vai entre aspas simples: ela começa com "$" e o Compose interpola o env_file, então sem aspas
#   o backend recebe a chave vazia (o recebimento sobe "ligado" e toda chamada ao Asaas falha).
# - Nunca imprime chave. Gera as chaves de cifra só se o arquivo ainda não existir: trocar a chave de cifra depois
#   deixaria ilegível o que já foi cifrado.
# - O deploy de dev (push na main com mudança em backend/**) sobe o backend lendo esse arquivo.
set -euo pipefail

copy_key=false
[[ "${1:-}" == "--copiar-chave" ]] && copy_key=true

ssh saqz-server 'bash -s' <<'REMOTE'
set -euo pipefail
cd /root/applications/saqz
key=$(grep -E '^SAQZ_ASAAS_API_KEY=' .env | head -1 | cut -d= -f2- | tr -d '"'"'"' ')
[[ "$key" == '$aact_hmlg_'* ]] || { echo "A SAQZ_ASAAS_API_KEY do dev não é de sandbox. Parei sem mudar nada." >&2; exit 1; }
if [[ -s receivables.env ]]; then
  echo "receivables.env já existe; não mexi. Variáveis nele:"
  cut -d= -f1 receivables.env | sed 's/^/  /'
  exit 0
fi
wallet=$(curl -fsS -H "access_token: $key" -H 'User-Agent: saqz-dev-setup' https://api-sandbox.asaas.com/v3/wallets \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"][0]["id"])')
umask 077
cat > receivables.env <<EOF
SAQZ_RECEIVABLES_LAUNCH_ENABLED=true
SAQZ_RECEIVABLES_PAYMENTS_ENABLED=true
SAQZ_RECEIVABLES_BAAS_ENABLED=true
SAQZ_RECEIVABLES_ASAAS_BASE_URL=https://api-sandbox.asaas.com/v3
SAQZ_RECEIVABLES_ASAAS_PLATFORM_KEY='$key'
SAQZ_RECEIVABLES_PLATFORM_WALLET_ID=$wallet
SAQZ_RECEIVABLES_ENCRYPTION_KEY=$(openssl rand -base64 32)
SAQZ_RECEIVABLES_ENCRYPTION_KEY_ID=dev-2026-10
SAQZ_RECEIVABLES_IDENTITY_LOOKUP_KEY=$(openssl rand -base64 32)
SAQZ_RECEIVABLES_WEBHOOK_BASE_URL=https://saqz-api.brunoalmeida.dev
SAQZ_RECEIVABLES_WEBHOOK_EMAIL=contato@egysis.com
SAQZ_RECEIVABLES_CHECKOUT_CALLBACK_BASE_URL=https://saqz.app
EOF
chmod 600 receivables.env
echo "receivables.env criado (600). Variáveis:"
cut -d= -f1 receivables.env | sed 's/^/  /'
REMOTE

if $copy_key; then
  mkdir -p ~/.saqz-multidev
  umask 077
  ssh saqz-server "grep -E '^SAQZ_ASAAS_API_KEY=' /root/applications/saqz/.env | head -1 | cut -d= -f2-" \
    | tr -d "\"' \r" > ~/.saqz-multidev/asaas-sandbox.key
  chmod 600 ~/.saqz-multidev/asaas-sandbox.key
  grep -q '^\$aact_hmlg_' ~/.saqz-multidev/asaas-sandbox.key && echo "Chave do sandbox gravada em ~/.saqz-multidev/asaas-sandbox.key (600)." \
    || { rm -f ~/.saqz-multidev/asaas-sandbox.key; echo "A chave lida não é de sandbox; apaguei a cópia local." >&2; exit 1; }
fi
