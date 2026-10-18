# Roteiro 06 · Papéis, lista de membros, telefone e link de presença do WhatsApp

Regras do cofre cobertas: MEM-001, MEM-002, MEM-005 e LINK-001 a LINK-005. Se o texto do app e a nota divergirem, vale a nota; anote a divergência no relato. Nenhuma destas regras é crítica; MEM-001 tem [push: só produção].

Papéis: **ORG** (dono), **ATL** (atleta), **ATL2** (vira administrador na preparação), **ATL3** (atleta; sobe e desce de administrador em MEM-001), **ATL4** e **ATL5** (pessoas de fora do grupo, para LINK-003 e MEM-001 "não membro"). ATL3, ATL4 e ATL5 dividem um aparelho: para trocar de papel, Perfil → "Sair da conta" e entrar com a conta do outro, em todo passo que pedir outro papel. Os aparelhos de cada papel estão em "Rodadas" no fim.

Use `<data>` = dia de hoje (`DD/MM`) nos nomes e `<tag>` = `DDMM` (sem barra) nos e-mails. Contas: `qa-org+<tag>@saqz.local`, `qa-atl+<tag>@saqz.local`, `qa-atl2+<tag>@saqz.local`, `qa-atl3+<tag>@saqz.local`, `qa-atl4+<tag>@saqz.local`, `qa-atl5+<tag>@saqz.local`, senha `SenhaQA2026`, celulares `11900000001` a `11900000006`, nomes `QA Org <data>`, `QA Atl <data>`, `QA Atl2 <data>`, `QA Atl3 <data>`, `QA Atl4 <data>`, `QA Atl5 <data>`, apelidos `Atl`, `Atl2`, `Atl3`, `Atl4`, `Atl5`. Grupo: `QA Vôlei <data>`.

As conferências de banco pressupõem banco limpo no começo de **cada rodada**: `stack.sh reset` + `stack.sh up` (e apagar os dados do app nos aparelhos, como no roteiro 01).

**Ordem de execução**: Preparação → MEM-001 → MEM-002 → MEM-005 → LINK-001 → LINK-002 → LINK-003 e LINK-004 → LINK-005 (a tela do link é conferida nos passos anteriores; o bloco LINK-005 junta os textos).

## Receitas de apoio

Todas rodam no terminal da máquina, não num aparelho.

```sh
TAG=<tag>
PSQL() { docker exec saqz-multidev-db psql -U saqz -d saqz -Atc "$1"; }
tok() { curl -s "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=qa" -H 'Content-Type: application/json' -d "{\"email\":\"qa-$1+$TAG@saqz.local\",\"password\":\"SenhaQA2026\",\"returnSecureToken\":true}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["idToken"])'; }
api() { w=$1; m=$2; p=$3; shift 3; curl -s -w '\n%{http_code}\n' -X "$m" "http://127.0.0.1:18080$p" -H "Authorization: Bearer $(tok $w)" -H 'Content-Type: application/json' "$@"; }
uid() { PSQL "select id from access_users where email='qa-$1+$TAG@saqz.local'"; }
GID=$(PSQL "select id from access_groups where name='QA Vôlei <data>' and deleted_at is null")
JOGO=$(PSQL "select id from games where group_id='$GID' order by created_at desc limit 1")
```

`api <papel> <método> <caminho> [-d '<json>']` devolve o corpo e, na última linha, o código HTTP. Papéis: `org`, `atl`, `atl2`, `atl3`, `atl4`, `atl5`.

- **R-LEMBRETE**: cria um lembrete do jogo, que é o que carrega o link de presença (o botão "Avisar" do app faz o mesmo quando o grupo do WhatsApp está vinculado; o stack local não vincula). Devolve o código do link na primeira linha: `PSQL "with m as (insert into group_messages(id,group_id,author_id,author_name,channel,request_id,body,game_id) select gen_random_uuid(),g.id,g.owner_user_id,u.display_name,'REMINDER',gen_random_uuid(),'Lembrete QA',(select id from games where group_id=g.id order by created_at desc limit 1) from access_groups g join access_users u on u.id=g.owner_user_id where g.id='$GID' returning id) insert into notification_attendance_links(message_id) select id from m returning code" | head -1`. Cada chamada gera um lembrete e um código novos; anotar como `LA`, `LB`, `LC`. Receita escrita a partir do esquema (V45 e V71) e **não executada** por quem escreveu o roteiro; se o `psql` recusar, registrar a mensagem e usar o botão "Avisar" do jogo com o Uazapi falso ligado (`ferramentas/uazapi-falso.mjs`; o código sai de `select code from notification_attendance_links order by message_id desc limit 1`).
- **R-LINK `<papel>` `<caminho>`**: entrega o link de presença ao aparelho. O caminho é `attendance/<código>` para "Vou" e `attendance/<código>?saqz_intent=decline` para "Não vou". Android: `adb -s <id> shell am start -a android.intent.action.VIEW -d 'https://links.saqz.app/<caminho>' app.saqz`. iOS: `xcrun simctl openurl <udid> 'saqz://links.saqz.app/<caminho>'`; se o app não reagir ao esquema `saqz://`, tentar `saqz://links.saqz.app/?saqz_attendance=<código>` (e `&saqz_intent=decline`) e registrar qual forma funcionou. O README só garante o esquema `saqz://` para `?saqz_invite=`.
- **R-LINK-CONVITE**: o convite do grupo, como no roteiro 05 (`select short_code from group_invites where group_id='$GID'`), entregue com `https://links.saqz.app/?saqz_invite=<código>` (Android) ou `saqz://links.saqz.app/?saqz_invite=<código>` (iOS).

## Preparação

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | terminal | `stack.sh reset`, `stack.sh up`, `aparelhos.sh instalar`; dados dos apps apagados; definir as receitas acima | Backend em 18080 | `select count(*) from access_users` = 0 | ☐ | ☐ |
| P2 | ORG | Criar conta como no passo 1 do roteiro 01 (celular `11900000001`), o grupo `QA Vôlei <data>` como no passo 3 e **marcar um jogo** amanhã às `20h00`, quadra `Quadra QA`, endereço `Rua QA, 1`, **2 vagas** (passo 9 do roteiro 01) | `Grupo criado!`; no grupo `PRÓXIMO JOGO` e `0 confirmados` | `select capacity,status from games where group_id='$GID'` = `2\|PUBLISHED` | ☐ | ☐ |
| P3 | ATL, ATL2, ATL3 | Em cada aparelho, "Criar conta ›" sem convite (celulares `11900000002`, `…03`, `…04`; ATL = `11900000002`, ATL2 = `11900000003`, ATL3 = `11900000004`); entrar no grupo com R-LINK-CONVITE; cadastro de atleta com apelidos `Atl`, `Atl2`, `Atl3` | `Você entrou no QA Vôlei <data>` | `select count(*) from group_memberships where group_id='$GID' and active` = 4 | ☐ | ☐ |
| P4 | ATL4 | "Criar conta ›" sem convite (celular `11900000005`); fica na Início, **sem entrar** no grupo | Início com `Tenho um convite` | `select count(*) from group_memberships where user_id='$(uid atl4)'` = 0 | ☐ | ☐ |
| P5 | ORG | Grupo → "Membros e permissões" → `QA Atl2 <data>` → "Tornar administrador" | Linha de `QA Atl2 <data>` com `Administrador` | `select role from group_memberships where user_id='$(uid atl2)' and group_id='$GID'` = `ADMIN` | ☐ | ☐ |
| P6 | terminal | R-LEMBRETE; anotar `LA` | Um código de 43 caracteres | `select count(*) from notification_attendance_links` = 1 | ☐ | ☐ |

## MEM-001 · Os três papéis e como se muda de papel [MULTI] [push: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| MEM1.1 | ORG, ATL2, ATL | Cada um abre o grupo `QA Vôlei <data>` | Os três veem o grupo com `PRÓXIMO JOGO` e o jogo de amanhã às 20h00 | — | ☐ | ☐ |
| MEM1.2 | ATL | Procurar na tela do grupo "Convidar por link", "Editar" e "Membros e permissões"; na API: `api atl GET /api/groups/$GID/entry-requests` | Atleta não vê `Convidar por link` nem `Editar`; API `403` | — | ☐ | ☐ |
| MEM1.3 | ATL2 (admin) | Gestão → "Convidar por link"; API `api atl2 GET /api/groups/$GID/entry-requests` | A tela `Convidar` abre com `Código do grupo`; API `200` | — | ☐ | ☐ |
| MEM1.4 | ORG | Mesma API: `api org GET /api/groups/$GID/entry-requests` | `200` | — | ☐ | ☐ |
| MEM1.5 | ATL2 (admin) | Mudar papéis: `api atl2 PUT /api/groups/$GID/memberships/$(uid atl3)/role -d '{"role":"ADMIN"}'`; na tela, "Membros e permissões" → `QA Atl3 <data>` | `403`; a folha de ATL3 não tem `Tornar administrador` | `role` de ATL3 = `ATHLETE` | ☐ | ☐ |
| MEM1.6 | ATL2 (admin) | Admin em si mesmo: `api atl2 PUT /api/groups/$GID/memberships/$(uid atl2)/role -d '{"role":"ATHLETE"}'` | `403`; ATL2 segue administrador | `role` de ATL2 = `ADMIN` | ☐ | ☐ |
| MEM1.7 | ATL | Atleta muda papel: `api atl PUT /api/groups/$GID/memberships/$(uid atl)/role -d '{"role":"ADMIN"}'` | `403` | `role` de ATL = `ATHLETE` | ☐ | ☐ |
| MEM1.8 | ATL4 (não membro) | `api atl4 GET /api/groups/$GID/athletes`, `api atl4 GET /api/groups/$GID/memberships`, `api atl4 GET /api/groups/$GID/invite` | `404` nas três (quem não é membro não sabe que o grupo existe) | — | ☐ | ☐ |
| MEM1.9 | ORG | "Membros e permissões" → `QA Atl3 <data>` → "Tornar administrador" (texto de apoio: `Pode marcar jogos e avisar a galera`); tocar duas vezes seguidas | Linha de `QA Atl3 <data>` com `Administrador`; sem erro pelo toque duplo | `role` de ATL3 = `ADMIN` (uma só linha) | ☐ | ☐ |
| MEM1.10 | ATL3 | Fechar e abrir o app; grupo → Gestão → "Convidar por link". [push: só produção] o push `Você agora é administrador de QA Vôlei <data>` não existe no stack; aqui só se confere o efeito | Gestão aparece e a tela `Convidar` abre; `api atl3 GET /api/groups/$GID/invite` = `200` | — | ☐ | ☐ |
| MEM1.11 | ORG | Promover quem já é admin: `api org PUT /api/groups/$GID/memberships/$(uid atl3)/role -d '{"role":"ADMIN"}'` | Sucesso (2xx) sem erro; nada muda | `role` = `ADMIN`; `updated_at` igual ao de antes da chamada (`select updated_at from group_memberships where user_id='$(uid atl3)' and group_id='$GID'` antes e depois) | ☐ | ☐ |
| MEM1.12 | ORG | "Membros e permissões" → `QA Atl3 <data>` → "Retirar acesso de administrador" (texto de apoio: `Continua no grupo, mas deixa de administrá-lo.`) | Linha sem `Administrador` | `role` de ATL3 = `ATHLETE` | ☐ | ☐ |
| MEM1.13 | ATL3 | Central de avisos ("Notificações"); fechar e abrir o app; Gestão | Sem aviso de rebaixamento (a pessoa rebaixada não é avisada); Gestão não aparece; `api atl3 GET /api/groups/$GID/invite` = `403` | `select count(*) from group_notifications where recipient_id='$(uid atl3)'` igual ao de antes de MEM1.12 | ☐ | ☐ |
| MEM1.14 | ORG | Rebaixar quem já é atleta: `api org PUT /api/groups/$GID/memberships/$(uid atl3)/role -d '{"role":"ATHLETE"}'` | Sucesso (2xx) sem erro; nada muda | `role` = `ATHLETE` | ☐ | ☐ |
| MEM1.15 | ORG | Dono nele mesmo: `api org PUT /api/groups/$GID/memberships/$(uid org)/role -d '{"role":"ATHLETE"}'`; na tela, a linha `você` do dono | `403` (dono imutável); a linha do dono não tem `Retirar acesso de administrador` | `select role from group_memberships where user_id='$(uid org)' and group_id='$GID'` = `ADMIN` (o dono é a linha ADMIN da tabela; papel de dono é resolvido na consulta) | ☐ | ☐ |
| MEM1.16 | ORG + ATL2 | Rede e permissão: ATL2 abre Gestão → "Convidar por link" e **deixa a tela aberta**; ORG rebaixa ATL2 ("Retirar acesso de administrador"); ATL2 toca "Trocar código" e confirma | Erro `Não foi possível concluir essa ação`; o servidor recusa (`403` no log ou em `api atl2 GET /api/groups/$GID/invite`) | `role` de ATL2 = `ATHLETE`; `short_code` do convite não mudou | ☐ | ☐ |
| MEM1.17 | ORG | Devolver: "Tornar administrador" em `QA Atl2 <data>` | Administrador de novo | `role` = `ADMIN` | ☐ | ☐ |

## MEM-002 · A lista de membros [MULTI]

Preparação do bloco: ORG torna `QA Atl <data>` mensalista (Editar jogador → "Tornar mensalista" → valor `50,00`, dia `10` → "Salvar") para existir valor e situação financeira a esconder.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| MEM2.0 | ORG | Preparação do bloco acima | Cartão `R$ 50,00 / mês · vence dia 10` em `QA Atl <data>` | `select membership_type, monthly_fee_cents, monthly_due_day from group_memberships where user_id='$(uid atl)' and group_id='$GID'` = `MENSALISTA\|5000\|10` | ☐ | ☐ |
| MEM2.1 | ATL3 (atleta) | Grupo → "Membros e permissões" | Todos os membros com nome, apelido, `Mensalista` ou `Avulso`, `Administrador` em ATL2 e `você` em si; **sem** `R$ 50,00 / mês` em nenhuma linha | — | ☐ | ☐ |
| MEM2.2 | ATL3 | `api atl3 GET /api/groups/$GID/athletes` | `200`; na linha de `QA Atl <data>`, `monthlyFeeCents` é `null` e `financialStatus` é o valor de situação desconhecida (registrar o literal) | — | ☐ | ☐ |
| MEM2.3 | ATL3 | Filtro financeiro: `api atl3 GET "/api/groups/$GID/athletes?financialStatus=PENDING"` | `403` | — | ☐ | ☐ |
| MEM2.4 | ORG e ATL2 | `api org GET /api/groups/$GID/athletes` e `api atl2 GET /api/groups/$GID/athletes`; na tela, "Editar jogador" de `QA Atl <data>` | Os dois veem `monthlyFeeCents` = `5000` e uma `financialStatus` de situação conhecida; a tela mostra `R$ 50,00 / mês · vence dia 10` | — | ☐ | ☐ |
| MEM2.5 | ATL3 | Inativos: `api atl3 GET "/api/groups/$GID/athletes?includeInactive=true"` (executar de novo depois de MEM2.6) | Recusado ou ignorado, e **sem** linhas de quem saiu (a nota: atleta nunca pede inativos; hoje pode devolver 200 com inativos: registrar o código e se vieram linhas) | — | ☐ | ☐ |
| MEM2.6 | ATL | Sair do grupo (Gestão fora; botão "Sair do grupo" na tela do grupo → "Sair do grupo") | Saiu | `active` de ATL = `f` | ☐ | ☐ |
| MEM2.7 | ORG e ATL3 | Reabrir "Membros e permissões" | `QA Atl <data>` não aparece nas duas listas | — | ☐ | ☐ |
| MEM2.8 | ORG | Admin pede inativos: `api org GET "/api/groups/$GID/athletes?includeInactive=true"` | `200` e a linha de ATL aparece (histórico), com `active` falso | — | ☐ | ☐ |
| MEM2.9 | ATL4 (não membro) | `api atl4 GET /api/groups/$GID/athletes` | `404` | — | ☐ | ☐ |
| MEM2.10 | ORG | "Buscar membro" → `Atl2` | Só `QA Atl2 <data>` na lista; na API `api org GET "/api/groups/$GID/athletes?search=Atl2"` devolve uma linha | — | ☐ | ☐ |
| MEM2.11 | banco + ORG | Restaurar ATL: `update group_memberships set active=true where user_id='$(uid atl)' and group_id='$GID'; delete from group_membership_removals where user_id='$(uid atl)' and group_id='$GID'` (reposição de ambiente) | — | — | ☐ | ☐ |
| MEM2.12 | ORG + ATL | Papéis independentes por grupo: ORG cria o grupo `QA Beach <data>`; ATL entra pelo convite dele; ORG torna ATL administrador **só no Beach** | ATL vê Gestão no Beach e **não** vê no `QA Vôlei <data>` | `select group_id, role from group_memberships where user_id='$(uid atl)'` = duas linhas, `ATHLETE` no Vôlei e `ADMIN` no Beach | ☐ | ☐ |

## MEM-005 · O telefone: quem vê [MULTI]

A pessoa que escolhe é `QA Atl3 <data>` (celular `11900000004`, ver P3). Observadores: **ATL** (atleta), **ATL2** (admin) e **ORG** (dono). A escolha mora na conta: Perfil → "Editar perfil" → `Quem vê seu celular` (`A galera`, `Só administradores`, `Ninguém`; `Vale para todos os grupos.`). Conferir o que cada um vê pela API (`phone` na linha de ATL3) e, na tela, em "Membros e permissões" → `QA Atl3 <data>` → "Ver perfil" (registrar o texto literal do celular; deve conter `11900000004` ou ficar vazio). Cada vez que ATL3 salva, os observadores fecham e abrem o app.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| MEM5.1 | ATL3 | Abrir "Quem vê seu celular" sem mexer | Marcado `Só administradores` (o padrão) | `select phone_visibility from access_users where id='$(uid atl3)'` = `ADMINS` | ☐ | ☐ |
| MEM5.2 | ATL, ATL2, ORG | No padrão, olhar a linha de ATL3 (tela e API `api <papel> GET /api/groups/$GID/athletes`) | ATL: `phone` vazio (campo vazio, **sem aviso**); ATL2 e ORG: `11900000004` | — | ☐ | ☐ |
| MEM5.3 | ATL3 | Mudar para `A galera` → salvar | Salvo, sem erro | `phone_visibility` = `EVERYONE` | ☐ | ☐ |
| MEM5.4 | ATL, ATL2, ORG, ATL3 | Os quatro olham a linha de ATL3 e ATL3 olha o próprio perfil (`api atl3 GET /api/athletes/me`) | Todos veem `11900000004` (incluindo a própria pessoa) | — | ☐ | ☐ |
| MEM5.5 | ATL3 | Mudar para `Só administradores` → salvar | Salvo | `phone_visibility` = `ADMINS` | ☐ | ☐ |
| MEM5.6 | ATL, ATL2, ORG, ATL3 | Mesma conferência | ATL: vazio; ATL2 e ORG: `11900000004`; ATL3: vê o próprio | — | ☐ | ☐ |
| MEM5.7 | ATL3 | Mudar para `Ninguém` → salvar | Salvo | `phone_visibility` = `NOBODY` | ☐ | ☐ |
| MEM5.8 | ATL, ATL2, ORG, ATL3 | Mesma conferência | Para ATL, ATL2 e ORG o campo vem vazio (nem dono nem admin veem: decisão de 18/10); ATL3 continua vendo o próprio número | — | ☐ | ☐ |
| MEM5.9 | ATL3 | Rede: modo avião; mudar para `A galera` → salvar | Erro de salvar na tela e o valor não muda | `phone_visibility` continua `NOBODY` | ☐ | ☐ |
| MEM5.10 | ATL3 | Desligar o modo avião e tocar de novo em salvar | Salvo | `phone_visibility` = `EVERYONE` | ☐ | ☐ |
| MEM5.11 | ORG (dono de dois grupos) | A escolha vale para todos os grupos: no `QA Beach <data>` (criado em MEM2.12, se ATL3 estiver lá; senão entrar com o convite dele), ATL vê o celular de ATL3 conforme `A galera` | Mesmo comportamento no outro grupo, sem nova escolha | `select count(*) from information_schema.columns where table_name='group_memberships' and column_name like '%phone%'` = 0 (a escolha não é por grupo) | ☐ | ☐ |

## LINK-001 · Os links de presença [MULTI]

Estado: o jogo de amanhã às 20h00 está publicado com 2 vagas; `LA` foi gerado na preparação (P6).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| LINK1.1 | terminal | Conferir o lembrete que carrega os links | Um lembrete do jogo com os dois links: "Vou" (`attendance/<LA>`) e "Não vou" (o mesmo com `saqz_intent=decline`) | `select m.channel, m.game_id='$JOGO', length(l.code) from notification_attendance_links l join group_messages m on m.id=l.message_id` = `REMINDER\|t\|43` | ☐ | ☐ |
| LINK1.2 | terminal | **Só com o Uazapi falso e o grupo do WhatsApp vinculado**: conferir o que o backend mandaria | Botões `😍 Vou, me confirma!` → `https://links.saqz.app/attendance/<código>` e `😢 Não conseguirei ir!` → o mesmo com `?saqz_intent=decline`. No stack padrão (WhatsApp desligado): sem passo | `~/.saqz-multidev/logs/uazapi-mock.jsonl` com `/send/menu` | ☐ | ☐ |
| LINK1.3 | ATL | R-LINK atl `attendance/<LA>` | O app abre no jogo certo (cartão `Jogo` com `Quadra QA`) e registra "Vou" (ver LINK2.1) | — | ☐ | ☐ |
| LINK1.4 | ATL | R-LINK atl `attendance/<LA>?saqz_intent=decline` | O app abre no jogo certo e mostra a folha `Não vai poder ir?` (ver LINK2.3) | — | ☐ | ☐ |

## LINK-002 · Abrir o link sendo membro [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| LINK2.1 | ATL (logado) | R-LINK atl `attendance/<LA>` ("Vou", jogo futuro) | Tela `Confirmar presença` com `Presença confirmada para sua conta.` e o cartão do jogo (dia, hora, `Quadra QA`) em cima | `select status from game_attendance where game_id='$JOGO' and member_user_id='$(uid atl)'` = `CONFIRMED` | ☐ | ☐ |
| LINK2.2 | ATL | Abrir o mesmo link de novo (repetição) | A mesma tela, sem erro e sem efeito duplo | `select count(*) from attendance_events where game_id='$JOGO' and member_user_id='$(uid atl)'` igual ao de antes da repetição | ☐ | ☐ |
| LINK2.3 | ATL | R-LINK atl `attendance/<LA>?saqz_intent=decline` | Folha com `Não vai poder ir?`, `Vamos tirar seu nome da lista deste jogo.`, `Se mudar de ideia, toque em Vou no lembrete.` e os botões `Voltar` e `Sim, não vou`; **nada gravado ainda** | `status` de ATL = `CONFIRMED` | ☐ | ☐ |
| LINK2.4 | ATL | Tocar em `Voltar`; abrir o link "Não vou" de novo; tocar duas vezes seguidas em `Sim, não vou` | Depois do `Voltar`, nada muda; depois de `Sim, não vou`: `Pronto: seu nome saiu da lista deste jogo.` e o cartão do jogo em cima; sem erro pelo toque duplo | `status` de ATL = `DECLINED` | ☐ | ☐ |
| LINK2.5 | ATL | Vou depois de Não vou: R-LINK atl `attendance/<LA>` | `Presença confirmada para sua conta.` (a resposta vale de novo; se aparecer o estado antigo, registrar: achado N-18 da rodada de 13/10) | `status` = `CONFIRMED` | ☐ | ☐ |
| LINK2.6 | ATL2 | R-LINK atl2 `attendance/<LA>` | `Presença confirmada para sua conta.` (2 de 2 vagas) | `status` de ATL2 = `CONFIRMED` | ☐ | ☐ |
| LINK2.7 | ATL3 | R-LINK atl3 `attendance/<LA>` com o jogo cheio | `Você entrou na lista de espera. Confira sua posição no jogo.` e o cartão do jogo | `select status, waitlist_sequence from game_attendance where game_id='$JOGO' and member_user_id='$(uid atl3)'` = `WAITLISTED\|1` | ☐ | ☐ |
| LINK2.8 | banco + ATL | Prazo vencido, jogo ainda não começou: `update games set confirmation_deadline=now()-interval '1 minute' where id='$JOGO'`. ATL abre o link "Não vou" e confirma `Sim, não vou`; depois o link "Vou" | Em ambos o membro ainda responde; o "Vou" vai para a espera: `Você entrou na lista de espera. Confira sua posição no jogo.` | `select status from game_attendance where member_user_id='$(uid atl)' and game_id='$JOGO'` = `WAITLISTED` | ☐ | ☐ |
| LINK2.9 | banco + ATL | Jogo começou: `update games set starts_at=now()-interval '1 hour', confirmation_deadline=now()-interval '3 hours' where id='$JOGO'`. ATL abre o link "Vou" | `O jogo já começou.` (nada é gravado) | `status` de ATL igual ao de LINK2.8 | ☐ | ☐ |
| LINK2.10 | banco + ATL | Restaurar: `update games set starts_at=now()+interval '1 day', confirmation_deadline=now()+interval '20 hours' where id='$JOGO'`. Jogo em rascunho: `update games set status='DRAFT' where id='$JOGO'`; ATL abre o link | `Esse jogo não está mais aberto.` | — | ☐ | ☐ |
| LINK2.11 | banco + ATL | `update games set status='PUBLISHED' where id='$JOGO'`. Depois ORG cancela o jogo pelo app ("Cancelar jogo"); ATL abre o link | `Esse jogo foi cancelado.` | `select status from games where id='$JOGO'` = `CANCELLED` | ☐ | ☐ |
| LINK2.12 | ATL | Código inventado: R-LINK atl `attendance/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` (43 caracteres) | Mensagem genérica: `Não foi possível confirmar. O prazo pode ter encerrado ou este jogo não está disponível para sua conta.` | — | ☐ | ☐ |
| LINK2.13 | ATL | Chute de códigos: abrir 11 links diferentes inexistentes (trocar o último caractere de LINK2.12 por `B`, `C`, `D`, `E`, `F`, `G`, `H`, `J`, `K`, `M`) | Os 10 primeiros com a mensagem genérica; o 11º com bloqueio temporário (registrar o texto) | `select invalid_count from attendance_link_resolution_limits where user_id='$(uid atl)'` = 10 | ☐ | ☐ |
| LINK2.14 | banco | Liberar: `delete from attendance_link_resolution_limits` | — | contagem 0 | ☐ | ☐ |
| LINK2.15 | ATL (sem sessão) | Rede e login: com o jogo publicado de novo (R-LEMBRETE e `update games set status='PUBLISHED'`, jogo novo se o cancelado não voltar), Perfil → "Sair da conta"; abrir o link "Vou" | Tela de entrada; depois do login, o app volta ao jogo e mostra a resposta | resposta registrada em `game_attendance` | ☐ | ☐ |

O jogo cancelado em LINK2.11 não volta. Para os blocos seguintes, ORG marca **outro jogo** amanhã às 21h00, **2 vagas**, e gera um lembrete novo com R-LEMBRETE (`LA2`); `JOGO` passa a ser o novo (`JOGO=$(PSQL "select id from games where group_id='$GID' and status='PUBLISHED' order by created_at desc limit 1")`) e ATL, ATL2 respondem "Vou" para lotá-lo (2 de 2). Os lembretes seguem em ordem: `LA2` (anterior a qualquer troca de código).

## LINK-003 · Abrir o link sem ser membro [MULTI]

ATL4 e ATL5 estão fora do grupo. Ordem: primeiro os casos em que ATL4 **não** entra, por último o que entra.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| LINK3.1 | ORG | Troca de código: Gestão → "Convidar por link" → "Trocar código" → confirmar (convite com `COD1` vira `COD2`). Gerar um lembrete novo: R-LEMBRETE → `LB` | Código novo | `select rotated_at is not null from group_invites where group_id='$GID'` = `t` | ☐ | ☐ |
| LINK3.2 | ATL4 | R-LINK atl4 `attendance/<LA2>` (lembrete **anterior** à troca) | Não vale como convite: `Convite inválido` (ou mensagem de link inválido; registrar o texto) e ATL4 não entra | `select count(*) from group_memberships where user_id='$(uid atl4)'` = 0 | ☐ | ☐ |
| LINK3.3 | ORG | Desativar o convite: "Desativar convite" → "Desativar". Depois gerar um lembrete novo: R-LEMBRETE → `LC` | `Convite desativado.` | `select count(*) from group_invites where group_id='$GID'` = 0 | ☐ | ☐ |
| LINK3.4 | ATL4 | R-LINK atl4 `attendance/<LB>` (anterior à desativação) | Não vale como convite; ATL4 não entra | contagem 0 | ☐ | ☐ |
| LINK3.5 | ATL5 | Aprovação: antes, ORG volta a criar o convite ("Criar código") e liga "Aprovar pedidos para entrar". ATL5 abre `attendance/<LC>` (posterior à desativação), faz login | Vira **pedido de entrada** (não entra direto); cadastro de atleta guardado no pedido; presença **não** registrada: `Sua entrada no grupo aguarda aprovação. Depois da aprovação, atualize para concluir seu cadastro e confirmar a presença.`; no cartão do jogo o local `Quadra QA` fica escondido | `select count(*) from group_entry_requests where user_id='$(uid atl5)'` = 1; `select count(*) from game_attendance where game_id='$JOGO' and member_user_id='$(uid atl5)'` = 0 | ☐ | ☐ |
| LINK3.6 | ORG | Desligar "Aprovar pedidos para entrar" e **aprovar** o pedido de ATL5 em "Pedidos para entrar" | `QA Atl5 <data> entrou no grupo.` | `group_memberships` de ATL5 `active` = `t` | ☐ | ☐ |
| LINK3.7 | ATL5 | Reabrir o link `attendance/<LC>` (depois de aprovada) | O app abre o cadastro de atleta (já preenchido com o que foi guardado) e, ao salvar, registra a presença: jogo cheio, então `Você entrou na lista de espera. Confira sua posição no jogo.` | `select status from game_attendance where game_id='$JOGO' and member_user_id='$(uid atl5)'` = `WAITLISTED` | ☐ | ☐ |
| LINK3.8 | ATL4 | Sem aprovação, qualquer momento, jogo cheio: R-LINK atl4 `attendance/<LC>`; entrar; cadastro de atleta (apelido `Atl4`) | Entra no grupo (`Você entrou no QA Vôlei <data>` ou equivalente), faz o cadastro e fica na lista de espera: `Você entrou na lista de espera. Confira sua posição no jogo.` | `select role, membership_type, active from group_memberships where user_id='$(uid atl4)' and group_id='$GID'` = `ATHLETE\|AVULSO\|t`; `status` = `WAITLISTED` | ☐ | ☐ |
| LINK3.9 | ORG | Removido antes: remover ATL3 do grupo ("Remover do grupo"); ATL3 abre `attendance/<LC>` | Não entra direto: vira pedido de entrada (`Pedido enviado`); presença não registrada | `select count(*) from group_entry_requests where user_id='$(uid atl3)'` = 1 | ☐ | ☐ |
| LINK3.10 | — | Jogo que já começou, para quem não é membro: **sem passo** (exigiria outra pessoa de fora; o caso do membro está em LINK2.9). A nota: entra no grupo e a presença do jogo não é registrada | — | — | ☐ | ☐ |
| LINK3.11 | — | Grupo excluído invalida o link: coberto no roteiro 05 (SAI-008) | — | — | ☐ | ☐ |

## LINK-004 · O cadastro de atleta antes da resposta [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| LINK4.1 | ATL4 | Membro novo, cadastro incompleto: já visto em LINK3.8. Conferir o texto da tela antes de salvar | `Complete seu cadastro de atleta neste grupo para confirmar a presença.` e depois a presença registrada | `athlete_registration_completed` de ATL4 = `t` depois de salvar | ☐ | ☐ |
| LINK4.2 | banco + ATL2 | Membro com cadastro incompleto: `update group_memberships set athlete_registration_completed=false where user_id='$(uid atl2)' and group_id='$GID'`. ATL2 abre `attendance/<LC>` | Cadastro de atleta primeiro; ao salvar, a presença é registrada (ATL2 já estava confirmado: segue `CONFIRMED`) | `athlete_registration_completed` = `t` depois | ☐ | ☐ |
| LINK4.3 | ATL | Membro com cadastro completo: abrir `attendance/<LC>` | Resposta direta, sem tela de cadastro | — | ☐ | ☐ |
| LINK4.4 | ATL5 | Grupo com aprovação, pedido pendente: já visto em LINK3.5 (sem resposta agora) e LINK3.7 (depois de aprovada o app abre o cadastro) | Conferir os dois textos da tela | — | ☐ | ☐ |

## LINK-005 · O que a tela do link diz [MULTI]

Esta tabela só reúne a conferência de texto; cada situação já aparece nos passos acima. Em **todas** as linhas, o cartão do jogo (dia, hora e local) aparece em cima do estado.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| LINK5.1 | ATL | "Vou", confirmado (LINK2.1 ou LINK2.5) | `Presença confirmada para sua conta.` e cartão do jogo com `Quadra QA` | — | ☐ | ☐ |
| LINK5.2 | ATL3 | "Vou", vai para a espera (LINK2.7) | `Você entrou na lista de espera. Confira sua posição no jogo.` e cartão do jogo | — | ☐ | ☐ |
| LINK5.3 | ATL | Folha de "Não vou", antes do toque (LINK2.3) | `Não vai poder ir?`, `Vamos tirar seu nome da lista deste jogo.`, `Se mudar de ideia, toque em Vou no lembrete.`; **nenhuma** promessa de avisar o grupo nem de "liberar vaga" | — | ☐ | ☐ |
| LINK5.4 | ATL | "Não vou", depois do toque (LINK2.4) | `Pronto: seu nome saiu da lista deste jogo.` | — | ☐ | ☐ |
| LINK5.5 | ATL5 | Não membro de grupo com aprovação (LINK3.5) | Cartão do jogo **sem** o local `Quadra QA`; texto `Sua entrada no grupo aguarda aprovação. Depois da aprovação, atualize para concluir seu cadastro e confirmar a presença.` | — | ☐ | ☐ |

## Rodadas

Rodar o roteiro inteiro duas vezes, cada uma com `stack.sh reset` e apps limpos. Marcar o resultado na coluna da plataforma do aparelho do papel que age na linha (se a linha tiver dois, a do primeiro citado).

| Papel | Rodada 1: ORG no iPhone, ATL no Android | Rodada 2: ORG no Android, ATL no iPhone |
| --- | --- | --- |
| ORG | iPhone 17 (`BAB08A28-4E8C-4AA3-BF6A-54904566BF0F`) | `Saqz_QA_A` (adb `emulator-5554`) |
| ATL | `Saqz_QA_A` (adb `emulator-5554`) | iPhone 17 |
| ATL2 | iPhone 17e (`F22B2FF2-494E-4CF3-BD18-FE48ECE5A85B`) | `Saqz_QA_B` (adb `emulator-5556`) |
| ATL3, ATL4 e ATL5 | `Saqz_QA_B` (adb `emulator-5556`) | iPhone 17e |

Relate também o tempo total e qualquer passo em que precisou usar adb/simctl no lugar do mobile-mcp.
