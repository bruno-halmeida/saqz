# Athlete Management Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name
and follow its Execute flow and Critical Rules.** Do not search for skill files
by filesystem path. The skill is the source of truth for the per-task cycle,
sub-agent delegation, adequacy review, independent Verifier, and discrimination
sensor.

If the skill cannot be activated, STOP and tell the user. Do not proceed without
it.

---

**Spec:** `.specs/features/athlete-management/spec.md`
**Context:** `.specs/features/athlete-management/context.md`
**Design:** `.specs/features/athlete-management/design.md`
**Status:** Draft for approval

---

## Scope Notes

- The épico's web screens are deferred: the mobile workspace has no web target
  (Android + iOS only). Web lands when a Compose web surface exists; common
  ViewModels/gateways built here are reused unchanged.
- The `namecompletion` MVI refactor currently in progress on `main` must land
  before Phase 2 starts; T09 extends that route.

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec. Guidelines found:
> root `AGENTS.md`, active architecture decisions in `.specs/STATE.md`, and the
> testing contracts of the approved `group-management` and
> `whatsapp-attendance-sharing` features. Existing test samples establish
> framework/style only; every acceptance criterion and listed edge case remains
> mandatory coverage.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Access phone domain/session | Kotlin unit | Phone parse/normalize/reject branches, `phoneRequired` derivation, idempotent profile update; 1:1 to ATH-01 | `backend/features/access/src/test/kotlin/**/*Test.kt` | `rtk backend/gradlew -p backend :features:access:test --console=plain` |
| Backend migrations/repositories | PostgreSQL integration | Additive upgrade, constraints, owner-row backfill, FK repoint, snapshot backfill, roster filters/derivation, removal survival of history | `backend/features/{access,groups}/src/integrationTest/kotlin/**/*IntegrationTest.kt` | `rtk backend/gradlew -p backend :features:access:integrationTest :features:groups:integrationTest --console=plain` |
| Groups athlete application | Kotlin unit | Every authorization, owner-immutable, idempotency, active-exclusion, snapshot-write and filter-combination branch; ATH-02..05 | `backend/features/groups/src/test/kotlin/**/athlete/*Test.kt` | `rtk backend/gradlew -p backend :features:groups:test --console=plain` |
| Backend HTTP/composition | Spring integration | Every new route: organizer success, athlete/self scopes, privacy `404`, malformed payloads, Bruno contracts | `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/*Athlete*IntegrationTest.kt`, `*SessionProfile*IntegrationTest.kt` | `rtk backend/gradlew -p backend :bootstrap:test --console=plain` |
| Mobile Ktor DTO/gateway | KMP unit | Exact method/path/body/response contracts and stable error mapping for profile completion and athlete routes | `mobile/features/{access,groups}/src/commonTest/kotlin/**/data/**/*Test.kt` | `rtk mobile/gradlew -p mobile :features:access:allTests :features:groups:allTests --console=plain` |
| Mobile presentation state | KMP unit | Completion gate sequencing with pending invite, onboarding skip/choose/retry, roster filters/edit/remove, profile state; SavedStateHandle restoration | `mobile/features/{access,groups}/src/commonTest/kotlin/**/presentation/**/*Test.kt` | `rtk mobile/gradlew -p mobile :features:access:allTests :features:groups:allTests :compose-app:allTests --console=plain` |
| Compose product UI | Compose UI unit | Phone mask/validation UX, skippable position step, roster search/filter/badge rendering, role-gated controls, removal confirmation, pt-BR labels, accessibility semantics | `mobile/features/{access,groups}/src/commonTest/kotlin/**/ui/**/*Test.kt` | `rtk mobile/gradlew -p mobile :features:access:allTests :features:groups:allTests --console=plain` |
| Architecture/config/safety | Architecture and repository contract | Feature boundaries, migration/table inventory sensors, no phone in logs, scope allowlist, no credentials | `backend/architecture-tests/src/test/**/*.kt`, `tests/scripts/**/*.sh` | `rtk scripts/check-credentials`, `rtk scripts/check-scope`, `rtk scripts/check-bruno`, `rtk scripts/check-gradle`, `rtk scripts/check-ios` |

### Sampled Existing Tests

- `backend/features/access/src/test/kotlin` session bootstrap tests
- `backend/features/groups/src/test/kotlin/br/com/saqz/groups/domain/GroupRoleTest.kt`
- `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/GroupsModuleIntegrationTest.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/MembershipEndpointIntegrationTest.kt`
- `mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/presentation/namecompletion/`
- `mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/ui/IdentityCompletionScreensTest.kt`
- `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/presentation/DeferredInviteCoordinatorTest.kt`

## Gate Check Commands

> Run commands separately, in the listed order; a non-zero exit blocks task
> completion.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Access quick | Access unit-only task | `rtk backend/gradlew -p backend :features:access:test --console=plain` |
| Groups quick | Groups unit-only task | `rtk backend/gradlew -p backend :features:groups:test --console=plain` |
| Backend data | JDBC/schema task | `rtk backend/gradlew -p backend :features:access:test :features:access:integrationTest :features:groups:test :features:groups:integrationTest --console=plain` |
| Backend full | HTTP/composition task | `rtk backend/gradlew -p backend :features:access:test :features:groups:test :features:access:integrationTest :features:groups:integrationTest :bootstrap:test :architecture-tests:test --console=plain` |
| Mobile common | KMP DTO/state/UI task | `rtk mobile/gradlew -p mobile :features:access:allTests :features:groups:allTests :compose-app:allTests --console=plain` |
| Android native | Android adapter/journey task | `rtk mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain` |
| iOS native | iOS adapter/journey task | `rtk scripts/check-ios --dev-only` |
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
T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T07
```

### Phase 2: Mobile Flows

```text
T07 -> T08 -> T09 -> T10 -> T11 -> T12 -> T13 -> T14
```

### Phase 3: Closeout

```text
T14 -> T15
```

Total: 15 tasks. At Execute, these three phases pack into approximately three
sequential task-budgeted batches. Offer batch sub-agents and wait for user
confirmation before dispatching any implementation worker.

---

## Task Breakdown

### T01: Add Account Phone Schema and Value Object

**What:** Add the Access Flyway migration for `access_users.phone` and the
`PhoneNumber` value object (BR mobile parse, E.164 normalization, rejection).

**Where:** `backend/features/access/src/main/resources/db/migration/V8__add_user_phone.sql`,
`backend/features/access/src/main/kotlin/br/com/saqz/access/domain/`,
access test/integration-test packages.

**Depends on:** None

**Reuses:** `AccessName` validation conventions; `V1` `CHECK`-constraint style;
migration inventory sensor update pattern (B59/B60 precedent).

**Requirement:** ATH-01

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Migration is additive; existing rows keep `phone NULL` and all current data survives upgrade.
- [ ] DB `CHECK` accepts only a normalized `+55` mobile number (2-digit DDD + leading `9` + 8 digits = 11 national digits) or `NULL`; a 10-digit landline number is rejected.
- [ ] `PhoneNumber` parses common masked BR inputs, normalizes to E.164, and rejects implausible numbers with typed failures.
- [ ] `toString` of the value object does not leak the full number into diagnostics.
- [ ] Access migration/table inventory sensors updated in this task.
- [ ] At least 10 new tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** Kotlin unit + PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(access): add user phone schema and value object`

---

### T02: Extend Session Contract with Phone Completion

**What:** Add `phone`/`phoneRequired` to the session response and the
`PATCH /api/session/profile` mutation (phone always accepted, displayName for
the existing conditional name case).

**Where:** `backend/features/access/src/main/kotlin/br/com/saqz/access/application/session/`,
`.../adapter/input/http/AccessSessionController.kt`, bootstrap tests, Bruno
contracts.

**Depends on:** T01

**Reuses:** `BootstrapSession`/`SessionModels` shapes; existing stable API
problem mapping.

**Requirement:** ATH-01

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] `PUT /api/session` response carries `phone` and `phoneRequired` (`phone == null`), additively.
- [ ] `PATCH /api/session/profile` validates via `PhoneNumber`, persists, and returns the refreshed session view.
- [ ] Only the authenticated account owner can set their phone; repeat submission is idempotent overwrite.
- [ ] Invalid phone returns a stable field-level problem; nothing persists.
- [ ] Phone never appears in logs or error payloads.
- [ ] Bruno contract added and `check-bruno` passes.
- [ ] At least 8 new tests pass with zero failures/skips.
- [ ] Backend full gate passes.

**Tests:** Kotlin unit + Spring integration
**Gate:** Backend full + API contract
**Commit:** `feat(access): require phone through session profile completion`

---

### T03: Add Athlete Attributes Schema, Owner Backfill, and History Snapshots

**What:** One Groups migration: membership athlete columns, missing owner
membership rows, `game_attendance` FK repoint to `access_users`, and
`member_display_name` snapshot columns with backfill on attendance and charges.

**Where:** `backend/features/groups/src/main/resources/db/migration/V8__add_athlete_attributes.sql`,
groups migration integration tests.

**Depends on:** T01

**Reuses:** `V5`/`V6` constraint style; `GroupRole.resolve` owner precedence;
migration inventory sensor update pattern.

**Requirement:** ATH-02, ATH-03, ATH-04, ATH-05

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] `position`, `membership_type` (default `AVULSO`), `active` (default true) exist with exact `CHECK` constraints; existing rows get safe defaults, nothing guessed.
- [ ] Every group owner ends with a persisted membership row; existing owner rows are untouched; effective role stays `OWNER`.
- [ ] `game_attendance.member_user_id` FK references `access_users`; deleting a membership with attendance history succeeds.
- [ ] `member_display_name` is backfilled from `access_users.display_name` and `NOT NULL` on both tables.
- [ ] Groups migration/table inventory sensors updated in this task.
- [ ] At least 12 new migration integration tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(groups): add athlete attributes and history snapshots schema`

---

### T04: Implement Athlete Domain and Management Commands

**What:** Add `AthletePosition`/`AthleteMembershipType`, extend membership
models, and implement `UpdateOwnAthleteProfile`, `UpdateAthlete`, and
`RemoveAthlete` with authorization.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/domain/`,
`.../application/athlete/`, groups unit tests.

**Depends on:** T03

**Reuses:** `GroupAccessPolicy`, `MembershipModels` result shapes,
`ChangeMemberRole` command structure.

**Requirement:** ATH-02, ATH-04

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Athlete self-update touches only the caller's own row and only `position`.
- [ ] Organizer update sets `position`/`membershipType`/`active` for any member including the owner row; no privilege changes possible.
- [ ] Removal requires `OWNER`/`ADMIN`, rejects removing the group owner, and is an idempotent no-op for absent rows.
- [ ] Non-member/athlete callers receive the existing `AccessForbidden`/`GroupNotFound` privacy shapes without mutation.
- [ ] At least 14 new unit tests pass with zero failures/skips.
- [ ] Groups quick gate passes.

**Tests:** Kotlin unit
**Gate:** Groups quick
**Commit:** `feat(groups): add athlete management commands`

---

### T05: Implement Athlete JDBC Repository and Roster Read Model

**What:** Persist athlete commands and implement `ListAthletes` with search,
combinable filters, and financial-status derivation, plus
`GetOwnAthleteProfile`.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/athlete/`,
groups integration tests.

**Depends on:** T04

**Reuses:** `JdbcMembershipRepository` owner-synthesis and `AccessName`
patterns; `group_charges` indexes.

**Requirement:** ATH-03, ATH-06

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Roster returns name, phone, position, type, active, and financial status per member with deterministic ordering.
- [ ] `PENDENTE` iff a `PENDING` charge exists for the member due on or before the current group-local month end; otherwise `EM_DIA`; charge-read failure yields `DESCONHECIDO` without failing the roster.
- [ ] Search is case- and accent-insensitive; type/position/financial/inactive filters combine with AND.
- [ ] Removal deletes exactly one row; attendance and charge history rows survive with their snapshot names.
- [ ] Re-invited removed user yields a fresh `AVULSO` row with no attribute carry-over.
- [ ] Own-profile read returns the caller's memberships with attributes and never other members' phones.
- [ ] At least 14 new integration tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(groups): persist athlete roster and management`

---

### T06: Write Name Snapshots and Respect Active Flag in Finance

**What:** Write `member_display_name` at attendance-row and charge creation;
exclude `active=false` members from monthly charge generation.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/attendance/`,
finance application/JDBC packages, related unit and integration tests.

**Depends on:** T03

**Reuses:** `RespondAttendance`, existing charge-generation member selection.

**Requirement:** ATH-05

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] New attendance rows and charges store the current display name at creation; later status updates never rewrite it.
- [ ] Renaming a user changes only future snapshots.
- [ ] Monthly generation selects only `active=true` members and reports how many inactive members were excluded.
- [ ] Existing attendance/finance behavior is regression-covered (charge idempotency, waitlist promotion charge).
- [ ] At least 10 new tests pass with zero failures/skips.
- [ ] Backend data gate passes.

**Tests:** Kotlin unit + PostgreSQL integration
**Gate:** Backend data
**Commit:** `feat(groups): snapshot athlete names and respect active flag`

---

### T07: Expose Athlete HTTP Routes

**What:** Add `AthleteController` (roster GET, organizer PATCH/DELETE, athlete
`me` PATCH) with Bruno contracts.

**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/input/http/AthleteController.kt`,
bootstrap integration tests, Bruno files.

**Depends on:** T05, T06

**Reuses:** Existing authenticated principal, privacy problem mapping,
membership endpoint test fixtures.

**Requirement:** ATH-02, ATH-03, ATH-04, ATH-06

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] All four routes enforce their exact role scopes; non-members receive privacy `404`.
- [ ] Query filters map 1:1 to the read model; malformed payloads return stable field problems.
- [ ] Phone appears only in organizer roster responses; enum names never leak as display strings.
- [ ] Bruno contracts exist for every route and `check-bruno` passes.
- [ ] At least 12 new integration tests pass with zero failures/skips.
- [ ] Backend full gate passes.

**Tests:** Spring integration
**Gate:** Backend full + API contract
**Commit:** `feat(groups): expose athlete management API`

---

### T08: Add Mobile Session and Athlete Gateways

**What:** Extend the mobile session DTO with `phone`/`phoneRequired`, add the
profile-completion request, and add athlete DTOs/gateway (roster, self patch,
organizer patch, delete).

**Where:** `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/data/`,
`mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/athlete/`,
common tests.

**Depends on:** T07

**Reuses:** Existing authenticated Ktor client, `NetworkResult`, stable problem
mapping.

**Requirement:** ATH-01, ATH-02, ATH-03, ATH-04, ATH-06

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] DTOs mirror the exact backend contracts (method/path/body/response) with MockEngine tests.
- [ ] Error mapping covers validation, forbidden, privacy `404`, and retryable infrastructure failures.
- [ ] No backend domain classes are imported (AD-005).
- [ ] At least 12 new tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit
**Gate:** Mobile common
**Commit:** `feat(mobile): add session profile and athlete gateways`

---

### T09: Extend Completion Flow with Mandatory Phone

**What:** Extend the landed `namecompletion` route into profile completion:
phone always required with BR mask, name conditional, gate sequencing before
deferred-invite resumption.

**Where:** `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/namecompletion/`,
`.../ui/`, coordinator sequencing, common tests.

**Depends on:** T08 (and the in-flight namecompletion refactor landed)

**Reuses:** Existing MVI Root/Screen split, `SavedStateHandle` restoration,
`DeferredInviteCoordinator` sequencing, `AccessValidators`.

**Requirement:** ATH-01

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] `phoneRequired=true` (or missing name) routes to completion before any group navigation; success clears the gate without re-prompt.
- [ ] Phone field applies a BR mask, validates locally for UX, and surfaces backend field errors inline.
- [ ] A pending invite capability survives the gate untouched and resumes after completion.
- [ ] Draft input survives rotation/process death via `SavedStateHandle`.
- [ ] pt-BR copy from Compose resources; accessibility semantics on all fields.
- [ ] At least 12 new tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit + Compose UI
**Gate:** Mobile common
**Commit:** `feat(access): require phone in profile completion`

---

### T10: Add Post-Redeem Position Onboarding Step

**What:** One skippable position-choice screen after successful invite
redemption and group selection.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/athlete/onboarding/`,
`.../ui/`, common tests.

**Depends on:** T08

**Reuses:** Redeem success navigation flow, existing selection controls
(`SaqzSegmentedButtons`-style), MVI conventions.

**Requirement:** ATH-02

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Step shows the five pt-BR position labels, persists a choice via the self route, and skip navigates on without a request.
- [ ] Re-redeem by an existing member never shows the step.
- [ ] Request failure is retryable in place; skip remains available.
- [ ] At least 8 new tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit + Compose UI
**Gate:** Mobile common
**Commit:** `feat(groups): add athlete position onboarding step`

---

### T11: Implement Roster ViewModel and State

**What:** Roster MVI state machine: load, search, combinable filters, edit
sheet state, removal confirmation, financial badges, role guards.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/athlete/roster/`,
common tests.

**Depends on:** T08

**Reuses:** `GroupRoutePolicy` guards, `GroupAdministrationCoordinator`
patterns, MVI State/Action/Effect conventions.

**Requirement:** ATH-03, ATH-04

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Search/filters combine with AND and reflect backend semantics; state restores through `SavedStateHandle`.
- [ ] Edit intents update one athlete; removal requires explicit confirmation and reports the history-preserved consequence.
- [ ] `DESCONHECIDO` financial state renders distinctly; roster loads even when badges are unknown.
- [ ] `ATHLETE` role exposes no management intents.
- [ ] At least 14 new tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit
**Gate:** Mobile common
**Commit:** `feat(groups): add athlete roster state machine`

---

### T12: Build Roster and Edit Screens

**What:** Compose roster screen (list, search, filter chips, badges) plus the
organizer edit bottom sheet and removal dialog.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/athlete/`,
common UI tests.

**Depends on:** T11

**Reuses:** Existing design-system components and theme tokens;
`MembershipInviteScreens` list patterns.

**Requirement:** ATH-03, ATH-04

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] pt-BR labels only (`Mensalista`, `Avulso`, `Líbero`, `Pago`, `Pendente`); enum names never render.
- [ ] Management controls render only for `OWNER`/`ADMIN`; athlete viewers see none.
- [ ] Compact viewport, keyboard, and max text scale keep every action reachable with ≥48 dp targets; list items scroll by semantic index (B46 precedent).
- [ ] Removal dialog names the consequence and requires explicit confirmation.
- [ ] At least 12 new Compose tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** Compose UI
**Gate:** Mobile common
**Commit:** `feat(groups): build athlete roster screens`

---

### T13: Build Athlete Own Profile

**What:** Athlete profile surface: account data (name, email, phone) and
per-group cards (type, position, active) with per-group history entries.

**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/athlete/profile/`,
`.../ui/athlete/`, common tests.

**Depends on:** T11

**Reuses:** Existing profile/history read endpoints and screen patterns.

**Requirement:** ATH-06

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] Multiple groups render independent attribute cards; own position is editable from here via the self route.
- [ ] History is grouped by group and shows only the caller's own data.
- [ ] At least 8 new tests pass with zero failures/skips.
- [ ] Mobile common gate passes.

**Tests:** KMP unit + Compose UI
**Gate:** Mobile common
**Commit:** `feat(groups): add athlete own profile`

---

### T14: Wire Navigation, DI, and Cross-Feature Journeys

**What:** Register routes/ViewModels in Koin and the nav graph; end-to-end
journey coverage: signup → completion → invite redeem → onboarding → roster.

**Where:** `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/`,
navigation/DI modules, journey tests, native journey suites.

**Depends on:** T09, T10, T12, T13

**Reuses:** `AuthenticatedAccessRoot`, `ComposePresentationModule`, existing
Android/iOS journey fixtures (V51 session-isolation rule).

**Requirement:** ATH-01, ATH-02, ATH-03

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] All new routes are reachable with role-correct entry points; deep-link invite ordering with the completion gate is journey-tested.
- [ ] Existing users without phone hit the gate once on next entry and never again after completion.
- [ ] Android and iOS journey suites pass with the new flows registered in their test targets (V42 precedent).
- [ ] At least 8 new tests pass with zero failures/skips.
- [ ] Mobile common + Android native + iOS native gates pass.

**Tests:** KMP unit + native journeys
**Gate:** Mobile common, Android native, iOS native
**Commit:** `feat(mobile): wire athlete management flows`

---

### T15: Final Aggregate Gates and Validation

**What:** Run every aggregate gate, update scope/inventory sensors missed
earlier, and record validation results.

**Where:** repository scripts, `.specs/features/athlete-management/validation.md`.

**Depends on:** T14

**Reuses:** `check-all` aggregate; validation.md convention from prior
features.

**Requirement:** All (ATH-01..06)

**Tools:** MCP: NONE. Skill: `tlc-spec-driven`.

**Done when:**

- [ ] `check-credentials`, `check-scope`, `check-bruno`, `check-gradle`, `check-ios`, and `check-all` pass.
- [ ] No phone number appears in any log assertion, fixture output, or committed artifact.
- [ ] `validation.md` records gate evidence and any regression backprop entries.
- [ ] Spec traceability table updated to `In Tasks`/`Done` states.

**Tests:** Aggregate gates
**Gate:** Complete
**Commit:** `chore(spec): validate athlete management delivery`
