# Rodada 21/10/2026 · Isenção da mensalidade (DEB-115), segunda passada nos aparelhos

- **Commit:** `062b23ace` (`main` local). APK devDebug e `SaqzIOS.app` gerados deste commit; backend com bootJar novo deste commit (ver Ambiente).
- **Aparelhos:** ADM = `Saqz_QA_A` (emulator-5554, organizador no Android); ATL = `Saqz_QA_B` (emulator-5556); ORG = iPhone 17 (`BAB08A28-…`). Ontem o organizador foi o iPhone; hoje a gestão no Android ficou com o ADM.
- **Dados:** os da rodada de 20/10 (tag `2010`, grupo `QA Vôlei 20/10`), sem `reset`. Regra: MEM-003/FIN-003. Cenários: `tests/roteiros/membros-e-papeis.md`, bloco da isenção.
- **Capturas:** `~/.saqz-multidev/evidencias/2026-10-21-isencao/` (`01`–`07` no iPhone, `A-*` e `B-*` nos Android).

## Ambiente (não é defeito do app)

- **O stack subiu com um backend velho.** `stack.sh up` reaproveita qualquer bootJar que exista em `backend/bootstrap/build/libs`; o que estava lá era de 14/10, anterior à isenção (a rodada de ontem usou o jar do worktree da filha). O roster vinha sem `monthlyFeeExempt` e a lista mostrava só `Mensalista`. Gerei o bootJar da `main` e reiniciei o backend; dali em diante tudo bateu. Antes de uma rodada, rodar `:bootstrap:bootJar` ou apagar o jar.
- Emulador iniciado com `nohup … &` de dentro de um comando do terminal do agente morre quando o comando termina. Subi cada emulador como tarefa de fundo própria.
- Para liberar outubro do ATL, que já tinha a mensalidade de outubro dispensada ontem, movi essa linha para agosto (`billing_month='2026-08-01'`). Por isso o histórico do atleta mostra `Mensalidade · Agosto · Isenta`.
- Para ver a diária do avulso, adiantei o prazo do jogo pago (`confirmation_deadline=now()-1min`) e esperei o job da reserva promover.

## Resultado

| Cenário | Resultado | iOS | Android | O que apareceu | Evidência |
| --- | --- | --- | --- | --- | --- |
| Isenta um mensalista (chave, "Dar isenção") | PASSOU | ☑ | ☑ | Android: ADM isenta o ATL; `MENSALISTA\|t`. iPhone: ORG desliga e religa a isenção do ADM. Em `Criar cobranças do mês` só aparece quem não é isento. Prioridade: num jogo antes do prazo, o ORG (mensalista isento) entrou `CONFIRMED` e o ADM (avulso) foi para `WAITLISTED` | `A-criar-cobrancas-outubro`, `04`, `05`, banco |
| Liga a chave e toca "Voltar" | PASSOU | — | ☑ | Folha `Dar isenção da mensalidade?`; depois de `Voltar`, chave desligada, `MENSALISTA\|f`, setembro e outubro `PENDING` | `A-S2-folha`, `A-S2-depois-voltar` |
| Isenta com pendente do mês atual | PASSOU | — | ☑ | Outubro `PENDING → WAIVED`, nota `Isenção da mensalidade`, autor ADM. O atleta vê `Mensalidade · Outubro · Isenta` | banco, `B-historico` |
| Isenta com pendente de mês anterior | PASSOU | — | ☑ | Setembro continua `PENDING`. O atleta vê `MENSALIDADE · SETEMBRO · Venceu em 10/09 · Em aberto` | banco, `B-grupo` |
| Admin isenta a si mesmo | PASSOU | — | ☑ | ADM na própria linha: desligar grava `f` sem folha; religar abre a folha, `Dar isenção` grava `t` | `A-S5-*` |
| Atleta tenta se isentar | PASSOU | — | ☑ | Na lista o atleta só tem `Ver perfil`. API: `PATCH …/athletes/<atl>` com `monthlyFeeExempt` `true` ou `false` dá `403 ACCESS_FORBIDDEN`; `PATCH …/athletes/me` com o campo dá `200` e ignora a isenção | `B-S6-linha-propria`, terminal |
| Isento na lista (dono ou admin) | PASSOU | ☑ | ☑ | `Mensalista · com isenção` para os isentos; no editor o cartão `R$ 80,00 / mês` e `Alterar cobrança` somem. Na lista do atleta, só `Mensalista` | `01`, `02`, `A-membros`, `A-S7-lista`, `B-S7-membros-visao-atleta` |
| Isento vira avulso | PASSOU | ☑ | ☑ | `Por jogo` → `Salvar alterações`: `AVULSO\|f`, `mensalista_since` vazio; a lista atualizou na volta nas duas plataformas. Diária: o ADM, já avulso, promovido da reserva recebeu `GAME 2000 PENDING`; ATL e ORG, mensalistas, não receberam | `06`, `07`, `A-S8-*`, banco |
| Avulso marcado como isento | PASSOU | ☑ | ☑ | Sem a chave no editor de avulso (só `Tornar mensalista`). API: `AVULSO` + `monthlyFeeExempt:true` dá `422` com `is only allowed for MENSALISTA` | `A-editor-atl-avulso`, `06`, terminal |
| App antigo (sem o campo) edita o isento | PASSOU | — | — | `PATCH` sem `monthlyFeeExempt`: `200`, corpo com `monthlyFeeExempt:true`, banco segue `t` | terminal |
| Isento sai do grupo e volta | PASSOU | — | ☑ | ATL isento → `Sair do grupo`; o link público virou pedido de entrada; ADM aceitou `Mensalista`: volta `MENSALISTA\|f`, sem "com isenção" | `B-S11-*`, `A-S11-*` |

Os "—" de hoje foram cobertos ontem no outro sistema (I4, I5, I10 no iPhone; I9 no Android), exceto "admin isenta a si mesmo" no iPhone (ontem foi o dono a si mesmo no iPhone). O job das 03:10 não rodou nesta passada; a consulta dele (`dueUpTo`) tem `AND NOT memberships.monthly_fee_exempt`, e há teste automático.

## Achados

Nenhum defeito da isenção.

### Observações (não marquei como FALHOU)

- A folha da própria linha fala em terceira pessoa: `Adm Isento continua mensalista e não recebe mais mensalidade.` (`A-S5-folha-propria`).
- Já vistas ontem e anteriores à isenção: o atleta vê `Isenta` e o caixa do organizador `Dispensada`; o cartão `0/3 mensalidades recebidas` conta as dispensadas.
- Fora do escopo: no caixa, o subtítulo dizia `outubro de 2026 · 2 mensalistas` com três mensalistas no grupo (passou a 3 quando o ATL ganhou a cobrança de outubro; parece contar cobranças do mês); na folha `Sair do grupo?` do Android há uma faixa vazia entre o texto e os botões; em `Quem ainda não pagou`, os botões `Cobrar`, `Recebi` e `Cancelar cobrança` ficam empilhados e apertados.

## Tempo

De 01:00 a 01:21, incluindo os builds, o bootJar e a subida dos emuladores.
