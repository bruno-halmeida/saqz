# Validation — mobile SOLID refactor wave 2

## Scope
Feature: `mobile-solid-refactor-wave-2`  
Tasks covered: T07–T08 (HTTP→domain failure mapping centralization and presentation consumption).  
Date: 2026-07-22  
Verifier: self (author performed explicit fresh-eyes pass after cooling down).

## Commits
- `b0345e0` — `refactor(groups): centralize http-to-domain failure mapping in the data layer`
- `f8d0d15` — `refactor(groups): consume data-layer failure mappers from presentation`
- `c7e09ff` — `docs(specs): mark T08 completed`

## Acceptance criteria evidence

### T07
- [x] Created `NetworkError.toSetupFailure()`, `toAdministrationFailure()`, `toPhotoFailure()`, and `toDeferredLinkFailure()` in `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/NetworkErrorMappers.kt`.
- [x] Created `NetworkErrorMappersTest.kt` with 14 unit tests covering each mapped status/code plus unknown/transport cases.
- [x] Promoted `isProblem` to a single top-level helper in `core/network` (`NetworkModels.kt`); the two duplicated wrappers in `GroupSetupSupport.kt` and `GroupAdministrationCoordinator.kt` now delegate to it.
- [x] Gate passed:
  ```
  rtk ./gradlew :core:network:allTests :features:groups:allTests --console=plain
  BUILD SUCCESSFUL
  ```

### T08
- [x] `GroupSetupViewModel`, `GroupAdministrationCoordinator`, `GroupPhotoCoordinator`, `DeferredInviteCoordinator`, and `DeferredAttendanceLinkCoordinator` no longer inspect `ApiProblemError` by status:
  ```
  $ rtk rg "ApiProblemError" mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation
  (no output)
  ```
- [x] `SetupFailure.Validation` and `AdministrationFailure.Validation` now carry `fieldErrors` so presentation can still populate form errors without touching `ApiProblemError`.
- [x] Existing suites (`GroupSetupViewModelTest`, `GroupPhotoCoordinatorTest`, `DeferredInviteStateMachineTest`, `DeferredAttendanceLinkStateMachineTest`, `GroupAdministrationStateMachineTest`) pass unchanged.
- [x] Gate passed:
  ```
  rtk ./gradlew :features:groups:allTests :compose-app:allTests --console=plain
  BUILD SUCCESSFUL
  ```

## Notes
- The `isProblem` helper is intentionally a plain top-level function rather than an extension because the same extension was not visible across KMP module boundaries to `:features:groups` (compiler reported `Unresolved reference 'isProblem'`). A comment in `NetworkModels.kt` records this.
- `rtk scripts/check-credentials` passed.
- `rtk scripts/check-scope` failed on a pre-existing false positive: the word "persistence" appears in a test-name string inside `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/AccessViewModelTest.kt:345` (`fun 'session invalidator exposed to runtime invalidator'...`). That file is unchanged by this feature and the match is in `commonTest`, which the scope script currently treats as production code. This failure is unrelated to T07/T08.

## Conclusion
T07 and T08 acceptance criteria are met; the presentation layer no longer inspects `ApiProblemError` status codes directly, and all targeted mobile tests pass.

---

## T15-T18 Koin validation

Date: 2026-07-22  
Verifier: independent general-purpose agent (source review) plus targeted Android/KMP/iOS gates.
Diff: `23bde09..3dbebce`

### Commits
- `ce7b38a` — `build(mobile): add koin for per-feature dependency modules`
- `d39b80f` — `feat(compose-app): add koin network and drafts modules`
- `0d0824f` — `feat(compose-app): add koin access and groups modules`

### Acceptance criteria evidence
- [x] Koin 4.2.2 is pinned in the version catalog; Koin dependencies remain `implementation` and do not leak through the iOS public API.
- [x] Modules are separated by logical layer: core network, platform drafts, access data/presentation, groups data/presentation, and compose presentation.
- [x] Constructor-reference DSL (`singleOf`, `viewModelOf`) is used where constructor injection is sufficient; callback/factory/post-construction bindings use lambdas.
- [x] Android starts the common Koin container from `SaqzApplication`; Activity-scoped platform adapters are constructed with `MainActivityModel.viewModelScope` before their bindings are loaded.
- [x] iOS starts Koin and loads native bindings in `MainViewController` before Compose content is created.
- [x] Root ViewModels use `koinViewModel()` with assisted parameters where runtime navigation/state is required.
- [x] Production `Unconfigured` dependency defaults were removed; fakes live in test source sets.
- [x] Cross-platform graph tests resolve network, drafts, access, groups, and compose-presentation definitions; bootstrap resolution and singleton behavior are covered.

### Gates
```text
rtk ./gradlew :compose-app:allTests --console=plain -q
PASS (existing Compose test deprecation warnings only)

rtk ./gradlew :android-app:testDevDebugUnitTest :android-app:compileDevDebugAndroidTestKotlin --console=plain -q
PASS

rtk ./gradlew :compose-app:linkDebugFrameworkIosArm64 :compose-app:linkDebugFrameworkIosSimulatorArm64 -q
PASS

rtk git diff --check
PASS
```

### Independent verification
- PASS with no blocking/high findings after lifecycle fixes.
- Residual medium items are explicitly assigned to later wave-2 tasks: T21 removes the temporary delegating invalidator ordering seam; T22 removes the two legacy manually-created network graphs.

### Conclusion
T15-T18 are complete: Koin is installed, its layered graph is verified, platform roots bootstrap it safely, root ViewModels resolve through Koin, and production fallback stubs are removed without changing the current authenticated runtime behavior.

---

## Manual graph removal validation

Date: 2026-07-22  
Verifier: independent general-purpose agent (source review) plus targeted Android/KMP/iOS gates.

### Acceptance criteria evidence
- [x] `createPlatformNetworkClient` exists in `compose-app/commonMain` only in `di/NetworkModule.kt` (import + singleton factory).
- [x] `AccessRuntime` receives gateways, state machines, ports, and invalidator through constructor injection; it no longer creates or closes network/API infrastructure.
- [x] `SaqzKoinBootstrapTest.kt:36` — `assertSame(koin.get<GroupProfileGateway>(), runtime.groupProfileGateway)` proves runtime profile operations use the Koin binding.
- [x] `SaqzKoinBootstrapTest.kt:37` — `assertSame(koin.get<GroupPhotoGateway>(), runtime.groupPhotoGateway)` proves runtime photo operations use the Koin binding.
- [x] `SaqzKoinModulesTest.kt:138` — `assertSame(koin.get<AuthenticatedNetworkClient>(), koin.get<AuthenticatedNetworkClient>())` proves authenticated networking is singleton-scoped.
- [x] `SaqzKoinBootstrapTest.kt:54` — `assertNotSame(first, koin.get<AuthenticatedNetworkClient>())` proves replacing platform bindings disposes/recreates the common singleton graph.
- [x] `ComposePresentationModuleTest.kt:80-81` — exact draft-read and time-zone detection assertions prove `GroupSetupViewModel` consumes Koin-resolved setup ports.
- [x] `ComposePresentationModuleTest.kt:117-118` — exact gateway call and `ShareAttendanceLink` effect assertions prove `GameDetailViewModel` consumes the Koin-resolved attendance-share gateway.
- [x] Assisted parameters contain only runtime input/navigation values plus an optional test scope; no service dependency is passed by the route.
- [x] The regenerated iOS framework header contains no Koin bootstrap declarations; Android-required functions use `@HiddenFromObjC` and all other bootstrap functions are internal.

### Gates
```text
rtk ./gradlew :features:groups:allTests --console=plain -q
PASS

rtk ./gradlew :compose-app:allTests --console=plain -q
PASS (existing Compose test deprecation warnings only)

rtk ./gradlew :android-app:testDevDebugUnitTest :android-app:compileDevDebugAndroidTestKotlin --console=plain -q
PASS

rtk ./gradlew :compose-app:linkDebugFrameworkIosArm64 :compose-app:linkDebugFrameworkIosSimulatorArm64 -q
PASS

rtk git diff --check
PASS
```

### Independent verification
- First pass found the missing `AttendanceShareGateway` ViewModel binding; implementation was corrected.
- A later evidence-or-zero pass requested direct presentation-module resolution tests and literal iOS API hiding; both gaps were corrected in `3dbebce`.
- Final pass: PASS with no blocker/high findings.

### Discrimination sensor
- Scratch mutation removed common-module unload/reload from `loadSaqzPlatformDependencies`.
- `reloadingPlatformBindingsRecreatesTheNetworkSingleton` failed against the mutant and passed against production code.
- Result: 1/1 mutation killed; the real worktree was not mutated.

### Test adequacy
All new assertions map directly to NAV-01/NAV-02 graph ownership, consumer wiring, and lifecycle criteria. They assert object identity/replacement or observable port calls and emitted effects; no existing assertions were weakened or removed.

### Conclusion
The authenticated runtime, setup flow, game detail, attendance, and photo consumers now use the verified Koin graph. Manual client/API/state-machine construction was removed from the route/runtime while existing behavior and platform lifecycle boundaries were preserved.
