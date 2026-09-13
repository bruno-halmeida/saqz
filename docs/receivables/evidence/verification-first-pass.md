# Verificação integrada — recebimentos / toggles e segmentação

Base revisada: `e55fa717..b8c97387` (adm `e887b298`, mobile `23bd2252`, backend `b8c97387`). Contratos: `docs/receivables/rollout-contract.md` e `docs/receivables/integration-gates.md`. Revisão de fontes somente leitura; nenhum add/commit/branch/push, servidor, chave ou Asaas real. Testes usam PostgreSQL embutido local, HTTP localhost e provedores simulados. Artefatos e teste adicional foram escritos apenas em diretórios temporários; builds normais escrevem saídas geradas ignoradas.

## Achado que impede aceite integrado

**P2 — logout em andamento ainda aceita callback de nova jornada.** Ponto novo: `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt:18` e `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBinding.kt:20`; consumidor: `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceivablesCoordinator.kt:59` e `:67`.

A composição deriva identidade apenas de `SessionAccessState.Ready.user.id`. Entretanto, `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/VerifiedSessionCoordinator.kt:585` invalida a geração interna e marca `loggingOut=true` ao receber Logout, e só publica SignedOut após callbacks do armazenamento local e `auth.signOut` terminarem (`:593`). A vista pública entrega apenas `state` (`:792`), sem expor a geração nem loggingOut. Portanto o binding não recebe invalidação nesse intervalo e a consulta síncrona também continua retornando o usuário anterior. Uma disponibilidade pendente pode devolver true e executar `onAvailable`; uma nova chamada a prepareNewJourney também não tem como detectar a saída já iniciada. O mesmo intervalo deixa discovery/maintenance locais associados à sessão em encerramento. Não demonstra saque ou pagamento não autorizado: essas jornadas ainda não existem e o backend mantém suas verificações.

Correção deve ligar recebimentos a um contexto autoritativo de sessão que invalide **no início** do logout, com geração própria acessível de forma síncrona; não basta depender do observador que recebe SignedOut no fim. Preservar manutenção para sessão realmente válida e não colocar rollout em gates de dinheiro existente. Regressão necessária: sessão Ready, consulta/callback pendente, native signOut atrasado, Logout, resposta permitida entregue antes da conclusão nativa; callback deve permanecer zero e novas tentativas não devem consultar/navegar. Acrescentar teste de troca/renovação de sessão usando esse contexto, não apenas MutableStateFlow artificial.

A suíte entregue de binding (`mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBindingTest.kt:24`) usa MutableStateFlow e publica SignedOut diretamente; por isso os gates existentes verdes não discriminam o intervalo real da máquina de sessão.

## Evidência comportamental adicional

Cópia isolada do mobile: `/tmp/saqz-rollout-mobile-probe` (sem builds/cache local, arquivos de chaves e configurações Firebase nativas). Fontes de produto copiadas sem mudanças. Foi copiada a fixture real de `VerifiedSessionCoordinatorTest` para `compose-app/src/commonTest/kotlin/br/com/saqz/access/presentation/RolloutLogoutProbeTest.kt`, acrescentando somente o teste `pendingReceivablesCallbackMustNotRunAfterLogoutIntent`. O teste usa a máquina real, binding real e coordinator real, com gateway/finalização nativa controlados; não replica a lógica de produção.

Comando: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/bruno_almeida/Library/Android/sdk /tmp/saqz-rollout-mobile-probe/gradlew -p /tmp/saqz-rollout-mobile-probe --offline :compose-app:iosSimulatorArm64Test --tests '*RolloutLogoutProbeTest.pendingReceivablesCallbackMustNotRunAfterLogoutIntent*'`.

Log: `/tmp/receivables-independent-logout-probe.log`. Resultado registrado ao final deste relatório.

## Contratos e gates conferidos

| Área | Evidência e resultado |
|---|---|
| OFF/SELECTED_USERS/ALL_USERS | `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/ReceivablesRollout.kt:21`: OFF vence ALLOW; SELECTED_USERS exige ALLOW; ALL_USERS exceto DENY; MOBILE depende BACKEND. `RolloutPolicyTest` verifica matriz completa e dependência. V53 começa OFF e INHERIT remove exceção. |
| HTTP e identidade | `backend/bootstrap/src/main/kotlin/br/com/saqz/adminweb/http/AdminReceivablesRolloutController.kt:35`: availability resolve ator da sessão, não query userId. Admin lookup executado por requisição em `:53`; suíte HTTP cobre 401/403, revogação na mesma sessão, 404, 400, 409, no-store e campos JSON sem envelope. |
| Piloto/usuário selecionado | Adm seleciona ID da listagem existente, monta `/users/{id}` em `adm-web/index.html:776`; envio em `:815` usa os dois overrides e null/boolean da conta. HTTP e PostgreSQL testam SELECTED_USERS+ALLOW, efeito mobile e ausência de conta. |
| Backend/adm/mobile | Endpoints, nomes dos enums, version/expectedVersion, requestId/reason, arrays e availability coincidem. `KtorReceivablesAvailabilityGateway.kt:25` usa GET autenticado sem identidade enviada e exige booleanos reais; envelope de erro é traduzido pelo status. |
| Auditoria/idempotência | `JdbcReceivablesRollout.kt:83` serializa escrita; replay compara ator/alvo/conteúdo e devolve snapshot original antes da versão. Testes verificam conflito de conteúdo/ator/versão, exatamente uma escrita concorrente, histórico imutável no banco e flag da conta na transação. Adm conserva corpo byte a byte após timeout e congela edição/reload até resposta/replay; 409 exige recarga. |
| Operações novas | `FinancialOnboarding.kt:59` consulta rollout para conta nova, `:78` bloqueia provisionamento READY pelo titular; UNKNOWN recupera sem recriar. `ManageGroupReceivables.kt:94` verifica titular, não ALLOW do delegado; ativação ainda exige cadastro aprovado, plano e flag operacional. |
| Dinheiro existente | `ManageGroupReceivables.kt:79` cancela sem consultar rollout/eligibilidade; teste remove tabela rollout para comprovar independência. Documentos/retomada/UNKNOWN continuam após OFF. `FinancialAccess.kt:59` mantém autorização própria, autenticação recente de saque/destino e não inclui renovação de instrumento nos novos negócios. Saldo/pagamento/saque/reembolso não foram implementados ou exercitados ponta a ponta neste lote. |
| Estado mobile | Coordinator falha fechado em loading/erro/OFF, reconsulta antes de nova jornada, descarta resposta fora da geração e resposta de outro userId; pausa invalida e resume consulta novamente. Exceção bloqueadora: intervalo do logout real descrito acima. Não há entrada ou rota fictícia de pagamentos. |
| Renovação de token | `AuthenticatedNetworkClient` compartilhado renova uma vez após 401, reenvia bearer atualizado e invalida no segundo 401; 16 testes iOS da classe passaram. Isso valida transporte de sessão; não corrige nem substitui invalidação autoritativa do contexto de recebimentos. |
| Adm sessão | Logout zera drafts, alvo, histórico, corpo pendente e descarta respostas antigas (`adm-web/index.html:738-761`), coberto pelos testes Node. |

## Comandos reais e contagens

Todos os Gradle usaram JDK 21 acima e `--offline`.

1. Em backend: `./gradlew --offline :features:receivables:check :bootstrap:test --tests '*ReceivablesRolloutEndpointIntegrationTest' --tests '*GroupReceivablesIntegrationTest' --tests '*BearerSecurityIntegrationTest' --tests '*PlatformAdminEndpointIntegrationTest' :architecture-tests:test --rerun-tasks` — BUILD SUCCESSFUL, 34 tasks executadas, 43s. XML: 15 domínio, 28 integração PostgreSQL, 22 bootstrap focados, 20 arquitetura; zero falhas/erros/skips. Log `/tmp/receivables-independent-backend.log`.
2. `node --test adm-web/tests/*.test.cjs` — 35 passaram, zero falhas. Log `/tmp/receivables-independent-adm.log`.
3. Em mobile: `./gradlew --offline :features:receivables:data:testAndroidHostTest :features:receivables:presentation:testAndroidHostTest :compose-app:iosSimulatorArm64Test --tests '*ReceivablesSessionBindingTest*' --tests '*SaqzKoinModulesTest*'` — BUILD SUCCESSFUL, 1m5s; 5 gateway + 7 coordinator Android e 7 DI/binding iOS nos XML, zero falhas/erros/skips. Distinguir tasks UP-TO-DATE de reexecução no log `/tmp/receivables-independent-mobile.log`; não interpretar cache como execução nova.
4. Em mobile: `./gradlew --offline :core:network:iosSimulatorArm64Test --tests '*AuthenticatedNetworkClientTest*'` — BUILD SUCCESSFUL, 11s; 16 testes, zero falhas/erros/skips. Log `/tmp/receivables-independent-token-refresh.log`.
5. `git diff --check e55fa717..b8c97387` — passou.

XMLs reais: `backend/{features/receivables,bootstrap,architecture-tests}/build/test-results/`, `mobile/features/receivables/{data,presentation}/build/test-results/`, `mobile/compose-app/build/test-results/iosSimulatorArm64Test/` e `mobile/core/network/build/test-results/iosSimulatorArm64Test/`.

Gates amplos anteriores (393 bootstrap, 266 Android integrados, detekt, compilações Android/iOS e navegador simulado) foram considerados evidência prévia dos workers em `docs/receivables/evidence/` e `adm-web/tests/receivables-evidence.md`; não alego reexecução desses gates nem browser conectado ao backend real.

## Sensor de discriminação independente

`/tmp/receivables-independent-sensor.json` aponta a cópia temporária `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-rollout-sensor-e4y78xdu`. Cópia limpa de index.html + receivables.test.cjs: 8 passam, exit 0. Mutação **só na cópia** troca `let pending = this._recebimentosPending[kind]` por `let pending = null`, quebrando reuso do requestId. Mesma suíte: 7 passam, 1 falha, exit 1; teste de timeout/reenvio byte-idêntico falha em `tests/receivables.test.cjs:25` por UUID diferente, não por compilação/setup. Logs `baseline.log` e `mutant.log` nessa pasta.

## Limites e próximo passo

Aceite integrado depende de corrigir o logout mobile e reexecutar sua regressão junto com binding/coordinator, mantendo os demais gates verdes. Nenhum outro defeito bloqueador confirmado nos toggles backend/adm neste passe. Integração financeira real, instrumentação de jornadas futuras e liberação de produção permanecem fora de escopo; simulações não atestam Asaas real. Mudanças observadas em `.specs/` durante a revisão pertencem ao trabalho concorrente do coordenador; não foram feitas por este verificador.

## Resultado final da reprodução

**Confirmado comportamentalmente:** teste adicional na cópia isolada executou 1 teste, 1 falha, 0 erros e 0 skips. Falha de asserção: `Logout intent must invalidate pending new-journey callback before native signOut completes. Expected <0>, actual <1>.` BUILD FAILED em 45s por comportamento, não por compilação/setup. XML: `/tmp/saqz-rollout-mobile-probe/compose-app/build/test-results/iosSimulatorArm64Test/TEST-iosSimulatorArm64Test.br.com.saqz.access.presentation.RolloutLogoutProbeTest.xml`. A revisão está concluída; o aceite do produto está bloqueado pela correção P2 descrita acima.

Precisão das tasks mobile: gateway Android foi UP-TO-DATE (5 testes reutilizados); coordinator Android (7) e DI/binding iOS (7) executaram nesta rodada. Transporte iOS (16) executou na rodada adicional. Não houve alteração de fontes na árvore original.
