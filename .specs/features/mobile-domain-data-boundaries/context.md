# Mobile Domain and Data Boundaries Context

**Gathered:** 2026-07-22  
**Spec:** `.specs/features/mobile-domain-data-boundaries/spec.md`  
**Status:** Ready for design

---

## Feature Boundary

Migrate the complete mobile Access and Groups features, including their composition seams, to enforce `presentation -> domain <- data` at compile time while preserving accepted Android and iOS behavior and adding the confirmed bounded data-layer retry policy. Navigation, MVI, DI placement, tooling alignment, persistence, caching, backend behavior, and product changes remain outside this feature.

## Implementation Decisions

### Migration scope and sequence

- Migrate shared typed result/error foundations first, then Access, then Groups, followed by repository-wide verification.
- A feature is complete only when its domain, data, and presentation boundaries are enforced and all temporary compatibility adapters for that feature are removed.
- Composition roots may depend on data implementations for wiring, but presentation code and feature public contracts may not.

### Typed failure contract

- Domain-facing failures distinguish connectivity, timeout, unauthenticated, forbidden, validation, conflict, not found, invalid response, payload too large, server, and unknown outcomes.
- Feature-specific errors may wrap or extend shared failures without exposing HTTP status codes, transport codes, DTOs, serialization types, or exceptions.
- Coroutine cancellation always propagates as control flow.

### Structured validation

- Preserve safe global messages and field-keyed messages needed for user correction.
- When the backend supplies no safe global message, presentation uses one generic localized validation message.
- Correlation identifiers and raw backend/transport diagnostics remain in approved non-domain diagnostics only.

### Behavior preservation

- Make the initial call plus at most three automatic retries after 500 ms, 1 second, and 2 seconds for connectivity, timeout, and 5xx failures.
- Retry only reads or writes already protected by an idempotency key, stop immediately on success, and never retry authentication, authorization, validation, conflict, not-found, rate-limit, invalid-response, payload-too-large, unknown, or cancellation outcomes.
- Introduce no cache, local persistence, synchronization, or offline-first behavior.
- Preserve explicit retry actions, endpoint semantics, request identifiers, idempotency, headers, pagination, and token acquisition behavior.
- Establish behavioral characterization tests before each migration slice, then require equivalent loading, success, empty, validation, authorization, connectivity, and retry outcomes.

### Agent's Discretion

- Exact Gradle module names, source/package migration order within each confirmed feature, and internal mapper organization, subject to the spec and active architecture decisions.
- Exact Kotlin names for shared result/error types, provided their semantics remain typed, exhaustive, framework-free, and cancellation-safe.

### Declined / Undiscussed Gray Areas -> Assumptions

- None. The proposed defaults for scope, errors, validation details, compatibility, sequence, generic validation fallback, and bounded data-layer retry were confirmed on 2026-07-22.

## Specific References

- Follow the existing KMP workspace boundary: one Compose Multiplatform implementation consumed by Android and iOS.
- Preserve `:core:network` as the Ktor transport foundation rather than turning it into a feature-domain module.

## Deferred Ideas

- Offline-first storage, caching, retry policies other than the confirmed bounded data-layer policy, new validation rules, navigation changes, screen MVI changes, DI/tooling alignment, and backend behavior changes remain separate specifications.
