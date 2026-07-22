# Mobile Domain and Data Boundaries Specification

**Status:** Confirmed  
**Phase:** Design  
**Created:** 2026-07-22  
**Confirmed:** 2026-07-22  
**Sequence:** 1 of 4 in the mobile architecture alignment program

## Problem

The mobile workspace does not consistently enforce the intended dependency direction between domain, data, presentation, and UI code. Feature modules contain mixed responsibilities, presentation code imports data-layer types, transport DTOs can cross feature boundaries, and failure handling is centered on transport-specific results instead of a shared typed application contract.

This makes feature behavior harder to test in isolation, permits framework and serialization details to leak into business-facing code, and increases the risk that later navigation and presentation refactors preserve the wrong boundaries.

## Goals

- Establish compile-time feature boundaries with the dependency direction `presentation -> domain <- data`.
- Keep domain contracts and models free of transport, serialization, UI, and platform-framework details.
- Keep DTOs, remote data sources, and transport mapping inside the data layer.
- Standardize typed success and failure handling across feature boundaries.
- Preserve all currently accepted product behavior while the structure is migrated incrementally.
- Support both Android and iOS consumers through the existing Compose Multiplatform workspace.

## Out of Scope

- Backend business rules, endpoints, payload semantics, or authorization policy.
- Navigation routes, back-stack behavior, or tab behavior.
- Screen MVI contracts, Compose state ownership, previews, or visual redesign.
- Koin module placement, Gradle convention plugins, and test-framework migration.
- Introducing a local database, cache policy, synchronization engine, or new offline-first product behavior.
- Adding new product features or changing user-visible copy.

## Assumptions and Decisions

- The backend remains authoritative for business rules and server-side validation.
- This migration covers the complete mobile `access` and `groups` features plus their composition seams. A feature is complete only after its domain, data, and presentation boundaries are enforced and all of its temporary compatibility adapters are removed.
- The migration sequence is shared typed result/error foundations, Access, Groups, then repository-wide verification.
- Each migrated feature SHALL expose separate `domain`, `data`, and `presentation` Gradle boundaries, or an equivalent set of compile-time boundaries proven to reject forbidden dependencies. The default design is feature-owned submodules.
- `:core:network` remains the transport foundation; it SHALL NOT become a home for feature domain models or feature repositories.
- A shared framework-free result/error foundation SHALL live in a narrowly scoped core domain module and SHALL contain no feature-specific errors.
- The shared domain-facing data error contract SHALL distinguish connectivity, timeout, unauthenticated, forbidden, validation, conflict, not found, invalid response, payload too large, server, and unknown failures. Feature-specific errors MAY wrap or extend that contract without exposing transport details.
- Safe structured validation details consist of global messages and field-keyed messages required for user correction. When no safe global message is supplied, presentation SHALL use one generic localized validation message. HTTP status codes, transport error codes, correlation identifiers, DTOs, and exceptions SHALL remain outside domain and presentation state.
- Migration is feature-by-feature. Temporary adapters MAY exist only inside the feature currently being migrated and SHALL be removed before that feature is declared complete.
- Current remote-only behavior is preserved. No persistence or cache policy is implied by this specification.
- The data layer SHALL make the initial call plus at most three automatic retries after 500 ms, 1 second, and 2 seconds for connectivity, timeout, and 5xx failures, only for reads or writes already protected by an idempotency key. Authentication, authorization, validation, conflict, not-found, rate-limit, invalid-response, payload-too-large, unknown, and cancellation outcomes SHALL NOT be retried automatically.
- Existing explicit retry actions and request idempotency semantics remain unchanged.
- Coroutine cancellation is control flow, not an application failure, and SHALL continue to propagate.
- This specification defines dependency ownership. The platform tooling specification defines how DI and build tooling enforce it.

**Open questions:** none — all identified gray areas are resolved above.

## User Stories and Requirements

### P1 — Enforced feature layer boundaries

**Story:** As a mobile maintainer, I need invalid layer dependencies to fail during compilation so architectural drift is detected before review or runtime.

- **MDB-001:** WHEN a feature is migrated, THEN its presentation layer SHALL depend on its domain API and SHALL NOT depend on its data implementation.
- **MDB-002:** WHEN a feature is migrated, THEN its data layer SHALL depend on its domain API and SHALL implement domain-owned repository contracts.
- **MDB-003:** WHEN a domain module is compiled, THEN it SHALL have no dependency on Compose, navigation, Koin, HTTP clients, serialization, database frameworks, Android APIs, or Apple APIs.
- **MDB-004:** WHEN one feature needs behavior from another feature, THEN the dependency SHALL use an explicit public contract or app-level coordination and SHALL NOT import the other feature's implementation layer.
- **MDB-005:** WHEN a forbidden dependency is introduced, THEN a deterministic build or architecture gate SHALL fail.
- **MDB-006:** WHEN a feature migration is complete, THEN no compatibility adapter that bypasses the declared dependency direction SHALL remain.

### P1 — Domain and transport separation

**Story:** As a feature author, I need business-facing models and contracts to remain stable when transport details change.

- **MDB-007:** WHEN remote data is decoded, THEN the data layer SHALL map transport DTOs into domain models before returning them through a repository contract.
- **MDB-008:** WHEN a request is sent, THEN the data layer SHALL map domain input into transport request DTOs without exposing those DTOs to presentation.
- **MDB-009:** WHEN transport fields are missing, malformed, optional, or versioned, THEN mapping SHALL produce either a valid domain value or a typed failure; it SHALL NOT leak a partially valid DTO.
- **MDB-010:** WHEN presentation renders or edits feature data, THEN it SHALL consume domain models or presentation UI models and SHALL NOT import serialization annotations or transport response types.
- **MDB-011:** WHEN a repository contract changes, THEN the change SHALL describe a domain capability rather than an HTTP method, URL, status code, or payload shape.
- **MDB-012:** WHEN paginated or tokenized transport metadata is required by domain behavior, THEN it SHALL be represented by a domain-owned abstraction before crossing the repository boundary.

### P1 — Typed result and error contract

**Story:** As a presentation author, I need exhaustive, transport-independent failures so each screen can react consistently and safely.

- **MDB-013:** WHEN an operation can fail, THEN its domain-facing contract SHALL return the shared typed `Result<T, E>` contract or its approved equivalent.
- **MDB-014:** WHEN an operation has no success payload, THEN it SHALL use a shared empty-success type rather than nullable or sentinel data.
- **MDB-015:** WHEN an HTTP, serialization, connectivity, timeout, or unknown transport failure occurs, THEN the data layer SHALL map it exhaustively to connectivity, timeout, unauthenticated, forbidden, validation, conflict, not found, invalid-response, payload-too-large, server, or unknown domain-facing failure as applicable.
- **MDB-016:** WHEN the backend returns structured validation failures that affect user correction, THEN the mapped failure SHALL preserve global messages and field-keyed safe messages required by presentation without exposing status codes, transport codes, correlation identifiers, DTOs, or exceptions; WHEN no safe global message is supplied, THEN presentation SHALL render one generic localized validation message.
- **MDB-017:** WHEN presentation consumes a typed result, THEN success and failure handling SHALL be exhaustive and SHALL NOT inspect raw status codes or transport exceptions.
- **MDB-018:** WHEN coroutine cancellation occurs, THEN repositories and mapping helpers SHALL rethrow it rather than convert it to a failure value.
- **MDB-019:** WHEN an unknown exception is mapped, THEN diagnostics MAY record the original cause through an approved non-domain mechanism, while the domain-facing value remains stable and credential-safe.

### P1 — Behavior-preserving migration

**Story:** As a product stakeholder, I need the architecture migration to preserve accepted mobile behavior on both supported platforms.

- **MDB-020:** WHEN a feature is migrated, THEN its accepted loading, success, empty, validation-error, authorization-error, connectivity-error, and retry outcomes SHALL remain behaviorally equivalent.
- **MDB-021:** WHEN request or response mapping is moved, THEN endpoint paths, methods, headers, payload meaning, pagination behavior, and idempotency semantics SHALL remain unchanged unless another confirmed feature specification says otherwise.
- **MDB-022:** WHEN credentials or session data are needed by a data source, THEN they SHALL continue to be obtained through approved abstractions and SHALL NOT be copied into domain or presentation state.
- **MDB-023:** WHEN a migrated repository is tested, THEN tests SHALL cover successful mapping, each supported failure category, malformed payload handling, and cancellation propagation.
- **MDB-024:** WHEN a migrated feature is verified, THEN both its narrow test gate and the applicable Android/KMP aggregate gate SHALL pass.
- **MDB-025:** WHEN the entire migration is declared complete, THEN repository-wide searches and dependency checks SHALL find no presentation-to-data imports, DTO leakage, or feature-to-feature implementation dependencies.
- **MDB-026:** WHEN an eligible data-layer call fails with connectivity, timeout, or 5xx, THEN the data layer SHALL retry after 500 ms, 1 second, and 2 seconds, stopping immediately on success and making no more than four total calls; only reads and writes already protected by an idempotency key are eligible, and authentication, authorization, validation, conflict, not-found, rate-limit, invalid-response, payload-too-large, unknown, and cancellation outcomes SHALL propagate without automatic retry.

## Edge Cases

- A response is successful at the HTTP layer but cannot form a valid domain model.
- A backend validation response contains both global and field-level errors.
- An optional remote field is absent on an older backend version.
- A request is cancelled during decoding or mapping.
- A feature needs an identifier owned by another feature but not its implementation.
- A temporary migration adapter would otherwise become a permanent cross-layer shortcut.
- A platform-specific implementation is required behind a common domain contract.

## Implicit-Dimension Closure

- **Input validation and bounds:** Existing domain and backend validation outcomes are preserved; new validation rules and limits are out of scope.
- **Authentication and authorization:** Existing behavior preserved by MDB-020 through MDB-022; policy changes are out of scope.
- **Persistence, data lifecycle, and recovery:** No new persistence, expiry, archival, deletion, or recovery behavior; existing durable behavior is preserved by MDB-020. New offline behavior is out of scope.
- **External calls and idempotency:** Covered by MDB-021 and failure mapping requirements.
- **Concurrency and ordering:** Cancellation is covered by MDB-018; no new concurrent write or ordering semantics are introduced.
- **Failure and retry:** Typed failure categories, three bounded data-layer retries, excluded outcomes, operation eligibility, and current explicit retry behavior are covered by MDB-015 through MDB-020 and MDB-026.
- **Observability and credentials:** Covered by MDB-019 and MDB-022.
- **State-transition integrity:** Existing loading, success, empty, error, and retry transitions are preserved by MDB-020; new transitions are out of scope.
- **Rate limits:** Existing server responses map to the server or validation-independent typed failure selected by Design; client throttling policy is out of scope.
- **Migration and compatibility:** Covered by MDB-006 and MDB-020 through MDB-025.

## Traceability

| Requirement group | IDs | Planned downstream artifact |
|---|---|---|
| Layer boundaries | MDB-001–MDB-006 | Design dependency graph, module rules, architecture gates |
| Domain/transport separation | MDB-007–MDB-012 | Design mapping contracts and feature migration tasks |
| Typed result/errors | MDB-013–MDB-019 | Design shared contracts and error-mapping tests |
| Behavior preservation and bounded retry | MDB-020–MDB-026 | Feature-by-feature tasks and validation evidence |

All 26 requirements are assigned to a downstream design or task category. Task identifiers will be added only when this feature enters the Tasks phase.

## Success Criteria

- Every migrated feature has enforceable domain, data, and presentation boundaries.
- Domain modules compile without UI, DI, transport, serialization, database, or platform dependencies.
- No migrated presentation source imports a data implementation or transport DTO.
- Domain-facing repository failures are typed, exhaustive, and cancellation-safe.
- Eligible transient data-layer failures perform exactly the bounded retry schedule, while excluded failures and unsafe writes perform no automatic retry.
- Mapping and failure-path tests pass for every migrated repository.
- Existing Android and iOS product behavior remains accepted by the relevant feature gates.
- Final repository verification reports zero prohibited layer or cross-feature implementation dependencies.
