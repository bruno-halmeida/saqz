# Verificação independente final — carteira e gestão financeira

Data: 2026-09-13. Verificador: worker TLC independente `task_1d70f06b8a69` (autor ≠ verificador).

## Veredito

**FAIL — a falha funcional encontrada foi corrigida, mas resta um mutante sobrevivente.** O snapshot
final compila e os gates focais passam. A revogação durante a leitura de saldo inicialmente chegava ao
POST de saque; o coordenador integrou duas barreiras de autorização, os novos testes passam e remover a
primeira barreira faz o teste falhar. Resta reforçar a cobertura de recent-auth no cadastro de destino.

## Snapshot exato

- Base do `git archive`: `cd0a4a3c19224a5b254a7ff860f6a4f00a60d2d3`.
- Scratch isolado: `/tmp/saqz-wallet-management-verifier-v2.2Bnr4i`.
- 79 arquivos alterados/não rastreados foram sobrepostos. Três dependências coordenadas backend e as
  dependências móveis de recorrência/Pix/MemberPayment entraram somente para compilação; não foram
  revisadas funcionalmente.
- SHA-256 da lista ordenada: `3459590efee3b6092f7e09bbd73da97ec9e3cf018d7e09fb782717647e3751dc`.
- SHA-256 do manifesto dos 79 hashes individuais:
  `7599ac8c6884bdfe46fea1781811959d6c715236a4ac3607957f24e72078ef92`.
- Manifestos completos: `/tmp/saqz-wallet-management-verifier-v2.2Bnr4i/SNAPSHOT_FILES.txt` e
  `/tmp/saqz-wallet-management-verifier-v2.2Bnr4i/SNAPSHOT_SHA256.txt`.

Hashes principais:

| Arquivo | SHA-256 |
|---|---|
| `backend/.../application/Wallets.kt` | `5a235dd96c71a973ff71a8b4ad51f83f1837009ca6134ce38c51f80df35c9025` |
| `backend/.../application/FinancialManagement.kt` | `0b899710d4f61cde0942006b4fad4d8a37df5a7e19d11eea022f56e9d3839610` |
| `backend/.../jdbc/JdbcFinancialManagement.kt` | `b580ddeb6324434962dac20bc3a19dec17b6dbd176abaf97bae3cbd0b8e2fc98` |
| `mobile/.../ReceiptFinanceHome.kt` | `3964f3b4420134e34028f4b03be21f3093085ffee1ea1f8b026f577a77880cdd` |
| `mobile/.../ReceiptWalletViewModel.kt` | `41915c4e31577e05b4765175376bb7b71839d67a6b010f01e0a3139f94513251` |
| `mobile/.../FinancialManagementViewModel.kt` | `a191b1ccf6286aa83a0fbcc7825e46566246e728e2ba960f2d1281b84e720a63` |
| `mobile/.../ReceivablesModule.kt` | `8aeaf0ba349ae3f2888608ee23db62d2405e76e503d53405064746d517b5a5de` |
| `mobile/.../SaqzNavHost.kt` | `1dddf04fb980703021d01a28b885441776f851267a853797bcf21b1b03ea8392` |

## Falha encontrada, corrigida e reprovada por mutação

### F1 — resolvida — revogação durante saldo→saque

Na primeira fotografia, `ManageWallet.withdraw` autorizava, consultava saldo remoto e seguia para
reserva/POST sem nova autorização. O teste independente removeu o delegado no callback de
`provider.balance`: esperava `NOT_FOUND`/zero POST, mas recebeu `Success` e um POST.

Evidência: `/tmp/fwm-v2-wallet-revocation-failure.log` (SHA-256
`cf83742f3c70ddec7ffeb19d02d4980adede3abfb2b2151693ca6f823703835c`). A falha é do
candidato anterior, não de uma mutação. No snapshot final, `Wallets.kt:155-158` reautoriza depois do
saldo, e `:184-200` reautoriza depois do claim imediatamente antes do IO. Os dois testes finais passam
com zero POST; remover a primeira barreira é morto pelo teste `revocation during balance read...`.

## Lacuna de cobertura comprovada

### G1 — média — recent-auth de destino bancário não é discriminada na camada de aplicação

O sensor substituiu por `true` o argumento `recentlyAuthenticated` de
`FinancialAction.CHANGE_BANK_DESTINATION` em `Wallets.kt:131-133` e executou a suíte `ManageWalletTest`.
Todos os testes continuaram verdes. O código atual contém a verificação, e o controller possui provas
de Bearer antigo, mas nenhum teste de aplicação garante que `saveDestination(..., false, ...)` retorna
`RECENT_AUTHENTICATION_REQUIRED`, não persiste destino e não faz IO. Por TLC, mutante sobrevivente é
gap e impede PASS.

## Pontos rechecados e aprovados

- O import de `Preview` do home financeiro está corrigido no snapshot atual; iOS e composition root
  compilam sem correção local.
- A correção comercial persiste snapshot cifrado de identidade antes do POST, compara os seis campos
  legais e os dez corrigíveis no retorno e no recovery. O teste de identidade legal passou e matou a
  mutação que removia `companyName`/`taxRegime` da comparação.
- Android e iOS verificam o mesmo UID nativo antes/depois da reautenticação e renovam o token. A bridge
  consome essa porta confiável; não foi mantido finding baseado em fake que violava o contrato nativo.
- `WalletAttempt` não persiste senha, formulário bancário ou aceite; `accepted` restaura `false` e
  recovery por requestId não repete POST. `amountCents`/`destinationId` discriminam resposta divergente.
- O contrato de gestão está alinhado com o marker opcional `targetId`/`termsVersion`; não persiste PII,
  dinheiro ou aceite, e os testes provam a retomada do mesmo comando.
- Nenhuma superfície nova contém rota, intent ou controller de pedido, aprovação ou execução de
  reembolso. Estados externos `REFUNDED`/chargeback preexistentes são reconciliação passiva.

## Gates e contagens

| Gate | Resultado |
|---|---|
| Backend focal: wallet + gestão unit/HTTP/provider/JDBC | PASS: 27 unitários + 6 JDBC = 33, 0 falhas/skips |
| Bootstrap compile + arquitetura | PASS: compile e 20 testes de arquitetura, 0 falhas/skips |
| Mobile iOS data | PASS: 61 testes, 0 falhas/skips |
| Mobile iOS apresentação | PASS: 108 testes, 0 falhas/skips |
| Mobile Android data/apresentação | PASS: testes focais e compile, 0 falhas/skips |
| Composition root iOS | PASS: `:compose-app:compileKotlinIosSimulatorArm64` |
| Composition root Android | PASS: `:compose-app:compileDebugKotlinAndroid` |

Logs dos gates:

- `/tmp/fwm-v3-backend-final.log` — `b61eb1c8087b200570a58331c3a1e5e902097f082601c0b481660e657a217409`
- `/tmp/fwm-v2-mobile.log` — `72ffdc3cbcda4d5f183041e5a35ddb97e5876dc98a92d76a4589da73515efdbb`
- `/tmp/fwm-v2-mobile-android.log` — `e7255fa0e7d9f6afce6917c9029b1f3b25e80774a962fab6baba9be4164c3667`
- `/tmp/fwm-v2-compose-final.log` — `70baf51038c9cec4659257731f57644cbd9f420dddb4e3bc134125bcb2314064`
- `/tmp/fwm-v3-wallet-fixed.log` — `a81df7c59e6be6793168f78fdad69c12d776abeb4a4442fdc4de118573bf3894`

Os testes móveis incluem dependências coordenadas porque os módulos são monolíticos; as afirmações
específicas deste escopo incluem os valores literais `R$ 123,45`, `R$ 67,89`, `R$ 2.500,01`,
`12345`, `6789`, `250001` e taxa `173`, sem `Double`.

## Sensor de discriminação no snapshot atual

Todas as mutações ocorreram somente no scratch e foram restauradas. Seis foram mortas e uma
sobreviveu:

| # | Falha injetada | Resultado / prova |
|---|---|---|
| 1 | consentimento explícito de saque ignorado | **KILLED** — exige `INVALID_INPUT` e zero POST |
| 2 | recent-auth do cadastro de destino forçado para verdadeiro | **SURVIVED** — G1 |
| 3 | recent-auth do saque forçado para verdadeiro | **KILLED** — exige `RECENT_AUTHENTICATION_REQUIRED` e zero POST |
| 4 | recovery do saque troca GET por novo POST | **KILLED** — exige um POST total e uma consulta de recovery |
| 5 | comparação de `companyName`/`taxRegime` removida | **KILLED** — recovery permanece `RESULT_PENDING` |
| 6 | recebíveis pendentes substituídos pelo saldo disponível | **KILLED** — exige `12345` e `6789` distintos |
| 7 | reautorização pós-saldo removida | **KILLED** — exige `NOT_FOUND`, zero reserva e zero POST |

Logs: `/tmp/fwm-v2-mut-consent.log`, `/tmp/fwm-v2-mut-recent-auth.log`,
`/tmp/fwm-v2-mut-withdraw-recent-auth.log`, `/tmp/fwm-v2-mut-recovery.log`,
`/tmp/fwm-v2-mut-identity.log`, `/tmp/fwm-v2-mut-balance.log` e
`/tmp/fwm-v3-mut-revocation-recheck.log` (SHA-256 da prova final:
`42ce19766f531a6116d952af64a830f1c9d73d9d0932dd2d9550b4a22aa3c789`). Sensor:
**6/7 killed, 1 survived — FAIL**.

## Rastreabilidade resumida

| Critério | Veredito |
|---|---|
| AC-W1/W2/W3 | PASS: endpoints separados, cursor isolado, cifra/idempotência e centavos exatos |
| AC-W4 | PASS após fix: revogação pós-saldo/pós-claim bloqueia IO; plano expirado não bloqueia dinheiro |
| AC-W5 | GAP de teste em recent-auth do destino; saque e consentimento são discriminados |
| AC-W6/W7 | PASS: lock/reserva, timeout, recovery GET, requestId e ausência de POST duplicado |
| AC-W8 / AC-M10 | PASS: nenhuma ação/API/UI de reembolso no escopo novo |
| AC-M01..M04 | PASS: autorização atual, owner/delegate/candidates e mutações owner-only |
| AC-M05 | PASS: Android/iOS, BRL literal, sessão/generation e recovery explícito; precisão documental menor anotada |
| MGT-06/MGT-07 | PASS: timeout/replay conserva um POST/requestId e conflito monetário é literal |
| MGT-08/MGT-09 | PASS comportamental: callback tardio/ator/sessão e persistência sem PII editável |

## Wiring e limites

- DI, navegação e serialização das rotas de gestão/wallet estão conectadas; o compile iOS do
  composition root passou.
- Não foi executado Gradle no workspace compartilhado, não houve mutação Git e nenhuma fonte de
  produção foi editada por este verificador.
- Nenhuma chamada real ao Asaas foi feita. HTTP foi simulado localmente; homologação, credenciais,
  condições comerciais, análise cadastral e piloto continuam externos.
- Sem screenshots/UAT: não eram necessários para autorização, recovery e persistência; os testes de
  UI compartilhados executaram no simulador iOS.
- Recorrência/Pix/MemberPayment foram apenas dependências de compilação e estão fora deste veredito.

## Fixes requeridos

1. Adicionar teste de aplicação para `saveDestination` sem recent-auth, com erro tipado e ausência de
   persistência/IO; repetir o sensor.


## Follow-up independente — G1 resolvida — 2026-09-13

**Veredito final: PASS para o snapshot revisado acima acrescido do teste de recent-auth do destino.**
Esta conclusão substitui o FAIL histórico e o fix requerido de G1: o sensor acumulado é agora
**7/7 killed, 0 survived** (seis provas anteriores preservadas e o sensor #2 repetido neste follow-up).
AC-W5 passa; nenhum fix permanece aberto neste escopo. Os gates amplos anteriores não foram repetidos.

Verificador independente: `task_91f880f1d14d`, dispatch `ctx_138be99d680f`; o coordenador escreveu o fix,
e este worker apenas o revisou, executou o sensor em scratch e acrescentou esta evidência (autor ≠ verificador).

### Delta exato e isolamento

- Os 79 hashes do manifesto anterior conferem com o scratch anterior restaurado, sem divergências.
- Entre os arquivos backend daquele manifesto, somente `ManageWalletTest.kt` mudou: novo teste nas
  linhas 116–125, contador `destinationSaves` iniciado em zero e incremento no fake de persistência
  nas linhas 174/178. O teste usa owner autorizado, dados válidos e `recentlyAuthenticated=false`;
  exige literalmente `RECENT_AUTHENTICATION_REQUIRED` e zero saves, posts, balanceReads e recoveries.
- `Wallets.kt` continua com SHA-256
  `5a235dd96c71a973ff71a8b4ad51f83f1837009ca6134ce38c51f80df35c9025`, idêntico à revisão anterior.
  O teste anterior tinha hash `3b327974f3f0da3316914922142c4d7e3507fe804bb116776b44529d90e35000`;
  o teste atual tem `8a0ffa6cfa0e1b8a73ebdac8586378686119488a317b103d44a43a916796e14d`.
- Scratch próprio: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-destination-auth-verifier-1ve2lram`.
  Copiou-se o backend anterior sem `build`, `.gradle` ou `.kotlin`, sobrepondo somente o teste atual.
  `GRADLE_USER_HOME` aponta para `gradle-user-home` dentro desse scratch: caches/wrapper foram copiados
  fisicamente por clone APFS, sem links compartilhados; nenhum Gradle rodou no workspace ou em home compartilhado.
- Artefatos no scratch: `root-test-delta.patch`, `snapshot-comparison.json`, `BASELINE_SHA256.txt`
  (hash `d68c910d88bdbe0b5ea73409e8201a275834a1f8f0f7137f828ece2e2b5e38d4`) e `mutation.patch`.
  Após restauração, todos os hashes de fontes do baseline conferem, e os dois arquivos wallet conferem
  byte a byte com o workspace. Nenhuma fonte de produção do workspace ou estado Git foi modificado.
- Há 15 alterações móveis posteriores ao manifesto anterior, listadas em `snapshot-comparison.json`,
  incluindo recorrência/Pix/MemberPayment e ReceiptExport/ReceiptExportText. Este PASS não certifica
  essas alterações posteriores: mantém os gates móveis vinculados ao snapshot histórico já aprovado.

### Sensor executado e evidência

Com JDK 21, dentro de `scratch/backend`, cada execução usou o mesmo comando focal:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home \
GRADLE_USER_HOME=../gradle-user-home \
./gradlew :features:receivables:test \
  --tests br.com.saqz.receivables.application.ManageWalletTest \
  --offline --no-daemon --no-build-cache --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process
```

| Etapa | Resultado |
|---|---|
| Baseline com teste novo | PASS: 10 testes, 0 falhas/erros/skips, exit 0 |
| Mutante #2: terceiro argumento de `context` em `CHANGE_BANK_DESTINATION` trocado de `recentlyAuthenticated` para `true` | **KILLED**: compilou; 10 testes, exatamente 1 falha de assertion, exit 1 |
| Fonte restaurada | PASS: 10 testes, 0 falhas/erros/skips, exit 0 |

A única falha do mutante foi `bank destination requires recent authentication before persistence or provider IO`,
`ManageWalletTest.kt:120`, `org.opentest4j.AssertionFailedError`: esperava `FinancialResult.Failure`,
mas recebeu `FinancialResult.Success`. Logo, é discriminação comportamental do bypass de autorização.
Uma tentativa preliminar usou um nome de parâmetro Kotlin inválido e falhou compilação; foi descartada
como prova e preservada em `invalid-mutant-compile.log`. Os resultados da tabela e hashes abaixo
correspondem ao mutante válido, com argumento posicional `true`.

Arquivos abaixo estão no scratch próprio informado acima:

| Evidência | SHA-256 |
|---|---|
| `baseline.log` | `b69099d56d9db9231cdb26a8877e513e213e20e6bb7e32e19f3d8bc977ba0db5` |
| `baseline.xml` | `869df5afa88ac3c49e1ef5c8b87b70373211cccd7808d959149de6fbcc755a13` |
| `mutant.log` | `ca42dae761c0b96abad865e00daef5d174f168bc4780c171f860ee6dd8302eb1` |
| `mutant.xml` | `7a065524efcaf6abf37f42904dfc3c518572a83456631e055b3c39e88e0c2ebe` |
| `restored.log` | `79f0a845cdfea01a9bc0f9c05c043d0fe7a1ed299dee5128fc953dee0e20c2d5` |
| `restored.xml` | `daa84ed3976b0c3148f14ae0b4070dc07d7e2677156d5a7666c8f094dc993e73` |

Permanecem os limites externos anteriores: nenhuma chamada real ao Asaas, homologação comercial ou
piloto foi executado. Não houve novas suítes móveis, JDBC, bootstrap ou arquitetura neste follow-up focal.
