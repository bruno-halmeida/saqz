# Project Initialization Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name
and follow its Execute flow and Critical Rules.** Do not search for skill files
by filesystem path. The skill is the source of truth for the per-task cycle,
sub-agent delegation, adequacy review, Verifier, and discrimination sensor.

If the skill cannot be activated, STOP and tell the user. Every task includes
its tests, must pass its gate, and produces one atomic commit. Never batch task
commits or weaken tests to pass a gate.

---

**Design**: `.specs/features/project-initialization/design.md`
**Status**: Approved
**Task count**: 25

---

## Test Coverage Matrix

> Generated from the codebase, approved spec, and approved design. Guidelines
> found: no existing test or quality guide; strong defaults apply. The user
> explicitly selected the complete matrix on 2026-07-14.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Backend identity domain/application | unit | All result branches; 1:1 to `AUTH-04..06`, `AUTH-10`, `EDGE-01..02`; no framework context | `backend/features/identity/src/test/**/*.kt` | `backend/gradlew -p backend :features:identity:test` |
| Backend architecture | architecture | One rule per `ARCH-01..08`; representative forbidden dependency must be killed | `backend/architecture-tests/src/test/**/*.kt` | `backend/gradlew -p backend :architecture-tests:test` |
| Backend HTTP/security | integration | Health and session happy path plus every specified `401`, `503`, `500`, profile, and correlation outcome | `backend/bootstrap/src/test/**/*.kt` | `backend/gradlew -p backend :bootstrap:test` |
| Firebase Admin adapter | unit + emulator integration | Every rejection/provider branch plus one real emulator token in the adapter task | `backend/features/identity/src/test/**/*.kt`, `firebase/tests/**` | `backend/gradlew -p backend :features:identity:test :features:identity:emulatorTest` |
| Shared Compose root | common UI | Root renders exact visible text `Saqz`; no navigation/ViewModel/network/Firebase dependency | `mobile/compose-app/src/commonTest/**/*.kt` | `mobile/gradlew -p mobile :compose-app:allTests` |
| Android launcher/Firebase | unit + instrumented smoke | Emulator endpoint/order and visible shared placeholder on Android emulator | `mobile/android-app/src/test/**/*.kt`, `mobile/android-app/src/androidTest/**/*.kt` | `mobile/gradlew -p mobile :android-app:testDebugUnitTest :android-app:connectedDebugAndroidTest` |
| iOS launcher | Xcode UI + simulator smoke | KMP embedding and objectively visible shared placeholder through accessibility query | `mobile/ios-app/SaqzIOSUITests/**/*.swift` | `scripts/check-ios` |
| iOS Firebase | Xcode unit | SwiftPM resolution, exact endpoint, and initialization order | `mobile/ios-app/SaqzIOSTests/**/*.swift` | `scripts/check-ios` |
| Angular shell/Firebase | unit | Root placeholder, no Kotlin UI, fake config, emulator endpoint, and initialization order | `frontend/src/**/*.spec.ts` | `npm --prefix frontend run test:ci` |
| Shell gates and fixtures | integration | Happy path, every finite failure, repeat run, `SIGINT`, `SIGTERM`, PID/account/port cleanup | `tests/scripts/**/*.sh` | `scripts/test-scripts` |
| Credential safety | integration | Clean repository passes; every forbidden file, literal, and scanner class fails | `tests/scripts/check-credentials.test.sh` | `tests/scripts/check-credentials.test.sh` |
| Landing preservation | integration | Baseline diff, Pages workflow contract, index and every local asset return `200` | `tests/scripts/check-landing.test.sh` | `tests/scripts/check-landing.test.sh` |
| CI workflow | contract | Four named gates, aggregate success only for all-success, eight failed/cancelled combinations | `tests/scripts/check-ci.test.sh` | `tests/scripts/check-ci.test.sh` |
| Scope guard | contract | Every finite `SCOPE-01..07` exclusion and clean-path pass | `tests/scripts/check-scope.test.sh` | `tests/scripts/check-scope.test.sh` |
| Repository guide | contract | Every prerequisite/gate and both feature-addition procedures are documented | `tests/scripts/check-readme.test.sh` | `tests/scripts/check-readme.test.sh` |
| Build/config-only changes | build | Native tool resolves configuration with pinned versions and no warnings treated as errors | corresponding build files | stack-specific build gate |

## Gate Check Commands

> Generated for the approved complete test matrix. Commands become available
> as their owning task is completed.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick backend | Identity contracts/adapters | `backend/gradlew -p backend :features:identity:test` |
| Full backend | HTTP, security, architecture, emulator | `backend/gradlew -p backend :shared-kernel:check :features:identity:check :features:identity:emulatorTest :architecture-tests:test :bootstrap:test :bootstrap:emulatorTest` |
| Mobile common | Shared Compose module | `mobile/gradlew -p mobile :compose-app:allTests` |
| Android full | Android launcher/Firebase | `mobile/gradlew -p mobile :android-app:testDebugUnitTest :android-app:connectedDebugAndroidTest` |
| iOS full | Xcode launcher/Firebase | `scripts/check-ios` |
| Angular quick | Angular behavior | `npm --prefix frontend run test:ci` |
| Angular full | Angular quality/build | `npm --prefix frontend ci && npm --prefix frontend run lint && npm --prefix frontend run test:ci && npm --prefix frontend run build` |
| Script full | Shell contracts | `scripts/test-scripts` |
| Build Kotlin | Complete Kotlin/Android gate with running emulators | `scripts/check-gradle` |
| Build all | Final local gate | `scripts/check-all` |

---

## Execution Plan

Phases and tasks execute strictly in order. Tests are co-located with the code
they verify.

### Phase 1: Kotlin And Backend Foundation

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T5A
```

### Phase 2: Identity Security And Emulator

```text
T5A -> T6 -> T7 -> T8 -> T9 -> T10 -> T11
```

### Phase 3: Multiplatform Client Shells

```text
T11 -> T12 -> T13 -> T14 -> T15 -> T16 -> T17
```

### Phase 4: Repository Gates And Documentation

```text
T17 -> T18 -> T19 -> T20 -> T21 -> T22 -> T23 -> T24
```

---

## Task Breakdown

### T1: Establish Pinned Gradle Foundation

**What**: Create the initial Gradle wrapper, settings, version catalog, and convention-plugin foundation with the exact approved Kotlin, Compose, AGP, Spring, Gradle, and JDK versions; T5A separates it into the final backend/mobile workspace model.
**Where**: `gradlew`, `gradlew.bat`, `gradle/`, `settings.gradle.kts`, `build.gradle.kts`, `build-logic/`
**Depends on**: None
**Reuses**: Approved version matrix in `design.md`
**Requirements**: `INIT-01`, `ARCH-01`, `SCOPE-03`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Gradle wrapper is exactly `9.5.0` and validates its distribution checksum.
- [ ] JDK toolchain is exactly 21 and the build rejects a different runtime for project gates.
- [ ] Version catalog pins every Kotlin/JVM dependency from the approved design.
- [ ] Convention plugins distinguish JVM backend, KMP Compose library, and Android application modules.
- [ ] No Spring/Java application plugin is applied to a KMP module.
- [ ] The Gradle foundation exits zero with JDK 21; after T5A its canonical command is `backend/gradlew -p backend help`.

**Tests**: none, build/config layer
**Gate**: `backend/gradlew -p backend help` after T5A (`./gradlew help` at original completion)
**Expected tests**: 0; configuration gate only
**Commit**: `build: establish pinned Gradle foundation`

---

### T2: Create Minimal Shared Kernel Module

**What**: Create `backend:shared-kernel` with only stable correlation/error primitives needed across bootstrap and identity.
**Where**: `backend/shared-kernel/`
**Depends on**: T1
**Reuses**: Backend package constraints from `design.md`
**Requirements**: `ARCH-01`, `ARCH-08`, `SCOPE-01`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Module compiles as plain Kotlin/JVM with no Spring, Firebase, HTTP, or persistence dependency.
- [ ] Public types are limited to correlation/error primitives actually consumed by later modules.
- [ ] Dependency report contains no client or database artifact.
- [ ] Module check exits zero.

**Tests**: none, primitive/config layer
**Gate**: `./gradlew :backend:shared-kernel:check` (historical pre-separation gate; T5A establishes the canonical backend command)
**Expected tests**: 0; compilation and dependency inspection only
**Commit**: `build(backend): add minimal shared kernel`

---

### T3: Define Provider-Neutral Identity Contracts

**What**: Implement identity API/application types and verification use case without framework dependencies.
**Where**: `backend/features/identity/src/main/kotlin/**/{api,application,domain}/`, co-located tests
**Depends on**: T2
**Reuses**: `AuthenticatedPrincipal`, `RawIdentityToken`, `TokenVerification`, `IdentityTokenVerifier`, and `VerifyRequestIdentity` from `design.md`
**Requirements**: `ARCH-02`, `ARCH-03`, `ARCH-05`, `AUTH-10`, `SCOPE-01`, `SCOPE-06`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Contracts use only Kotlin and identity-owned types.
- [ ] Use case delegates exactly once to the output port and preserves every result variant.
- [ ] Repeating the same token produces equal provider-neutral results with the fake verifier.
- [ ] No persistence port or state-changing operation exists in the module.
- [ ] Four unit scenarios pass: verified, rejected, provider unavailable, repeated token.

**Tests**: unit
**Gate**: `./gradlew :backend:features:identity:test` (historical pre-separation gate; T5A establishes the canonical backend command)
**Expected tests**: 4
**Commit**: `feat(identity): define provider-neutral verification contracts`

---

### T4: Create Spring Boot Composition Root And Public Health

**What**: Create the Spring Boot bootstrap module with Actuator health and configuration-only wiring.
**Where**: `backend/bootstrap/`
**Depends on**: T3
**Reuses**: Shared kernel and identity public API
**Requirements**: `ARCH-01`, `ARCH-04`, `AUTH-03`, `SCOPE-01`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Bootstrap contains only entry point, bean wiring, and configuration properties.
- [ ] `GET /actuator/health` without credentials returns `200` and status `UP`.
- [ ] No database or persistence auto-configuration dependency exists.
- [ ] Two integration scenarios pass: application starts and health is public/up.

**Tests**: integration
**Gate**: `./gradlew :backend:bootstrap:test` (historical pre-separation gate; T5A establishes the canonical backend command)
**Expected tests**: 2
**Commit**: `feat(backend): add bootstrap and public health`

---

### T5: Enforce Backend Architecture Boundaries

**What**: Add a dedicated architecture-test module enforcing all backend module and package dependency rules.
**Where**: `backend/architecture-tests/`
**Depends on**: T4
**Reuses**: ArchUnit `1.4.2`, Gradle project graph
**Requirements**: `ARCH-01` through `ARCH-07`, the original dependency-direction subset of `ARCH-08`, `SCOPE-01`, `SCOPE-06`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Eight original architecture rules correspond one-to-one with the pre-AD-006 `ARCH-01..08`; T5A extends `ARCH-08` for independent workspaces.
- [ ] Rules inspect both package imports and Gradle dependencies.
- [ ] Identity domain/application cannot import Spring, Firebase, HTTP, adapters, clients, or another feature.
- [ ] Bootstrap package inventory matches `ARCH-04`.
- [ ] A temporary forbidden Spring import in identity application makes the suite fail and is discarded.
- [ ] All eight original architecture tests pass after restoring valid code.

**Tests**: architecture
**Gate**: `./gradlew :backend:architecture-tests:test` (historical pre-separation gate; T5A establishes the canonical backend command)
**Expected tests**: 8
**Commit**: `test(backend): enforce hexagonal module boundaries`

---

### T5A: Separate Backend Frontend And Mobile Workspaces

**What**: Convert the initial shared Gradle root into an independent backend Gradle build and reserve independent `frontend/` and `mobile/` product roots, with mobile build logic deferred to T12.
**Where**: `backend/{gradlew,gradlew.bat,gradle/,build-logic/,settings.gradle.kts,build.gradle.kts}`, root Gradle files removed, existing backend build references/tests, `tests/scripts/check-workspace-isolation.test.sh`
**Depends on**: T5
**Reuses**: AD-006 and all Phase 1 modules
**Requirements**: `INIT-01`, `ARCH-01`, `ARCH-08`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] No Gradle settings, wrapper, catalog, or build logic remains at repository root.
- [ ] Backend owns its wrapper/settings/catalog/build logic and contains no Compose, AGP, Android, iOS, mobile, or frontend plugin/dependency.
- [ ] Backend module paths are `:shared-kernel`, `:features:identity`, `:bootstrap`, and `:architecture-tests`.
- [ ] Architecture tests reject sibling `includeBuild`, build-logic, project, and artifact dependencies.
- [ ] Backend configuration and tests pass from a scratch copy containing `backend/` but no `frontend/` or `mobile/`.
- [ ] `backend/gradlew -p backend help` and every Phase 1 backend gate exit zero without the duplicate Kotlin-plugin warning.
- [ ] One backend workspace-isolation shell scenario passes.

**Tests**: architecture + build configuration + shell integration
**Gate**: `backend/gradlew -p backend :shared-kernel:check :features:identity:test :bootstrap:test :architecture-tests:test && tests/scripts/check-workspace-isolation.test.sh backend`
**Expected tests**: 14 Gradle + 1 workspace-isolation scenario
**Commit**: `refactor(build): separate backend Gradle workspace`

---

### T6: Implement Firebase Admin Token Verifier

**What**: Implement the identity output adapter and its direct emulator-backed verification harness.
**Where**: `backend/features/identity/src/main/kotlin/**/adapter/output/firebase/`, co-located unit/emulator tests, minimal `firebase.json`
**Depends on**: T5A
**Reuses**: Identity output port; Firebase Admin Java `9.10.0`
**Requirements**: `AUTH-01`, `AUTH-05`, `AUTH-06`, `ARCH-05`, `OBS-03`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Verified Firebase token maps subject and optional email claims exactly.
- [ ] Invalid signature, expiry, and revocation map to `Rejected` without leaking Firebase exceptions.
- [ ] Timeout, connection refusal, and provider service failure map to `ProviderUnavailable`.
- [ ] Firebase imports exist only in output adapter or Spring wiring.
- [ ] A direct adapter integration test creates an emulator user/token and verifies it without starting the backend.
- [ ] Nine adapter scenarios pass: complete claims, absent optional claims, invalid signature, expired, revoked, timeout, connection refusal, provider service failure, and real emulator token.

**Tests**: unit + emulator integration
**Gate**: `backend/gradlew -p backend :features:identity:test :features:identity:emulatorTest`
**Expected tests**: 9
**Commit**: `feat(identity): add Firebase token verifier adapter`

---

### T7: Add Bearer Security Filter

**What**: Implement the HTTP security filter and the exact unauthorized ProblemDetail contract.
**Where**: `backend/features/identity/src/main/kotlin/**/adapter/input/http/`, bootstrap security wiring, co-located integration tests
**Depends on**: T6
**Reuses**: `VerifyRequestIdentity`, Spring Security
**Requirements**: `AUTH-05`, `EDGE-01`, `ARCH-03`, `ARCH-05`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Missing, empty, malformed, and non-Bearer Authorization values do not call the verifier.
- [ ] Rejected credentials never establish a security context.
- [ ] Verified credentials establish only `AuthenticatedPrincipal`.
- [ ] Missing, malformed, invalid, expired, and revoked cases each return `application/problem+json`, status `401`, and code `AUTHENTICATION_REQUIRED`.
- [ ] Six security integration scenarios pass: the five exact unauthorized credential classes plus verified principal establishment.

**Tests**: integration
**Gate**: `backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :architecture-tests:test :bootstrap:test`
**Expected tests**: 6
**Commit**: `feat(identity): enforce bearer authentication filter`

---

### T8: Add Protected Session Endpoint

**What**: Implement `GET /api/session` mapping the authenticated principal to the exact response contract.
**Where**: identity HTTP adapter controller/response and bootstrap integration tests
**Depends on**: T7
**Reuses**: Spring Security principal and `SessionResponse`
**Requirements**: `AUTH-04`, `AUTH-10`, `EDGE-02`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Valid principal returns exact `subject`, `email`, and `emailVerified` fields.
- [ ] Missing email claims return both optional fields as `null`.
- [ ] Two calls with the same principal return equal field values and create no state.
- [ ] Three endpoint integration scenarios pass.

**Tests**: integration
**Gate**: `backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :architecture-tests:test :bootstrap:test`
**Expected tests**: 3
**Commit**: `feat(identity): expose protected session endpoint`

---

### T9: Standardize Problem Details And Safe Diagnostics

**What**: Add exact `401`, `503`, and generic `500` ProblemDetail mappings with correlation/log redaction.
**Where**: backend HTTP adapter, bootstrap filters/handlers, co-located integration tests
**Depends on**: T8
**Reuses**: Shared-kernel correlation/error primitives
**Requirements**: `AUTH-05`, `AUTH-06`, `OBS-01` through `OBS-03`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Rejected credentials return exact `401` media type/status/code.
- [ ] Provider unavailability returns exact `503` media type/status/code.
- [ ] Synthetic unexpected failure returns generic `500` without internal details.
- [ ] Each error response and matching structured log share one non-empty correlation ID.
- [ ] Captured logs/responses from `200`, `401`, `503`, and `500` contain none of the fixture secrets or Firebase exception details.
- [ ] Six diagnostic scenarios pass.

**Tests**: integration
**Gate**: `backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :architecture-tests:test :bootstrap:test`
**Expected tests**: 6
**Commit**: `feat(backend): add safe problem details and correlation`

---

### T10: Enforce Firebase Environment Isolation

**What**: Add local/test emulator wiring and fail-closed validation for every other backend profile.
**Where**: bootstrap configuration/profiles and integration tests
**Depends on**: T9
**Reuses**: Firebase verifier adapter and local project ID
**Requirements**: `AUTH-01`, `AUTH-02`, `EDGE-03`, `AUTH-08`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Local and test profiles initialize Admin SDK against emulator without service account.
- [ ] `dev`, `staging`, and `production` fail before HTTP bind when either emulator switch is present.
- [ ] Absence of emulator switch does not bypass normal credential requirements outside local/test.
- [ ] Nine profile scenarios pass: local with environment switch, test with property switch, each forbidden switch under dev/staging/production, and non-local startup without emulator or credentials.

**Tests**: integration
**Gate**: `backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :architecture-tests:test :bootstrap:test`
**Expected tests**: 9
**Commit**: `feat(backend): isolate Firebase emulator profiles`

---

### T11: Add Emulator-Backed Session Fixture

**What**: Create the Auth Emulator lifecycle fixture and real-token session integration suite.
**Where**: `firebase/`, backend emulator test source set, `tests/scripts/emulator-fixture.test.sh`
**Depends on**: T10
**Reuses**: Firebase CLI `15.23.0`, `/api/session`
**Requirements**: `AUTH-04`, `AUTH-09`, `AUTH-10`, `EDGE-05`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Fixture creates a UUID email account and obtains a real emulator ID token.
- [ ] Session returns exact subject/email/false verification for that token.
- [ ] Repeated-token response is equal.
- [ ] Normal, failed, `SIGINT`, and `SIGTERM` exits delete the account, kill child PIDs, and leave port 9099 bindable.
- [ ] Six fixture/integration scenarios pass with no production credential.

**Tests**: emulator integration + shell integration
**Gate**: `backend/gradlew -p backend :bootstrap:emulatorTest && tests/scripts/emulator-fixture.test.sh`
**Expected tests**: 2 backend emulator scenarios + 4 shell lifecycle scenarios
**Commit**: `test(identity): verify sessions with Auth Emulator`

---

### T12: Create Shared Compose Umbrella Module

**What**: Create the independent mobile Gradle root and `:compose-app` Android/iOS KMP library, shared `SaqzApp()` placeholder, and single static `SaqzMobile` framework.
**Where**: `mobile/` Gradle root and `mobile/compose-app/`
**Depends on**: T11
**Reuses**: Version matrix from T1 without sharing backend build logic; AD-001 and AD-006
**Requirements**: `INIT-04`, `ARCH-08`, `SCOPE-03`, `SCOPE-04`, `SCOPE-06`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Targets are Android library, `iosArm64`, and `iosSimulatorArm64` using default hierarchy.
- [ ] Mobile owns its wrapper, settings, version catalog, and KMP/Android build logic with no backend project dependency.
- [ ] Mobile settings reject sibling `includeBuild`/build-logic references and the mobile gate passes from a scratch copy containing `mobile/` but no `backend/` or `frontend/`.
- [ ] `SaqzApp()` renders exact visible text `Saqz`.
- [ ] One static framework named `SaqzMobile` is produced.
- [ ] No navigation, ViewModel, network, Firebase, domain, application, or use-case dependency/path exists.
- [ ] Common UI test passes and all target compilations resolve.

**Tests**: common UI + shell integration
**Gate**: `mobile/gradlew -p mobile :compose-app:allTests && tests/scripts/check-workspace-isolation.test.sh mobile`
**Expected tests**: 1 common UI + 2 accumulated workspace-isolation scenarios
**Commit**: `feat(mobile): add shared Compose umbrella shell`

---

### T13: Add Android Launcher And Local Firebase Bootstrap

**What**: Create the Android entry point hosting `SaqzApp()` and initializing official Firebase Auth against the emulator.
**Where**: `mobile/android-app/`
**Depends on**: T12
**Reuses**: `:compose-app`, Firebase Android BoM `34.15.0`
**Requirements**: `INIT-01`, `INIT-04`, `AUTH-07`, `AUTH-08`, `SCOPE-02`, `SCOPE-04`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Application ID and fake local Firebase values exactly match design.
- [ ] Firebase Auth receives `10.0.2.2:9099` before any Auth consumer is exposed.
- [ ] Build needs no `google-services.json` and uses no KTX artifact.
- [ ] Android emulator displays the shared `Saqz` placeholder.
- [ ] Two tests pass: initialization order/endpoint and instrumented visible text.

**Tests**: unit + instrumented smoke
**Gate**: Android full
**Expected tests**: 2
**Commit**: `feat(android): add launcher and local Firebase bootstrap`

---

### T14: Add iOS Compose Launcher And UI Smoke Gate

**What**: Create the SwiftUI/Xcode entry point, direct KMP framework integration, UI-test target, and simulator gate.
**Where**: `mobile/ios-app/`, `scripts/check-ios`
**Depends on**: T13
**Reuses**: `SaqzMobile`
**Requirements**: `INIT-03`, `INIT-04`, `CI-02`, `SCOPE-02`, `SCOPE-04`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] SwiftUI shell hosts `MainViewController()` from the single `SaqzMobile` framework.
- [ ] Xcode build phase uses `embedAndSignAppleFrameworkForXcode`.
- [ ] `scripts/check-ios` selects an available simulator whose runtime matches the active Xcode SDK and runs unsigned build/tests.
- [ ] `SaqzIOSUITests` launches the app and observes accessibility text exactly equal to `Saqz`.
- [ ] One iOS UI smoke scenario passes and framework embedding/build exits zero.

**Tests**: Xcode UI + simulator smoke
**Gate**: iOS full
**Expected tests**: 3
**Commit**: `feat(ios): add Compose host and simulator smoke gate`

---

### T15: Add iOS Local Firebase Bootstrap

**What**: Add Firebase Apple through SwiftPM and initialize Auth against the local emulator before Compose root creation.
**Where**: `mobile/ios-app/` Swift package resolution, app delegate/bootstrap, `SaqzIOSTests/`
**Depends on**: T14
**Reuses**: Firebase Apple SDK `12.16.0`, existing Xcode scheme and `scripts/check-ios`
**Requirements**: `AUTH-07`, `AUTH-08`, `SCOPE-02`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Firebase SDK `12.16.0` and exact `Package.resolved` are pinned through SwiftPM.
- [ ] Checked-in fake options exactly match design; no plist is required.
- [ ] `Auth.auth().useEmulator` receives `127.0.0.1:9099` before Compose root creation.
- [ ] One Xcode unit scenario proves exact options, endpoint, and initialization ordering.
- [ ] The existing UI smoke scenario remains green through `scripts/check-ios`.

**Tests**: Xcode unit
**Gate**: iOS full
**Expected tests**: 1 unit + 1 accumulated UI smoke
**Commit**: `feat(ios): initialize local Firebase Auth`

---

### T16: Create Angular Shell And Native Quality Gate

**What**: Generate the Angular workspace with one standalone root component, pinned current-compatible Node/npm/dependencies, lint, tests, and production build.
**Where**: `frontend/`, `.nvmrc`
**Depends on**: T15
**Reuses**: Angular version matrix from design
**Requirements**: `INIT-02`, `INIT-05`, `SCOPE-02` through `SCOPE-04`, `SCOPE-06`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Node 26.5.0 and npm 12.0.1 are pinned.
- [ ] Angular/CLI 22.0.6, TypeScript 6.0.3, RxJS 7.8.2, and angular-eslint 22.1.0 are lockfile-pinned.
- [ ] One standalone root component renders exact visible text `Saqz`.
- [ ] No router, navigation, feature screen, generated client, Kotlin artifact, or auth flow exists.
- [ ] Two component tests pass: placeholder present and no additional route/UI surface.
- [ ] `npm ci`, lint, tests, and production build all exit zero.

**Tests**: unit
**Gate**: Angular full
**Expected tests**: 2
**Commit**: `feat(web): add Angular application shell`

---

### T17: Add Angular Local Firebase Bootstrap

**What**: Initialize the modular Firebase web SDK from checked-in fake local options and connect Auth to the emulator before Angular bootstrap.
**Where**: `frontend/src/app/firebase/`, environment source, co-located specs
**Depends on**: T16
**Reuses**: Firebase JavaScript SDK `12.16.0`
**Requirements**: `AUTH-07`, `AUTH-08`, `SCOPE-02`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Fake project/app values exactly match design and are TypeScript constants, not `.env`.
- [ ] `connectAuthEmulator` receives `http://127.0.0.1:9099` before app bootstrap completes.
- [ ] No session service, token source, route guard, redirect, or auth UI is added.
- [ ] Two unit scenarios pass: exact endpoint/options and initialization ordering.
- [ ] Angular full gate remains green.

**Tests**: unit
**Gate**: Angular full
**Expected tests**: 2
**Commit**: `feat(web): initialize local Firebase Auth`

---

### T18: Add Credential Safety Gate

**What**: Implement the tracked-file, literal-pattern, and Gitleaks credential gate with discrimination fixtures.
**Where**: `scripts/check-credentials`, `tests/scripts/check-credentials.test.sh`
**Depends on**: T17
**Reuses**: Gitleaks `8.30.1`; Git tracked-file inventory
**Requirements**: `AUTH-08`, `OBS-02`, `SCOPE-07`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Clean tracked repository passes.
- [ ] Forbidden Android config, Apple config, and non-example env each fail in isolated scratch state.
- [ ] Private-key and service-account literals each fail in isolated scratch state.
- [ ] Gitleaks credential, private-key, and bearer-token classifications each fail in isolated scratch state.
- [ ] `.specs/` remains ignored and untracked.
- [ ] Ten named gate scenarios pass without altering the working tree.

**Tests**: shell integration
**Gate**: `tests/scripts/check-credentials.test.sh`
**Expected tests**: 10
**Commit**: `test(security): add credential safety gate`

---

### T19: Add Landing Preservation Gate

**What**: Implement baseline diff, Pages workflow contract, and local asset HTTP checks without changing landing content.
**Where**: `scripts/check-landing`, `tests/scripts/check-landing.test.sh`
**Depends on**: T18
**Reuses**: Baseline `c03a8ccbc800b70a982b1f9bb93a4b0af3d87c44`, existing Pages workflow
**Requirements**: `LAND-01` through `LAND-03`, `CI-03`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Diff gate proves no landing content change from baseline.
- [ ] Workflow gate proves trigger, dispatch, artifact path, and environment remain intact.
- [ ] Local server returns `200` for index and every referenced local asset.
- [ ] Test cleanup leaves no server process/port behind.
- [ ] Three landing scenarios pass.

**Tests**: shell integration
**Gate**: `tests/scripts/check-landing.test.sh`
**Expected tests**: 3
**Commit**: `test(landing): add preservation gate`

---

### T20: Add Scope Guard Gate

**What**: Implement a finite repository guard for every excluded artifact/path in `SCOPE-01..07` and the single-client-root constraints.
**Where**: `scripts/check-scope`, `tests/scripts/check-scope.test.sh`
**Depends on**: T19
**Reuses**: Spec's exact prohibited dependency, path, route, workflow, and Git inventories
**Requirements**: `ARCH-07`, `SCOPE-01` through `SCOPE-07`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Clean repository passes all seven scope groups.
- [ ] Scratch mutations for database/persistence, auth/product UI, OpenAPI generation, extra UI/navigation, production deployment, cross-workspace composite/shared build logic, client domain/application, and tracked `.specs` each fail.
- [ ] Guard ignores documentation/test fixtures only where the spec permits.
- [ ] Nine scope/architecture-boundary scenarios pass.

**Tests**: shell contract/integration
**Gate**: `tests/scripts/check-scope.test.sh`
**Expected tests**: 9
**Commit**: `test: enforce initialization scope boundaries`

---

### T21: Add Complete Kotlin And Android Gate

**What**: Implement one explicit repository gate that invokes both independent Gradle builds and contains every backend, emulator, KMP, Android, credential, and scope check from the selected matrix.
**Where**: `scripts/check-gradle`, `tests/scripts/check-gradle.test.sh`
**Depends on**: T20
**Reuses**: Backend/KMP/Android commands, `scripts/check-credentials`, `scripts/check-scope`
**Requirements**: `INIT-01`, `AUTH-08`, `SCOPE-01` through `SCOPE-07`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Gate invokes credential and scope checks before compilation/tests.
- [ ] Gate invokes only `backend/gradlew -p backend` for backend unit/integration/architecture tests and both Firebase emulator test tasks.
- [ ] Gate invokes only `mobile/gradlew -p mobile` for KMP checks, Android unit tests, and Android instrumented smoke tests.
- [ ] Gate requires or provisions Firebase Auth Emulator and an Android emulator; it fails rather than skipping either suite.
- [ ] One complete inventory test asserts both independent Gradle wrappers and every backend, Firebase emulator, KMP, Android, credential, and scope command in this contract.
- [ ] Four failure tests prove credential, scope, Firebase emulator, and Android instrumented failures cannot be ignored.

**Tests**: shell integration
**Gate**: `tests/scripts/check-gradle.test.sh`
**Expected tests**: 5
**Commit**: `build: add complete Kotlin and Android gate`

---

### T22: Add Aggregate Local Gate And Signal Cleanup

**What**: Implement `scripts/check-all` with exact platform order, fail-fast behavior, host prerequisites, and signal cleanup.
**Where**: `scripts/check-all`, `scripts/test-scripts`, `tests/scripts/check-all.test.sh`
**Depends on**: T21
**Reuses**: `scripts/check-gradle`, Angular full gate, `scripts/check-ios`, `scripts/check-landing`
**Requirements**: `INIT-06`, `EDGE-04`, `EDGE-05`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] Gate order is complete Gradle, Angular, iOS, landing.
- [ ] Because Gradle gate owns credential and scope checks, neither can be bypassed by aggregate execution.
- [ ] All-success returns zero; each of four gate failures stops subsequent gates and returns non-zero.
- [ ] Unsupported host reports the documented limitation without weakening CI requirements.
- [ ] `SIGINT` and `SIGTERM` kill owned child PIDs and leave ports bindable.
- [ ] Eight named scenarios pass: all-success, four fail-fast positions, unsupported host, `SIGINT`, and `SIGTERM`.

**Tests**: shell integration
**Gate**: `scripts/test-scripts`
**Expected tests**: 8
**Commit**: `build: add aggregate local quality gate`

---

### T23: Add Pull Request Quality Workflow

**What**: Add the four native GitHub Actions gate jobs, tested aggregate status evaluator, and final `initialization-gate`.
**Where**: `.github/workflows/initialization-gate.yml`, `scripts/evaluate-ci-gates`, `tests/scripts/check-ci.test.sh`
**Depends on**: T22
**Reuses**: Native scripts and existing Pages deployment workflow
**Requirements**: `CI-01` through `CI-10`, `EDGE-04`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`, `security-best-practices`

**Done when**:
- [ ] Every PR to `main` runs named Gradle, Angular, iOS, and landing jobs.
- [ ] `gradle-gate` starts Firebase Auth Emulator and an Android emulator, then runs `scripts/check-gradle` without skipping emulator/instrumented tests.
- [ ] Android CI starts ADB before AVD launch, pins a compatible emulator build, and enforces finite boot/job timeouts.
- [ ] Android CI uses the API 30 `google_atd` x86 Automated Test Device with bounded memory instead of a generic phone image.
- [ ] Android CI directly grants and verifies runner read/write access to `/dev/kvm` before emulator launch.
- [ ] iOS CI pins Xcode 26.4, requires a matching simulator runtime, and enforces a finite job timeout.
- [ ] The invoked Gradle gate necessarily runs credential and scope checks.
- [ ] iOS job runs on macOS; other gates use supported Linux runners.
- [ ] Aggregate job succeeds only when all four named results are success.
- [ ] Each gate failure and cancellation produces aggregate failure in all eight combinations.
- [ ] Workflow requires no production credential, signing, database, or deployment secret.
- [ ] Existing Pages workflow is unchanged.
- [ ] Twenty-one named workflow cases pass: four job identities/commands, macOS iOS runner, Gradle emulator provisioning, Android ATD, KVM access, and boot guards, iOS Xcode/runtime guard and Java fallback, no-secret contract, Pages preservation, and eight aggregate failure/cancellation evaluations.

**Tests**: shell contract
**Gate**: `tests/scripts/check-ci.test.sh`
**Expected tests**: 21
**Commit**: `ci: enforce multiplatform initialization gates`

---

### T24: Document Reproducible Architecture And Commands

**What**: Expand the tracked README with prerequisites, every native gate, architecture boundaries, and backend/mobile feature-addition procedures.
**Where**: `README.md`, `tests/scripts/check-readme.test.sh`
**Depends on**: T23
**Reuses**: Existing landing instructions, approved design, final commands
**Requirements**: `ARCH-06`, `INIT-01` through `INIT-06`, `EDGE-04`, `AUTH-07`, `AUTH-08`

**Tools**:
- MCP: none
- Skill: `tlc-spec-driven`

**Done when**:
- [ ] README retains landing development and Pages instructions.
- [ ] JDK 21, Node 22.22.3, Android SDK, Xcode 26.4, simulator, Firebase CLI, and Gitleaks prerequisites are explicit.
- [ ] Gradle, Angular, Android, iOS, emulator, landing, credential, scope, scripts, and aggregate commands are exact.
- [ ] Dependency-direction rules and package boundaries are documented.
- [ ] Independent workspace boundaries/commands, backend feature-module procedure, and KMP feature/umbrella procedure are complete.
- [ ] Local fake Firebase values and production config exclusions are explicit.
- [ ] Twelve named README assertions pass: retained landing instructions, seven prerequisite/command groups, dependency rules, backend feature procedure, KMP umbrella procedure, and Firebase exclusions.
- [ ] Final `scripts/check-all` passes with the documented supported environment.

**Tests**: shell contract
**Gate**: build all
**Expected tests**: `check-readme` 12; final backend 49, mobile/launcher 5, Angular 4, shell/contract 69
**Commit**: `docs: document reproducible project foundation`

---

## Phase Execution Map

```text
Phase 1: T1 -> T2 -> T3 -> T4 -> T5 -> T5A
                                          |
Phase 2:                                  v
          T6 -> T7 -> T8 -> T9 -> T10 -> T11
                                             |
Phase 3:                                     v
          T12 -> T13 -> T14 -> T15 -> T16 -> T17
                                                |
Phase 4:                                 v
          T18 -> T19 -> T20 -> T21 -> T22 -> T23 -> T24
```

Execution is strictly sequential. Each task depends on its immediately previous
task, preserving a single testable baseline and one atomic commit per task.

---

## Task Granularity Check

| Task | Single deliverable | Status |
| --- | --- | --- |
| T1 | Initial Gradle foundation | PASS |
| T2 | Shared-kernel module | PASS |
| T3 | Identity contracts/use case | PASS |
| T4 | Bootstrap health component | PASS |
| T5 | Architecture-test component | PASS |
| T5A | Independent backend Gradle workspace | PASS |
| T6 | Firebase output adapter | PASS |
| T7 | Bearer security filter | PASS |
| T8 | Session endpoint | PASS |
| T9 | Problem/correlation boundary | PASS |
| T10 | Environment isolation component | PASS |
| T11 | Emulator fixture component | PASS |
| T12 | Shared Compose umbrella component | PASS |
| T13 | Android launcher component | PASS |
| T14 | iOS Compose launcher component | PASS |
| T15 | iOS Firebase bootstrap | PASS |
| T16 | Angular shell component | PASS |
| T17 | Angular Firebase bootstrap | PASS |
| T18 | Credential gate component | PASS |
| T19 | Landing gate component | PASS |
| T20 | Scope guard component | PASS |
| T21 | Complete Kotlin/Android gate component | PASS |
| T22 | Aggregate local gate component | PASS |
| T23 | PR quality workflow component | PASS |
| T24 | Repository guide | PASS |

---

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | Phase 1 root | PASS |
| T2 | T1 | T1 -> T2 | PASS |
| T3 | T2 | T2 -> T3 | PASS |
| T4 | T3 | T3 -> T4 | PASS |
| T5 | T4 | T4 -> T5 | PASS |
| T5A | T5 | T5 -> T5A | PASS |
| T6 | T5A | T5A -> T6 | PASS |
| T7 | T6 | T6 -> T7 | PASS |
| T8 | T7 | T7 -> T8 | PASS |
| T9 | T8 | T8 -> T9 | PASS |
| T10 | T9 | T9 -> T10 | PASS |
| T11 | T10 | T10 -> T11 | PASS |
| T12 | T11 | T11 -> T12 | PASS |
| T13 | T12 | T12 -> T13 | PASS |
| T14 | T13 | T13 -> T14 | PASS |
| T15 | T14 | T14 -> T15 | PASS |
| T16 | T15 | T15 -> T16 | PASS |
| T17 | T16 | T16 -> T17 | PASS |
| T18 | T17 | T17 -> T18 | PASS |
| T19 | T18 | T18 -> T19 | PASS |
| T20 | T19 | T19 -> T20 | PASS |
| T21 | T20 | T20 -> T21 | PASS |
| T22 | T21 | T21 -> T22 | PASS |
| T23 | T22 | T22 -> T23 | PASS |
| T24 | T23 | T23 -> T24 | PASS |

---

## Test Co-location Validation

| Task | Layer | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Build/config | build | none + build gate | PASS |
| T2 | Primitive/config | build | none + build gate | PASS |
| T3 | Identity application | unit | unit | PASS |
| T4 | Backend HTTP/bootstrap | integration | integration | PASS |
| T5 | Backend architecture | architecture | architecture | PASS |
| T5A | Backend workspace separation | architecture + build + integration | architecture + build + shell integration | PASS |
| T6 | Firebase adapter | unit + emulator integration | unit + emulator integration | PASS |
| T7 | Backend security | integration | integration | PASS |
| T8 | Backend endpoint | integration | integration | PASS |
| T9 | Error/observability HTTP | integration | integration | PASS |
| T10 | Backend config/profile | integration | integration | PASS |
| T11 | Emulator/fixture | integration | emulator + shell integration | PASS |
| T12 | Mobile workspace/shared Compose root | common UI + integration | common UI + shell integration | PASS |
| T13 | Android launcher | unit + instrumented | unit + instrumented | PASS |
| T14 | iOS launcher | Xcode UI + simulator | Xcode UI + simulator | PASS |
| T15 | iOS Firebase | Xcode unit | Xcode unit | PASS |
| T16 | Angular shell | unit | unit | PASS |
| T17 | Angular Firebase | unit | unit | PASS |
| T18 | Credential safety | integration | shell integration | PASS |
| T19 | Landing preservation | integration | shell integration | PASS |
| T20 | Scope guard | contract | contract + integration | PASS |
| T21 | Kotlin/Android gate | integration | shell integration | PASS |
| T22 | Shell orchestration | integration | shell integration | PASS |
| T23 | CI workflow | contract | contract | PASS |
| T24 | Repository guide | contract | contract | PASS |

---

## Requirement Coverage

| Requirement | Implemented/verified by |
| --- | --- |
| `INIT-01` | T1, T5A, T13, T21, T24 |
| `INIT-02` | T16, T22, T24 |
| `INIT-03` | T14, T22, T24 |
| `INIT-04` | T12, T13, T14 |
| `INIT-05` | T16 |
| `INIT-06` | T22, T24 |
| `ARCH-01` | T1, T2, T3, T4, T5, T5A |
| `ARCH-02` | T3, T5 |
| `ARCH-03` | T3, T5, T7 |
| `ARCH-04` | T4, T5 |
| `ARCH-05` | T3, T5, T6, T7 |
| `ARCH-06` | T24 |
| `ARCH-07` | T5, T20 |
| `ARCH-08` | T5, T5A, T12, T20 |
| `AUTH-01` | T6, T10, T11 |
| `AUTH-02` | T10 |
| `AUTH-03` | T4 |
| `AUTH-04` | T8, T11 |
| `AUTH-05` | T6, T7, T9 |
| `AUTH-06` | T6, T9 |
| `AUTH-07` | T13, T15, T17 |
| `AUTH-08` | T10, T13, T15, T17, T18, T21 |
| `AUTH-09` | T11 |
| `AUTH-10` | T3, T8, T11 |
| `LAND-01` | T19 |
| `LAND-02` | T19, T23 |
| `LAND-03` | T19, T24 |
| `CI-01` | T23 |
| `CI-02` | T14, T23 |
| `CI-03` | T19, T23 |
| `CI-04` | T23 |
| `CI-05` | T23 |
| `CI-06` | T23 |
| `CI-07` | T23 |
| `CI-08` | T14, T23 |
| `CI-09` | T23 |
| `CI-10` | T23 |
| `OBS-01` | T9 |
| `OBS-02` | T9, T18 |
| `OBS-03` | T6, T9 |
| `SCOPE-01` | T2, T5, T20, T21 |
| `SCOPE-02` | T13, T15, T16, T17, T20, T21 |
| `SCOPE-03` | T1, T12, T16, T20, T21 |
| `SCOPE-04` | T12, T13, T14, T16, T20, T21 |
| `SCOPE-05` | T20, T23 |
| `SCOPE-06` | T3, T5, T12, T20, T21 |
| `SCOPE-07` | T18, T20, T21 |
| `EDGE-01` | T7 |
| `EDGE-02` | T8 |
| `EDGE-03` | T10 |
| `EDGE-04` | T22, T23, T24 |
| `EDGE-05` | T11, T22 |

**Coverage:** 48 of 48 requirements mapped; 0 unmapped.
