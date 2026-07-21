# WhatsApp Attendance Sharing Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name
and follow its Execute flow and Critical Rules.** Do not search for skill files
by filesystem path. The skill is the source of truth for the per-task cycle,
sub-agent delegation, adequacy review, independent Verifier, and discrimination
sensor.

If the skill cannot be activated, STOP and tell the user. Do not proceed without
it.

---

**Spec:** `.specs/features/whatsapp-attendance-sharing/spec.md`
**Context:** `.specs/features/whatsapp-attendance-sharing/context.md`
**Design:** `.specs/features/whatsapp-attendance-sharing/design.md`
**Status:** Draft for approval

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec. Guidelines found:
> root `AGENTS.md`, active architecture decisions in `.specs/STATE.md`, and the
> testing contracts in the approved `group-management` feature. Existing test
> samples establish framework/style only; every acceptance criterion and listed
> edge case remains mandatory coverage.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Backend capability/application policy | Kotlin unit | Every authorization, lifecycle, privacy, retry, idempotency, deadline and rotation branch; 1:1 to WA-01, WA-02, WA-05, WA-07 and related edge cases | `backend/features/groups/src/test/kotlin/**/attendance/share/*Test.kt` | `rtk backend/gradlew -p backend :features:groups:test --console=plain` |
| Backend JDBC migration/repositories | PostgreSQL integration | Upgrade/schema constraints, digest-only persistence, atomic rotation, rollback, concurrency, invalid-attempt limits, consistent nominal projection and deterministic ordering | `backend/features/groups/src/integrationTest/kotlin/**/attendance/share/*IntegrationTest.kt` | `rtk backend/gradlew -p backend :features:groups:integrationTest --console=plain` |
| Backend HTTP/composition | Spring integration | Every new route: owner/admin success, athlete denial, privacy-equivalent non-member/invalid capability, malformed body, deadline/lifecycle, provider failure, rate limit, response redaction and Bruno contract | `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/*AttendanceShare*IntegrationTest.kt` | `rtk backend/gradlew -p backend :bootstrap:test --console=plain` |
| Mobile Ktor DTO/gateway | KMP unit | Exact method/path/body/response contracts and stable error mappings for link rotation, resolution and snapshot | `mobile/features/groups/src/commonTest/kotlin/**/data/attendance/share/*Test.kt` | `rtk mobile/gradlew -p mobile :features:groups:allTests --console=plain` |
| Mobile deferred/presentation state | KMP unit | Every pending lifecycle, auth resume, replacement, dedup, terminal/retryable outcome, group selection, explicit confirmation handoff, share/privacy state and one-shot effect | `mobile/features/groups/src/commonTest/kotlin/**/presentation/**/*Test.kt` | `rtk mobile/gradlew -p mobile :features:groups:allTests :compose-app:allTests --console=plain` |
| Compose product UI | Compose UI unit | Owner/admin visibility, athlete absence, privacy acknowledgement, loading/error/retry, image content hierarchy, empty sections, long/duplicate/diacritic names and accessibility semantics | `mobile/features/groups/src/commonTest/kotlin/**/ui/**/*Test.kt` | `rtk mobile/gradlew -p mobile :features:groups:allTests --console=plain` |
| Android Branch/local/image adapters | JVM unit plus instrumented integration | Cold/warm/direct/deferred typed dispatch and invite regression; exact image MIME/URI grant/provider containment, one-image dimensions, no clipping, cleanup and cancellation/failure | `mobile/android-app/src/test/kotlin/**/*Test.kt`, `mobile/android-app/src/androidTest/kotlin/**/*Test.kt` | `rtk mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain` |
| iOS Branch/local/image adapters | XCTest | Typed cold/warm/direct/deferred dispatch and invite regression; protected temporary PNG, one-image dimensions/content, popover-safe sheet, cleanup and completion/cancellation | `mobile/ios-app/SaqzIOSTests/**/*Tests.swift` | `rtk scripts/check-ios --dev-only` |
| Architecture/config/safety | Architecture and repository contract | Groups ownership, no backend feature coupling, no platform types in common state, no capability/name logging, narrow file exposure and no credentials | `backend/architecture-tests/src/test/**/*.kt`, `mobile/features/groups/src/commonTest/**/*FeatureTest.kt`, `mobile/compose-app/src/commonTest/**/*.kt`, `tests/scripts/**/*.sh` | `rtk scripts/check-credentials`, `rtk scripts/check-scope`, `rtk scripts/check-bruno`, `rtk scripts/check-gradle`, `rtk scripts/check-ios` |

### Sampled Existing Tests

- `backend/features/groups/src/test/kotlin/br/com/saqz/groups/adapter/output/invite/InviteTokenAdaptersTest.kt`
- `backend/features/groups/src/test/kotlin/br/com/saqz/groups/application/invite/manage/ManageInviteTest.kt`
- `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/invite/JdbcInviteManagementRepositoryIntegrationTest.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/InviteManagementEndpointIntegrationTest.kt`
- `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/attendance/AttendanceConcurrencySensorIntegrationTest.kt`
- `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/presentation/DeferredInviteCoordinatorTest.kt`
- `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/presentation/games/detail/GameDetailAttendanceViewModelTest.kt`
- `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/access/AndroidLinkAdapterTest.kt`
- `mobile/ios-app/SaqzIOSTests/IOSLinkAdapterTests.swift`
- `mobile/ios-app/SaqzIOSTests/IOSGroupPhotoAdaptersTests.swift`

## Gate Check Commands

> Generated from existing Gradle modules and repository scripts. Run commands
> separately, in the listed order; a non-zero exit blocks task completion.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Backend quick | Backend unit-only task | `rtk backend/gradlew -p backend :features:groups:test --console=plain` |
| Backend data | JDBC/schema task | `rtk backend/gradlew -p backend :features:groups:test :features:groups:integrationTest --console=plain` |
| Backend full | HTTP/composition task | `rtk backend/gradlew -p backend :features:groups:test :features:groups:integrationTest :bootstrap:test :architecture-tests:test --console=plain` |
| Mobile common | KMP DTO/state/UI task | `rtk mobile/gradlew -p mobile :features:groups:allTests :compose-app:allTests --console=plain` |
| Android unit | Android adapter without instrumentation changes | `rtk mobile/gradlew -p mobile :android-app:testDevDebugUnitTest --console=plain` |
| Android native | Android adapter task | `rtk mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain` |
| iOS native | iOS adapter task | `rtk scripts/check-ios --dev-only` |
| Safety | Security/scope boundary task | `rtk scripts/check-credentials` then `rtk scripts/check-scope` |
| API contract | After any backend route/Bruno change | `rtk scripts/check-bruno` |
| Aggregate Gradle | End of implementation | `rtk scripts/check-gradle` |
| Aggregate iOS | End of implementation | `rtk scripts/check-ios` |
| Complete | Final author gate before Verifier | `rtk scripts/check-all` |

Local backend integration gates require JDK 21 and may require
`DOCKER_HOST=unix:///Users/bruno_almeida/.colima/default/docker.sock` plus
`TESTCONTAINERS_RYUK_DISABLED=true` in this environment.

---

## Execution Plan

Phases and tasks execute strictly in order. Tests are written in the same task
as production code. Every completed task receives one atomic Conventional
Commit containing no unrelated worktree changes.

### Phase 1: Authoritative Backend

```text
T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T07 -> T08 -> T09
```

### Phase 2: Deferred Mobile Link

```text
T09 -> T10 -> T11 -> T12 -> T13 -> T14 -> T15 -> T16
```

### Phase 3: Image and End-to-End Sharing

```text
T16 -> T17 -> T18 -> T19 -> T20 -> T21 -> T22
```

Total: 22 tasks. At Execute, these three whole phases pack into approximately
three sequential task-budgeted batches. Offer batch sub-agents and wait for user
confirmation before dispatching any implementation worker.

---

## Task Breakdown

### T01: Add Attendance-Link Persistence Schema

**What:** Add the Flyway migration for one active digest per game and an
attendance-link invalid-attempt window keyed by authenticated user.

**Where:** `backend/features/groups/src/main/resources/db/migration/`,
`backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/migration/`

**Depends on:** None

**Reuses:** `V1__create_access_schema.sql` invite digest/limit constraints and
attendance game/group/user foreign-key conventions.

**Requirement:** WA-02, WA-07, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] One game has at most one active attendance-link row and digests are globally unique and exactly 32 bytes.
- [ ] Creator, group and game references are constrained without storing raw capability values.
- [ ] Invalid-attempt count/window constraints enforce the ten-attempt policy.
- [ ] Upgrade from the existing schema preserves all current group/attendance data.
- [ ] At least 10 new migration integration tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(groups): add attendance link persistence schema`

---

### T02: Implement Attendance Capability and Branch Primitives

**What:** Add attendance-specific code/digest/token value objects, secure token
generation, and strict Branch Long Link construction.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/attendance/share/`,
`backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/crypto/`,
`backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/link/`

**Depends on:** T01

**Reuses:** `InviteTokenPorts.kt`, `JcaSecureTokenGenerator.kt`, and
`BranchInviteLinkFactory.kt` behavior without changing invite contracts.

**Requirement:** WA-01, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Generated codes are exactly 43-character unpadded Base64URL values backed by 32 random bytes.
- [ ] Digests defensively copy exactly 32 bytes and all capability `toString` output is redacted.
- [ ] Branch URLs contain only `attendance/<code>`, `saqz_attendance=<code>`, and required provider flags.
- [ ] Domain validation rejects non-HTTPS, path/query/fragment/user-info/port configuration.
- [ ] Tests prove URLs/diagnostics contain no group, game, member, contact, attendance or finance data.
- [ ] At least 10 new unit tests pass with zero failures/skips.
- [ ] Backend quick gate passes.

**Tests:** Kotlin unit
**Gate:** Backend quick
**Commit:** `feat(groups): add attendance capability primitives`

---

### T03: Implement Atomic Attendance-Link Rotation Repository

**What:** Add the JDBC repository that privacy-loads and locks a game and
atomically inserts/replaces its sole active capability digest.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/attendance/share/`,
`backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/attendance/share/`

**Depends on:** T02

**Reuses:** `JdbcInviteManagementRepository` lock/upsert/rollback patterns.

**Requirement:** WA-02, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Repository locks the target game before rotation and stores only the digest.
- [ ] Rotation replaces the previous digest atomically and preserves creator/timestamps correctly.
- [ ] Simultaneous rotations leave exactly one valid persisted digest.
- [ ] Missing/inaccessible game is indistinguishable through the repository contract.
- [ ] Injected failure rolls back without losing the prior active capability.
- [ ] At least 9 new integration tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(groups): persist atomic attendance link rotation`

---

### T04: Implement Capability Resolution and Attempt-Limit Repository

**What:** Add digest lookup, current-member target resolution, and the
attendance-specific invalid-attempt sliding window.

**Where:** Same attendance/share JDBC package and integration-test package as T03.

**Depends on:** T03

**Reuses:** `JdbcInviteRedemptionRepository` digest lookup and ten-in-ten-minute
limit semantics; current membership privacy queries.

**Requirement:** WA-05, WA-07, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Digest lookup returns a target only for a current same-group member.
- [ ] Unknown/rotated capabilities and non-members have the same empty result.
- [ ] Ten invalid attempts in ten minutes are allowed and the next is rate-limited with exact retry duration.
- [ ] Attempts 1 through 10 remain counted for the full ten-minute window, attempt 11 returns exact retry duration, success does not reset the count, and only window expiry starts a new window.
- [ ] Concurrent attempts cannot bypass the bound or corrupt counters.
- [ ] At least 10 new integration tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(groups): resolve attendance capabilities safely`

---

### T05: Implement Organizer Attendance-Link Rotation Use Case

**What:** Add the application use case that authorizes owner/admin, validates
game lifecycle/deadline, creates the Branch URL, and then rotates persistence.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/attendance/share/RotateAttendanceLink.kt`

**Depends on:** T04

**Reuses:** `RotateInvite`, `GroupAccessPolicy`, existing game lifecycle models.

**Requirement:** WA-01, WA-02

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Owner and admin can rotate at `deadline - 1ns` and exactly at the confirmation deadline for a published game.
- [ ] Athlete/non-member, draft/cancelled/completed game, and `deadline + 1ns` are denied without persistence.
- [ ] Branch failure leaves the prior active capability unchanged.
- [ ] Returned application model contains only the share URL.
- [ ] Telemetry contains only operation, coarse outcome/error, correlation ID and duration, without role, lifecycle, count, target, capability, contact or private game data.
- [ ] At least 10 new unit tests cover all branches with zero failures/skips.
- [ ] Backend quick gate passes.

**Tests:** Kotlin unit
**Gate:** Backend quick
**Commit:** `feat(groups): authorize attendance link rotation`

---

### T06: Implement Read-Only Attendance-Link Resolution Use Case

**What:** Add authenticated syntax/rate/lifecycle/membership resolution that
returns the exact private destination without invoking attendance mutation.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/attendance/share/ResolveAttendanceLink.kt`

**Depends on:** T05

**Reuses:** `RedeemInvite` public-result privacy and existing game deadline rules.

**Requirement:** WA-05, WA-06, WA-07

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Valid current-member capability returns only group/game destination IDs.
- [ ] Resolution performs no attendance, audit, charge or membership mutation.
- [ ] Malformed, unknown, rotated, non-member, non-published and passed-deadline cases share one terminal result.
- [ ] Rate-limited result carries exact retry duration and infrastructure failure remains retryable.
- [ ] Repeated successful resolution is equivalent and side-effect free.
- [ ] Boundary tests prove resolution succeeds at `deadline - 1ns` and `deadline` and is terminal at `deadline + 1ns`.
- [ ] Outcome telemetry distinguishes success, terminal, rate-limited and unavailable without capability, member or target data.
- [ ] At least 12 new unit tests pass with zero failures/skips.
- [ ] Backend quick gate passes.

**Tests:** Kotlin unit
**Gate:** Backend quick
**Commit:** `feat(groups): resolve attendance links without mutation`

---

### T07: Expose and Wire Attendance-Link HTTP Contracts

**What:** Add rotate/resolve endpoints, stable API problems, Spring composition,
and Bruno examples for the capability flow.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/input/http/AttendanceShareController.kt`,
`backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/`,
`backend/bootstrap/src/test/`, root `bruno/` collection.

**Depends on:** T06

**Reuses:** Invite controllers, `ApiProblemWriter`, existing Branch configuration.

**Requirement:** WA-01 through WA-07, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] `POST .../attendance-link` and `POST /api/attendance-links/resolve` expose exactly the approved DTOs.
- [ ] Owner/admin, athlete, unauthenticated, non-member, malformed, rotated, deadline, provider failure and rate-limit paths have exact assertions.
- [ ] Responses/problems/log fixtures never expose raw capabilities or private target fields on failure.
- [ ] Existing invite endpoints and Branch configuration remain behaviorally unchanged.
- [ ] Resolving then submitting the existing confirmation twice leaves one attendance row, one stable waitlist position when full, no duplicate audit event and no duplicate charge.
- [ ] Bruno examples cover rotation and authenticated resolution without real credentials.
- [ ] At least 16 new Spring integration tests pass with zero failures/skips.
- [ ] Backend full, API contract and safety gates pass.

**Tests:** Spring integration plus architecture/safety
**Gate:** Backend full, API contract, Safety
**Commit:** `feat(groups): expose attendance link endpoints`

---

### T08: Implement Organizer Nominal Attendance Projection

**What:** Add the application query and JDBC projection for one consistent,
ordered confirmed/waitlisted/declined snapshot.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/attendance/share/AttendanceShareSnapshot.kt`,
attendance/share JDBC package and integration tests.

**Depends on:** T07

**Reuses:** Attendance schema, `AccessName`, membership name/order conventions,
group/game privacy queries.

**Requirement:** WA-08, WA-09, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Owner/admin receive game presentation fields plus only confirmed, waitlisted and declined responders.
- [ ] Athletes/non-members receive no nominal data.
- [ ] Waitlist is FIFO; other sections use deterministic name ordering with duplicate names preserved.
- [ ] No-response members are absent and no internal user/contact IDs leave the projection.
- [ ] One read transaction produces a consistent snapshot under concurrent promotion/capacity changes.
- [ ] Diacritics and maximum valid display names round-trip unchanged.
- [ ] Snapshot telemetry contains only operation, coarse outcome/error, correlation ID and duration, without authorization role, lifecycle, counts, names or private game fields.
- [ ] At least 14 unit/integration tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** Kotlin unit plus PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(groups): project nominal attendance sharing data`

---

### T09: Expose and Wire Organizer Snapshot Endpoint

**What:** Add the organizer-only attendance-share GET endpoint, DTO mapping,
composition, diagnostics redaction and Bruno example.

**Where:** Existing `AttendanceShareController.kt`, bootstrap composition/tests,
root `bruno/` collection.

**Depends on:** T08

**Reuses:** Existing attendance HTTP conventions and privacy problem mapping.

**Requirement:** WA-08, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Owner and admin receive the exact snapshot DTO; athlete is `403`; inaccessible group/game is privacy-preserving `404`.
- [ ] Response contains no timestamp, no-response section, IDs, email, phone or username.
- [ ] Empty and populated sections serialize deterministically.
- [ ] Malformed IDs and repository failure map to existing stable problems without leaking names.
- [ ] Bruno example uses a fake authenticated environment and documents nominal-data sensitivity.
- [ ] At least 12 new Spring integration tests pass with zero failures/skips.
- [ ] Backend full, API contract and safety gates pass.

**Tests:** Spring integration plus safety
**Gate:** Backend full, API contract, Safety
**Commit:** `feat(groups): expose attendance share snapshot`

---

### T10: Add Mobile Attendance-Sharing Gateway

**What:** Add serializable DTOs and Ktor gateway operations for rotate, resolve,
and organizer snapshot APIs.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/attendance/share/AttendanceShareApi.kt`

**Depends on:** T09

**Reuses:** Existing `AuthenticatedNetworkClient`, `NetworkResult`, and
`AttendanceApiTest` request-capture style.

**Requirement:** WA-03, WA-05, WA-08

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Exact methods, paths, empty/body contracts and DTO fields match backend responses.
- [ ] Snapshot DTO has three sections and no timestamp, IDs or contact fields in person rows.
- [ ] Stable terminal, retryable, rate-limit, authorization and provider errors map through existing network results.
- [ ] At least 12 new common tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit
**Gate:** Mobile common
**Commit:** `feat(groups): add attendance sharing mobile gateway`

---

### T11: Introduce Typed Group-Link Event Contract

**What:** Replace invite-only native delivery with one provider-neutral typed
event contract while preserving the existing invitation coordinator behavior.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/port/NativeGroupPorts.kt`,
`DeferredInviteCoordinator.kt` and common tests.

**Depends on:** T10

**Reuses:** Existing listener/cancelable patterns and invite code validation.

**Requirement:** WA-04, WA-07

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Common contract distinguishes invite from attendance capabilities without provider types.
- [ ] Invite coordinator consumes only invite events and all existing invite lifecycle behavior remains unchanged.
- [ ] Unknown/malformed events cannot enter common pending state.
- [ ] At least 8 new/updated common tests pass and no existing invite test is removed or weakened.
- [ ] Mobile common gate passes.

**Tests:** KMP unit
**Gate:** Mobile common
**Commit:** `refactor(groups): type native group link events`

---

### T12: Dispatch Typed Attendance Links on Android

**What:** Refactor the single Android Branch adapter to parse, buffer and dedupe
typed invite/attendance events from direct, cold, warm and deferred delivery.

**Where:** `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/access/AndroidLinkAdapter.kt`,
Android group-port bridge and JVM tests.

**Depends on:** T11

**Reuses:** Current exact Base64URL validation, Branch initialization and direct
versus provider dedup.

**Requirement:** WA-04, WA-07, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] `saqz_attendance` and `attendance/<code>` produce attendance events only.
- [ ] Existing `saqz_invite` behavior remains unchanged.
- [ ] Direct/Branch duplicates emit once; distinct kind/value events remain distinct; latest pre-listener event buffers safely.
- [ ] Mixed, HTTP, malformed and provider-metadata-only payloads are rejected without leaking values.
- [ ] At least 14 Android JVM tests pass with zero failures/skips.
- [ ] Android unit portion of the native gate passes.

**Tests:** Android JVM unit
**Gate:** Android unit
**Commit:** `feat(android): dispatch attendance deep links`

---

### T13: Dispatch Typed Attendance Links on iOS

**What:** Apply the same typed parse, buffer and dedup contract to the sole iOS
Branch adapter across cold, URL and universal-link delivery.

**Where:** `mobile/ios-app/SaqzIOS/IOSLinkAdapter.swift`,
`mobile/ios-app/SaqzIOSTests/IOSLinkAdapterTests.swift`

**Depends on:** T12

**Reuses:** Existing iOS Branch lifecycle and validation behavior.

**Requirement:** WA-04, WA-07, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] iOS behavior matches Android for attendance, invite compatibility, dedup, buffering and rejection.
- [ ] No second Branch session consumer is introduced.
- [ ] At least 14 iOS link tests pass with zero failures/skips.
- [ ] iOS native gate passes.

**Tests:** XCTest
**Gate:** iOS native
**Commit:** `feat(ios): dispatch attendance deep links`

---

### T14: Persist Pending Attendance Capability Locally

**What:** Extend the Groups local-state port and Android/iOS implementations to
store, replace and clear one opaque pending attendance capability.

**Where:** `NativeGroupPorts.kt`, Android shared-preferences adapter, iOS
UserDefaults/Keychain access-state adapter and their tests.

**Depends on:** T13

**Reuses:** Existing pending-invite storage, namespacing and callback/result
semantics.

**Requirement:** WA-04, WA-07, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Only the opaque attendance code persists; no resolved group/game/private data is written.
- [ ] Read/write/replace/clear behavior is equivalent on Android and iOS and survives safe restart.
- [ ] Invite and attendance values cannot overwrite or masquerade as each other.
- [ ] At least 6 Android and 6 iOS new tests pass with zero failures/skips.
- [ ] Android unit and iOS native gates pass.

**Tests:** Android JVM unit plus XCTest
**Gate:** Android unit, iOS native
**Commit:** `feat(mobile): persist pending attendance links`

---

### T15: Implement Deferred Attendance-Link Coordinator

**What:** Add the shared state machine that waits for verified auth, resolves the
pending capability, handles replacement/retry/terminal state, and emits one
private destination.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/attendance/share/DeferredAttendanceLinkCoordinator.kt`

**Depends on:** T14

**Reuses:** `DeferredInviteCoordinator` lifecycle structure without membership
redemption behavior.

**Requirement:** WA-04, WA-05, WA-06, WA-07

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Cold/warm/deferred code waits through install/auth and resolves once the session is verified.
- [ ] Resolution itself never calls attendance mutation.
- [ ] New code replaces old pending state safely during an in-flight request.
- [ ] Direct/provider duplicate event opens at most one destination.
- [ ] Terminal/discard/logout clears; temporary/rate-limited outcome remains retryable with exact timing.
- [ ] At least 16 common state-machine tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit
**Gate:** Mobile common
**Commit:** `feat(groups): coordinate deferred attendance links`

---

### T16: Route Resolved Attendance Links to the Exact Game

**What:** Compose the coordinator into authenticated app navigation so a
resolved target invalidates old private state, selects its group and opens the
existing game detail route once.

**Where:** `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/`,
compose-app navigation tests.

**Depends on:** T15

**Reuses:** Selected-group reconciliation, private-state invalidation and current
game-detail destination patterns.

**Requirement:** WA-04, WA-05, WA-06, WA-07

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Resolved target selects the correct group before rendering the exact game.
- [ ] Old selected-group private state is cleared before target content appears.
- [ ] Game opens with current attendance unchanged and explicit confirm/decline controls.
- [ ] Duplicate effects, recreation and auth bootstrap do not open twice.
- [ ] Non-member/terminal target never renders a private preview.
- [ ] At least 12 new compose-app navigation tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP/navigation integration
**Gate:** Mobile common
**Commit:** `feat(mobile): route attendance links to games`

---

### T17: Define Deterministic Attendance Image Content and Layout

**What:** Add a shared immutable image model and checked layout calculation for
one vertical image containing all three sections.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/attendance/share/AttendanceShareImage.kt`

**Depends on:** T16

**Reuses:** Saqz design tokens, localized pt-BR game formatting and validated
snapshot DTOs.

**Requirement:** WA-09, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Model includes exact pt-BR game title, local date/time in the group timezone, venue, capacity, `Confirmados`, `Lista de espera`, `Fora`, all three counts, names and waitlist positions only.
- [ ] No timestamp, no-response section, pagination, ID, email, phone or username exists in output model.
- [ ] Height calculation accounts for every wrapped row using checked arithmetic and fixed accessible typography tokens.
- [ ] Empty sections remain visible with zero count and explicit empty text.
- [ ] Duplicate/long/diacritic names remain separate and deterministic.
- [ ] At least 14 common tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit
**Gate:** Mobile common
**Commit:** `feat(groups): define attendance share image layout`

---

### T18: Add Game-Detail Attendance Sharing State Machine

**What:** Extend the route ViewModel with owner/admin link sharing, snapshot
loading, privacy acknowledgement, retry and one-shot text/image effects.

**Where:** Existing `GameDetailViewModel.kt` and common tests.

**Depends on:** T17

**Reuses:** Typed `onIntent`, immutable state/effects and existing attendance
refresh/idempotency behavior.

**Requirement:** WA-03, WA-06, WA-08, WA-10

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Owner/admin can request link sharing; athletes cannot produce the intent from route access state.
- [ ] Snapshot fetch is organizer-only and creates image content only after explicit privacy acknowledgement.
- [ ] Image share cancellation/failure retains a retryable image model without changing attendance.
- [ ] Link/image effects emit exactly once across recomposition/retry.
- [ ] Failed/cancelled link sharing retains the exact returned URL; retry emits that URL with zero additional rotate calls, while explicit new generation rotates it.
- [ ] Existing confirmation remains explicit and uses the unchanged authoritative mutation command.
- [ ] Route telemetry records rotate/snapshot/share outcomes without URL, capability, names, image content or private game fields.
- [ ] At least 16 new ViewModel tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit
**Gate:** Mobile common
**Commit:** `feat(groups): orchestrate attendance sharing`

---

### T19: Add Compose Sharing Controls and Privacy Acknowledgement

**What:** Add stateless organizer controls, progress/error/retry states and the
nominal-data privacy confirmation to the game-detail UI.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/games/detail/`,
Compose UI tests and localized resources.

**Depends on:** T18

**Reuses:** Existing Saqz buttons/dialogs, game-detail semantics and pt-BR style.

**Requirement:** WA-03, WA-08, WA-10

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Owner/admin see “Compartilhar link” and “Compartilhar lista”; athlete sees neither.
- [ ] Link action has clear loading/retry feedback without a privacy dialog.
- [ ] List action explains that names will leave Saqz and requires explicit continue/cancel each time.
- [ ] UI never claims WhatsApp delivery or live image freshness.
- [ ] Controls meet 48 dp, large-text, keyboard/screen-reader and compact-layout contracts.
- [ ] At least 14 new Compose UI tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** Compose UI unit
**Gate:** Mobile common
**Commit:** `feat(groups): add attendance sharing controls`

---

### T20: Implement Android Attendance Sharing

**What:** Add the Groups-owned Android Activity Result share adapter for the
exact cached link and for one rasterized PNG with narrow FileProvider access.

**Where:** `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/groups/attendance/share/`,
Android manifest/XML provider paths, JVM and instrumented tests.

**Depends on:** T19

**Reuses:** Android photo cache containment/encoding and existing share-sheet
launcher patterns.

**Requirement:** WA-09, WA-10, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Adapter renders exactly one PNG at computed height with every model row present and no clipping.
- [ ] Link payload uses `text/plain` with the exact cached URL and reports cancellation/failure without requesting a new link.
- [ ] Dedicated cache directory alone is exposed through FileProvider.
- [ ] Intent uses `image/png`, `EXTRA_STREAM` and one-time read permission without hard-coding WhatsApp.
- [ ] Activity Result-backed launcher reports `Presented`, `Cancelled`, `Failed`, or `Unknown`; it distinguishes cancellation/completion only with reliable platform evidence and never claims recipient delivery.
- [ ] Terminal callbacks delete the current file; bounded startup cleanup handles missing/unreliable Android callbacks; allocation/encode failure leaves no partial file.
- [ ] Cancellation/failure keeps the common image model retryable and never changes attendance.
- [ ] Outcome telemetry excludes image bytes, names, file paths/URIs, capability and private game fields.
- [ ] Long/empty/three-section fixtures verify dimensions, pixels/semantics and no omitted rows.
- [ ] At least 10 JVM and 5 instrumented tests pass with zero failures/skips.
- [ ] Android native and safety gates pass.

**Tests:** Android JVM unit plus instrumented integration
**Gate:** Android native, Safety
**Commit:** `feat(android): share attendance images safely`

---

### T21: Implement iOS Attendance Sharing

**What:** Add the equivalent Groups-owned link/PNG adapter with
CoreGraphics/ImageIO protected files and popover-safe activity lifecycle.

**Where:** `mobile/ios-app/SaqzIOS/GroupsAttendanceShare/`, iOS app composition,
`mobile/ios-app/SaqzIOSTests/`

**Depends on:** T20

**Reuses:** iOS group-photo atomic/protected files and current activity-share
presentation pattern.

**Requirement:** WA-09, WA-10, WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] iOS renders one PNG with the same content/layout contract and every row present.
- [ ] Link payload shares the exact cached URL and reports cancellation/failure without requesting a new link.
- [ ] File is atomically written with complete protection and only its URL enters `UIActivityViewController`.
- [ ] Phone/tablet presentation is safe; completion/cancellation/error cleans temporary files.
- [ ] Adapter reports share-sheet outcome only and never claims WhatsApp delivery.
- [ ] Outcome telemetry excludes image bytes, names, file URLs, capability and private game fields.
- [ ] Long/empty/three-section fixtures match Android content and dimension rules.
- [ ] At least 15 new XCTest cases pass with zero failures/skips.
- [ ] iOS native and safety gates pass.

**Tests:** XCTest
**Gate:** iOS native, Safety
**Commit:** `feat(ios): share attendance images safely`

---

### T22: Complete Cross-Platform Sharing Journeys and Aggregate Gates

**What:** Wire image/text effects through Android/iOS composition, add complete
owner/admin/athlete journeys and architecture sensors, then run all repository
gates from a clean task state.

**Where:** `mobile/compose-app` runtime/navigation, Android/iOS composition,
backend/mobile architecture tests, cross-platform journey tests, this feature's
spec/task status.

**Depends on:** T21

**Reuses:** Existing authenticated group journey fixtures, architecture
inventories and aggregate scripts.

**Requirement:** WA-01 through WA-11

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Owner and admin journeys create/share a link, resume exact game after auth, explicitly confirm, and share one three-section image.
- [ ] Athlete/non-member journeys prove link-management and nominal-image privacy boundaries.
- [ ] Cold/warm/direct/deferred and duplicate delivery journeys pass on Android and iOS without invite regression.
- [ ] Repeated-confirm journeys below capacity and at capacity prove one row, stable FIFO position, no duplicate event and no duplicate charge.
- [ ] Architecture tests prove Groups ownership, provider-neutral common code, no backend cross-feature dependency and no broad file exposure.
- [ ] At least 12 new cross-layer journey/architecture tests pass with zero failures/skips.
- [ ] `scripts/check-credentials`, `scripts/check-scope`, `scripts/check-bruno`, `scripts/check-gradle`, `scripts/check-ios`, and fresh `scripts/check-all` all pass.
- [ ] All 11 requirement rows move to `Implementing`; task checkboxes and evidence are current before independent verification.

**Tests:** Cross-platform integration/journey plus architecture/safety
**Gate:** Safety, Aggregate Gradle, Aggregate iOS, Complete
**Commit:** `feat(groups): complete attendance sharing journeys`

---

## Phase Execution Map

```text
Phase 1: T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T07 -> T08 -> T09
                                                                         |
                                                                         v
Phase 2: T10 -> T11 -> T12 -> T13 -> T14 -> T15 -> T16
                                                       |
                                                       v
Phase 3: T17 -> T18 -> T19 -> T20 -> T21 -> T22
```

Execution is strictly sequential. Transitive prerequisites are inherited; each
task body lists its immediate predecessor, matching the map.

---

## Task Granularity Check

| Task | Single Deliverable | Status |
| --- | --- | --- |
| T01 | Persistence migration | PASS |
| T02 | Capability/link primitives | PASS |
| T03 | Rotation JDBC repository | PASS |
| T04 | Resolution/limit JDBC repository | PASS |
| T05 | Rotation use case | PASS |
| T06 | Resolution use case | PASS |
| T07 | Link HTTP adapter/composition | PASS |
| T08 | Nominal projection | PASS |
| T09 | Snapshot HTTP adapter/composition | PASS |
| T10 | Mobile API gateway | PASS |
| T11 | Common typed link contract | PASS |
| T12 | Android Branch dispatcher | PASS |
| T13 | iOS Branch dispatcher | PASS |
| T14 | Cross-platform pending state port | PASS - one cohesive platform contract |
| T15 | Deferred attendance coordinator | PASS |
| T16 | Navigation integration | PASS |
| T17 | Shared image model/layout | PASS |
| T18 | Game-detail sharing state machine | PASS |
| T19 | Compose sharing UI | PASS |
| T20 | Android image share adapter | PASS |
| T21 | iOS image share adapter | PASS |
| T22 | Composition/journey aggregate | PASS - earliest runnable cross-platform seam |

No task combines independent product capabilities. Platform implementations are
separate tasks; tests remain co-located with each production deliverable.

---

## Diagram-Definition Cross-Check

| Task | Depends On in Body | Diagram Shows | Status |
| --- | --- | --- | --- |
| T01 | None | Phase start | PASS |
| T02 | T01 | T01 -> T02 | PASS |
| T03 | T02 | T02 -> T03 | PASS |
| T04 | T03 | T03 -> T04 | PASS |
| T05 | T04 | T04 -> T05 | PASS |
| T06 | T05 | T05 -> T06 | PASS |
| T07 | T06 | T06 -> T07 | PASS |
| T08 | T07 | T07 -> T08 | PASS |
| T09 | T08 | T08 -> T09 | PASS |
| T10 | T09 | T09 -> T10 | PASS |
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

All body dependencies and diagram arrows match; none points to a later task.

---

## Test Co-location Validation

| Task | Code Layer | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T01 | Schema/migration | PostgreSQL integration | PostgreSQL integration | PASS |
| T02 | Backend primitive/adapter | Kotlin unit | Kotlin unit | PASS |
| T03 | JDBC repository | PostgreSQL integration | PostgreSQL integration | PASS |
| T04 | JDBC repository | PostgreSQL integration | PostgreSQL integration | PASS |
| T05 | Application policy | Kotlin unit | Kotlin unit | PASS |
| T06 | Application policy | Kotlin unit | Kotlin unit | PASS |
| T07 | HTTP/composition | Spring integration | Spring integration plus architecture/safety | PASS |
| T08 | Application/JDBC projection | Unit plus integration | Kotlin unit plus PostgreSQL integration | PASS |
| T09 | HTTP/composition | Spring integration | Spring integration plus safety | PASS |
| T10 | Ktor DTO/gateway | KMP unit | KMP unit | PASS |
| T11 | Common link contract | KMP unit | KMP unit | PASS |
| T12 | Android adapter | Android JVM unit | Android JVM unit | PASS |
| T13 | iOS adapter | XCTest | XCTest | PASS |
| T14 | Native local state | Android unit plus XCTest | Android JVM unit plus XCTest | PASS |
| T15 | Shared state machine | KMP unit | KMP unit | PASS |
| T16 | Navigation integration | KMP/navigation integration | KMP/navigation integration | PASS |
| T17 | Shared layout logic | KMP unit | KMP unit | PASS |
| T18 | Route ViewModel | KMP unit | KMP unit | PASS |
| T19 | Compose UI | Compose UI unit | Compose UI unit | PASS |
| T20 | Android image adapter | JVM plus instrumentation | Android JVM plus instrumented integration | PASS |
| T21 | iOS image adapter | XCTest | XCTest | PASS |
| T22 | Composition/architecture | Cross-platform journey plus architecture | Cross-platform journey plus architecture/safety | PASS |

No production layer defers its required test type to a later task. T22 tests only
the composition seam that becomes runnable after both native adapters exist.

---

## Requirement-to-Task Coverage

| Requirement | Tasks |
| --- | --- |
| WA-01 | T02, T05, T07, T22 |
| WA-02 | T01, T03, T05, T07, T22 |
| WA-03 | T07, T10, T18, T19, T22 |
| WA-04 | T11-T16, T22 |
| WA-05 | T04, T06, T07, T10, T15, T16, T22 |
| WA-06 | T06, T15, T16, T18, T22 |
| WA-07 | T01, T04, T06, T07, T10-T16, T22 |
| WA-08 | T08-T10, T18, T19, T22 |
| WA-09 | T08, T10, T17, T20-T22 |
| WA-10 | T17-T22 |
| WA-11 | T01-T04, T07-T09, T11-T14, T17, T20-T22 |

**Coverage:** 11 requirements, 11 mapped, 0 unmapped.

---

## Execute Entry Gate

Before T01 starts:

- [ ] User approves this task plan and provisional test matrix.
- [ ] User chooses whether to use the proposed three sequential sub-agent batches or execute inline.
- [ ] User confirms the default tool choice (`tlc-spec-driven`, repository tools, no additional MCP/skill) or names alternatives.
- [ ] Current `group-management` handoff/worktree changes are reconciled or confirmed non-conflicting; no unrelated changes are staged.
- [ ] `rtk git status`, `rtk git diff`, and the active handoff are re-read.

After T22, the orchestrator automatically runs an independent Verifier (author
is not verifier), performs the spec-anchored outcome check and discrimination
sensor, writes `validation.md`, and loops through grounded fixes up to the skill
limit before reporting completion.
