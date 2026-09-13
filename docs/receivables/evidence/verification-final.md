# Reverificação independente mobile — 11b56e33 sobre b8c97387

## Decisão

**P2 resolvido. Nenhum novo achado bloqueador confirmado no fix ou no entorno revisado.** O probe comportamental que falhava na base agora passa, e as regressões reais de logout/relogin, binding, DI, manutenção e sessão passaram em execuções novas. Revisão em HEAD `11b56e33d2b4b3b104002944b84b05afa77496f4`, conforme mobile/AGENTS.md, contrato de rollout e os dois relatórios anteriores solicitados.

## Achados e evidência de resolução

- **P2 anterior encerrado — revogação síncrona:** `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/VerifiedSessionCoordinator.kt:168` projeta null quando loggingOut ou fora de Ready. O CAS em `:591` troca geração e marca loggingOut antes até da primeira escrita local; portanto não depende de signOut terminar. O getter em `:799` consulta o contexto autoritativo a cada leitura, sem flow espelhado sujeito a atraso. O estado visual continua com a projeção anterior em `:165`.
- **Nova sessão da mesma pessoa:** geração incrementa em `VerifiedSessionCoordinator.kt:256`, inclusive no logout e na nova identidade (`:642`); a chave inclui geração e ID (`:170`). Conflation do StateFlow pode omitir SignedOut, mas não confunde a chave final com a anterior. `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceivablesLogoutTest.kt:62` entrega resposta antiga antes do observador e comprova descarte, chave diferente e consulta nova.
- **Wiring e guardas:** `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt:17` e `:20` ligam tanto a consulta síncrona quanto o binding à mesma chave. `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBinding.kt:18` observa mudanças e `:25` relê a chave no resume. As guardas de `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceivablesCoordinator.kt:47` e `:58` agora detectam logout antes do observador, sem permitir consulta nova/callback. Sem nova dependência access na feature receivables nem segunda via de DI.
- **Manutenção preservada:** `ReceivablesLogoutTest.kt:62` mantém manutenção com rollout OFF e em background na sessão ativa; `:39` verifica revogação após logout. `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceivablesCoordinatorTest.kt:33` cobre OFF e falha sem esconder manutenção. A publicação do estado local pelo observador continua assíncrona; a proteção de novas operações consulta a chave síncrona, e não presume publicação instantânea do estado visual.

## Execuções e XML

Todos os comandos rodaram offline, com `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` e `ANDROID_HOME=/Users/bruno_almeida/Library/Android/sdk`. Comandos abaixo executados em mobile, exceto o probe em seu scratch.

1. `./gradlew --offline :compose-app:testAndroidHostTest --tests '*ReceivablesLogoutTest*' --tests '*ReceivablesSessionBindingTest*' --tests '*SaqzKoinModulesTest*' --tests '*SaqzKoinBootstrapTest*' :compose-app:iosSimulatorArm64Test --tests '*ReceivablesLogoutTest*' --tests '*ReceivablesSessionBindingTest*' --tests '*SaqzKoinModulesTest*' --tests '*SaqzKoinBootstrapTest*' :features:access:iosSimulatorArm64Test --tests '*SessionAccessStateMachineTest*'`
   - Log `/tmp/saqz-reverify-mobile.log`: BUILD SUCCESSFUL, 5s, 32 tasks executadas / 299 up-to-date. **Ambas as tasks compose-app executaram**, 12 testes em cada plataforma: logout 3, binding 1, DI 6, bootstrap 2. XMLs em `mobile/compose-app/build/test-results/{testAndroidHostTest,iosSimulatorArm64Test}/`, timestamps 17:05:22–23 UTC. Sessão nesta primeira chamada foi UP-TO-DATE; não contada como execução nova.
2. `./gradlew --offline :features:access:iosSimulatorArm64Test --tests '*SessionAccessStateMachineTest*' --rerun`
   - Log `/tmp/saqz-reverify-session.log`: BUILD SUCCESSFUL, 3s, task de teste executada. XML `mobile/features/access/build/test-results/iosSimulatorArm64Test/TEST-iosSimulatorArm64Test.br.com.saqz.access.presentation.SessionAccessStateMachineTest.xml`: **67 testes**, timestamp novo 17:06:02 UTC. Filtro é o nome real da classe, não o nome do arquivo VerifiedSessionCoordinatorTest.
3. `./gradlew --offline :features:receivables:presentation:testAndroidHostTest --tests '*ReceivablesCoordinatorTest*' --rerun :features:receivables:presentation:iosSimulatorArm64Test --tests '*ReceivablesCoordinatorTest*' --rerun`
   - Log `/tmp/saqz-reverify-coordinator.log`: BUILD SUCCESSFUL, 14s. **7 Android + 7 iOS**, ambas tasks executadas, XMLs em `mobile/features/receivables/presentation/build/test-results/{testAndroidHostTest,iosSimulatorArm64Test}/`, timestamps 17:06:41–42 UTC.
4. Probe independente descrito abaixo: **1 teste novo passando**, timestamp 17:06:59 UTC.

**Total efetivamente executado nesta revisão: 106 testes, zero falhas, erros ou skips nos XMLs.** Dependências UP-TO-DATE não são apresentadas como reexecuções. `git diff --check b8c97387 11b56e33` passou; `git diff --quiet -- mobile` confirmou nenhuma edição local de produto.

## Independência: sensor anterior reutilizado

Em `/tmp/saqz-rollout-mobile-probe`, copiei literalmente apenas os três arquivos de produção alterados (máquina, módulo DI e binding). Adaptei no teste independente `compose-app/src/commonTest/kotlin/br/com/saqz/access/presentation/RolloutLogoutProbeTest.kt:39` somente as duas expressões de wiring para consumir activeSessionKey em lugar de state/ID. A fixture, agendamento, atraso nativo, resposta permitida e assert de zero navegações permanecem os mesmos do sensor que antes falhou com actual 1.

A primeira compilação encontrou o teste antigo ReceivablesSessionBindingTest incompatível com o novo tipo de entrada; copiei a versão corrigida desse teste para o scratch e repeti. Essa primeira tentativa (`/tmp/saqz-reverify-probe.log`) foi falha de preparação do scratch, não evidência comportamental nem defeito do produto.

Comando no scratch: `./gradlew --offline :compose-app:iosSimulatorArm64Test --tests '*RolloutLogoutProbeTest.pendingReceivablesCallbackMustNotRunAfterLogoutIntent*'`. Resultado final `/tmp/saqz-reverify-probe-final.log`: BUILD SUCCESSFUL em 46s, task de teste executada. XML `/tmp/saqz-rollout-mobile-probe/compose-app/build/test-results/iosSimulatorArm64Test/TEST-iosSimulatorArm64Test.br.com.saqz.access.presentation.RolloutLogoutProbeTest.xml`: 1 teste, 0 failures/errors/skips. Comparação discriminante com a execução anterior documentada em `/tmp/saqz-rollout-verification.md` e `/tmp/receivables-independent-logout-probe.log`: mesma asserção anteriormente falhava por callback executado durante signOut atrasado. Não fiz mutação em produto nem acrescentei teste à árvore original.

## Evidências reutilizadas e limites

Backend/adm permanecem nas evidências da revisão anterior: domínio 15, integração PostgreSQL 28, bootstrap focado 22, arquitetura 20, Node 35; sensor de idempotência adm 8/8 limpo e 7/8 mutado, conforme `/tmp/saqz-rollout-verification.md`. Não repeti esses gates: o fix contém somente oito arquivos mobile. Transporte de renovação de token iOS (16), gateway e gates amplos anteriores continuam evidência prévia, não nova execução deste passe.

Nenhum servidor, chave, Asaas real, fonte original, staging, commit, branch ou push foi alterado. Escritas desta revisão se limitam ao relatório/logs/scratch em /tmp e saídas geradas de build. As alterações concorrentes observadas em .specs pertencem ao coordenador. Não houve mudança visual neste fix; não alego validação financeira real nem novas jornadas de pagamentos ainda inexistentes. Não resta correção mobile para o bloqueio P2 revisado; aceite integrado e próximos lotes permanecem com o coordenador.
