# Atalhos de banco e API dos roteiros multi-aparelho, para bash e zsh. Cada comando do terminal começa do zero, então
# carregue em toda chamada, a partir da raiz do repositório:
#
#   R=29/10i source tests/multi-aparelhos/ferramentas/rodada.sh; PSQL "select count(*) from games where group_id=$G"
#
# R é o rótulo da rodada dado ao preparar-grupo.mjs (data + sufixo). Contas qa-<papel>+<R sem barra>@saqz.local.
: "${R:?defina R, por exemplo R=29/10i}"
RODADA=$R; TAG=${R/\//}
FERR=tests/multi-aparelhos/ferramentas

PSQL() { docker exec saqz-multidev-db psql -U saqz -d saqz -Atc "$1"; }
G="(select id from access_groups where name='QA Vôlei $R')"
GRUPO=$(PSQL "select id from access_groups where name='QA Vôlei $R'")
U() { echo "(select id from access_users where display_name='QA $1 $RODADA')"; }
J() { echo "(select id from games where group_id=$G and venue_name='$1')"; }
JID() { PSQL "select id from games where group_id=$G and venue_name='$1'"; }

# API <papel> <MÉTODO> <caminho> [json] [If-Match]: imprime o código HTTP e o começo do corpo; $RID no json vira um UUID novo.
API() { local quem=$1; shift; node "$FERR/api.mjs" "qa-$quem+$TAG@saqz.local" "$@"; }
# MARCAR <dias a partir de hoje> <HH:MM> <quadra> [vagas]: marca e publica pela API com a conta do ORG, como o app antigo
# (sem valor nem marca: o jogo leva o padrão do grupo).
MARCAR() { node "$FERR/marcar-jogo.mjs" "qa-org+$TAG@saqz.local" "$GRUPO" "$@"; }

# Leva o jogo N dias para o passado (início, data e prazo juntos) e encerra como o job completeFinished.
PASSADO() { PSQL "update games set starts_at=starts_at-interval '$2 days', local_date=local_date-$2, confirmation_deadline=confirmation_deadline-interval '$2 days', updated_at=now() where id=$(J "$1")"; }
ENCERRA() { PSQL "update games set status='COMPLETED', version=version+1, updated_at=now() where id=$(J "$1") and status='PUBLISHED' and starts_at+make_interval(mins=>duration_minutes)<=now()"; }

# LEMBRETE <quadra>: lembrete do jogo com link de presença; imprime o código do link (abrir com attendance/<código>).
LEMBRETE() { PSQL "with m as (insert into group_messages(id,group_id,author_id,author_name,channel,request_id,body,game_id) select gen_random_uuid(),g.id,g.owner_user_id,u.display_name,'REMINDER',gen_random_uuid(),'Lembrete QA',$(J "$1") from access_groups g join access_users u on u.id=g.owner_user_id where g.id='$GRUPO' returning id) insert into notification_attendance_links(message_id) select id from m returning code" | head -1; }
