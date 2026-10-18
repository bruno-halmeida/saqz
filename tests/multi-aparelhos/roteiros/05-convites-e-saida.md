# Roteiro 05 · Convites, pedidos de entrada, saída, remoção e exclusão

Regras do cofre cobertas: CONV-001, 002, 003, 005, 006, 007, 008 e SAI-001 a SAI-009. Se o texto do app e a nota divergirem, vale a nota; anote a divergência no relato.

Papéis: **ORG** (dono), **ATL**, **ATL2** (vira administrador na preparação), **ATL3**, **ATL4** (a pessoa "de fora": entra por pedido) e **ATL5** (conta nova, só para SAI-005). ATL3, ATL4 e ATL5 dividem um aparelho: para trocar de papel, Perfil → "Sair da conta" e entrar com a conta do outro, em todo passo que pedir outro papel. Os aparelhos de cada papel estão em "Rodadas" no fim.

Use `<data>` = dia de hoje (`DD/MM`) nos nomes e `<tag>` = `DDMM` (sem barra) nos e-mails. Contas: `qa-org+<tag>@saqz.local`, `qa-atl+<tag>@saqz.local`, `qa-atl2+<tag>@saqz.local`, `qa-atl3+<tag>@saqz.local`, `qa-atl4+<tag>@saqz.local`, `qa-atl5+<tag>@saqz.local`, senha `SenhaQA2026`, celulares `11900000001` a `11900000006`, nomes `QA Org <data>`, `QA Atl <data>`, `QA Atl2 <data>`, `QA Atl3 <data>`, `QA Atl4 <data>`, `QA Atl5 <data>`, apelidos `Atl`, `Atl2`, `Atl3`, `Atl4`, `Atl5`. Grupo: `QA Vôlei <data>`.

As conferências de banco pressupõem banco limpo no começo de **cada rodada**: `stack.sh reset` + `stack.sh up` (e apagar os dados do app nos aparelhos, como no roteiro 01).

**Ordem de execução** (o estado de um bloco alimenta o seguinte): Preparação → CONV-001 → CONV-002 → CONV-005 → CONV-006 → CONV-003 → CONV-008 → CONV-007 → SAI-001 → SAI-002 → SAI-003 → SAI-004 → SAI-005 → SAI-006 → SAI-007 → SAI-008 → SAI-009. SAI-008 e SAI-009 são destrutivas e vão por último.

Etiquetas: **[CRÍTICO]** entra na rodada segura. **[push: só produção]**: o stack local não tem FCM/APNs; o passo confere só a central de avisos e o banco, e o push em si fica para `tests/producao`.

## Receitas de apoio

Todas rodam no terminal da máquina, não num aparelho. Troque `<tag>`, `<data>` e `<jogo>` pelos valores da rodada.

```sh
TAG=<tag>
PSQL() { docker exec saqz-multidev-db psql -U saqz -d saqz -Atc "$1"; }
tok() { curl -s "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=qa" -H 'Content-Type: application/json' -d "{\"email\":\"qa-$1+$TAG@saqz.local\",\"password\":\"SenhaQA2026\",\"returnSecureToken\":true}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["idToken"])'; }
api() { w=$1; m=$2; p=$3; shift 3; curl -s -w '\n%{http_code}\n' -X "$m" "http://127.0.0.1:18080$p" -H "Authorization: Bearer $(tok $w)" -H 'Content-Type: application/json' "$@"; }
uid() { PSQL "select id from access_users where email='qa-$1+$TAG@saqz.local'"; }
GID=$(PSQL "select id from access_groups where name='QA Vôlei <data>' and deleted_at is null")
```

`api <papel> <método> <caminho> [-d '<json>']` devolve o corpo e, na última linha, o código HTTP (ex.: `api atl GET /api/groups/$GID/invite`). Papéis: `org`, `atl`, `atl2`, `atl3`, `atl4`, `atl5`.

- **R-CÓDIGO**: `PSQL "select short_code from group_invites where group_id='$GID'"` devolve o código de 8 caracteres. O convite usa o link `https://links.saqz.app/?saqz_invite=<código>`.
- **R-ENTREGAR**: entrega do link a um aparelho. Android: `adb -s <id> shell am start -a android.intent.action.VIEW -d 'https://links.saqz.app/?saqz_invite=<código>' app.saqz`. iOS: `xcrun simctl openurl <udid> 'saqz://links.saqz.app/?saqz_invite=<código>'`. O código também pode ser colado em Início → "Tenho um convite".
- **R-RESTAURAR `<papel>` [ADMIN]**: devolve a pessoa ao grupo sem passar pela regra de volta (reposição de ambiente, não faz parte da regra): `PSQL "update group_memberships set active=true, role='ATHLETE', updated_at=now() where group_id='$GID' and user_id='$(uid <papel>)'; delete from group_membership_removals where group_id='$GID' and user_id='$(uid <papel>)'"` (para admin, troque `'ATHLETE'` por `'ADMIN'`). Depois, o aparelho dessa pessoa fecha e reabre o app.
- **R-LIMITE-LIGAR** (plano com limite de 25 atletas por grupo; o teste grátis é ilimitado): `PSQL "insert into subscriptions(owner_user_id,plan,cycle,status,asaas_customer_id,asaas_subscription_id,current_period_end,first_confirmed_at,created_at,updated_at) select id,'TITULAR','MONTHLY','ACTIVE','cus_qa','sub_qa',now()+interval '30 days',now(),now(),now() from access_users where email='qa-org+$TAG@saqz.local'"` e depois encher o grupo até 25 atletas ativos (sem contar o dono) com contas de mentira. Primeiro `N` = `PSQL "select count(*) from group_memberships gm join access_groups g on g.id=gm.group_id where gm.group_id='$GID' and gm.active and gm.user_id<>g.owner_user_id"`; depois, com `N` trocado pelo número: `PSQL "insert into access_users(id,firebase_subject,email,email_verified,display_name,created_at,updated_at) select gen_random_uuid(),'fake-'||g,'fake'||g||'@saqz.local',true,'Fake '||g,now(),now() from generate_series(1,25-N) g; insert into group_memberships(group_id,user_id,role,created_at,updated_at) select '$GID',id,'ATHLETE',now(),now() from access_users where firebase_subject like 'fake-%'"`. Conferência: o mesmo `select count(*)` de `N` agora dá 25.
- **R-LIMITE-DESLIGAR**: `PSQL "delete from group_memberships where user_id in (select id from access_users where firebase_subject like 'fake-%'); delete from access_users where firebase_subject like 'fake-%'; delete from subscriptions where owner_user_id=(select id from access_users where email='qa-org+$TAG@saqz.local')"`.
- **R-VENCIDO-LIGAR** (plano do organizador vencido): `PSQL "update organizer_trials set started_at=now()-interval '20 days', ends_at=now()-interval '20 days'+interval '336 hours' where owner_user_id=(select id from access_users where email='qa-org+$TAG@saqz.local')"`. **R-VENCIDO-DESLIGAR**: `PSQL "update organizer_trials set started_at=now(), ends_at=now()+interval '336 hours' where owner_user_id=(select id from access_users where email='qa-org+$TAG@saqz.local')"`.

> As receitas R-LIMITE e R-VENCIDO foram escritas a partir do esquema (V13 e V48 das migrações) e **não foram executadas** por quem escreveu o roteiro. Se alguma falhar, registre a mensagem do `psql` e marque BLOQUEADO só os passos que dependem dela.

## Preparação

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | terminal | `stack.sh reset`, `stack.sh up`, `aparelhos.sh instalar`; dados dos apps apagados; definir as receitas acima | Backend em 18080 | `select count(*) from access_users` = 0 | ☐ | ☐ |
| P2 | ORG | Criar conta como no passo 1 do roteiro 01 (nome `QA Org <data>`, celular `11900000001`) e o grupo `QA Vôlei <data>` como no passo 3 | `Grupo criado!` | `select count(*) from access_groups where name='QA Vôlei <data>'` = 1 | ☐ | ☐ |
| P3 | ATL, ATL2, ATL3, ATL4 | Em cada aparelho, "Criar conta ›" pela tela inicial, **sem convite** (nome, e-mail, celular, senha). A conta de ATL4 é criada em CONV-006 (C6.2), no aparelho de ATL3; a de ATL5, em SAI-005 | Início sem grupos, com `Tenho um convite` | `select count(*) from access_users` = 4 (ORG, ATL, ATL2, ATL3) | ☐ | ☐ |
| P4 | ORG | Gestão → "Convidar por link"; anotar o código (R-CÓDIGO) | Tela `Convidar` com `Código do grupo` | `select count(*) from group_invites where group_id='$GID'` = 1 | ☐ | ☐ |
| P5 | ATL, ATL2 | Receber o link (R-ENTREGAR) → "Entrar no grupo" → cadastro de atleta com apelidos `Atl` e `Atl2` → "Salvar meu perfil" | `Você entrou no QA Vôlei <data>` | `select count(*) from group_memberships where group_id='$GID' and active` = 3 | ☐ | ☐ |
| P6 | ORG | Grupo → "Membros e permissões" → `QA Atl2 <data>` → "Tornar administrador" | Linha de `QA Atl2 <data>` com `Administrador` | `select role from group_memberships where user_id='$(uid atl2)' and group_id='$GID'` = `ADMIN` | ☐ | ☐ |

## CONV-001 · O convite do grupo [CRÍTICO] [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| C1.1 | ORG | Gestão → "Convidar por link"; "Mostrar QR Code" | `Código do grupo` com 8 caracteres, `Link não expira`, `Link do convite` iniciando por `https://links.saqz.app/?saqz_invite=`; no QR: `O convite vale até você trocar o código` | `select length(short_code), expires_at is null from group_invites where group_id='$GID'` = `8\|t` | ☐ | ☐ |
| C1.2 | ATL2 (admin) | Reabrir o app; grupo → Gestão → "Convidar por link" | O mesmo código e o mesmo link de C1.1, em outro aparelho | O código mostrado é o de R-CÓDIGO | ☐ | ☐ |
| C1.3 | ATL | Grupo: procurar "Convidar por link" na listagem | Não existe `Convidar por link` na tela do grupo de ATL | `api atl GET /api/groups/$GID/invite` = `403` | ☐ | ☐ |
| C1.4 | banco + ORG | Tornar o convite legado vencido: `update group_invites set short_code=null, token_digest=sha256('legado-qa'::bytea), expires_at=now()-interval '1 day' where group_id='$GID'`. ORG sai da tela e abre "Convidar por link" de novo | Aparece um código novo de 8 caracteres e `Link não expira` | `select short_code is not null, expires_at is null from group_invites where group_id='$GID'` = `t\|t`; anotar o código novo | ☐ | ☐ |
| C1.5 | ATL4 (conta criada, sem grupo) | Início → "Tenho um convite" → digitar o código em **minúsculas e sem hífen** → "Entrar no grupo" | Tela `Convite recebido` com `QA Vôlei <data>` e o botão `Entrar no grupo` (não tocar ainda) | — | ☐ | ☐ |
| C1.6 | ATL4 | Fechar a folha sem entrar (BACK ou "Ver outros grupos") | Continua sem grupo | `select count(*) from group_memberships where user_id='$(uid atl4)'` = 0 | ☐ | ☐ |

## CONV-002 · Trocar o código [CRÍTICO] [MULTI]

Anotar o código atual como `COD1`.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| C2.1 | ORG | Convidar → "Trocar código" → na folha, "Cancelar" | Folha `Trocar o código?` com `Quem tem o código ou o link antigo não vai conseguir entrar.`; depois de cancelar, o código continua `COD1` | `short_code` = `COD1`, `rotated_at` nulo | ☐ | ☐ |
| C2.2 | ORG | "Trocar código" → confirmar | Código novo `COD2` diferente de `COD1` e link novo | `select short_code, rotated_at is not null from group_invites where group_id='$GID'` = `COD2\|t` | ☐ | ☐ |
| C2.3 | ATL4 | Entregar o link com `COD1` (R-ENTREGAR) | `Convite inválido` e `Peça um novo convite pra quem te convidou.` | `select count(*) from group_memberships where user_id='$(uid atl4)'` = 0 | ☐ | ☐ |
| C2.4 | ATL2 (admin) | Fechar a tela Convidar e abrir de novo | Código `COD2` (o novo) | — | ☐ | ☐ |
| C2.5 | ATL | Pedir a troca pela API: `api atl POST /api/groups/$GID/invite -d '{}'` | `403`; o código no banco segue `COD2` | `short_code` = `COD2` | ☐ | ☐ |

## CONV-005 · Entrar com o convite [MULTI]

Convite direto (grupo sem aprovação) para ATL3. A aprovação fica desligada até CONV-006.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| C5.1 | ATL3 | Início → "Tenho um convite" → colar o link com `COD2` → "Entrar no grupo" → cadastro com apelido `Atl3` → "Salvar meu perfil" | `Você entrou no QA Vôlei <data>` e depois o grupo | `select role, membership_type, active from group_memberships where user_id='$(uid atl3)' and group_id='$GID'` = `ATHLETE\|AVULSO\|t` | ☐ | ☐ |
| C5.2 | ORG | Reabrir Início e o grupo | ATL3 na lista de "Membros e permissões"; na central de avisos só existe o aviso de primeiro membro, de ATL (preparação), e nada novo para ATL3 | `select count(*) from group_memberships where group_id='$GID' and active` = 4; `select count(*) from group_messages where group_id='$GID' and channel='FIRST_MEMBER'` = 1 | ☐ | ☐ |
| C5.3 | ATL3 | Entregar o mesmo link outra vez (já é membro) | Abre o grupo, sem tela de erro e sem novo cadastro | contagem de C5.2 inalterada (4) | ☐ | ☐ |
| C5.4 | ORG | Entregar o próprio link ao aparelho de ORG (o dono não passa pelo limite) | Abre o grupo, sem tela de limite | contagem inalterada (4) | ☐ | ☐ |
| C5.5 | banco + ATL4 | R-LIMITE-LIGAR (grupo com 25 atletas). ATL4 usa o link com `COD2` | `O grupo atingiu o limite de participantes` e `Este grupo atingiu o limite de atletas do plano. Não há fila de espera.` | `select count(*) from group_memberships where user_id='$(uid atl4)'` = 0; `select count(*) from group_entry_requests where user_id='$(uid atl4)'` = 0 | ☐ | ☐ |
| C5.6 | banco | R-LIMITE-DESLIGAR | — | `select count(*) from subscriptions` = 0 | ☐ | ☐ |
| C5.7 | banco + ATL4 | R-VENCIDO-LIGAR. ATL4 usa o link com `COD2` | `Não foi possível entrar` e `Este grupo não está recebendo novos atletas agora. Fale com o organizador.` (se aparecer o texto do limite, registrar o literal: a nota pede a mensagem neutra) | `select count(*) from group_memberships where user_id='$(uid atl4)'` = 0 | ☐ | ☐ |
| C5.8 | banco | R-VENCIDO-DESLIGAR | — | `select ends_at > now() from organizer_trials` = `t` | ☐ | ☐ |
| C5.9 | ATL4 | Rate limit: em "Tenho um convite", tentar 11 códigos inexistentes e diferentes (`22222222`, `33333333`, `44444444`, `55555555`, `66666666`, `77777777`, `88888888`, `99999999`, `AAAAAAAA`, `BBBBBBBB`, `CCCCCCCC`) | Nos 10 primeiros: `Convite inválido`; no 11º: `Muitas tentativas` e `Aguarde … antes de tentar de novo.` | `select invalid_count from invite_redemption_limits where user_id='$(uid atl4)'` = 10 | ☐ | ☐ |
| C5.10 | ATL4 | Com o bloqueio ativo, entregar o link **válido** (`COD2`) | Continua `Muitas tentativas` (a espera vale também para o código certo) | ATL4 sem membership | ☐ | ☐ |
| C5.11 | banco | Liberar: `delete from invite_redemption_limits` | — | contagem 0 | ☐ | ☐ |
| C5.12 | — | Grupo excluído: coberto em SAI-008 (passo S8.5) | — | — | ☐ | ☐ |

## CONV-006 · O pedido de entrada [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| C6.1 | ORG | Convidar → ligar "Aprovar pedidos para entrar" | Interruptor ligado; texto `Ligado, quem abre o link pede para entrar e você aprova. Desligado, entra na hora.` | `select entry_requires_approval from access_groups where id='$GID'` = `t` | ☐ | ☐ |
| C6.2 | ATL3 | Perfil → "Sair da conta"; entrar com `qa-atl4+<tag>@saqz.local` | Início de ATL4, sem grupos | — | ☐ | ☐ |
| C6.3 | ATL4 | Entregar o link (`COD2`) | Tela `Convite recebido` com `O admin precisa aprovar sua entrada.` e o botão `Pedir pra entrar` | — | ☐ | ☐ |
| C6.4 | ATL4 | "Pedir pra entrar" → cadastro de atleta (apelido `Atl4`, posição, nível) → "Salvar meu perfil" | `Pedido enviado para o Organizador` (ou `Pedido enviado`) e `Seu pedido já foi pro organizador.` | `select nickname, athlete_registration_completed from group_entry_requests where user_id='$(uid atl4)'` = `Atl4\|t`; `group_memberships` de ATL4 = 0 linhas | ☐ | ☐ |
| C6.5 | ATL4 | Voltar à Início | Cartão `Pedido enviado · aguardando o organizador` com `Cancelar pedido`; nada do grupo aparece | — | ☐ | ☐ |
| C6.6 | ORG | Central de avisos ("Notificações") | Aviso do pedido de ATL4 (texto "pediu para entrar no QA Vôlei <data>" [push: só produção] no push; aqui só o aviso) | `select count(*) from group_messages where group_id='$GID' and channel='ENTRY_REQUEST'` = 1 | ☐ | ☐ |
| C6.7 | ATL2 (admin) | Central de avisos | O mesmo aviso (admins também recebem) | `select count(*) from group_notifications n join group_messages m on m.id=n.message_id where m.channel='ENTRY_REQUEST' and n.recipient_id='$(uid atl2)'` = 1 | ☐ | ☐ |
| C6.8 | ORG | Convidar → "Pedidos para entrar" | Linha `QA Atl4 <data>` com a hora do pedido no fuso do grupo (conferir: o horário bate com o relógio de São Paulo, não UTC) | `select requested_at at time zone 'America/Sao_Paulo' from group_entry_requests` | ☐ | ☐ |
| C6.9 | ATL4 | Pedir de novo enquanto pendente: entregar o link outra vez | `Pedido enviado`; continua um único pedido | `select count(*) from group_entry_requests where user_id='$(uid atl4)'` = 1; `ENTRY_REQUEST` em `group_messages` continua 1 | ☐ | ☐ |
| C6.10 | ATL4 | Início → "Cancelar pedido" → na folha `Cancelar o pedido para entrar em QA Vôlei <data>?` → "Sim, cancelar" (dois toques seguidos em "Sim, cancelar") | Toast `Pedido cancelado.` e o cartão some | `select count(*) from group_entry_requests where user_id='$(uid atl4)'` = 0 | ☐ | ☐ |
| C6.11 | ORG | Reabrir "Pedidos para entrar" | A lista não tem mais ATL4 | — | ☐ | ☐ |
| C6.12 | ATL4 | Pedir de novo (como C6.3 e C6.4, apelido `Atl4`) | Novo pedido pendente e novo aviso ao organizador (a nota: cada pedido novo avisa) | `select count(*) from group_entry_requests` = 1; `ENTRY_REQUEST` em `group_messages` = 2 | ☐ | ☐ |
| C6.13 | ATL | Permissão: `api atl GET /api/groups/$GID/entry-requests` | `403` | — | ☐ | ☐ |
| C6.14 | ATL4 | Rede: ativar modo avião, abrir Início, desligar, reabrir o app | Cartão `Pedido enviado · aguardando o organizador` continua | — | ☐ | ☐ |

## CONV-003 · Desativar o convite [MULTI]

O pedido de ATL4 (C6.12) segue pendente. A nota diz que o app ainda não tem a ação (DEB-032); o app atual tem `Desativar convite`. Registrar como divergência a favor do app se funcionar.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| C3.1 | ORG | Convidar → "Desativar convite" → folha `Desativar o convite?` com `Quem tem o código ou o link não vai conseguir entrar.` → "Desativar" (dois toques seguidos) | Um único toast `Convite desativado.`; a tela passa a `Crie um código para convidar a galera` com `O convite não expira. Você pode trocar o código quando quiser.` | `select count(*) from group_invites where group_id='$GID'` = 0; `select count(*) from group_invite_deactivations where group_id='$GID'` = 1 | ☐ | ☐ |
| C3.2 | ATL4 | Entregar o link com `COD2` (o código que vigorava antes de desativar) | `Convite inválido` e `Peça um novo convite pra quem te convidou.` | ATL4 segue sem membership | ☐ | ☐ |
| C3.3 | ORG | Fechar a tela e abrir "Convidar por link" outra vez | Continua `Crie um código para convidar a galera` (reabrir não recria o convite) | `select count(*) from group_invites where group_id='$GID'` = 0 | ☐ | ☐ |
| C3.4 | ATL2 (admin) | Abrir Convidar | A mesma tela sem convite | — | ☐ | ☐ |
| C3.5 | ORG | Convidar → "Pedidos para entrar" | O pedido de ATL4 continua na lista | `select count(*) from group_entry_requests` = 1 | ☐ | ☐ |
| C3.6 | ATL | Permissão: `api atl DELETE /api/groups/$GID/invite` | `403` | `group_invite_deactivations` segue 1 | ☐ | ☐ |
| C3.7 | ORG | "Criar código" | Código novo `COD3` e link; a tela volta a `Código do grupo` | `select short_code from group_invites where group_id='$GID'` = `COD3`; `select count(*) from group_invite_deactivations` = 0 | ☐ | ☐ |

## CONV-008 · Recusar o pedido [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| C8.1 | ATL | Permissão: `api atl DELETE /api/groups/$GID/entry-requests/$(uid atl4)` | `403`; o pedido continua | `select count(*) from group_entry_requests` = 1 | ☐ | ☐ |
| C8.2 | ORG | Convidar → "Pedidos para entrar" → "Recusar" em `QA Atl4 <data>` → folha `Recusar o pedido de QA Atl4 <data>?` com `A pessoa não entra no grupo. Ela precisa pedir de novo se quiser tentar outra vez.` → confirmar | Toast `Pedido recusado.`; a lista atualiza na hora sem ATL4 | `select count(*) from group_entry_requests` = 0 | ☐ | ☐ |
| C8.3 | ATL4 | Reabrir Início (fechar e abrir o app) | O cartão `Pedido enviado · aguardando o organizador` some; nenhum aviso de recusa | `select count(*) from group_notifications where recipient_id='$(uid atl4)'` = 0 | ☐ | ☐ |
| C8.4 | banco | Recusar pedido que sumiu: `api org DELETE /api/groups/$GID/entry-requests/$(uid atl4)` | Sucesso (2xx) sem efeito | contagem segue 0 | ☐ | ☐ |
| C8.5 | ATL4 | Pedir de novo na hora (link com `COD3`, cadastro `Atl4`) | Novo pedido pendente | `select count(*) from group_entry_requests` = 1 | ☐ | ☐ |
| C8.6 | ORG | Central de avisos | Novo aviso de pedido, o terceiro desde C6.4 | `select count(*) from group_messages where channel='ENTRY_REQUEST'` = 3 | ☐ | ☐ |

## CONV-007 · Aprovar o pedido [CRÍTICO] [MULTI] [push: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| C7.1 | ATL | Permissão: `api atl POST /api/groups/$GID/entry-requests/$(uid atl4)/approve` | `403` | `group_memberships` de ATL4 = 0 linhas | ☐ | ☐ |
| C7.2 | banco | Limite: R-LIMITE-LIGAR. ORG tenta "Aprovar" em ATL4 | Bloqueado; o pedido continua na lista (registrar o texto literal; esperado o do limite ou `Não foi possível concluir essa ação`) | `select count(*) from group_entry_requests` = 1 | ☐ | ☐ |
| C7.3 | banco | R-LIMITE-DESLIGAR; depois R-VENCIDO-LIGAR. ORG tenta "Aprovar" | Bloqueado, pedido continua (registrar o texto) | pedido = 1 | ☐ | ☐ |
| C7.4 | banco | R-VENCIDO-DESLIGAR | — | — | ☐ | ☐ |
| C7.5 | ORG | "Aprovar" em `QA Atl4 <data>` | Toast `QA Atl4 <data> entrou no grupo.` (ou `Pedido aprovado`); sai da lista na hora | `select role, membership_type, active from group_memberships where user_id='$(uid atl4)' and group_id='$GID'` = `ATHLETE\|AVULSO\|t`; `group_entry_requests` = 0; `select nickname from group_memberships where user_id='$(uid atl4)'` = `Atl4` (cadastro aplicado) | ☐ | ☐ |
| C7.6 | ATL4 | Reabrir Início; abrir o grupo | O grupo aparece; "Cadastro de atleta" já vem com o apelido `Atl4` | — | ☐ | ☐ |
| C7.7 | ATL4 | Central de avisos | [push: só produção] O push `Você entrou no QA Vôlei <data>` não existe no stack; conferir só que o banco tem a entrega: `select count(*) from group_notifications where recipient_id='$(uid atl4)'` (registrar o valor) | — | ☐ | ☐ |
| C7.8 | ORG | Aprovar quem já é membro: `api org POST /api/groups/$GID/entry-requests/$(uid atl4)/approve` | Sucesso (2xx) ou `404` de pedido que sumiu; nada muda (registrar o código) | `group_memberships` de ATL4 = 1 linha | ☐ | ☐ |

## SAI-001 · Sair do grupo [MULTI]

Preparação do bloco SAI (ATL3 e ATL4 respondem cada um no seu turno, trocando de conta no aparelho): ORG marca um jogo amanhã às `20h00`, quadra `Quadra QA`, endereço `Rua QA, 1`, **2 vagas** (como no passo 9 do roteiro 01) e `<jogo>` = `select id from games order by created_at desc limit 1`. Presenças com "Vou": ATL3 (confirmado 1/2), ATL (confirmado 2/2), ATL4 (espera 1º), ATL2 (espera 2º). Conferência: `select member_display_name,status,waitlist_sequence from game_attendance where game_id='<jogo>' order by waitlist_sequence nulls first`.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S1.1 | ATL | Grupo → "Sair do grupo" → na folha, "Continuar no grupo"; de novo "Sair do grupo" → "Sair do grupo" (dois toques seguidos) | Folha `Sair do grupo?` com `Você perderá o acesso a este grupo. Seu histórico e as cobranças existentes serão mantidos. Para voltar, será necessário entrar novamente por convite.`; depois, a Início sem o grupo | `select active from group_memberships where user_id='$(uid atl)' and group_id='$GID'` = `f`; `select requires_approval from group_membership_removals where user_id='$(uid atl)'` = `t` | ☐ | ☐ |
| S1.2 | ORG | Reabrir "Membros e permissões" | `QA Atl <data>` não aparece | — | ☐ | ☐ |
| S1.3 | ORG | O dono não sai: grupo → procurar "Sair do grupo" | Não existe `Sair do grupo` na tela do dono; pela API: `api org DELETE /api/groups/$GID/memberships/me` devolve `403` (dono imutável) | `select active from group_memberships where user_id='$(uid org)' and group_id='$GID'` = `t` | ☐ | ☐ |
| S1.4 | banco | Quem já não é membro: `api atl DELETE /api/groups/$GID/memberships/me` | Sucesso (2xx) sem efeito | linha de ATL inalterada (`active` = f) | ☐ | ☐ |
| S1.5 | ATL | Rede: reabrir o app depois da saída | O grupo não aparece mais e o jogo não aparece em "Próximos jogos" | — | ☐ | ☐ |

O admin que sai está em SAI-002 (passo S2.3) e SAI-004.

## SAI-002 · O que acontece com as presenças de quem sai [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S2.1 | ATL3 | Logo após o passo S1.1 (ordem de chegada): reabrir o cartão do jogo | ATL3 segue `Você está confirmado.` | — | ☐ | ☐ |
| S2.1b | ATL4 | Reabrir Início (fechar e abrir o app) | Cartão passa de espera para `Você está confirmado.` (a vaga de ATL subiu) | ATL `DECLINED`, ATL4 `CONFIRMED`, ATL2 `WAITLISTED` com sequência 1; `select source::text,old_status,new_status from attendance_events where game_id='<jogo>' order by occurred_at desc limit 1` = `SYSTEM\|WAITLISTED\|CONFIRMED` | ☐ | ☐ |
| S2.1c | ATL2 | Reabrir o jogo | `Lista de espera · 1º` | — | ☐ | ☐ |
| S2.2 | ORG | Anotar `select promotion_mode from access_groups where id='$GID'` (`MODO0`) e passar o grupo para escolha manual: `update access_groups set promotion_mode='MANUAL' where id='$GID'`. ATL4 (confirmado) sai do grupo (como S1.1) | ATL4 vira "Não vou" e a vaga **fica aberta** | ATL4 `DECLINED`; ATL2 continua `WAITLISTED`; `select count(*) from game_attendance where game_id='<jogo>' and status='CONFIRMED'` = 1 | ☐ | ☐ |
| S2.3 | ATL2 (admin, na espera) | Sair do grupo (como S1.1) | Sai da fila de espera; perde a permissão de administrador | `select status from game_attendance where game_id='<jogo>' and member_user_id='$(uid atl2)'` = `DECLINED`; membership de ATL2 `active` = `f`; nenhum aviso para a pessoa que saiu: `select count(*) from group_notifications where recipient_id='$(uid atl2)'` não aumenta | ☐ | ☐ |
| S2.4 | ORG | Reabrir o jogo | Confirmados `1` (ATL3), espera vazia, vaga aberta | — | ☐ | ☐ |
| S2.5 | banco | Restaurar o ambiente: R-RESTAURAR atl, atl2 ADMIN e atl4; `update access_groups set promotion_mode='<MODO0>' where id='$GID'` | — | `select count(*) from group_memberships where group_id='$GID' and active` = 5 | ☐ | ☐ |
| S2.6 | ORG | Convidados: "Ajustar vagas" para `6`. ATL3 (confirmado): "Levar convidado" duas vezes (`Conv A`, `Conv B`), em seguida ORG espera até 70 s | Os dois convidados entram na lista de espera e sobem: `3 confirmados` de ATL3 e seus convidados | `select count(*) from game_attendance where game_id='<jogo>' and status='CONFIRMED' and (member_user_id='$(uid atl3)' or host_user_id='$(uid atl3)')` = 3 (se a coluna do anfitrião tiver outro nome, usar `\d game_attendance`) | ☐ | ☐ |
| S2.7 | ATL3 | Sair do grupo (como S1.1) | Sai; as 3 vagas voltam | os 3 registros de ATL3 e convidados `DECLINED`; `CONFIRMED` do jogo diminui em 3 | ☐ | ☐ |
| S2.8 | banco | Jogo já iniciado: R-RESTAURAR atl3; `update games set starts_at=now()-interval '1 hour', confirmation_deadline=now()-interval '3 hours' where id='<jogo>'`. ATL (confirmado nesse jogo) sai do grupo | Nada muda no jogo iniciado: a presença de ATL segue como estava | `select status from game_attendance where game_id='<jogo>' and member_user_id='$(uid atl)'` igual ao de antes da saída | ☐ | ☐ |
| S2.9 | banco | Restaurar: R-RESTAURAR atl; `update games set starts_at=now()+interval '1 day', confirmation_deadline=now()+interval '20 hours' where id='<jogo>'` | — | — | ☐ | ☐ |

## SAI-003 · Remover membro [MULTI]

Pré-requisito: todos de volta (S2.5 e S2.9). Para ter dois administradores: ORG torna `QA Atl <data>` administrador também.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S3.1 | ORG | "Membros e permissões" → `QA Atl4 <data>` → "Editar jogador" → "Tornar mensalista" (R$ `50,00`, dia `10`) → "Salvar"; depois "Remover do grupo" | Folha `Remover QA Atl4 <data> do grupo?` com `Perde o acesso e sai das próximas listas de presença. Os jogos que já aconteceram continuam no histórico.` e `É mensalista: a mensalidade deixa de valer.`; após "Remover", some da lista | `active` de ATL4 = `f` | ☐ | ☐ |
| S3.2 | ATL4 | Reabrir o app | O grupo some da Início; nenhum aviso de remoção | `select count(*) from group_notifications where recipient_id='$(uid atl4)'` não aumenta | ☐ | ☐ |
| S3.3 | ORG | Remover um administrador: "Membros e permissões" → `QA Atl2 <data>` → "Remover do grupo" → confirmar | Removido | `active` de ATL2 = `f` | ☐ | ☐ |
| S3.4 | banco | R-RESTAURAR atl2 ADMIN; R-RESTAURAR atl4 | — | — | ☐ | ☐ |
| S3.5 | ATL2 (admin) | Remover um atleta: `QA Atl3 <data>` → "Remover do grupo" → confirmar | Removido | `active` de ATL3 = `f` | ☐ | ☐ |
| S3.6 | ORG | Tornar `QA Atl <data>` administrador (R-RESTAURAR atl3 antes). ATL2 (admin) tenta remover `QA Atl <data>` (outro admin) | Aviso `Só o organizador remove um admin. Toque aqui para fechar o aviso.`; ATL continua no grupo. Se removeu, FALHOU (a nota manda recusar) | `active` de ATL = `t` | ☐ | ☐ |
| S3.7 | ATL2 (admin) | Remover o dono: `api atl2 DELETE /api/groups/$GID/athletes/$(uid org)` | `403` (dono imutável); na tela, a linha do dono não tem "Remover do grupo" | `active` de ORG = `t` | ☐ | ☐ |
| S3.8 | ATL3 (atleta) | `api atl3 DELETE /api/groups/$GID/athletes/$(uid atl4)`; na tela, abrir o perfil de outro atleta | `403`; não há "Remover do grupo" para atleta | `active` de ATL4 = `t` | ☐ | ☐ |
| S3.9 | ORG | Remover quem já saiu: remover ATL3 uma vez; ATL2 (com a tela do ATL3 aberta antes) toca "Remover do grupo" → confirmar | Sucesso sem efeito, sem erro na tela | linha de ATL3 inalterada (`active` = `f`, uma única linha em `group_membership_removals`) | ☐ | ☐ |
| S3.10 | banco | R-RESTAURAR atl3; ATL volta a atleta (`update group_memberships set role='ATHLETE' where user_id='$(uid atl)' and group_id='$GID'`) | — | — | ☐ | ☐ |

## SAI-004 · Admin que sai, é removido ou rebaixado [MULTI]

A delegação financeira exige a conta de recebimentos (Asaas), que o stack local não tem: `select count(*) from receivable_delegations` = 0 nos quatro eventos. O que se confere aqui é o papel; **a revogação da delegação em si fica sem passo local** (vai para produção ou para os testes de integração).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S4.1 | ATL2 (admin) | Sair do grupo (como S1.1) — já feito em S2.3; repetir se o ambiente foi restaurado | Perde o papel de administrador | `role` de ATL2 volta a `ATHLETE` ao voltar (ver S4.4) | ☐ | ☐ |
| S4.2 | ORG | Remover um administrador (como S3.3) | Removido | `active` = `f` | ☐ | ☐ |
| S4.3 | ORG | Rebaixar: R-RESTAURAR atl2 ADMIN; "Membros e permissões" → `QA Atl2 <data>` → "Retirar acesso de administrador" | Continua no grupo como atleta (texto da ação: `Continua no grupo, mas deixa de administrá-lo.`) | `role` = `ATHLETE`, `active` = `t` | ☐ | ☐ |
| S4.4 | ORG | Voltar a ser admin: "Tornar administrador" em `QA Atl2 <data>` | Administrador de novo; a delegação não volta sozinha (sem passo local, ver acima) | `role` = `ADMIN` | ☐ | ☐ |
| S4.5 | ATL2 | Reabrir o app | Gestão volta a aparecer no grupo | — | ☐ | ☐ |

## SAI-005 · Limite de atletas do plano: por grupo, só quem é membro [MULTI]

Mesma receita de CONV-005 (R-LIMITE-LIGAR). Antes de ligar o limite, ORG cria um **segundo grupo** `QA Beach <data>` (o plano TITULAR só permite um grupo) e anota o convite dele (`COD-B`). `QA Atl5 <data>` é uma conta nova que nunca esteve no grupo ("Criar conta ›" sem convite, celular `11900000006`, no aparelho de ATL3/ATL4). No começo do bloco os atletas reais ativos (sem o dono) são ATL, ATL2, ATL3 e ATL4; a aprovação de entrada está desligada.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S5.1 | ORG | Criar o grupo `QA Beach <data>` como no passo 3 do roteiro 01 e abrir "Convidar por link" nele | `Grupo criado!` e um código de convite (`COD-B`) | `select count(*) from access_groups where deleted_at is null and owner_user_id=(select id from access_users where email='qa-org+<tag>@saqz.local')` = 2 | ☐ | ☐ |
| S5.2 | banco | R-LIMITE-LIGAR no `QA Vôlei <data>` até **25** atletas ativos (21 de mentira) | — | a contagem de atletas ativos do grupo = 25 | ☐ | ☐ |
| S5.3 | ATL5 | Entregar o link do `QA Vôlei <data>` (código atual) | Limite 25 com 25 no grupo: entrada **bloqueada**; `O grupo atingiu o limite de participantes` e `Este grupo atingiu o limite de atletas do plano. Não há fila de espera.` | `select count(*) from group_memberships where user_id='$(uid atl5)'` = 0 | ☐ | ☐ |
| S5.4 | ATL2 (admin) | Sair do grupo (como SAI-001, S1.1) | Saiu | contagem de atletas ativos = 24 (a vaga volta na hora, sem esperar) | ☐ | ☐ |
| S5.5 | ATL5 | Entregar o link outra vez e fazer o cadastro (apelido `Atl5`) | Com 24 no grupo: entrada **permitida**; `Você entrou no QA Vôlei <data>` | contagem = 25 | ☐ | ☐ |
| S5.6 | ATL5 | Mesma pessoa em dois grupos: entrar também no `QA Beach <data>` (convite `COD-B`), com o Vôlei cheio | Entra no Beach (cada grupo conta o seu limite) | `select count(*) from group_memberships where user_id='$(uid atl5)' and active` = 2; no Beach `select count(*) from group_memberships where group_id=(select id from access_groups where name='QA Beach <data>') and active and role='ATHLETE'` = 1 | ☐ | ☐ |
| S5.7 | ATL2 + ORG | Plano ilimitado: `update subscriptions set plan='ORGANIZADOR'`. ATL2 (saiu, volta só por pedido) entrega o link; ORG aprova em "Pedidos para entrar" | Pedido pendente e aprovado sem checagem de limite (26 atletas); ATL2 volta como **atleta** | contagem = 26; `role` de ATL2 = `ATHLETE` | ☐ | ☐ |
| S5.8 | banco | R-LIMITE-DESLIGAR | — | `select count(*) from subscriptions` = 0 | ☐ | ☐ |

## SAI-006 · Pendências financeiras de quem sai [MULTI]

Cobranças inseridas por banco (a regra é sobre o que acontece com cobranças já criadas). Três cobranças pendentes, `due_date` daqui a 5 dias: mensalidade de `QA Atl <data>`, diária de `QA Atl3 <data>` (jogo `<jogo>`) e mensalidade de `QA Atl2 <data>`:

```sql
insert into group_charges(id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at)
select gen_random_uuid(),'<gid>',u.id,'MONTHLY',date_trunc('month',now())::date,5000,current_date+5,'PENDING',g.owner_user_id,g.owner_user_id,now(),now()
from access_users u, access_groups g where g.id='<gid>' and u.email in ('qa-atl+<tag>@saqz.local','qa-atl2+<tag>@saqz.local');
insert into group_charges(id,group_id,member_user_id,kind,game_id,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at)
select gen_random_uuid(),'<gid>',u.id,'GAME','<jogo>',2000,current_date+5,'PENDING',g.owner_user_id,g.owner_user_id,now(),now()
from access_users u, access_groups g where g.id='<gid>' and u.email='qa-atl3+<tag>@saqz.local';
```

(Receita escrita a partir do esquema V5; **não foi executada**.)

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S6.1 | ATL | Início → "Minhas cobranças" antes de sair | Em `Em aberto`: `Mensalidade · <mês>` | `select status from group_charges where member_user_id='$(uid atl)'` = `PENDING` | ☐ | ☐ |
| S6.2 | ATL | Sair do grupo (como S1.1) | Saiu | `select status from group_charges where member_user_id='$(uid atl)'` = `PENDING` (continua) | ☐ | ☐ |
| S6.3 | ORG | Financeiro do grupo → "Quem ainda não pagou" | A cobrança de `QA Atl <data>` continua na lista | idem S6.2 | ☐ | ☐ |
| S6.4 | ORG | Remover `QA Atl3 <data>` (como S3.1) | Removido | `select status from group_charges where member_user_id='$(uid atl3)'` = `PENDING` | ☐ | ☐ |
| S6.5 | ORG | Financeiro → "Criar cobranças do mês", mês seguinte | Em `Selecione os mensalistas ativos` não aparece quem saiu | `select count(*) from group_charges where billing_month=date_trunc('month',now()+interval '1 month')::date` = 0 | ☐ | ☐ |
| S6.6 | ATL | Voltar ao grupo (SAI-007, passos S7.1 a S7.3) e abrir "Minhas cobranças" | `Em aberto` com `Mensalidade · <mês>` (a cobrança antiga) | `status` = `PENDING` | ☐ | ☐ |
| S6.7 | — | Convidado confirmado do anfitrião que sai → cobrança cancelada; conta excluída com cobrança pendente | **Sem passo local**: a cobrança do convidado depende de diária definida no grupo (`Se o convidado entrar no jogo, a taxa de … vem para você.`), que o roteiro não configura. A conta excluída está em SAI-009 (S9.3 e S9.4) | — | ☐ | ☐ |

## SAI-007 · Voltar ao grupo depois de sair ou ser removido [MULTI]

Estado de entrada: `QA Atl <data>` saiu (S6.2), `QA Atl3 <data>` foi removido (S6.4) e `QA Atl2 <data>` já voltou como atleta (S5.7). Convite do grupo ativo (código atual) e aprovação **desligada**.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S7.1 | ATL | Saiu por conta própria; convite sem aprovação: entregar o link | Não entra direto: tela `Pedido enviado` ou `Convite recebido` com pedido; Início `Pedido enviado · aguardando o organizador` | `select count(*) from group_entry_requests where user_id='$(uid atl)'` = 1; `active` = `f` | ☐ | ☐ |
| S7.2 | ORG | Convidar → "Pedidos para entrar" → "Aprovar" em `QA Atl <data>` | Toast `QA Atl <data> entrou no grupo.` | `active` = `t`, `role` = `ATHLETE`, `select count(*) from group_membership_removals where user_id='$(uid atl)'` = 0 (marca limpa) | ☐ | ☐ |
| S7.3 | ATL | Reabrir o app | O grupo volta à Início; presenças de jogos futuros seguem `Não vou` | — | ☐ | ☐ |
| S7.4 | ORG | Ligar "Aprovar pedidos para entrar" (C6.1). ATL3 (removido) entrega o link | Pedido pendente (convite com aprovação) | `group_entry_requests` de ATL3 = 1 | ☐ | ☐ |
| S7.5 | ORG | "Recusar" o pedido de `QA Atl3 <data>` | Pedido some | `active` de ATL3 = `f`; `group_entry_requests` = 0 | ☐ | ☐ |
| S7.6 | ATL2 | Ex-admin volta: já voltou em S5.7. Reabrir o app de ATL2 e o grupo | Volta como **atleta**: a Gestão não aparece para ATL2 | `select role from group_memberships where user_id='$(uid atl2)' and group_id='$GID'` = `ATHLETE` | ☐ | ☐ |
| S7.7 | banco | Limite cheio: R-LIMITE-LIGAR e ATL3 pede de novo; ORG tenta aprovar | Aprovação bloqueada (registrar o texto); sem membership ativa | `active` de ATL3 = `f` | ☐ | ☐ |
| S7.8 | banco | R-LIMITE-DESLIGAR; ORG desliga a aprovação | — | `entry_requires_approval` = `f` | ☐ | ☐ |
| S7.9 | — | Grupo excluído: coberto em SAI-008 (S8.5) | — | — | ☐ | ☐ |

## SAI-008 · Excluir o grupo [MULTI] [push: só produção]

Usa o `QA Beach <data>` (criado em S5.1, com `QA Atl5 <data>` de membro), para manter o `QA Vôlei <data>` até SAI-009. Antes: ORG cria também `QA Extra <data>` (com R-LIMITE-DESLIGAR já feito), ficando com 3 grupos.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S8.1 | ATL5 e ATL2 | Permissão: `api atl5 DELETE /api/groups/<id-do-Beach>` (atleta) e `api atl2 DELETE /api/groups/$GID` (ATL2 voltou como atleta; para testar admin de verdade, ORG o torna administrador antes). Na tela, procurar "Excluir grupo" | `403` nos dois; não existe `Excluir grupo` para admin nem para atleta | `deleted_at` dos dois grupos nulo | ☐ | ☐ |
| S8.2 | ORG | Com 3 grupos (Vôlei, Beach, Extra), tentar criar um quarto | Bloqueado pelo limite de grupos do plano | `select count(*) from access_groups where deleted_at is null and owner_user_id=(select id from access_users where email='qa-org+<tag>@saqz.local')` = 3 | ☐ | ☐ |
| S8.3 | ORG | Abrir `QA Beach <data>` → "Editar" → rolar → "Excluir grupo" → folha `Excluir o QA Beach <data>?` → "Excluir grupo" | O grupo some da Início de ORG | `select deleted_at is not null from access_groups where name='QA Beach <data>'` = `t` | ☐ | ☐ |
| S8.4 | ATL5 | Reabrir o app | `QA Beach <data>` não aparece; o `QA Vôlei <data>` continua. [push: só produção] o push de "grupo excluído" não existe no stack; o aviso fica no banco | `select count(*) from group_messages where group_id=(select id from access_groups where name='QA Beach <data>') and channel='AUTO_CONFIRMATION'` = 1 | ☐ | ☐ |
| S8.5 | ATL3 (sem o grupo Beach) | Entregar o convite do `QA Beach <data>` (`COD-B`) | `Grupo excluído` (texto da nota; o app não traz essa frase nos recursos, então pode aparecer `Convite inválido`: registrar o literal como divergência) | ATL3 sem membership no Beach | ☐ | ☐ |
| S8.6 | ORG | Excluir de novo o mesmo grupo: `api org DELETE /api/groups/<id-do-Beach>` | `404` | — | ☐ | ☐ |
| S8.7 | ORG | Criar outro grupo `QA Novo <data>` (dono com 3 grupos exclui 1 e pode criar) | `Grupo criado!` | grupos ativos do ORG = 3 de novo | ☐ | ☐ |

## SAI-009 · Excluir a conta [CRÍTICO] [MULTI]

Pré-requisito: `QA Atl4 <data>` cria o grupo `QA Dono4 <data>` e `QA Atl2 <data>` entra nele pelo convite. Antes de começar, anotar `<id-de-ATL>` = `select id from access_users where email='qa-atl+<tag>@saqz.local'` (depois da exclusão o e-mail some da linha).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| S9.1 | ATL | Perfil → "Excluir conta" → marcar `Entendo as consequências e quero excluir minha conta.` → senha **errada** → "Confirmar senha e excluir" | Texto da folha com `Se você assinou pela App Store ou pelo Google Play, cancele também na loja.`; depois do erro: `Não foi possível confirmar sua identidade. Use o método de entrada desta conta e tente novamente.`; a conta segue ativa | `select deleted_at is null from access_users where id='<id-de-ATL>'` = `t` | ☐ | ☐ |
| S9.2 | ATL | Mesma tela, senha `SenhaQA2026` | A sessão termina e o app volta à tela de entrada | `select deleted_at is not null, display_name from access_users where id='<id-de-ATL>'` = `t\|Conta excluída`; `select count(*) from account_deletion_requests where user_id='<id-de-ATL>'` = 1 | ☐ | ☐ |
| S9.3 | ORG | Reabrir o grupo e o jogo | ATL não aparece em "Membros e permissões"; presenças e cobranças antigas mostram `Conta excluída`; presenças futuras viram `Não vou` | `select member_display_name,status from game_attendance where member_user_id='<id-de-ATL>'` = `Conta excluída\|DECLINED`; `select member_display_name from group_charges where member_user_id='<id-de-ATL>'` = `Conta excluída` | ☐ | ☐ |
| S9.4 | ORG | Financeiro → "Quem ainda não pagou" | A cobrança que era de ATL aparece como `Conta excluída` e pode ser dispensada ("Dispensar") | `status` segue `PENDING` até dispensar | ☐ | ☐ |
| S9.5 | ATL | Entrar de novo com o mesmo e-mail e `SenhaQA2026` ou "Criar conta ›" com ele (a limpeza do login no emulador roda em segundo plano, cerca de 1 min) | Conta **nova**, sem grupos nem histórico: Início com `Tenho um convite` | `select count(*) from group_memberships where user_id in (select id from access_users where email='qa-atl+<tag>@saqz.local')` = 0 e o `id` é diferente de `<id-de-ATL>` | ☐ | ☐ |
| S9.6 | ATL4 (dono de `QA Dono4 <data>`) | Perfil → "Excluir conta" com a senha certa | A sessão termina | `select deleted_at is not null, name from access_groups where id='<id-do-QA-Dono4>'` = `t\|Grupo excluído` | ☐ | ☐ |
| S9.7 | ATL2 (membro do grupo de ATL4) | Reabrir o app | `QA Dono4 <data>` some da Início. [push: só produção] o push de grupo excluído não existe no stack | `select count(*) from group_memberships where group_id='<id-do-QA-Dono4>' and active` (registrar; o grupo está excluído) | ☐ | ☐ |
| S9.8 | banco + ATL3 | Conta suspensa: `update access_users set suspended_at=now() where email='qa-atl3+<tag>@saqz.local'`. ATL3 abre o app e tenta entrar | Entrada recusada (conta suspensa); nada é apagado | `deleted_at` nulo; `select count(*) from group_memberships where user_id='$(uid atl3)' and active` igual ao de antes | ☐ | ☐ |
| S9.9 | terminal | `api atl3 DELETE /api/session` logo depois de `tok atl3` (o login precisa ter menos de 5 minutos) | Recusado por conta suspensa (registrar o código HTTP) | `deleted_at` nulo | ☐ | ☐ |
| S9.10 | — | Assinatura Asaas cancelada em segundo plano; assinatura da Apple ou do Google (DEB-030) | **Sem passo local**: Asaas desligado e sem loja neste stack. Conferir só a fila: `select user_id, completed_at from account_deletion_requests` | linha de ATL com `completed_at` preenchido depois de cerca de 1 min, ou registrar o estado | ☐ | ☐ |

## Rodadas

Rodar o roteiro inteiro duas vezes, cada uma com `stack.sh reset` e apps limpos. Marcar o resultado na coluna da plataforma do aparelho do papel que age na linha (se a linha tiver dois, a do primeiro citado).

| Papel | Rodada 1: ORG no iPhone, ATL no Android | Rodada 2: ORG no Android, ATL no iPhone |
| --- | --- | --- |
| ORG | iPhone 17 (`BAB08A28-4E8C-4AA3-BF6A-54904566BF0F`) | `Saqz_QA_A` |
| ATL | `Saqz_QA_A` | iPhone 17 |
| ATL2 | iPhone 17e (`F22B2FF2-494E-4CF3-BD18-FE48ECE5A85B`) | `Saqz_QA_B` |
| ATL3 e ATL4 | `Saqz_QA_B` | iPhone 17e |

Link para cada plataforma: iPhone com `xcrun simctl openurl <udid> 'saqz://links.saqz.app/?saqz_invite=<código>'`; Android com `adb -s <id> shell am start -a android.intent.action.VIEW -d 'https://links.saqz.app/?saqz_invite=<código>' app.saqz`.

Relate também o tempo total e qualquer passo em que precisou usar adb/simctl no lugar do mobile-mcp.
