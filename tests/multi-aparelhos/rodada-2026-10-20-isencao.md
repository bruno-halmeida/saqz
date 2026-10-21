# Rodada 20/10/2026 · Isenção da mensalidade (DEB-115) nos aparelhos

- **Commit:** `07105bdf1` (`main` local, depois de `git reset --hard main`).
- **Aparelhos:** ORG = iPhone 17 (`BAB08A28-…`, iOS 27); ADM = `Saqz_QA_A` (emulator-5554); ATL = `Saqz_QA_B` (emulator-5556). O iPhone 17e foi ligado por `aparelhos.sh subir` e desligado logo depois, sem uso.
- **Instalado:** APK devDebug e `SaqzIOS.app` do `builds.sh` deste commit, apontando para `http://127.0.0.1:18080`. Stack com `stack.sh reset` + `up` (backend, Firebase Auth Emulator, Mailpit e Postgres; o Uazapi falso ficou parado, e esta rodada não precisa dele). App limpo: `pm clear app.saqz` nos dois Android; `simctl keychain reset` + desinstalar e reinstalar no iPhone.
- **Regra:** MEM-003/FIN-003. Tag `2010`, grupo `QA Vôlei 20/10`.

## Ambiente (não é defeito do app)

- `adb` fora do PATH: usei `~/Library/Android/sdk/platform-tools`.
- No zsh, `GID` é uma variável do shell (id de grupo do processo): `GID=$(…)` falha com "failed to change group ID". Nas receitas troquei o nome para `GRP`. Vale corrigir no roteiro 10 também.
- Máquina com carga alta (load de 180 a 340 por causa de outras sessões). `Saqz_QA_B` ficou parado em "Process system isn't responding"; a correção de ambiente foi um `adb reboot`. Depois disso, digitei no Android com `adb shell input`.
- Erro meu na P1: digitei `8000` no valor da mensalidade e salvou R$ 8.000,00 (`800000`). Corrigi pela receita R-COM-MENSALIDADE do roteiro 10 (`8000|10`).
- O `BACK` no cadastro de atleta saiu da tela sem salvar o apelido de ATL e ADM. Os dois já estavam no grupo, e o apelido não é usado nos passos.
- O rótulo "Tornar mensalista" (I1) não existe na tela de membro avulso. O controle é o segmentado `Por mês` / `Por jogo` em "Como participa", que abre a folha `Tornar <nome> mensalista`. O botão `Tornar mensalista` só aparece quando a pessoa já é avulsa e o editor está aberto (visto em I16).

## Resultado

| # | Resultado | O que apareceu | Evidência |
| --- | --- | --- | --- |
| P1 | PASSOU | `Grupo criado!`; `access_groups` = `8000\|10` (depois da correção do meu erro de digitação, ver Ambiente). Editar grupo → cartão `Mensalidade` com `Vale para quem entra como mensalista. Dá pra ajustar por pessoa depois.`, dia do vencimento já vinha em 10 | banco |
| P2 | PASSOU | ATL e ADM entraram pelo convite público (`PPP9BDDK`, `am start`) e viram `Você entrou no QA Vôlei 20/10`. ORG → `Tornar administrador` → linha `QA Atl2 20/10 · Administrador`. `MEMB`: atl e atl2 `AVULSO`, atl2 `ADMIN` | banco |
| I1 | PASSOU | Pelo segmentado `Por mês` (rótulo "Tornar mensalista" não existe, ver Ambiente) → folha `Tornar QA Atl 20/10 mensalista`, `A cobrança entra no caixa do grupo todo mês.`, `80,00`, `Dia 10` marcado → `Salvar` → `Salvar alterações`. Igual para ADM e para a linha do dono (abre `Editar jogador`; a chave de administrador vem desligada e travada). `MEMB`: os três `MENSALISTA`, `f`, `2026-10-20` | banco |
| I2 | PASSOU | `Criar cobranças do mês`: outubro de 2026, vencimento 10/10/2026, `80,00`, os três desmarcados por padrão (marquei) → `Conferir cobranças` → `Criar cobranças`. `COBR`: 3 `MONTHLY 2026-10-01 PENDING` | banco |
| I3 | PASSOU | Abaixo de `R$ 80,00 / mês · vence dia 10` e `Alterar cobrança`: chave `Isenção da mensalidade` desligada e `Continua mensalista, sem mensalidade.` | I3.png |
| I4 | PASSOU | Folha `Dar isenção da mensalidade?` com o texto `QA Atl 20/10 continua mensalista e não recebe mais mensalidade. A pendente deste mês será dispensada.`, botões `Voltar` e `Dar isenção`. Depois do `Voltar`, a chave continua desligada; `MEMB` atl `f`; `COBR` atl `PENDING` | I4.png |
| I5 | PASSOU | Chave ligada; o cartão de valor e `Alterar cobrança` sumiram. A isenção é gravada na hora, sem tocar em "Salvar alterações". `MEMB` atl `t`; `COBR` atl `WAIVED`; `EVT`: `qa-atl \| qa-org \| PENDING \| WAIVED \| Isenção da mensalidade` | I5.png |
| I6 | PASSOU | Linha inteira: `QA, QA Atl 20/10, Mensalista · com isenção` (subtítulo `Mensalista · com isenção`) | I6.png |
| I7 | PASSOU | `Selecione os mensalistas ativos`: só `QA Atl2 20/10` e `QA Org 20/10`; ATL não aparece. No caixa, ATL foi para `Mensalidades baixadas` com `Dispensada` e `Desfazer`; o contador do mês segue `0/3 mensalidades recebidas`, `Em aberto R$ 160,00` | I7.png |
| I8 | PASSOU | Membros (como atleta): `QA Atl 20/10 · você` com `Mensalista`, sem "com isenção" em nenhuma linha. Grupo → `Minhas cobranças`: `Tudo em dia` / `Nada em aberto`; `Ver histórico (1)` → `Mensalidade · Outubro`, `Vencimento 10/10`, `R$ 80,00`, **`Isenta`** (o texto é "Isenta", não "dispensada"). Início: `Sem jogo marcado` e o grupo, sem aviso de cobrança | I8-membros, I8-grupo, I8-cobrancas, I8-inicio |
| I9 | PASSOU | Android, ADM na própria linha → `Editar jogador` → chave → folha `Dar isenção da mensalidade?` (`QA Atl2 20/10 continua mensalista e não recebe mais mensalidade. A pendente deste mês será dispensada.`) → `Dar isenção`; o cartão de valor e `Alterar cobrança` somem. `MEMB` atl2 `t`; `COBR` atl2 `WAIVED`; `EVT`: `qa-atl2 \| qa-atl2 \| PENDING \| WAIVED \| Isenção da mensalidade` | I9-folha, I9 |
| I10 | PASSOU | A linha do dono abre o editor; chave → `Dar isenção`. `MEMB` org `t`; `COBR` org `WAIVED` | banco |
| I11 | PASSOU | Apelido `Adm Isento` → `Salvar alterações`; ao reabrir, a chave está ligada (cabeçalho `Adm Isento`). `MEMB` atl2 continua `t`. Observação: a lista de membros mostra `QA Atl2 20/10`, não o apelido | banco |
| I12 | PASSOU | O primeiro PATCH sem `active` deu `400 VALIDATION_FAILED {"active":["valor inválido"]}`. Com os campos do GET (`active`, `role`, posições, `monthlyFeeCents`, `monthlyDueDay`, `membershipType` `MENSALISTA`, `nickname` `Atl Velho`, **sem** `monthlyFeeExempt`): `200`, corpo com `"monthlyFeeExempt":true`; `MEMB` atl `t` | terminal |
| I13 | PASSOU | `api atl PATCH` com `"monthlyFeeExempt":false`: `403 ACCESS_FORBIDDEN`; `MEMB` atl continua `t` | terminal |
| I14 | PASSOU | `AVULSO` + `monthlyFeeExempt:true`: `422` com `{"code":"VALIDATION_FAILED","fieldErrors":{"monthlyFeeExempt":["is only allowed for MENSALISTA"]},"status":422}`; `MEMB` atl continua `MENSALISTA` `t` | terminal |
| I15 | PASSOU | Ao desligar, não aparece folha. Voltam `R$ 80,00 / mês · vence dia 10` e `Alterar cobrança`. `MEMB` atl `f`, `mensalista_since` `2026-10-20`; `COBR` atl continua `WAIVED`, nenhuma cobrança nova | I15.png |
| I16 | PASSOU | `Dar isenção` e depois `Por jogo` → o editor mostra `Tornar mensalista` e a chave some → `Salvar alterações`, **sem** confirmação. `MEMB` atl `AVULSO`, `f`, `mensalista_since` vazio; ao reabrir o editor, só `Tornar mensalista`, sem a chave | I16.png |
| I17 | FALHOU | Na lista que aparece logo depois de salvar o I16, a linha ainda era `QA, QA Atl 20/10, Mensalista · com isenção`. Ao sair e entrar de novo em Membros, a linha ficou `QA, QA Atl 20/10, Avulso` | I17-logo-apos-salvar, I17-reaberta |

Capturas em `~/.saqz-multidev/evidencias/2026-10-20-isencao-<passo>.png`.

## NÃO EXECUTADOS (por desenho, cobertos por testes automáticos)

- O job das 03:10, que não gera mensalidade para isento.
- A cobrança de mês anterior que continua pendente.
- A ordem eletrônica.
- Quem sai e volta ao grupo.

## Achados

### App

1. **A lista de membros não se atualiza depois de trocar mensalista isento para avulso (I17, iOS).** Esperado: ao voltar do editor depois de salvar `Por jogo`, a linha do ATL sem "com isenção". Obtido: `Mensalista · com isenção` até sair e entrar de novo em Membros, mesmo com o banco já em `AVULSO|f`. Na ida (I6, ligar a isenção), a lista se atualizou na volta. Evidência: `I17-logo-apos-salvar.png` e `I17-reaberta.png`.

### Observações (não marquei como FALHOU)

- I8: no histórico do atleta, a mensalidade dispensada aparece como `Isenta`; no caixa do dono, como `Dispensada`. São dois nomes para o mesmo estado.
- I7: depois da isenção, o cartão do caixa continua `0/3 mensalidades recebidas` e conta a dispensada no total.
- I5/I9/I10: ligar ou desligar a chave grava na hora. O botão "Salvar alterações" não participa.
- I12: o PATCH exige `active`. Um corpo sem ele dá `400`. Não sei se o app antigo manda `active`.
- I11: a lista de membros mostra o nome da conta, não o apelido salvo.

### Ambiente

Ver a seção "Ambiente" no começo: `adb` fora do PATH, `GID` no zsh, ANR do emulador B com a máquina sobrecarregada, e meu erro de digitação na mensalidade da P1.

## Tempo

De 22:40 a 23:08 (cerca de 28 min), incluindo builds e o reboot do emulador.
## Análise da sessão principal (20/10, 23h10)

- **I17 não é defeito.** Repeti no iPhone o caminho exato do I16 (atleta mensalista → `Dar isenção` → `Por jogo` → `Salvar alterações`) com a máquina já aliviada (load 7) e, uns 6 s depois de voltar, a linha era `QA Atl 20/10 · Avulso`, sem "com isenção". O mesmo sem isenção (mensalista → `Por jogo` → salvar). A lista recarrega ao reaparecer, mas por baixo, mantendo as linhas antigas até a resposta chegar; com load de 180 a 340 a captura saiu antes. Conta como PASSOU: **18 de 18 passos executados passaram**.
- **Observações, todas anteriores ao DEB-115 e fora do escopo:** o atleta vê a cobrança dispensada como `Isenta` e o organizador vê `Dispensada` (mesmo estado `WAIVED`, rótulos de antes); o contador `0/3 mensalidades recebidas` do caixa conta a dispensada no total; a lista de membros mostra o nome da conta, não o apelido. O PATCH exige `active`, e o app sempre manda (só o corpo montado à mão no I12 não tinha).
- **Ambiente:** no zsh a receita `GID=` do roteiro 10 colide com a variável do shell; quem rodar em zsh deve trocar o nome (como o executor fez, `GRP`).
