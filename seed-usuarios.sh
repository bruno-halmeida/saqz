#!/usr/bin/env bash
# Cria as 21 contas do cenário de exploração como usuários DE VERDADE: conta no Firebase
# (logável pelo app, com senha) e linha em `access_users` do ambiente escolhido.
#
#   ./seed-usuarios.sh                  # contra o backend local (localhost:8080)
#   ./seed-usuarios.sh server           # contra o Server Dev
#
# Depois rode `seed-exploracao.sh`, que monta grupo, vínculos e financeiro em cima destas.
#
# São três chamadas por pessoa:
#   1. accounts:signUp        cria a conta no Firebase e devolve um idToken
#   2. accounts:update        grava o displayName — o backend lê o claim `name` do token,
#                             e nome é pré-condição do bootstrap
#   3. PUT /api/session       bootstrap: é o que cria a linha em `access_users`
#
# Rodar de novo é seguro: conta que já existe cai no login em vez do cadastro, e o
# bootstrap é idempotente. O projeto Firebase é o mesmo (`saqz-dev`) nos dois ambientes,
# então as contas valem para local e servidor; o que muda é onde o passo 3 grava.

set -euo pipefail

# Chave web do projeto saqz-dev. É pública por natureza (vai no bundle do adm-web) e não
# dá acesso a nada sozinha — é identificador de projeto, não credencial.
readonly api_key="${SAQZ_FIREBASE_API_KEY:-AIzaSyC_7NhdA7NOnL0SXzzNlcI2nAbBmwVodB4}"
readonly identity="https://identitytoolkit.googleapis.com/v1"
readonly senha="${SEED_PASSWORD:-saqz12345}"

case "${1:-local}" in
local)  readonly api="${SEED_API:-http://localhost:8080}" ;;
server) readonly api="${SEED_API:-https://saqz-api.brunoalmeida.dev}" ;;
*)      echo "uso: $0 [local|server]" >&2; exit 64 ;;
esac

command -v curl >/dev/null || { echo "curl não encontrado" >&2; exit 69; }
command -v jq   >/dev/null || { echo "jq não encontrado (brew install jq)" >&2; exit 69; }

# Os mesmos nomes e e-mails que o seed-exploracao.sql procura. Mexer aqui exige mexer lá.
readonly -a pessoas=(
    "owner@saqz.local|Bruno Dono"
    "atleta01@saqz.local|Ana Ribeiro"
    "atleta02@saqz.local|Bruno Tavares"
    "atleta03@saqz.local|Carla Mendes"
    "atleta04@saqz.local|Diego Barbosa"
    "atleta05@saqz.local|Elisa Fontes"
    "atleta06@saqz.local|Felipe Andrade"
    "atleta07@saqz.local|Gabriela Lima"
    "atleta08@saqz.local|Henrique Sales"
    "atleta09@saqz.local|Isabela Moura"
    "atleta10@saqz.local|João Peixoto"
    "atleta11@saqz.local|Karina Duarte"
    "atleta12@saqz.local|Lucas Vasques"
    "atleta13@saqz.local|Mariana Cordeiro"
    "atleta14@saqz.local|Nuno Ferraz"
    "atleta15@saqz.local|Olívia Bastos"
    "atleta16@saqz.local|Pedro Quintana"
    "atleta17@saqz.local|Renata Siqueira"
    "atleta18@saqz.local|Sérgio Antunes"
    "atleta19@saqz.local|Tatiana Reis"
    "atleta20@saqz.local|Vitor Camargo"
)

# Devolve o idToken, criando a conta ou entrando nela se já existir.
token_de() {
    local email="$1" resposta erro

    resposta="$(curl -sS "$identity/accounts:signUp?key=$api_key" \
        -H 'content-type: application/json' \
        -d "$(jq -nc --arg e "$email" --arg p "$senha" \
              '{email:$e, password:$p, returnSecureToken:true}')")"

    erro="$(jq -r '.error.message // empty' <<<"$resposta")"
    if [[ "$erro" == "EMAIL_EXISTS" ]]; then
        resposta="$(curl -sS "$identity/accounts:signInWithPassword?key=$api_key" \
            -H 'content-type: application/json' \
            -d "$(jq -nc --arg e "$email" --arg p "$senha" \
                  '{email:$e, password:$p, returnSecureToken:true}')")"
        erro="$(jq -r '.error.message // empty' <<<"$resposta")"
        # Conta antiga com outra senha: o script não tem como adivinhar, e trocar a senha de
        # alguém seria pior do que parar.
        if [[ "$erro" == "INVALID_LOGIN_CREDENTIALS" || "$erro" == "INVALID_PASSWORD" ]]; then
            echo "  $email já existe com OUTRA senha — apague no Firebase Console ou ajuste SEED_PASSWORD." >&2
            return 1
        fi
    fi

    [[ -z "$erro" ]] || { echo "  falha no Firebase para $email: $erro" >&2; return 1; }
    jq -r .idToken <<<"$resposta"
}

# Grava o nome e devolve o token novo, já com o claim `name`.
nomear() {
    local token="$1" nome="$2" resposta erro

    resposta="$(curl -sS "$identity/accounts:update?key=$api_key" \
        -H 'content-type: application/json' \
        -d "$(jq -nc --arg t "$token" --arg n "$nome" \
              '{idToken:$t, displayName:$n, returnSecureToken:true}')")"

    erro="$(jq -r '.error.message // empty' <<<"$resposta")"
    [[ -z "$erro" ]] || { echo "  falha ao nomear: $erro" >&2; return 1; }

    # `accounts:update` nem sempre devolve idToken novo; quando não devolve, o antigo serve.
    jq -r '.idToken // empty' <<<"$resposta" | grep . || printf '%s' "$token"
}

echo "backend: $api"
echo "senha de todos: $senha"
echo

falhas=0
for pessoa in "${pessoas[@]}"; do
    email="${pessoa%%|*}"
    nome="${pessoa#*|}"

    printf '%-24s %-18s ' "$email" "$nome"

    if ! token="$(token_de "$email")" || [[ -z "$token" ]]; then
        falhas=$((falhas + 1)); continue
    fi
    if ! token="$(nomear "$token" "$nome")" || [[ -z "$token" ]]; then
        falhas=$((falhas + 1)); continue
    fi

    codigo="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "$api/api/session" \
        -H "Authorization: Bearer $token")"

    if [[ "$codigo" == "200" ]]; then
        echo "ok"
    else
        echo "bootstrap devolveu HTTP $codigo"
        falhas=$((falhas + 1))
    fi
done

echo
if (( falhas > 0 )); then
    echo "$falhas de ${#pessoas[@]} falharam." >&2
    exit 1
fi
echo "${#pessoas[@]} contas prontas. Todas entram no app com a senha acima."
echo "Agora: ./seed-exploracao.sh owner@saqz.local ${1:-local}"
