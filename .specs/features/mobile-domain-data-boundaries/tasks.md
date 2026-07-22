# Mobile Domain and Data Boundaries Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the per-task cycle, test adequacy review, atomic commits, sub-agent batching, final Verifier, and discrimination sensor.

If the skill cannot be activated, STOP and tell the user. Execute exactly one task at a time, in listed order. Never weaken, delete, skip, or defer a task's tests. Preserve unrelated worktree changes.

**Design:** `.specs/features/mobile-domain-data-boundaries/design.md`  
**Status:** Approved  
**Approved:** 2026-07-22  
**Execution root:** commands marked `mobile/` run from `/Users/bruno_almeida/Private/saqz/mobile`; commands marked `repository root` run from `/Users/bruno_almeida/Private/saqz`.

## Test Coverage Matrix

> Generated from `AGENTS.md`, `README.md`, `scripts/check-gradle`, Gradle module files, and existing KMP tests including `NetworkClientTest`, `PrivateMediaNetworkTest`, `VerifiedSessionCoordinatorTest`, `NativeAccessPortsTest`, `GroupApiTest`, `NetworkErrorMappersTest`, `GameApiTest`, `FinanceApiTest`, `GameDetailViewModelTest`, and `SaqzKoinModulesTest`. Project rules require tests derived from acceptance criteria, focused gates per task, no weakening/skipping, and the aggregate only at final completion. Existing KMP style uses `kotlin.test`, `runTest`, fakes, and Ktor `MockEngine`; test-framework migration is out of scope.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
|---|---|---|---|---|
| Shared domain result/error/value types | unit | All branches and equality semantics; 1:1 with MDB-013–MDB-016; empty success and exhaustive helpers | `core/domain/src/commonTest/**/*.kt` | `rtk ./gradlew :core:domain:allTests --console=plain` |
| Core transport classification/retry | unit | Exact classification, four-call maximum, 500/1000/2000 ms schedule, early stop, every excluded category, unsafe writes, exhaustion, cancellation during call/backoff | `core/network/src/commonTest/**/*.kt` | `rtk ./gradlew :core:network:allTests --console=plain` |
| Feature domain models/contracts/mappers without frameworks | unit | Constructor/value invariants, all enum/value mappings, validation details, version/pagination/retry values, every feature error branch | `features/*/domain/src/commonTest/**/*.kt` | `rtk ./gradlew :features:<feature>:domain:allTests --console=plain` |
| Feature data implementations | unit + transport integration via MockEngine | Success mapping, exact request path/method/body/headers, every supported shared/feature error, malformed/missing/optional/versioned fields, ETag/cursor semantics, retry eligibility/exclusions, cancellation and credential-safe output | `features/*/data/src/commonTest/**/*.kt` | `rtk ./gradlew :features:<feature>:data:allTests --console=plain` |
| Feature presentation | unit + existing Compose UI tests | Preserve loading/success/empty/validation/auth/connectivity/retry states and effects; fakes implement domain contracts; generic localized validation fallback; zero transport assertions | `features/*/src/commonTest/**/*.kt` | `rtk ./gradlew :features:<feature>:allTests --console=plain` |
| App composition/cross-feature coordination | unit | Koin graph resolves domain interfaces to data implementations; Access-to-Groups translation preserves exact safe values; no DTO/data implementation enters state contracts | `compose-app/src/commonTest/**/*.kt` | `rtk ./gradlew :compose-app:allTests --console=plain` |
| Android platform adapters touched by port moves | focused Android unit | Adapter implements relocated domain port and preserves callback/result behavior; no provider type leaks | `android-app/src/test/**/*Test.kt` | `rtk ./gradlew :android-app:testDevDebugUnitTest --tests '<task pattern>' --console=plain` |
| Gradle/module configuration | none | Focused compile of only newly declared/changed modules; dependency allowlist checked later by architecture gate | `settings.gradle.kts`, `**/build.gradle.kts` | `rtk ./gradlew <changed modules>:compileAndroidMain --console=plain` |
| Repository architecture scripts | shell contract | Positive baseline plus negative mutations for every forbidden edge/import; command-contract tests use scratch repositories | `tests/scripts/check-mobile-boundaries.test.sh`, related script tests | `rtk tests/scripts/check-mobile-boundaries.test.sh` |
| Final integration | aggregate + independent verification | All repository gates, Android/iOS contracts, zero forbidden imports/dependencies, every MDB has evidence, discrimination mutants killed | `.specs/features/mobile-domain-data-boundaries/validation.md` | `rtk scripts/check-all` |

## Gate Check Commands

> Generated from the current Gradle graph and repository scripts, then constrained by the user's focused-testing decision. Commands below are patterns; every task supplies its exact command. `allTests` is allowed only for the module/layer currently changed. `scripts/check-gradle`, `scripts/check-ios`, and `scripts/check-all` are forbidden before T34.

| Gate Level | When to Use | Command |
|---|---|---|
| Quick | One domain, data, network, presentation, composition, Android adapter, or script layer | The exact single-layer command in the task, such as `rtk ./gradlew :features:groups:data:allTests --console=plain` |
| Full focused | A task changes a feature boundary plus its immediate composition/platform consumer | Only the explicitly listed changed modules/tests in that task; never a repository aggregate |
| Build focused | Config-only task or end of a phase | `rtk ./gradlew <changed modules>:compileAndroidMain [<changed modules>:allTests] --console=plain` |
| Final aggregate | T37 only | From repository root: `rtk scripts/check-all` |

## Global Task Constraints

- Write tests first from the listed MDBs and task outcomes; do not derive assertions from the implementation.
- All moved production code carries its existing behavioral tests in the same task. A later task may add coverage, but never serves as deferred coverage for earlier production code.
- Keep endpoint paths, methods, headers, request identifiers, bodies, ETags, cursors, retry-after values, token acquisition, and explicit retry actions unchanged.
- Automatic retry means one initial call plus at most three retries at 500 ms, 1 s, and 2 s. Only connectivity, timeout, and 5xx are retryable; only reads and existing idempotency-key writes are eligible.
- Authentication, authorization, validation, conflict, not-found, 429/rate-limit, invalid-response, payload-too-large, unknown, unsafe writes, and cancellation receive zero automatic retries.
- `CancellationException` is rethrown from transport, delay, mapping, and gateway boundaries.
- Use the current KMP test stack. Do not add JUnit5, AssertK, Turbine, mock libraries, a database, cache, new retry library, or a Gradle convention plugin.
- Koin changes are limited to binding relocated implementations to domain contracts in the existing composition root; do not redesign DI placement.
- Do not run `scripts/check-gradle`, `scripts/check-ios`, or `scripts/check-all` in T01–T36. T37 runs `scripts/check-all` once and does not invoke the other aggregates separately.
- Every task ends with its focused gate, an evidence-or-zero adequacy table, and exactly one Conventional Commit containing no unrelated changes.

## Execution Plan

Phases and tasks are strictly sequential.

### Phase 1: Shared foundation and module graph

```text
T01 -> T02 -> T03 -> T04 -> T05
```

### Phase 2: Access boundary

```text
T05 -> T06 -> T07 -> T08 -> T09 -> T10
```

### Phase 3: Groups profile, membership, roles and invites

```text
T10 -> T11 -> T12 -> T13 -> T14 -> T15 -> T16
```

### Phase 4: Groups photo, deferred links and attendance sharing

```text
T16 -> T17 -> T18 -> T19 -> T20 -> T21 -> T22
```

### Phase 5: Games and attendance

```text
T22 -> T23 -> T24 -> T25 -> T26 -> T27 -> T28 -> T29 -> T30
```

### Phase 6: Finance

```text
T30 -> T31 -> T32 -> T33 -> T34
```

### Phase 7: Integration, architecture gate and final aggregate

```text
T34 -> T35 -> T36 -> T37
```

## Task Breakdown

### T01: Add the shared framework-free domain result/error module

**Status:** Complete — `edc58b5`; focused gate passed.

**What:** Create `:core:domain` with the approved typed result, empty success, helpers, validation details, data errors, and genuinely shared opaque identifiers.

**Where:** `settings.gradle.kts`; `core/domain/build.gradle.kts`; `core/domain/src/commonMain/**`; `core/domain/src/commonTest/**`.

**Depends on:** None  
**Reuses:** Pure KMP target shape from `core/common/build.gradle.kts`.  
**Requirements:** MDB-003, MDB-013–MDB-016.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Use `SaqzError`, `SaqzResult.Success(value)`, `SaqzResult.Failure(error)`, and `EmptyResult<E>` as approved in Design.
- Implement only `map`, `mapError`, `onSuccess`, `onFailure`, and `asEmptyResult`; all branches must return typed results and helpers must not catch exceptions.
- Implement the eleven approved `DataError` outcomes and `ValidationDetails(globalMessages, fieldMessages)`.
- Keep shared identifiers limited to cross-feature values proven necessary, initially `GroupId`; do not move feature roles/models here.
- Production dependencies must be Kotlin standard library only.

**Done when:**

- [ ] `:core:domain` compiles for Android, iOS arm64, and iOS simulator targets.
- [ ] No Compose/Koin/Ktor/serialization/database/platform import or dependency exists.
- [ ] Result helpers are exhaustive and preserve the exact success/error payload.
- [ ] Validation details preserve ordered global messages and every field message.
- [ ] Minimum 16 task-specific unit cases pass: success/failure construction (2), five helpers across both branches (10), empty result (1), validation equality (1), data-error exhaustiveness fixture (1), shared ID equality (1).
- [ ] Focused gate exits 0 and no relevant pre-existing test is removed/skipped.

**Tests:** unit, co-located in `core/domain`; minimum 16.  
**Gate:** Quick — from `mobile/`: `rtk ./gradlew :core:domain:allTests --console=plain`.  
**Commit:** `feat(mobile-domain): add typed domain result foundation`

### T02: Separate known connectivity from unknown core transport failures

**Status:** Complete — `3286f22`; focused gate passed.

**What:** Refine `NetworkError` and `NetworkClient` so retry eligibility can distinguish connectivity from unknown exceptions without changing existing timeout, HTTP, problem, payload, logging, or cancellation behavior.

**Where:** `core/network/src/commonMain/.../NetworkModels.kt`; `NetworkClient.kt`; focused `core/network/src/commonTest/**`.

**Depends on:** T01  
**Reuses:** Existing bounded error parsing/logging and cancellation tests.  
**Requirements:** MDB-015, MDB-018, MDB-019, MDB-026.  
**Tools:** local filesystem/shell; skills `android-error-handling`, `android-data-layer`, `android-testing`.

**Implementation instructions:**

- Add distinct transport outcomes for known connectivity and unknown failures; do not infer unknown as connectivity.
- Preserve explicit timeout and `CancellationException` catch ordering.
- Identify supported connectivity exceptions from APIs already present in the dependency set; if classification is uncertain, stop and consult official Ktor documentation before coding.
- Keep secret/error-body/correlation logging protections unchanged.

**Done when:**

- [ ] Known connectivity maps only to the connectivity transport error.
- [ ] An unrelated thrown exception maps to unknown and is credential-safe.
- [ ] Timeout, cancellation, HTTP status, structured problem, malformed body, payload-too-large, no-content, and binary/media behavior remain exact.
- [ ] Minimum 8 focused cases cover connectivity, unknown, timeout, cancellation, 5xx structured/unstructured, credential-safe failure, and media path classification.
- [ ] Existing `NetworkClientTest` and `PrivateMediaNetworkTest` cases remain present.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 8 new/updated focused cases plus existing core-network suite.  
**Gate:** Quick — `rtk ./gradlew :core:network:allTests --console=plain`.  
**Commit:** `refactor(network): distinguish connectivity from unknown failures`

### T03: Implement the deterministic bounded transport retry helper

**Status:** Complete — `1d3bf55`; focused gate passed.

**What:** Add a reusable retry mechanism beside `NetworkResult`, invoked explicitly by feature data code with `RetrySafety.Never`, `Read`, or `IdempotentWrite`.

**Where:** `core/network/src/commonMain/**/TransportRetry.kt`; `core/network/src/commonTest/**/TransportRetryTest.kt`.

**Depends on:** T02  
**Reuses:** Refined `NetworkError`, coroutine delay, existing `NetworkResult`.  
**Requirements:** MDB-018, MDB-021, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Default delays are exactly `[500, 1000, 2000]` milliseconds and precede attempts 2–4.
- Retry only connectivity, timeout, and any structured/unstructured 5xx result.
- Require data callers to pass safety explicitly; never infer an idempotent write from an HTTP verb alone.
- Inject the suspend delay function for virtual-time tests; do not add a retry dependency.
- Return the last failure after exhaustion; never wrap or replace it.

**Done when:**

- [ ] Eligible persistent failure performs exactly 4 calls and delays `[500, 1000, 2000]`.
- [ ] Success on attempts 1, 2, 3, and 4 stops immediately with exact payload and expected consumed delays.
- [ ] Connectivity, timeout, structured 5xx, and raw 5xx are retried.
- [ ] All eleven excluded outcomes, `RetrySafety.Never`, and unsafe writes make exactly one call and no delay.
- [ ] Cancellation from the call and from each backoff point propagates without a failure value.
- [ ] Minimum 22 task-specific cases pass: schedule/exhaustion (2), four early-success positions (4), four retryable forms (4), eleven exclusions/safety groups (11), cancellation call/backoff (at least 2; parameterization allowed but assertions must name every outcome).
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 22 named/parameterized outcome cases.  
**Gate:** Quick — `rtk ./gradlew :core:network:allTests --console=plain`.  
**Commit:** `feat(network): add bounded data retry mechanism`

### T04: Declare Access domain and data child modules

**Status:** Complete — `ae57e2b`; focused compile gate passed.

**What:** Add empty compilable `:features:access:domain` and `:features:access:data` KMP modules without changing current Access behavior or dependencies yet.

**Where:** `settings.gradle.kts`; `features/access/domain/build.gradle.kts`; `features/access/data/build.gradle.kts`.

**Depends on:** T03  
**Reuses:** `:core:domain`, existing Access target versions, current KMP module style.  
**Requirements:** MDB-001–MDB-005.  
**Tools:** local filesystem/shell; skill `android-module-structure`.

**Implementation instructions:**

- Access domain depends only on `:core:domain` plus `kotlin("test")` in tests.
- Access data depends on Access domain, core domain, core network, Ktor/serialization and test-only MockEngine/coroutines.
- Do not add Compose, resources, Koin, lifecycle, or presentation dependencies to either child.
- Do not remove dependencies from the existing presentation module in this task.

**Done when:**

- [ ] Both modules are included once and have unique Android namespaces.
- [ ] Both compile Android main; iOS targets are declared consistently.
- [ ] Dependency declarations match the Design allowlist exactly.
- [ ] No production source is moved in this config-only task.
- [ ] Focused build gate exits 0.

**Tests:** none — configuration/build gate only.  
**Gate:** Build focused — `rtk ./gradlew :features:access:domain:compileAndroidMain :features:access:data:compileAndroidMain --console=plain`.  
**Commit:** `build(access): declare domain and data modules`

### T05: Declare Groups domain and data child modules

**Status:** Complete — `605fd70`; focused compile gate passed.

**What:** Add empty compilable `:features:groups:domain` and `:features:groups:data` KMP modules without changing Groups behavior or dependencies yet.

**Where:** `settings.gradle.kts`; `features/groups/domain/build.gradle.kts`; `features/groups/data/build.gradle.kts`.

**Depends on:** T04  
**Reuses:** `:core:domain`, existing Groups target versions, current KMP module style.  
**Requirements:** MDB-001–MDB-005.  
**Tools:** local filesystem/shell; skill `android-module-structure`.

**Implementation instructions:**

- Groups domain depends only on `:core:domain` plus test dependencies.
- Groups data depends on Groups domain, core domain, core network, Ktor/serialization/datetime only where DTO mapping requires them.
- No Compose/Koin/lifecycle/presentation dependency is allowed.
- Do not move current Groups sources yet.

**Done when:**

- [ ] Both modules are included once with unique namespaces and Android/iOS targets.
- [ ] Dependency declarations match the Design allowlist.
- [ ] Existing Groups module remains behaviorally untouched.
- [ ] Focused build gate exits 0.

**Tests:** none — configuration/build gate only.  
**Gate:** Build focused — `rtk ./gradlew :features:groups:domain:compileAndroidMain :features:groups:data:compileAndroidMain --console=plain`.  
**Commit:** `build(groups): declare domain and data modules`

### T06: Define Access session domain contracts and models

**Status:** Complete — `99b9a28`; focused gate passed.

**What:** Create framework-free Access session/user/membership models, bootstrap capability, access errors, and selected-group values in Access domain.

**Where:** `features/access/domain/src/commonMain/**/session/**`; co-located domain tests.

**Depends on:** T05  
**Reuses:** Current `SessionDto`, `SessionUserDto`, `SessionMembershipDto`, `SessionGateway` semantics as transport evidence only; `SaqzResult` and `GroupId`.  
**Requirements:** MDB-002–MDB-004, MDB-007, MDB-010–MDB-017, MDB-022.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Domain names must not end in `Dto` and contain no serialization or HTTP fields.
- `SessionGateway.bootstrap()` returns `SaqzResult<AccessSession, AccessError>`.
- Model email verification, unauthenticated/forbidden/email-not-verified, validation and data failures exhaustively enough for current presentation outcomes.
- Session membership may reference `GroupId`, but must not import Groups roles/models; preserve role as an Access-owned safe session value until app-level translation.

**Done when:**

- [ ] Domain session shapes preserve user ID/email/display name and membership group ID/name/role semantics.
- [ ] Every current bootstrap outcome has one typed Access error or success.
- [ ] No network/serialization/Compose/Koin/platform import exists.
- [ ] Minimum 12 domain cases cover value equality, nullable email, empty membership, multiple memberships, role value preservation, success, every Access error branch, and validation details.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 12.  
**Gate:** Quick — `rtk ./gradlew :features:access:domain:allTests --console=plain`.  
**Commit:** `feat(access-domain): define session contracts`

### T07: Move session transport into Access data and map it to domain

**Status:** Complete — `0cab9d0`; focused gate passed.

**What:** Move feature-specific session DTO/API behavior out of `:core:network` into Access data, implement the domain `SessionGateway`, and apply eligible read/idempotent retry semantics.

**Where:** `features/access/data/src/commonMain/**/session/**`; `features/access/data/src/commonTest/**`; keep the existing `core/network/.../SessionApi.kt` compatibility surface unchanged until its remaining consumers migrate in T09–T10.

**Depends on:** T06  
**Reuses:** Existing endpoint `PUT api/session`, `AuthenticatedNetworkClient`, DTO field semantics, transport tests, retry helper.  
**Requirements:** MDB-002, MDB-007–MDB-009, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Keep the endpoint, method, token behavior and response shape exact.
- Decide retry safety from the existing idempotency guarantee of session bootstrap; if no idempotency key/contract is present, use `Never` even though the method is PUT.
- Map DTO to a complete `AccessSession`; malformed required fields return `InvalidResponse`.
- Map safe email-not-verified semantics in data; do not expose API code/status/correlation ID.
- Rethrow cancellation from request, retry delay, decode and mapping.
- Treat the still-consumed core session surface as a time-boxed compatibility seam: do not add behavior to it, and remove it in T10 after presentation and app composition use the new domain/data path.

**Done when:**

- [ ] Access data owns the new session DTO/API implementation and mapping; the legacy core surface remains behaviorally unchanged only for consumers scheduled to migrate in T09–T10.
- [ ] Domain consumer receives only `AccessSession` or typed `AccessError`.
- [ ] Exact PUT path/token and payload-less request are asserted.
- [ ] Minimum 18 data cases cover success, nullable email, empty/multiple memberships, malformed required fields, 401, email-not-verified 403, other 403, validation, 404, 5xx, timeout, connectivity, unknown, retry eligible/ineligible behavior, final exhaustion, and cancellation.
- [ ] Failure `toString()` evidence contains no token/correlation secret.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine integration; minimum 18.  
**Gate:** Quick — `rtk ./gradlew :features:access:data:allTests --console=plain`.  
**Commit:** `refactor(access-data): own session transport and mapping`

### T08: Move native Access port contracts into Access domain

**Status:** Complete — `f20afe4`; focused gate passed (`5f11e9a` records B4).

**What:** Relocate provider-neutral native auth/link/share/local-state contracts and result types from the presentation module into Access domain, updating Android adapters without changing callbacks or platform behavior.

**Where:** `features/access/domain/src/commonMain/**/port/**`; remove old presentation-owned port file; `android-app/src/main/**/access/**`; focused Android tests; composition imports needed to compile.

**Depends on:** T07  
**Reuses:** Existing `NativeAccessPorts.kt`, `NativeAccessPortsTest.kt`, Android adapter tests and callback shapes.  
**Requirements:** MDB-003, MDB-004, MDB-013, MDB-018, MDB-022.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Preserve provider-neutral callback interfaces required by Swift/Android; this task does not redesign them into suspending APIs.
- Keep Firebase/Branch/platform SDK types confined to platform adapters.
- Make the presentation module expose Access domain transitively only as required by the umbrella framework; never expose Access data.
- Update Android imports/signatures only; iOS source changes are allowed only if compilation demands them and will receive final iOS coverage at T34.

**Done when:**

- [ ] All native port contracts compile from Access domain and contain no platform import.
- [ ] Android auth/link/local-state adapters implement the relocated contracts with unchanged observable results.
- [ ] Existing port contract tests are moved co-located and all remain present.
- [ ] Minimum 12 port/domain cases and focused Android adapter cases pass; include cancellation, success/failure/cancelled auth, token, value and operation callbacks.
- [ ] No Access data dependency is added to Android platform code beyond composition needs.
- [ ] Focused gate exits 0.

**Tests:** unit + focused Android unit; minimum 12 common cases plus existing focused adapter tests.  
**Gate:** Full focused — `rtk ./gradlew :features:access:domain:allTests :android-app:testDevDebugUnitTest --tests 'br.com.saqz.androidapp.access.*' --console=plain`.  
**Commit:** `refactor(access-domain): own native access ports`

### T09: Switch Access presentation to domain-only session and error contracts

**Status:** Complete — `3819db9`; focused gate passed.

**What:** Replace `NetworkResult`, `NetworkError`, session DTO/API-problem inspection and old port imports in Access presentation with Access domain models/results/errors while preserving every state and screen outcome.

**Where:** `features/access/src/commonMain/**`; `features/access/src/commonTest/**`; `features/access/build.gradle.kts`.

**Depends on:** T08  
**Reuses:** Existing state machines, validators, UI resources and behavioral tests; domain fakes instead of transport fakes.  
**Requirements:** MDB-001, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`, `android-module-structure`.

**Implementation instructions:**

- Preserve state/intent/effect types and user copy; only replace boundary types and exhaustive mappings.
- `SessionAccessState.Ready` holds `AccessSession`, never `SessionDto`.
- Email-not-verified behavior is selected from typed `AccessError`, not status/code inspection.
- For validation with no global message, map to exactly one existing/new localized generic Access validation string.
- Remove core-network, Ktor and serialization production dependencies from the Access presentation build file once imports are zero.

**Done when:**

- [ ] `rg` finds zero Access presentation imports of Access data, `br.com.saqz.network`, Ktor or serialization.
- [ ] Existing authentication/session validators and Compose UI behavior remain exact.
- [ ] Presentation `when` expressions exhaustively handle typed outcomes without raw fallbacks.
- [ ] Minimum 20 behavior cases pass, including current verified/unverified/name/bootstrap/logout/retry cases, connectivity/auth/validation outcomes, and generic validation fallback.
- [ ] Existing Access tests are not deleted or weakened and use domain fakes.
- [ ] Focused gate exits 0.

**Tests:** unit + existing Compose UI; minimum 20 relevant behavior cases, retaining all pre-existing Access tests.  
**Gate:** Quick — `rtk ./gradlew :features:access:allTests --console=plain`.  
**Commit:** `refactor(access): consume domain-only contracts`

### T10: Rebind Access data in compose-app and close the Access boundary

**Status:** Complete — `065d2f3`; focused gate and boundary scans passed (`46fe41d` records B5–B8).

**What:** Bind the Access data implementation to the Access domain contract in the existing Koin composition root, update Access app consumers to domain models, and remove all completed Access compatibility seams.

**Where:** `compose-app/src/commonMain/**/di/NetworkModule.kt`, Access DI/composition files and Access consumers; `compose-app/src/commonTest/**`; Access module build files; the minimal Groups-owned session-selection input and its existing presentation consumers required to eliminate the shared core DTO.

**Depends on:** T09  
**Reuses:** Existing Koin modules and `SaqzKoinModulesTest`; do not relocate Koin modules.  
**Requirements:** MDB-001–MDB-006, MDB-020, MDB-022, MDB-024.  
**Tools:** local filesystem/shell; skills `android-di-koin`, `android-module-structure`, `android-testing`.

**Implementation instructions:**

- `compose-app` may construct Access data and bind `SessionGateway`, but state/orchestrator contracts may see only Access domain types.
- Preserve one `AuthenticatedNetworkClient`, token provider, invalidator and session state-machine instance semantics.
- Keep `:compose-app` umbrella exports stable; Access data must not be exported to iOS.
- Delete only Access adapters/import aliases made obsolete by T06–T09.
- Remove the legacy core-network `SessionDto`, `SessionGateway`, `SessionApi` and endpoint implementation after all Access presentation and app consumers have migrated; leave no compatibility alias behind.
- Replace Groups presentation's use of the core Access DTO with a minimal Groups-owned selection input (group ID/name/role only), and translate `AccessSession` to that input in `compose-app`; Groups must not depend on Access domain or data.

**Done when:**

- [ ] Koin resolves one Access data implementation as the domain `SessionGateway`.
- [ ] Access presentation has no data/network dependency and Access data has no presentation dependency.
- [ ] App state/orchestrators contain no `SessionDto`, `NetworkResult`, `NetworkError` or Access data type.
- [ ] Core network contains no Access DTO, Access gateway, or session endpoint.
- [ ] Groups selection state/navigation/UI contains no Access/core session type, and the app-level translator is the only bridge from the Access session to the Groups-owned input.
- [ ] Minimum 8 composition cases cover singleton transport, gateway binding, invalidator binding, machine resolution, bootstrap graph, reload graph, domain session injection and absence of implementation in state.
- [ ] Access domain/data/presentation and compose-app focused gates exit 0; no global gate runs.

**Tests:** composition unit plus focused Access modules; minimum 8 composition cases.  
**Gate:** Full focused — `rtk ./gradlew :features:access:domain:allTests :features:access:data:allTests :features:access:allTests :compose-app:allTests --console=plain`.  
**Commit:** `refactor(compose): close access layer boundary`

### T11: Define Groups profile/setup domain models and capability contracts

**What:** Move group profile, setup, venue, schedule, finance-default, role, version and create/update command semantics into Groups domain without transport annotations.

**Where:** `features/groups/domain/src/commonMain/**/group/**`; co-located domain tests; source candidates currently in `groups/model` and `groups/data/GroupApi.kt`.

**Depends on:** T10  
**Reuses:** Current setup validators/contracts, `GroupId`, `VersionToken`, `SaqzResult`.  
**Requirements:** MDB-002–MDB-004, MDB-007–MDB-014.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Domain models use business names without `Dto`; preserve nullable/optional semantics only where current accepted behavior allows.
- Define capability-shaped group/profile gateways returning typed Groups errors.
- Preserve exact version token as opaque value; no header or HTTP terminology in contracts.
- Keep current client-side setup validation behavior unchanged.

**Done when:**

- [ ] All profile/setup values and commands compile without framework dependencies.
- [ ] Create/read/update/profile capabilities expose domain commands and typed results only.
- [ ] Required/optional fields and enum values are explicit.
- [ ] Minimum 20 domain cases cover all group enums, profile optionality, venue/slot values, finance defaults, create/update commands, version preservation, validation and feature errors.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 20.  
**Gate:** Quick — `rtk ./gradlew :features:groups:domain:allTests --console=plain`.  
**Commit:** `feat(groups-domain): define profile and setup contracts`

### T12: Implement Groups profile/setup DTO mapping and data gateways

**What:** Move group/profile request/response DTOs and Ktor operations into Groups data, implement domain gateways, and map transport metadata/errors/retries.

**Where:** `features/groups/data/src/commonMain/**/group/**`; data tests migrated from `GroupApiTest`, `GroupProfileApiTest`, `NetworkErrorMappersTest` subsets.

**Depends on:** T11  
**Reuses:** Existing endpoint/method/body/header tests, `AuthenticatedNetworkClient`, retry helper.  
**Requirements:** MDB-002, MDB-007–MDB-009, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Keep all existing group/profile endpoints, bodies, ETag requirements and idempotency request values exact.
- Response DTO mappers return complete domain models or typed invalid response.
- Only reads and writes already carrying the accepted idempotency key opt into retry.
- Map structured validation field messages and leave absent global messages empty for presentation fallback.

**Done when:**

- [ ] No profile/setup DTO or Ktor type is declared outside Groups data.
- [ ] Domain gateway results contain no response metadata/header/status/code/correlation object.
- [ ] Minimum 28 cases retain all current endpoint/payload/ETag assertions and add malformed required field, optional old-version field, each shared failure category, feature conflict/validation, retry success/exhaustion/exclusion, cancellation and secret safety.
- [ ] Existing `GroupApiTest`/`GroupProfileApiTest` assertions are moved, not weakened.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine integration; minimum 28 relevant cases.  
**Gate:** Quick — `rtk ./gradlew :features:groups:data:allTests --console=plain`.  
**Commit:** `refactor(groups-data): map profile transport to domain`

### T13: Switch Groups profile/setup/selection presentation to domain contracts

**Status:** Complete — focused Groups presentation gate passed.

**What:** Convert group setup, selection, administration, route policy and profile UI consumers to Groups domain models/gateways/errors without changing state, effects, copy, drafts or retry behavior.

**Where:** `features/groups/src/commonMain/**/{setup,GroupSelection*,GroupAdministration*,GroupRoutePolicy*,ui/GroupContext*,ui/GroupsRoute*}` and corresponding tests.

**Depends on:** T12  
**Reuses:** Existing state machines, setup tests, UI tests and draft ports.  
**Requirements:** MDB-001, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Replace only boundary types; preserve current intents/state/effects and visual strings.
- Domain validation fields reach presentation; absent global message yields one generic localized message.
- Conflict retains exact version token and reload/retry operation.
- No raw status/code or data mapper call remains in presentation.

**Done when:**

- [ ] Targeted profile/setup sources import only Groups domain/presentation/core presentation types.
- [ ] Loading, success, empty/incomplete profile, validation, forbidden, conflict, connectivity and retry outcomes remain exact.
- [ ] Minimum 28 targeted behavior cases pass, retaining all existing setup/selection/administration/UI assertions.
- [ ] Fakes implement Groups domain gateways and return `SaqzResult`.
- [ ] Focused gate exits 0.

**Tests:** unit + existing Compose UI; minimum 28 targeted cases.  
**Gate:** Quick — `rtk ./gradlew :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): consume profile domain contracts`

### T14: Define membership, role and invite domain contracts

**Status:** Complete — 14 domain cases passed.

**What:** Add Groups domain models, commands, gateway capabilities and typed errors for memberships, role changes, invite rotation/expiry/redemption and safe attempt limits.

**Where:** `features/groups/domain/src/commonMain/**/membership/**`; co-located tests.

**Depends on:** T13  
**Reuses:** Current `RolesInvitesGateway`, role/membership/invite DTO semantics, `GroupId`, safe retry delay.  
**Requirements:** MDB-002–MDB-004, MDB-007–MDB-017.  
**Tools:** local filesystem/shell; skills `android-error-handling`, `android-testing`, `android-module-structure`.

**Implementation instructions:**

- Model roles/memberships/invite URL/redeemed membership without serialization.
- Preserve `InvalidOrExpired` and `AttemptLimit(retryAfterSeconds)` as domain feature errors.
- Domain contracts describe list/change/rotate/expire/redeem capabilities, not URLs or status codes.
- Ensure role values are owned only by Groups domain; app translation will map Access session role text later.

**Done when:**

- [ ] Membership/role/invite contracts contain no data/network/framework type.
- [ ] Safe attempt limit preserves nullable retry delay without exposing code/status.
- [ ] Minimum 14 cases cover every role, membership value, invite URL, redeemed result, invalid/expired, attempt-limit with/without delay, validation, forbidden, not-found and data failure wrappers.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 14.  
**Gate:** Quick — `rtk ./gradlew :features:groups:domain:allTests --console=plain`.  
**Commit:** `feat(groups-domain): define membership and invite contracts`

### T15: Implement membership, role and invite data mapping

**Status:** Complete — 27 data and transport cases passed.

**What:** Move roles/invites DTOs and API implementation into Groups data, implement domain contracts, and preserve safe structured failures and retry eligibility.

**Where:** `features/groups/data/src/commonMain/**/membership/**`; corresponding data tests migrated from `RolesInvitesApiTest` and error-mapper tests.

**Depends on:** T14  
**Reuses:** Existing exact endpoints/payloads, problem parsing, attempt-limit mapping and retry helper.  
**Requirements:** MDB-007–MDB-009, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Keep list/change/rotate/expire/redeem endpoint semantics exact.
- 429 attempt-limit is never automatically retried and preserves safe `retryAfterSeconds` only in feature error.
- Invalid/expired and role/authorization/validation failures map exhaustively.
- Apply retry only to reads or writes proven idempotent by their existing key/contract.

**Done when:**

- [ ] DTOs/serialization and transport codes stay in Groups data.
- [ ] Minimum 24 cases retain current request/response assertions and cover every feature/shared failure, malformed payload, retry eligibility/exclusion, 429 zero-retry call count, exhaustion, cancellation and secret safety.
- [ ] Exact attempt-limit delay survives while raw invite code/correlation does not.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine integration; minimum 24.  
**Gate:** Quick — `rtk ./gradlew :features:groups:data:allTests --console=plain`.  
**Commit:** `refactor(groups-data): map membership and invite transport`

### T16: Switch membership, role and invite presentation to domain contracts

**Status:** Complete — focused Groups presentation gate passed.

**What:** Convert deferred invite, invite tool, group administration membership flows and membership UI to domain models/results/errors while preserving rate-limit and retry UX.

**Where:** `features/groups/src/commonMain/**/{DeferredInvite*,InviteTool*,GroupAdministration*,ui/MembershipInvite*}` and tests.

**Depends on:** T15  
**Reuses:** Existing state machines/UI tests and localized invite strings.  
**Requirements:** MDB-001, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Preserve deferred-link single-flight, pending-link clearing, exact retry delay display and retry-disabled behavior.
- Remove `toDeferredLinkFailure` and raw `NetworkError` handling from presentation.
- Use the typed domain attempt-limit error directly and generic localized validation fallback when needed.

**Done when:**

- [ ] Targeted presentation has zero Groups data/network imports.
- [ ] Invalid/expired, attempt-limit, unavailable, success, duplicate/single-flight and explicit retry states match existing behavior.
- [ ] Minimum 28 targeted state/UI cases pass; existing invite/administration tests remain present and use domain fakes.
- [ ] Focused gate exits 0.

**Tests:** unit + existing Compose UI; minimum 28 targeted cases.  
**Gate:** Quick — `rtk ./gradlew :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): consume membership and invite domain contracts`

### T17: Define group-photo domain and native capability contracts

**What:** Create Groups domain models, remote capability, media outcomes, version values, and provider-neutral native photo selection/encoding/preview contracts.

**Where:** `features/groups/domain/src/commonMain/**/photo/**`; move relevant contracts from `groups/port/GroupPhotoPorts.kt`; co-located tests.

**Depends on:** T16  
**Reuses:** Current `GroupPhotoGateway`, `GroupPhotoReceipt`, `GroupPhotoReadResult`, photo port contracts and `VersionToken`.  
**Requirements:** MDB-002–MDB-004, MDB-007–MDB-014, MDB-022.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Domain remote models contain bytes/content metadata only when presentation behavior needs them; no Ktor channel/header/content-type class may cross.
- Model available/not-modified, upload/remove receipt, source handle, crop/encoding/selection outcomes explicitly.
- Keep native ports provider-neutral and callback-based where Swift interoperability requires it.

**Done when:**

- [ ] Photo contracts/models compile in Groups domain with no network/serialization/platform import.
- [ ] Version/not-modified/media-limit/failure outcomes are exhaustive and transport-independent.
- [ ] Minimum 16 domain/port cases cover every source, crop, selection, encoding, preview, available/not-modified, receipt and error outcome.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 16.  
**Gate:** Quick — `rtk ./gradlew :features:groups:domain:allTests --console=plain`.  
**Commit:** `feat(groups-domain): define photo capability contracts`

### T18: Implement group-photo transport and domain mapping

**What:** Move photo API implementation/transport models into Groups data and implement upload/read/remove domain capabilities with exact media limits, ETags, 304 behavior, retries and cancellation.

**Where:** `features/groups/data/src/commonMain/**/photo/**`; migrated `GroupPhotoApiTest` coverage.

**Depends on:** T17  
**Reuses:** Core binary/media network functions, current routes/multipart behavior and retry helper.  
**Requirements:** MDB-007–MDB-009, MDB-012, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Preserve multipart field/file/content behavior, cache headers, ETags, 304 not-modified and maximum body limits.
- Convert headers/content types/byte channels into domain values before returning.
- Reads are retryable; upload/remove retry only when the current request has an accepted idempotency key. Missing eligibility means one call.
- Cancellation must close/cancel owned media sources and propagate.

**Done when:**

- [ ] Photo DTO/transport/media implementation exists only in Groups data/core network.
- [ ] Missing required ETag/content type maps to typed invalid response.
- [ ] Minimum 22 cases preserve all current upload/read/remove/304/ETag/size/secret assertions and add retry schedule/exclusions, exhaustion and cancellation-at-call/backoff/source.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine/media integration; minimum 22.  
**Gate:** Quick — `rtk ./gradlew :features:groups:data:allTests --console=plain`.  
**Commit:** `refactor(groups-data): map photo transport to domain`

### T19: Switch group-photo presentation and Android adapters to domain contracts

**What:** Convert photo coordinator/list loader/editor/app consumers and Android photo adapters to Groups domain contracts without changing selection, crop, preview, caching or retry behavior.

**Where:** `features/groups/src/commonMain/**/photo/**`; relevant `compose-app` photo consumers; `android-app/src/main/**/groups/photo/**`; focused common/Android tests.

**Depends on:** T18  
**Reuses:** Existing photo coordinator, editor, loader, Coil preview/cache and Android adapter tests.  
**Requirements:** MDB-001, MDB-004, MDB-010, MDB-013–MDB-017, MDB-020, MDB-022.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Replace DTO/data/network types only; preserve UI state/effects and existing file lifecycle.
- Android adapters implement relocated domain ports; Android SDK/file-provider types remain platform-only.
- No photo data implementation may enter presentation state or app runtime contracts.

**Done when:**

- [ ] Targeted common presentation/app sources have zero photo data/network imports.
- [ ] Selection/cancel/crop/encode/upload/read/not-modified/remove/retry/cache outcomes remain exact.
- [ ] Minimum 20 common behavior cases plus all focused Android photo adapter/request/file tests pass.
- [ ] Focused gate exits 0; no iOS aggregate runs before T37.

**Tests:** common unit/Compose plus focused Android unit; minimum 20 common cases and retained adapter cases.  
**Gate:** Full focused — `rtk ./gradlew :features:groups:allTests :compose-app:allTests :android-app:testDevDebugUnitTest --tests 'br.com.saqz.androidapp.groups.photo.*' --console=plain`.  
**Commit:** `refactor(groups): consume photo domain contracts`

### T20: Define attendance-link and sharing domain contracts

**What:** Add Groups domain models/capabilities/errors for attendance links, resolved destinations, share snapshots/images, attempt limits and provider-neutral native sharing/link ports.

**Where:** `features/groups/domain/src/commonMain/**/attendance/share/**`; co-located tests; relevant contracts moved from current Groups ports/data.

**Depends on:** T19  
**Reuses:** Current attendance-share DTO semantics, `DeferredLinkFailure`, link/share port callbacks and safe retry delay.  
**Requirements:** MDB-002–MDB-004, MDB-007–MDB-017, MDB-022.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Keep resolved destination and snapshot person/count/privacy values domain-owned.
- Preserve invalid/expired and attempt-limit-with-delay outcomes without status/code.
- Native share/link ports contain no Branch/Android/iOS type.

**Done when:**

- [ ] All link/share contracts are framework-free and Groups-owned.
- [ ] Snapshot and destination models preserve every currently rendered value.
- [ ] Minimum 16 cases cover rotate/resolve/snapshot values, empty snapshot, privacy model, invalid/expired, attempt limit with/without delay, unavailable and native success/cancel/failure callbacks.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 16.  
**Gate:** Quick — `rtk ./gradlew :features:groups:domain:allTests --console=plain`.  
**Commit:** `feat(groups-domain): define attendance sharing contracts`

### T21: Implement attendance-link and share-snapshot data mapping

**What:** Move attendance-share DTOs/API into Groups data and implement rotate/resolve/read-snapshot domain capabilities with exact routes, safe failures, retry rules and cancellation.

**Where:** `features/groups/data/src/commonMain/**/attendance/share/**`; migrated `AttendanceShareApiTest` and mapper coverage.

**Depends on:** T20  
**Reuses:** Existing endpoints/payloads, attempt-limit mapper and transport retry helper.  
**Requirements:** MDB-007–MDB-009, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Preserve rotate, resolve and snapshot request semantics exactly.
- 429/attempt-limit is never auto-retried; reads may retry transient failures, writes only with accepted idempotency.
- Map malformed nested snapshot/person/count values to invalid response, never partial domain output.

**Done when:**

- [ ] DTOs and raw link/problem values stay inside data.
- [ ] Minimum 20 cases retain current route/payload/snapshot assertions and add malformed nesting, every safe failure, 429 one-call behavior, retry schedule/exhaustion/exclusion, cancellation and secret safety.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine integration; minimum 20.  
**Gate:** Quick — `rtk ./gradlew :features:groups:data:allTests --console=plain`.  
**Commit:** `refactor(groups-data): map attendance sharing transport`

### T22: Switch attendance sharing presentation and Android adapter to domain

**What:** Convert deferred attendance-link coordinator, share-image mapping/UI state, compose-app destination store and Android share adapter to Groups domain contracts.

**Where:** `features/groups/src/commonMain/**/attendance/share/**`; relevant `compose-app` files; `android-app/src/main/**/groups/attendance/share/**`; focused tests.

**Depends on:** T21  
**Reuses:** Existing coordinator/image/privacy tests, destination store and Android sharing tests.  
**Requirements:** MDB-001, MDB-004, MDB-010, MDB-013–MDB-017, MDB-020, MDB-022.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Preserve pending-link consumption, single-flight, privacy confirmation, retry delay, image content and share-result effects.
- Remove data/network error conversion from presentation.
- Platform adapter sees only domain port/image inputs plus Android APIs.

**Done when:**

- [ ] Targeted presentation/app state contains no share DTO/data/network type.
- [ ] Minimum 24 common cases cover resolve success/error/rate limit/retry, snapshot/image mapping, privacy confirm/cancel and share outcomes; focused Android adapter tests pass.
- [ ] Focused gate exits 0.

**Tests:** common unit/Compose plus focused Android unit; minimum 24 common cases.  
**Gate:** Full focused — `rtk ./gradlew :features:groups:allTests :compose-app:allTests :android-app:testDevDebugUnitTest --tests 'br.com.saqz.androidapp.groups.attendance.share.*' --console=plain`.  
**Commit:** `refactor(groups): consume attendance sharing domain contracts`

### T23: Define games and recurrence domain contracts

**What:** Create framework-free game, venue, status, version, weekly recurrence, slot, series-boundary commands/results/errors and game gateway capabilities in Groups domain.

**Where:** `features/groups/domain/src/commonMain/**/game/**`; co-located tests.

**Depends on:** T22  
**Reuses:** Current game DTO/command semantics, `VersionToken`, existing editor/list/detail rules.  
**Requirements:** MDB-002–MDB-004, MDB-007–MDB-014.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Domain commands describe create/edit/lifecycle/series/boundary capabilities without routes, verbs, headers or serialization.
- Preserve current date/time strings or domain time values exactly; do not introduce a new time abstraction.
- Model every current enum/status/weekday/scope/action and versioned response explicitly.

**Done when:**

- [ ] Game/series domain contains no `Dto`, Ktor, serialization or presentation type.
- [ ] Minimum 28 cases cover all enums/statuses, venue/null venue ID, counts, terminal flags, recurrence slots, nullable fee/notes, boundary actions/scopes, commands, versions and every feature error.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 28.  
**Gate:** Quick — `rtk ./gradlew :features:groups:domain:allTests --console=plain`.  
**Commit:** `feat(groups-domain): define games and recurrence contracts`

### T24: Implement game and recurrence data mapping

**What:** Move game/series DTOs and API operations into Groups data, implement domain gateways, and preserve exact endpoints, commands, ETags, failures, retries and cancellation.

**Where:** `features/groups/data/src/commonMain/**/game/**`; migrated `GameApiTest` plus mapper tests.

**Depends on:** T23  
**Reuses:** Existing compact Game API implementation/tests, transport client and retry helper.  
**Requirements:** MDB-007–MDB-009, MDB-012, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Preserve list/read/create/edit/lifecycle/create-series/read-series/boundary routes and bodies byte-for-byte in meaning.
- Require ETags for versioned outcomes and map missing ETag to invalid response.
- Writes retry only where an existing idempotency key is asserted; lifecycle/boundary operations without a safe key make one call.

**Done when:**

- [ ] All Game DTO/serialization/transport mapping stays in Groups data.
- [ ] Minimum 34 cases retain all current route/body/ETag/status/series assertions and add malformed fields/enums, every error category, retry eligibility/exclusion/exhaustion, cancellation and secret safety.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine integration; minimum 34.  
**Gate:** Quick — `rtk ./gradlew :features:groups:data:allTests --console=plain`.  
**Commit:** `refactor(groups-data): map game transport to domain`

### T25: Switch games list presentation to domain contracts

**What:** Convert games list ViewModel/state/intent/item mapping/screen inputs to domain models and gateway results.

**Where:** `features/groups/src/commonMain/**/games/list/**`; corresponding tests/UI tests.

**Depends on:** T24  
**Reuses:** Existing list state/item mapper, role visibility and tests.  
**Requirements:** MDB-001, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:** Preserve load/refresh/empty/error/navigation intent behavior and UI formatting; replace only data/network types and fakes.

**Done when:**

- [ ] List package has zero data/network imports.
- [ ] Minimum 14 cases cover loading, populated/empty list, every status mapping, role actions, connectivity/error/retry and emitted navigation inputs.
- [ ] Existing screen tests remain present; focused gate exits 0.

**Tests:** unit + existing Compose UI; minimum 14.  
**Gate:** Quick — `rtk ./gradlew :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): use domain games in list`

### T26: Switch game editor presentation to domain contracts

**What:** Convert game editor ViewModel, commands, draft models/store seams, recurrence/boundary mapping and screen inputs to domain types/results.

**Where:** `features/groups/src/commonMain/**/games/editor/**`; corresponding tests/UI tests; platform draft imports only as required.

**Depends on:** T25  
**Reuses:** Existing editor command mapper, draft recovery and ViewModel tests.  
**Requirements:** MDB-001, MDB-008, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Presentation constructs domain commands, never request DTOs.
- Preserve draft, validation fields/generic fallback, recurrence editing, conflict reload, exact version and one-shot effects.
- Do not redesign SavedState/draft persistence.

**Done when:**

- [ ] Editor package has zero data/network/serialization imports.
- [ ] Minimum 28 cases retain current editor/draft/recurrence/boundary tests and cover typed validation, generic fallback, conflict, unavailable/retry and success effects.
- [ ] Focused gate exits 0.

**Tests:** unit + existing Compose UI; minimum 28.  
**Gate:** Quick — `rtk ./gradlew :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): use domain commands in game editor`

### T27: Switch game-detail lifecycle presentation to domain contracts

**What:** Convert game-detail loading and lifecycle mutation behavior (excluding attendance, handled next) to domain game models/results/errors.

**Where:** `features/groups/src/commonMain/**/games/detail/**` game/lifecycle portions and focused tests.

**Depends on:** T26  
**Reuses:** Existing `GameDetailViewModelTest` lifecycle assertions and effect contract.  
**Requirements:** MDB-001, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:** Preserve initial load, terminal read-only policy, single-flight lifecycle confirmation, exact version, conflict snapshot/reload and effects. Leave attendance seams compiling for T28–T30 without importing data.

**Done when:**

- [ ] Game-detail lifecycle path uses only domain game contracts.
- [ ] Minimum 14 lifecycle cases cover load success/failure, terminal state, action visibility, confirmation/cancel, duplicate confirm, success effect, conflict preservation/reload and explicit retry.
- [ ] Focused gate exits 0.

**Tests:** unit + existing Compose UI focused on lifecycle; minimum 14.  
**Gate:** Quick — `rtk ./gradlew :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): use domain games in detail lifecycle`

### T28: Define attendance domain models and capability contracts

**What:** Create attendance entry/detail/audit/capacity/mutation/status/intent/command/version/error models and gateway capabilities in Groups domain.

**Where:** `features/groups/domain/src/commonMain/**/attendance/**` excluding share; co-located tests.

**Depends on:** T27  
**Reuses:** Current attendance DTO/command semantics and `VersionToken`.  
**Requirements:** MDB-002–MDB-004, MDB-007–MDB-014.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:** Preserve confirmed/declined/waitlisted, own attendance, aggregate counts, capacity, audit, deadline/frozen/conflict/hidden/auth outcomes and request IDs without serialization.

**Done when:**

- [ ] Attendance contracts contain no transport/presentation type.
- [ ] Minimum 22 cases cover all statuses/intents, nullable own attendance, waitlist position, counts, capacity/version, audit, commands and every feature error.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 22.  
**Gate:** Quick — `rtk ./gradlew :features:groups:domain:allTests --console=plain`.  
**Commit:** `feat(groups-domain): define attendance contracts`

### T29: Implement attendance data mapping and mutations

**What:** Move attendance DTOs/API into Groups data and implement read/respond/override/capacity mappings with exact routes, commands, ETags, failures, retry rules and cancellation.

**Where:** `features/groups/data/src/commonMain/**/attendance/**` excluding share; migrated `AttendanceApiTest` and mapper tests.

**Depends on:** T28  
**Reuses:** Current compact Attendance API tests, network client and retry helper.  
**Requirements:** MDB-007–MDB-009, MDB-012, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:** Preserve all routes/commands/request IDs/ETags. Reads retry transient failures; mutations only with accepted idempotency key. Deadline/frozen/conflict/hidden/auth/validation remain typed feature errors and never auto-retry unless their underlying outcome is the approved transient set.

**Done when:**

- [ ] Attendance DTOs/transport codes stay in data.
- [ ] Minimum 28 cases retain current round-trip/ETag/waitlist/full-capacity assertions and add malformed payload, every feature/shared error, retry schedule/exclusion/exhaustion, cancellation and secret safety.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine integration; minimum 28.  
**Gate:** Quick — `rtk ./gradlew :features:groups:data:allTests --console=plain`.  
**Commit:** `refactor(groups-data): map attendance transport to domain`

### T30: Switch game-detail attendance presentation to domain contracts

**What:** Convert game-detail attendance state/intents/operations/capacity/override/retry behavior and screen inputs to domain attendance contracts.

**Where:** `features/groups/src/commonMain/**/games/detail/**` attendance portions; attendance-related tests/UI tests.

**Depends on:** T29  
**Reuses:** Existing attendance ViewModel tests, retry-operation state and effects.  
**Requirements:** MDB-001, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:** Preserve single-flight, pending confirmations, exact retry operation/command key, capacity/version, waitlist/full success, deadline/frozen/hidden/auth/conflict/validation states and effects.

**Done when:**

- [ ] Entire game-detail package has zero Groups data/network imports after T27+T30.
- [ ] Minimum 30 attendance/detail cases retain current behavior and use domain fakes/results.
- [ ] Generic localized validation fallback appears only when no safe global message exists.
- [ ] Focused gate exits 0.

**Tests:** unit + existing Compose UI; minimum 30 attendance/detail cases.  
**Gate:** Build focused (end of phase) — `rtk ./gradlew :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): consume attendance domain contracts`

### T31: Define finance domain models and capability contracts

**What:** Create charges, expenses, totals, audit, categories, statuses, commands, versions, role-safe capabilities and finance errors in Groups domain.

**Where:** `features/groups/domain/src/commonMain/**/finance/**`; co-located tests.

**Depends on:** T30  
**Reuses:** Current Finance DTO/command semantics, currency formatter expectations and `VersionToken`.  
**Requirements:** MDB-002–MDB-004, MDB-007–MDB-014.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-error-handling`, `android-testing`.

**Implementation instructions:** Preserve cents as integral values, months/due dates/status/audit/totals/category/custom notes/active flags and organizer/athlete capability split. No HTTP or serialization terminology.

**Done when:**

- [ ] Finance contracts/models are framework-free and feature-owned.
- [ ] Minimum 30 cases cover all charge/expense statuses/categories, cents, totals, audit, versions, monthly/write/status commands, role capability split and every feature error.
- [ ] Focused gate exits 0.

**Tests:** unit; minimum 30.  
**Gate:** Quick — `rtk ./gradlew :features:groups:domain:allTests --console=plain`.  
**Commit:** `feat(groups-domain): define finance contracts`

### T32: Implement finance DTO mapping and data capabilities

**What:** Move Finance DTOs/API into Groups data and implement organizer/athlete charge, expense and totals capabilities with exact payloads, routes, ETags, idempotency, failures, retries and cancellation.

**Where:** `features/groups/data/src/commonMain/**/finance/**`; migrated `FinanceApiTest` and mapper tests.

**Depends on:** T31  
**Reuses:** Existing dense finance transport tests, authenticated client and retry helper.  
**Requirements:** MDB-007–MDB-009, MDB-012, MDB-015–MDB-019, MDB-021–MDB-023, MDB-026.  
**Tools:** local filesystem/shell; skills `android-data-layer`, `android-error-handling`, `android-testing`.

**Implementation instructions:**

- Preserve every charge/expense/totals route, method, payload, cents value, audit item, ETag and idempotency header/key.
- Only writes whose existing tests prove the idempotency key opt into retry.
- Map validation/hidden/forbidden/conflict/precondition/lifecycle/auth/transient/invalid outcomes exhaustively.

**Done when:**

- [ ] Finance DTOs, serializers and raw problems exist only in Groups data.
- [ ] Minimum 40 cases retain all current exact transport assertions and add malformed/missing fields, every shared/feature error, retry eligible/excluded/exhausted, cancellation and credential safety.
- [ ] Focused gate exits 0.

**Tests:** unit + MockEngine integration; minimum 40.  
**Gate:** Quick — `rtk ./gradlew :features:groups:data:allTests --console=plain`.  
**Commit:** `refactor(groups-data): map finance transport to domain`

### T33: Switch finance charges presentation to domain contracts

**What:** Convert charge state/intents/operations/rules/ViewModel/screen inputs and membership capability use to Groups domain types/results.

**Where:** `features/groups/src/commonMain/**/finance/charges/**`; corresponding tests/UI tests.

**Depends on:** T32  
**Reuses:** Existing charge rules/ViewModel/screen tests and draft-store seams.  
**Requirements:** MDB-001, MDB-010, MDB-013–MDB-017, MDB-020.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`.

**Implementation instructions:** Preserve role visibility, loading/empty/totals, cents, monthly draft, status mutation, audit, conflict exact retry operation, validation fields/generic fallback and effects.

**Done when:**

- [ ] Charges presentation has zero data/network imports.
- [ ] Minimum 26 charge behavior/UI cases remain present and use domain fakes.
- [ ] Exact version/command is retained for conflict retry; excluded failures do not auto-retry in presentation.
- [ ] Focused gate exits 0.

**Tests:** unit + existing Compose UI; minimum 26.  
**Gate:** Quick — `rtk ./gradlew :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): consume charge domain contracts`

### T34: Switch finance expenses presentation to domain contracts

**What:** Convert expense form/rules/state/intents/ViewModel/screen inputs and totals interactions to Groups domain types/results, completing Groups presentation cleanup.

**Where:** `features/groups/src/commonMain/**/finance/expenses/**`; corresponding tests/UI tests; final Groups presentation build dependencies.

**Depends on:** T33  
**Reuses:** Existing expense rules/ViewModel/screen tests and draft-store seams.  
**Requirements:** MDB-001, MDB-006, MDB-010, MDB-013–MDB-017, MDB-020, MDB-024.  
**Tools:** local filesystem/shell; skills `android-presentation-mvi`, `android-error-handling`, `android-testing`, `android-module-structure`.

**Implementation instructions:** Preserve categories/custom notes, edit/create/void, cents, totals, conflict exact draft/version/operation, validation fields/generic fallback, role visibility and effects. After conversion remove core-network/Ktor/serialization/data dependencies from Groups presentation.

**Done when:**

- [ ] Expenses and all Groups presentation/UI sources have zero `groups.data`, `br.com.saqz.network`, Ktor or serialization imports.
- [ ] Groups presentation build file depends on Groups domain, not Groups data/core network.
- [ ] Minimum 26 expense behavior/UI cases remain present and use domain fakes.
- [ ] Groups domain/data/presentation focused phase gate exits 0.

**Tests:** unit + existing Compose UI; minimum 26.  
**Gate:** Build focused — `rtk ./gradlew :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests --console=plain`.  
**Commit:** `refactor(groups): complete finance domain boundary`

### T35: Replace app-level DTO coupling with explicit feature coordination

**What:** Convert compose-app Access/Groups orchestration, navigation state inputs and Koin bindings to domain contracts, including explicit Access-membership-to-Groups-access translation.

**Where:** `compose-app/src/commonMain/**/{di,navigation,groups}/**`; `compose-app/src/commonTest/**`; `compose-app/build.gradle.kts`.

**Depends on:** T34  
**Reuses:** Existing `AccessOrchestrator`, `GroupsNavigationViewModel`, Koin modules/tests and AD-026/AD-029 ownership.  
**Requirements:** MDB-001, MDB-004–MDB-006, MDB-010, MDB-020, MDB-022, MDB-025.  
**Tools:** local filesystem/shell; skills `android-di-koin`, `android-module-structure`, `android-presentation-mvi`, `android-testing`.

**Implementation instructions:**

- App composition may import data implementations only inside binding definitions; runtime state/orchestration imports domain/presentation contracts only.
- Translate Access-owned membership role text into Groups-owned role at one explicit app mapper; invalid role becomes a typed safe reconciliation outcome, never a partially valid model.
- Preserve current selected-group reconciliation, photo loading, invalidation, singleton graph and umbrella framework exports.
- Do not implement Navigation 3 or reorganize Koin modules.

**Done when:**

- [ ] compose-app state/navigation/UI files contain no DTO, `NetworkResult`, `NetworkError`, or Groups data implementation.
- [ ] Only DI binding files import feature data implementation classes.
- [ ] Minimum 16 composition/coordination cases cover every role translation, unknown role, empty/multiple memberships, selected-group reconciliation, photo/profile capabilities, Koin singleton/bindings and bootstrap reload.
- [ ] `:compose-app:allTests` and compile gate exit 0.

**Tests:** composition/orchestration unit; minimum 16.  
**Gate:** Full focused — `rtk ./gradlew :compose-app:compileAndroidMain :compose-app:allTests --console=plain`.  
**Commit:** `refactor(compose): coordinate features through domain contracts`

### T36: Add the deterministic mobile boundary architecture gate

**What:** Add a repository gate and negative scratch tests that reject every forbidden Gradle edge/import, wire it into `scripts/check-gradle`, and update only the affected script command contracts.

**Where:** `scripts/check-mobile-boundaries`; `tests/scripts/check-mobile-boundaries.test.sh`; focused edits to `scripts/check-gradle`, `tests/scripts/check-gradle.test.sh`, `scripts/test-scripts`, and its contract test if required.

**Depends on:** T35  
**Reuses:** Existing shell gate style, scratch-repository mutation patterns in `check-scope.test.sh` and `check-workspace-isolation.test.sh`.  
**Requirements:** MDB-001–MDB-006, MDB-010, MDB-025.  
**Tools:** local filesystem/shell; skills `android-module-structure`, `android-testing`.

**Implementation instructions:**

- Check explicit Gradle allowlists for core domain, feature domain, data, presentation and compose-app composition.
- Scan production imports for presentation-to-data/network/serialization, domain-to-framework, data-to-presentation and feature-to-feature dependencies.
- Reject compatibility adapter markers/paths after both feature migrations complete.
- Tests must create isolated scratch repositories; never mutate the real source tree.
- Wire the gate before the mobile Gradle aggregate but do not run `scripts/check-gradle` in this task.

**Done when:**

- [ ] Positive real-worktree gate exits 0.
- [ ] At least 10 negative cases fail for: presentation->data Gradle edge, presentation->network, domain->Compose, domain->Koin, domain->Ktor, domain->serialization, data->presentation, Access->Groups, Groups->Access, forbidden source import, and compatibility adapter (11 preferred).
- [ ] Each negative assertion checks a stable diagnostic naming the forbidden edge/file.
- [ ] Focused script contract proves `check-gradle` invokes the new gate without running the real aggregate.
- [ ] Focused script gates exit 0.

**Tests:** shell contract; minimum 11 negative plus positive baseline.  
**Gate:** Full focused — from repository root: `rtk tests/scripts/check-mobile-boundaries.test.sh`, then `rtk tests/scripts/check-gradle.test.sh`, then `rtk scripts/check-mobile-boundaries`.  
**Commit:** `test(architecture): enforce mobile domain data boundaries`

### T37: Update architecture documentation and run the only final aggregate

**What:** Update the mobile architecture/testing inventory and aggregate commands for the new modules, perform final zero-leakage searches, and run the complete repository gate exactly once.

**Where:** Focused sections of `README.md`, `scripts/check-gradle`, related command-contract tests if not completed in T36, and feature task/status documentation; no production behavior changes.

**Depends on:** T36  
**Reuses:** Existing README module/gate sections and repository `scripts/check-all`.  
**Requirements:** MDB-005, MDB-006, MDB-020, MDB-024–MDB-026.  
**Tools:** local filesystem/shell; skills `tlc-spec-driven`, `android-module-structure`, `android-testing`.

**Implementation instructions:**

- Document `:core:domain`, Access/Groups domain/data children, stable presentation coordinates, dependency direction, focused commands and final aggregate.
- Update `scripts/check-gradle` module tasks/test discovery only as needed so final aggregate covers every new module.
- Before the aggregate, run repository searches proving zero forbidden presentation/data/network/serialization imports, zero feature cross-dependencies and zero compatibility adapters; record commands/results for the Verifier.
- Run no separate `check-gradle` or `check-ios`; `check-all` owns all final aggregates.

**Done when:**

- [ ] README and script command contracts name every new module and exact direction.
- [ ] Boundary gate and zero-leakage searches return zero violations.
- [ ] From repository root, `rtk scripts/check-all` exits 0; discovered tests are non-zero and no test is skipped/deleted.
- [ ] The task records module test counts accumulated during T01–T36 and the final aggregate count for comparison.
- [ ] One atomic documentation/gate commit is created with no unrelated README/STATE changes.
- [ ] After this commit, the orchestrator automatically dispatches an independent Verifier; the author does not self-approve validation.

**Tests:** final aggregate and subsequent independent verification.  
**Gate:** Final aggregate — from repository root, only now: `rtk scripts/check-all`.  
**Commit:** `docs(mobile): finalize domain data boundary contract`

## Phase Execution Map

```text
Phase 1: T01 -> T02 -> T03 -> T04 -> T05
Phase 2: T06 -> T07 -> T08 -> T09 -> T10
Phase 3: T11 -> T12 -> T13 -> T14 -> T15 -> T16
Phase 4: T17 -> T18 -> T19 -> T20 -> T21 -> T22
Phase 5: T23 -> T24 -> T25 -> T26 -> T27 -> T28 -> T29 -> T30
Phase 6: T31 -> T32 -> T33 -> T34
Phase 7: T35 -> T36 -> T37

Whole feature:
T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T07 -> T08 -> T09 -> T10
    -> T11 -> T12 -> T13 -> T14 -> T15 -> T16 -> T17 -> T18 -> T19 -> T20
    -> T21 -> T22 -> T23 -> T24 -> T25 -> T26 -> T27 -> T28 -> T29 -> T30
    -> T31 -> T32 -> T33 -> T34 -> T35 -> T36 -> T37
```

Execution is strictly sequential. No phase exceeds ten tasks. At Execute, whole phases pack into six recommended sequential worker batches: `{P1=5}`, `{P2=5}`, `{P3=6}`, `{P4=6}`, `{P5=8}`, `{P6+P7=7}`. No phase is split. The user must approve delegation before any worker is spawned.

## Requirement-to-Task Traceability

| Requirement | Implemented/verified by |
|---|---|
| MDB-001 | T04–T05 module graph; T09–T10 Access closure; T13/T16/T19/T22/T25–T27/T30/T33–T35 presentation closure; T36 gate |
| MDB-002 | T06–T07 Access contract/implementation; T11–T12, T14–T15, T17–T18, T20–T21, T23–T24, T28–T29, T31–T32 Groups capability pairs |
| MDB-003 | T01/T04–T06/T08 domain foundations; every Groups domain task; T36 negative gate |
| MDB-004 | T06/T08 and Groups domain tasks; T35 app-level translation; T36 cross-feature negative tests |
| MDB-005 | T04–T05 compile boundaries; T36 deterministic positive/negative gate; T37 final aggregate |
| MDB-006 | T10 Access cleanup; T34 Groups cleanup; T35–T37 final zero-adapter proof |
| MDB-007 | Every domain/data capability pair: T06–T07, T11–T12, T14–T15, T17–T18, T20–T21, T23–T24, T28–T29, T31–T32 |
| MDB-008 | Request/command pairs in T11–T12, T14–T15, T17–T18, T20–T21, T23–T24, T28–T29, T31–T32 |
| MDB-009 | Malformed/missing/optional mapping tests in T07/T12/T15/T18/T21/T24/T29/T32 |
| MDB-010 | T09 Access and T13/T16/T19/T22/T25–T27/T30/T33–T35 Groups/app presentation migrations |
| MDB-011 | Capability-shaped interfaces in T06/T11/T14/T17/T20/T23/T28/T31 |
| MDB-012 | Version/page/media domain metadata in T11–T12/T17–T18/T23–T24/T28–T29/T31–T32 |
| MDB-013 | T01 shared result; all domain contracts and presentation consumers |
| MDB-014 | T01 `EmptyResult`; no-payload capabilities in T14–T15/T17–T18/T20–T21/T23–T24/T28–T29/T31–T32 |
| MDB-015 | T01–T03 shared errors/classification/retry and every data mapper task |
| MDB-016 | T01 validation value, every validation mapper, and presentation fallback tasks T09/T13/T16/T26/T30/T33/T34 |
| MDB-017 | Exhaustive presentation mappings in T09/T13/T16/T19/T22/T25–T27/T30/T33–T35 |
| MDB-018 | T02–T03 transport cancellation and every data capability cancellation test |
| MDB-019 | T02 transport distinction plus credential-safe diagnostics in every data task |
| MDB-020 | Characterization-preserving presentation tasks T09/T13/T16/T19/T22/T25–T27/T30/T33–T35 and T37 aggregate |
| MDB-021 | Exact transport assertions and idempotency decisions in T03 and all data tasks |
| MDB-022 | T08 native Access ports, T17/T20 Groups native ports, platform seams T19/T22, app composition T10/T35 |
| MDB-023 | Mapping/error/malformed/cancellation coverage in T07/T12/T15/T18/T21/T24/T29/T32 |
| MDB-024 | Focused feature completion gates T10/T30/T34 and final T37 aggregate |
| MDB-025 | T35 app cleanup, T36 boundary gate, T37 zero-leakage scans/aggregate |
| MDB-026 | T02 classification, T03 retry engine, every data task's eligibility/exclusion tests, T37 aggregate |

Coverage: 26 requirements, 26 mapped, 0 unmapped.

## Task Granularity Check

| Task | Single deliverable | Scope judgment | Status |
|---|---|---|---|
| T01 | Shared result/error module | One cohesive foundation module | ✅ Granular |
| T02 | Transport failure classification | One mapping responsibility | ✅ Granular |
| T03 | Retry helper | One reusable mechanism | ✅ Granular |
| T04 | Access child module declarations | One module-graph edge set | ✅ Granular |
| T05 | Groups child module declarations | One module-graph edge set | ✅ Granular |
| T06 | Access session domain API | One capability contract | ✅ Granular |
| T07 | Access session data gateway | One endpoint capability | ✅ Granular |
| T08 | Native Access ports | One provider-neutral port surface | ✅ Granular |
| T09 | Access presentation boundary | One feature presentation conversion | ✅ Granular |
| T10 | Access composition closure | One integration seam | ✅ Granular |
| T11 | Group profile/setup domain API | One capability family | ✅ Granular |
| T12 | Group profile/setup data gateway | One capability family | ✅ Granular |
| T13 | Group profile/setup presentation | One capability family | ✅ Granular |
| T14 | Membership/invite domain API | One capability family | ✅ Granular |
| T15 | Membership/invite data gateway | One capability family | ✅ Granular |
| T16 | Membership/invite presentation | One capability family | ✅ Granular |
| T17 | Photo domain/native contracts | One media capability | ✅ Granular |
| T18 | Photo data gateway | One media capability | ✅ Granular |
| T19 | Photo presentation/platform seam | One media capability | ✅ Granular |
| T20 | Attendance sharing domain API | One sharing capability | ✅ Granular |
| T21 | Attendance sharing data gateway | One sharing capability | ✅ Granular |
| T22 | Attendance sharing presentation/platform seam | One sharing capability | ✅ Granular |
| T23 | Games domain API | One game capability | ✅ Granular |
| T24 | Games data gateway | One game capability | ✅ Granular |
| T25 | Games list presentation | One screen state machine | ✅ Granular |
| T26 | Game editor presentation | One screen state machine | ✅ Granular |
| T27 | Game detail lifecycle presentation | One state-machine responsibility | ✅ Granular |
| T28 | Attendance domain API | One attendance capability | ✅ Granular |
| T29 | Attendance data gateway | One attendance capability | ✅ Granular |
| T30 | Attendance detail presentation | One state-machine responsibility | ✅ Granular |
| T31 | Finance domain API | One finance capability family | ✅ Granular |
| T32 | Finance data gateway | One finance capability family | ✅ Granular |
| T33 | Charges presentation | One screen state machine | ✅ Granular |
| T34 | Expenses presentation | One screen state machine | ✅ Granular |
| T35 | App feature coordination | One composition seam | ✅ Granular |
| T36 | Architecture gate | One repository guardrail | ✅ Granular |
| T37 | Docs + final aggregate | One finalization deliverable | ✅ Granular |

## Diagram-Definition Cross-Check

| Task | Depends On (body) | Diagram shows | Status |
|---|---|---|---|
| T01 | None | Start | ✅ Match |
| T02 | T01 | T01 -> T02 | ✅ Match |
| T03 | T02 | T02 -> T03 | ✅ Match |
| T04 | T03 | T03 -> T04 | ✅ Match |
| T05 | T04 | T04 -> T05 | ✅ Match |
| T06 | T05 | T05 -> T06 | ✅ Match |
| T07 | T06 | T06 -> T07 | ✅ Match |
| T08 | T07 | T07 -> T08 | ✅ Match |
| T09 | T08 | T08 -> T09 | ✅ Match |
| T10 | T09 | T09 -> T10 | ✅ Match |
| T11 | T10 | T10 -> T11 | ✅ Match |
| T12 | T11 | T11 -> T12 | ✅ Match |
| T13 | T12 | T12 -> T13 | ✅ Match |
| T14 | T13 | T13 -> T14 | ✅ Match |
| T15 | T14 | T14 -> T15 | ✅ Match |
| T16 | T15 | T15 -> T16 | ✅ Match |
| T17 | T16 | T16 -> T17 | ✅ Match |
| T18 | T17 | T17 -> T18 | ✅ Match |
| T19 | T18 | T18 -> T19 | ✅ Match |
| T20 | T19 | T19 -> T20 | ✅ Match |
| T21 | T20 | T20 -> T21 | ✅ Match |
| T22 | T21 | T21 -> T22 | ✅ Match |
| T23 | T22 | T22 -> T23 | ✅ Match |
| T24 | T23 | T23 -> T24 | ✅ Match |
| T25 | T24 | T24 -> T25 | ✅ Match |
| T26 | T25 | T25 -> T26 | ✅ Match |
| T27 | T26 | T26 -> T27 | ✅ Match |
| T28 | T27 | T27 -> T28 | ✅ Match |
| T29 | T28 | T28 -> T29 | ✅ Match |
| T30 | T29 | T29 -> T30 | ✅ Match |
| T31 | T30 | T30 -> T31 | ✅ Match |
| T32 | T31 | T31 -> T32 | ✅ Match |
| T33 | T32 | T32 -> T33 | ✅ Match |
| T34 | T33 | T33 -> T34 | ✅ Match |
| T35 | T34 | T34 -> T35 | ✅ Match |
| T36 | T35 | T35 -> T36 | ✅ Match |
| T37 | T36 | T36 -> T37 | ✅ Match |

No dependency points to a later task or skips an incomplete phase.

## Test Co-location Validation

| Task | Code layer | Matrix requires | Task says | Status |
|---|---|---|---|---|
| T01 | Core domain | Unit, all branches | 16 co-located unit cases | ✅ OK |
| T02 | Core network classification | Unit, error/cancellation branches | 8 focused cases plus retained suite | ✅ OK |
| T03 | Core network retry | Unit, exact schedule/exclusions/cancellation | 22 co-located cases | ✅ OK |
| T04 | Access Gradle config | No direct tests; focused compile | Two child-module compile tasks | ✅ OK |
| T05 | Groups Gradle config | No direct tests; focused compile | Two child-module compile tasks | ✅ OK |
| T06 | Access session domain | Unit, all values/errors | 12 co-located cases | ✅ OK |
| T07 | Access session data | Unit + MockEngine | 18 co-located mapping/error/retry cases | ✅ OK |
| T08 | Access domain ports + Android adapters | Common + focused Android unit | 12 common plus retained adapter cases | ✅ OK |
| T09 | Access presentation | Unit + existing Compose UI | 20 relevant cases, all existing retained | ✅ OK |
| T10 | Access composition | Koin/composition unit | 8 compose-app cases plus focused feature suites | ✅ OK |
| T11 | Group profile domain | Unit, every value/error | 20 co-located cases | ✅ OK |
| T12 | Group profile data | Unit + MockEngine | 28 co-located transport/mapping/retry cases | ✅ OK |
| T13 | Group profile presentation | Unit + existing Compose UI | 28 relevant cases | ✅ OK |
| T14 | Membership/invite domain | Unit, every value/error | 14 co-located cases | ✅ OK |
| T15 | Membership/invite data | Unit + MockEngine | 24 co-located cases | ✅ OK |
| T16 | Membership/invite presentation | Unit + existing Compose UI | 28 relevant cases | ✅ OK |
| T17 | Photo domain/native contracts | Unit, every outcome | 16 co-located cases | ✅ OK |
| T18 | Photo data | Unit + MockEngine/media | 22 co-located cases | ✅ OK |
| T19 | Photo presentation/platform | Common/Compose + focused Android unit | 20 common plus retained adapter cases | ✅ OK |
| T20 | Sharing domain | Unit, every value/error | 16 co-located cases | ✅ OK |
| T21 | Sharing data | Unit + MockEngine | 20 co-located cases | ✅ OK |
| T22 | Sharing presentation/platform | Common/Compose + focused Android unit | 24 common plus adapter cases | ✅ OK |
| T23 | Games domain | Unit, every value/error | 28 co-located cases | ✅ OK |
| T24 | Games data | Unit + MockEngine | 34 co-located cases | ✅ OK |
| T25 | Games list presentation | Unit + existing Compose UI | 14 relevant cases | ✅ OK |
| T26 | Game editor presentation | Unit + existing Compose UI | 28 relevant cases | ✅ OK |
| T27 | Game lifecycle presentation | Unit + existing Compose UI | 14 relevant cases | ✅ OK |
| T28 | Attendance domain | Unit, every value/error | 22 co-located cases | ✅ OK |
| T29 | Attendance data | Unit + MockEngine | 28 co-located cases | ✅ OK |
| T30 | Attendance presentation | Unit + existing Compose UI | 30 relevant cases | ✅ OK |
| T31 | Finance domain | Unit, every value/error | 30 co-located cases | ✅ OK |
| T32 | Finance data | Unit + MockEngine | 40 co-located cases | ✅ OK |
| T33 | Charges presentation | Unit + existing Compose UI | 26 relevant cases | ✅ OK |
| T34 | Expenses presentation | Unit + existing Compose UI | 26 relevant cases | ✅ OK |
| T35 | App coordination | Koin/orchestration unit | 16 compose-app cases | ✅ OK |
| T36 | Architecture scripts | Positive + negative shell contracts | 11 negative plus positive baseline | ✅ OK |
| T37 | Final integration | Full aggregate + independent verification | `check-all` only here, then Verifier | ✅ OK |

No production task defers required tests to a later task.

## Tools and Skills Proposed for Execute

- **MCPs:** none required. Use local filesystem, `rtk` shell commands, Gradle, Xcode only through the final aggregate, and `apply_patch` for edits.
- **Skills:** `tlc-spec-driven` for every task; `android-module-structure`, `android-data-layer`, `android-error-handling`, `android-testing`, `android-presentation-mvi`, and `android-di-koin` only where listed in each task.
- **Skill precedence notes:** keep the repository's `kotlin.test` style; do not migrate test frameworks, convention plugins, MVI structure, or Koin module placement because those are explicitly outside this feature.

## Execute Entry Gate

Before T01, the user must:

1. Approve this Test Coverage Matrix, Gate Check Commands, task breakdown and proposed tools/skills.
2. Choose whether to use the six sequential phase-aligned worker batches. Never spawn workers without explicit approval.

After T37, dispatch the independent Verifier automatically. The Verifier re-derives MDB-001–MDB-026 evidence, runs behavior-level discrimination mutations in scratch state, writes `validation.md`, and returns PASS/FAIL. A surviving mutant creates a fix task and re-verification loop, bounded to three iterations.
