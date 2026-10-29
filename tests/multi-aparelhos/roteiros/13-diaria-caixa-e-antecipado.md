# Roteiro 13 · Valor por jogo, caixa novo, diária retroativa, Pix na página do grupo, pagamento antecipado e lista de grupos

Regras do cofre cobertas: JOGO-008 (valor por jogo, DEB-146), FIN-006 (caixa novo, DEB-148), FIN-016 (diária retroativa, DEB-147),
FIN-008 (Pix na página do grupo, DEB-148), PROM-010 (vaga do avulso só com pagamento antecipado, DEB-150) e GER-003 (lista de
grupos, DEB-145). Tabelas de um aparelho com as mesmas regras: `tests/roteiros/jogos.md`, `financeiro.md`,
`presenca-e-lista-de-espera.md` e `geral.md`. Se a nota do cofre e o app divergirem, vale a nota: registre o texto que apareceu.

Cada rodada usa **uma plataforma**: ORG e ATL em aparelhos do mesmo sistema; ATL2 e ATL3 só pela API. As duas rodadas podem correr
ao mesmo tempo contra o mesmo stack, cada uma com o seu grupo e as suas contas (ver "Rodadas", no fim).

Papéis: **ORG** (dono), **ATL** (avulso, no aparelho), **ATL2** (avulso, API), **ATL3** (mensalista, API; leva os convidados Bia e
Caio). Contas `qa-<papel>+<tag>@saqz.local`, senha `SenhaQA2026`, nomes `QA Org <R>`, `QA Atl <R>` etc.

**Atalhos.** Cada comando do terminal começa do zero: comece **toda** chamada com
`cd <raiz do repo> && R=<R> source tests/multi-aparelhos/ferramentas/rodada.sh;`. Ela dá `PSQL "<sql>"`, `$G` (subconsulta do
grupo), `$GRUPO` (id), `U Atl2` (subconsulta da pessoa), `J 'Quadra J1'` (subconsulta do jogo), `JID 'Quadra J1'` (id),
`API <papel> <MÉTODO> <caminho> [json] [If-Match]` (imprime o código HTTP e o começo do corpo; `$RID` no json vira um UUID novo),
`MARCAR <dias> <HH:MM> <quadra> [vagas]` (marca e publica pela API, como o app antigo), `PASSADO <quadra> <dias>`,
`ENCERRA <quadra>` e `LEMBRETE <quadra>` (cria o lembrete do jogo e imprime o código do link de presença).

Leitura dos valores: "R$ 20" e "R$ 20,00" são o mesmo texto; anote como apareceu. Datas `<amanhã>`, `<ontem>` etc. no formato que
a tela usa.

## Preparação (já feita pela sessão principal)

Stack com o bootJar da `main` (V206), apps `devDebug` e `SaqzDev` novos instalados, `preparar-grupo.mjs 29/10 i` e `29/10 a`, grupo
sem valor por jogo (`gameFeeUndefined`), Ordem de chegada, prioridade de mensalista desligada e ATL3 mensalista. A foto
`quadra-900.jpg` já está na galeria do aparelho do ORG.

| # | Aparelho | Faça | Espere | Banco |
| --- | --- | --- | --- | --- |
| P1 | ORG, ATL | Abrir o app limpo e entrar com e-mail e senha (`qa-org+<tag>@saqz.local` no ORG, `qa-atl+<tag>@saqz.local` no ATL) | Início de cada um; o grupo `QA Vôlei <R>` em Grupos | `PSQL "select default_game_fee_cents is null, default_no_game_fee, promotion_mode, mensalista_priority from access_groups where id='$GRUPO'"` = `t\|f\|FIFO\|f` |

## C · Caixa novo (FIN-006) — antes de qualquer lançamento

| # | Aparelho | Faça | Espere | Banco | Resultado |
| --- | --- | --- | --- | --- | --- |
| C1 | ORG | Grupo → `Caixa do grupo` | `SALDO DO CAIXA`; cartão `Quanto o grupo já tem em caixa?` com `Entra como saldo inicial e soma no saldo.`, campo `Valor`, `Salvar saldo inicial` e `Começar do zero`; botões `Entrada` e `Saída` (sem `Registrar`); sem cartão do Pix | `PSQL "select opening_balance_cents is null from access_groups where id='$GRUPO'"` = `t` | ☐ |
| C2 | ORG | Valor `250` → `Salvar saldo inicial` | Pergunta some; saldo `R$ 250,00` e `Inclui o saldo inicial de R$ 250,00` | `opening_balance_cents` = `25000` | ☐ |
| C3 | ORG | `Mais ações do caixa` (⋯) | Itens `Saldo inicial` (apoio `R$ 250 · antes do Saqz`) e `Criar cobranças do mês`; nota `O Pix do grupo fica na página do grupo.` | — | ☐ |
| C4 | ORG | ⋯ → `Saldo inicial` → folha `Saldo inicial` (`Quanto o grupo tinha antes do Saqz. Soma no saldo.`) → `300` → `Salvar` | Saldo `R$ 300,00` | `opening_balance_cents` = `30000` | ☐ |
| C5 | terminal | `API org PUT "/api/groups/$GRUPO/finance/opening-balance" '{"amountCents":-100}' 9`; `API atl2 GET "/api/groups/$GRUPO/finance/opening-balance"` | `400` com `amountCents`; `403` | — | ☐ |
| C6 | ORG | `Saída` | `Nova saída`; categorias `Quadra`, `Material`, `Árbitro`, `Outros` numa linha só (sem quebrar); atalhos `R$ 80`, `R$ 120`, `R$ 160`; data `Hoje · <hoje>` com `Toque para escolher outro dia.` | — | ☐ |
| C7 | ORG | Tocar na data → calendário `Data do lançamento` → escolher **ontem** → `Usar esta data`; `Quadra`; atalho `R$ 120`; descrição `quadra de terça` → `Salvar saída` | Volta ao caixa: saldo `R$ 180,00`; placar `Saiu` `R$ 120` em vermelho (print) | `PSQL "select category, direction, amount_cents, expense_date = current_date - 1 from group_expenses where group_id='$GRUPO'"` = `VENUE\|OUT\|12000\|t` | ☐ |
| C8 | ORG | `Entrada` | `Nova entrada`; categorias `Patrocínio`, `Venda`, `Outros`; **sem** atalhos de valor | — | ☐ |
| C9 | ORG | Valor `50`, `Patrocínio`, descrição `patrocínio da loja` → `Salvar entrada` | Saldo `R$ 230,00`; placar `Entrou` `R$ 50` | linha `SPONSORSHIP\|IN\|5000` | ☐ |
| C10 | ORG | Tocar `Entrou`, depois `Saiu`, depois `Falta receber` no placar | A lista de baixo troca: `Entradas de <mês>` com a entrada; `Saídas de <mês>` com a saída; `Ninguém devendo. Tudo recebido.` | — | ☐ |
| C11 | terminal | Saída antiga de Racha (app antigo): `API org POST "/api/groups/$GRUPO/expenses" '{"requestId":"$RID","description":"racha antigo","amountCents":3000,"expenseDate":"<hoje AAAA-MM-DD>","category":"RACHA","direction":"OUT"}'`; e entrada inválida: o mesmo com `"category":"SPONSORSHIP","direction":"OUT"` | `201`; `400` com `category` | — | ☐ |
| C12 | ORG | Reabrir o caixa → `Ver extrato` | Topo fixo `Saldo inicial` · `Antes do Saqz` · `R$ 300` com `Editar`; filtro `Tudo` · `Entradas` · `Saídas`; a saída da quadra no dia de **ontem**; `Racha` na saída de R$ 30; valores de saída em vermelho (print) | — | ☐ |
| C13 | ORG | Filtro `Saídas` | Só as duas saídas | — | ☐ |

## V · Valor por jogo (JOGO-008)

| # | Aparelho | Faça | Espere | Banco | Resultado |
| --- | --- | --- | --- | --- | --- |
| V1 | ORG | Página do grupo | Cartão `Só você e os admins veem` · `Defina o valor por jogo` · `Hoje o avulso joga sem diária.` · `Definir valor` | — | ☐ |
| V2 | ATL | Página do grupo | Sem esse cartão | — | ☐ |
| V3 | ORG | `Marcar jogo`: **amanhã** 20:00, quadra `Quadra J1`, vagas `6`; no bloco `Valor por jogo` (`Cobrar diária` · `Sem diária`) **não** escolher nada → `Marcar jogo` | Não salva; `Escolha o valor ou Sem diária.` no bloco; anote a mensagem do topo | `PSQL "select count(*) from games where group_id='$GRUPO'"` = `0` | ☐ |
| V4 | ORG | `Cobrar diária` com o valor vazio → `Marcar jogo`; depois digitar `0` → `Marcar jogo` | Não salva nas duas; `Informe um valor maior que R$ 0,00.` | `0` jogos | ☐ |
| V5 | ORG | `Sem diária` (apoio `O avulso joga sem pagar por jogo.`) → `Marcar jogo` | Jogo criado; de volta ao grupo, o cartão `Defina o valor por jogo` sumiu | `PSQL "select no_game_fee, game_fee_cents is null, status from games where id=$(J 'Quadra J1')"` = `t\|t\|PUBLISHED`; `PSQL "select default_no_game_fee from access_groups where id='$GRUPO'"` = `t` | ☐ |
| V6 | ATL | Abrir o J1 | **Sem** a linha `Diária de …`; `Vou` → `Você está confirmado.` | `PSQL "select count(*) from group_charges where game_id=$(J 'Quadra J1')"` = `0` | ☐ |
| V7 | terminal | ATL2 e ATL3 dão Vou; ATL3 leva a convidada Bia: `API atl2 PUT "/api/groups/$GRUPO/games/$(JID 'Quadra J1')/attendance" '{"requestId":"$RID","intent":"CONFIRM"}'`; o mesmo com `atl3`; `API atl3 POST "/api/groups/$GRUPO/games/$(JID 'Quadra J1')/attendance/guests" '{"requestId":"$RID","displayName":"Bia"}'` | `200`, `200`, `201` | `PSQL "select count(*) from game_attendance where game_id=$(J 'Quadra J1') and status='CONFIRMED'"` = `4`; cobranças do J1 = `0` | ☐ |
| V8 | ORG | Editar grupo → `Regras dos jogos` → `Valor por jogo`: está em `Sem diária` → trocar para `Cobrar diária` `20` → salvar | Salva | `PSQL "select default_game_fee_cents, default_no_game_fee from access_groups where id='$GRUPO'"` = `2000\|f`; o J1 continua `no_game_fee` = `t` | ☐ |
| V9 | ORG | `Marcar jogo`: amanhã 22:00, `Quadra J2`, vagas `6` | O bloco já vem em `Cobrar diária` com `R$ 20,00`; `Marcar jogo` salva | `PSQL "select game_fee_cents, no_game_fee from games where id=$(J 'Quadra J2')"` = `2000\|f` | ☐ |
| V10 | ATL | Abrir o J2 | `Diária de R$ 20, cobrada ao confirmar` **antes** do `Vou`; `Vou` → `Você está confirmado.` | `PSQL "select amount_cents, status, due_date from group_charges where game_id=$(J 'Quadra J2') and member_user_id=$(U Atl)"` = `2000\|PENDING\|<amanhã>` | ☐ |
| V11 | ATL | Link de presença do J2: `LEMBRETE 'Quadra J2'` (anote o código `L1`) → abrir `attendance/<L1>` no ATL (comandos em "Rodadas") | Tela do link com `Diária de R$ 20, cobrada ao confirmar` junto do jogo | — | ☐ |
| V12 | terminal + ORG | `PSQL "update access_groups set monthly_fee_cents=8000 where id='$GRUPO'"`; ORG → `Marcar jogo` → `Cobrar diária` | Embaixo do valor: `Sugestão: mensalidade ÷ 4 (R$ 80 ÷ 4).`; sair do editor **sem** salvar | `2` jogos | ☐ |
| V13 | terminal | App antigo (sem valor nem marca): `MARCAR 3 20:00 'Quadra Compat'` | Imprime `Quadra Compat <id> PUBLISHED` | `PSQL "select game_fee_cents, no_game_fee from games where id=$(J 'Quadra Compat')"` = `2000\|f` | ☐ |

## R · Quem jogou sem cobrança: a diária retroativa (FIN-016)

| # | Aparelho | Faça | Espere | Banco | Resultado |
| --- | --- | --- | --- | --- | --- |
| R1 | ORG | Abrir o J1 → editar o jogo → `Valor por jogo`: `Cobrar diária` `20` → salvar | Salva | `PSQL "select game_fee_cents, no_game_fee from games where id=$(J 'Quadra J1')"` = `2000\|f`; cobranças do J1 = `0` | ☐ |
| R2 | ORG | `Caixa do grupo` | Faixa `3 jogaram sem cobrança` · `No jogo de <amanhã>` com `Revisar` (ATL, ATL2 e Bia; ATL3 é mensalista) | `API org GET "/api/groups/$GRUPO/finance/uncharged"` → `"peopleCount":3` | ☐ |
| R3 | terminal | `PASSADO 'Quadra J1' 2; ENCERRA 'Quadra J1'` | `UPDATE 1` duas vezes | `status` do J1 = `COMPLETED` | ☐ |
| R4 | ORG | Abrir o J1 (agenda do grupo ou `Jogos anteriores`) → `Encerrar jogo e acertar` (ou `Ver acerto`) | Acerto com `JOGO ENCERRADO`; faixa `3 jogaram sem cobrança` com `Confirmaram antes do valor` e `Revisar`; `Mensalistas` numa linha (`1 · cobertos pela mensalidade`) | — | ☐ |
| R5 | ORG | `Revisar` | `Quem jogou sem cobrança`; `O jogo não tinha valor quando confirmaram.`; `Valor da diária` `R$ 20,00`; linhas `QA Atl <R>` (`Avulso`), `QA Atl2 <R>` (`Avulso`) e `Bia` (`Convite · paga QA Atl3 <R>`), todas em `Cobrar`; `Mensalistas ficam de fora: já pagam a mensalidade.`; resumo `3 cobranças`; `Confirmar` | — | ☐ |
| R6 | ORG | Valor `10`; estado de `QA Atl2` → `Já pagou` → no `Entra no caixa em <ontem> · Pix`, tocar `Pix` → `Como pagou` → `Dinheiro`; estado de `Bia` → `Não cobrar` | Linha do ATL2 com `Entra no caixa em <ontem> · Dinheiro`; resumo `1 cobrança · 1 já pagou · 1 sem cobrança` | — | ☐ |
| R7 | ORG | `Confirmar` | Volta ao acerto sem a faixa; seção `Sem cobrança` com Bia e a etiqueta `Não cobrar` | `PSQL "select member_display_name, coalesce(guest_display_name,'-'), amount_cents, status, coalesce(paid_method,'-'), due_date=current_date from group_charges where game_id=$(J 'Quadra J1') order by 1,2"` = `QA Atl <R>\|-\|1000\|PENDING\|-\|t` e `QA Atl2 <R>\|-\|1000\|PAID\|CASH\|…`; `PSQL "select count(*) from game_uncharged_skips where game_id=$(J 'Quadra J1')"` = `1` | ☐ |
| R8 | ORG | `Caixa do grupo` → `Ver extrato` | A diária de R$ 10 do ATL2 no dia de **ontem** (data do jogo); sem faixa de retroativa no caixa | `uncharged` → `"peopleCount":0` | ☐ |
| R9 | ATL | Central de avisos; depois `Minhas cobranças` no grupo | Nada novo sobre cobrança na central; em Minhas cobranças, a diária de `R$ 10` pendente (J1) e a de `R$ 20` (J2) | — | ☐ |
| R10 | ORG | Acerto do J1 → `Reabrir a revisão` → Bia → `Cobrar` → `Confirmar` | Bia sai de `Sem cobrança` | cobrança `QA Atl3 <R>\|Bia\|1000\|PENDING` no J1; `game_uncharged_skips` do J1 = `0` | ☐ |
| R11 | terminal | `API atl GET "/api/groups/$GRUPO/games/$(JID 'Quadra J1')/uncharged"`; depois o mesmo POST duas vezes com o **mesmo** requestId: `API org POST "/api/groups/$GRUPO/games/$(JID 'Quadra J1')/uncharged/review" '{"requestId":"6f1d2c3b-0000-4000-8000-000000000013","amountCents":1000,"people":[{"memberId":"<id do ATL>","guestSeq":0,"decision":"CHARGE"}]}'` (id: `PSQL "select $(U Atl)"`) | `403`; as duas respostas iguais, com `"ignored":1` | cobranças do J1 continuam `3` | ☐ |

## X · Pix do grupo na página do grupo (FIN-008)

| # | Aparelho | Faça | Espere | Banco | Resultado |
| --- | --- | --- | --- | --- | --- |
| X1 | terminal | `PSQL "update access_groups set pix_key='pix.qa@saqz.local', pix_label='E-mail' where id='$GRUPO'"` | `UPDATE 1` | — | ☐ |
| X2 | ATL | Página do grupo (reabrir) | Com cobrança em aberto: **sem** a seção `Pix do grupo`; o cartão da cobrança mostra a chave | — | ☐ |
| X3 | ORG | `Caixa do grupo` → `Falta receber` | Linha do ATL com `Recebi` e o ⋮ (`Mais ações de …`) com `Cobrar`, `Dispensar` e `Cancelar` (fechar o menu sem escolher) | — | ☐ |
| X4 | ORG | `Recebi` nas duas diárias do ATL (J1 e J2) | Somem de `Falta receber`; `Entrou` sobe R$ 30 | `PSQL "select count(*) from group_charges where group_id='$GRUPO' and member_user_id=$(U Atl) and status='PENDING'"` = `0` | ☐ |
| X5 | ATL | Página do grupo (reabrir) | Seção `Pix do grupo` logo depois de `Minhas cobranças`, com `pix.qa@saqz.local`, o tipo `E-mail` e `Copiar`; `Copiar` copia a chave (conferir a área de transferência, comandos em "Rodadas") | — | ☐ |
| X6 | ORG | Página do grupo | Seção `Pix do grupo` depois da `Galera` | — | ☐ |
| X7 | terminal + ATL + ORG | `PSQL "update access_groups set pix_key=null, pix_label=null where id='$GRUPO'"`; reabrir o grupo nos dois | ATL sem a seção; ORG sem a seção (anote o aviso ou a checklist do Pix que aparecer) | — | ☐ |
| X8 | terminal | Devolver a chave: o comando do X1 | `UPDATE 1` | — | ☐ |

## A · Vaga do avulso só com pagamento antecipado (PROM-010)

| # | Aparelho | Faça | Espere | Banco | Resultado |
| --- | --- | --- | --- | --- | --- |
| A1 | ORG | Editar grupo → `Como preencher as vagas` | Em `Ordem de chegada`: **sem** `Vaga só com pagamento antecipado` | — | ☐ |
| A2 | ORG | `Escolha manual` → aparece `Vaga só com pagamento antecipado` (`O avulso paga antes de entrar.`) → ligar → salvar | Ligada, com `Vale para avulso e convidado. A diária nasce no Vou, com o Pix do grupo.`; salva | `PSQL "select promotion_mode, advance_payment_required from access_groups where id='$GRUPO'"` = `MANUAL\|t` | ☐ |
| A3 | terminal | `MARCAR 2 20:00 'Quadra J3' 6` | `Quadra J3 <id> PUBLISHED` | `game_fee_cents` do J3 = `2000` | ☐ |
| A4 | ATL | Abrir o J3 → `Vou` | Na espera, com `Pague R$ 20 para entrar`, `A vaga é liberada pelo organizador depois do pagamento.` e `Copiar o Pix do grupo`; tocar → `Pix do grupo copiado.` | `PSQL "select a.status, c.amount_cents, c.status, c.due_date from game_attendance a join group_charges c on c.game_id=a.game_id and c.member_user_id=a.member_user_id where a.game_id=$(J 'Quadra J3') and a.member_user_id=$(U Atl) and a.guest_seq=0"` = `WAITLISTED\|2000\|PENDING\|<data do J3>` | ☐ |
| A5 | ATL | `Minhas cobranças` do grupo; depois a Início | A diária de `R$ 20` do J3 nos dois | — | ☐ |
| A6 | terminal | ATL3 (mensalista) dá Vou e leva Bia e Caio; ATL2 dá Vou: `API atl3 PUT "/api/groups/$GRUPO/games/$(JID 'Quadra J3')/attendance" '{"requestId":"$RID","intent":"CONFIRM"}'`; `API atl3 POST "/api/groups/$GRUPO/games/$(JID 'Quadra J3')/attendance/guests" '{"requestId":"$RID","displayName":"Bia"}'`; o mesmo com `Caio`; o PUT com `atl2` | `200`, `201`, `201`, `200` | `PSQL "select member_display_name, coalesce(guest_display_name,'-'), status from group_charges where game_id=$(J 'Quadra J3') order by 1,2"` = 4 linhas `PENDING`: ATL, ATL2, `QA Atl3 <R>\|Bia`, `QA Atl3 <R>\|Caio` (nenhuma só do ATL3) | ☐ |
| A7 | ORG | Abrir o J3 → `Na espera` | ATL e ATL2 com `Falta pagar R$ 20`, `Recebi` e `Colocar`; ATL3 sem linha de pagamento; Bia e Caio com `QA Atl3 <R> não pagou R$ 20`; rodapé `Você escolhe quem entra. Pix e dinheiro chegam por fora: marque Recebi.` | — | ☐ |
| A8 | ORG | `Recebi` no ATL | Linha vira `Pagou R$ 20` | cobrança do ATL no J3 = `PAID` | ☐ |
| A9 | ORG | `Colocar` no ATL | Confirmado sem folha | ATL `CONFIRMED`; continua **uma** cobrança do ATL no J3 | ☐ |
| A10 | ATL | Reabrir o J3 | `Você está confirmado.`; sem a caixa de pagar | — | ☐ |
| A11 | ORG | `Colocar` na Bia | Folha `… ainda não pagou` · `Diária de R$ 20. Como você quer colocar …?` com `Colocar, já pagou` (`Marca os R$ 20 como recebidos.`), `Colocar e cobrar depois` (`A diária fica pendente, como hoje.`) e `Voltar` (anote os nomes que a folha usa) → `Colocar, já pagou` | Bia confirmada | cobrança `QA Atl3 <R>\|Bia` = `PAID` | ☐ |
| A12 | ORG | `Colocar` no Caio → `Colocar e cobrar depois` | Caio confirmado | cobrança do Caio = `PENDING` | ☐ |
| A13 | ORG | `Colocar` no ATL3 | Confirmado sem folha | sem cobrança do ATL3 sozinho | ☐ |
| A14 | terminal | `API atl PUT "/api/groups/$GRUPO/games/$(JID 'Quadra J3')/attendance" '{"requestId":"$RID","intent":"DECLINE"}'`; e o atleta tentando: `API atl2 PUT "/api/groups/$GRUPO" '{"advancePaymentRequired":false}'` | `200`; `403` | cobrança do ATL no J3 continua `PAID` | ☐ |
| A15 | terminal | `PASSADO 'Quadra J3' 3; ENCERRA 'Quadra J3'` | `UPDATE 1` duas vezes | ATL2 continua `WAITLISTED` com a diária `PENDING` | ☐ |
| A16 | ORG | Acerto do J3 | `Não jogaram · você decide` com ATL (`Desistiu`, `Pagou R$ 20`) e ATL2 (`Não liberado`, `Falta pagar R$ 20`) | — | ☐ |
| A17 | ORG | Editar grupo → `Como preencher as vagas` → `Ordem de chegada` | A opção some, com `A vaga volta a ser por ordem de chegada; quem está na espera sobe pela fila.`; salvar | `MANUAL`→`FIFO` e `advance_payment_required` = `f`; as cobranças do J3 não mudam | ☐ |

## L · Lista de grupos com foto (GER-003)

| # | Aparelho | Faça | Espere | Banco | Resultado |
| --- | --- | --- | --- | --- | --- |
| L1 | ORG | Editar grupo → foto do grupo → escolher `quadra-900.jpg` da galeria → salvar | Foto no topo da página do grupo | `PSQL "select byte_size > 0 from group_photos where group_id='$GRUPO'"` = `t` | ☐ |
| L2 | terminal | `MARCAR 1 21:00 'Quadra J4' 6` | `Quadra J4 <id> PUBLISHED` | — | ☐ |
| L3 | ATL | Aba Grupos (puxar para atualizar) | Cartão do grupo com a foto à esquerda; `Confirmar` azul na base; selo com dia, data e hora do J4; pino `Quadra J4`; vagas `N de 6 vão` e `M vagas abertas` (print) | — | ☐ |
| L4 | ORG | Aba Grupos | Etiqueta `Admin` no canto de cima da foto | — | ☐ |
| L5 | ATL | `Confirmar` → tocar fora da folha | A folha (foto, nome, local, selo, vagas, `Você vai nesse jogo?`, `Vou` e `Não vou`) fecha sem responder; `Confirmar` continua | — | ☐ |
| L6 | ATL | `Confirmar` → `Vou` | Folha fecha; etiqueta `Você vai` | ATL `CONFIRMED` no J4 | ☐ |
| L7 | terminal + ATL | `API atl PUT "/api/groups/$GRUPO/games/$(JID 'Quadra J4')/attendance" '{"requestId":"$RID","intent":"DECLINE"}'`; puxar a lista | Etiqueta `Não vai` | — | ☐ |

## N · Criar grupo sem valor por jogo (JOGO-008, decidido em 29/10)

O ATL vira dono de um grupo novo: rode por último.

| # | Aparelho | Faça | Espere | Banco | Resultado |
| --- | --- | --- | --- | --- | --- |
| N1 | ATL | Criar grupo (`Criar meu grupo` → `Entendi, quero experimentar` se aparecer) → nome `QA Novo <R>`, `Vôlei de quadra` → seguir até criar **sem** mexer no `Valor por jogo` | Grupo criado | `PSQL "select default_game_fee_cents is null, default_no_game_fee from access_groups where name='QA Novo $RODADA'"` = `t\|f` | ☐ |
| N2 | ATL | Página do grupo novo | Cartão `Defina o valor por jogo` | — | ☐ |
| N3 | ATL | `Caixa do grupo` → `Começar do zero` | Pergunta some | `opening_balance_cents` do grupo novo = `0` | ☐ |
| N4 | ATL | `Marcar jogo` (ou `Marcar primeiro jogo`): bloco `Valor por jogo` sem escolha → `Cobrar diária` `15` → salvar | Jogo criado; o cartão some | `default_game_fee_cents` do grupo novo = `1500` | ☐ |

## Rodadas

| Rodada | R | ORG | ATL |
| --- | --- | --- | --- |
| iOS | `29/10i` | iPhone 17 `BAB08A28-4E8C-4AA3-BF6A-54904566BF0F` | iPhone 17e `F22B2FF2-494E-4CF3-BD18-FE48ECE5A85B` |
| Android | `29/10a` | `Saqz_QA_A` (emulator-5554) | `Saqz_QA_B` (emulator-5556) |

- Link de presença no ATL: iOS `xcrun simctl openurl <udid> 'saqz://links.saqz.app/attendance/<código>'` (se não abrir, tentar
  `saqz://links.saqz.app/?saqz_attendance=<código>` e anotar qual abriu); Android
  `adb -s emulator-5556 shell am start -a android.intent.action.VIEW -d 'https://links.saqz.app/attendance/<código>' app.saqz`.
- Área de transferência: iOS `xcrun simctl pbpaste <udid>`; Android `adb -s <id> shell cmd clipboard get-primary-clip` (se o
  comando não existir, anote BLOQUEADO só nessa conferência).
- Prints: iOS `xcrun simctl io <udid> screenshot ~/.saqz-multidev/evidencias/2026-10-29-<R sem barra>-<passo>.png`; Android
  `adb -s <id> exec-out screencap -p > ~/.saqz-multidev/evidencias/2026-10-29-<R sem barra>-<passo>.png`.
