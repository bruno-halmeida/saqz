# Rodada 22/10/2026 · novas features (roteiro 12)

Roteiro: [roteiros/12-anteriores-faltou-e-lista.md](roteiros/12-anteriores-faltou-e-lista.md) (JOGO-011, PRES-016, GER-002,
NOT-008). Stack local construído do branch `bruno-halmeida/testes-novas-features` (= `main` 5b27377dc + commits só de testes),
bootJar refeito do zero antes da rodada; builds `devDebug` e `SaqzDev` novos (`builds.sh`). Sem veredito: o resultado de cada
passo é o que apareceu na tela e no banco. Capturas em `~/orca/workspaces/saqz/evidencias-rodada-22-10/`.

Desvios de operação (valem nas duas rodadas, salvo nota):

- Preparação do roteiro 01 (contas, grupo, membros) por API e SQL com `ferramentas/preparar-grupo.mjs`, não pela tela.
- Jogos de preparação marcados e publicados pela API com a conta do ORG (`ferramentas/marcar-jogo.mjs`); respostas de
  presença da preparação e de alguns passos (marcados "API") feitas pela API com a conta do atleta (`ferramentas/api.mjs`).
  O que está sob teste em cada passo (a tela do aparelho indicado ou a fila) foi feito/lido no aparelho ou no banco.
- Horários da coluna Banco em `FILA` aparecem em UTC (`18:25` = 15:25 em São Paulo).

## Rodada 1 · ORG no iPhone 17, ATL no Android (Saqz_QA_A)

ATL2 = iPhone 17e, ATL3 = Saqz_QA_B. Início 15:07, fim 16:06 (São Paulo).

| Passo | Resultado | O que apareceu na tela | Banco |
| --- | --- | --- | --- |
| P1 | PASSOU | — | MENSALISTA; 2500\|FIFO |
| P2 | PASSOU | Grupo sem o botão `Jogos anteriores` (seção "Como funciona", sem agenda) | 0 jogos |
| P3 | PASSOU (jogos marcados pela API, desvio) | Grupos: `23/10 · 20h00 · 0 de 6`; agenda do grupo: J2 `0 de 6 · Quadra J2`, J3 sáb 24/10, J4 dom 25/10 (J1 no destaque) | 4 PUBLISHED |
| P4 | PASSOU (respostas pela API, desvio) | — | 6 CONFIRMED; pendentes = 5 após P5 (4 no momento do P4: o roteiro conta antes do J3) |
| P5 | PASSOU (API) | — | Atl CONFIRMED, Atl2 DECLINED |
| P6 | PASSOU (cancelamento pela API, desvio) | Mural do grupo: `QA Org 22/10: O jogo de sábado 24/10 às 20h00 foi cancelado.` | CANCELLED\|t |
| P7 | PASSOU | — | J2 COMPLETED 2026-09-12, J3 CANCELLED 2026-10-17, J1 COMPLETED 2026-10-20, J4 PUBLISHED 2026-10-25 |
| P8 | PASSOU | — | CANCELLED\|t |
| J11-1 | PASSOU | Seção "Próximos jogos": o cartão da agenda tem só `Jogos anteriores` (o J4 aparece no destaque acima, não na lista); também `Acertar o jogo de 20/10 · 2 avulsos · R$ 50,00 a receber` em "Esperando você" | 3 |
| J11-2 | PASSOU | ATL (Android) no grupo: destaque `PRÓXIMO JOGO Domingo, 20h00 · Quadra J4`, sem seção de agenda e sem `Jogos anteriores` | — |
| J11-3 | PASSOU | — | `403 {"code":"ACCESS_FORBIDDEN"…}` |
| J11-4 | PASSOU | `Jogos anteriores`; `OUTUBRO DE 2026`: Terça 20/10 (J1), Sábado 17/10 (J3); `SETEMBRO DE 2026`: Sábado 12/09 (J2); sem `Quadra Rascunho` | — |
| J11-5 | FALHOU | Linha do J1: `Terça · 20h00` · `3 de 6 foram` (sem ` · Quadra J1`) · `Encerrado` · `Acerto pendente · 2 pessoas, R$ 50,00` | `2\|5000` |
| J11-6 | PASSOU | `Sábado · 20h00` · `Cancelado com 1 confirmado` · selo `Cancelado` (vermelho); linha com data e título em cinza (apagada) | — |
| J11-7 | PASSOU | `JOGO CANCELADO`; abas `Confirmados` (1, QA Atl) e `Não iam` (1, QA Atl2); sem `Vão`/`Não vão` | — |
| J11-8 | PASSOU | Lista igual ao #4 (dois meses, três linhas) | — |
| J11-9 | PASSOU | `JOGO REALIZADO`; `Encerrar jogo e acertar`; abas `Jogaram` (3/6) · `Não foram` (0) | — |
| J11-10 | PASSOU | Acerto do J2: `3 pessoas jogaram`, `0 de 2 avulsos acertaram · faltam R$ 50,00` → `Recebi` ×2 (folha `Confirmar recebimento`, `Diarista · jogo de 23/10`*) → jogo com `Ver acerto` | `0` |
| J11-11 | PASSOU | Linha do J2: `3 de 6 foram` · `Encerrado`, sem acerto pendente; J1 mantém `Acerto pendente · 2 pessoas, R$ 50,00` | — |
| J11-12 | PASSOU | Sem próximo jogo: destaque `Sem jogo marcado`; seção `Próximos jogos` só com `Jogos anteriores` | — |
| J11-13 | PASSOU | J4 volta ao destaque `Domingo, 20h00 · 25 de outubro · Quadra J4` | PUBLISHED |
| J11-14 | PASSOU | Lista rolada até `JULHO DE 2026` · Sexta 31/07 (24.º e último); abrir esse jogo e voltar mantém a lista no fim. `Carregando mais jogos` não chegou a aparecer na listagem (3 GET `/games/past` em 0,6 s no log) | 21 |
| P16-1 | PASSOU | ATL (Android, Grupo → Galera → Membros → si mesmo → Ver perfil): `Jogos: 2` · `Presença: 100%` · `Faltas: 0`. ORG (Membros → ATL → Ver perfil): `Jogos: 2` · `Presença: 100%` · `Faltas: 0`. O Perfil do ATL (aba) mostra só `2 Jogos · 1 Grupos` | `2` |
| P16-2 | PASSOU | Folha com título `QA Atl 22/10` (o botão ⋮ tem o rótulo `Ações de QA Atl 22/10`), `Avulso · confirmou para terça, 20 de outubro`, `Marcar falta` · `Confirmou e não apareceu`, `Só você e os admins veem a falta, e a pessoa não recebe aviso. A diária de R$ 25,00 continua; no acerto você decide se cobra.` | — |
| P16-3 | PASSOU | Abas `Jogaram` 2/6 · `Faltaram` 1 · `Não foram` 0; ATL fora de Jogaram | `t\|t`, status `CONFIRMED` |
| P16-4 | PASSOU | Aba `Faltaram`: QA Atl com `Faltou`; rodapé `Só você e os admins veem quem faltou. Dá para marcar até 27 de outubro.` | — |
| P16-5 | PASSOU | Central do ATL: só `O jogo de sábado 24/10 às 20h00 foi cancelado.` (15:09) e o aviso de jogo liberado do J1 (15:08); nada sobre falta | `0` |
| P16-6 | PASSOU | ATL abriu o J1 por `Abrir` no aviso de jogo liberado: `JOGO REALIZADO`, abas `Jogaram` 3/6 · `Não foram` 0, ele na linha 1 com `Você`; sem `Faltaram`, sem `Faltou`, sem ⋮ | — |
| P16-7 | PASSOU | ATL2 (iPhone 17e, mesmo caminho): `Jogaram` 3/6 com QA Atl, QA Atl2 (`Você`), QA Atl3; sem `Faltaram` | elenco do ATL2 pela API: só `"noShow":false` |
| P16-8 | PASSOU | — | `403 ACCESS_FORBIDDEN`; marca do Atl3 vazia |
| P16-9 | PASSOU | Acerto do J1: `2 pessoas jogaram`; `0 de 2 avulsos acertaram · faltam R$ 50,00`; QA Atl com `Recebi`, `Dispensar`, `Faltou`, `Cancelar cobrança`; QA Atl2 com `Recebi` e `Cancelar cobrança` | API do gestor: cobrança do ATL `PENDING`, `"noShow":true` |
| P16-10 | PASSOU | Folha `Dispensar cobrança` com `Quem faltou não paga esta diária. A pessoa não recebe aviso. Dá para desfazer depois.` → `Dispensar`: QA Atl `Dispensado` + `Desfazer` (a etiqueta `Faltou` some da linha); progresso `0 de 2 avulsos acertaram · faltam R$ 25,00` | `WAIVED` |
| P16-11 | PASSOU | Grupo do ATL → `Minhas cobranças`: `Tudo em dia · Nada em aberto`; histórico: `Jogo avulso R$ 25,00 Cancelada` (J3), `Paga` (J2), `Isenta` (J1); nenhuma menção a falta | — |
| P16-12 | PASSOU | ORG → Membros → ATL → Ver perfil: `Jogos: 1` · `Presença: 50%` · `Faltas: 0 avisadas · 1 sem avisar` | `1` |
| P16-13 | FALHOU | ATL (Android) → Membros → si mesmo → Ver perfil: `Jogos: 1` · `Presença: 50%` · `Faltas: 0` (esperado `Faltas: 1`, sem a separação). Captura `r1-pres016-13-atl-perfil-faltas-0.png` | `member_played_games` = `1` |
| P16-14 | PASSOU | ⋮ do ATL em Faltaram → folha com `Desfazer` · `Volta para Jogaram` → aba `Faltaram` some; `Jogaram` 3/6 com ATL na linha 1 | marca vazia; cobrança segue `WAIVED` |
| P16-15 | PASSOU | Acerto: `3 pessoas jogaram`; ATL `Dispensado` + `Desfazer`, sem `Faltou`; `0 de 2 avulsos acertaram · faltam R$ 25,00` | — |
| P16-16 | PASSOU | Folha do ATL3: `Mensalista · confirmou para terça, 20 de outubro`, `Só você e os admins veem a falta, e a pessoa não recebe aviso.` (sem a frase da diária) → ATL3 em `Faltaram` com `Faltou` | `0` cobranças do ATL3 no J1 |
| P16-17 | PASSOU | J2 (12/09): aba `Jogaram` sem ⋮ em nenhuma linha e sem rodapé de falta (listagem do passo J11-10) | — |
| P16-18 | PASSOU | — | `409 ATTENDANCE_FROZEN`; marca do ATL no J2 vazia |
| P16-19 | PASSOU | J1 levado a 14/10: aba `Faltaram` com ATL3 `Faltou`, sem ⋮; rodapé `Só você e os admins veem quem faltou.`; lista mostra `Quarta 14/10 · 2 de 6 foram · Acerto pendente · 1 pessoa, R$ 25,00` | `t` |
| G2-1 | PASSOU | Início do ORG: J4 com `0 confirmados` (N = 0) | — |
| G2-2 | PASSOU | ATL tocou `Vou` no Android; ORG puxou a Início: `1 confirmado`, `3 pendentes`. A bola não chegou a ser capturada na listagem (gesto e recarga terminam antes) | `1` |
| G2-3 | PASSOU (resposta do ATL2 pela API) | Lista de grupos: `25/10 · 20h00 · 1 de 6` → `2 de 6` após puxar | — |
| G2-4 | PASSOU (API) | Detalhe do grupo após puxar: `1 confirmado` · `1 não vai` (era `2 confirmados` · `0 não vão`) | — |
| G2-5 | PASSOU (API) | Tela do J4 após puxar: `Vão` 2/6 com QA Atl3 (`Mensalista`) na linha 2; `1 pessoa sem resposta` | — |
| G2-6 | PASSOU (API) | Caixa antes: diária do ATL2 no J4 com `Desistiu` e `Dispensar`; ATL2 `Vou` de novo; após puxar, a mesma cobrança aparece sem `Desistiu` (só `Cobrar`, `Recebi`, `Cancelar cobrança`). Não surge cobrança nova: é a mesma linha | `2` |
| G2-7 | PASSOU | Após `PASSADO 'Quadra J4' 4; ENCERRA`, puxar a lista: topo `Quarta · 20h00` 21/10 `3 de 6 foram` · `Encerrado` · `Acerto pendente · 2 pessoas, R$ 50,00`; resto igual | `COMPLETED\|2026-10-21` |
| G2-8 | PASSOU | ATL parado na aba Perfil (`3 Jogos`); ORG marcou falta do ATL no J4; ATL puxou: `2 Jogos`. A aba Perfil não mostra presença (%) | `3` → `2` |
| G2-9 | BLOQUEADO | ORG no simulador iOS: não há como tirar a rede só do simulador | — |
| G2-10 | FALHOU | Dois puxões seguidos na Início (gesto de ~1 s cada, em sequência) | log: `/api/me/home` às 15:19:57 e de novo às 15:19:58 (duas leituras); não dá para afirmar se a primeira já tinha terminado |
| N8-1 | PASSOU | Colunas de `group_whatsapp_bindings` conferidas com `\d` (iguais às do roteiro); INSERT ok | `t\|t`; antes: `game_system` vazio, modo `SCHEDULED` |
| N8-2 | PASSOU | Editar grupo (fim da tela): `Lista no grupo do WhatsApp`, `Por horário` (marcado) e `A cada confirmação`, apoio `Envia a lista às 9h, 12h30 e 18h.` | `SCHEDULED` |
| N8-3 | PASSOU | ORG → Membros → QA Atl2 → `Tornar administrador` (sem confirmação): `Administradores · 2`. ATL2 (iPhone 17e) → grupo → Editar: a tela termina em `Pix do grupo`, sem `Lista no grupo do WhatsApp` | `ADMIN` |
| N8-4 | PASSOU | ORG: `6-0` em `Sistema de jogo` + `A cada confirmação` → apoio `Envia quando a lista muda, das 8h às 22h.` → `Salvar alterações`; reabrir mantém as duas escolhas | `ON_CHANGE`. Obs.: o editor gravou `play_style=SIX_ZERO` e deixou `game_system` vazio; `game_system='SIX_ZERO'` aplicado por SQL às 15:21:55 (preparação do J5, como a revisão permite) |
| N8-5 | PASSOU | J5 marcado pela API às 15:22 (precondição). Passada das 15:25 publicou | fila: `15:25:00 REMINDER ON_CHANGE PENDING`; log `attendance_updates published=1` |
| N8-6 | PASSOU | ATL `Vou` no J5 às 15:26:07 (API). Passadas de 15:30 e 15:35: nada novo | `published=0`; fila sem linha nova |
| N8-7 | PASSOU | Passada das 15:40:00,05 não publicou (o lembrete anterior foi gravado às 15:25:00,989; faltavam ~0,9 s para os 15 min). Saiu na passada das 15:45 | linha `15:45:00 REMINDER ON_CHANGE PENDING`; `count(*) ON_CHANGE` = `2` |
| N8-8 | BLOQUEADO | Antes de uma passada inteira sem mexer, o J5 foi cancelado (passo N8-13, para o J6 virar o próximo jogo); a passada das 15:50 caiu no freio de qualquer forma | — |
| N8-9 | PASSOU | — | corpo: `Jogo: sábado, 24/10 às 20:00` / `Local: Quadra J5` / `Confirmações até sáb 18h00` / `Vagas: 1/6` / `✅ Confirmados:` / `1. QA Atl 22/10` / `2.` … `6.` |
| N8-10 | PASSOU | J5 → `Avisar` → `Lembrete enviado no Saqz para 2 pessoas.` | `15:45:43 REMINDER MANUAL PENDING` |
| N8-11 | PASSOU | Editar → `Por horário` → salvar; J5 → `Avisar` | modo `SCHEDULED`; `15:46:07 REMINDER MANUAL PENDING` |
| N8-12 | PASSOU | INSERT com `author_name` (coluna obrigatória além das do roteiro) | mensagem gravada (`1`); na fila: `0` |
| N8-13 | PASSOU | Editor: `5-1` + `A cada confirmação` → salvar. J6 marcado pela API (+4 dias 20:00). ATL `Vou` como `LEVANTADOR`, ATL2 como `PONTA` (API). J5 cancelado pela API às 15:47 para o J6 ser o próximo jogo (o lembrete só pega o próximo). Publicou na passada das 16:05 | `FIVE_ONE\|FIVE_ONE\|ON_CHANGE`; `game_position_slots` do J6 = `4`; corpo: `Vagas: 2/12` / `Levantadores(as): 1. QA Atl 22/10 2.` / `Ponteiros(as): 1. QA Atl2 22/10 2. 3. 4.` / `Centrais: 1. 2. 3. 4.` / `Opostos(as): 1. 2.`. Obs.: o J6 foi marcado pedindo 6 vagas e ficou com `games.capacity` = `12` (as vagas por posição do 5-1) |
| N8-14 | BLOQUEADO | Rodada 1 terminou às 16:06, antes das 22h | — |
