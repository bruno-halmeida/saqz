#!/usr/bin/env bash
# Contas de revisão da App Store em PRODUÇÃO (api.saqz.app, Firebase saquz-app).
#
#   REVISAO_SENHA='...' ./seed-revisao-apple.sh producao
#
# Cria (ou reaproveita) nove contas com e-mail fictício em @saqz.app, já com e-mail
# confirmado, nome e celular, e monta em volta delas um grupo de vôlei como os de verdade:
# plano ORGANIZADOR ativo para a organizadora, dois jogos publicados com presença
# confirmada, avisos, mensalidades e despesas. Quatro contas vão para a Apple:
#
#   revisao.organizadora@saqz.app  dona do grupo, plano ativo (gestão completa)
#   revisao.atleta@saqz.app        atleta do grupo (confirmar presença, denunciar, bloquear)
#   revisao.exclusao@saqz.app      atleta do grupo, reservada para testar a exclusão de conta
#   revisao.expirada@saqz.app      teste do Organizador vencido e sem assinatura (compra na App Store)
#
# As outras seis só povoam o grupo. A conta expirada volta ao estado vencido a cada execução:
# as compras de loja dela (sandbox da revisão) são apagadas e o teste é refeito já vencido. A senha é a mesma para todas e NÃO mora aqui: o
# repositório é público. Rodar de novo é seguro: as contas são atualizadas, a de exclusão
# é recriada se a Apple a tiver excluído, e o grupo anterior é arquivado (deleted_at) e
# refeito do zero — histórico de presença é append-only, então apagar não é opção.
#
# Segredos: a service account do Firebase e a conexão do banco saem dos Secrets do k3s
# pelo ssh, vão para arquivos temporários (removidos na saída) ou para variáveis do lado
# do servidor, e nunca são impressos. O banco é acessado de dentro do servidor, com o
# psql de uma imagem postgres:16-alpine — não há psql instalado lá.

set -euo pipefail

[[ "${1:-}" == "producao" ]] || { echo "uso: REVISAO_SENHA=... $0 producao" >&2; exit 64; }
[[ -n "${REVISAO_SENHA:-}" ]] || { echo "defina REVISAO_SENHA (mínimo 8 caracteres)" >&2; exit 64; }
(( ${#REVISAO_SENHA} >= 8 )) || { echo "REVISAO_SENHA precisa de 8 caracteres ou mais" >&2; exit 64; }
for tool in curl jq node ssh; do command -v "$tool" >/dev/null || { echo "$tool não encontrado" >&2; exit 69; }; done

readonly root="$(cd "$(dirname "$0")" && pwd)"
readonly plist="$root/mobile/ios-app/SaqzIOS/Config/Prod/GoogleService-Info.plist"
readonly api="https://api.saqz.app"
readonly project="saquz-app"
readonly bundle="app.saqz"
readonly identity="https://identitytoolkit.googleapis.com/v1"
readonly server="saqz-server"
[[ -f "$plist" ]] || { echo "falta $plist (arquivo local, fora do git)" >&2; exit 66; }
api_key="$(/usr/libexec/PlistBuddy -c 'Print :API_KEY' "$plist")"
readonly api_key

tmp="$(mktemp -d)"
chmod 700 "$tmp"
trap 'rm -rf "$tmp"' EXIT

# --- banco: SQL pela entrada padrão, executado no servidor --------------------------------
readonly remote_psql='set -euo pipefail
k() { k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml -n saqz-prod "$@"; }
v() { k get secret backend-env -o "jsonpath={.data.$1}" | base64 -d; }
url="$(v SPRING_DATASOURCE_URL)"; url="${url#jdbc:}"
PGUSER="$(v SPRING_DATASOURCE_USERNAME)"; PGPASSWORD="$(v SPRING_DATASOURCE_PASSWORD)"; export PGUSER PGPASSWORD
exec docker run --rm -i -e PGUSER -e PGPASSWORD postgres:16-alpine psql "$url" -1 -v ON_ERROR_STOP=1 -X -q -At -F "|" -f /dev/stdin'
sql() { ssh -o BatchMode=yes "$server" "bash -c $(printf '%q' "$remote_psql")"; }

# --- Firebase Admin: token OAuth a partir da service account -----------------------------
ssh -o BatchMode=yes "$server" \
    "k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml -n saqz-prod get secret firebase-admin -o 'jsonpath={.data.firebase-admin\\.json}'" \
    | base64 -d > "$tmp/sa.json"
chmod 600 "$tmp/sa.json"
admin_token="$(node - "$tmp/sa.json" <<'JS'
const fs = require("fs"), crypto = require("crypto");
const sa = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const b64 = (o) => Buffer.from(typeof o === "string" ? o : JSON.stringify(o)).toString("base64url");
const now = Math.floor(Date.now() / 1000);
const unsigned = `${b64({ alg: "RS256", typ: "JWT" })}.${b64({
  iss: sa.client_email, aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  scope: "https://www.googleapis.com/auth/identitytoolkit https://www.googleapis.com/auth/cloud-platform",
})}`;
const jwt = `${unsigned}.${crypto.createSign("RSA-SHA256").update(unsigned).sign(sa.private_key, "base64url")}`;
fetch("https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "content-type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
}).then((r) => r.json()).then((j) => {
  if (!j.access_token) { console.error("OAuth recusou a service account:", j.error || "sem token"); process.exit(1); }
  process.stdout.write(j.access_token);
});
JS
)"
rm -f "$tmp/sa.json"

admin() { # admin <caminho> <json>
    curl -sS --max-time 30 -X POST "$identity/projects/$project/$1" \
        -H "Authorization: Bearer $admin_token" -H 'content-type: application/json' -d "$2"
}
client() { # client <método> <json>  — mesma chave e bundle que o app usa
    curl -sS --max-time 30 -X POST "$identity/accounts:$1?key=$api_key" \
        -H "X-Ios-Bundle-Identifier: $bundle" -H 'content-type: application/json' -d "$2"
}
call() { # call <token> <método> <caminho> [json] — imprime o corpo e grava o ETag em $tmp/etag
    local token="$1" method="$2" path="$3" body="${4:-}" code
    local args=(-sS --max-time 30 -X "$method" "$api$path" -H "Authorization: Bearer $token"
        -D "$tmp/headers" -o "$tmp/body" -w '%{http_code}')
    [[ -n "$body" ]] && args+=(-H 'content-type: application/json' -d "$body")
    [[ -n "${IF_MATCH:-}" ]] && args+=(-H "If-Match: $IF_MATCH")
    code="$(curl "${args[@]}")"
    tr -d '\r' < "$tmp/headers" | awk -F': ' 'tolower($1)=="etag"{print $2}' > "$tmp/etag"
    if [[ "$code" != 2* ]]; then
        echo "HTTP $code em $method $path: $(head -c 400 "$tmp/body")" >&2
        return 1
    fi
    cat "$tmp/body"
}

# --- pessoas -------------------------------------------------------------------------------
# email|nome|celular|apelido|papel
readonly -a pessoas=(
    "revisao.organizadora@saqz.app|Marina Costa|+5511987650001|Marina|ORGANIZADORA"
    "revisao.atleta@saqz.app|Rafael Lima|+5511987650002|Rafa|ATLETA"
    "revisao.exclusao@saqz.app|Paula Souza|+5511987650003|Paula|ATLETA"
    "revisao.expirada@saqz.app|Carla Mendes|+5511987650004|Carla|ATLETA"
    "revisao.membro1@saqz.app|Bruna Alves|+5511987650011|Bruna|ATLETA"
    "revisao.membro2@saqz.app|Diego Rocha|+5511987650012|Diego|ATLETA"
    "revisao.membro3@saqz.app|Fernanda Dias|+5511987650013|Nanda|ATLETA"
    "revisao.membro4@saqz.app|Gustavo Pires|+5511987650014|Guga|ATLETA"
    "revisao.membro5@saqz.app|Juliana Melo|+5511987650015|Ju|ATLETA"
    "revisao.membro6@saqz.app|Thiago Nunes|+5511987650016|Thiago|ATLETA"
)
token_de() { cat "$tmp/token-$1"; }

echo "1/5 contas no Firebase e no backend"
for pessoa in "${pessoas[@]}"; do
    IFS='|' read -r email nome _ _ _ <<<"$pessoa"
    local_id="$(admin accounts:lookup "$(jq -nc --arg e "$email" '{email:[$e]}')" | jq -r '.users[0].localId // empty')"
    perfil="$(jq -nc --arg e "$email" --arg p "$REVISAO_SENHA" --arg n "$nome" \
        '{email:$e, password:$p, displayName:$n, emailVerified:true, disabled:false}')"
    if [[ -z "$local_id" ]]; then
        resposta="$(admin accounts "$perfil")"
    else
        resposta="$(admin accounts:update "$(jq -c --arg id "$local_id" '. + {localId:$id}' <<<"$perfil")")"
    fi
    erro="$(jq -r '.error.message // empty' <<<"$resposta")"
    [[ -z "$erro" ]] || { echo "  $email: Firebase recusou ($erro)" >&2; exit 1; }

    token="$(client signInWithPassword "$(jq -nc --arg e "$email" --arg p "$REVISAO_SENHA" \
        '{email:$e, password:$p, returnSecureToken:true}')" | jq -r '.idToken // empty')"
    [[ -n "$token" ]] || { echo "  $email: não consegui entrar" >&2; exit 1; }
    call "$token" PUT /api/session >/dev/null
    printf '%s' "$token" > "$tmp/token-$email"
    printf '  %-32s %s\n' "$email" "$nome"
done

echo "2/5 perfil, plano e grupo (banco)"
valores="$(for pessoa in "${pessoas[@]}"; do
    IFS='|' read -r email _ celular apelido papel <<<"$pessoa"
    printf "('%s','%s','%s','%s')," "$email" "$celular" "$apelido" "$papel"
done)"
# Sem tabela temporária: o banco fica atrás do pooler do Supabase. A lista vai em cada
# comando como CTE, e o psql roda o arquivo numa transação só (-1).
revisao="revisao(email, phone, nickname, papel) AS (VALUES ${valores%,})"
grupo="$(sql <<SQL
WITH $revisao
UPDATE access_users u SET phone = r.phone, nickname = r.nickname, city = 'São Paulo',
       email_verified = true, updated_at = now()
FROM revisao r WHERE u.email = r.email AND u.deleted_at IS NULL;

INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id,
                           billing_type, current_period_end, first_confirmed_at, created_at, updated_at)
SELECT id, 'ORGANIZADOR', 'ANNUAL', 'ACTIVE', 'cus_revisao_apple', 'sub_revisao_apple', 'PIX',
       now() + interval '365 days', now() - interval '60 days', now() - interval '60 days', now()
FROM access_users WHERE email = 'revisao.organizadora@saqz.app' AND deleted_at IS NULL
ON CONFLICT (owner_user_id) DO UPDATE SET plan = EXCLUDED.plan, cycle = EXCLUDED.cycle, status = EXCLUDED.status,
    current_period_end = EXCLUDED.current_period_end, canceled_at = NULL, pending_plan = NULL,
    pending_plan_effective_at = NULL, past_due_since = NULL, pending_upgrade_plan = NULL,
    pending_upgrade_charge_id = NULL, updated_at = now();

-- Conta expirada: sem compra de loja e com o teste de 14 dias vencido há 16 dias. Sem grupo próprio,
-- a tela de compra aparece em Perfil → Meu plano, no "+" de Grupos e em Criar grupo.
DELETE FROM app_store_transactions WHERE owner_user_id IN
    (SELECT id FROM access_users WHERE email = 'revisao.expirada@saqz.app');
DELETE FROM app_store_subscriptions WHERE owner_user_id IN
    (SELECT id FROM access_users WHERE email = 'revisao.expirada@saqz.app');
DELETE FROM google_play_orders WHERE owner_user_id IN
    (SELECT id FROM access_users WHERE email = 'revisao.expirada@saqz.app');
DELETE FROM google_play_subscriptions WHERE owner_user_id IN
    (SELECT id FROM access_users WHERE email = 'revisao.expirada@saqz.app');
INSERT INTO organizer_trials (owner_user_id, started_at, ends_at)
SELECT id, now() - interval '30 days', now() - interval '16 days'
FROM access_users WHERE email = 'revisao.expirada@saqz.app' AND deleted_at IS NULL
ON CONFLICT (owner_user_id) DO UPDATE SET started_at = EXCLUDED.started_at, ends_at = EXCLUDED.ends_at,
    coupon_id = NULL, coupon_code = NULL, campaign = NULL;

UPDATE access_groups SET deleted_at = now(), updated_at = now()
WHERE owner_user_id = (SELECT id FROM access_users WHERE email = 'revisao.organizadora@saqz.app' AND deleted_at IS NULL)
  AND deleted_at IS NULL;

WITH $revisao,
dona AS (SELECT id FROM access_users WHERE email = 'revisao.organizadora@saqz.app' AND deleted_at IS NULL),
novo AS (
    INSERT INTO access_groups (
        id, owner_user_id, creation_key, name, time_zone, created_at, updated_at,
        profile_status, modality, composition, description, city, level, play_style,
        default_capacity, default_confirmation_lead_minutes, default_game_fee_cents,
        monthly_fee_cents, monthly_due_day, entry_requires_approval, mensalista_priority,
        promotion_mode, auto_confirm_enabled, pix_key, pix_label)
    SELECT gen_random_uuid(), dona.id, gen_random_uuid(), 'Vôlei de Quinta', 'America/Sao_Paulo',
        now() - interval '60 days', now(), 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED',
        'Vôlei misto toda quinta, 20h, quadra coberta. Nível intermediário.', 'São Paulo',
        'INTERMEDIATE', 'FIVE_ONE', 12, 120, 2500, 8000, 10, false, true, 'FIFO', false,
        'revisao.organizadora@saqz.app', 'Vôlei de Quinta'
    FROM dona RETURNING id, owner_user_id)
INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at, membership_type, active)
SELECT novo.id, u.id,
       (CASE WHEN r.papel = 'ORGANIZADORA' THEN 'ADMIN' ELSE 'ATHLETE' END)::group_role,
       now() - interval '50 days', now(),
       (CASE WHEN r.email IN ('revisao.atleta@saqz.app', 'revisao.membro1@saqz.app', 'revisao.membro2@saqz.app',
                              'revisao.membro3@saqz.app', 'revisao.organizadora@saqz.app')
             THEN 'MENSALISTA' ELSE 'AVULSO' END)::athlete_membership_type,
       true
FROM novo JOIN revisao r ON true JOIN access_users u ON u.email = r.email AND u.deleted_at IS NULL
RETURNING group_id;
SQL
)"
grupo="$(head -n1 <<<"$grupo")"
[[ "$grupo" =~ ^[0-9a-f-]{36}$ ]] || { echo "  o banco não devolveu o grupo: $grupo" >&2; exit 1; }
echo "  grupo $grupo"

echo "3/5 jogos (API, como a organizadora)"
dona="$(token_de revisao.organizadora@saqz.app)"
# As duas próximas quintas às 20h de São Paulo (UTC-3, sem horário de verão desde 2019).
proximas="$(node -e '
const d = new Date(); const out = [];
for (let i = 1; out.length < 2; i++) {
  const c = new Date(d.getTime() + i * 864e5);
  const local = new Date(c.getTime() - 3 * 36e5);
  if (local.getUTCDay() === 4) out.push(local.toISOString().slice(0, 10));
}
console.log(out.join(" "));')"
jogos=()
for dia in $proximas; do
    corpo="$(jq -nc --arg id "$(uuidgen | tr 'A-Z' 'a-z')" --arg dia "$dia" '{
        requestId:$id, title:"Vôlei de Quinta",
        venue:{name:"Arena Pinheiros", address:"Rua dos Pinheiros, 1000 - Pinheiros, São Paulo - SP", court:"Quadra 2"},
        localDate:$dia, localTime:"20:00", zoneId:"America/Sao_Paulo",
        startsAt:($dia + "T23:00:00Z"), durationMinutes:120, capacity:12,
        confirmationDeadline:($dia + "T21:00:00Z"), useDefaultGameFee:true }')"
    jogo="$(call "$dona" POST "/api/groups/$grupo/games" "$corpo" | jq -r '.id')"
    IF_MATCH="$(cat "$tmp/etag")" call "$dona" POST "/api/groups/$grupo/games/$jogo/publish" >/dev/null
    jogos+=("$jogo")
    echo "  $dia 20h  $jogo"
done

echo "4/5 presenças e avisos"
for email in revisao.organizadora@saqz.app revisao.membro1@saqz.app revisao.membro2@saqz.app \
             revisao.membro3@saqz.app revisao.membro4@saqz.app revisao.membro5@saqz.app; do
    call "$(token_de "$email")" PUT "/api/groups/$grupo/games/${jogos[0]}/attendance" \
        "$(jq -nc --arg id "$(uuidgen | tr 'A-Z' 'a-z')" '{requestId:$id, intent:"CONFIRM"}')" >/dev/null
done
call "$(token_de revisao.membro6@saqz.app)" PUT "/api/groups/$grupo/games/${jogos[0]}/attendance" \
    "$(jq -nc --arg id "$(uuidgen | tr 'A-Z' 'a-z')" '{requestId:$id, intent:"DECLINE"}')" >/dev/null
for aviso in \
    "Bem-vindos ao Vôlei de Quinta! Jogamos toda quinta às 20h na Arena Pinheiros, quadra 2. Confirme sua presença pelo app até as 18h." \
    "Lembrete: a mensalidade de outubro vence dia 10. O Pix está no caixa do grupo. Obrigada, pessoal!"; do
    call "$dona" POST "/api/groups/$grupo/messages?channel=NOTICE" \
        "$(jq -nc --arg id "$(uuidgen | tr 'A-Z' 'a-z')" --arg b "$aviso" '{requestId:$id, body:$b}')" >/dev/null
done
echo "  6 confirmados e 1 fora no próximo jogo; 2 avisos"

echo "5/5 mensalidades e despesas (banco)"
sql <<SQL >/dev/null
WITH dona AS (SELECT owner_user_id AS id FROM access_groups WHERE id = '$grupo')
INSERT INTO group_charges (id, group_id, member_user_id, member_display_name, kind, billing_month,
    amount_cents, due_date, status, paid_method, created_by_user_id, changed_by_user_id, created_at, updated_at)
SELECT gen_random_uuid(), m.group_id, m.user_id, u.display_name, 'MONTHLY',
       date_trunc('month', current_date)::date, 8000, date_trunc('month', current_date)::date + 9,
       (CASE WHEN u.email IN ('revisao.membro1@saqz.app', 'revisao.membro2@saqz.app') THEN 'PAID' ELSE 'PENDING' END)::charge_status,
       (CASE WHEN u.email IN ('revisao.membro1@saqz.app', 'revisao.membro2@saqz.app') THEN 'PIX' END)::charge_paid_method,
       dona.id, dona.id, now() - interval '1 day', now()
FROM group_memberships m JOIN access_users u ON u.id = m.user_id, dona
WHERE m.group_id = '$grupo' AND m.membership_type = 'MENSALISTA' AND m.role = 'ATHLETE';

WITH dona AS (SELECT owner_user_id AS id FROM access_groups WHERE id = '$grupo')
INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, custom_category,
    notes, direction, created_by_user_id, changed_by_user_id, created_at, updated_at)
SELECT gen_random_uuid(), '$grupo', d.descricao, d.valor, current_date - d.dias, d.categoria::expense_category,
       NULL, NULL, 'OUT'::expense_direction, dona.id, dona.id, now(), now()
FROM dona, (VALUES ('Aluguel da quadra — outubro', 48000, 3, 'VENUE'),
                   ('Duas bolas novas', 32000, 12, 'EQUIPMENT')) AS d(descricao, valor, dias, categoria);
SQL

echo
echo "Pronto. Contas para a Apple (senha em REVISAO_SENHA):"
echo "  organizadora: revisao.organizadora@saqz.app"
echo "  atleta:       revisao.atleta@saqz.app"
echo "  exclusão:     revisao.exclusao@saqz.app"
echo "  expirada:     revisao.expirada@saqz.app"
