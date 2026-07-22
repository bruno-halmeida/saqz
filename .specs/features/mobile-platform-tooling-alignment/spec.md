# Mobile Platform and Tooling Alignment Specification

**Status:** Draft  
**Phase:** Specify  
**Created:** 2026-07-22  
**Sequence:** 4 of 4 in the mobile architecture alignment program

## Problem

The mobile workspace does not consistently encode its architecture in dependency injection, Gradle conventions, or tests. Feature dependencies are assembled centrally instead of being owned by their layers, build configuration and dependency versions are repeated, and the test stack mixes legacy and current APIs without a uniform structure for asynchronous ViewModel and Compose UI behavior.

As a result, architectural violations are easier to introduce, module setup drifts over time, tests use inconsistent scheduling and assertion styles, and deprecated Compose test APIs increase maintenance risk.

## Goals

- Make each feature and layer own its Koin definitions while keeping final platform assembly in the composition root.
- Encode repeated Android/KMP module rules in convention plugins and keep dependency versions centralized.
- Establish a platform-appropriate, deterministic test stack for common, JVM/Android, and Compose UI tests.
- Migrate deprecated Compose UI test usage and apply robot abstractions to complex interaction flows.
- Add deterministic gates that detect DI, dependency, test, and build-configuration drift.
- Preserve product behavior while changing only platform wiring and developer tooling.

## Out of Scope

- Changing domain/data contracts, repository mapping, or error semantics.
- Defining navigation routes, hosts, tabs, or back-stack behavior.
- Refactoring screen state, intents, effects, or visual design.
- Broad dependency upgrades unrelated to compatibility with the approved tooling.
- Backend, landing-page, CI-provider, release-signing, or production-credential changes.
- Replacing Koin or Gradle with a different DI or build system.

## Assumptions and Decisions

- Koin remains the mobile dependency-injection framework.
- The owning feature/layer SHALL publish its module definitions; `compose-app` SHALL assemble modules and platform entry points SHALL start Koin.
- Common tests MAY retain the multiplatform `kotlin.test` facade where required for native portability. JVM and Android unit-test execution SHALL use JUnit 5. AssertK and Turbine SHALL be used where compatible and materially improve assertions or Flow testing.
- The currently approved dependency versions remain pinned unless a compatibility issue is recorded and resolved during Design.
- Convention plugins SHALL remove repeated configuration without hiding feature-specific dependencies or platform capabilities.
- Compose UI Test v2 is the target API. Robot abstractions are required for complex screens and reusable flows, not for every one-line assertion.
- Tooling migration MAY be incremental, but the final state SHALL have one documented approach per test category and no deprecated test API in migrated scope.
- Tests SHALL never gain timing sleeps, weakened assertions, ignored failures, or production-only hooks to compensate for migration issues.

## User Stories and Requirements

### P1 — Layer-owned dependency injection

**Story:** As a feature maintainer, I need dependency definitions to live with their owners so wiring changes remain local and architectural direction is visible.

- **MPT-001:** WHEN a domain layer exposes injectable use cases or services, THEN its Koin definitions SHALL be published by that domain layer and SHALL depend only on allowed contracts.
- **MPT-002:** WHEN a data layer implements repositories or data sources, THEN its Koin definitions SHALL be published by that data layer and SHALL bind implementations to domain-owned contracts.
- **MPT-003:** WHEN a presentation layer exposes ViewModels, THEN its Koin definitions SHALL be published by that presentation layer and SHALL use the approved ViewModel DSL.
- **MPT-004:** WHEN the application starts, THEN the composition root SHALL assemble public feature/core modules without redefining feature-owned implementations.
- **MPT-005:** WHEN a platform-specific dependency is required, THEN the platform module SHALL provide it behind a common contract without leaking the platform type into common domain code.
- **MPT-006:** WHEN Koin modules are verified, THEN missing definitions, duplicate incompatible bindings, and forbidden layer dependencies SHALL fail deterministically before product runtime.
- **MPT-007:** WHEN a screen obtains a ViewModel, THEN it SHALL use the approved Koin integration at the route root and SHALL NOT manually construct the production dependency graph.

### P1 — Gradle and dependency conventions

**Story:** As a module author, I need new and existing modules to share one build contract so compiler, platform, and dependency configuration cannot silently drift.

- **MPT-008:** WHEN multiple modules repeat Android/KMP compiler, target, source-set, serialization, Compose, or test configuration, THEN that shared configuration SHALL be represented by an appropriately scoped convention plugin.
- **MPT-009:** WHEN a module applies a convention plugin, THEN feature-specific dependencies and capabilities SHALL remain explicit in that module's build file.
- **MPT-010:** WHEN a dependency or plugin version is declared, THEN it SHALL come from the version catalog or another single approved source and SHALL NOT be hardcoded in feature build files.
- **MPT-011:** WHEN a new feature layer module is created, THEN it SHALL be able to adopt the applicable convention without copying compiler or platform boilerplate.
- **MPT-012:** WHEN convention behavior changes, THEN representative fixture or build-logic tests SHALL prove the intended configuration and failure mode.
- **MPT-013:** WHEN the Gradle project graph is configured, THEN independent repository workspaces and the iOS umbrella-framework boundary SHALL remain unchanged.
- **MPT-014:** WHEN dependency alignment is verified, THEN duplicate aliases, direct version literals, and incompatible test engines SHALL fail a deterministic gate.

### P1 — Deterministic unit and Flow tests

**Story:** As a maintainer, I need tests to express outcomes consistently and complete deterministically across common and JVM/Android targets.

- **MPT-015:** WHEN common Kotlin code is tested, THEN tests SHALL use portable APIs supported by every compiled target.
- **MPT-016:** WHEN JVM or Android unit tests execute, THEN they SHALL run on JUnit 5 with the approved Gradle test-engine configuration.
- **MPT-017:** WHEN assertions compare values, collections, failures, or state, THEN they SHALL use the approved fluent assertion style where supported and SHALL produce actionable failure output.
- **MPT-018:** WHEN a Flow, StateFlow, or effect stream is tested, THEN collection SHALL use Turbine or the approved deterministic equivalent and SHALL assert completion, cancellation, or remaining events when relevant.
- **MPT-019:** WHEN coroutine-driven code is tested, THEN dispatchers and schedulers SHALL be controlled without real-time sleeps, production-scope mutation, or order-dependent global state.
- **MPT-020:** WHEN a repository or ViewModel collaborator is replaced, THEN a focused fake SHALL model the required contract and SHALL NOT duplicate production implementation logic.
- **MPT-021:** WHEN an acceptance criterion describes a failure or lifecycle outcome, THEN at least one test SHALL assert that outcome rather than only the happy path.
- **MPT-022:** WHEN tests are migrated, THEN assertion strength and behavioral coverage SHALL be preserved or increased.

### P1 — Compose UI test modernization

**Story:** As a UI maintainer, I need Compose tests to use supported APIs and readable interaction abstractions so failures remain diagnosable.

- **MPT-023:** WHEN a Compose UI test is migrated, THEN it SHALL use the approved Compose UI Test v2 entry point and SHALL NOT call deprecated test runners.
- **MPT-024:** WHEN a screen test has multiple interactions, repeated selectors, or reusable flows, THEN it SHALL encapsulate them in a screen-specific robot with semantic action names.
- **MPT-025:** WHEN a robot locates UI, THEN it SHALL prefer stable semantics, roles, labels, or test tags over layout position or implementation hierarchy.
- **MPT-026:** WHEN a Compose test performs an asynchronous action, THEN it SHALL use framework idling or deterministic clock control and SHALL NOT use fixed sleeps.
- **MPT-027:** WHEN accessibility semantics are part of the screen contract, THEN Compose tests SHALL assert the relevant role, label, enabled state, or content description.
- **MPT-028:** WHEN a test fails, THEN its output SHALL identify the expected user-visible state or interaction rather than only an internal node lookup.

### P1 — Migration gates and compatibility

**Story:** As a repository owner, I need the tooling migration to be measurable and reversible at atomic task boundaries.

- **MPT-029:** WHEN an atomic tooling task completes, THEN its narrow Gradle or test gate SHALL pass before unrelated migration work begins.
- **MPT-030:** WHEN the complete specification is verified, THEN the applicable Android/KMP and iOS aggregate gates SHALL pass from a clean invocation.
- **MPT-031:** WHEN static checks scan the final migrated scope, THEN they SHALL find no feature-owned DI definitions in the composition root, hardcoded dependency versions, deprecated Compose test runners, production test hooks, or fixed test sleeps.
- **MPT-032:** WHEN a tooling change cannot preserve an accepted platform behavior, THEN implementation SHALL stop and the conflict SHALL return to Specify or Design instead of weakening a gate.
- **MPT-033:** WHEN validation is recorded, THEN it SHALL include commands, exit status, and evidence for DI verification, build conventions, unit tests, Compose UI tests, Android/KMP checks, and the iOS contract where applicable.

## Edge Cases

- A common test compiles for Native but requires JVM-only JUnit APIs.
- Koin has two valid platform implementations for one common contract.
- A convention plugin accidentally applies Android-only behavior to a pure common module.
- A version alias resolves but selects an incompatible test engine at runtime.
- Compose UI Test v2 changes synchronization behavior for animations or clocks.
- A robot abstraction hides the assertion that actually failed.
- A platform target cannot run a test category locally but still must compile it.
- Existing uncommitted work overlaps a build or test file selected for migration.

## Implicit-Dimension Closure

- **Authentication, authorization, payments, and business behavior:** Not changed; regression preservation is covered by MPT-029 through MPT-032.
- **Persistence and recovery:** No persistence behavior is introduced or changed.
- **External calls:** Dependency resolution is a build concern; production network semantics are out of scope.
- **Concurrency:** Deterministic coroutine and Flow testing is covered by MPT-018, MPT-019, and MPT-026.
- **Failure handling:** DI, configuration, test, and aggregate failures must be deterministic and actionable under MPT-006, MPT-012, MPT-014, and MPT-028.
- **Credentials and signing:** Production credentials and signing changes are explicitly out of scope; repository credential gates remain mandatory.
- **Migration and compatibility:** Covered by MPT-022 and MPT-029 through MPT-033.

## Traceability

| Requirement group | IDs | Planned downstream artifact |
|---|---|---|
| Dependency injection | MPT-001–MPT-007 | DI ownership design, module verification, migration tasks |
| Gradle conventions | MPT-008–MPT-014 | Build-logic design and convention tests |
| Unit/Flow tests | MPT-015–MPT-022 | Test matrix, fixture rules, migration tasks |
| Compose UI tests | MPT-023–MPT-028 | UI test design and robot migrations |
| Gates/compatibility | MPT-029–MPT-033 | Execution gates and final validation evidence |

All 33 requirements are assigned to a downstream design or task category. Task identifiers will be added only when this feature enters the Tasks phase.

## Success Criteria

- Feature and layer owners publish their Koin modules; the composition root only assembles them.
- Koin verification detects missing, duplicate-incompatible, and forbidden bindings before runtime.
- Repeated module configuration is encoded in tested, appropriately scoped convention plugins.
- Feature build files contain no direct dependency or plugin version literals.
- Common tests remain portable, while JVM/Android tests execute consistently on JUnit 5.
- Flow and coroutine tests are deterministic and use no fixed sleeps or production-scope mutation.
- Migrated Compose tests use UI Test v2; complex flows use readable robots and stable semantics.
- Static and aggregate gates pass on Android/KMP and iOS without weakening existing tests or product behavior.
