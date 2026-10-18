# Roteiro 04 · Promoção da lista de espera: vagas, ordem, job do prazo, cobrança e avisos

Cobre PROM-001, 002, 003, 004, 005, 007, 008 e 009 (PROM-006, "aumentar e reduzir vagas", é de uma pessoa só e não entra;
ele aparece aqui só como causa de vaga aberta). Fonte: `Obsidian Vault/Saqz/Regras/Presença e lista de espera/`; se o app e a nota divergirem, vale a nota.
Tabela de um aparelho com as mesmas regras: `tests/roteiros/presenca-e-lista-de-espera.md`.

Etiquetas: **[CRÍTICO]**, **[MULTI]** e **[push: só produção]** como no [roteiro 03](03-presenca.md).

## Papéis, contas, preparação e atalhos

Tudo igual ao [roteiro 03](03-presenca.md): mesmas contas e papéis (ORG, ATL, ATL2, ATL3), seção **Preparação** (passos 1 a 6),
atalhos **RESET(vagas, modo, prioridade)**, **MENSAL(nome)**, **ESTADO**, **EVENTOS**, funções `tok` e `$B` de API.
`<G>` e `<J>` são o grupo e o jogo da preparação. Atalhos extras deste roteiro:

- **CONTA(nome)**: itens na central de um atleta para o jogo:
  `select count(*) from group_notifications n join group_messages m on m.id=n.message_id where m.game_id='<J>' and n.recipient_id=(select id from access_users where display_name='QA <nome> <data>');`
- **COBRANÇAS**: `select u.display_name,c.guest_seq,c.guest_display_name,c.amount_cents,c.status,c.due_date from group_charges c join access_users u on u.id=c.member_user_id where c.game_id='<J>' order by c.created_at;`
- **PROMOVER(alvo)** pela API (modo `MANUAL`): `curl -s -w '\n%{http_code}\n' -X POST $B/promote -H "authorization: Bearer $(tok qa-org+<tag>@saqz.local)" -H 'content-type: application/json' -d '{"requestId":"<uuid>","memberId":"<U-alvo>","reason":"Escolha do organizador"}'`
- **Fila no app**: a Início de quem espera mostra `Lista de espera · Nº`; "Ver o jogo" mostra `Você está em Nº na lista de espera.`;
  para o organizador, o detalhe do jogo tem a seção `Lista de espera` com `Nº na fila` por pessoa e, no modo manual, `Colocar no jogo`.
  Trocar de aba Grupos → Início (ou reabrir "Ver o jogo") para ver o efeito de outro aparelho.
- **Capacidade pelo app**: "Ajustar vagas" aceita de 4 a 100; jogo antigo com 2 ou 3 vagas mostra
  `Este jogo tem N vagas. O mínimo agora é 4, e salvar grava 4`. Por isso os cenários de aumento partem de 2 vagas (RESET) e vão a 4.
- **Job do prazo**: roda de minuto em minuto; depois de vencer o prazo no banco, esperar até 70 s e então conferir.

---

## PROM-001 · O que libera uma vaga [CRÍTICO] [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(2, FIFO, false) | — | ESTADO vazio | ☐ | ☐ |
| 2 | ATL e ATL3 | ATL "Vou"; ATL3 "Vou" (2 de 2) | Os dois `Você está confirmado.` | `CONFIRMED` nos dois | ☐ | ☐ |
| 3 | ATL2 | "Vou" (jogo cheio) | `Lista de espera · 1º` | `WAITLISTED|1` | ☐ | ☐ |
| 4 | ATL | "Alterar" → "Não vou" (confirmado desiste: libera 1) | `Sua vaga foi liberada.` | ATL `DECLINED`; ATL2 `CONFIRMED`; EVENTOS: último `SYSTEM|WAITLISTED|CONFIRMED` | ☐ | ☐ |
| 5 | ATL2 | Trocar de aba | `Você está confirmado.` | — | ☐ | ☐ |
| 6 | ATL | Quem estava na espera desiste: banco RESET(2, FIFO, false); ATL e ATL3 "Vou" (2 de 2); ATL2 "Vou" (espera 1º); ATL2 "Alterar" → "Não vou" | `Tudo bem, na próxima.` | ATL2 `DECLINED`; ATL e ATL3 seguem `CONFIRMED`; EVENTOS sem `SYSTEM` novo (não libera vaga) | ☐ | ☐ |
| 7 | banco | **Anfitrião com 2 convidados confirmados.** RESET(2, FIFO, false); ATL "Vou"; ATL2 "Vou" (2 de 2); ATL "Levar convidado" → `Conv A`, depois `Conv B` (os dois na espera) | `Conv A entrou na lista de espera.` e `Conv B entrou na lista de espera.` | ESTADO: `Conv A` `WAITLISTED|1`, `Conv B` `WAITLISTED|2` | ☐ | ☐ |
| 8 | ATL2 | "Alterar" → "Não vou" (sobe `Conv A`) | `Sua vaga foi liberada.` | `Conv A` `CONFIRMED` | ☐ | ☐ |
| 9 | ORG | Jogo → "Ajustar vagas" → `4` → "Salvar vagas" (libera 2: sobe `Conv B`; sobra 1 aberta) | Volta ao jogo; `3 de 4 confirmados` (ATL, `Conv A`, `Conv B`) | `Conv B` `CONFIRMED` | ☐ | ☐ |
| 10 | banco + ATL3 + ORG | `update games set capacity=3 where id='<J>'` (3 de 3, jogo cheio); ATL3 "Vou" (espera 1º); ORG "Vou" (espera 2º); ATL3 "Levar convidado" → `Conv C` | `Conv C entrou na lista de espera.` | ESTADO: ATL, `Conv A`, `Conv B` `CONFIRMED`; ATL3 `WAITLISTED`, ORG `WAITLISTED`, `Conv C` `WAITLISTED` (nessa ordem de sequência) | ☐ | ☐ |
| 11 | ATL | "Alterar" → "Não vou" (anfitrião sai: libera a vaga dele e a de cada convidado confirmado = 3) | `Sua vaga foi liberada.`; `Conv A` e `Conv B` saem junto | ESTADO: ATL `DECLINED`; `Conv A` e `Conv B` sem linha confirmada; ATL3, ORG e `Conv C` `CONFIRMED` (3 vagas, 3 na fila); EVENTOS: os dois convidados saem por `SYSTEM` antes das três subidas `SYSTEM|WAITLISTED|CONFIRMED` | ☐ | ☐ |
| 12 | banco | **Anfitrião sai com convidado na espera.** RESET(2, FIFO, false); ATL e ATL3 "Vou" (2 de 2); ATL2 "Vou" (espera 1º); ATL "Levar convidado" → `Conv D` (espera 2º) | — | `Conv D` `WAITLISTED|2` | ☐ | ☐ |
| 13 | ATL | "Alterar" → "Não vou" | Libera 1 (a dele); `Conv D` só sai da fila | `Conv D` sem linha ou `DECLINED`; ATL2 `CONFIRMED` (sobe 1, não 2) | ☐ | ☐ |
| 14 | banco | **Membro sai ou é removido.** RESET(2, FIFO, false); ATL e ATL3 "Vou" (2 de 2); ATL2 "Vou" (espera 1º) | — | — | ☐ | ☐ |
| 15 | ORG | Grupo → membros → `Atl3` → "Remover do grupo" → "Remover" | Folha `Remover Atl3 do grupo?` com `Perde o acesso e sai das próximas listas de presença.`; ATL3 some dos membros | ATL3 sem linha confirmada em jogos futuros; ATL2 `CONFIRMED` (vaga liberada, a fila andou) | ☐ | ☐ |
| 16 | ATL3 | Abrir o app | Perdeu o acesso ao grupo (sem o grupo na lista) | — | ☐ | ☐ |
| 17 | banco | **Organizador tira um confirmado.** RESET(2, FIFO, false); ATL e ATL3 "Vou" (2 de 2); ATL2 "Vou" (espera 1º) | — | — | ☐ | ☐ |
| 18 | ORG | Jogo → ao lado de `Atl3` (confirmado) "Tirar" → "Tirar" | `Sai dos confirmados, a vaga vai para o próximo da lista.` (e `Atl3 vai receber um aviso, ok?`) | ATL3 `DECLINED` por `ORGANIZER`; ATL2 `CONFIRMED` por `SYSTEM` | ☐ | ☐ |
| 19 | banco | **Aumento de vagas.** RESET(2, FIFO, false); ORG e ATL "Vou" (2 de 2); ATL2 e ATL3 "Vou" (espera 1º e 2º) | — | — | ☐ | ☐ |
| 20 | ORG | Jogo → "Ajustar vagas" → `4` → "Salvar vagas" (libera 2) | Dois sobem de uma vez | ATL2 e ATL3 `CONFIRMED`; `select capacity from games where id='<J>'` = 4 | ☐ | ☐ |
| 21 | ATL2 e ATL3 | Trocar de aba | `Você está confirmado.` nos dois | — | ☐ | ☐ |

No modo escolha manual, nenhum desses eventos move a fila (ver PROM-004); o organizador não recebe aviso quando a vaga abre (decidido em 17/10).

## PROM-002 · Quem sobe primeiro [CRÍTICO] [MULTI]

A ocupante da única vaga é ORG, que fica mensalista para confirmar direto. Fila de chegada: **A** = ATL (avulso), **M** = ATL2 (mensalista), **B** = ATL3 (avulso).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(1, FIFO, true); MENSAL(Org); MENSAL(Atl2) | — | — | ☐ | ☐ |
| 2 | ORG | "Vou" (mensalista, confirma a única vaga) | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 3 | ATL, ATL2, ATL3 nessa ordem | "Vou" em cada um (jogo cheio) | ATL `Lista de espera · 2º`, ATL2 `1º`, ATL3 `3º` (ver PROM-007) | `WAITLISTED` com sequências 1, 2, 3 para A, M, B | ☐ | ☐ |
| 4 | ORG | "Alterar" → "Não vou" (antes do prazo, prioridade ligada) | `Sua vaga foi liberada.` | Sobe **M** (ATL2); A e B seguem `WAITLISTED` | ☐ | ☐ |
| 5 | banco | RESET(1, FIFO, true); MENSAL(Org); MENSAL(Atl2); ORG "Vou"; ATL, ATL2, ATL3 "Vou" (fila A, M, B); depois `update games set confirmation_deadline=now()-interval '1 minute' where id='<J>'` (jogo cheio: o job não sobe ninguém) | — | ESTADO: A, M, B na espera | ☐ | ☐ |
| 6 | ORG | "Alterar" → "Não vou" (depois do prazo: ordem só por chegada) | `Sua vaga foi liberada.` | Sobe **A** (ATL); M e B seguem na espera | ☐ | ☐ |
| 7 | banco | RESET(1, FIFO, false); MENSAL(Atl2); ORG "Vou" (confirma, prioridade desligada); ATL, ATL2, ATL3 "Vou" (fila A, M, B) | — | — | ☐ | ☐ |
| 8 | ORG | "Alterar" → "Não vou" (prioridade desligada) | `Sua vaga foi liberada.` | Sobe **A**, o mais antigo, sem olhar o tipo | ☐ | ☐ |
| 9 | banco | **Convidado do mensalista.** RESET(2, FIFO, true); MENSAL(Org); MENSAL(Atl2); ORG "Vou"; ATL2 "Vou" (2 de 2, os dois mensalistas confirmam); ATL "Vou" (avulso, espera 1º); ATL2 "Levar convidado" → `Conv M` (espera 2º) | `Conv M entrou na lista de espera.` | ESTADO: ATL `WAITLISTED|1`, `Conv M` `WAITLISTED|2` | ☐ | ☐ |
| 10 | ORG | "Alterar" → "Não vou" (antes do prazo) | `Sua vaga foi liberada.` | Sobe **A** (ATL); o convidado do mensalista vale como avulso, na ordem de chegada dele | ☐ | ☐ |
| 11 | banco | **Duas vagas.** RESET(2, FIFO, false); ORG e ATL2 "Vou" (2 de 2); ATL "Vou" (espera 1º); ATL3 "Vou" (espera 2º) | — | — | ☐ | ☐ |
| 12 | ORG | Jogo → "Ajustar vagas" → `4` → "Salvar vagas" (libera 2) | Sobem os dois | ATL (A) `CONFIRMED`, depois ATL3 (B) `CONFIRMED`: `select u.display_name,e.occurred_at from attendance_events e join access_users u on u.id=e.member_user_id where e.game_id='<J>' and e.source='SYSTEM' order by e.occurred_at,e.id` mostra A antes de B | ☐ | ☐ |

## PROM-003 · Ordem de chegada: promoção automática [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(1, FIFO, false); ORG "Vou" (confirma); ATL, ATL2 "Vou" (fila de 2) | ATL `1º`, ATL2 `2º` | `WAITLISTED|1` e `WAITLISTED|2` | ☐ | ☐ |
| 2 | ORG | "Alterar" → "Não vou" | O 1º (ATL) sobe, o 2º continua | ATL `CONFIRMED`, ATL2 `WAITLISTED` | ☐ | ☐ |
| 3 | ATL, ATL2 | Trocar de aba | ATL `Você está confirmado.`; ATL2 `Lista de espera · 1º` (todo mundo sobe uma posição) | — | ☐ | ☐ |
| 4 | banco | **Origem do evento.** `select source,actor_user_id=(select id from access_users where display_name='QA Org <data>') as autor_e_org from attendance_events where game_id='<J>' and new_status='CONFIRMED' and old_status='WAITLISTED'` | — | `SYSTEM|t` (origem sistema, autor = quem abriu a vaga) | ☐ | ☐ |
| 5 | banco | **Aumento com fila.** RESET(2, FIFO, false); ORG e ATL "Vou" (2 de 2); ATL2 "Vou" (espera 1º); ATL "Levar convidado" → `Conv A` (2º) e `Conv B` (3º) | — | 3 na espera | ☐ | ☐ |
| 6 | ORG | "Ajustar vagas" → `4` → "Salvar vagas" (libera 2) | Sobem 2; um continua | ATL2 e `Conv A` `CONFIRMED`, `Conv B` `WAITLISTED|1` | ☐ | ☐ |
| 7 | banco | **Anfitrião desiste com 2 convidados confirmados.** O mesmo cenário dos passos 7 a 11 do PROM-001: 3 vagas abertas, sobem até 3 | Ver PROM-001 | Ver PROM-001 | ☐ | ☐ |
| 8 | banco | **Fila vazia.** RESET(2, FIFO, false); ORG e ATL "Vou"; ATL "Alterar" → "Não vou" | `Sua vaga foi liberada.` | Ninguém sobe; `confirmed` = 1 em ESTADO; nenhum evento `SYSTEM` | ☐ | ☐ |
| 9 | ORG | Jogo | `1 de 2 confirmado` e vaga aberta | — | ☐ | ☐ |
| 10 | banco | **Depois do início, ninguém sobe.** RESET(2, FIFO, false); ORG e ATL "Vou"; ATL2 "Vou" (espera 1º); `update games set starts_at=now()-interval '1 minute' where id='<J>'` | Em ATL2 "Ver o jogo": `Em andamento. Não dá mais para responder.` | — | ☐ | ☐ |
| 11 | ORG | Abrir o jogo (já começou); ao lado de `Atl` "Tirar" → "Tirar" (organizador age depois do início) | Lista de espera mostra `O jogo já começou. A fila não anda mais.` | ATL `DECLINED`; **ATL2 segue `WAITLISTED`**. Se ATL2 subir, FALHOU e anotar: DEB-018 ainda aberto | ☐ | ☐ |
| 12 | banco | `update games set capacity=4 where id='<J>'` (aumento depois do início) e esperar 70 s | — | ATL2 segue `WAITLISTED` (o job também pula jogo começado) | ☐ | ☐ |

## PROM-004 · Escolha manual: "Colocar no jogo" [CRÍTICO] [MULTI] [push: só produção]

Pré: RESET(3, MANUAL, true) em cada bloco, salvo indicação.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(3, MANUAL, true) | — | — | ☐ | ☐ |
| 2 | ATL2 | "Vou" (avulso, modo manual) | `Você entrou na lista de espera. O organizador escolhe quem entra.` | `WAITLISTED|1` | ☐ | ☐ |
| 3 | ORG | Abrir o jogo; seção `Lista de espera` | `1º na fila` com `Atl2` e botão `Colocar no jogo` | — | ☐ | ☐ |
| 4 | ATL2 | Abrir "Ver o jogo" | `Você está em 1º na lista de espera.` | — | ☐ | ☐ |
| 5 | ORG | `Colocar no jogo` ao lado de `Atl2` | Atl2 passa para Confirmados | ATL2 `CONFIRMED`; EVENTOS: `SYSTEM` ou `ORGANIZER` com `reason`=`Escolha do organizador` | ☐ | ☐ |
| 6 | ATL2 | Trocar de aba (sem reabrir o app) | `Você está confirmado.` | — | ☐ | ☐ |
| 7 | ATL2 | Central "Notificações" | Item de "você foi colocado no jogo" (PRES-013; se não existir, FALHOU: DEB-013 ainda não implementado) | CONTA(Atl2) = 1 | ☐ | ☐ |
| 8 | push | Push da colocação | `[push: só produção]` | — | ☐ | ☐ |
| 9 | banco | **Jogo cheio.** RESET(1, MANUAL, true); MENSAL(Atl); ATL "Vou" (mensalista, confirma); ATL2 "Vou" (espera 1º) | — | ATL `CONFIRMED`, ATL2 `WAITLISTED|1` | ☐ | ☐ |
| 10 | ORG | `Colocar no jogo` ao lado de `Atl2` | `Não foi possível colocar essa pessoa no jogo. Tente novamente.` (ou o botão não aparece) | ATL2 segue `WAITLISTED`; pela API `PROMOVER(Atl2)` devolve `400` (campo `promotion`) | ☐ | ☐ |
| 11 | ORG | Abrir "Ajustar vagas" → `4` → "Salvar vagas" (no modo manual nada sobe sozinho) | Volta ao jogo; `1 de 4 confirmado` | ATL2 segue `WAITLISTED` | ☐ | ☐ |
| 12 | ORG | `Colocar no jogo` ao lado de `Atl2` (agora com vaga) | Atl2 passa a confirmado | ATL2 `CONFIRMED` | ☐ | ☐ |
| 13 | API (ORG) | **Pessoa fora da espera.** `PROMOVER(Atl3)` (Atl3 sem resposta) | `400` (campo `promotion`) | ESTADO sem linha de ATL3 | ☐ | ☐ |
| 14 | API (ORG) | `PROMOVER(Atl2)` (já confirmado) | `400` (campo `promotion`) | sem evento novo | ☐ | ☐ |
| 15 | banco + ORG | **Modo ordem de chegada.** `update access_groups set promotion_mode='FIFO' where id='<G>'`; ORG reabre o jogo | A seção de espera não mostra `Colocar no jogo` | — | ☐ | ☐ |
| 16 | API (ORG) | `PROMOVER(Atl3)` em modo FIFO | `400` (campo `promotionMode`) | — | ☐ | ☐ |
| 17 | banco | RESET(3, MANUAL, true); ATL2 "Vou" (espera 1º) | — | — | ☐ | ☐ |
| 18 | ATL | **Atleta comum.** Abrir o jogo | Sem `Colocar no jogo`, sem `Tirar`, sem `Ajustar vagas` | — | ☐ | ☐ |
| 19 | API (ATL) | `POST $B/promote` com o token de ATL | `403` | ATL2 segue `WAITLISTED` | ☐ | ☐ |
| 20 | API (ORG) | **Mesmo `requestId`.** `PROMOVER(Atl2)` duas vezes com o mesmo `<uuid>` | Os dois devolvem o mesmo resultado, sem segundo efeito | ATL2 `CONFIRMED`; EVENTOS: **uma** linha de promoção para ATL2 | ☐ | ☐ |
| 21 | banco + ORG | **Toque duplo.** RESET(3, MANUAL, true); ATL2 "Vou"; ORG toca duas vezes seguidas em `Colocar no jogo` | O botão fica em carregamento e ignora o segundo toque | ATL2 `CONFIRMED`; uma só linha de evento | ☐ | ☐ |
| 22 | banco + API (ORG) | **Rascunho, cancelado e concluído.** RESET(3, MANUAL, true); ATL2 "Vou"; `update games set status='DRAFT' where id='<J>'`; `PROMOVER(Atl2)`; repetir com `'CANCELLED'` e `'COMPLETED'` | `409` (`ATTENDANCE_FROZEN`) nos três | ATL2 segue `WAITLISTED` | ☐ | ☐ |
| 23 | banco + ORG | **Depois do início.** `update games set status='PUBLISHED', starts_at=now()-interval '1 minute' where id='<J>'`; ORG reabre o jogo | Sem botão `Colocar no jogo`; `O jogo já começou. A fila não anda mais.` | — | ☐ | ☐ |
| 24 | API (ORG) | `PROMOVER(Atl2)` depois do início | Negado (a nota exige; hoje pode aceitar: se `2xx`, FALHOU com DEB-018) | ATL2 segue `WAITLISTED` | ☐ | ☐ |
| 25 | rede | RESET(3, MANUAL, true); ATL2 "Vou"; ORG desliga a rede do aparelho (Android: `adb -s emulator-5554 shell svc wifi disable; svc data disable`; iOS: sem comando, registrar BLOQUEADO) e toca `Colocar no jogo` | `Não foi possível colocar essa pessoa no jogo. Tente novamente.`; religar a rede e tocar de novo funciona | ATL2 `CONFIRMED` só no segundo toque | ☐ | ☐ |
| 26 | ATL | **Os outros da espera não recebem nada.** RESET(3, MANUAL, true); ATL2 e ATL "Vou" (espera 1º, 2º); ORG coloca ATL2 | ATL fica em `Lista de espera · 1º` e sem item novo | CONTA(Atl) = 0 | ☐ | ☐ |

## PROM-005 · O job do prazo [MULTI]

Esperar até 70 s depois de vencer o prazo; conferir sempre com ESTADO e as mensagens do canal `WAITLIST_DEADLINE`:
`select recipient_count,body from group_messages where game_id='<J>' and channel='WAITLIST_DEADLINE';`

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | **3 vagas, 5 na espera, ordem de chegada.** RESET(4, FIFO, true); MENSAL(Org) | — | — | ☐ | ☐ |
| 2 | ORG | "Vou" (mensalista confirma, 1 de 4) | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 3 | ATL, ATL2, ATL3 | "Vou" nos três (avulsos, prioridade ligada: esperam mesmo com vaga) | `Lista de espera` com `Mensalista na espera sobe primeiro.` | `WAITLISTED|1,2,3` | ☐ | ☐ |
| 4 | ATL | "Levar convidado" → `Conv A`, depois `Conv B` | Os dois na espera | `Conv A` `WAITLISTED|4`, `Conv B` `WAITLISTED|5` | ☐ | ☐ |
| 5 | banco | `update games set confirmation_deadline=now()-interval '1 minute' where id='<J>'`; esperar até 70 s | — | ESTADO: ATL, ATL2, ATL3 `CONFIRMED` (pela ordem), `Conv A` e `Conv B` `WAITLISTED` (sobram 2); EVENTOS: três `SYSTEM|WAITLISTED|CONFIRMED` | ☐ | ☐ |
| 6 | ATL, ATL2, ATL3 | Trocar de aba | `Você está confirmado.` nos três | — | ☐ | ☐ |
| 7 | ORG | Central "Notificações" | Sem aviso de prazo no modo automático | `select count(*) from group_messages where game_id='<J>' and channel='WAITLIST_DEADLINE'` = 0 | ☐ | ☐ |
| 8 | banco | **Rodar de novo não repete.** Esperar mais 70 s | Nada muda | ESTADO e EVENTOS iguais | ☐ | ☐ |
| 9 | banco | **3 vagas, 5 na espera, escolha manual.** Repetir os passos 1 a 4 com RESET(4, MANUAL, true) (no manual, ORG mensalista com prioridade ligada confirma; os outros esperam) e vencer o prazo; esperar até 70 s | — | ESTADO: **ninguém sobe**; ORG `CONFIRMED`; os 5 seguem `WAITLISTED` | ☐ | ☐ |
| 10 | ORG | Central "Notificações" | Aviso `Prazo de confirmação encerrado: …` com `5 na lista de espera · 3 vagas abertas` e `O grupo escolhe quem entra: abra o jogo e libere as vagas.` | `recipient_count` = 1 (o dono) | ☐ | ☐ |
| 11 | ATL, ATL2, ATL3 | Central | Nenhum aviso de prazo para quem não gerencia | CONTA(Atl) = 0 | ☐ | ☐ |
| 12 | ORG e ATL | **Dono e admins.** Repetir 9 e 10 com ATL promovido a `Administrador` antes (Grupo → membros → `Atl` → papel) | ATL também recebe o aviso (admin) | `recipient_count` = 2 | ☐ | ☐ |
| 13 | banco | Esperar mais 70 s | Aviso não repete (um por jogo) | `recipient_count` e número de mensagens iguais | ☐ | ☐ |
| 14 | banco | **0 vaga.** RESET(2, FIFO, true); MENSAL(Org); MENSAL(Atl); ORG e ATL "Vou" (2 de 2); ATL2 e ATL3 "Vou" (espera); vencer o prazo; esperar 70 s | Nada acontece | ESTADO igual; sem mensagem `WAITLIST_DEADLINE` | ☐ | ☐ |
| 15 | banco | **Sem espera.** RESET(4, FIFO, false); ORG "Vou"; vencer o prazo; esperar 70 s | Nada acontece | ESTADO só com ORG; sem mensagem | ☐ | ☐ |
| 16 | banco | **Jogo já começado.** RESET(4, FIFO, true); MENSAL(Org); ORG "Vou"; ATL "Vou" (espera); `update games set confirmation_deadline=now()-interval '2 minutes', starts_at=now()-interval '1 minute' where id='<J>'`; esperar 70 s | Nada acontece | ATL segue `WAITLISTED` (se subir: FALHOU, DEB-018) | ☐ | ☐ |
| 17 | banco | **Jogo cancelado.** RESET(4, FIFO, true); ORG "Vou"; ATL "Vou"; `update games set status='CANCELLED', confirmation_deadline=now()-interval '1 minute' where id='<J>'`; esperar 70 s | Nada acontece | ATL segue `WAITLISTED` | ☐ | ☐ |

## PROM-007 · Posição na fila [MULTI]

ORG ocupa a única vaga como mensalista; **A** = ATL (avulso), **M** = ATL2 (mensalista), **B** = ATL3 (avulso).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(1, FIFO, true); MENSAL(Org); MENSAL(Atl2) | — | — | ☐ | ☐ |
| 2 | ORG | "Vou" (confirma) | `Você está confirmado.` | `CONFIRMED` | ☐ | ☐ |
| 3 | ATL, ATL2, ATL3 nessa ordem | "Vou" em cada um | — | `WAITLISTED` com sequências 1, 2, 3 (A, M, B) | ☐ | ☐ |
| 4 | ATL, ATL2, ATL3 | Trocar de aba em cada um | Antes do prazo, prioridade ligada: ATL2 (M) `Lista de espera · 1º`, ATL (A) `Lista de espera · 2º`, ATL3 (B) `Lista de espera · 3º` | — | ☐ | ☐ |
| 5 | ORG | Abrir o jogo → `Lista de espera` | O dono vê a mesma ordem: 1º `Atl2`, 2º `Atl`, 3º `Atl3` (`Nº na fila`) | — | ☐ | ☐ |
| 6 | banco | `update games set confirmation_deadline=now()-interval '1 minute' where id='<J>'` (o job não sobe ninguém: jogo cheio) | — | — | ☐ | ☐ |
| 7 | ATL, ATL2, ATL3 | Reabrir "Ver o jogo" em cada um | Depois do prazo a ordem é só por chegada, sem aviso extra: ATL `Você está em 1º na lista de espera.`, ATL2 `2º`, ATL3 `3º` (o mensalista que era 1º virou 2º) | — | ☐ | ☐ |
| 8 | ORG | Abrir o jogo | 1º `Atl`, 2º `Atl2`, 3º `Atl3`, igual aos atletas | — | ☐ | ☐ |
| 9 | ORG | "Alterar" → "Não vou" (A sobe) | `Sua vaga foi liberada.` | ATL `CONFIRMED` | ☐ | ☐ |
| 10 | ATL2, ATL3 | Trocar de aba | ATL2 `Lista de espera · 1º`; ATL3 `Lista de espera · 2º` (depois que A sobe, o próximo é o 1º) | — | ☐ | ☐ |
| 11 | banco | **Dois avulsos.** RESET(1, FIFO, true); MENSAL(Org); ORG "Vou"; ATL e ATL3 "Vou" (avulsos) | ATL `Lista de espera · 1º`, ATL3 `Lista de espera · 2º` (antes e depois do prazo; repetir depois de vencer o prazo) | `WAITLISTED|1,2` | ☐ | ☐ |
| 12 | banco | **Convidado conta como avulso.** RESET(2, FIFO, true); MENSAL(Org); MENSAL(Atl2); ORG e ATL2 "Vou" (2 de 2); ATL3 "Vou" (avulso, espera); ATL2 "Levar convidado" → `Conv M` | ORG vê 1º `Atl3`, 2º `Conv M` (convidado do mensalista vale como avulso, na ordem de chegada) | `Conv M` `WAITLISTED|2` | ☐ | ☐ |

## PROM-008 · A cobrança de quem sobe [MULTI]

Pré: `update games set game_fee_cents=2000 where id='<J>'` depois de cada RESET (o RESET zera a taxa). Cobrança esperada: `kind='GAME'`, `amount_cents=2000`, `due_date` = data do jogo, `status='PENDING'`.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco | RESET(3, FIFO, false) + taxa 2000 | — | COBRANÇAS vazio | ☐ | ☐ |
| 2 | ATL (avulso) | "Vou" (confirma direto) | `Você está confirmado.` | COBRANÇAS: uma de ATL, 2000, `PENDING` | ☐ | ☐ |
| 3 | ATL | Repetir "Vou" (já confirmado) | Nada muda | COBRANÇAS continua com uma linha | ☐ | ☐ |
| 4 | ATL2 e ATL3 | "Vou" nos dois (3 de 3 com ATL) | Os dois `Você está confirmado.` | duas cobranças novas | ☐ | ☐ |
| 5 | ORG (avulso) | "Vou" (jogo cheio, espera) | `Lista de espera · 1º` | **sem** cobrança de ORG (quem espera não é cobrado) | ☐ | ☐ |
| 6 | ATL2 | "Alterar" → "Não vou" (ORG sobe da espera) | `Sua vaga foi liberada.` | Cobrança de ORG criada na promoção; a de ATL2 segue `PENDING` (desistência não cancela) | ☐ | ☐ |
| 7 | ORG | Caixa do grupo (Grupo → "Caixa do grupo") | A cobrança de Atl2 aparece com a etiqueta `Desistiu` e a ação `Dispensar` ao lado de `Recebi` (decisão de 17/10) | — | ☐ | ☐ |
| 8 | banco | **Mensalista, qualquer caminho.** RESET(3, FIFO, false) + taxa 2000; MENSAL(Atl2) | — | — | ☐ | ☐ |
| 9 | ATL2 (mensalista) | "Vou" (confirma direto) | `Você está confirmado.` | **sem** cobrança de ATL2 (mensalista paga uma vez por mês). Hoje o código cobra todo confirmado (DEB-021): se criou, FALHOU | ☐ | ☐ |
| 10 | banco | **Promovido pelo job.** RESET(2, FIFO, false) + taxa 2000; MENSAL(Atl2); ORG e ATL "Vou"; ATL3 "Vou" (espera 1º) e ATL2 "Vou" (espera 2º); `update games set capacity=3, confirmation_deadline=now()-interval '1 minute'`; esperar 70 s | ATL3 sobe | Cobrança de ATL3 (avulso) criada na promoção; ATL2 (mensalista) segue na espera sem cobrança | ☐ | ☐ |
| 11 | banco | **Promovido por "Colocar no jogo".** RESET(3, MANUAL, false) + taxa 2000; ATL3 "Vou" (espera); ORG `Colocar no jogo` ao lado de `Atl3` | Atl3 confirmado | Cobrança de ATL3 criada | ☐ | ☐ |
| 12 | banco | **Jogo sem valor.** RESET(3, FIFO, false) (taxa nula); ATL "Vou" | `Você está confirmado.` | COBRANÇAS vazio | ☐ | ☐ |
| 13 | banco | **Convidado promovido.** RESET(2, FIFO, false) + taxa 2000; MENSAL(Atl); ATL "Vou" e ATL2 "Vou" (2 de 2); ATL "Levar convidado" → `Conv A` (a folha mostra `Se o convidado entrar no jogo, a taxa de R$ 20,00 vem para você.`); ATL2 "Alterar" → "Não vou" | `Conv A` sobe | Cobrança em nome de ATL (anfitrião mensalista): `guest_seq=1`, `guest_display_name='Conv A'`, 2000; sem cobrança de ATL pela própria vaga | ☐ | ☐ |
| 14 | ATL | "Ver o jogo" | Linha do convidado `Convidado seu · R$ 20,00 na sua conta` | — | ☐ | ☐ |
| 15 | ATL | "Tirar" no `Conv A` confirmado | Folha `Sai dos confirmados, a vaga vai para o próximo da lista e a cobrança de R$ 20,00 em seu nome é cancelada.` | Cobrança de `Conv A` `CANCELLED` | ☐ | ☐ |
| 16 | banco + ATL | **Convidado na espera não gera cobrança.** `Levar convidado` → `Conv B` com jogo cheio | `Conv B entrou na lista de espera.` | COBRANÇAS sem linha de `Conv B` | ☐ | ☐ |

## PROM-009 · Os avisos da lista de espera e da promoção [MULTI]

O stack local liga `saqz.notifications.reminder.enabled`, mas o lembrete automático só sai nos horários 8h, 11h, 14h, 17h e 20h
(São Paulo): fica no relato como `[push: só produção]`/horário, e aqui se confere o corpo pelo "Avisar" manual, que deve ser **idêntico**.
Pré: RESET(4, FIFO, true); MENSAL(Org); ORG "Vou".

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL, ATL2 | "Vou" em ATL (avulso, espera 1º) e ATL2 (avulso, espera 2º); ATL3 sem resposta | — | `WAITLISTED|1,2` | ☐ | ☐ |
| 2 | ORG | Grupo → bloco de pendentes: tocar `Avisar` (mostra `1 pessoa sem resposta` com ATL3) | Toast `Lembrete enviado no Saqz para 1 pessoa.` | `select channel,recipient_count from group_messages where game_id='<J>' order by created_at desc limit 1` = `REMINDER|1` | ☐ | ☐ |
| 3 | ATL3 | Central "Notificações" | Item do lembrete com o corpo abaixo | — | ☐ | ☐ |
| 4 | banco | Corpo do lembrete: `select body from group_messages where game_id='<J>' and channel='REMINDER' order by created_at desc limit 1` | `Jogo: <dia>, DD/MM às HH:MM`, `Local: Quadra QA`, `Confirmações até <dia> HHhMM` (ou `Prazo encerrado`), `Vagas: 1/4`, e as seções `✅ Confirmados:` (`QA Org <data>`), `🕒 Lista de espera:` (duas linhas) e `❌ Fora:` só se houver | corpo completo; até 2.000 caracteres | ☐ | ☐ |
| 5 | banco | **Ordem da espera = posição mostrada.** Comparar a ordem das linhas em `🕒 Lista de espera:` com o 1º e 2º de ATL e ATL2 na Início | A mesma ordem (a lista nominal usa a ordem real de promoção, PROM-002; com um mensalista na fila antes do prazo ele sai à frente) | — | ☐ | ☐ |
| 6 | banco | Vencer o prazo: `update games set confirmation_deadline=now()-interval '1 minute'`; ORG toca `Avisar` de novo | A linha de prazo vira `Prazo encerrado`; as vagas seguem `Vagas: 1/4` | corpo novo; o do "Avisar" manual tem os mesmos campos que o automático | ☐ | ☐ |
| 7 | banco | Jogo lotado: `update games set capacity=1`; "Avisar" | `Lista completa` no lugar de `Vagas: …` | — | ☐ | ☐ |
| 8 | banco + ORG | **Confirmado direto ou promovido** (PRES-013): RESET(1, FIFO, false); ATL "Vou"; ATL2 "Vou" (espera); ATL "Não vou" | ATL2 recebe item na central; ATL não | CONTA(Atl2) = 1; CONTA(Atl) = 0 | ☐ | ☐ |
| 9 | ORG | **Organizador tirou alguém** (PRES-013): "Tirar" ATL2 | ATL2 recebe item "tirado" sem o motivo | CONTA(Atl2) = 2 | ☐ | ☐ |
| 10 | banco | **Prazo vencido, escolha manual**: ver PROM-005 passos 9 a 13 | Um aviso a dono e admins, uma vez por jogo | ver PROM-005 | ☐ | ☐ |
| 11 | banco | **Jogo cancelado.** RESET(4, FIFO, false); ATL "Vou" (confirmado); ATL2 "Vou" (confirmado); ATL3 sem resposta | — | — | ☐ | ☐ |
| 12 | ORG | Abrir o jogo → "Cancelar jogo" → confirmar em `Cancelar o jogo?` (`Os confirmados recebem um aviso e o jogo some da agenda. Isso não pode ser desfeito.`) | O jogo some da agenda | `select status from games where id='<J>'` = `CANCELLED` | ☐ | ☐ |
| 13 | ATL, ATL2 | Central "Notificações" | Item `O jogo de <dia> DD/MM às HHhMM foi cancelado.` nos dois (confirmados) | CONTA(Atl) e CONTA(Atl2) ≥ 1 | ☐ | ☐ |
| 14 | ATL3 | Central | Sem aviso de cancelamento para quem não respondeu (só confirmados e espera) | CONTA(Atl3) conforme o canal de lembrete anterior | ☐ | ☐ |
| 15 | banco | `select channel,recipient_count,body from group_messages where game_id='<J>' order by created_at desc limit 1` | `NOTICE` com o corpo do cancelamento | `recipient_count` = 2 | ☐ | ☐ |
| 16 | WhatsApp | Aviso do cancelamento no grupo do WhatsApp | `[BLOQUEADO local]`: sem WhatsApp no stack padrão (com `ferramentas/uazapi-falso.mjs` ligado, conferir a mensagem gravada) | — | ☐ | ☐ |

---

## Rodadas

**Rodada 1: ORG no iPhone, ATL no Android.** ORG = iPhone 17, ATL = `Saqz_QA_A`, ATL2 = iPhone 17e, ATL3 = `Saqz_QA_B`.
Rodar a Preparação do roteiro 03 e as regras na ordem do arquivo. Marcar a coluna da plataforma do aparelho que fez o passo
(ORG vale iOS, ATL Android, ATL2 iOS, ATL3 Android).

**Rodada 2: ORG no Android, ATL no iPhone.** ORG = `Saqz_QA_A`, ATL = iPhone 17, ATL2 = `Saqz_QA_B`, ATL3 = iPhone 17e.
`stack.sh reset`, nova Preparação com os aparelhos trocados, regras de novo; marcar iOS/Android pelo aparelho em cada papel
(ORG vale Android, ATL iOS, ATL2 Android, ATL3 iOS).

## Resumo

Críticos: PROM-001, PROM-002, PROM-004 (3 de 8).

## O que ficou sem passo

- **Falha numa promoção (PROM-005, última linha):** exige injetar erro na cobrança no meio do job; só dá para cobrir por teste (`SettleWaitlistAtDeadlineTest`).
- **Lembrete automático (PROM-009):** sai só às 8h, 11h, 14h, 17h e 20h; o corpo é conferido pelo "Avisar" manual, que deve ser igual. O horário em si fica para produção.
- **Push (PROM-004, PROM-009):** só produção; aqui a conferência é a central de avisos. O aviso direto da promoção (PRES-013, DEB-013) ainda não existe no código desta branch: os passos de central de PROM-004 (7) e PROM-009 (8 e 9) devem dar FALHOU até a implementação.
- **WhatsApp do cancelamento:** sem WhatsApp no stack padrão.
- **Cobrança de mensalista e vínculo de quem sobe (PROM-008):** a nota pede "mensalista nunca é cobrado" e o vínculo real de quem sobe pelo job/ajuste (DEB-021); hoje o código cobra todo confirmado. Os passos 9 e 10 podem falhar por isso.
- **"Posição não informada" (PROM-007):** só aparece se a posição não puder ser calculada; não há como forçar isso pelo app.
- **Rede no iOS (PROM-004, passo 25):** o simulador não tem comando para cortar a rede; vale só no Android.
