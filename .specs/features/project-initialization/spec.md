# Project Initialization Specification

## Problem Statement

Saqz currently contains only its static pre-launch landing page, while the
ClickUp backlog assumes a multiplatform product whose architecture and web
stack no longer match the decisions made for implementation. The team needs a
reproducible project foundation that supports backend, Angular, Android, and
iOS development without coupling business rules to frameworks or requiring
production Firebase credentials.

## Goals

- [ ] Establish independently buildable backend, Angular, Android, and iOS
  foundations in one repository.
- [ ] Enforce feature-oriented hexagonal boundaries in the Kotlin backend.
- [ ] Prove Firebase bearer-token validation through a protected backend
  endpoint and a credential-free local emulator test.
- [ ] Keep the existing static landing page and GitHub Pages deployment
  behavior unchanged.
- [ ] Provide one documented, reproducible gate for local development and pull
  requests.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Login, signup, password reset, Google Sign-In, and visual logout | These are user-facing authentication features, not initialization infrastructure. |
| User persistence and Firebase-to-database synchronization | PostgreSQL and Flyway belong to a later increment. |
| Roles, authorization, groups, invitations, and onboarding | They require business behavior beyond identity verification. |
| PostgreSQL, Flyway, and production database provisioning | Covered by dedicated setup tasks after initialization. |
| OpenAPI client generation | The contract mechanism is decided, but generation is a separate task. |
| Design-system components and production screens | Initialization provides only platform shells. |
| Production deployment of backend, Angular, Android, or iOS | This increment validates builds and CI only. |
| Migration or rewrite of `landing-page/` | The existing static landing remains independently deployed. |
| Empty feature modules for future domains | A module is created only when it owns implemented behavior. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Status |
| --- | --- | --- | --- |
| Repository model | Hybrid monorepo with independent `backend/`, `frontend/`, and `mobile/` workspaces | Keeps coordinated changes together while isolating backend Gradle from KMP/Android compatibility constraints. | Confirmed for Spec |
| Web application | Angular with TypeScript | Explicit user choice; web is not Compose Multiplatform. | Confirmed for Spec |
| Mobile application | Compose Multiplatform for Android and iOS | Shares mobile screens and logic while retaining native launchers. | Confirmed for Spec |
| Backend architecture | Feature-oriented hexagonal architecture | Explicit user requirement for business logic organized by feature. | Confirmed for Spec |
| Backend feature isolation | One Gradle module per implemented feature | Makes invalid cross-feature dependencies fail at build or architecture-test time. | Confirmed for Spec |
| Business-rule authority | Kotlin backend only | Avoids duplicating authoritative rules in Angular or mobile clients. | Confirmed for Spec |
| Existing landing page | Keep `landing-page/` and GitHub Pages workflow unchanged | Preserves the already working acquisition surface. | Confirmed for Spec |
| Initialization auth depth | Infrastructure only | Explicitly excludes user-facing auth behavior while proving token validation. | Confirmed for Spec |
| Kotlin and framework versions | Select current mutually compatible stable versions during Design and pin them in wrapper/catalog files | Exact versions require official compatibility research; no version is assumed in this spec. | Confirmed for Spec |
| Node package management | npm with committed `package-lock.json` | Angular-native, reproducible, and requires no extra package manager. | Confirmed for Spec |
| JVM and Node baselines | JDK 21 and Node 26 | Latest supported Node baseline accepted while starting the project. | Confirmed for Spec |
| Protected proof endpoint | `GET /api/session` | Minimal read-only proof of authenticated request identity. | Confirmed for Spec |
| Unauthorized response disclosure | Missing, malformed, invalid, and expired credentials share one public error code | Prevents verifier detail leakage and keeps clients independent of provider failure detail. | Confirmed for Spec |
| Landing comparison baseline | Commit `c03a8ccbc800b70a982b1f9bb93a4b0af3d87c44` | This is the repository tip containing the approved landing immediately before initialization specification work. | Confirmed for Spec |
| TLC artifact storage | Keep `.specs/` ignored by Git | Explicit user choice; specifications, tasks, and validation reports remain local rather than versioned. | Confirmed for Spec |

**Open questions:** none. Design may refine internal implementation choices but
must preserve every observable requirement and the confirmed feature boundary.

---

## User Stories

### P1: Reproducible Multiplatform Workspace - MVP

**User Story**: As a Saqz developer, I want each product surface to use its
native toolchain inside one repository so that I can build and change backend,
web, Android, and iOS predictably.

**Why P1**: No product feature can be implemented safely until every target has
a reproducible shell and build contract.

**Acceptance Criteria**:

1. **INIT-01** - WHEN a developer runs the documented Gradle gate with JDK 21
   THEN the system SHALL compile and test the backend, KMP shared code, and
   Android launcher with a zero exit status.
2. **INIT-02** - WHEN a developer uses Node 26 to run `npm ci` followed by the
   documented Angular lint, test, and production-build commands THEN the system
   SHALL complete every command with a zero exit status using the committed
   `package-lock.json`.
3. **INIT-03** - WHEN a developer runs the documented credential-free
   `xcodebuild` command against an iOS Simulator destination THEN the system
   SHALL assemble the iOS launcher with code signing disabled and a zero exit
   status.
4. **INIT-04** - WHEN the Android and iOS launchers start THEN each system SHALL
   render the same shared Compose placeholder containing the visible text
   `Saqz`.
5. **INIT-05** - WHEN the Angular development application starts THEN the
   system SHALL render an Angular-owned placeholder containing the visible text
   `Saqz` without loading UI code compiled from Kotlin.
6. **INIT-06** - WHEN a developer runs `scripts/check-all` on macOS with the
   documented prerequisites THEN the script SHALL invoke, in order, the Gradle,
   Angular, iOS, and landing gates defined by `INIT-01`, `INIT-02`, `INIT-03`,
   and `LAND-03`; it SHALL stop at the first failed gate and return non-zero, or
   return zero only after all four gates pass.

**Independent Test**: On a clean checkout, install documented prerequisites,
run `scripts/check-all`, launch each client shell, and observe the specified
placeholder text.

---

### P1: Enforced Hexagonal Backend Foundation - MVP

**User Story**: As a backend developer, I want business features isolated by
hexagonal boundaries so that domain behavior can evolve without framework or
cross-feature coupling.

**Why P1**: The architecture must be executable policy before business modules
begin accumulating dependencies.

**Acceptance Criteria**:

1. **ARCH-01** - WHEN the Gradle project is evaluated THEN the system SHALL
   expose separate modules for the Spring Boot composition root, the minimal
   shared kernel, and the identity feature.
2. **ARCH-02** - WHEN architecture tests inspect identity domain and
   application classes THEN the system SHALL fail the test if either layer
   depends on Spring, Firebase, persistence, HTTP adapter packages, or another
   feature's internal packages.
3. **ARCH-03** - WHEN architecture tests inspect adapter dependencies THEN the
   system SHALL fail the test if an input or output adapter is referenced by
   domain or application code.
4. **ARCH-04** - WHEN architecture tests inspect the Spring Boot composition
   root THEN its non-test source SHALL contain only the application entry point,
   Spring bean wiring, and environment/configuration-property declarations; it
   SHALL declare no package segment named `domain`, `application`, `usecase`,
   `port`, or `adapter`.
5. **ARCH-05** - WHEN the identity feature verifies a credential THEN its
   domain, application, and public API signatures SHALL reference only Kotlin
   standard-library types or types declared in those three identity packages;
   they SHALL NOT import or reference Spring, HTTP, Firebase SDK, or identity
   adapter types.
6. **ARCH-06** - WHEN a developer reads the repository guide THEN it SHALL
   define the allowed dependency direction and the steps for adding one new
   feature module without modifying another feature's internals.
7. **ARCH-07** - WHEN Gradle settings and backend source roots are inspected at
   the end of initialization THEN `identity` SHALL be the only module under
   `backend/features/`; no module or source package SHALL exist for groups,
   athletes, games, finance, or subscriptions.
8. **ARCH-08** - WHEN build dependencies and settings are inspected THEN
   `backend/` and `mobile/` SHALL own independent Gradle wrappers, settings,
   catalogs, and build logic; neither SHALL use a composite build, shared build
   logic, project dependency, or compiled artifact from the sibling workspace;
   `frontend/` SHALL NOT compile against backend Gradle artifacts or backend
   domain classes; and each Gradle workspace SHALL evaluate successfully when
   its sibling product workspace is unavailable.

**Independent Test**: Run the architecture-test suite, then introduce a
temporary forbidden Spring dependency in identity domain code and confirm that
the suite fails before discarding the mutation.

---

### P1: Credential-Free Authentication Infrastructure - MVP

**User Story**: As a developer, I want Firebase authentication infrastructure
to run locally without production credentials so that protected backend
behavior can be developed and tested safely.

**Why P1**: Authentication is an external boundary and must be proven before
features rely on request identity.

**Acceptance Criteria**:

1. **AUTH-01** - WHEN the backend starts with the `local` or `test` profile and
   the documented emulator configuration THEN the identity output-adapter
   package SHALL initialize the Firebase Admin SDK and use Firebase Auth
   Emulator for token verification without loading a service-account
   credential; no package outside the identity output adapter or Spring wiring
   SHALL import a Firebase Admin SDK type.
2. **AUTH-02** - WHEN the backend starts under any profile other than `local`
   or `test` while `FIREBASE_AUTH_EMULATOR_HOST` is set or the property
   `saqz.firebase.emulator.enabled` equals `true` THEN startup SHALL fail with a
   non-zero exit status before binding an HTTP port.
3. **AUTH-03** - WHEN a caller requests the public health endpoint without an
   Authorization header THEN the system SHALL return HTTP `200` and a body
   reporting status `UP`.
4. **AUTH-04** - WHEN a credential-free test fixture creates a temporary
   account through the Auth Emulator REST API and sends its ID token as a
   bearer credential to `GET /api/session` THEN the system SHALL return HTTP
   `200` with JSON fields `subject`, `email`, and `emailVerified`; `subject`
   SHALL equal the token subject, `email` SHALL equal the fixture account
   email, and `emailVerified` SHALL equal `false`.
5. **AUTH-05** - WHEN `GET /api/session` receives a missing, malformed, invalid,
   expired, or revoked bearer credential THEN the system SHALL return HTTP `401`,
   content type `application/problem+json`, numeric field `status` equal to
   `401`, and string field `code` equal to `AUTHENTICATION_REQUIRED`.
6. **AUTH-06** - WHEN Firebase token verification is unavailable for a reason
   other than token absence, malformed syntax, invalid signature, revoked
   token, or token expiry, including connection refusal, timeout, and provider
   service failure, THEN the system SHALL return HTTP `503`, content type
   `application/problem+json`, numeric field `status` equal to `503`, and
   string field `code` equal to `IDENTITY_PROVIDER_UNAVAILABLE`.
7. **AUTH-07** - WHEN Angular, Android, and iOS use their documented local
   configuration THEN each system's effective Firebase Auth endpoint SHALL be
   `127.0.0.1:9099` for Angular and the iOS Simulator and `10.0.2.2:9099` for
   the Android Emulator, verified through a client initialization test, and no
   client build SHALL require an untracked Firebase platform file.
8. **AUTH-08** - WHEN the repository is scanned for tracked authentication
   configuration using `git ls-files` and the documented secret scanner THEN
   the scan SHALL pass with no file named `google-services.json`,
   `GoogleService-Info.plist`, or non-example `.env`; no match for
   a private-key boundary or a JSON service-account type marker; and no scanner finding
   classified as a credential, private key, or bearer token.
9. **AUTH-09** - WHEN the emulator-backed fixture runs repeatedly or after a
   failed prior run THEN it SHALL use a unique account identity for that run
   and, on normal exit, test failure, `SIGINT`, or `SIGTERM`, SHALL stop every
   child process it started, leave port `9099` bindable, and leave the Auth
   Emulator with no fixture account from that run.
10. **AUTH-10** - WHEN the same valid bearer token calls `GET /api/session`
    twice THEN both responses SHALL have equal `subject`, `email`, and
    `emailVerified` values, and the identity application path SHALL invoke no
    persistence port or state-changing operation.
11. **AUTH-11** - WHEN the identity output-adapter emulator test starts Firebase
    Auth Emulator THEN it SHALL use the shared `firebase/session-fixture`
    lifecycle instead of launching `firebase-tools` directly, and its cleanup
    SHALL prove removal of the fixture account and token, termination of every
    child process, and bindability of port `9099` before another emulator task
    starts.

**Independent Test**: Start the emulator and backend through the documented
local command, run the fixture against `/api/session`, exercise all specified
error outcomes through verifier test doubles, and run client initialization
smoke tests without credentials.

---

### P1: Preserved Landing Page - MVP

**User Story**: As the product owner, I want project initialization to preserve
the existing pre-launch page so that acquisition is not disrupted by product
scaffolding.

**Why P1**: The landing page is the only currently deployed product surface.

**Acceptance Criteria**:

1. **LAND-01** - WHEN initialization changes are compared with baseline commit
   `c03a8ccbc800b70a982b1f9bb93a4b0af3d87c44` THEN the diff SHALL contain no content changes under
   `landing-page/`.
2. **LAND-02** - WHEN `.github/workflows/deploy-pages.yml` is compared with
   baseline commit `c03a8ccbc800b70a982b1f9bb93a4b0af3d87c44` THEN it SHALL retain its existing
   `push` trigger for `main` changes under `landing-page/**`, retain manual
   `workflow_dispatch`, upload `landing-page` as the Pages artifact, and deploy
   that artifact to the `github-pages` environment.
3. **LAND-03** - WHEN the documented landing check serves `landing-page/` THEN
   an HTTP request for `/index.html` SHALL return `200`, and every local asset
   URL referenced by that document SHALL return `200`.

**Independent Test**: Inspect the feature diff against its recorded base commit
and run the static-site link check against a local HTTP server.

---

### P1: Pull Request Quality Gate - MVP

**User Story**: As a maintainer, I want CI to enforce the same credential-free
checks documented for local development so that invalid foundations cannot be
merged silently.

**Why P1**: Reproducibility is only useful when the repository enforces it.

**Acceptance Criteria**:

1. **CI-01** - WHEN any pull request targets `main` THEN GitHub Actions SHALL run
   distinct jobs named `gradle-gate`, `angular-gate`, `ios-gate`, and
   `landing-gate` using the native commands defined by `INIT-01`, `INIT-02`,
   `INIT-03`, and `LAND-03`.
2. **CI-02** - WHEN `ios-gate` runs THEN GitHub Actions SHALL use a macOS runner
   and the documented credential-free `xcodebuild` simulator command.
3. **CI-03** - WHEN `landing-gate` runs THEN it SHALL execute `LAND-03` without
   modifying or replacing `.github/workflows/deploy-pages.yml`.
4. **CI-04** - WHEN all four named gate jobs finish THEN a job named
   `initialization-gate` SHALL succeed only if `gradle-gate`, `angular-gate`,
   `ios-gate`, and `landing-gate` all succeeded; any failed or cancelled gate
   SHALL make `initialization-gate` fail.
5. **CI-05** - WHEN all CI gates execute THEN none SHALL require production
   Firebase credentials, signing identities, production database credentials,
   or deployment credentials.
6. **CI-06** - WHEN CI provisions native toolchains THEN platform gates SHALL
   preserve toolchain environment provided by GitHub Actions setup steps, and
   Android emulator creation SHALL write AVD metadata into the same directory
   searched by the emulator before boot is attempted.
7. **CI-07** - WHEN `gradle-gate` provisions its Android emulator THEN it SHALL
   start the ADB server before launching the AVD, pin a known-compatible stable
   emulator build, and bound both emulator boot and job execution with finite
   timeouts.
8. **CI-08** - WHEN `ios-gate` runs on a GitHub macOS image THEN it SHALL pin the
   documented Xcode baseline, select only a simulator runtime matching that
   Xcode's active iOS Simulator SDK, and bound job execution with a finite
   timeout.
9. **CI-09** - WHEN `gradle-gate` runs its Android instrumented smoke test on a
   standard GitHub Linux runner THEN it SHALL provision the API 30
   `google_atd` x86 Automated Test Device with the `pixel_2` profile and a
   bounded 2048 MB RAM allocation.
10. **CI-10** - WHEN `gradle-gate` enables Linux KVM THEN it SHALL directly grant
    read/write access to `/dev/kvm` and verify that access as the runner user
    before launching the emulator, preventing silent fallback to software
    acceleration.

**Independent Test**: Run the four workflow-equivalent commands locally,
inspect the aggregate job's declared dependencies and success condition, then
evaluate that condition once for each gate as `failure` and once as `cancelled`
while the other three are `success`; every one of the eight evaluations SHALL
produce a failed aggregate outcome.

---

### P1: Safe Diagnostic Contract - MVP

**User Story**: As a maintainer, I want failures to be traceable without
exposing authentication material so that local and CI incidents can be
diagnosed safely.

**Why P1**: Authentication and external-provider failures are otherwise either
opaque or likely to leak sensitive request data.

**Acceptance Criteria**:

1. **OBS-01** - WHEN tests exercise the `401` response from `AUTH-05`, the `503`
   response from `AUTH-06`, and one synthetic uncaught exception under the
   `test` profile THEN each response SHALL include a non-empty
   `correlationId`, and captured structured logs SHALL contain that same value
   for the corresponding request.
2. **OBS-02** - WHEN captured logs from the `200`, `401`, `503`, and synthetic
   `500` test scenarios are scanned THEN they SHALL NOT contain the fixture
   bearer token, service-account content, private key content, or complete
   Firebase credential payload used by the test.
3. **OBS-03** - WHEN the backend returns `AUTHENTICATION_REQUIRED` or
   `IDENTITY_PROVIDER_UNAVAILABLE` THEN its public response SHALL NOT contain
   Firebase exception class names, stack traces, token contents, or credential
   configuration.

**Independent Test**: Exercise the specified `200`, `401`, simulated provider
`503`, and synthetic `500` scenarios; assert response/log correlation for the
three error responses and scan captured responses and logs from all four
scenarios for the forbidden values.

---

### P1: Bounded Initialization Scope - MVP

**User Story**: As the product owner, I want initialization to stop at the
approved foundation so that infrastructure work does not silently consume
future feature scope.

**Why P1**: The existing ClickUp epics separate database, authentication flows,
design system, API generation, and deployment into later increments.

**Acceptance Criteria**:

1. **SCOPE-01** - WHEN initialization dependencies and source files are
   inspected THEN the repository SHALL contain no PostgreSQL driver, Flyway
   dependency, database migration, persistence adapter, or user repository.
2. **SCOPE-02** - WHEN Angular and mobile routes, components, and use cases are
   inspected THEN they SHALL contain no login, signup, password-reset, Google
   Sign-In, logout, user-synchronization, onboarding, group, invitation, or
   role-authorization flow.
3. **SCOPE-03** - WHEN generated and source files are inspected THEN the
   repository SHALL contain no generated OpenAPI client or client generation
   task.
4. **SCOPE-04** - WHEN client UI source is inspected THEN it SHALL contain only
   one root Compose screen shared by Android/iOS and one root Angular component,
   each implementing only the placeholder required by `INIT-04` or `INIT-05`;
   no additional application route or navigation dependency SHALL exist.
5. **SCOPE-05** - WHEN GitHub Actions and deployment files are inspected THEN
   no new workflow SHALL deploy the backend, Angular app, Android app, or iOS
   app to a production environment.
6. **SCOPE-06** - WHEN backend and client source is inspected THEN no business
   domain SHALL be shared into a client: client build graphs SHALL contain no
   backend module dependency, and client source SHALL contain no package or
   directory segment named `domain`, `usecase`, or `application`.
7. **SCOPE-07** - WHEN Git ignore behavior is inspected THEN
   `git check-ignore .specs/features/project-initialization/spec.md` SHALL
   succeed, and `git ls-files .specs` SHALL return no tracked path.

**Independent Test**: Inspect the feature diff, build dependency graphs, route
inventories, and production source path inventories for every finite artifact
and path prohibited by `SCOPE-01` through `SCOPE-07`; every prohibited item
SHALL be absent outside documentation and test fixtures.

---

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --- | --- |
| Input validation and bounds | `AUTH-05` defines missing, malformed, invalid, and expired bearer-token outcomes. No other user input exists in this increment. |
| Failure and partial-failure states | `AUTH-06`, `INIT-06`, and `CI-04` define provider and build-gate failures. `INIT-01` through `INIT-03` keep native gates directly runnable. |
| Idempotency, retry, and duplicates | `AUTH-10` defines read-only repeat behavior; `AUTH-09` defines repeatable fixture behavior and cleanup. |
| Auth boundaries and rate limits | Health is public; session is authenticated by `AUTH-03` through `AUTH-05`. Rate limiting is N/A because this increment exposes no credential-issuing or mutable public endpoint. |
| Concurrency and ordering | N/A because initialization introduces no shared mutable business state, queue, or ordered event processing. |
| Data lifecycle and expiry | `SCOPE-01` prohibits persistence, `AUTH-09` removes fixture accounts, and expired credentials map to `AUTH-05`. |
| Observability | `OBS-01` through `OBS-03` define response/log correlation and forbidden disclosures. |
| External-dependency failure | `AUTH-06` defines verifier unavailability; `AUTH-01` and `CI-05` require credential-free local and CI execution. |
| State-transition integrity | N/A because this increment implements no business state machine, persistence transition, signup flow, or authorization-role transition. |

---

## Edge Cases

- **EDGE-01** - WHEN an Authorization header uses a scheme other than `Bearer`
  or contains an empty token THEN the system SHALL apply `AUTH-05`.
- **EDGE-02** - WHEN a verified token omits email claims THEN `/api/session`
  SHALL retain the verified `subject` and return `null` for `email` and
  `emailVerified`.
- **EDGE-03** - WHEN emulator environment variables leak into `dev`, `staging`,
  or `production` THEN the system SHALL apply `AUTH-02` even if other Firebase
  credentials are valid.
- **EDGE-04** - WHEN one platform gate is unavailable on the current operating
  system THEN its native command MAY be skipped locally only when the README
  marks the limitation; CI SHALL still execute that gate on a supported runner.
- **EDGE-05** - WHEN `scripts/check-all` receives `SIGINT` or `SIGTERM` THEN it
  SHALL exit non-zero and, before exiting, stop each local HTTP server or
  emulator process it started; the verification SHALL assert their recorded
  process IDs are no longer alive and their ports are bindable.

---

## Requirement Traceability

### Scope Closure Mapping

| Specification statement | Requirement coverage |
| --- | --- |
| Independently buildable backend, Angular, Android, and iOS | `INIT-01` through `INIT-06` |
| Feature-oriented hexagonal backend | `ARCH-01` through `ARCH-08` |
| Credential-free Firebase proof | `AUTH-01` through `AUTH-11` |
| Preserve landing content and deployment | `LAND-01` through `LAND-03` |
| Reproducible pull-request gate | `CI-01` through `CI-10` |
| Safe failure diagnostics | `OBS-01` through `OBS-03` |
| No database, auth UI, product features, generated clients, design system, or product deployment | `SCOPE-01` through `SCOPE-06` |
| TLC artifacts remain local and ignored by Git | `SCOPE-07` |
| Backend-only business-rule authority and no shared backend domain models | `ARCH-08` and `SCOPE-06` |
| One module per implemented feature and no empty future modules | `ARCH-01` and `ARCH-07` |
| Firebase SDK and environment wiring for backend and each client | `AUTH-01`, `AUTH-07`, and `AUTH-08` |

### Requirement Inventory

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| INIT-01 through INIT-06 | P1: Reproducible Multiplatform Workspace | Tasks | Mapped |
| ARCH-01 through ARCH-08 | P1: Enforced Hexagonal Backend Foundation | Tasks | Mapped |
| AUTH-01 through AUTH-11 | P1: Credential-Free Authentication Infrastructure | Tasks | Mapped |
| LAND-01 through LAND-03 | P1: Preserved Landing Page | Tasks | Mapped |
| CI-01 through CI-10 | P1: Pull Request Quality Gate | Tasks | Mapped |
| OBS-01 through OBS-03 | P1: Safe Diagnostic Contract | Tasks | Mapped |
| SCOPE-01 through SCOPE-07 | P1: Bounded Initialization Scope | Tasks | Mapped |
| EDGE-01 through EDGE-05 | Cross-story edge cases | Tasks | Mapped |

**Coverage:** 53 requirements total, all 53 mapped to tasks.

---

## Backprop Log

| ID | Date | Root cause | Guard |
| --- | --- | --- | --- |
| B1 | 2026-07-14 | CI gates assumed local toolchain paths and implicit Android AVD location, so GitHub runners failed despite green local script tests. | CI-06 |
| B2 | 2026-07-14 | The generic API 35 Android image registered offline while the job had no pinned emulator build or finite failure bounds. | CI-07 |
| B3 | 2026-07-14 | The iOS gate used Xcode 26.5 with the first available iOS 26.4 runtime, so XCTest could not resolve an LLDB version and timed out launching the app. | CI-08 |
| B4 | 2026-07-14 | The generic API 35 Google APIs phone image remained permanently ADB-offline on the GitHub Ubuntu runner even after ADB was prestarted and the emulator build was pinned. | CI-09 |
| B5 | 2026-07-15 | Reloading the KVM udev rule left `/dev/kvm` inaccessible to the runner user, so the emulator action silently disabled hardware acceleration and launched with `-accel off`. | CI-10 |
| B6 | 2026-07-15 | The identity emulator test stopped only the parent `npx` process, so the Firebase Java child could retain port `9099` after test cleanup. | AUTH-11 |

---

## Success Criteria

- [ ] A clean checkout can execute every credential-free gate using only the
  documented toolchains and local emulator.
- [ ] Backend, Angular, Android, and iOS shells build; all three client shells
  display the specified `Saqz` placeholder.
- [ ] The emulator-backed test proves one valid `200` session outcome and the
  exact `401` and `503` failure contracts.
- [ ] Architecture tests kill representative forbidden-dependency mutations.
- [ ] CI fails when any native gate fails and requires no production secret.
- [ ] The initialization diff leaves the landing content and deployment
  behavior unchanged.
