# Mobile SOLID Refactor Wave 2 Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user — do not proceed without it.**

---

**Design**: `.specs/features/mobile-solid-refactor-wave-2/design.md`
**Spec**: `.specs/features/mobile-solid-refactor-wave-2/spec.md`
**Status**: Approved

**Convenções obrigatórias (valem para todas as tasks):**

- Todo comando shell prefixado com `rtk` (regra do repositório).
- Comandos Gradle rodam com workdir `mobile/` (ex.: `rtk ./gradlew :core:common:allTests --console=plain`).
- Scripts de repo rodam com workdir na raiz (ex.: `rtk scripts/check-credentials`).
- Um commit Conventional Commit por task (ver campo **Commit**). Nunca misturar fix comportamental com refactor estrutural no mesmo commit (FIX-05).
- Nunca enfraquecer, pular ou deletar teste para fazer o gate passar.
- Estilo de teste existente (seguir): `kotlin.test` + `kotlinx.coroutines.test.runTest` + fakes/hand-rolled, arquivos em `src/commonTest/kotlin/**` espelhando o pacote do source; android-app usa `src/test/kotlin/**`.
- Kotlin: sem comentários no código, salvo pedido explícito.

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec — confirm before Execute. Guidelines found: `AGENTS.md` (root: gates `scripts/check-*`, "Never weaken, skip, or delete a test", Definition of Done), `mobile/AGENTS.md` boundaries, testes existentes amostrados (`FinanceViewModelTest.kt`, `AndroidGroupDraftStoreTest.kt`, `AccessViewModelTest.kt`).

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command (workdir `mobile/`) |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Presentation (state machines, ViewModels, coordinators, orchestrator) | unit | Todas as transições/ramos; 1:1 com ACs da spec; edge cases da spec cobertos; suites existentes sem redução | `mobile/features/*/src/commonTest/kotlin/**`, `mobile/compose-app/src/commonTest/kotlin/**` | `rtk ./gradlew :features:groups:allTests :features:access:allTests :compose-app:allTests --console=plain` |
| Data (gateways, mappers HTTP→domínio) | unit | Todos os mapeamentos de status/erro; 1:1 com SHR-02 | `mobile/features/groups/src/commonTest/kotlin/**` | `rtk ./gradlew :features:groups:allTests --console=plain` |
| core/common (formatters, validators) | unit | Todos os ramos; entradas-limite (vazio, separador duplo, valores grandes, datas inválidas) | `mobile/core/common/src/commonTest/kotlin/**` | `rtk ./gradlew :core:common:allTests --console=plain` |
| core/network (transport, error mapper, multipart) | unit | Pipeline único: happy + timeout/socket/genérico + corpo de erro limitado; multipart preservado | `mobile/core/network/src/commonTest/kotlin/**` | `rtk ./gradlew :core:network:allTests --console=plain` |
| android-app adapters | unit | Happy + erro + regressão do fix correspondente | `mobile/android-app/src/test/kotlin/**` | `rtk ./gradlew :android-app:testDevDebugUnitTest --console=plain` |
| DI wiring (módulos Koin) | unit | Grafo resolvível (koin-test `verify()` ou teste de resolução por módulo) | `mobile/compose-app/src/commonTest/kotlin/**` (fallback: `androidUnitTest`) | `rtk ./gradlew :compose-app:allTests --console=plain` |
| Config (version catalog, gradle) | none | — (build gate apenas) | — | build gate |

## Gate Check Commands

> Generated from codebase — confirm before Execute.

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | Após tasks de um único módulo | `rtk ./gradlew :<modulo>:allTests --console=plain` (workdir `mobile/`; android-app: `:android-app:testDevDebugUnitTest`) |
| Full | Após tasks cross-módulo (F1 consumo, F3 hub, F4, F5) | `rtk ./gradlew :core:common:allTests :core:network:allTests :features:access:allTests :features:groups:allTests :compose-app:allTests :android-app:testDevDebugUnitTest --console=plain` (workdir `mobile/`) |
| Build | Ao final de cada fase | Full gate + `rtk scripts/check-credentials` + `rtk scripts/check-scope` (workdir raiz). Gate agregado final (validação): `rtk scripts/check-gradle` e `rtk scripts/check-ios` (requerem emulador/simulador) |

---

## Execution Plan

Phases are ordered and run sequentially — each phase completes before the next begins, and tasks within a phase execute in order.

### Phase 0: Fixes comportamentais isolados

```
T01 → T02 → T03 → T04
```

### Phase 1: Fundações de formatação e mapeamento de erro

```
T05 → T06 → T07 → T08
```

### Phase 2: Fundações de access + consumo dos formatters

```
T09 → T10 → T11
```

### Phase 3: NetworkClient decomposto

```
T12 → T13 → T14
```

### Phase 4: Koin bootstrap

```
T15 → T16 → T17 → T18
```

### Phase 5: Orchestrator e decomposição do hub

```
T19 → T20 → T21 → T22 → T23
```

### Phase 6: State machines genéricos

```
T24 → T25 → T26
```

### Phase 7: GroupSetupScreen decomposta

```
T27 → T28 → T29 → T30
```

---

## Task Breakdown

### T01: FIX-01 — Propagar expenseId no clear de draft de despesa

**Status**: completed (e8a6144)

**What**: `ExpenseAdapter.clear(groupId, expenseId, commandKey, done)` passa a incluir `expenseId` como `resourceId` na referência do draft (mesmo mecanismo já usado por GAME), em vez de descartá-lo.
**Where**: `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/groups/draft/AndroidGroupDraftStore.kt` (adapter em `:63`; `store.clearExpense` em `:35`)
**Depends on**: None
**Reuses**: `AndroidDraftRef(EXPENSE, groupId, resourceId)` (padrão já usado por `gameRef` em `:50-52`)
**Requirement**: FIX-01, FIX-05

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [x] `clearExpense` valida/limpa apenas o envelope cuja ref contém o `expenseId` recebido
- [x] Novo teste de regressão em `AndroidGroupDraftStoreTest`: dois drafts de despesa no mesmo grupo; clear de um preserva o outro (falha sem o fix)
- [x] Gate quick passa: `rtk ./gradlew :android-app:testDevDebugUnitTest --console=plain`
- [x] Contagem de testes do módulo não diminui (+1 novo)

**Tests**: unit
**Gate**: quick
**Commit**: `fix(android): scope expense draft clear to the given expense id`

---

### T02: FIX-02 — Persistência real de attendance link no adapter de estado de grupos

**Status**: completed (ce07d76)

**What**: Eliminar o adapter fake `AndroidGroupStateAdapter` (read retorna `Success(null)`, write no-op) garantindo que a composição use o adapter real (`AndroidLocalGroupStateAdapter`); se o fake tiver algum consumidor, redirecioná-lo para o real.
**Where**: `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/access/AndroidGroupPorts.kt:33-34`; composição em `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/AndroidAppComposition.kt:79`
**Depends on**: None
**Reuses**: `AndroidLocalGroupStateAdapter` (`AndroidLocalAccessAdapters.kt:31-65`) — já implementa read/write reais sobre `AndroidAccessStateStore`
**Requirement**: FIX-02, FIX-05

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [x] Nenhum caminho de produção usa um adapter no-op para attendance link (fake removido ou delegando ao store real)
- [x] Novo teste: `writePendingAttendanceLink(value)` seguido de `readPendingAttendanceLink()` retorna `value` (falha sem o fix)
- [x] Nenhuma referência residual ao adapter removido (`rg` confirma)
- [x] Gate quick passa: `rtk ./gradlew :android-app:testDevDebugUnitTest --console=plain`
- [x] Contagem de testes do módulo não diminui (+1 novo)

**Tests**: unit
**Gate**: quick
**Commit**: `fix(android): persist pending attendance links in the real local store`

---

### T03: FIX-03 — Branch callback extrai parâmetro de attendance

**Status**: completed (5a27066)

**What**: `BranchSdkSessionClient.branchCallback` passa a extrair `saqz_attendance` dos parâmetros do Branch e emitir o mesmo `GroupLinkEvent` de attendance que o parser de URL direta emite.
**Where**: `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/access/AndroidLinkAdapter.kt:158-165` (constante `ATTENDANCE_PARAMETER` em `:169`)
**Depends on**: None
**Reuses**: parser de URL direta `directEvent`/`branchEvent` (companion, `:100-123`) — o evento emitido deve ser idêntico ao do caminho direto
**Requirement**: FIX-03, FIX-05

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [x] Params Branch contendo `saqz_attendance` emitem o attendance event equivalente ao da URL direta
- [x] Params contendo ambos (invite + attendance) respeitam a mesma regra de exclusividade do parser direto
- [x] Novo teste em `AndroidLinkAdapterTest`: params com attendance → evento emitido (falha sem o fix)
- [x] Gate quick passa: `rtk ./gradlew :android-app:testDevDebugUnitTest --console=plain`
- [x] Contagem de testes do módulo não diminui (+1 novo)

**Tests**: unit
**Gate**: quick
**Commit**: `fix(android): emit attendance link events from branch session params`

---

### T04: FIX-04 — 401 no grafo de rede do game-detail invalida a sessão

**Status**: completed (4f134c5)

**What**: O `SessionInvalidator` usado pelo grafo de rede criado no composable (`AuthenticatedAccessRoot.kt:215`, hoje `override fun invalidate() = Unit`) passa a delegar ao invalidator real da sessão.
**Where**: `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/AuthenticatedAccessRoot.kt:203-219`
**Depends on**: None
**Reuses**: `DelegatingSessionInvalidator` (`:646-650`) e o invalidator real do `AccessRuntime` (`:487-492`)
**Requirement**: FIX-04, FIX-05

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [x] 401 em requisição do grafo do composable chama `invalidate()` do invalidator real
- [x] Novo teste em `compose-app` commonTest: 401 no client secundário → invalidator real acionado (falha sem o fix)
- [x] Comentário/código não deixa invalidator no-op em produção
- [x] Gate quick passa: `rtk ./gradlew :compose-app:allTests --console=plain`
- [x] Contagem de testes do módulo não diminui (+1 novo)

**Tests**: unit
**Gate**: quick
**Commit**: `fix(compose-app): invalidate session on unauthorized game-detail requests`

---

### T05: Estender SaqzCurrencyFormatter com parse/sanitize e formato canônico

**Status**: completed (472ebba)

**What**: Adicionar a `core/common` as funções centrais de BRL: `formatBrl(cents)` no formato canônico `R$ 1.234,56`, `parseBrlToCents(input)` e `sanitizeBrlInput(input)`.
**Where**: `mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/formatting/SaqzCurrencyFormatter.kt`
**Depends on**: None
**Reuses**: `SaqzCurrencyFormatter.kt:6-17` existente; semântica de `parseBrlCents`/`sanitizeBrlInput`/`formatBrlInput` hoje em `features/groups/.../ui/setup/GroupSetupScreen.kt:980-1009` (fonte da migração)
**Requirement**: SHR-01

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [x] `formatBrl(123456) == "R$ 1.234,56"` (canônico único)
- [x] Parse aceita os formatos de teclado atuais do setup (`"70,00"` → 7000) e rejeita entradas inválidas retornando `null`
- [x] Testes novos cobrem: vazio, só separador, separador duplo, milhares, valores grandes, negativos/zero
- [x] Gate quick passa: `rtk ./gradlew :core:common:allTests --console=plain`
- [x] Contagem de testes do módulo não diminui (+N novos)

**Tests**: unit
**Gate**: quick
**Commit**: `feat(core): centralize brl currency formatting and parsing`

---

### T06: Estender SaqzDateTimeFormatter com data local e mês pt-BR

**Status**: completed (2f80249)

**What**: Adicionar `formatLocalDatePtBr(iso: String): String` e `formatMonthPtBr(...)` a `core/common`, cobrindo os usos hoje duplicados nas telas de finanças.
**Where**: `mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/formatting/SaqzDateTimeFormatter.kt`
**Depends on**: None
**Reuses**: `SaqzDateTimeFormatter.kt:9-20` (injeta `SaqzTimeZoneProvider`); semântica de `localDate()`/`monthPtBr()` em `features/groups/.../ui/finance/charges/FinanceScreen.kt:80-81`
**Requirement**: SHR-01

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [x] Saídas idênticas às implementações atuais para os casos exercitados hoje
- [x] Testes novos cobrem: datas válidas, mês por extenso/abreviado conforme uso atual, entradas inválidas
- [x] Gate quick passa: `rtk ./gradlew :core:common:allTests --console=plain`
- [x] Contagem de testes do módulo não diminui (+N novos)

**Tests**: unit
**Gate**: quick
**Commit**: `feat(core): centralize pt-br date and month formatting`

---

### T07: Mapeadores HTTP→domínio na data layer de groups + isProblem único

**Status**: completed (b0345e0)

**What**: Criar em `features/groups/data` os mapeadores `NetworkError.toSetupFailure()`, `toAdministrationFailure()`, `toPhotoFailure()`, `toDeferredLinkFailure()` (conforme falhas consumidas hoje), promovendo `isProblem` a extensão pública única em `core/network`.
**Where**: novos arquivos em `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/`; `isProblem` em `mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/`
**Depends on**: None
**Reuses**: padrão `toFinanceGatewayFailure` (`features/groups/.../data/finance/FinanceApi.kt:47-62`); extensões duplicadas em `presentation/GroupAdministrationCoordinator.kt:198-199` e `presentation/setup/GroupSetupSupport.kt:85-86`
**Requirement**: SHR-02

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [x] Cada mapeador cobre os status/códigos que seu consumidor trata hoje (403/404/400, `VERSION_CONFLICT`, `retryAfterSeconds`)
- [x] `isProblem` existe uma única vez (core/network) e as duas cópias apontam para ela
- [x] Testes novos por mapeador: cada status/código mapeado + caso desconhecido
- [x] Gate quick passa: `rtk ./gradlew :core:network:allTests :features:groups:allTests --console=plain`
- [x] Contagem de testes não diminui (+N novos)

**Tests**: unit
**Gate**: quick
**Commit**: `refactor(groups): centralize http-to-domain failure mapping in the data layer`

---

### T08: Presentation de groups consome os mapeadores da data layer

**Status**: completed (f8d0d15)

**What**: Migrar `GroupSetupViewModel`, `GroupAdministrationCoordinator`, `GroupPhotoCoordinator` e os dois state machines de links diferidos para usar os mapeadores de T07, removendo inspeção direta de `ApiProblemError`/status em presentation.
**Where**: `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/**` (`setup/GroupSetupViewModel.kt:273-287,207`, `GroupAdministrationCoordinator.kt:177-189,102`, `photo/GroupPhotoCoordinator.kt:138-141,189-190`, `DeferredInviteCoordinator.kt:152-171`, `attendance/share/DeferredAttendanceLinkCoordinator.kt:153-172`)
**Depends on**: T07
**Reuses**: mapeadores de T07
**Requirement**: SHR-02

**Done when**:
- [x] `rg "ApiProblemError" mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation` sem ocorrências de inspeção por status (exceto tipos importados pelos mapeadores)
- [x] Comportamento preservado: suites `GroupSetupViewModelTest`, `GroupAdministrationCoordinatorTest` (se existir), `GroupPhotoCoordinatorTest` e deferred-link tests passam sem alteração de expectativas
- [x] Gate full passa
- [x] Contagem de testes não diminui

**Tests**: unit (suites existentes como contrato)
**Gate**: full
**Commit**: `refactor(groups): consume data-layer failure mappers from presentation`

---

### T09: AuthUiErrorMapper único e migração das três telas de access

**What**: Criar `AuthUiError.messageRes()` (`when` exaustivo, sem `else`) e `NativeFailureCode.toUiError()` em `features/access/presentation`; migrar `LoginScreen`, `RegistrationScreen`, `IdentityCompletionScreens` e os dois coordinators para as fontes únicas.
**Where**: `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/`; consumidores em `ui/LoginScreen.kt:402-410`, `ui/RegistrationScreen.kt:257-263`, `ui/IdentityCompletionScreens.kt:270-275`, `presentation/AuthenticationCoordinator.kt:203-211`, `presentation/VerifiedSessionCoordinator.kt:239-247`
**Depends on**: None
**Reuses**: enum `AuthUiError` existente
**Requirement**: SHR-03

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Um único mapeamento `AuthUiError`→string resource no módulo; `else` que engolia erros removido — `INVALID_CREDENTIALS`, `EMAIL_IN_USE`, `WEAK_PASSWORD` têm mensagem própria em todas as telas
- [ ] Um único `NativeFailureCode`→`AuthUiError`
- [ ] Testes novos do mapeador cobrem todo valor do enum (exaustividade)
- [ ] Suites de access existentes passam sem alteração de expectativas (exceto onde o `else` mudava a mensagem — ajustar expectativa para a mensagem correta, justificado no commit)
- [ ] Gate quick passa: `rtk ./gradlew :features:access:allTests --console=plain`
- [ ] Contagem de testes não diminui (+N novos)

**Tests**: unit
**Gate**: quick
**Commit**: `refactor(access): centralize auth error mapping with exhaustive messages`

---

### T10: AccessValidators centrais (email + nome) e migração dos fluxos

**What**: Criar `AccessValidators` em `features/access/presentation` com `isValidEmail` e `isValidDisplayName` (2..80 chars, sem ISO control — regra restrita uniforme, confirmada); migrar coordinators e telas (incluindo registro, que hoje aceita `isNotBlank()`).
**Where**: `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/`; consumidores em `AuthenticationCoordinator.kt:198-201`, `VerifiedSessionCoordinator.kt:231-234`, `ui/RegistrationScreen.kt:93-94`, `ui/IdentityCompletionScreens.kt:199`
**Depends on**: None
**Reuses**: regra existente de `validName` (`VerifiedSessionCoordinator.kt:231-234`)
**Requirement**: SHR-04

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Uma única regra de email e uma única regra de nome no módulo, consumidas por coordinator e estados de UI
- [ ] Registro aplica a regra restrita de nome (mudança comportamental aprovada; teste cobre)
- [ ] Testes novos: limites 1/2/80/81 chars, ISO control, espaços, emails válidos/inválidos
- [ ] Gate quick passa: `rtk ./gradlew :features:access:allTests --console=plain`
- [ ] Contagem de testes não diminui (+N novos)

**Tests**: unit
**Gate**: quick
**Commit**: `refactor(access): centralize email and display name validation`

---

### T11: Features/groups consome os formatters centrais de BRL e data

**What**: Substituir as 5 implementações locais de BRL e os helpers de data duplicados pelos formatters de `core/common` (T05/T06), removendo o código local.
**Where**: `mobile/features/groups/.../ui/setup/GroupSetupScreen.kt:980-1009`, `presentation/finance/expenses/ExpenseRules.kt:58-68`, `presentation/finance/charges/FinanceChargeRules.kt:42-50`, `ui/finance/charges/FinanceScreen.kt:80-82`, `ui/finance/expenses/ExpenseScreen.kt:72-73`
**Depends on**: T05, T06
**Reuses**: `SaqzCurrencyFormatter`/`SaqzDateTimeFormatter` (T05/T06)
**Requirement**: SHR-01

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Nenhuma função local de BRL (`parseBrlCents`, `formatBrlInput`, `sanitizeBrlInput`, `toBrl`, `brlToCents`, `Long.brl`) ou de data (`localDate`, `monthPtBr`) permanece em features/groups
- [ ] Telas que exibiam `1.234,56` passam a exibir `R$ 1.234,56` (mudança visual aprovada; screenshots/expectativas de teste ajustadas se houver)
- [ ] Suites de groups passam (expectativas de string atualizadas apenas onde o formato mudou)
- [ ] Gate full passa
- [ ] Contagem de testes não diminui

**Tests**: unit (suites existentes + ajustes de expectativa de formato)
**Gate**: full
**Commit**: `refactor(groups): consume centralized brl and date formatters`

---

### T12: Environment tipado em NetworkConfig

**What**: Substituir a decisão por string mágica `"prod"` por `enum class Environment { Dev, Prod }` em `NetworkConfig`; factories Android/iOS decidem logging pelo enum; quem constrói `NetworkConfig` passa o enum.
**Where**: `mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/NetworkClient.kt` (config), `mobile/core/network/src/androidMain/kotlin/br/com/saqz/network/PlatformNetworkClient.android.kt:10-14`, `mobile/core/network/src/iosMain/kotlin/br/com/saqz/network/PlatformNetworkClient.ios.kt:9-13`; produtores de `environment` (`SaqzAppEnvironment.kt`, composition)
**Depends on**: None
**Reuses**: `NetworkConfig` existente
**Requirement**: NET-03

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] `rg '"prod"' mobile/core/network mobile/compose-app/src/commonMain` sem ocorrências de comparação por string mágica
- [ ] Mapeamento string→enum acontece uma única vez na borda (environment do app)
- [ ] Suites de network/compose-app passam
- [ ] Gate full passa
- [ ] Contagem de testes não diminui

**Tests**: unit (suites existentes; +teste do mapeamento de borda se inexistente)
**Gate**: full
**Commit**: `refactor(network): type the environment instead of magic strings`

---

### T13: Extrair multipart builder e logger injetável do NetworkClient

**What**: Mover `BoundedMultipartContent` para arquivo/classe próprios (`MultipartBodyBuilder`) e extrair logging/duração para `NetworkCallLogger` injetável por construtor (default preserva comportamento atual).
**Where**: `mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/NetworkClient.kt:341-390` (multipart), `:212-214,289-303` (logging); novos arquivos no mesmo pacote
**Depends on**: None
**Reuses**: implementações atuais (movimento puro)
**Requirement**: NET-01 (parcial)

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] `NetworkClient.kt` não declara mais multipart nem logging inline
- [ ] Comportamento de logging e multipart preservado (mesmos limites e campos)
- [ ] Suites de network passam sem alteração de expectativas
- [ ] Gate quick passa: `rtk ./gradlew :core:network:allTests --console=plain`
- [ ] Contagem de testes não diminui

**Tests**: unit (suites existentes como contrato)
**Gate**: quick
**Commit**: `refactor(network): extract multipart builder and call logger`

---

### T14: HttpTransport único + NetworkErrorMapper injetável; NetworkClient vira fachada

**What**: Criar `HttpTransport.execute` (pipeline único: montagem de request, cadeia de catches timeout/socket/genérico, leitura limitada do corpo de erro via `toBoundedError`) e `NetworkErrorMapper` injetável (default `ApiProblemErrorMapper`, `Json` injetado); `NetworkClient` mantém a API pública atual delegando ao transporte — `executeResponse` e `executeMediaRequest` deixam de duplicar o pipeline.
**Where**: `mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/` (novos `HttpTransport.kt`, `NetworkErrorMapper.kt`; `NetworkClient.kt:139-179,216-278` reescrito como fachada)
**Depends on**: T13
**Reuses**: `toBoundedError` (`:195-206`), cadeias de catch atuais, T13
**Requirement**: NET-01, NET-02

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Um único pipeline atende JSON e mídia; montagem bearer/headers, catches e leitura limitada existem uma única vez
- [ ] `Json` e estratégia de erro injetáveis por construtor
- [ ] Testes novos/ajustados cobrem: happy path JSON e mídia, timeout, socket, genérico, corpo de erro truncado no limite, `ApiProblem` decodificado
- [ ] Suites de network + compose-app + features passam sem alteração comportamental
- [ ] Gate full passa
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: full
**Commit**: `refactor(network): unify the request pipeline behind an injectable transport`

---

### T15: Pesquisar e pinar Koin no version catalog

**What**: Verificar compatibilidade Koin 4.x com o Kotlin/Compose/AGP atuais do workspace (docs oficiais Koin + `gradle/libs.versions.toml` + `build-logic`), pinar `koin-core` e `koin-test` no catalog e aplicar aos módulos necessários (`:compose-app` e targets de teste).
**Where**: `mobile/gradle/libs.versions.toml`, `mobile/compose-app/build.gradle.kts`, convenções em `mobile/build-logic/` se aplicável
**Depends on**: None
**Reuses**: catálogo e convenção KMP existentes
**Requirement**: NAV-01, NAV-04 (habilitador)

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Versão pinada com evidência de compatibilidade registrada no corpo do commit (link/release notes oficial)
- [ ] `:compose-app` compila para todos os targets com koin-core no classpath
- [ ] Nenhum tipo Koin exportado na API pública do framework (dependências `implementation`, não `api`)
- [ ] Build gate passa (compilação + suites existentes verdes)
- [ ] Contagem de testes não diminui

**Tests**: none (config — build gate)
**Gate**: build
**Commit**: `build(mobile): add koin for per-feature dependency modules`

---

### T16: networkModule + draftsModule com verificação de grafo

**What**: Criar módulos Koin `networkModule` (NetworkConfig, NetworkClient/HttpTransport, `AuthenticatedNetworkClient` single, `SessionInvalidator` real) e `draftsModule` (bindings das ports de draft por plataforma); adicionar teste de verificação do grafo (koin-test `verify()` ou teste de resolução).
**Where**: `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/` (novo pacote); testes em `src/commonTest` (fallback `androidUnitTest` se `verify()` não cobrir todos os targets — ver Risks no design)
**Depends on**: T15
**Reuses**: construção atual em `AccessRuntime` (`AuthenticatedAccessRoot.kt:484-497`) como referência de wiring
**Requirement**: NAV-01, NAV-02, NAV-04

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] `AuthenticatedNetworkClient` declarado como singleton; um único grafo por sessão
- [ ] Teste de verificação/resolução do grafo passa
- [ ] Gate quick passa: `rtk ./gradlew :compose-app:allTests --console=plain`
- [ ] Contagem de testes não diminui (+N novos)

**Tests**: unit
**Gate**: quick
**Commit**: `feat(compose-app): add koin network and drafts modules`

---

### T17: accessModule + groupsModule com bindings de máquinas e gateways

**What**: Criar módulos Koin `accessModule` (state machines de auth/session + bindings das ports nativas) e `groupsModule` (`GroupApi`, `GroupPhotoApi`, `RolesInvitesApi`, `AttendanceShareApi`, `GameApi`, state machines de selection/administration/deferred links); estender o teste de grafo.
**Where**: `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/`
**Depends on**: T16
**Reuses**: wiring de `AccessRuntime` (`:493-516`)
**Requirement**: NAV-01, NAV-04

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Todas as dependências hoje instanciadas em `AccessRuntime`/composable têm binding Koin
- [ ] Bindings de ports nativas resolvidos por plataforma (actual/expect ou módulos por source set)
- [ ] Teste de verificação do grafo cobre os novos módulos
- [ ] Gate quick passa: `rtk ./gradlew :compose-app:allTests --console=plain`
- [ ] Contagem de testes não diminui (+N novos)

**Tests**: unit
**Gate**: quick
**Commit**: `feat(compose-app): add koin access and groups modules`

---

### T18: startKoin nos entry points; SaqzAppDependencies deixa de ser o default

**What**: `SaqzApplication` (Android) e o entry iOS (`MainViewController`/`SaqzAccessibilityController`) inicializam Koin com os módulos reais; `SaqzApp()` deixa de usar `SaqzAppDependencies.Unconfigured` como default de produção; stubs migram para source sets de teste.
**Where**: `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/SaqzApplication.kt`, `mobile/compose-app/src/iosMain/kotlin/br/com/saqz/composeapp/MainViewController.kt`, `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt:13`, `SaqzAppDependencies.kt:90-185`
**Depends on**: T17
**Reuses**: factories de plataforma existentes (`AndroidAppComposition`)
**Requirement**: NAV-04

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Entry points de produção constroem o grafo Koin real (sem `Unconfigured`)
- [ ] Stubs/fakes de dependências vivem em `commonTest`/fixtures
- [ ] App Android sobe (compila + testes de launch existentes verdes); iOS framework compila
- [ ] Gate build passa
- [ ] Contagem de testes não diminui

**Tests**: unit (suites existentes de launch/composição)
**Gate**: build
**Commit**: `feat(app): bootstrap koin modules at the platform entry points`

---

### T19: InviteToolStateMachine na feature groups

**What**: Mover `rotateInvite`/`expireInvite`/`shareFinished` de `AccessRuntime` (`AuthenticatedAccessRoot.kt:582-616`) para um `InviteToolStateMachine` em `features/groups/presentation` (dona de `InviteToolState`/`InviteUiError`); `AccessRuntime`/orchestrator passa a delegar.
**Where**: novo arquivo em `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/`; chamadores em compose-app
**Depends on**: T18
**Reuses**: lógica atual de `AccessRuntime` (movimento puro); `RolesInvitesGateway`
**Requirement**: NAV-03

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Navegação não chama `roles.rotateInvite/expireInvite` diretamente
- [ ] Testes novos do state machine: rotate success/failure, expire success/failure, shareFinished(false), guard `isLoading`, groupId ausente
- [ ] Suites de groups + compose-app passam
- [ ] Gate full passa
- [ ] Contagem de testes não diminui (+N novos)

**Tests**: unit
**Gate**: full
**Commit**: `refactor(groups): own the invite tool state machine in the feature`

---

### T20: RequestIdGenerator injetável

**What**: Extrair a geração manual de UUID v4 (`AuthenticatedAccessRoot.kt:618-624`) para um `RequestIdGenerator` injetável (binding Koin), consumido pelo contrato de runtime.
**Where**: novo arquivo em `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/` (ou `core/common` se outro módulo precisar); `AuthenticatedAccessRoot.kt:618-624`
**Depends on**: T18
**Reuses**: algoritmo UUID v4 atual (movimento puro)
**Requirement**: NAV-03

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] UUID gerado por componente injetável; formato v4 preservado
- [ ] Teste novo: formato/versão/variant do UUID; injetável em testes (determinístico via fake)
- [ ] Gate quick passa: `rtk ./gradlew :compose-app:allTests --console=plain`
- [ ] Contagem de testes não diminui (+1 novo)

**Tests**: unit
**Gate**: quick
**Commit**: `refactor(compose-app): inject request id generation`

---

### T21: AccessOrchestrator absorve AccessRuntime e a reconciliação

**What**: Criar `AccessOrchestrator` (Kotlin puro, não-composable) dono das state machines, do auth observer e de toda reconciliação cross-feature hoje em `AccessRuntime` + `LaunchedEffect`s do composable (`:221-365`); expõe estado combinado + `onIntent` único + `start()/close()`; `AccessRuntime` é removido.
**Where**: `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/AccessOrchestrator.kt` (novo); `AuthenticatedAccessRoot.kt:221-365,480-629` (removido/diluído)
**Depends on**: T19, T20
**Reuses**: `AccessRuntime` como referência de wiring; `AccessRuntimeContract` (mantida como superfície; segregação deferida para onda 3)
**Requirement**: NAV-03

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Toda reconciliação session↔selection↔photo↔setup tem dono único no orchestrator
- [ ] `lateinit` de `DelegatingSessionInvalidator` eliminado (construção ordenada)
- [ ] Testes novos do orchestrator cobrem as reconciliações hoje exercitadas indiretamente; suites `AccessViewModelTest`, `AuthenticatedAccessRootTest` passam sem alteração de expectativas comportamentais
- [ ] Gate full passa
- [ ] Contagem de testes não diminui (+N novos)

**Tests**: unit
**Gate**: full
**Commit**: `refactor(compose-app): extract the access orchestrator from the route composable`

---

### T22: Grafo de rede único — game-detail consome o grafo primário

**What**: Remover o segundo grafo de rede criado no composable (`AuthenticatedAccessRoot.kt:203-219`); o fluxo de game-detail/attendance consome o `AuthenticatedNetworkClient` singleton do Koin (T16), herdando o invalidator real (FIX-04 vira estrutural).
**Where**: `AuthenticatedAccessRoot.kt:203-219` e consumidores do grafo secundário (`GameApi`, `AttendanceApi` instanciados no composable)
**Depends on**: T21
**Reuses**: `networkModule` (T16)
**Requirement**: NAV-02

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] `rg "createPlatformNetworkClient" mobile/compose-app/src/commonMain` tem ocorrência apenas no módulo Koin
- [ ] Fluxo de game-detail funciona sobre o grafo primário, incluindo refresh de token e invalidação (teste cobre)
- [ ] Suites de compose-app passam
- [ ] Gate full passa
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: full
**Commit**: `refactor(compose-app): serve game detail from the single network graph`

---

### T23: Composable de rota magro — coleta estado e despacha intents

**What**: `AuthenticatedAccessRoute` passa a obter o orchestrator via Koin, coletar estado e despachar intents; `LaunchedEffect`s de reconciliação, criação de ViewModels com factories inline e wiring de Coil/cache movem para o orchestrator/módulos; `SaqzAppDependencies` removido de vez.
**Where**: `AuthenticatedAccessRoot.kt:166-397` (reescrito magro), `SaqzAppDependencies.kt` (removido)
**Depends on**: T22
**Reuses**: orchestrator (T21), módulos (T16-T18)
**Requirement**: NAV-01, NAV-03, NAV-05

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Nenhum composable instancia NetworkClient/Api/gateway/coordinator (`rg` confirma)
- [ ] `SaqzAppDependencies.kt` não existe mais; referências removidas
- [ ] Suites `AuthenticatedAccessRootTest`, `SaqzNavHostTest`, `GroupsRouteHostTest`, `AccessViewModelTest` passam sem alteração de expectativas comportamentais (apenas wiring)
- [ ] Gate build passa (inclui compilação iOS do framework — tipos Koin não vazam)
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: build
**Commit**: `refactor(compose-app): render destinations from the orchestrator state`

---

### T24: DeferredLinkStateMachine genérico + wrappers de convite e attendance

**What**: Criar `DeferredLinkStateMachine<T, E>` parametrizado por (eventFilter, readPending/writePending, resolve, mapError, retryAfter, onResolved); `DeferredInviteStateMachine` e `DeferredAttendanceLinkStateMachine` viram wrappers finos mantendo API pública e estados atuais.
**Where**: `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/` (novo genérico; `DeferredInviteCoordinator.kt:52-188` e `attendance/share/DeferredAttendanceLinkCoordinator.kt:49-189` reescritos como wrappers)
**Depends on**: T08 (consome mapeadores de erro da data layer)
**Reuses**: corpos duplicados atuais (fonte do comportamento a preservar)
**Requirement**: DUP-01

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] start/stop/restore/setSessionReady/retry/logout/receive/clearPending/persist existem uma única vez (no genérico)
- [ ] Wrappers contêm apenas configuração (filtro, persistência, resolver, erros `INVITE_*`/`ATTENDANCE_LINK_*`, callback)
- [ ] Testes existentes dos dois coordinators passam sem alteração de expectativas; transições restore/retry/logout preservadas (edge cases da spec)
- [ ] Gate full passa
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: full
**Commit**: `refactor(groups): unify deferred link flows on one state machine`

---

### T25: FinanceCapability sealed + DraftMutationSupport; FinanceViewModel delega

**What**: Criar `sealed interface FinanceCapability { Athlete; Organizer(gateway) }` e `DraftMutationSupport` (restore/persist/retry/execute + mapeamento de erro parametrizado, por composição); `FinanceViewModel` delega e deixa de receber gateway nullable.
**Where**: `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/finance/charges/` (novos arquivos + `FinanceViewModel.kt`)
**Depends on**: T08
**Reuses**: corpo atual de `FinanceViewModel.kt:59-75,288-313`
**Requirement**: DUP-02

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Nenhum gateway nullable como flag de capacidade; `Athlete` sem gateway segue falhando `Forbidden` em mutações
- [ ] restore/persist/retry/error-mapping vivem no support; VM contém apenas regras de cobranças
- [ ] ETag permanece montado no VM (assumption confirmada)
- [ ] `FinanceViewModelTest` passa sem alteração de expectativas (construção do fixture pode mudar para a capability)
- [ ] Gate quick passa: `rtk ./gradlew :features:groups:allTests --console=plain`
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: quick
**Commit**: `refactor(finance): model charge capability as sealed config over a shared mutation support`

---

### T26: ExpenseViewModel delega ao DraftMutationSupport

**What**: Migrar `ExpenseViewModel` para o mesmo `DraftMutationSupport` + `FinanceCapability`, removendo o molde duplicado (restore/persist/retry/execute/error-mapping) e o gateway nullable.
**Where**: `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/finance/expenses/ExpenseViewModel.kt:70-85,274-299`
**Depends on**: T25
**Reuses**: `DraftMutationSupport`/`FinanceCapability` (T25)
**Requirement**: DUP-02

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Nenhuma duplicação estrutural restante entre os dois VMs (`rg`/inspeção confirma)
- [ ] `ExpenseViewModelTest` passa sem alteração de expectativas
- [ ] Atleta segue recebendo `failed(Forbidden)`; ETag permanece no VM
- [ ] Gate full passa
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: full
**Commit**: `refactor(finance): delegate expense mutations to the shared support`

---

### T27: GroupSetupScreen delega BRL ao formatter central

**What**: Remover `parseBrlCents`/`sanitizeBrlInput`/`formatBrlInput` da tela e delegar a `SaqzCurrencyFormatter` (T05); ajustar expectativas de teste para o formato canônico.
**Where**: `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/setup/GroupSetupScreen.kt:980-1009`
**Depends on**: T11 (já removeu usos locais de BRL em groups — esta task remove a fonte na tela e qualquer resto)
**Reuses**: `SaqzCurrencyFormatter`
**Requirement**: UI-01

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Nenhuma função de BRL no arquivo da tela
- [ ] Campo de taxa comporta-se igual na UI (parse/format idênticos aos centralizados)
- [ ] Suites de groups passam
- [ ] Gate quick passa: `rtk ./gradlew :features:groups:allTests --console=plain`
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: quick
**Commit**: `refactor(groups): delegate setup fee formatting to the central formatter`

---

### T28: Extrair label mappers e formatação de horas do GroupSetupScreen

**What**: Mover `modalityLabel`/`compositionLabel`/`levelLabel`/`playStyleLabel`/`weekdayLabel` para `ui/setup/SetupEnumLabels.kt` e `formatHours`/`parseHours` para `ui/setup/SetupFieldFormat.kt`; unificar `weekdayLabel` duplicado (`GroupsRouteScreens.kt:875-885`).
**Where**: `ui/setup/GroupSetupScreen.kt:908-943,967-968`; novos arquivos no mesmo pacote; `ui/GroupsRouteScreens.kt:875-885`
**Depends on**: T27
**Reuses**: implementações atuais (movimento puro)
**Requirement**: UI-02

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Nenhum mapper de enum→string no arquivo da tela; um único `weekdayLabel`
- [ ] Suites de groups passam sem alteração de expectativas
- [ ] Gate quick passa: `rtk ./gradlew :features:groups:allTests --console=plain`
- [ ] Contagem de testes não diminui

**Tests**: unit (suites existentes como contrato)
**Gate**: quick
**Commit**: `refactor(groups): extract setup enum labels and hour formatting`

---

### T29: Extrair componentes reutilizáveis para ui/setup/components/

**What**: Mover os composables privados (TopBar, Section, SetupInput, SegmentedChoice, SelectorField, Stepper, FeeEditor, MonthlyToggle, SelectionSheet, MaterialIcon) para arquivos em `ui/setup/components/`; tela passa a importá-los.
**Where**: `ui/setup/GroupSetupScreen.kt` (18+ composables privados); novos arquivos em `ui/setup/components/`
**Depends on**: T28
**Reuses**: implementações atuais (movimento puro); `MaterialIcon` duplicado 4x no módulo vira um único componente compartilhado
**Requirement**: UI-02

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Arquivo da tela contém apenas a composição de seções + hoisting
- [ ] Um único `MaterialIcon` no módulo (demais call sites migrados)
- [ ] Suites de groups passam sem alteração de expectativas
- [ ] Gate quick passa: `rtk ./gradlew :features:groups:allTests --console=plain`
- [ ] Contagem de testes não diminui

**Tests**: unit (suites existentes como contrato)
**Gate**: quick
**Commit**: `refactor(groups): extract setup screen components`

---

### T30: Regras de negócio do setup com fonte única

**What**: Eliminar duplicação de regras tela×support: capacidade 2..100, dia de vencimento default `10` e regra de editabilidade passam a ter fonte única em `GroupSetupSupport`; a tela consome.
**Where**: `ui/setup/GroupSetupScreen.kt:705,714,790,798,802,124`; `presentation/setup/GroupSetupSupport.kt:34`
**Depends on**: T29
**Reuses**: `GroupSetupSupport` existente
**Requirement**: UI-03

**Tools**: MCP: NONE — Skill: NONE

**Done when**:
- [ ] Constantes/regras existem uma única vez (support); tela referencia
- [ ] Mensagem hardcoded `"Revise este campo."` **mantida** (deferred — não alterar)
- [ ] Suites de groups passam; cobertura da regra de editabilidade/capacidade mantida ou ampliada se houver gap
- [ ] Gate build passa (última task da feature: full gate + check-credentials + check-scope)
- [ ] Contagem de testes não diminui

**Tests**: unit
**Gate**: build
**Commit**: `refactor(groups): single-source setup business rules`

---

## Phase Execution Map

```
Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7

Phase 0:  T01 ──→ T02 ──→ T03 ──→ T04
Phase 1:  T05 ──→ T06 ──→ T07 ──→ T08
Phase 2:  T09 ──→ T10 ──→ T11
Phase 3:  T12 ──→ T13 ──→ T14
Phase 4:  T15 ──→ T16 ──→ T17 ──→ T18
Phase 5:  T19 ──→ T20 ──→ T21 ──→ T22 ──→ T23
Phase 6:  T24 ──→ T25 ──→ T26
Phase 7:  T27 ──→ T28 ──→ T29 ──→ T30
```

Execution is strictly sequential — there is no intra-phase parallelism. A single agent (or batch worker) works one task at a time, in order.

**Batch packing para Execute (~7 tasks/worker, fases inteiras):**

| Batch | Fases | Tasks | Total |
| --- | --- | --- | --- |
| Worker 1 | Phase 0 + Phase 1 | T01–T08 | 8 |
| Worker 2 | Phase 2 + Phase 3 | T09–T14 | 6 |
| Worker 3 | Phase 4 + Phase 5 | T15–T23 | 9 |
| Worker 4 | Phase 6 + Phase 7 | T24–T30 | 7 |

---

## Task Granularity Check

| Task | Scope | Status |
| ---- | ----- | ------ |
| T01–T04 (fixes) | 1 mudança comportamental + 1 teste cada | ✅ Granular |
| T05, T06 | 1 formatter estendido cada | ✅ Granular |
| T07 | 1 conjunto coeso de mapeadores (mesma camada, mesmo padrão) | ✅ Granular |
| T08 | migração de consumidores de um único padrão | ✅ Granular (coeso) |
| T09, T10 | 1 fonte única + migração de seus consumidores | ✅ Granular |
| T11 | substituição de um único concern (formatação) | ✅ Granular |
| T12 | 1 enum + 2 factories | ✅ Granular |
| T13 | 2 extrações do mesmo arquivo (multipart + logger) | ⚠️ OK — coeso (prepara T14) |
| T14 | 1 pipeline unificado | ✅ Granular |
| T15 | 1 pin de dependência | ✅ Granular |
| T16, T17 | 2 módulos Koin cada (mesma camada DI) | ⚠️ OK — coeso |
| T18 | entry points + remoção do default | ✅ Granular |
| T19–T23 | 1 componente/extração cada | ✅ Granular |
| T24 | 1 genérico + 2 wrappers (mesma mudança) | ⚠️ OK — coeso |
| T25, T26 | 1 VM cada | ✅ Granular |
| T27–T30 | 1 extração cada | ✅ Granular |

## Diagram-Definition Cross-Check

| Task | Depends On (body) | Diagram Shows | Status |
| ---- | ----------------- | ------------- | ------ |
| T01–T04 | None / sequência de fase | P0 em cadeia | ✅ Match |
| T05, T06 | None | início de P1 | ✅ Match |
| T07 | None | início de P1 | ✅ Match |
| T08 | T07 | T07 → T08 | ✅ Match |
| T09, T10 | None | início de P2 | ✅ Match |
| T11 | T05, T06 | P1 → P2 (fase anterior) | ✅ Match |
| T12, T13 | None | início de P3 | ✅ Match |
| T14 | T13 | T13 → T14 | ✅ Match |
| T15 | None | início de P4 | ✅ Match |
| T16 | T15 | T15 → T16 | ✅ Match |
| T17 | T16 | T16 → T17 | ✅ Match |
| T18 | T17 | T17 → T18 | ✅ Match |
| T19, T20 | T18 | P4 → P5 | ✅ Match |
| T21 | T19, T20 | T19/T20 → T21 | ✅ Match |
| T22 | T21 | T21 → T22 | ✅ Match |
| T23 | T22 | T22 → T23 | ✅ Match |
| T24 | T08 | P1 → P6 (fase anterior) | ✅ Match |
| T25 | T08 | P1 → P6 | ✅ Match |
| T26 | T25 | T25 → T26 | ✅ Match |
| T27 | T11 | P2 → P7 | ✅ Match |
| T28 | T27 | T27 → T28 | ✅ Match |
| T29 | T28 | T28 → T29 | ✅ Match |
| T30 | T29 | T29 → T30 | ✅ Match |

Nenhuma dependência aponta para fase posterior. ✅

## Test Co-location Validation

| Task | Code Layer | Matrix Requires | Task Says | Status |
| ---- | ---------- | --------------- | --------- | ------ |
| T01–T03 | android-app adapters | unit | unit | ✅ OK |
| T04 | presentation (compose-app) | unit | unit | ✅ OK |
| T05, T06 | core/common | unit | unit | ✅ OK |
| T07, T08 | data + presentation | unit | unit | ✅ OK |
| T09, T10 | presentation (access) | unit | unit | ✅ OK |
| T11 | presentation/ui (groups) | unit | unit | ✅ OK |
| T12 | core/network + config de borda | unit | unit | ✅ OK |
| T13, T14 | core/network | unit | unit | ✅ OK |
| T15 | config (catalog/gradle) | none — build gate | none | ✅ OK |
| T16, T17 | DI wiring | unit (verify) | unit | ✅ OK |
| T18 | entry points + DI wiring | unit | unit | ✅ OK |
| T19–T23 | presentation (orchestrator/hub) | unit | unit | ✅ OK |
| T24–T26 | presentation (state machines/VMs) | unit | unit | ✅ OK |
| T27–T30 | presentation/ui (setup) | unit | unit | ✅ OK |
