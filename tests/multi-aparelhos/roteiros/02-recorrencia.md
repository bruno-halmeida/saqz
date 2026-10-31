# Roteiro 02 · Horário fixo pelos dois caminhos (editor e "Jogos e horários")

Pré-requisito: roteiro 01 já rodado (grupo `QA Vôlei <data>` com ORG em `Saqz_QA_A` e ATL2 em
`Saqz_QA_B`, jogo avulso de sábado já marcado). Regras em vigor desde 09/10/2026: o próximo jogo de
cada horário fixo sai **publicado na hora** em que o horário é criado, pelos dois caminhos, e o jogo
avulso publicado no horário do slot é **adotado pela série** (mesmo id, título do grupo, `series_id`).
`<jogo-id>` abaixo é o id da linha correspondente em `games`.

| # | Aparelho | Faça | Espere (texto exato na listagem) | Banco |
| --- | --- | --- | --- | --- |
| 1 | ORG | Voltar até a Início; "Marcar jogo"; Data: a próxima quarta-feira; Horário `19:00`; quadra/endereço se o editor pedir (`Quadra QA`, `Rua QA, 1`); ligar "Repetir toda semana" | Antes de salvar: `Quarta · 19h00 · novo` e `Os próximos jogos entram na agenda sozinhos: cada um é liberado 4 dias antes.` | — |
| 2 | ORG | "Marcar jogo" (salvar) | Volta ao grupo ou à Início; em "Próximos jogos" aparece a quarta às `19h00` | `select title,status::text,series_id is not null,slot_key is not null from games where local_time='19:00' order by local_date limit 1` = `QA Vôlei <data>\|PUBLISHED\|t\|t` (título do grupo, não "Jogo extra"); `select local_date,status::text from games where series_id is not null and local_time='19:00' order by local_date` = primeira quarta `PUBLISHED`, as seguintes `DRAFT` |
| 3 | ORG | Abrir o jogo da quarta (toque no cartão em "Próximos jogos") | Tela "Jogo" com `Jogo criado`, `Qua, 14/10/2026 · 19:00`, `Quadra QA`, `12 vagas` (a tela do jogo não mostra o título; ele aparece em "Jogos e horários" e na Início) | — |
| 4 | ORG | Voltar; Grupos → `QA Vôlei <data>` → rolar até Gestão → "Jogos e horários" | Linha da quarta: `19h00 · QA Vôlei <data>` | — |
| 5 | ORG | "Adicionar dia e horário" → na folha "Dias e horário" marcar `Qui`, hora `20h` no contador e minutos `00` (resumo `Toda quinta, às 20h00.`) → "Salvar" → salvar a tela | A lista passa a ter a quinta: `20h00 · QA Vôlei <data>`, já na próxima quinta (não some até 4 dias antes) | `select local_date,status::text from games where local_time='20:00' and series_id is not null order by local_date` = primeira quinta `PUBLISHED`, seguintes `DRAFT` |
| 6 | ORG | Voltar até a Início (trocar de aba se precisar) | "Próximos jogos" lista a quarta e a quinta (além do sábado avulso, se ainda for futuro) | — |
| 7 | ATL2 | Trocar de aba Grupos → Início, sem reabrir o app | "Próximos jogos" lista a quarta e a quinta com `Sem resposta` | `select local_date,status::text from games where series_id is not null order by starts_at` = só o primeiro jogo de cada horário `PUBLISHED`, os demais `DRAFT` (o aviso "Jogo liberado" só sai para o próximo jogo do grupo e com `saqz.notifications.reminder.enabled`; não conferir aqui) |
| 8 | ORG | Abrir o jogo da quinta a partir de "Próximos jogos" | `Qui, 15/10/2026 · 20:00`, `Confirmações até 14:00` (6h antes), placar `Vou` / `Não vou` / `Sem resposta` e botões `Ajustar vagas`, `Editar jogo`, `Cancelar jogo` (o dono responde pelo cartão da Início/grupo, não aqui) | — |

Relate também o tempo total e qualquer passo em que precisou usar adb/simctl no lugar do mobile-mcp.
