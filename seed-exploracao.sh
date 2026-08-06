#!/usr/bin/env bash
# Aplica seed-exploracao.sql no banco escolhido.
#
#   ./seed-exploracao.sh local     # banco do compose
#   ./seed-exploracao.sh server    # Supabase do Server Dev
#
# É só conveniência de conexão: o .sql é SQL puro e pode ser colado no editor do Supabase
# ou em qualquer console, sem passar por aqui. O dono está fixo lá dentro
# (`owner@saqz.local`), que é o que o seed-usuarios.sh cria.
#
# O alvo `server` é ambiente compartilhado e pede confirmação; SEED_YES=1 pula.
# Nos dois casos o psql roda dentro de container, então nada precisa estar instalado.
#
# As 21 contas precisam existir antes, no MESMO ambiente: rode `./seed-usuarios.sh` primeiro.
# Ele cria as contas no Firebase e faz o bootstrap que gera a linha em `access_users`; SQL
# não alcança o Firebase. Se faltar gente, este script para com mensagem dizendo o que falta.

set -euo pipefail

readonly here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly sql="$here/seed-exploracao.sql"
readonly target="${1:-}"

[[ -n "$target" ]] || { echo "uso: $0 <local|server>" >&2; exit 64; }
[[ -f "$sql" ]]    || { echo "não achei $sql" >&2; exit 66; }

case "$target" in
local)
    # `database` é o serviço do compose.yaml; -T porque a entrada vem de arquivo.
    # ON_ERROR_STOP vem por -v e não do arquivo, para o .sql seguir sem meta-comando.
    exec docker compose -f "$here/compose.yaml" exec -T database \
        psql -U saqz -d saqz -v ON_ERROR_STOP=1 -f - <"$sql"
    ;;
server)
    # Supabase, os mesmos parâmetros do SPRING_DATASOURCE_URL do compose.server.yaml.
    # Senha vai por PGPASSWORD e não na URL: assim caractere especial não precisa de
    # escape, e ela não aparece na linha de comando de quem estiver olhando o `ps`.
    if [[ -z "${SAQZ_DB_PASSWORD:-}" && -f "$here/.env" ]]; then
        set -a
        # shellcheck disable=SC1091
        . "$here/.env"
        set +a
    fi
    if [[ -z "${SAQZ_DB_PASSWORD:-}" ]]; then
        echo "SAQZ_DB_PASSWORD não está no ambiente nem no .env deste diretório." >&2
        exit 78
    fi

    if [[ "${SEED_YES:-}" != "1" ]]; then
        echo "Isto reescreve o seed no banco do Server Dev, que é compartilhado."
        echo "Apaga e refaz o grupo 9a000000-…-0001 e os usuários seed-atleta-*."
        read -r -p "Confirma? [s/N] " resposta
        [[ "$resposta" == "s" || "$resposta" == "S" ]] || { echo "cancelado."; exit 1; }
    fi

    exec docker run --rm -i \
        -e PGPASSWORD="$SAQZ_DB_PASSWORD" \
        -e PGSSLMODE=require \
        postgres:16-alpine \
        psql -h aws-0-sa-east-1.pooler.supabase.com -p 5432 \
             -U postgres.jrwpmobttggeturyekot -d postgres \
             -v ON_ERROR_STOP=1 -f - <"$sql"
    ;;
*)
    echo "alvo desconhecido: $target (use 'local' ou 'server')" >&2
    exit 64
    ;;
esac
