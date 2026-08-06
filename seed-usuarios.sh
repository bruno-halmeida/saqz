#!/usr/bin/env bash
# Cria as 21 contas do cenário de exploração como usuários DE VERDADE: conta no Firebase
# (logável pelo app, com senha) e linha em `access_users` do ambiente escolhido.
#
#   ./seed-usuarios.sh local            # backend em localhost:8080 (compose de pé)
#   ./seed-usuarios.sh server           # https://saqz-api.brunoalmeida.dev
#
# Depois rode `seed-exploracao.sh`, que monta grupo, vínculos e financeiro em cima destas.
#
# Por pessoa:
#   1. accounts:signUp             cria a conta (EMAIL_EXISTS não é erro: já existe, segue)
#   2. accounts:update             grava o displayName
#   3. accounts:signInWithPassword token NOVO — este passo não é redundante, veja abaixo
#   4. PUT /api/session            bootstrap: cria a linha em `access_users`
#
# O passo 3 existe porque `accounts:update` só às vezes devolve `idToken` novo. Reusar o
# token anterior manda ao backend um JWT sem o claim `name`, e `BootstrapSession` recusa
# com InvalidDisplayName — 400 em toda conta recém-criada, e só nelas, porque conta que já
# tinha nome trazia o claim desde o login. Pedir um token fresco depois de nomear é
# determinístico e custa uma chamada.
#
# Rodar de novo é seguro. O projeto Firebase é o mesmo (`saqz-dev`) nos dois ambientes,
# então as contas valem para local e servidor; o que muda é onde o passo 4 grava.

set -euo pipefail

# Chave web do projeto saqz-dev. É pública por natureza (vai no bundle do adm-web) e não
# dá acesso a nada sozinha — é identificador de projeto, não credencial.
readonly api_key="${SAQZ_FIREBASE_API_KEY:-AIzaSyC_7NhdA7NOnL0SXzzNlcI2nAbBmwVodB4}"
readonly identity="https://identitytoolkit.googleapis.com/v1"
readonly senha="${SEED_PASSWORD:-saqz12345}"

case "${1:-}" in
local)  readonly api="${SEED_API:-http://localhost:8080}" ;;
server) readonly api="${SEED_API:-https://saqz-api.brunoalmeida.dev}" ;;
"")     echo "uso: $0 <local|server>  — o alvo é obrigatório para não semear no ambiente errado" >&2; exit 64 ;;
*)      echo "alvo desconhecido: $1 (use 'local' ou 'server')" >&2; exit 64 ;;
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

# Erro de conexão aqui vale por 21: melhor parar antes do laço do que falhar em cada um.
# Sem -f de propósito: qualquer resposta HTTP prova que o host responde, mesmo 404.
if ! curl -sS -o /dev/null --max-time 15 "$api/actuator/health" 2>/dev/null; then
    echo "o backend não respondeu em $api" >&2
    [[ "$1" == "local" ]] && echo "o compose está de pé? para o Server Dev: $0 server" >&2
    exit 69
fi

identidade() {
    curl -sS --max-time 30 "$identity/accounts:$1?key=$api_key" \
        -H 'content-type: application/json' -d "$2" 2>/dev/null || true
}

echo "backend: $api"
echo "senha de todos: $senha"
echo

falhas=0
for pessoa in "${pessoas[@]}"; do
    email="${pessoa%%|*}"
    nome="${pessoa#*|}"
    credenciais="$(jq -nc --arg e "$email" --arg p "$senha" \
        '{email:$e, password:$p, returnSecureToken:true}')"

    printf '%-24s %-18s ' "$email" "$nome"

    # 1. conta existe? cria; se já existia, segue em frente.
    erro="$(identidade signUp "$credenciais" | jq -r '.error.message // empty')"
    if [[ -n "$erro" && "$erro" != "EMAIL_EXISTS" ]]; then
        echo "Firebase recusou o cadastro: $erro"; falhas=$((falhas + 1)); continue
    fi

    # 2. token para poder nomear.
    entrada="$(identidade signInWithPassword "$credenciais")"
    erro="$(jq -r '.error.message // empty' <<<"$entrada")"
    if [[ -n "$erro" ]]; then
        # Conta antiga com outra senha: adivinhar seria pior do que parar.
        echo "não entrou: $erro (existe com outra senha? ajuste SEED_PASSWORD)"
        falhas=$((falhas + 1)); continue
    fi
    token="$(jq -r '.idToken' <<<"$entrada")"

    # 3. grava o nome e pede um token NOVO, agora com o claim `name`.
    erro="$(identidade update "$(jq -nc --arg t "$token" --arg n "$nome" \
        '{idToken:$t, displayName:$n, returnSecureToken:true}')" | jq -r '.error.message // empty')"
    if [[ -n "$erro" ]]; then
        echo "não gravou o nome: $erro"; falhas=$((falhas + 1)); continue
    fi
    token="$(identidade signInWithPassword "$credenciais" | jq -r '.idToken // empty')"
    if [[ -z "$token" ]]; then
        echo "não consegui token depois de nomear"; falhas=$((falhas + 1)); continue
    fi

    # 4. bootstrap.
    resposta="$(curl -sS --max-time 30 -w $'\n%{http_code}' -X PUT "$api/api/session" \
        -H "Authorization: Bearer $token" 2>/dev/null || true)"
    codigo="${resposta##*$'\n'}"
    corpo="${resposta%$'\n'*}"

    if [[ "$codigo" == "200" ]]; then
        echo "ok"
    else
        echo "bootstrap HTTP ${codigo:-sem resposta} — $(jq -rc '.detail // .title // .' <<<"$corpo" 2>/dev/null || echo "$corpo")"
        falhas=$((falhas + 1))
    fi
done

echo
if (( falhas > 0 )); then
    echo "$falhas de ${#pessoas[@]} falharam." >&2
    exit 1
fi
echo "${#pessoas[@]} contas prontas. Todas entram no app com a senha acima."
echo "Agora: ./seed-exploracao.sh owner@saqz.local $1"
