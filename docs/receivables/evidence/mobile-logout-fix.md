# Mobile rollout: revogação no intento Logout

## Resultado

A composition root de recebíveis agora usa `SessionAccessStateMachine.activeSessionKey`, uma projeção síncrona do mesmo `SessionContext` que governa a máquina. A chave é nula durante `loggingOut` ou fora de `Ready`, e inclui a geração da sessão quando ativa. O CAS existente do intento Logout a revoga imediatamente, antes de qualquer callback nativo; relogin da mesma pessoa produz outra chave mesmo que o coletor não observe SignedOut.

A implementação generaliza a vista StateFlow existente, sem segundo estado espelhado e sem alterar o estado de tela consumido pelas demais features. Receivables continua sem dependência de access: a conversão vive apenas no app, usando o port já existente `ReceivablesSessionContext`. Não foi necessário alterar o coordinator da feature: suas guardas síncronas agora consultam o contexto correto.

## Regressão

`ReceivablesLogoutTest` usa SessionAccessStateMachine real, NativeAuthPort fake com signOut retido e gateway que pode responder mesmo depois do cancelamento. O scope da resposta é Unconfined e o binding usa o scheduler normal, permitindo responder antes da observação de sessão, sem depender de sleep.

- Resposta em voo após Logout, com estado de tela ainda Ready: nenhum callback ou descoberta; resume não cria outra consulta.
- Logout e relogin do mesmo usuário antes de o coletor observar SignedOut: chave diferente, resposta velha descartada, nova consulta exclusiva da sessão atual.
- Novo prepareNewJourney/refresh imediatamente depois do intento, antes do coletor: nenhuma chamada adicional ao gateway.
- Manutenção permanece disponível para sessão efetivamente ativa com rollout OFF e em background; não permanece disponível para sessão encerrada.

O probe independente anterior foi inspecionado somente para leitura em `/tmp/saqz-rollout-mobile-probe/compose-app/src/commonTest/kotlin/br/com/saqz/access/presentation/RolloutLogoutProbeTest.kt`; seu scratch não foi alterado.

## Validação

Logs:

- `/tmp/saqz-logout-gates.log`: BUILD SUCCESSFUL; testes gateway e coordinator Android/iOS, compileAndroidMain e compileKotlinIosSimulatorArm64 do compose-app, detektAll global e android-app:testDevDebugUnitTest.
- `/tmp/saqz-logout-session-di-final.log`: BUILD SUCCESSFUL em 55s; rodada final dos testes de sessão, DI e regressão e detekt dos módulos alterados.

Contagens verificadas nos XMLs:

| Suíte | Android | iOS |
|---|---:|---:|
| Receivables gateway | 5 | 5 |
| Receivables coordinator | 7 | 7 |
| Compose-app binding, logout e DI | 12 | 12 |
| SessionAccessStateMachineTest | coberta também pela regressão real no app | 67 |
| android-app dev unit tests | 186 | — |

A primeira tentativa de host DI expôs ausência de Main na JVM e uma espera de callback com relógio virtual enquanto o app usa outro scope. Os testes agora configuram/resetam Main com UnconfinedTestDispatcher e mantêm a mesma espera de 1 segundo em contexto real; nenhum assert foi removido, nenhum timeout ampliado. A suíte host nova compila apenas binding/logout/DI e suas fixtures; os testes Compose comuns continuam no runner iOS conforme AGENTS.

O primeiro filtro de sessão usou por engano o nome do arquivo VerifiedSessionCoordinatorTest, cujo nome real de classe é SessionAccessStateMachineTest; a rodada final corrigiu o filtro e verificou 67 testes, sem considerar a rodada de filtro vazio como evidência.

## Arquivos deste fix

1. `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/VerifiedSessionCoordinator.kt`
2. `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt`
3. `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBinding.kt`
4. `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBindingTest.kt`
5. `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceivablesLogoutTest.kt`
6. `mobile/compose-app/build.gradle.kts`
7. `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/SaqzKoinBootstrapTest.kt`
8. `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/di/SaqzKoinModulesTest.kt`

Fix: 256 linhas alteradas incluindo o novo teste ainda untracked; lote mobile relativo a e55fa717: 913 linhas incluindo untracked, abaixo do teto de 2000. `git diff --check -- mobile` limpo. Nenhuma alteração visual; nenhuma mudança em docs, .specs, backend, adm, branch, staging, commit ou push foi feita por este worker.
