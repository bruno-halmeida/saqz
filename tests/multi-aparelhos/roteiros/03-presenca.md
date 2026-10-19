# Roteiro 03 · Presença: prazo, início, permissões, convidados, repetição e organizador

Cobre PRES-002 a PRES-013 (as regras de mais de uma pessoa; PRES-001 é de uma pessoa só e não entra).
Fonte: notas do cofre em `Obsidian Vault/Saqz/Regras/Presença e lista de espera/`; se o app e a nota divergirem, vale a nota.
Tabela de um aparelho com as mesmas regras: `tests/roteiros/presenca-e-lista-de-espera.md`.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas; **[push: só produção]**
o stack local não tem FCM/APNs (o item na central de avisos se confere aqui; o push em si, só em produção).

## Papéis e contas

Mesmas contas do [roteiro 01](01-dono-e-atletas.md): `<data>` = dia de hoje (`DD/MM`), `<tag>` = `DDMM`;
`qa-org+<tag>@saqz.local`, `qa-atl+<tag>@saqz.local`, `qa-atl2+<tag>@saqz.local`, `qa-atl3+<tag>@saqz.local`,
senha `SenhaQA2026`, celulares `11900000001` a `11900000004`, nomes `QA Org <data>`, `QA Atl <data>`,
`QA Atl2 <data>`, `QA Atl3 <data>`, apelidos `Atl`, `Atl2`, `Atl3`. Grupo `QA Vôlei <data>`.

| Papel | Rodada 1 | Rodada 2 |
| --- | --- | --- |
| ORG | iPhone 17 | `Saqz_QA_A` (Android) |
| ATL | `Saqz_QA_A` (Android) | iPhone 17 |
| ATL2 | iPhone 17e | `Saqz_QA_B` (Android) |
| ATL3 | `Saqz_QA_B` (Android) | iPhone 17e |

## Preparação (uma vez por rodada)

1. `stack.sh reset` + `stack.sh up`; apagar os dados do app nos quatro aparelhos (receita do roteiro 01).
2. Rodar os **passos 1 a 8 do roteiro 01** (ORG cria conta e grupo; ATL, ATL2 e ATL3 entram pelo convite e fazem o perfil), com os aparelhos da rodada.
3. ORG marca um jogo (passo 9 do roteiro 01: amanhã, 20h00, `Quadra QA`, `Rua QA, 1`, **vagas 3**). Esse é o jogo deste roteiro; **não** rode os passos 10 em diante do 01.
4. Anotar ids: `<G>` = `select id from access_groups where name='QA Vôlei <data>'`;
   `<J>` = `select id from games where group_id='<G>' order by created_at desc limit 1`.
5. Atalhos de banco (psql do README). Quando um passo disser **RESET(vagas, modo, prioridade)**, rodar:

```sql
delete from group_charges where game_id='<J>';
delete from attendance_events where game_id='<J>';
delete from game_attendance where game_id='<J>';
update games set status='PUBLISHED', capacity=<vagas>, waitlist_sequence_allocator=0, game_fee_cents=null,
  confirmation_deadline=now()+interval '2 days', starts_at=now()+interval '3 days' where id='<J>';
update access_groups set promotion_mode='<FIFO|MANUAL>', mensalista_priority=<true|false> where id='<G>';
update group_memberships set membership_type='AVULSO', mensalista_since=null where group_id='<G>';
```

   **MENSAL(nome)** deixa a pessoa mensalista:
   `update group_memberships set membership_type='MENSALISTA', mensalista_since=current_date where group_id='<G>' and user_id=(select id from access_users where display_name='QA <nome> <data>');`
   (se a restrição de mensalidade reclamar, acrescentar `monthly_fee_cents=5000`.)

   **ESTADO** (conferência da lista):
   `select member_display_name,guest_seq,status,waitlist_sequence from game_attendance where game_id='<J>' order by waitlist_sequence nulls first, member_display_name;`

   **EVENTOS** (auditoria):
   `select u.display_name,e.guest_seq,e.source,e.old_status,e.new_status,e.reason from attendance_events e join access_users u on u.id=e.member_user_id where e.game_id='<J>' order by e.occurred_at,e.id;`

6. Chamadas de API (só onde o passo pede; ids de membro `<U-atl>` etc. = `select id from access_users where display_name='QA Atl <data>'`):

```sh
tok() { curl -s "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake" \
  -H 'content-type: application/json' \
  -d "{\"email\":\"$1\",\"password\":\"SenhaQA2026\",\"returnSecureToken\":true}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["idToken"])'; }
B=http://127.0.0.1:18080/api/groups/<G>/games/<J>/attendance
# resposta própria: tok qa-atl+<tag>@saqz.local ; intent CONFIRM ou DECLINE
curl -s -o /dev/null -w '%{http_code}\n' -X PUT $B -H "authorization: Bearer $(tok <email>)" -H 'content-type: application/json' \
  -d '{"requestId":"<uuid>","intent":"CONFIRM"}'
# organizador por outra pessoa
curl -s -w '\n%{http_code}\n' -X POST $B/override -H "authorization: Bearer $(tok <email>)" -H 'content-type: application/json' \
  -d '{"requestId":"<uuid>","memberId":"<U-alvo>","intent":"CONFIRM","reason":"Avisou por fora"}'
```

Como o app só tem duas respostas em tela, as linhas "API" abaixo trazem o código HTTP esperado.
Textos do app citados nas colunas "Espere" vêm de `strings_home.xml`, `strings_game_response.xml`, `strings_game_waitlist.xml`,
`strings_game_guest.xml` e `strings_game_organizer.xml`. A Início mostra `Lista de espera · Nº`; "Ver o jogo" mostra
`Você está em Nº na lista de espera.`; as telas não recarregam sozinhas: trocar de aba Grupos → Início ou reabrir "Ver o jogo".

---

## PRES-002 · Início do jogo trava o atleta; o prazo nunca nega [CRÍTICO] [MULTI]

Passos por **ATL** e **ATL2** (respondem) e **ORG** (responde por outra pessoa). Modo `MANUAL` para ver o "Vou" tardio ficar na espera.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(3, FIFO, false) | — | ESTADO vazio | ☐ | ☐ |
| 2 | ATL | Início → "Vou" (antes do prazo) | `Você está confirmado.` | ESTADO: ATL `CONFIRMED` | ☐ | ☐ |
| 3 | ATL | Mesmo cartão: "Alterar" → "Não vou" (antes do prazo) | `Tudo bem, na próxima. Sua vaga já foi liberada.` | ATL `DECLINED` | ☐ | ☐ |
| 4 | banco + ATL2 | `update games set confirmation_deadline=now()+interval '90 seconds' where id='<J>'`; ATL2 já com "Ver o jogo" aberto; em até 60 s toca "Vou" (dentro do prazo) | `Você está confirmado.` (não vai para a espera; a borda exata do instante do prazo é do teste unitário, ver "O que ficou sem passo") | ATL2 `CONFIRMED` | ☐ | ☐ |
| 5 | banco + ATL3 | `update games set confirmation_deadline=now()-interval '1 minute' where id='<J>'`; `update access_groups set promotion_mode='MANUAL' where id='<G>'`; ATL3 reabre "Ver o jogo" | Aviso `O prazo acabou: quem confirmar agora entra na lista de espera e o gestor escolhe quem sobe.` | — | ☐ | ☐ |
| 6 | ATL3 | "Vou" (depois do prazo, antes do início) | `Você entrou na lista de espera.` (toast: `Você entrou na lista de espera. O organizador escolhe quem entra.`) e chip `Lista de espera · 1º` | ATL3 `WAITLISTED` | ☐ | ☐ |
| 7 | ATL3 | "Alterar" → "Não vou" (depois do prazo, atleta sempre pode) | `Tudo bem, na próxima. Sua vaga já foi liberada.` | ATL3 `DECLINED` | ☐ | ☐ |
| 8 | banco | `update games set starts_at=now()-interval '1 minute' where id='<J>'` | — | — | ☐ | ☐ |
| 9 | ATL | Reabrir "Ver o jogo" (jogo começou) | `Em andamento. Não dá mais para responder.` e nenhum botão `Vou`/`Não vou` | — | ☐ | ☐ |
| 10 | API (ATL) | `PUT $B` com `CONFIRM` e depois com `DECLINE`, tokens de ATL | os dois devolvem `409` (código `ATTENDANCE_FROZEN`) | ESTADO e EVENTOS iguais ao passo 7 | ☐ | ☐ |
| 11 | API (ATL2, confirmado) | `PUT $B` com `DECLINE` (depois do início) | `409` | ATL2 segue `CONFIRMED` | ☐ | ☐ |
| 12 | ORG | Abrir o jogo (Início → cartão do jogo ou Grupo → "Ver o jogo"); em ATL2 (confirmado) tocar "Tirar" → "Tirar" (motivo padrão) | Folha `Tirar Atl2 do jogo?` e `Atl2 vai receber um aviso, ok?`; depois da confirmação, ATL2 sai de Confirmados (organizador age depois do início) | ATL2 `DECLINED`; EVENTOS: última linha `ORGANIZER|CONFIRMED|DECLINED` com `reason` preenchido | ☐ | ☐ |
| 13 | ATL2 | Reabrir "Ver o jogo" | `Você não vai jogar.` e ainda sem botões de resposta | — | ☐ | ☐ |

## PRES-003 · Cada pessoa responde só por si [MULTI]

Pré: RESET(3, FIFO, false). Fora do grupo: criar `qa-fora+<tag>@saqz.local` (conta nova, sem entrar no grupo) só pela API (`accounts:signUp` do emulador) ou usar qualquer conta que não seja membro.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | Início → "Vou" | `Você está confirmado.` | ATL `CONFIRMED` | ☐ | ☐ |
| 2 | ATL2 | Início do ATL2 mostra o jogo sem a resposta do ATL: cartão `Sem resposta` (ninguém responde pelo colega no app) | `Vou` e `Não vou` ainda disponíveis para ATL2 | ATL2 sem linha em ESTADO | ☐ | ☐ |
| 3 | API (ATL2) | `PUT $B` com token de ATL2: responde só por ATL2 (o corpo não aceita `memberId`); com `intent` `CONFIRM` | `2xx` e ESTADO só ganha linha de ATL2; a linha do ATL não muda de versão | ATL2 `CONFIRMED`, ATL igual | ☐ | ☐ |
| 4 | API (ATL2, rota do organizador) | `POST $B/override` com token de ATL2, `memberId`=`<U-atl3>`, `CONFIRM`, `reason` `Teste` | `403` | ESTADO sem linha de ATL3 | ☐ | ☐ |
| 5 | API (ATL2, outro membro pelo `PUT`) | `PUT $B` com token de ATL2 e `memberId`=`<U-atl3>` no corpo | corpo extra ignorado: `2xx` afetando só ATL2 (não existe rota de atleta em outro membro) | ESTADO sem linha de ATL3 | ☐ | ☐ |
| 6 | API (de fora) | `PUT $B` e `GET $B` com token de `qa-fora` | `404` nos dois | — | ☐ | ☐ |
| 7 | ORG | Jogo → "Colocar alguém no jogo" → escolher `Atl3` → botão `Colocar Atl3 no jogo` (dono responde por outra pessoa; a folha usa o motivo `Escolha do organizador`) | Atl3 passa a Confirmados | ATL3 `CONFIRMED`; EVENTOS: `ORGANIZER|…|CONFIRMED` com `reason`=`Escolha do organizador` | ☐ | ☐ |
| 8 | ORG e ATL | ORG: Grupo → membros → `Atl` → papel `Administrador` → salvar. ATL abre o jogo e toca "Tirar" em `Atl3` → "Tirar" | Admin também age por outra pessoa (a folha `Tirar Atl3 do jogo?` aparece para ATL) | ATL3 `DECLINED`; EVENTOS: `ORGANIZER|CONFIRMED|DECLINED`, ator ATL (`select u.display_name from attendance_events e join access_users u on u.id=e.actor_user_id …`) | ☐ | ☐ |
| 9 | ATL3 | Reabrir "Ver o jogo" | `Você não vai jogar.` | — | ☐ | ☐ |

## PRES-004 · "Não vou" sempre vale e libera a vaga [MULTI]

Pré: RESET(2, FIFO, false) com a prioridade desligada para o "Vou" confirmar direto.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | "Vou" | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 2 | ATL3 | "Vou" | `Você está confirmado.` e `2 de 2 confirmados` | `CONFIRMED` | ☐ | ☐ |
| 3 | ATL2 | "Vou" (jogo cheio) | `Lista de espera · 1º` e `As vagas acabaram. Você entrou na lista de espera.` | ATL2 `WAITLISTED|1` | ☐ | ☐ |
| 4 | ORG | Sem resposta ainda; "Não vou" no cartão (sem resposta → não vou) | `Tudo bem, na próxima.`; nada muda na lista | ORG `DECLINED`; sem outro `SYSTEM` em EVENTOS | ☐ | ☐ |
| 5 | ATL | "Alterar" → "Não vou" (confirmado libera 1 vaga) | `Sua vaga foi liberada.` (toast) | ATL `DECLINED`; ATL2 `CONFIRMED`; último evento `SYSTEM|WAITLISTED|CONFIRMED` | ☐ | ☐ |
| 6 | ATL2 | Trocar de aba Grupos → Início | `Você está confirmado.` (a fila andou sem reabrir o app) | — | ☐ | ☐ |
| 7 | ATL | Repetir "Não vou" (já em `Não vou`): reabrir a Início e tocar de novo em "Não vou" se o botão estiver ativo; senão `PUT $B` `DECLINE` pela API | resposta `2xx`; nada muda | EVENTOS sem linha nova; mesma `version` de ATL | ☐ | ☐ |
| 8 | banco + ATL | RESET(2, FIFO, false); ATL e ATL3 "Vou"; ATL2 "Vou" (espera). ATL2 "Alterar" → "Não vou" (sai da fila) | `Tudo bem, na próxima. Sua vaga já foi liberada.` | ATL2 `DECLINED`; ATL e ATL3 seguem `CONFIRMED` (quem estava na espera não libera vaga) | ☐ | ☐ |
| 9 | ATL3 | "Alterar" → "Não vou" com a fila vazia | `Sua vaga foi liberada.` | ATL3 `DECLINED`; nenhum evento `SYSTEM` novo | ☐ | ☐ |
| 10 | banco + ATL | RESET(2, FIFO, false); `update games set game_fee_cents=2000 where id='<J>'`; ATL (avulso) "Vou" e depois "Alterar" → "Não vou" | `Sua vaga foi liberada.` | `select status from group_charges where game_id='<J>'` = `PENDING` (a cobrança do desistente continua pendente; o organizador decide) | ☐ | ☐ |
| 11 | ORG | Grupo → "Caixa do grupo" (cobranças do jogo) | Linha de ATL com etiqueta `Desistiu` e ação `Dispensar` ao lado de `Recebi` (decidido em 17/10; se a etiqueta não existir, registrar como divergência) | — | ☐ | ☐ |

Convidados que saem com o anfitrião: ver PRES-008.

## PRES-005 · "Vou" depois do prazo vai sempre para a espera [CRÍTICO] [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(2, FIFO, true); `update games set confirmation_deadline=now()-interval '1 minute' where id='<J>'`; MENSAL(Atl) | — | — | ☐ | ☐ |
| 2 | ATL (mensalista) | Reabrir "Ver o jogo"; ler o aviso; "Vou" | Aviso `O prazo acabou: quem confirmar agora entra na lista de espera e sobe pela ordem se houver vaga.`; mensalista não tem prioridade depois do prazo, mas o jogo tem vaga e a fila está vazia: sobe na hora | ATL `CONFIRMED`; EVENTOS: `SELF|…|WAITLISTED` e depois `SYSTEM|WAITLISTED|CONFIRMED` | ☐ | ☐ |
| 3 | ATL2 | "Vou" (ordem de chegada, vaga livre) | Sobe na hora: `Você está confirmado.` e `2 de 2 confirmados` | ATL2 `CONFIRMED` | ☐ | ☐ |
| 4 | ATL3 | "Vou" (jogo cheio, depois do prazo) | `Lista de espera · 1º` e `As vagas acabaram. Você entrou na lista de espera.` (fim da fila) | ATL3 `WAITLISTED|1` | ☐ | ☐ |
| 5 | banco | RESET(1, FIFO, false); ATL "Vou" (confirma, 1 de 1), ATL2 "Vou" (jogo cheio, antes do prazo: espera 1º). Depois, **de uma vez**: `update games set capacity=2, confirmation_deadline=now()-interval '1 minute' where id='<J>';` | ESTADO: ATL `CONFIRMED`, ATL2 `WAITLISTED|1` (há vaga livre e prazo vencido) | — | ☐ | ☐ |
| 6 | ATL3 (logo em seguida, antes de o job de minuto em minuto rodar) | "Vou" (depois do prazo, vaga livre, fila antiga na frente) | ATL2 sobe antes (fila antiga primeiro); ATL3 `Lista de espera · 1º` e `As vagas acabaram. Você entrou na lista de espera.` Se o job já tiver rodado, o estado final é o mesmo | ESTADO: ATL2 `CONFIRMED`, ATL3 `WAITLISTED|1`; EVENTOS: ATL2 `SYSTEM|WAITLISTED|CONFIRMED` | ☐ | ☐ |
| 7 | banco + ATL | `update access_groups set promotion_mode='MANUAL' where id='<G>'`; apagar `game_attendance` e `attendance_events` do jogo; `update games set capacity=3 where id='<J>'` (prazo segue vencido); ATL "Vou" | Toast `Você entrou na lista de espera. O organizador escolhe quem entra.`; mesmo com vaga, fica na espera | ATL `WAITLISTED` | ☐ | ☐ |
| 8 | ORG | "Ver o jogo" → Lista de espera | `1º na fila` ao lado de Atl, motivo `O organizador escolhe quem entra.` e botão `Colocar no jogo` | — | ☐ | ☐ |

## PRES-006 · "Vou" antes do prazo, em ordem de chegada [CRÍTICO] [MULTI]

Prazo no futuro (RESET deixa `+2 dias`). Cada linha da nota é um RESET novo.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(3, FIFO, true); MENSAL(Atl) | — | — | ☐ | ☐ |
| 2 | ATL (mensalista, prioridade ligada, vaga livre) | "Vou" | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 3 | ATL2 (avulso, prioridade ligada, vaga livre) | "Vou" | Toast `Você entrou na lista de espera.`; cartão com `Mensalista na espera sobe primeiro.` (não aparece `As vagas acabaram`, ainda há vaga) | ATL2 `WAITLISTED|1` | ☐ | ☐ |
| 4 | ORG | "Ver o jogo" → Lista de espera | Linha de Atl2 com `Avulso: mensalista na espera sobe primeiro.` | — | ☐ | ☐ |
| 5 | banco | RESET(1, FIFO, true); MENSAL(Atl); MENSAL(Atl3) | — | — | ☐ | ☐ |
| 6 | ATL3 e depois ATL (dois mensalistas, uma vaga) | ATL3 "Vou"; em seguida ATL "Vou" | ATL3 `Você está confirmado.`; ATL `Lista de espera · 1º` e `As vagas acabaram. Você entrou na lista de espera.` (o primeiro a chegar confirma; o outro espera, sem travar) | ATL3 `CONFIRMED`, ATL `WAITLISTED|1` | ☐ | ☐ |
| 7 | banco | RESET(1, FIFO, true); MENSAL(Atl3); MENSAL(Atl2) (ATL fica avulso) | — | — | ☐ | ☐ |
| 8 | ATL3 (mensalista), ATL (avulso), ATL2 (mensalista), nessa ordem | ATL3 "Vou" (confirma); ATL "Vou" (jogo cheio); ATL2 "Vou" (jogo cheio) | ATL3 `Você está confirmado.`; ATL e ATL2 em `Lista de espera` | ATL `WAITLISTED|1`, ATL2 `WAITLISTED|2` (sequência de chegada) | ☐ | ☐ |
| 9 | ATL e ATL2 | Trocar de aba Grupos → Início nos dois | ATL2 (mensalista) `Lista de espera · 1º`; ATL (avulso) `Lista de espera · 2º` (a posição é a ordem real de promoção, antes do prazo) | — | ☐ | ☐ |
| 10 | ATL3 | "Alterar" → "Não vou" (confirmado libera a vaga antes do prazo) | `Sua vaga foi liberada.` | Sobe ATL2 (mensalista), não ATL (avulso mais antigo): ATL2 `CONFIRMED`, ATL `WAITLISTED` | ☐ | ☐ |
| 11 | banco | RESET(3, FIFO, false); MENSAL(Atl) | — | — | ☐ | ☐ |
| 12 | ATL (mensalista, prioridade desligada) | "Vou" | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 13 | ATL2 (avulso, prioridade desligada, vaga livre) | "Vou" | `Você está confirmado.` (ordem estrita de chegada) | `CONFIRMED` | ☐ | ☐ |
| 14 | ATL3 e ORG | ATL3 "Vou" (3 de 3, última vaga); ORG "Vou" (sem vaga) | ATL3 `Você está confirmado.`; ORG `Lista de espera · 1º` e `As vagas acabaram. Você entrou na lista de espera.` | ORG `WAITLISTED|1` | ☐ | ☐ |
| 15 | ORG | Grupo → "Editar" → rolar até "Como preencher as vagas" | Com a prioridade ligada: `Os mensalistas entram primeiro` e `Mensalistas têm prioridade até o fim do prazo`; desligada, os dois textos não aparecem (trocar o interruptor entre duas leituras e salvar) | — | ☐ | ☐ |

## PRES-007 · "Vou" antes do prazo, em escolha manual [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(3, MANUAL, true); MENSAL(Atl) | — | — | ☐ | ☐ |
| 2 | ATL (mensalista, prioridade ligada, vaga livre) | "Vou" | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 3 | ATL2 (avulso, prioridade ligada) | "Vou" | `Você entrou na lista de espera.` e `O organizador escolhe quem entra.` | `WAITLISTED|1` | ☐ | ☐ |
| 4 | banco | `update games set capacity=1 where id='<J>'` | — | — | ☐ | ☐ |
| 5 | ATL3 (avulso) | "Vou" (sem vaga) | `Lista de espera · 2º` | `WAITLISTED|2` | ☐ | ☐ |
| 6 | banco | RESET(3, MANUAL, false); MENSAL(Atl) | — | — | ☐ | ☐ |
| 7 | ATL (mensalista, prioridade desligada) | "Vou" | `Você entrou na lista de espera.` (sem prioridade nada entra direto) | `WAITLISTED` | ☐ | ☐ |
| 8 | ATL2 (avulso, prioridade desligada) | "Vou" | `Você entrou na lista de espera.` | `WAITLISTED` | ☐ | ☐ |
| 9 | ATL3 e banco | ATL3 "Vou" (espera) e, depois, ATL3 "Alterar" → "Não vou"; esperar 70 s com a vaga aberta | Nada sobe sozinho (nem por desistência, nem pelo job: o prazo ainda não venceu) | ESTADO: ninguém `CONFIRMED` além do que já estava; ATL e ATL2 seguem `WAITLISTED` | ☐ | ☐ |
| 10 | ORG | Jogo → `Colocar no jogo` ao lado de Atl | Atl entra (ver PROM-004) | ATL `CONFIRMED` | ☐ | ☐ |

## PRES-008 · Convidado entra sempre pela espera [MULTI]

Pré: RESET(3, FIFO, false). Valor do jogo para a cobrança do convidado: `update games set game_fee_cents=2000 where id='<J>'`.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL (sem resposta) | Jogo → bloco de convidados | `Responda “Vou” para poder levar alguém.` e nenhum botão `Levar convidado` ativo | — | ☐ | ☐ |
| 2 | ATL | "Vou" (confirmado, com vaga) → `Levar convidado` → nome `Conv A` | Folha `Levar convidado` com `Entra no fim da lista de espera e sobe quando abrir vaga.` e `Se o convidado entrar no jogo, a taxa de R$ 20,00 vem para você.`; botão `Adicionar à lista de espera`; depois `Conv A entrou na lista de espera.` | ESTADO: `Conv A` `guest_seq=1` `WAITLISTED` mesmo com vaga | ☐ | ☐ |
| 3 | ORG | "Ver o jogo" (lista de espera) | Linha de `Conv A` com `Convidado de Atl` | — | ☐ | ☐ |
| 4 | ATL2 | "Vou" e "Alterar" → "Não vou" | Anfitrião que não vai ("Não vou") perde o botão: `Responda “Vou” para poder levar alguém.` | ATL2 `DECLINED` | ☐ | ☐ |
| 5 | banco + ATL2 + ATL3 | RESET(3, FIFO, false); `update games set capacity=1 where id='<J>'`; ATL2 "Vou" (confirma); ATL3 "Vou" (espera 1º); ATL3 `Levar convidado` → `Conv B` → `Adicionar à lista de espera` | Anfitrião na espera pode levar convidado: `Conv B entrou na lista de espera.` (fim da fila) | ESTADO: `Conv B` `guest_seq=1` `WAITLISTED` com sequência maior que a de ATL3 | ☐ | ☐ |
| 6 | banco + ATL + ATL2 | RESET(2, FIFO, false); `update games set game_fee_cents=2000 where id='<J>'`; ATL "Vou" e ATL2 "Vou" (2 de 2); ATL `Levar convidado` → `Conv A` | `Conv A entrou na lista de espera.` (jogo cheio) | ESTADO: ATL e ATL2 `CONFIRMED`, `Conv A` (`guest_seq=1`) `WAITLISTED` | ☐ | ☐ |
| 7 | ATL2 | "Alterar" → "Não vou" (abre vaga; sobe o convidado, ordem de chegada) | `Sua vaga foi liberada.` | `Conv A` `CONFIRMED`; `select kind,amount_cents,status,guest_seq,guest_display_name,member_user_id from group_charges where game_id='<J>'` mostra cobrança `GAME` de 2000, `guest_seq=1`, `guest_display_name='Conv A'`, no `member_user_id` de ATL (o anfitrião) | ☐ | ☐ |
| 8 | ATL | Início → "Ver o jogo" | `Convidado seu · R$ 20,00 na sua conta` na linha do convidado | — | ☐ | ☐ |
| 9 | ATL | `Tirar` no `Conv A` confirmado | Folha `Tirar Conv A do jogo?` com `Sai dos confirmados e a vaga vai para o próximo da lista e a cobrança de R$ 20,00 em seu nome é cancelada.` | `Conv A` removido; cobrança `CANCELLED` | ☐ | ☐ |
| 10 | ATL | Adicionar `Conv C` (espera); depois `Tirar` | `Ele sai da lista de espera. Nada é cobrado.` (texto: `Sai da lista de espera. Nada é cobrado.`) | sem linha em `group_charges` para `Conv C` | ☐ | ☐ |
| 11 | banco + ATL | RESET(4, FIFO, false); ATL "Vou"; ATL adiciona `Conv D` e `Conv E`; ATL "Alterar" → "Não vou" | Os dois convidados saem junto com o anfitrião; ATL em `Não vou` | ESTADO sem linhas de convidado de ATL; EVENTOS: uma linha `SYSTEM|…|DECLINED` por convidado, antes de qualquer promoção | ☐ | ☐ |
| 12 | banco + ATL2 + ORG | RESET(4, FIFO, false); ATL2 "Vou"; ATL2 adiciona `Conv F`; ORG abre o jogo, toca "Tirar" ao lado de `Conv F` | Folha `Tirar Conv F do jogo?`, `Sai da lista de espera. Nada é cobrado.` e `O anfitrião Atl2 vai receber um aviso, ok?`; admin remove convidado de outro anfitrião | `Conv F` removido de ESTADO | ☐ | ☐ |
| 13 | banco + ATL3 | RESET(4, FIFO, false); ATL3 "Vou"; ATL3 adiciona `Conv G`; ATL3 abre o grupo → "Sair do grupo" → "Sair do grupo" (confirmar) | ATL3 sai do grupo | ESTADO sem `Conv G` nem linha de ATL3 (convidados saem junto, como no "Não vou") | ☐ | ☐ |

## PRES-009 · Repetir a mesma resposta não muda nada [CRÍTICO] [MULTI]

Pré: RESET(2, FIFO, false).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | Antes: `update games set game_fee_cents=2000 where id='<J>'`. ATL "Vou" | `Você está confirmado.` | EVENTOS: 1 linha de ATL; `select count(*) from group_charges where game_id='<J>'` = 1; anotar `version`: `select version from game_attendance where game_id='<J>' and member_display_name='QA Atl <data>'` | ☐ | ☐ |
| 2 | API (ATL) | `PUT $B` `CONFIRM` com outro `requestId` (retry do "Vou" por quem já está confirmado) | `2xx`; continua confirmado, não vai para a espera | mesma `version`, EVENTOS sem linha nova | ☐ | ☐ |
| 3 | ATL2 | "Vou" no mesmo jogo, depois ATL3 "Vou" (jogo cheio, 3º fica na espera) | `Lista de espera · 1º` | ATL3 `WAITLISTED|1` | ☐ | ☐ |
| 4 | API (ATL3) | `PUT $B` `CONFIRM` (outro `requestId`) | continua na espera, `1º` | mesma `waitlist_sequence` e mesma `version` | ☐ | ☐ |
| 5 | ATL3 | Reabrir a Início | `Lista de espera · 1º` | — | ☐ | ☐ |
| 6 | API (ATL3) | `DECLINE` e depois `DECLINE` de novo (outros `requestId`) | `2xx` nos dois; um só evento de saída | EVENTOS: uma linha `SELF|WAITLISTED|DECLINED`; nada depois | ☐ | ☐ |
| 7 | ATL | Toque duplo rápido em "Não vou" (dois toques em menos de 300 ms; no Android `adb shell 'input tap X Y & input tap X Y'`; no iOS `mobile_double_tap_on_screen`) | O app ignora o segundo toque enquanto o primeiro está em andamento; resultado `Tudo bem, na próxima. Sua vaga já foi liberada.` | uma linha de ATL; EVENTOS: um só `CONFIRMED→DECLINED`; sem sequências duplicadas | ☐ | ☐ |
| 8 | ATL | Toque duplo rápido em "Vou" | `Você está confirmado.` uma vez | uma linha de ATL; um só evento `…→CONFIRMED` | ☐ | ☐ |
| 9 | API (ATL) | Repetir `CONFIRM` de ATL (já confirmado) mais uma vez | `2xx`, continua confirmado | `select count(*) from group_charges where game_id='<J>' and member_user_id=(select id from access_users where display_name='QA Atl <data>')` = 1 (sem segunda cobrança) | ☐ | ☐ |
| 10 | banco + ATL2 + ATL3 | RESET(2, FIFO, false); ATL2 e ATL3 "Vou" (2 de 2); `update games set capacity=1 where id='<J>'` (redução abaixo dos confirmados); ATL "Vou" | Ninguém é rebaixado: ATL2 e ATL3 seguem `Você está confirmado.`; ATL `Lista de espera · 1º` | ESTADO: ATL2 e ATL3 `CONFIRMED`, ATL `WAITLISTED|1`; sem evento novo para os dois confirmados | ☐ | ☐ |

## PRES-010 · O mesmo requestId só é repetição se for a última resposta [MULTI]

O link do WhatsApp usa o mesmo `requestId` por jogo e pessoa; o stack local não tem WhatsApp, então a sequência é feita pela API com um `requestId` fixo `<R>` (um uuid por linha) e conferida na tela do ATL. Pré: RESET(3, FIFO, false) antes de cada linha da tabela.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | API (ATL) | `CONFIRM` com `<R1>`, `DECLINE` com `<R2>`, `CONFIRM` com `<R1>` outra vez | O terceiro vale como resposta nova (não é repetição do primeiro): ATL fica confirmado | EVENTOS: 3 linhas de ATL (`CONFIRMED`, `DECLINED`, `CONFIRMED`) | ☐ | ☐ |
| 2 | ATL | Início | `Você está confirmado.` | — | ☐ | ☐ |
| 3 | banco + API (ATL2) | RESET(3, FIFO, false); `DECLINE` `<R3>`, `CONFIRM` `<R4>`, `DECLINE` `<R3>` | Recusado no fim | EVENTOS: 3 linhas de ATL2; ESTADO `DECLINED` | ☐ | ☐ |
| 4 | API (ATL3) | RESET; `CONFIRM` `<R5>` duas vezes seguidas | confirmado; o segundo é repetição | EVENTOS: **1** linha de ATL3 | ☐ | ☐ |
| 5 | API (ATL3) | `DECLINE` `<R6>` e retry de `<R6>`; RESET antes | recusado | EVENTOS: 2 linhas (confirmou e recusou); o retry não cria a terceira | ☐ | ☐ |
| 6 | API (ATL3) | `CONFIRM` `<R7>`, `DECLINE` `<R8>`, retry de `<R7>` | Vale como resposta nova: confirmado | EVENTOS: 3 linhas | ☐ | ☐ |
| 7 | API + ATL | RESET(1, FIFO, false); ATL2 `CONFIRM` (ocupa a única vaga); ATL `CONFIRM` `<R9>`, `DECLINE` `<R10>`, `CONFIRM` `<R9>` (terceiro toque com jogo cheio) | Vai para a espera: ATL mostra `Lista de espera · 1º`, não `Presença confirmada` | ATL `WAITLISTED` | ☐ | ☐ |
| 8 | ATL | Reabrir "Ver o jogo" | `Você está em 1º na lista de espera.` (a tela só diz confirmada quando está confirmada) | — | ☐ | ☐ |

## PRES-011 · Quem desiste e volta vai para o fim da fila [MULTI]

Pré: RESET(1, FIFO, false).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | "Vou" (confirma, 1 de 1) | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 2 | ATL2 | "Vou" (espera) | `Lista de espera · 1º` | `WAITLISTED|1` | ☐ | ☐ |
| 3 | ATL3 | "Vou" (espera) | `Lista de espera · 2º` | `WAITLISTED|2` | ☐ | ☐ |
| 4 | ATL2 | "Alterar" → "Não vou" (na espera, 1º, desiste) | `Tudo bem, na próxima.`; sai da fila | ATL2 `DECLINED` | ☐ | ☐ |
| 5 | ATL3 | Trocar de aba | `Lista de espera · 1º` | — | ☐ | ☐ |
| 6 | ATL2 | "Vou" de novo (volta) | `Lista de espera · 2º` (fim da fila, não recupera o 1º) | ATL2 `WAITLISTED` com sequência maior que a de ATL3 e maior que a anterior de ATL2 | ☐ | ☐ |
| 7 | ATL2 | Repete "Vou" (já na espera em 2º) | continua `2º` | mesma sequência e mesma `version` | ☐ | ☐ |
| 8 | ATL | "Alterar" → "Não vou" (confirmado desiste; sobe ATL3 por ordem de chegada) | `Sua vaga foi liberada.` | ATL3 `CONFIRMED`; ATL2 passa a `1º` | ☐ | ☐ |
| 9 | ATL | "Vou" de novo (jogo cheio) | `Lista de espera · 2º` (fim da fila, número novo) | ATL `WAITLISTED` com sequência maior que a de ATL2 | ☐ | ☐ |
| 10 | banco + ATL3 | RESET(3, FIFO, false); ATL3 "Vou", "Não vou" e "Vou" (com vaga livre) | Segue a regra do tipo e do prazo: confirma direto (prioridade desligada) | `CONFIRMED`; EVENTOS: 3 linhas | ☐ | ☐ |

## PRES-012 · O organizador responde por alguém [MULTI]

A nota diz "app sem tela" (DEB-017), mas o app **já tem** as folhas `Tirar <Nome> do jogo?` e `Colocar alguém no jogo` (divergência a registrar). O motivo é fixo `Escolha do organizador` (campo `Outro motivo` é opcional); os limites do motivo (2 a 500 caracteres) conferem-se pela API.

Pré: RESET(2, FIFO, false).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | API (ORG) | `POST $B/override` para ATL3 `CONFIRM` com `reason` ausente; depois `""`; depois `" "`; depois `"a"`; depois 501 caracteres; depois `"duas\nlinhas"` | seis respostas `400` com `reason` no corpo | EVENTOS sem linha de ATL3 | ☐ | ☐ |
| 2 | API (ORG) | `POST $B/override` para ATL3 `CONFIRM` com `reason` `"Avisou por fora"` (vaga livre, antes do prazo) | `2xx` | EVENTOS: `ORGANIZER|NULL|CONFIRMED` de ATL3 com `reason`=`Avisou por fora`; ator ORG | ☐ | ☐ |
| 3 | ATL3 | Início | `Você está confirmado.` | — | ☐ | ☐ |
| 4 | banco + API (ORG) | `update games set game_fee_cents=2000, confirmation_deadline=now()-interval '1 minute' where id='<J>'`; `override` `CONFIRM` para ATL com `reason` `"Apareceu na hora"` | `2xx` mesmo com o prazo vencido (o organizador passa por cima do prazo); 2 de 2 | ATL `CONFIRMED`; `select count(*) from group_charges where game_id='<J>' and member_user_id=(select id from access_users where display_name='QA Atl <data>')` = 1 (cobrança do avulso, jogo com valor) | ☐ | ☐ |
| 5 | ORG | Abrir o jogo (cheio, 2 de 2) → "Colocar alguém no jogo" | Folha com `Jogo cheio: entra na lista de espera.` | — | ☐ | ☐ |
| 6 | ORG | Escolher `Atl2` → botão `Colocar Atl2 no jogo` | Atl2 entra na `Lista de espera` (o organizador não passa por cima das vagas) | ATL2 `WAITLISTED`; EVENTOS: `ORGANIZER` com `reason`=`Escolha do organizador` | ☐ | ☐ |
| 7 | ORG | "Tirar" ao lado de `Atl` (confirmado) | Folha `Tirar Atl do jogo?` com `Sai dos confirmados, a vaga vai para o próximo da lista e a cobrança de R$ 20,00 é cancelada.` e `Atl vai receber um aviso, ok?`; tocar `Tirar` | ATL `DECLINED`; ATL2 `CONFIRMED` (a vaga liberada anda a fila, FIFO); `select status from group_charges …` de ATL: registrar o valor (a folha promete `CANCELLED`) | ☐ | ☐ |
| 8 | ATL | Reabrir "Ver o jogo" | `Você não vai jogar.` | — | ☐ | ☐ |
| 9 | banco + API (ORG) | `update games set starts_at=now()-interval '1 minute' where id='<J>'`; `override` `DECLINE` para ATL3 (confirmado) com `reason` `"Não apareceu"` | `2xx`: o organizador age depois do início | ATL3 `DECLINED`; EVENTOS: `ORGANIZER|CONFIRMED|DECLINED` | ☐ | ☐ |
| 9b | banco + API (ORG) | `update games set status='CANCELLED' where id='<J>'`; `override` `CONFIRM` para ATL3 | `409` (`ATTENDANCE_FROZEN`, mesmo erro do rascunho e do concluído) | ESTADO inalterado | ☐ | ☐ |
| 10 | API (ATL2) | `PUT $B` do próprio atleta (republicar antes: `update games set status='PUBLISHED', starts_at=now()+interval '1 day' where id='<J>'`) com `"reason":"xxxxx"` extra | `2xx`; o motivo é ignorado | `select count(*) from attendance_events where reason is not null and source='SELF'` = 0 | ☐ | ☐ |

## PRES-013 · Aviso direto quando a pessoa é confirmada ou tirada por outra parte [MULTI] [push: só produção]

Nota "em implementação": no momento da escrita (branch `bruno-halmeida/roteiros-multi-a`, `0b2178061`) **não há** código de aviso direto para esses eventos. Se um item não aparecer na central, o resultado é FALHOU com o motivo "DEB-013 não implementado", e não BLOQUEADO. A central é "Notificações" (ícone de sino) em ORG, ATL, ATL2 e ATL3. O push em si, nenhuma conferência local.
Pré: RESET(1, FIFO, false) e `update games set game_fee_cents=2000 where id='<J>'`. Contagem de itens de um atleta na central:
`select count(*) from group_notifications n join group_messages m on m.id=n.message_id where m.game_id='<J>' and n.recipient_id=(select id from access_users where display_name='QA <Nome> <data>');` (chamada **CONTA(nome)**).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | "Vou" (confirma sozinho, 1 de 1) | **Não avisa:** nada novo na central de ATL | CONTA(Atl) = 0 | ☐ | ☐ |
| 2 | ATL2 e ATL3 | ATL2 "Vou" (espera 1º); ATL3 "Vou" (espera 2º) | Nenhum item novo | CONTA(Atl2) = 0, CONTA(Atl3) = 0 | ☐ | ☐ |
| 3 | ATL | "Alterar" → "Não vou" (desistiu sozinho; sobe ATL2 por ordem de chegada) | ATL: nada. ATL2: item novo na central ("Notificações"). ATL3 segue na espera, sem item | CONTA(Atl)=0, CONTA(Atl2)=1, CONTA(Atl3)=0 | ☐ | ☐ |
| 4 | ATL2 | Abrir "Notificações" | Item sobre o jogo, texto curto e sem supor gênero, dizendo que a presença está confirmada | `select m.body from group_messages m join group_notifications n on n.message_id=m.id where m.game_id='<J>' and n.recipient_id=(select id from access_users where display_name='QA Atl2 <data>')` | ☐ | ☐ |
| 5 | ATL2 | Repetir "Vou" pela API (já confirmado, `requestId` novo) | Sem segundo item | CONTA(Atl2) continua 1 | ☐ | ☐ |
| 6 | ORG | "Tirar" em `Atl2` (confirmado) → "Tirar" | ATL2: item "tirado do jogo" **sem o motivo digitado**; ATL3 sobe da espera (FIFO) e recebe item de subida | CONTA(Atl2)=2, CONTA(Atl3)=1; o corpo do item de ATL2 não contém `Escolha do organizador` | ☐ | ☐ |
| 7 | banco + ORG | `update games set capacity=2 where id='<J>'`; ORG "Colocar alguém no jogo" → `Atl` → `Colocar Atl no jogo` | ATL: item de "organizador confirmou". ATL3 (já confirmado): sem item novo | CONTA(Atl)=1, CONTA(Atl3)=1 | ☐ | ☐ |
| 8 | ATL | "Levar convidado" → `Conv A` (jogo cheio, espera); ATL3 "Alterar" → "Não vou" (desistiu sozinho; `Conv A` sobe) | ATL (anfitrião): item **separado** citando `Conv A`. ATL3: nada novo | CONTA(Atl)=2, CONTA(Atl3)=1 | ☐ | ☐ |
| 9 | ORG | "Tirar" em `Conv A` (confirmado) | ATL recebe item (convidado tirado) | CONTA(Atl)=3 | ☐ | ☐ |
| 10 | ATL | Levar `Conv B`; ATL "Alterar" → "Não vou" (convidados caem junto) | **Não avisa:** nenhum item novo para ATL | CONTA(Atl)=3 | ☐ | ☐ |
| 11 | ATL3 | Sair do grupo (Grupos → "Sair do grupo" → "Sair do grupo") | **Não avisa** | CONTA(Atl3) inalterada | ☐ | ☐ |
| 12 | ORG (dono) | Dono na espera do jogo sobe pelo job do prazo; gestor sobe ao aumentar as vagas | **Não** recebem o aviso (exceções decididas em 17/10) | CONTA(Org) inalterada | ☐ | ☐ |
| 13 | ATL2 | Perfil → notificações: desligar os avisos de jogo; repetir um evento que o avisaria | Sem item (respeita as preferências) | CONTA(Atl2) inalterada | ☐ | ☐ |
| 14 | todos | Compatibilidade: abrir a central nos apps 1.0.4 a 1.0.6 depois de um aviso novo | Só em produção (build antigo); aqui registrar que a central do app da rodada abre sem erro | — | ☐ | ☐ |
| 15 | push | Push dos itens 3, 6, 7, 8 e 9 | `[push: só produção]` | — | ☐ | ☐ |

---

## Rodadas

**Rodada 1: ORG no iPhone, ATL no Android.** ORG = iPhone 17, ATL = `Saqz_QA_A`, ATL2 = iPhone 17e, ATL3 = `Saqz_QA_B`.
Rodar Preparação e as regras 002 a 013 na ordem. Nas colunas iOS e Android, marcar a plataforma do aparelho que fez o passo (um passo de ORG vale iOS; de ATL, Android; ATL2, iOS; ATL3, Android).

**Rodada 2: ORG no Android, ATL no iPhone.** Papéis invertidos: ORG = `Saqz_QA_A`, ATL = iPhone 17, ATL2 = `Saqz_QA_B`, ATL3 = iPhone 17e.
`stack.sh reset`, repetir Preparação com os aparelhos trocados e todas as regras de novo; marcar iOS/Android de acordo com o aparelho em cada papel (ORG agora vale Android; ATL, iOS; ATL2, Android; ATL3, iOS).

## Resumo

Críticos: PRES-002, PRES-005, PRES-006, PRES-009 (4 de 12).

## O que ficou sem passo

- **Borda exata do prazo e do início (PRES-002):** o instante exato não é alcançável com o relógio real; fica no `AttendanceTransitionPolicyTest` (athlete may respond exactly at deadline / cannot respond once the game started). Aqui testamos um pouco antes e um pouco depois.
- **Atleta em outro membro "pela API" (PRES-003):** a rota do atleta não aceita `memberId`; a linha da nota é leitura de código e fica coberta pelo passo 5 (corpo extra ignorado) e pelo `AttendanceControllerTest`.
- **WhatsApp e link do atleta (PRES-010):** sem WhatsApp no stack, a sequência com `requestId` fixo é pela API; o texto `Presença confirmada` do link só em produção.
- **Push (PRES-013):** só produção; e o aviso direto ainda não existe no código desta branch (DEB-013).
- **Toque duplo no iOS/Android (PRES-009):** depende de o mobile-mcp acertar a janela de milissegundos; se não repetir, registrar BLOQUEADO e confiar no `AttendanceConcurrencySensorIntegrationTest`.
