# Mobile — liberação de recebimentos

Entrega estrutural conforme docs/receivables/rollout-contract.md, sem mudança visual, rota ou promessa de pagamento. Total: 697 linhas alteradas/adicionadas em 17 arquivos mobile; nenhum arquivo fora de mobile modificado por este worker; sem branch/staging/commit/push.

## Implementação

- Módulos KMP `:features:receivables:domain`, `:data`, `:presentation`; produto exclusivamente em commonMain. Domínio depende apenas de core:domain; data depende de domínio/core:network; apresentação não importa HTTP, data ou outra feature.
- `KtorReceivablesAvailabilityGateway` usa o AuthenticatedNetworkClient compartilhado, GET api/receivables/availability sem identidade do cliente e retryTransport(Read). DTO internal exige os três campos e booleanos JSON reais; resposta contraditória, inválida e falha de rede viram erro tipado. Não persiste nem reutiliza resultados entre consultas.
- `ReceivablesCoordinator` mantém StateFlow fechado para descoberta inicialmente, durante consulta, erro, OFF e background. `maintenanceAvailable` depende só da sessão autenticada e não de toggles ou erros de disponibilidade; endpoints continuam responsáveis por autorização do recurso.
- Login/troca de usuário limpa o estado e consulta; logout descarta estado e pendências. Guarda de geração e consulta síncrona do contexto atual descartam respostas e callbacks de usuário anterior, inclusive antes do observador da sessão executar.
- `ReceivablesSessionBinding`, no compose-app, observa SessionAccessState; AccessGate conecta LifecycleResumeEffect (Android/iOS) ao refresh e limpa descoberta/callbacks em pausa. Koin é a composição única de gateway, contexto local, coordenador e binding.

## Integração do próximo lote

O Root da futura jornada resolve `ReceivablesCoordinator` via Koin e coleta `state`. Use `state.discoveryAvailable` apenas para descoberta; antes de navegar para NOVA jornada chame `prepareNewJourney { callbackRealDoApp() }`, que sempre consulta novamente. O callback não será chamado após erro, revogação, troca de sessão, background ou consulta superada; a UI pode observar loading/error para feedback.

`ReceivablesEntryCallbacks` define callbacks opcionais de nova jornada e manutenção por resourceId. Null significa destino não implementado e exige ausência da entrada correspondente; nenhum desses callbacks está conectado a rota fictícia nesta entrega. Novo cadastro, ativação, emissão e contratação passam pelo gate; pagar ou renovar instrumento de ordem já emitida, carteira e histórico são acesso preservado mesmo em OFF. A futura manutenção deve usar o callback de manutenção sem passar por prepareNewJourney/discoveryAvailable; aprovação, plano, ativação de grupo e autorização financeira permanecem verificações do backend. Pagamentos, documentos, saldo, saque e demais jornadas são follow-up, sem implementação/promessa neste lote.

## Gates

- `:features:receivables:data:testAndroidHostTest`: 5 testes passaram.
- `:features:receivables:presentation:testAndroidHostTest`: 7 testes passaram.
- `:features:receivables:data:iosSimulatorArm64Test`: 5 testes passaram.
- `:features:receivables:presentation:iosSimulatorArm64Test`: 7 testes passaram.
- `:compose-app:compileAndroidMain` e `:compose-app:compileKotlinIosSimulatorArm64`: passaram.
- `detektAll`: passou, sem alterar baseline.
- `:android-app:testDevDebugUnitTest`: 266 testes passaram.
- `git diff --check -- mobile`: passou.
- `:compose-app:iosSimulatorArm64Test --tests '*SaqzKoinModulesTest*' --tests '*ReceivablesSessionBindingTest*'`: 7 testes passaram.

Logs: /tmp/receivables-mobile-final-gates.log e /tmp/receivables-mobile-final-di.log. Relatórios XML em mobile/features/receivables/{data,presentation}/build/test-results/, mobile/compose-app/build/test-results/ e mobile/android-app/build/test-results/. Nenhuma captura visual necessária porque nenhum estado visual/componente/rota foi alterado; não foi executado fluxo instrumentado em emulador nem build Xcode assinado.

## Arquivos

- `mobile/compose-app/build.gradle.kts`
- `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt`
- `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt`
- `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/SaqzKoinBootstrap.kt`
- `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBinding.kt`
- `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/di/SaqzKoinModulesTest.kt`
- `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBindingTest.kt`
- `mobile/features/receivables/data/build.gradle.kts`
- `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorReceivablesAvailabilityGateway.kt`
- `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorReceivablesAvailabilityGatewayTest.kt`
- `mobile/features/receivables/domain/build.gradle.kts`
- `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/ReceivablesAvailability.kt`
- `mobile/features/receivables/presentation/build.gradle.kts`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceivablesContract.kt`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceivablesCoordinator.kt`
- `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceivablesCoordinatorTest.kt`
- `mobile/settings.gradle.kts`
