# Roteiro 12 · Jogos anteriores, Faltou, puxar para atualizar e a lista no grupo do WhatsApp

Regras do cofre cobertas: JOGO-011 (Jogos anteriores, DEB-128), PRES-016 (Faltou, DEB-129), GER-002 (Puxar para atualizar,
DEB-130), NOT-008 (lista no grupo do WhatsApp por horário ou a cada confirmação, DEB-127).
Tabelas de um aparelho com as mesmas regras: `tests/roteiros/jogos.md`, `tests/roteiros/presenca-e-lista-de-espera.md`,
`tests/roteiros/geral.md` e `tests/roteiros/avisos-e-notificacoes.md` (as colunas iOS e Android daqui são o contrato de resultado).
Se a nota do cofre e o app divergirem, vale a nota: registre o texto que apareceu.

Papéis: **ORG** (dono), **ATL** (avulso), **ATL2** (avulso; vira admin no bloco NOT-008), **ATL3** (mensalista). Contas, senha,
celulares e nomes iguais ao [roteiro 01](01-dono-e-atletas.md): `<data>` = `DD/MM`, `<tag>` = `DDMM`. Os aparelhos de cada rodada
estão no fim do arquivo (Rodada 1: ORG no iPhone, ATL no Android; Rodada 2: invertido). **Anote a hora de início da rodada**
(o bloco NOT-008 depende do horário de silêncio, 22h às 8h de São Paulo).

Atalhos usados nas colunas **Banco** (rode no Mac):

```sh
PSQL='docker exec saqz-multidev-db psql -U saqz -d saqz -Atc'
G="(select id from access_groups where name='QA Vôlei <data>')"
U() { echo "(select id from access_users where display_name='QA $1 <data>')"; }   # U Org, U Atl, U Atl2, U Atl3
J() { echo "(select id from games where group_id=$G and venue_name='$1')"; }       # J 'Quadra J1' etc.
```

**Levar um jogo para o passado e encerrar como o job faria.** A API não cria jogo no passado e a presença congela no início,
então as respostas são dadas antes. Depois, `PASSADO` desloca o jogo inteiro N dias para trás (data local, início e prazo
andam juntos, a hora local não muda) e `ENCERRA` é o mesmo `UPDATE` do job `completeFinished` (`JdbcFinishedGamesRepository`):
status `COMPLETED`, `version+1`, `updated_at=now()`, só em `PUBLISHED` cujo início + duração já passou. Não mexa em
`group_charges`: a diária nasce na confirmação e fica como está.

```sh
PASSADO() { $PSQL "update games set starts_at=starts_at-interval '$2 days', local_date=local_date-$2, confirmation_deadline=confirmation_deadline-interval '$2 days', updated_at=now() where id=$(J "$1")"; }
ENCERRA() { $PSQL "update games set status='COMPLETED', version=version+1, updated_at=now() where id=$(J "$1") and status='PUBLISHED' and starts_at+make_interval(mins=>duration_minutes)<=now()"; }
```

**Token para chamar a API à mão** (passos de rota 403/409), com a conta de quem chama:

```sh
TOKEN() { curl -s 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-saqz-local-api-key' \
  -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"SenhaQA2026\",\"returnSecureToken\":true}" | sed -E 's/.*"idToken": ?"([^"]+)".*/\1/'; }
GID=$($PSQL "select id from access_groups where name='QA Vôlei <data>'")
```

"Reabrir a tela" = trocar de aba Grupos → Início e voltar, ou abrir a tela de novo. "Puxar" = arrastar a lista de cima para
baixo (no mobile-mcp: `mobile_swipe_on_screen` direção `down` a partir do topo do conteúdo); espere a bola girar e sumir.

## Preparação (uma vez por rodada)

Banco limpo (`stack.sh reset` + `stack.sh up`, com bootJar refeito da `main`), app limpo nos 4 aparelhos, depois **passos 1 a 8 do
roteiro 01** (contas, grupo `QA Vôlei <data>`, ATL, ATL2 e ATL3 dentro). Em seguida:

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | banco | ATL3 vira mensalista; diária do grupo `R$ 25,00`; prioridade de mensalista desligada; promoção automática: `update group_memberships set membership_type='MENSALISTA' where group_id=$G and user_id=$(U Atl3); update access_groups set default_game_fee_cents=2500, mensalista_priority=false, promotion_mode='AUTOMATIC' where id=$G` | — | `select membership_type from group_memberships where group_id=$G and user_id=$(U Atl3)` = `MENSALISTA`; `select default_game_fee_cents from access_groups where id=$G` = `2500` | ☐ | ☐ |
| P2 | ORG | Grupo → "Próximos jogos" | Sem o botão `Jogos anteriores` (grupo nunca teve jogo anterior) | `select count(*) from games where group_id=$G` = `0` | ☐ | ☐ |
| P3 | ORG | "Marcar jogo" 4 vezes, todos com vagas `6` e valor do grupo: **J1** amanhã 20:00 quadra `Quadra J1`; **J2** amanhã 22:00 `Quadra J2`; **J3** depois de amanhã 20:00 `Quadra J3`; **J4** daqui a 3 dias 20:00 `Quadra J4` | Os 4 em "Próximos jogos" (`Ver mais 1 jogo` no rodapé se a agenda mostrar 3) | `select venue_name,status from games where group_id=$G order by starts_at` → `Quadra J1..J4` todos `PUBLISHED` | ☐ | ☐ |
| P4 | ATL, ATL2, ATL3 | "Vou" no J1 e no J2 (abrir cada jogo pela agenda) | `Você está confirmado.` em cada | `select count(*) from game_attendance where game_id in ($(J 'Quadra J1'),$(J 'Quadra J2')) and status='CONFIRMED'` = `6`; `select count(*) from group_charges where group_id=$G and status='PENDING'` = `4` (ATL e ATL2 avulsos, 2 jogos; ATL3 mensalista sem diária) | ☐ | ☐ |
| P5 | ATL | "Vou" no J3; ATL2 "Não vou" no J3 | — | `select member_display_name,status from game_attendance where game_id=$(J 'Quadra J3') order by 1` → ATL `CONFIRMED`, ATL2 `DECLINED` | ☐ | ☐ |
| P6 | ORG | J3 → ⋮ → "Cancelar jogo" → confirmar | Status `Cancelado` | `select status, published_at is not null from games where id=$(J 'Quadra J3')` = `CANCELLED\|t` | ☐ | ☐ |
| P7 | banco | J1 → 2 dias atrás e encerrado; J2 → 40 dias atrás (mês anterior) e encerrado; J3 (cancelado) → 5 dias atrás: `PASSADO 'Quadra J1' 3; ENCERRA 'Quadra J1'; PASSADO 'Quadra J2' 41; ENCERRA 'Quadra J2'; PASSADO 'Quadra J3' 7` | — | `select venue_name,status,local_date from games where group_id=$G order by starts_at` → J2 `COMPLETED` (mês anterior), J3 `CANCELLED`, J1 `COMPLETED` (anteontem), J4 `PUBLISHED` | ☐ | ☐ |
| P8 | banco | Rascunho de série cancelado em lote (nunca publicado), 4 dias atrás: `insert into games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) select gen_random_uuid(),group_id,title,local_date+1,local_time,zone_id,starts_at+interval '1 day',duration_minutes,confirmation_deadline+interval '1 day','Quadra Rascunho','',capacity,'CANCELLED',now(),now() from games where id=$(J 'Quadra J3')` | — | `select status, published_at is null from games where venue_name='Quadra Rascunho'` = `CANCELLED\|t` | ☐ | ☐ |

Estado depois da preparação: J1 (anteontem, encerrado, ATL/ATL2/ATL3 jogaram, 2 diárias pendentes de R$ 25,00), J2 (mês
anterior, encerrado, fora da janela de 7 dias da falta), J3 (cancelado depois de publicado, 1 confirmado), rascunho cancelado
nunca publicado, J4 (próximo, aberto).

---

## JOGO-011 · Jogos anteriores [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Reabrir o grupo; olhar o rodapé de "Próximos jogos" | Só o J4 na agenda e, no rodapé, o botão `Jogos anteriores` | `select count(*) from games where group_id=$G and starts_at<now() and (status='COMPLETED' or (status='CANCELLED' and published_at is not null))` = `3` | ☐ | ☐ |
| 2 | ATL (permissão) | Reabrir o grupo | Agenda com o J4, **sem** `Jogos anteriores` | — | ☐ | ☐ |
| 3 | ATL (rota) | `curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(TOKEN qa-atl+<tag>@saqz.local)" http://127.0.0.1:18080/api/groups/$GID/games/past` | — | `403` | ☐ | ☐ |
| 4 | ORG | "Jogos anteriores" | Título `Jogos anteriores`; cabeçalho do mês atual com J1 em cima e J3 abaixo (mais recente primeiro); cabeçalho do mês anterior com J2. **Sem** `Quadra Rascunho` | — | ☐ | ☐ |
| 5 | ORG | Ler a linha do J1 | `3 de 6 foram · Quadra J1`; selo `Encerrado`; `Acerto pendente · 2 pessoas, R$ 50,00` | `select count(*), sum(amount_cents) from group_charges where game_id=$(J 'Quadra J1') and status='PENDING'` = `2\|5000` | ☐ | ☐ |
| 6 | ORG | Ler a linha do J3 | `Cancelado com 1 confirmado`; selo `Cancelado`; linha apagada (registre como ela aparece) | — | ☐ | ☐ |
| 7 | ORG | Tocar no J3 | Tela do jogo cancelado com as abas `Confirmados` (ATL) e `Não iam` (ATL2); **sem** `Vão` / `Não vão` | — | ☐ | ☐ |
| 8 | ORG | Voltar | A lista volta igual ao passo 4 (mesmos meses e linhas, sem encolher para o primeiro mês nem recarregar do zero) | — | ☐ | ☐ |
| 9 | ORG | Tocar no J1 | Botão `Encerrar jogo e acertar` (há diária pendente); abas `Jogaram` · `Não foram` | — | ☐ | ☐ |
| 10 | ORG | Voltar; tocar no J2 → "Encerrar jogo e acertar" → `Recebi` em ATL e em ATL2 (confirmar cada folha) → voltar ao jogo | O botão do J2 passa a `Ver acerto` (contorno) | `select count(*) from group_charges where game_id=$(J 'Quadra J2') and status='PENDING'` = `0` | ☐ | ☐ |
| 11 | ORG | Voltar à lista | Linha do J2 sem `Acerto pendente`; J1 continua com ele | — | ☐ | ☐ |
| 12 | banco + ORG | Sem próximo jogo: `update games set status='CANCELLED', published_at=null where id=$(J 'Quadra J4')`; ORG reabre o grupo | A seção "Próximos jogos" aparece **só** com o botão `Jogos anteriores` | — | ☐ | ☐ |
| 13 | banco | Desfazer: `update games set status='PUBLISHED', published_at=now() where id=$(J 'Quadra J4')` | ORG reabre: J4 volta à agenda | `select status from games where id=$(J 'Quadra J4')` = `PUBLISHED` | ☐ | ☐ |
| 14 | banco + ORG (paginação, opcional) | 21 jogos encerrados extras: `insert into games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,published_at,created_at,updated_at) select gen_random_uuid(),group_id,title,local_date-60-n,local_time,zone_id,starts_at-make_interval(days=>60+n),duration_minutes,confirmation_deadline-make_interval(days=>60+n),'Quadra P'\|\|n,'',capacity,'COMPLETED',now(),now(),now() from games, generate_series(1,21) n where id=$(J 'Quadra J1')`; ORG reabre a lista e rola até o fim | Primeiro vêm 20 linhas; ao rolar, `Carregando mais jogos` e o resto (24 no total); voltar de um jogo do fim mantém a lista inteira | `select count(*) from games where group_id=$G and venue_name like 'Quadra P%'` = `21` | ☐ | ☐ |

## PRES-016 · Faltou [MULTI]

ATL (avulso) e ATL3 (mensalista) jogaram o J1; a falta é marcada no ATL.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL e ORG | Antes de marcar: ATL abre Perfil (estatísticas do grupo); ORG abre Membros → ATL | Anote `Jogos: N` e `Presença: X%` de cada lado | `select count(*) from member_played_games where group_id=$G and user_id=$(U Atl)` = `2` (J1 e J2) | ☐ | ☐ |
| 2 | ORG | Jogos anteriores → J1 → ⋮ do ATL em `Jogaram` | Folha `Ações de QA Atl <data>` com `Avulso · confirmou para <dia da semana>, <data do J1>`, `Só você e os admins veem a falta, e a pessoa não recebe aviso. A diária de R$ 25,00 continua; no acerto você decide se cobra.` e `Marcar falta` (`Confirmou e não apareceu`) | — | ☐ | ☐ |
| 3 | ORG | "Marcar falta" | Abas `Jogaram` (2) · `Faltaram` · `Não foram`; ATL sai de Jogaram | `select no_show_marked_at is not null, no_show_marked_by=$(U Org) from game_attendance where game_id=$(J 'Quadra J1') and member_user_id=$(U Atl)` = `t\|t`; `status` segue `CONFIRMED` | ☐ | ☐ |
| 4 | ORG | Aba `Faltaram` | ATL com a etiqueta `Faltou`; rodapé `Só você e os admins veem quem faltou. Dá para marcar até <data do J1 + 7 dias>.` | — | ☐ | ☐ |
| 5 | ATL | Reabrir "Notificações" | Nenhum aviso novo sobre falta (a pessoa não recebe aviso); se a central estiver vazia, `Nenhuma notificação.` | `select count(*) from group_notifications n join group_messages m on m.id=n.message_id where n.recipient_id=$(U Atl) and m.created_at > (select no_show_marked_at from game_attendance where game_id=$(J 'Quadra J1') and member_user_id=$(U Atl))` = `0` | ☐ | ☐ |
| 6 | ATL | Abrir o J1 (pela notificação antiga do jogo ou pelo link de presença; atleta não tem `Jogos anteriores`) | ATL se vê em `Jogaram`; **sem** aba `Faltaram`, sem `Faltou`, sem ⋮ | — | ☐ | ☐ |
| 7 | ATL2 | Abrir o J1 do mesmo jeito | ATL em `Jogaram` (3 pessoas), sem `Faltaram` | — | ☐ | ☐ |
| 8 | ATL2 (rota) | `curl -s -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $(TOKEN qa-atl2+<tag>@saqz.local)" http://127.0.0.1:18080/api/groups/$GID/games/$($PSQL "select id from games where venue_name='Quadra J1' and group_id=$G")/attendance/no-show/$($PSQL "select id from access_users where display_name='QA Atl3 <data>'")/0` | — | `403`; `select no_show_marked_at from game_attendance where game_id=$(J 'Quadra J1') and member_user_id=$(U Atl3)` vazio | ☐ | ☐ |
| 9 | ORG | J1 → "Encerrar jogo e acertar" | Cabeçalho `2 pessoas jogaram` (não 3); ATL com a etiqueta `Faltou` e os botões `Recebi` e `Dispensar`; ATL2 só com `Recebi` | `select no_show from …` — conferir pela API do gestor: `curl -s -H "Authorization: Bearer $(TOKEN qa-org+<tag>@saqz.local)" http://127.0.0.1:18080/api/groups/$GID/charges` → a cobrança do ATL no J1 com `"noShow":true` | ☐ | ☐ |
| 10 | ORG | `Dispensar` no ATL → folha (`Quem faltou não paga esta diária. A pessoa não recebe aviso. Dá para desfazer depois.`) → confirmar | ATL `Dispensado`; o "falta receber" cai para R$ 25,00 | `select status from group_charges where game_id=$(J 'Quadra J1') and member_user_id=$(U Atl)` = `WAIVED` | ☐ | ☐ |
| 11 | ATL (caixa do atleta) | Início → "Minhas cobranças" | A diária do J1 some das pendentes (dispensada); nenhuma menção a falta | — | ☐ | ☐ |
| 12 | ORG | Membros → ATL (perfil do membro) | `Jogos: N-1` (o J1 saiu), `Presença` mais baixa que no passo 1, `Faltas: 0 avisadas · 1 sem avisar` | `select count(*) from member_played_games where group_id=$G and user_id=$(U Atl)` = `1` | ☐ | ☐ |
| 13 | ATL | Perfil → estatísticas do grupo | `Jogos: N-1` e `Presença` mais baixa, como o ORG vê; `Faltas: 1` **sem** a separação "avisadas · sem avisar" | — | ☐ | ☐ |
| 14 | ORG | J1 → aba `Faltaram` → ⋮ do ATL → `Desfazer` (`Volta para Jogaram`) | Aba `Faltaram` some; ATL volta a `Jogaram` (3) | `select no_show_marked_at from game_attendance where game_id=$(J 'Quadra J1') and member_user_id=$(U Atl)` vazio; a cobrança segue `WAIVED` | ☐ | ☐ |
| 15 | ORG | Abrir o acerto do J1 de novo | Cabeçalho `3 pessoas jogaram`; ATL sem `Faltou`, segue `Dispensado` | — | ☐ | ☐ |
| 16 | ORG | Marcar falta no ATL3 (mensalista) | Folha **sem** a frase da diária (`Só você e os admins veem a falta, e a pessoa não recebe aviso.`); ATL3 em `Faltaram`; nada no acerto (sem diária) | `select count(*) from group_charges where game_id=$(J 'Quadra J1') and member_user_id=$(U Atl3)` = `0` | ☐ | ☐ |
| 17 | ORG (prazo) | Abrir o J2 (encerrado há 40 dias) → aba `Jogaram` | **Sem** ⋮ para marcar falta; se houver rodapé de falta, sem `Dá para marcar até` | — | ☐ | ☐ |
| 18 | ORG (rota, prazo) | `curl -s -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $(TOKEN qa-org+<tag>@saqz.local)" http://127.0.0.1:18080/api/groups/$GID/games/$($PSQL "select id from games where venue_name='Quadra J2' and group_id=$G")/attendance/no-show/$($PSQL "select id from access_users where display_name='QA Atl <data>'")/0` | — | `409`; `select no_show_marked_at from game_attendance where game_id=$(J 'Quadra J2') and member_user_id=$(U Atl)` vazio | ☐ | ☐ |
| 19 | banco + ORG (8 dias) | Levar o J1 para 8 dias atrás: `PASSADO 'Quadra J1' 6`; ORG reabre o J1 | ATL3 continua em `Faltaram` (a marca fica como está) e **sem** ⋮ (nem marcar nem desfazer); rodapé `Só você e os admins veem quem faltou.` | `select no_show_marked_at is not null from game_attendance where game_id=$(J 'Quadra J1') and member_user_id=$(U Atl3)` = `t` | ☐ | ☐ |

## GER-002 · Puxar para atualizar [MULTI]

Sem recarregar por troca de aba: cada passo muda algo num aparelho e o outro **só puxa**. J4 é o próximo jogo.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Abrir a Início e deixar parada | Anote `N confirmados` do J4 | — | ☐ | ☐ |
| 2 | ATL → ORG | ATL: "Vou" no J4. ORG: **puxar** a Início | A bola do Saqz gira num círculo branco abaixo da barra de cima, some, e o J4 mostra `N+1 confirmados` | `select count(*) from game_attendance where game_id=$(J 'Quadra J4') and status='CONFIRMED'` = `N+1` | ☐ | ☐ |
| 3 | ATL2 → ORG | ORG abre a aba Grupos e fica parado. ATL2: "Vou" no J4. ORG: **puxar** a lista de grupos | Atualiza sem erro (registre o que muda na linha do grupo) | — | ☐ | ☐ |
| 4 | ATL2 → ORG | ORG abre o grupo e fica parado. ATL2: "Alterar" → "Não vou". ORG: **puxar** o detalhe do grupo | Contagem do J4 volta a `N+1 confirmados` | — | ☐ | ☐ |
| 5 | ATL3 → ORG | ORG abre o J4 e fica parado. ATL3: "Vou". ORG: **puxar** a tela do jogo | ATL3 aparece em `Vão` | — | ☐ | ☐ |
| 6 | ATL → ORG | ORG abre a Caixa do grupo e fica parado. ATL (avulso) confirmou no passo 2, então a diária dele no J4 existe; ATL2 "Vou" de novo no J4. ORG: **puxar** a Caixa | A diária nova de ATL2 no J4 aparece entre as pendentes | `select count(*) from group_charges where game_id=$(J 'Quadra J4') and status='PENDING'` = `2` | ☐ | ☐ |
| 7 | banco → ORG | ORG abre `Jogos anteriores` e fica parado. Encerrar o J4 como o job: `PASSADO 'Quadra J4' 4; ENCERRA 'Quadra J4'`. ORG: **puxar** a lista | J4 aparece no topo com `Acerto pendente · 2 pessoas, R$ 50,00`; o resto da lista continua | — | ☐ | ☐ |
| 8 | ORG → ATL | ATL abre Perfil e fica parado. ORG: J4 → ⋮ do ATL → "Marcar falta". ATL: **puxar** o Perfil | `Jogos` cai em 1 e a `Presença` cai | `select count(*) from member_played_games where group_id=$G and user_id=$(U Atl)` diminuiu 1 | ☐ | ☐ |
| 9 | ORG (sem rede) | Desligar a rede do aparelho do ORG (Android: `adb -s <serial> shell svc wifi disable; svc data disable`; iOS: BLOQUEADO se não houver como) e puxar a Início | Os dados ficam na tela e aparece embaixo `Não foi possível atualizar. Verifique sua conexão e tente de novo.` | — | ☐ | ☐ |
| 10 | ORG | Religar a rede; puxar duas vezes seguidas, rápido | Uma bola só; dados atualizados | Log do backend: uma única leitura da Início nesse segundo (`grep '/api/home' ~/.saqz-multidev/logs/backend.log \| tail`) | ☐ | ☐ |

## NOT-008 · Lista no grupo do WhatsApp por horário ou a cada confirmação [MULTI]

O stack não tem WhatsApp: o vínculo é criado por SQL e só a **fila** é conferida (`notification_whatsapp_group_queue`, preenchida
pelo gatilho de `group_messages`). A passada ON_CHANGE roda a cada 5 min (`0 */5 * * * *`, São Paulo); espere a passada seguinte
antes de ler o banco. **Registre a hora de cada passo.** Para isso há um jogo aberto novo: ORG marca o **J5** daqui a 2 dias 20:00,
`Quadra J5`, vagas `6`, sistema de jogo `6-0` (o grupo de quadra já tem; se o editor pedir, escolher `6-0`).

```sh
FILA="select m.created_at, m.channel, coalesce(m.reminder_origin,'MANUAL'), q.status from group_messages m join notification_whatsapp_group_queue q on q.message_id=m.id where m.group_id=$G order by m.created_at"
```

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | Vincular um grupo do WhatsApp de mentira: `insert into group_whatsapp_bindings (group_id,whatsapp_jid,invite_code,group_name,instance_jid,created_by) values ($G,'120363000000000000@g.us','QA<tag>','QA Vôlei WhatsApp','5511900000000@s.whatsapp.net',$(U Org))` | — | `select enabled, broken_at is null from group_whatsapp_bindings where group_id=$G` = `t\|t` | ☐ | ☐ |
| 2 | ORG | Grupo → "Editar" → rolar até `Lista no grupo do WhatsApp` | Seletor com `Por horário` (marcado) e `A cada confirmação`; apoio `Envia a lista às 9h, 12h30 e 18h.` | `select attendance_updates_mode from access_groups where id=$G` = `SCHEDULED` | ☐ | ☐ |
| 3 | ORG | Membros → ATL2 → `Tornar administrador`. ATL2 (admin) abre "Editar" do grupo | ATL2 **não** vê `Lista no grupo do WhatsApp` (só o dono) | `select role from group_memberships where group_id=$G and user_id=$(U Atl2)` = `ADMIN` | ☐ | ☐ |
| 4 | ATL2 (rota) | `curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $(TOKEN qa-atl2+<tag>@saqz.local)" -H 'Content-Type: application/json' -d '{"attendanceUpdatesMode":"ON_CHANGE"}' http://127.0.0.1:18080/api/groups/$GID/settings` | — | `403`; modo segue `SCHEDULED` | ☐ | ☐ |
| 5 | ORG | `A cada confirmação` → "Salvar alterações" | Apoio vira `Envia quando a lista muda, das 8h às 22h.`; reabrir "Editar" mantém a escolha | `select attendance_updates_mode from access_groups where id=$G` = `ON_CHANGE` | ☐ | ☐ |
| 6 | banco (primeira passada) | Esperar a próxima passada de 5 min (anotar a hora) | — | Entre 8h e 22h: `$PSQL "$FILA"` ganha uma linha `REMINDER\|ON_CHANGE\|PENDING` do J5 (primeira passada de um jogo publica). Entre 22h e 8h: nenhuma linha `ON_CHANGE` | ☐ | ☐ |
| 7 | ATL | "Vou" no J5 (anotar a hora; < 15 min depois da linha do passo 6) | — | Nas passadas antes de completar 15 min da última `ON_CHANGE`: **nenhuma** linha nova (freio) | ☐ | ☐ |
| 8 | banco | Esperar a primeira passada depois dos 15 min | — | Linha nova `REMINDER\|ON_CHANGE\|PENDING` (a mudança segurada sai); `select count(*) from group_messages where group_id=$G and reminder_origin='ON_CHANGE'` = `2` | ☐ | ☐ |
| 9 | banco | Esperar mais uma passada sem ninguém mexer | — | Nenhuma linha nova (lista igual à última publicada) | ☐ | ☐ |
| 10 | banco | Corpo 6-0 com 1 de 6: `select body from group_messages where group_id=$G and reminder_origin='ON_CHANGE' order by created_at desc limit 1` | — | Linha `1.` com o nome do ATL e linhas `2.` a `6.` só com o número | ☐ | ☐ |
| 11 | ORG | J5 → `Avisar` (lembrete manual para quem não respondeu) | Aviso enviado | `$PSQL "$FILA"` ganha `REMINDER\|MANUAL\|PENDING` (vai à fila mesmo em `ON_CHANGE`) | ☐ | ☐ |
| 12 | ORG | Voltar o grupo para `Por horário` e salvar; J5 → `Avisar` de novo | Aviso enviado | Modo `SCHEDULED`; nova linha `REMINDER\|MANUAL\|PENDING` na fila | ☐ | ☐ |
| 13 | banco (origem fora do modo) | Simular um lembrete ON_CHANGE num grupo em `SCHEDULED`: `insert into group_messages (id,group_id,channel,author_id,body,game_id,reminder_origin,request_id,created_at) select gen_random_uuid(),$G,'REMINDER',$(U Org),'teste origem',$(J 'Quadra J5'),'ON_CHANGE',gen_random_uuid(),now()` (se a coluna não existir com esse nome, registre o erro e marque BLOQUEADO) | — | A mensagem **não** entra na fila: `select count(*) from notification_whatsapp_group_queue q join group_messages m on m.id=q.message_id where m.body='teste origem'` = `0` | ☐ | ☐ |
| 14 | banco + ORG (5-1) | Voltar a `A cada confirmação`. Mudar o J5 para 5-1 pelo editor (`Sistema de jogo` → `5-1`) ou, se o editor não deixar com gente confirmada, `update games set play_style='FIVE_ONE' where id=$(J 'Quadra J5')` (registre qual); ATL2 "Vou" escolhendo a posição; esperar passada (respeitando os 15 min) | — | Corpo da última `ON_CHANGE`: uma seção por posição, vagas numeradas dentro dela e as vazias só com o número | ☐ | ☐ |
| 15 | banco (horário de silêncio) | Se a rodada passar das 22h: ATL3 "Vou" depois das 22h e esperar duas passadas | — | Nenhuma linha `ON_CHANGE` nova entre 22h e 8h. Se a rodada acabar antes das 22h, BLOQUEADO com a hora | ☐ | ☐ |

> No passo 13 confira antes as colunas obrigatórias de `group_messages` (`\d group_messages`) e ajuste só a lista de colunas, sem
> mudar `channel`, `reminder_origin` nem o grupo.

---

## Rodadas

**Rodada 1: ORG no iPhone, ATL no Android.**
ORG = iPhone 17, ATL = `Saqz_QA_A`, ATL2 = iPhone 17e, ATL3 = `Saqz_QA_B`. Roteiro inteiro (preparação, JOGO-011, PRES-016,
GER-002, NOT-008).

**Rodada 2: ORG no Android, ATL no iPhone.**
Reset do stack (`stack.sh reset` + `up`), apps limpos, `<tag>` novo. ORG = `Saqz_QA_A`, ATL = iPhone 17, ATL2 = `Saqz_QA_B`,
ATL3 = iPhone 17e. Rodar de novo todos os passos e marcar a coluna da plataforma de quem **executou o "Faça"**.

No relato, informe por rodada: hora de início e fim (NOT-008), tempo total, passos em que usou adb/simctl, o que ficou BLOQUEADO
e as divergências entre nota e app (ver README dos roteiros).
