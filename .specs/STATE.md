# STATE

## Decisions

### AD-001
- **Decision**: Mobile uses progressively added KMP feature modules aggregated by one umbrella Compose framework consumed by the iOS app.
- **Reason**: Feature modules preserve boundaries and scale independently, while one umbrella framework avoids duplicated Kotlin runtimes and incompatible shared dependency types on iOS.
- **Trade-off**: The umbrella module adds Gradle configuration and iOS always consumes the complete shared mobile framework rather than an individual feature subset.
- **Scope**: All Android/iOS shared UI, presentation, and mobile feature modules.
- **Date**: 2026-07-14
- **Status**: active

### AD-002
- **Decision**: The repository is hybrid: Gradle owns backend/KMP/Android, Angular CLI owns web, and Xcode owns the iOS launcher.
- **Reason**: Each platform keeps its native build and debugging workflow without forcing Angular or Xcode behind Gradle.
- **Trade-off**: Local and CI orchestration must coordinate three toolchains instead of one universal build.
- **Scope**: Repository layout, build scripts, CI, and all product surfaces.
- **Date**: 2026-07-14
- **Status**: superseded by AD-017

### AD-003
- **Decision**: Backend business features use one Gradle module per implemented feature with hexagonal package boundaries.
- **Reason**: Compile and architecture-test boundaries prevent framework and cross-feature coupling.
- **Trade-off**: Every new backend feature requires explicit module and wiring configuration.
- **Scope**: All backend business features and the Spring Boot composition root.
- **Date**: 2026-07-14
- **Status**: active

### AD-004
- **Decision**: Firebase uses official native Android, Apple, web, and Admin SDKs at platform edges; common KMP code receives interfaces only when a real flow needs them.
- **Reason**: Firebase does not publish an official KMP client SDK, and native SDKs preserve supported platform behavior.
- **Trade-off**: Platform adapters are implemented separately instead of sharing one Firebase implementation.
- **Scope**: Authentication and future Firebase integrations on backend, web, Android, and iOS.
- **Date**: 2026-07-14
- **Status**: active

### AD-005
- **Decision**: The Kotlin backend is authoritative for business rules; Angular and mobile do not consume backend domain classes.
- **Reason**: Clients remain independently evolvable and cannot bypass server validation.
- **Trade-off**: Client-side UX validation may duplicate simple checks but is never authoritative.
- **Scope**: API contracts, backend domains, Angular, and KMP clients.
- **Date**: 2026-07-14
- **Status**: active

### AD-006
- **Decision**: The monorepo has three independent product workspaces: `backend/`, `frontend/`, and `mobile/`. Backend and mobile own separate Gradle wrappers, settings, version catalogs, and build logic; frontend is an npm/Angular workspace.
- **Reason**: Backend releases and dependency upgrades must not be constrained by Kotlin Multiplatform, Compose, AGP, Android, or Xcode compatibility, and each product surface must remain independently buildable.
- **Trade-off**: Gradle wrapper and build configuration are duplicated between backend and mobile, while repository scripts coordinate both native builds.
- **Scope**: Repository layout, local gates, CI, dependency boundaries, and all future backend, frontend, Android, iOS, and shared-mobile modules.
- **Date**: 2026-07-14
- **Status**: superseded by AD-017

### AD-007
- **Decision**: Frontend uses the latest currently supported Node/npm baseline while keeping Angular-compatible package pins: Node 26.5.0, npm 12.0.1, Angular/CLI 22.0.6, TypeScript 6.0.3, RxJS 7.8.2, Firebase JS 12.16.0, angular-eslint 22.1.0.
- **Reason**: The project is starting fresh, so the user chose current toolchain baselines; TypeScript remains capped at 6.0.3 because Angular 22 requires `>=6.0 <6.1`.
- **Trade-off**: Local developers need Node 26/npm 12 instead of the earlier Node 22/npm 11 baseline, while accepting Angular 22's TypeScript peer constraint over the newer standalone TypeScript major.
- **Scope**: Frontend workspace, CI Angular jobs, README prerequisites, and future npm lockfile regeneration.
- **Date**: 2026-07-14
- **Status**: superseded by AD-017

### AD-008
- **Decision**: Android CI keeps API 35 `google_apis` x86_64 but pins emulator 36.1.9 (`13823996`), starts ADB before AVD launch, limits boot to 300 seconds, and limits the complete Gradle job to 45 minutes.
- **Reason**: The first Android CI failure combined an unpinned emulator package, an ADB startup race, and unbounded execution; this decision bounded those variables while diagnosis continued.
- **Trade-off**: Emulator upgrades become intentional maintenance instead of following the latest stable package automatically.
- **Scope**: GitHub Actions Android provisioning and its workflow contract test.
- **Date**: 2026-07-14
- **Status**: superseded by AD-010

### AD-009
- **Decision**: iOS CI pins Xcode 26.4 and `scripts/check-ios` selects a simulator only from the runtime matching the active iOS Simulator SDK; the job is capped at 45 minutes.
- **Reason**: The macOS runner selected Xcode 26.5 while the script selected the first available iOS 26.4 runtime, causing XCTest to report no LLDB version and time out during app launch.
- **Trade-off**: Xcode/runtime upgrades become explicit and a machine without the matching simulator fails immediately instead of falling back to another installed runtime.
- **Scope**: Local and GitHub Actions iOS gates.
- **Date**: 2026-07-14
- **Status**: active

### AD-010
- **Decision**: Android CI uses the API 30 `google_atd` x86 Automated Test Device with the `pixel_2` profile and 2048 MB RAM instead of the generic API 35 Google APIs phone image.
- **Reason**: The generic phone image remained permanently ADB-offline after ADB prestart and emulator pinning; the current instrumented test only validates Compose semantics and the app supports API 23+, so API 30 ATD exercises the required behavior with a CI-optimized image.
- **Trade-off**: This smoke gate no longer exercises API 35-specific behavior or hardware-rendered screenshot behavior; neither is part of the current instrumented test contract.
- **Scope**: GitHub Actions Android provisioning and its workflow contract test.
- **Date**: 2026-07-14
- **Status**: active

### AD-011
- **Decision**: Android CI applies mode `0666` directly to `/dev/kvm` after reloading udev rules and verifies runner read/write access before launching the emulator.
- **Reason**: The udev rule alone left `/dev/kvm` inaccessible on the hosted runner, causing `android-emulator-runner` to silently disable KVM and launch the x86 emulator with `-accel off`.
- **Trade-off**: The hosted runner grants all local users access to KVM for the duration of the ephemeral job; the benefit is deterministic hardware acceleration and early failure if KVM is unavailable.
- **Scope**: GitHub Actions Android provisioning and its workflow contract test.
- **Date**: 2026-07-15
- **Status**: active

### AD-012
- **Decision**: Cross-platform UI parity is enforced through one semantic contract with independent Angular and Compose implementations; no runtime artifact or generated package is shared between workspaces.
- **Reason**: Angular and KMP must remain independently buildable while presenting the same Saqz tokens, states, strings, and formatting outcomes.
- **Trade-off**: Equivalent contracts are implemented twice and require parity fixtures plus a repository-level comparison gate.
- **Scope**: Design tokens, reusable UI components, presentation state, localization, and formatting across web, Android, and iOS.
- **Date**: 2026-07-15
- **Status**: superseded by AD-017

### AD-013
- **Decision**: Shared mobile foundations live in real KMP modules `:core:common` and `:core:design-system`, while `:compose-app` owns routes, shell composition, screens, and the sole exported `SaqzMobile` framework.
- **Reason**: Future mobile features need stable shared primitives without coupling features to the app shell or exporting multiple Kotlin frameworks to iOS.
- **Trade-off**: The mobile Gradle graph gains two modules and one pure-KMP convention, increasing configuration overhead.
- **Scope**: All current and future shared Android/iOS presentation foundations and mobile feature modules.
- **Date**: 2026-07-15
- **Status**: active

### AD-014
- **Decision**: Web navigation uses Angular Router and shared mobile navigation uses Navigation Compose 2; route ownership remains in each application shell.
- **Reason**: Both are stable, officially supported, lifecycle-aware choices that satisfy the current two-route flow without introducing a custom back stack or Navigation 3 complexity.
- **Trade-off**: Route models are implemented separately in TypeScript and Kotlin, and a future Navigation 3 migration would require an explicit decision.
- **Scope**: Angular routes, Android/iOS shared routes, back behavior, and future client-side application navigation.
- **Date**: 2026-07-15
- **Status**: superseded by AD-017

### AD-015
- **Decision**: Product UI uses an Apple-inspired but Saqz-owned visual language: Saqz colors, low-density composition, system-like typography, restrained elevation, and platform-adapted navigation rather than copied Apple marketing/store components.
- **Reason**: The product needs a coherent craft standard that matches the approved landing direction while preserving Saqz identity and application usability.
- **Trade-off**: Web and mobile share principles rather than pixel-identical layouts, and the landing remains visually related but not automatically synchronized.
- **Scope**: All future authenticated web, Android, and iOS product interfaces.
- **Date**: 2026-07-15
- **Status**: active

### AD-016
- **Decision**: API 31+ Android UI behavior is verified in a dedicated API 35 `google_atd` job, separate from the stable API 30 complete smoke gate; the exact image/emulator tuple becomes blocking only after a provisioning probe passes and is pinned.
- **Reason**: The design-system feature introduces Android 12+ splash and modern edge-to-edge behavior while the current API 30 ATD gate intentionally cannot verify those APIs.
- **Trade-off**: CI gains one Android job and a bounded provisioning task; generic `google_apis` fallback remains prohibited because it previously stayed ADB-offline.
- **Scope**: Android launch-screen, edge-to-edge, and modern UI verification in CI.
- **Date**: 2026-07-15
- **Status**: superseded by AD-023

### AD-017
- **Decision**: Active product surfaces are the Kotlin backend, one Compose Multiplatform app for Android/iOS, and the independent static landing page; the Angular workspace is removed, and any future product web app requires a new spec and architecture decision.
- **Reason**: The project should concentrate delivery and validation on one app until user demand justifies a browser product, avoiding duplicate UI, dependency, and CI cost.
- **Trade-off**: There is no authenticated browser product now, and a future web surface is not guaranteed to be a direct port; Node/npm remain only for Firebase CLI and local tooling.
- **Scope**: Repository layout, product UI, design system, local gates, CI, documentation, and future client-platform decisions.
- **Date**: 2026-07-15
- **Status**: active

### AD-018
- **Decision**: Android/iOS product UI is Compose-first, including iOS semantics, resources, typography, Dynamic Type, navigation, and motion; native Swift/UIKit or Android code is limited to launchers, supported platform SDK edges, and system capabilities without a common Compose API.
- **Reason**: One shared presentation implementation maximizes behavioral parity and keeps accessibility contracts testable without duplicating UI or applying platform typography twice.
- **Trade-off**: Platform-native customization is intentionally constrained, and every native adapter must prove that no suitable common Compose capability exists.
- **Scope**: All current and future Android/iOS product UI, design-system components, accessibility adapters, and mobile feature modules.
- **Date**: 2026-07-15
- **Status**: active

### AD-019
- **Decision**: Durable backend business data uses PostgreSQL migrations managed by Flyway and explicit Spring JdbcClient adapters, with integration tests against disposable PostgreSQL through Testcontainers.
- **Reason**: Authorization and financial-adjacent invariants need real relational constraints, transactions, locking behavior, and migrations without coupling domain code to an ORM.
- **Trade-off**: Features own SQL and row mapping explicitly, and local/CI integration tests require a container runtime.
- **Scope**: All current and future persistent backend business features, database migrations, and persistence integration gates.
- **Date**: 2026-07-15
- **Status**: active

### AD-020
- **Decision**: Shared Android/iOS backend communication uses a Ktor KMP network module; native authentication adapters supply Firebase ID tokens on demand, and application code never persists Firebase tokens.
- **Reason**: One client enforces consistent serialization, error mapping, and single-refresh bearer behavior while official Firebase SDKs retain session ownership at platform edges.
- **Trade-off**: Native launchers must implement a small token bridge, and Ktor engines remain target-specific dependencies.
- **Scope**: All authenticated mobile-to-backend calls, mobile networking modules, and Android/iOS authentication adapters.
- **Date**: 2026-07-15
- **Status**: active

### AD-021
- **Decision**: Invite and future install-deferred app links use provider-neutral link ports with Branch native SDK adapters and non-expiring Branch Long Links; Firebase Dynamic Links and provider data are never authoritative for access.
- **Reason**: Firebase Dynamic Links is shut down, standard App/Universal Links do not preserve context through installation, and Long Links avoid the inactivity expiration and server-side Short Links API entitlement.
- **Trade-off**: Branch processes opaque link capabilities, production requires provider/domain configuration and privacy review, and deferred matching remains subject to OS/provider windows.
- **Scope**: Android/iOS invite, referral, and other install-deferred links plus their backend link factories.
- **Date**: 2026-07-15
- **Status**: active

### AD-022
- **Decision**: The provider-neutral authenticated request identity contract lives in backend shared-kernel; business features never depend on the identity feature, and cross-cutting HTTP problem/correlation infrastructure is composed in bootstrap.
- **Reason**: Identity verifies credentials while independent business features consume one stable principal and one API error contract without cross-feature dependencies.
- **Trade-off**: Moving the existing principal and filters requires a coordinated compatibility migration across bootstrap and tests.
- **Scope**: Backend authentication principal, feature module boundaries, HTTP diagnostics, and all authenticated business endpoints.
- **Date**: 2026-07-15
- **Status**: active

### AD-023
- **Decision**: API 31+ Android UI behavior is blocking in a dedicated API 35 `google_apis` x86_64 job using `pixel_7`, 4096 MB RAM, AVD `saqz-api35-probe`, emulator build `13823996` and a 300-second boot timeout; the complete API 30 `google_atd` gate remains unchanged.
- **Reason**: This exact API 35 tuple booted and passed the four modern Android behavior tests in three independent runs on the same SHA, while preserving the stable API 30 full smoke gate.
- **Trade-off**: The API 35 job consumes more memory than the ATD image, but its pinned tuple is evidence-backed and isolates modern-platform behavior from the complete API 30 suite.
- **Scope**: Android launch-screen, edge-to-edge, modern UI verification and CI aggregate evaluation.
- **Date**: 2026-07-15
- **Status**: active

### AD-024
- **Decision**: GitHub Actions runs only the complete `SaqzDev` iOS unit/UI suite; the local aggregate continues to run SaqzDev plus the SaqzProd Release build and Release unit tests until production CI is designed separately.
- **Reason**: The serial Prod build/test invocations dominate the initialization CI wall time, while current delivery does not require a production signing or deployment pipeline.
- **Trade-off**: Pull requests receive remote Dev coverage and local Prod coverage; remote Prod regressions depend on the complete local gate until the dedicated production CI work is approved.
- **Scope**: Initialization iOS CI command, iOS gate mode contract and README.
- **Date**: 2026-07-16
- **Status**: active

### AD-025
- **Decision**: Every Compose product navigation route owns a lifecycle-aware KMP ViewModel whose screen command surface is one typed `onIntent`, with immutable `StateFlow` state, explicit one-shot effects, and stateless visual composables.
- **Reason**: A single state-machine entry makes Android/iOS presentation transitions discoverable, testable and lifecycle-consistent while keeping UI rendering independent from business coordination.
- **Trade-off**: Presentation features require explicit intent/state/effect types and route factories; state-derived panels inside one route share that route ViewModel until they become real navigation entries.
- **Scope**: All current and future Compose Multiplatform product routes and feature presentation modules.
- **Date**: 2026-07-17
- **Status**: active

### AD-026
- **Decision**: Group profile, memberships, roles, invitations, venues, games, attendance, manual charges, and expenses live in dedicated backend/mobile Groups features; Access retains verified identity, account/session bootstrap, and selected-group reconciliation, with backend cross-feature collaboration only through provider-neutral shared-kernel ports wired in bootstrap.
- **Reason**: The current Access feature and route ViewModel already mix authentication with group administration, while upcoming recurrence, concurrency, media, and finance behavior needs an independently enforced business boundary without feature-to-feature dependencies.
- **Trade-off**: Existing group/membership/invite code must migrate compatibly, bootstrap gains two shared port bindings, and architecture inventories/tests must expand before new product behavior is added.
- **Scope**: Backend and mobile module graphs, group/access ownership, shared-kernel integration seams, Spring composition, and all group-management delivery.
- **Date**: 2026-07-19
- **Status**: active

## Handoff

- **Feature**: group-management — `.specs/features/group-management/`
- **Phase / Task**: Execute — Phase 7 / T50 (attendance transition rules).
- **Completed**: T01 `49a4730`, T02 `024d689`, T03 `dfd35d8`, T08 `463dc86`, T09 `94ab9e8`, T13 `85d841b`, T14 `124250c`, T15 `d85ccb0`, T16 `e5fd6d3`, T17 `0946523`, T18, T19 `224136b`, T20 `edaa6c1`, T21 `7f5103f`, T22 `4ae41b7`, T23 `c132762`, T24 `45ea845`, T25 `3ed6ddc`, T26 `0566f97`, T27 `9d37f52`, T28 `bfbb4d2`, T29 `f2318dd`, T30 `c686a61`, T31 `7c20675`, T32 `0dc87d4`, T33, and T34 series boundaries. T34 backend full and safety gates passed with 17 added unit/PostgreSQL cases covering stable one-occurrence detachment, complete successor revision snapshots, regenerated future occurrences, preserved history/completed games, cancellation identities, retry, concurrent locking, and injected rollback. The user-approved task amendment merged the former T03–T07 and T09–T12 incompatible dependency sequences.
- **In-progress** (file:line): `.specs/features/group-management/tasks.md:1442` — implement attendance transition and deadline rules.
- **Next step**: Execute T50 with exhaustive self-service and organizer override decisions across capacity, deadline, lifecycle, and current attendance state.
- **Blockers**: None; backend Gradle requires JDK 21 and Testcontainers requires `DOCKER_HOST=unix:///Users/bruno_almeida/.colima/default/docker.sock` in this environment. Local Testcontainers gates also require `TESTCONTAINERS_RYUK_DISABLED=true` unless the Ryuk sidecar starts reliably.
- **Branch**: main
