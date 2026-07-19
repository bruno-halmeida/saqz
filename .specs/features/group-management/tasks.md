# Group Management Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name
and follow its Execute flow and Critical Rules.** Do not search for skill files
by filesystem path. The skill is the source of truth for the per-task cycle,
batch delegation, adequacy review, Verifier, and discrimination sensor.

If the skill cannot be activated, stop and tell the user. Every shell command
must be prefixed with `rtk`. Work on exactly one task at a time, derive tests
from the cited acceptance criteria, run the task gate, and make one Conventional
Commit containing only that task. Never weaken, skip, or delete a test.

After T67, dispatch an independent Verifier automatically. The Verifier must
perform the spec-anchored outcome check and discrimination sensor, then write
`validation.md`. It does not reuse the implementing agent's conclusions.

**Test-count notation:** `Δ+N` means the task must add at least `N` discovered
test cases relative to the last completed task. `Δ0` means refactor/move work
must retain the complete affected test count. Every task records before/after
counts in its commit evidence; no suite may silently decrease.

---

**Spec:** `.specs/features/group-management/spec.md`
**Context:** `.specs/features/group-management/context.md`
**Design:** `.specs/features/group-management/design.md`
**Status:** Approved

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec — confirm before
> Execute. Guidelines found: `AGENTS.md` (acceptance-derived tests, mandatory
> gates, no weakening), `README.md` (workspace commands), `scripts/check-gradle`,
> `scripts/check-ios`, `scripts/check-bruno`, and
> `.github/workflows/initialization-gate.yml`. Existing samples establish style
> and location only; the spec's strong coverage requirements remain the target.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Backend domain/application | unit | All branches; 1:1 AC mapping; every enum, conditional rule, permission, transition, idempotency, and listed edge case | `backend/features/groups/src/test/**/*.kt` | `rtk backend/gradlew -p backend :features:groups:test --console=plain` |
| Backend JDBC/migrations | PostgreSQL integration | Upgrade from V1; every constraint/key query/error path; transaction rollback; deterministic concurrent locking tests | `backend/features/groups/src/integrationTest/**/*.kt` | `rtk backend/gradlew -p backend :features:groups:integrationTest --console=plain` |
| Backend HTTP/composition | Spring integration | Every route: happy, validation, role, privacy-equivalent 404, auth, conflict, retry, malformed body, and redaction paths | `backend/bootstrap/src/test/**/*.kt` | `rtk backend/gradlew -p backend :bootstrap:test --console=plain` |
| Shared-kernel/module boundaries | unit/architecture | Provider-neutral contracts, exact module inventory, no feature-to-feature dependency/import, clean hexagonal layers | `backend/shared-kernel/src/test/**/*.kt`, `backend/architecture-tests/src/test/**/*.kt` | `rtk backend/gradlew -p backend :shared-kernel:check :architecture-tests:test --console=plain` |
| Bruno route contracts | contract | Every explicit backend route has a method/path-matching request with assertions and no duplicated secrets | `bruno/**/*.bru`, `tests/scripts/check-bruno.test.sh` | `rtk scripts/check-bruno` |
| KMP network/gateway/state | common unit | Exact serialization, normalized failures, stable idempotency, all state/intent/effect branches, restart restoration | `mobile/core/network/src/commonTest/**/*.kt`, `mobile/features/groups/src/commonTest/**/*.kt` | `rtk mobile/gradlew -p mobile :core:network:allTests :features:groups:allTests --console=plain` |
| Compose feature UI | common Compose UI | Every action/state; compact viewport, keyboard, maximal text, semantic order, 48 dp targets, role-based visibility | `mobile/features/groups/src/commonTest/**/*.kt` | `rtk mobile/gradlew -p mobile :features:groups:allTests --console=plain` |
| Compose app/navigation | common unit/Compose UI | Route lifecycle, selected-group reconciliation, one-shot effects, no Access/Groups state leakage | `mobile/compose-app/src/commonTest/**/*.kt` | `rtk mobile/gradlew -p mobile :compose-app:allTests --console=plain` |
| Android native adapters | JVM + instrumented | Success/cancel/denial/recovery, app-private storage, cold/warm/deferred links, permissions, lifecycle and accessibility | `mobile/android-app/src/test/**/*.kt`, `mobile/android-app/src/androidTest/**/*.kt` | `rtk mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain` |
| iOS native adapters | XCTest + XCUITest | Success/cancel/denial/recovery, app-private storage, cold/warm/deferred links, lifecycle, Dynamic Type and accessibility | `mobile/ios-app/SaqzIOSTests/**/*.swift`, `mobile/ios-app/SaqzIOSUITests/**/*.swift` | `rtk scripts/check-ios` |
| Build/config/docs/scripts | script/build | Exact inventories, zero-test detection, credentials/scope/workspace isolation, accurate commands and no stale ownership claims | `tests/scripts/**/*.sh`, build/settings files, `README.md` | `rtk scripts/test-scripts` |

## Gate Check Commands

> Generated from codebase — confirm before Execute. A task names one or more of
> these gates; every command listed for that gate must exit zero.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Safety | Any tracked-file or contract change | `rtk scripts/check-credentials && rtk scripts/check-scope` |
| Backend boundary | Shared-kernel/module/architecture work | `rtk backend/gradlew -p backend :shared-kernel:check :architecture-tests:test --console=plain` |
| Backend quick | Groups domain/application work | `rtk backend/gradlew -p backend :features:groups:test --console=plain` |
| Backend full | Groups JDBC/migration work | `rtk backend/gradlew -p backend :features:groups:test :features:groups:integrationTest --console=plain` |
| Backend HTTP | Controller/composition work | `rtk backend/gradlew -p backend :features:groups:test :features:groups:integrationTest :bootstrap:test :architecture-tests:test --console=plain && rtk scripts/check-bruno` |
| Network quick | Core Ktor work | `rtk mobile/gradlew -p mobile :core:network:allTests --console=plain` |
| Mobile quick | Groups KMP/Compose work | `rtk mobile/gradlew -p mobile :features:groups:compileAndroidMain :features:groups:allTests --console=plain` |
| Mobile integration | Compose app/navigation work | `rtk mobile/gradlew -p mobile :features:access:allTests :features:groups:allTests :compose-app:allTests --console=plain` |
| Android native | Android adapter/lifecycle work | `rtk mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain` |
| iOS native | iOS adapter/lifecycle work | `rtk scripts/check-ios` |
| Script contract | Gate/readme/inventory work | `rtk scripts/test-scripts` |
| Gradle aggregate | Completed backend/mobile phase | `rtk scripts/check-gradle` |
| Complete aggregate | Final implementation task | `rtk scripts/check-all` |

## Tools and Skills

Recommended execution defaults for every task:

- Local tools: `apply_patch`, `rtk`, Gradle, Docker/Testcontainers, Android
  emulator/ADB, Xcode/simulator, and repository scripts as named by the task.
- MCPs: none required. External research is allowed only when the codebase and
  project docs do not answer an implementation question; use official primary
  documentation.
- Skills: `tlc-spec-driven` for Execute; `backprop` immediately after any test
  or gate failure to decide whether a new invariant is needed.
- Git: one task, one Conventional Commit; stage only task files and preserve all
  unrelated worktree changes.

## Execution Plan

Phases and tasks are strictly sequential. No phase begins until the preceding
phase's last gate and commit are complete.

### Phase 1: Backend Groups boundary and compatibility

```text
T01 → T02 → T03
```

### Phase 2: Mobile Groups boundary and invitation journey

```text
T03 → T08 → T09 → T13
```

### Phase 3: Group profile, defaults, and registration

```text
T13 → T14 → T15 → T16 → T17 → T18 → T19 → T20 → T21 → T22
```

### Phase 4: Private group photo

```text
T22 → T23 → T24 → T25 → T26 → T27 → T28 → T29
```

### Phase 5: Games and recurrence backend

```text
T29 → T30 → T31 → T32 → T33 → T34 → T35 → T36
```

### Phase 6: Games mobile experience

```text
T36 → T37 → T38 → T39 → T40 → T41 → T42
```

### Phase 7: Manual finance backend

```text
T42 → T43 → T44 → T45 → T46 → T47 → T48
```

### Phase 8: Attendance and waitlist backend

```text
T48 → T49 → T50 → T51 → T52 → T53 → T54
```

### Phase 9: Attendance and finance mobile experience

```text
T54 → T55 → T56 → T57 → T58 → T59 → T60 → T61 → T62
```

### Phase 10: Native drafts, app integration, and aggregate gate

```text
T62 → T63 → T64 → T65 → T66 → T67
```

## Task Breakdown

### T01: Define provider-neutral actor and membership-summary contracts

**What:** Add the two shared-kernel integration ports that let Access and Groups
collaborate without feature imports.
**Where:** `backend/shared-kernel/src/main/kotlin/br/com/saqz/sharedkernel/actor/`,
`backend/shared-kernel/src/main/kotlin/br/com/saqz/sharedkernel/group/`, matching
shared-kernel tests.
**Depends on:** None.
**Reuses:** `RequestIdentity`, provider-neutral conventions from AD-022.
**Requirement:** `GRP-REG-04`, `GRP-REGRESSION-01`, `INVITE-03`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] `AuthenticatedActorResolver` resolves a request identity to a stable
  internal actor without exposing Access types.
- [ ] `GroupMembershipSummaryReader` returns only bootstrap-safe group summary
  values and no Groups domain class.
- [ ] Shared-kernel remains framework/provider/feature neutral.
- [ ] Backend boundary gate passes; test count `Δ+4` or greater.

**Tests:** unit (`Δ+4`).
**Gate:** Backend boundary + Safety.
**Commit:** `feat(groups): define access integration contracts`
**Status:** Complete (`49a4730`); required boundary and safety gates passed.

### T02: Scaffold the backend Groups module and enforce its boundary

**What:** Add `:features:groups` as an independently testable backend feature
with the approved hexagonal package and Gradle boundaries.
**Where:** `backend/settings.gradle.kts`, `backend/features/groups/`,
`backend/architecture-tests/src/test/kotlin/br/com/saqz/architecture/BackendArchitectureTest.kt`.
**Depends on:** T01.
**Reuses:** `features/access/build.gradle.kts`, backend convention plugin,
existing architecture inventory helpers.
**Requirement:** `GRP-REGRESSION-01`; AD-003, AD-019, AD-022, AD-026.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Groups depends on shared-kernel and never on Access, Identity, or
  bootstrap.
- [ ] Domain/application packages reject Spring, JDBC, adapter, and other
  feature imports.
- [ ] Architecture inventory expects exactly Access, Groups, and Identity.
- [ ] Groups has unit and PostgreSQL integration source sets with non-zero-test
  protection ready for subsequent tasks.
- [ ] Backend boundary gate passes; test count `Δ+4` or greater.

**Tests:** architecture/build (`Δ+4`).
**Gate:** Backend boundary + Safety.
**Commit:** `build(groups): add backend feature boundary`
**Status:** Complete (`024d689`); required boundary and safety gates passed.

### T03: Migrate complete backend group ownership to Groups

**What:** Atomically transfer the existing group authorization, value objects,
create/read/settings, membership administration, invitation lifecycle, HTTP
controllers, and bootstrap composition from Access to Groups. These pieces share
the same domain types and cannot cross the enforced Access → Groups boundary in
intermediate commits.
**Where:** Access group-owned domain/application/JDBC/HTTP code and matching
tests; `backend/features/groups/src/main/kotlin/br/com/saqz/groups/`; bootstrap
composition and HTTP tests.
**Depends on:** T02.
**Reuses:** `GroupRole`, `GroupAccessPolicy`, `AccessName`, `IanaTimeZone`,
`CreateGroup`, `GetGroup`, `UpdateGroupSettings`, membership/invite use cases,
existing V1 schema, `BootstrapSession`, and `AccessSessionConfiguration`.
**Requirement:** `GRP-REG-02..04`, `GRP-DEFAULT-04`, `GRP-PRIVATE-01`,
`GRP-REGRESSION-01`, `INVITE-01..04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Roles, authorization, group values, IDs, table names, creation keys,
  ETags, endpoint paths, statuses, and wire values remain compatible.
- [ ] Groups owns every group-scoped domain decision, use case, repository
  writer, controller, and invite operation; Access owns only identity, account,
  session, and selected-membership reconciliation.
- [ ] Owner representation, privacy-equivalent non-member behavior, invite
  rotation/expiry/digest/rate limiting, redemption idempotency, and stronger
  role preservation remain intact.
- [ ] Session bootstrap consumes Groups summaries only through shared ports;
  V1 stays unchanged and a migrated V1 database passes group, membership,
  invitation, session, and rollback coverage.
- [ ] Backend HTTP, architecture, Bruno, and safety gates pass with no affected
  suite count decrease and at least four new composition compatibility cases.

**Tests:** moved unit + PostgreSQL integration + Spring HTTP/compatibility
(`Δ0` moved suites; `Δ+4` composition cases).
**Gate:** Backend HTTP + Backend boundary + Safety.
**Commit:** `refactor(groups): migrate backend group ownership`
**Status:** Complete (`dfd35d8`); Groups unit/integration, bootstrap,
architecture, Bruno, credential, and scope gates passed with the active Colima
socket supplied to Testcontainers.

### T08: Scaffold the mobile Groups feature boundary

**What:** Add `:features:groups` as a KMP Compose feature and expose it through
the single `SaqzMobile` framework without coupling it to Access.
**Where:** `mobile/settings.gradle.kts`, `mobile/features/groups/`,
`mobile/compose-app/build.gradle.kts`, module inventory tests.
**Depends on:** T03.
**Reuses:** `features/access` KMP build conventions, AD-001/013/018/025.
**Requirement:** `GRP-UI-01`, `GRP-REGRESSION-01`; AD-026.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Groups depends only on approved core modules and never on Access.
- [ ] Compose app consumes/exports Groups through the one umbrella framework.
- [ ] Resources generate under `br.com.saqz.groups.resources`.
- [ ] A non-placeholder module contract test is discovered.
- [ ] Mobile quick gate passes; test count `Δ+1` or greater.

**Tests:** build/module unit (`Δ+1`).
**Gate:** Mobile quick + Safety.
**Commit:** `build(groups): add mobile feature boundary`
**Status:** Complete (`463dc86`); required mobile quick and safety gates passed.

### T09: Migrate complete mobile group and invitation ownership to Groups

**What:** Atomically transfer group transport, selection/administration state,
membership/invitation transport and behavior, people/invite UI/resources, and
group-specific share/pending-code ports, route ViewModels/composition, and
Android/iOS invite adapters to Groups. These pieces share Access resources,
bootstrap UI, and native ports, so they cannot cross the enforced Groups →
Access boundary in intermediate commits.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/{data,presentation}/`,
`ui/`, `port/`, `composeResources/`, matching common tests; `compose-app`
navigation/composition; Android/iOS invitation adapters and tests. Remove
transferred Access files and retain authentication-only ports in Access.
**Depends on:** T08.
**Reuses:** `GroupApi`, `RolesInvitesApi`, selection/administration and deferred
invite coordinators, `AuthenticatedNetworkClient`, route lifecycle factories,
Android/iOS Branch adapters, and native share behavior.
**Requirement:** `GRP-REG-02..04`, `GRP-DEFAULT-04`, `GRP-PRIVATE-01`,
`GRP-REGRESSION-01`, `INVITE-01..04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Exact current group/membership/invite request/response/ETag serialization
  is retained; selection restores, reconciles, retries, and clears exactly as
  before without an Access implementation import.
- [ ] Membership roles, invite management, share effects, pending opaque-code
  states, terminal errors, and retry states remain exact; only invite codes are
  persisted/passed from native links.
- [ ] Access contains no group state, people, invitation, or presentation code;
  authentication-only ports remain there.
- [ ] Groups route ViewModels own group context/people/invite commands;
  Access route owns only auth/session/logout, and one-shot effects do not replay.
- [ ] Cold, warm, Universal/App Link, and deferred Branch callbacks emit only
  validated opaque codes once; unrelated/PII parameters remain ignored.
- [ ] Existing gateway, coordinator, Compose, Android JVM, and XCTest coverage
  moves without a count decrease; mobile integration, Android native, iOS
  native, and safety gates pass.

**Tests:** KMP gateway/state + Compose UI + app integration + Android JVM + XCTest
(`Δ0` moved suites; `Δ+8` route cases).
**Gate:** Mobile integration + Android native + iOS native + Safety.
**Commit:** `refactor(groups): migrate complete mobile group ownership`
**Status:** Complete (`94ab9e8`); mobile integration, Android native, iOS
native, and safety gates passed. Backprop `B3/V3` records the iOS generated
Swift protocol-label failure found during this gate.

### T13: Prove the complete invitation and deep-link journey

**What:** Add cross-layer journey coverage from link management/share through
authentication waiting, redemption, group selection, and all terminal/retry
outcomes.
**Where:** `backend/bootstrap/src/test/`,
`mobile/features/groups/src/commonTest/`, `mobile/compose-app/src/commonTest/`,
Android/iOS native lifecycle tests, existing invitation Bruno requests.
**Depends on:** T09.
**Reuses:** existing invite endpoint, deferred state-machine, Branch adapters,
selected-group store and auth/session fakes.
**Requirement:** `INVITE-01..04`, `GRP-PRIVATE-01`,
`GRP-REGRESSION-01`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Owner/admin generate/share/rotate/expire one opaque link; link assertions
  reject group/user/PII fields.
- [ ] Cold, warm, restart-before-login, already-authenticated, and
  install-deferred journeys deliver/redeem/select exactly once.
- [ ] Existing roles are never downgraded; duplicate deliveries/retries create
  no membership or selection duplicate.
- [ ] Invalid/expired/rotated codes clear terminal state with generic `404`;
  temporary errors retain it; ten invalid attempts return `429`/`Retry-After`.
- [ ] Backend HTTP, Mobile integration, Android native, and iOS native gates
  pass; test count `Δ+12` or greater.

**Tests:** cross-layer HTTP/KMP/native journey (`Δ+12`).
**Gate:** Backend HTTP + Mobile integration + Android native + iOS native + Safety.
**Commit:** `test(groups): cover invitation deep link journey`

### T14: Add the profile/default/venue/slot migration

**What:** Add the first immutable Groups migration for complete group profile,
game/finance defaults, reusable venue, regular slots, and legacy incomplete
compatibility.
**Where:** `backend/features/groups/src/main/resources/db/migration/V2__expand_group_profile.sql`,
Groups schema integration tests.
**Depends on:** T13.
**Reuses:** unchanged `V1__create_access_schema.sql`, Flyway classpath scanning,
PostgreSQL constraints from AD-019.
**Requirement:** `GRP-REG-02..04`, `GRP-DEFAULT-01..03`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] V2 additively models every specified profile/default/venue/slot field,
  fixed PRIVATE/BRL values, versions, timestamps, limits, and conditional checks.
- [ ] Modality/composition remain nullable only for V1 legacy rows; no migration
  guesses values.
- [ ] Fresh V1→V2 and already-populated V1 upgrades preserve every ID, owner,
  membership, invite, and creation key.
- [ ] New invalid direct SQL writes fail at the appropriate constraint.
- [ ] Backend full gate passes; test count `Δ+14` or greater.

**Tests:** PostgreSQL migration/constraint integration (`Δ+14`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): migrate group profile defaults`

### T15: Implement group profile and default domain validation

**What:** Define immutable domain values for every registration/profile/default,
venue, and regular-slot field with exact normalization and conditional cleanup.
**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/domain/group/`,
matching unit tests.
**Depends on:** T14.
**Reuses:** existing value-object factory style, `IanaTimeZone`, server clock.
**Requirement:** `GRP-REG-01`, `GRP-REG-05`, `GRP-DEFAULT-01..03`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] All enum values, text ranges/control rejection, cent/day/capacity/lead/
  duration limits, and blank-to-null rules match the spec exactly.
- [ ] Non-court modality rejects style payloads and atomically clears stored
  style; presets reject/clear contradictory custom labels.
- [ ] Venue/monthly/default-slot conditional requirements report stable field
  identities together, before any transaction.
- [ ] Backend quick gate passes; test count `Δ+24` or greater.

**Tests:** domain unit (`Δ+24`).
**Gate:** Backend quick + Safety.
**Commit:** `feat(groups): validate group profile defaults`

### T16: Extend group registration as one idempotent transaction

**What:** Make `CreateGroup` atomically persist required profile, scalar
defaults, optional venue, regular slots, and sole owner under the creation key.
**Where:** Groups group-create application ports/use case, JDBC adapter, unit
and integration tests.
**Depends on:** T15.
**Reuses:** existing `(owner_user_id, creation_key)` uniqueness,
`JdbcTransactionRunner`, current retry semantics.
**Requirement:** `GRP-REG-01..04`, `GRP-DEFAULT-01..02`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Validation completes before transaction/write and returns all stable
  field errors.
- [ ] Success commits exactly one group/owner/default set/optional venue/slots,
  selects no game-related table, and returns OWNER/version/profile status.
- [ ] Same-key sequential and concurrent retries return the original aggregate
  even if retry payload differs.
- [ ] Any injected child-write failure rolls back the whole aggregate.
- [ ] Backend full gate passes; test count `Δ+14` or greater.

**Tests:** unit + PostgreSQL integration (`Δ+14`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): create complete group aggregate`

### T17: Implement privacy-safe group read projections

**What:** Return complete non-financial group profile/defaults to members and
finance defaults only to organizers, with derived legacy profile status.
**Where:** Groups group-read application/repository/mapper code and tests.
**Depends on:** T16.
**Reuses:** current group role synthesis and privacy-preserving `GroupNotFound`
outcome.
**Requirement:** `GRP-REG-04`, `GRP-DEFAULT-04`, `GRP-PRIVATE-01`,
`FIN-05`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] OWNER/ADMIN/ATHLETE projections expose exactly their allowed fields.
- [ ] Legacy rows missing modality/composition read as `INCOMPLETE`; complete
  new rows read as `COMPLETE`.
- [ ] Non-member and unknown group are indistinguishable and load no photo
  bytes or finance/expense detail.
- [ ] Backend full gate passes; test count `Δ+10` or greater.

**Tests:** unit + PostgreSQL integration (`Δ+10`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): read private group profile`

### T18: Implement versioned group profile/default updates

**What:** Add organizer-only optimistic updates for profile, defaults, venue,
and slots that never mutate existing operational history.
**Where:** Groups update application/JDBC code and tests.
**Depends on:** T17.
**Reuses:** existing ETag/version update pattern, domain conditional cleanup,
transaction runner.
**Requirement:** `GRP-DEFAULT-01..04`, `GAME-01`, `FIN-03`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] OWNER/ADMIN update; ATHLETE gets `403`; non-member/unknown gets identical
  `404`; stale/missing version never mutates.
- [ ] Modality/preset changes clear obsolete values in the same transaction.
- [ ] Venue/slot replacement is atomic and preserves valid stable IDs where
  submitted.
- [ ] Before/after fixtures prove all pre-existing series/game/attendance/
  charge/expense rows are semantically unchanged.
- [ ] Backend full gate passes; test count `Δ+14` or greater.

**Tests:** unit + PostgreSQL integration (`Δ+14`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): update group profile defaults`

### T19: Expose complete group registration/read/update HTTP contracts

**What:** Replace thin group DTOs with exact nested profile/default contracts,
field errors, privacy mapping, ETags, and complete Bruno examples.
**Where:** Groups group HTTP adapters/DTOs, bootstrap tests,
`bruno/Groups/04 Create Group.bru`, `05 Read Group.bru`,
`06 Update Group Settings.bru` (rename only if route contract requires).
**Depends on:** T18.
**Reuses:** `ApiProblem`, correlation/redaction handler, authenticated actor
resolver, existing endpoint paths.
**Requirement:** `GRP-REG-01..05`, `GRP-DEFAULT-01..04`,
`GRP-PRIVATE-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] JSON accepts/returns every required, optional, conditional, system, and
  role-filtered field with stable enum names and integer cents.
- [ ] Invalid fields return exact field paths; extra owner/role/system fields
  cannot override server authority.
- [ ] Create returns equivalent success for replay; reads/updates emit quoted
  ETags and require `If-Match` as designed.
- [ ] Every route has Bruno status/body assertions and no secret variable.
- [ ] Backend HTTP gate passes; test count `Δ+18` or greater.

**Tests:** Spring HTTP + Bruno contract (`Δ+18`).
**Gate:** Backend HTTP + Safety.
**Commit:** `feat(groups): expose complete group profile api`

### T20: Add mobile group profile DTOs, gateway, timezone, and draft contracts

**What:** Model the complete API in KMP and define provider-neutral system
timezone plus versioned non-sensitive draft persistence ports.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/{data,domain,port}/`,
matching common tests.
**Depends on:** T19.
**Reuses:** `AuthenticatedNetworkClient`, `kotlinx-datetime`, currency/date
formatters, current selected-group local adapter pattern.
**Requirement:** `GRP-REG-01..05`, `GRP-DEFAULT-01..04`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] DTO serialization covers exact enums/nested values/null cleanup, ETags,
  field problems, and no raw object key/public URL.
- [ ] System timezone returns a valid zone or a typed failure; no raw timezone
  text field exists in the normal form contract.
- [ ] Draft schema stores form values, group/version and stable command key but
  excludes bearer tokens, invite codes, and photo bytes.
- [ ] Mobile quick gate passes; test count `Δ+14` or greater.

**Tests:** KMP gateway/domain/port unit (`Δ+14`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): model group setup transport`

### T21: Implement GroupSetupViewModel

**What:** Build the typed state machine for create/edit, conditional fields,
timezone fallback, draft restore, stable submit key, conflict reload, and
post-create photo retry.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/setup/`,
matching tests.
**Depends on:** T20.
**Reuses:** AD-025 ViewModel contract, current stable request-ID/single-flight
patterns, group gateway and draft ports.
**Requirement:** `GRP-REG-01..05`, `GRP-DEFAULT-01..04`,
`GRP-PHOTO-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] One immutable state exposes all sections, conditional visibility, local
  validation, loading/conflict/error/success and friendly timezone fallback.
- [ ] Modality/preset intents immediately clear obsolete style/custom values.
- [ ] Invalid submit makes no request; duplicate/restart retry uses one command
  key; success selects and opens the returned group without creating a game.
- [ ] Draft is cleared only for its confirmed successful command.
- [ ] Mobile quick gate passes; test count `Δ+18` or greater.

**Tests:** KMP ViewModel/state-machine unit (`Δ+18`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): coordinate group setup`

### T22: Build the accessible group registration/profile/default screen

**What:** Replace the name/raw-timezone screen with one scrollable three-section
Compose flow including venue/slot editors, BRL inputs, profile completion, and
role-correct edit/read states.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/setup/`,
Compose resources and tests.
**Depends on:** T21.
**Reuses:** Saqz design-system inputs/buttons/cards/dialogs, pt-BR formatters,
keyboard-safe layouts and semantic tags.
**Requirement:** `GRP-REG-01`, `GRP-REG-05`, `GRP-DEFAULT-01..04`,
`GRP-UI-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Name/modality/composition are the only unconditional user-required
  fields; every optional/conditional field is discoverable with exact labels.
- [ ] Court style/custom fields, venue name/address, slot weekday/time/duration,
  monthly due day, and BRL cent conversion behave exactly as specified.
- [ ] Friendly timezone selector appears only on detection failure and never
  exposes enum/cents/IANA identifiers in normal presentation.
- [ ] Compact/keyboard/max-text tests keep every action in semantic order with
  at least 48 dp targets.
- [ ] Mobile quick gate passes; test count `Δ+24` or greater.

**Tests:** common Compose UI (`Δ+24`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): build complete group setup ui`

### T23: Add bounded authenticated multipart/binary networking

**What:** Extend core Ktor networking with streaming multipart upload and
bounded private binary response operations under the existing refresh/error
policy.
**Where:** `mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/`,
platform source sets only if engine adaptation is necessary, matching tests.
**Depends on:** T22.
**Reuses:** `NetworkClient`, `AuthenticatedNetworkClient`, timeout/error/logging
contracts and Ktor multipart APIs.
**Requirement:** `GRP-PHOTO-01..02`, `GRP-PRIVATE-01`,
`GRP-UI-02`.

**Tools:** MCP: none; official Ktor docs only if needed. Skill:
`tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Upload streams/bounds bytes, carries content metadata/ETag, and retries
  once after token refresh without changing the logical operation.
- [ ] Binary read enforces a configured maximum before allocation and returns
  media type, ETag, and cache metadata.
- [ ] Logs contain method/path/status/duration only—never payload, token,
  filename, image metadata, or error-body overflow.
- [ ] Cancellation closes channels/resources and remains cancellation, not a
  normalized failure.
- [ ] Network quick gate passes; test count `Δ+12` or greater.

**Tests:** Ktor MockEngine common unit (`Δ+12`).
**Gate:** Network quick + Safety.
**Commit:** `feat(network): support private media transport`

### T24: Persist and validate private photos in PostgreSQL

**What:** Add V3 photo storage plus an adapter that validates actual media before
atomically replacing/removing a group's private photo and group version.
**Where:** `V3__add_group_photos.sql`, Groups media application port,
`adapter/output/media/`, `adapter/output/jdbc/photo/`, tests, version catalog.
**Depends on:** T23.
**Reuses:** PostgreSQL `bytea`, ETag/version pattern, TwelveMonkeys
`imageio-webp` 3.13.1 for WebP decoding, JDK PNG/JPEG readers.
**Requirement:** `GRP-PHOTO-01..02`, `GRP-PRIVATE-01`.

**Tools:** MCP: none; official TwelveMonkeys/Maven Central references already
recorded. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] V3 stores exactly one photo/group with bytes, actual type, size,
  dimensions, digest, version, actor, and timestamps; normal group queries never
  select bytes.
- [ ] Streaming limit rejects over 5 MiB before publication; decoded limit
  rejects dimensions over 4096×4096 and invalid/empty data.
- [ ] Declared/actual mismatch, GIF, PNG `acTL`, WebP `ANIM`/`ANMF`, corrupt and
  decompression-bomb fixtures are rejected before replacement.
- [ ] Valid JPEG/PNG/WebP replacement and idempotent removal update group/photo
  versions atomically; injected failure preserves the old photo.
- [ ] Backend full gate passes; test count `Δ+18` or greater.

**Tests:** unit + PostgreSQL/media integration (`Δ+18`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): store validated private photos`

### T25: Expose member-only photo upload/read/remove endpoints

**What:** Add the private multipart photo resource with organizer mutation,
member read, ETags, cache revalidation, safe limits, and Bruno contracts.
**Where:** Groups photo HTTP adapter/DTO, bootstrap multipart configuration and
tests, `bruno/Groups/` photo requests.
**Depends on:** T24.
**Reuses:** actor resolver, group access policy, `ApiProblem`, private media
store.
**Requirement:** `GRP-PHOTO-01..02`, `GRP-PRIVATE-01`,
`GRP-DEFAULT-04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] PUT/GET/DELETE enforce exact role/member/privacy rules and missing/stale
  `If-Match` behavior.
- [ ] GET returns actual type, private/no-cache, ETag and bytes only after
  current membership; conditional read works without a public URL/key.
- [ ] Multipart/request/decode failures map to stable safe problems and never
  replace current media or leak parser/internal details.
- [ ] Every photo route has Bruno method/path/status assertions.
- [ ] Backend HTTP gate passes; test count `Δ+16` or greater.

**Tests:** Spring multipart HTTP + Bruno (`Δ+16`).
**Gate:** Backend HTTP + Safety.
**Commit:** `feat(groups): expose private group photo api`

### T26: Implement mobile photo gateway and shared crop state

**What:** Add photo transport, provider-neutral selection/encoding ports, and a
shared square crop/preview/retry state machine without persisting source bytes.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/{data,port,presentation/photo}/`,
matching tests.
**Depends on:** T25.
**Reuses:** bounded authenticated network operations, GroupSetupViewModel
post-create state, AD-018 native-edge rule.
**Requirement:** `GRP-PHOTO-01..02`, `GRP-PRIVATE-01`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Ports expose camera/library selection, bounded preview handle, crop
  transform, encode/cancel, and cleanup without platform types in common code.
- [ ] Shared state handles choose/crop/replace/remove/upload/retry/cancel and
  keeps the existing photo on every failure.
- [ ] Upload uses the current ETag and survives group-create response loss
  without recreating the group.
- [ ] Logout/membership loss emits cache/source cleanup and no photo bytes enter
  drafts/logs.
- [ ] Mobile quick gate passes; test count `Δ+14` or greater.

**Tests:** KMP gateway/state/port unit (`Δ+14`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): coordinate private photo editing`

### T27: Implement Android camera/library and crop encoder adapters

**What:** Implement the Groups photo native ports with Android Photo Picker,
system camera capture to app-private storage, bounded decode, square encoding,
and cleanup.
**Where:** `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/groups/photo/`,
manifest only where required, JVM/instrumented tests and composition.
**Depends on:** T26.
**Reuses:** Activity-result composition pattern, Photo Picker fallback, current
activity provider, app cache/files APIs.
**Requirement:** `GRP-PHOTO-01..02`, `GRP-PRIVATE-01`,
`GRP-UI-02`.

**Tools:** MCP: none; official Android Photo Picker/camera docs if needed.
Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Library selection requests no broad storage/media permission and grants
  access only to the chosen item.
- [ ] Camera cancel/denial/failure deletes app-private temp data and returns a
  typed non-crashing outcome.
- [ ] Bounds are read before full decode; crop transform produces one static
  accepted-format square payload within limits.
- [ ] Rotation/process recreation and explicit cleanup do not duplicate results
  or leave sensitive temporary files.
- [ ] Android native gate passes; test count `Δ+10` or greater.

**Tests:** Android JVM + instrumented (`Δ+10`).
**Gate:** Android native + Safety.
**Commit:** `feat(android): add private group photo adapters`

### T28: Implement iOS camera/library and crop encoder adapters

**What:** Implement the Groups photo native ports with PhotosUI, system camera,
app-private temporary storage, bounded decode, square encoding, and cleanup.
**Where:** `mobile/ios-app/SaqzIOS/GroupsPhoto/`, app composition,
`SaqzIOSTests/` and lifecycle UI tests.
**Depends on:** T27.
**Reuses:** Swift composition root, current presentation-root provider, PhotosUI
privacy model and app container APIs.
**Requirement:** `GRP-PHOTO-01..02`, `GRP-PRIVATE-01`,
`GRP-UI-02`.

**Tools:** MCP: none; official Apple PhotosUI/camera docs if needed. Skill:
`tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Library picker exposes only selected media; camera/library cancellation
  and denial return typed outcomes without stale presentation.
- [ ] Metadata/bounds are checked before full decode and crop encoding produces
  one static accepted-format square payload within limits.
- [ ] Background/foreground, controller recreation, logout and cleanup remove
  temp/cache data without duplicate callbacks.
- [ ] iOS native gate passes; test count `Δ+10` or greater.

**Tests:** XCTest + targeted XCUITest (`Δ+10`).
**Gate:** iOS native + Safety.
**Commit:** `feat(ios): add private group photo adapters`

### T29: Add shared photo choose/crop/preview UI

**What:** Integrate camera/library choice, square crop, preview, upload progress,
replace/retry/remove, and deterministic fallback into group setup/profile UI.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/photo/`,
setup/profile integration and Compose tests.
**Depends on:** T28.
**Reuses:** shared photo state, Saqz dialog/sheet/button/progress components,
role-aware group setup screen.
**Requirement:** `GRP-PHOTO-01..02`, `GRP-DEFAULT-04`,
`GRP-UI-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Optional registration photo is clearly skippable and uploads only after
  group success; failure never returns to/repeats group creation.
- [ ] OWNER/ADMIN can choose/crop/replace/retry/remove; ATHLETE sees private
  photo/fallback with no edit action.
- [ ] Cancel/invalid/network/stale-version states keep prior media and provide
  accurate recoverable actions.
- [ ] Compact/keyboard/max-text semantics retain ordered 48 dp actions.
- [ ] Mobile quick gate passes; test count `Δ+14` or greater.

**Tests:** common Compose UI (`Δ+14`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): add private photo editor ui`

### T30: Add the games and series migration

**What:** Add V4 relational storage for series revisions/slots and immutable
game occurrence snapshots with lifecycle/version constraints.
**Where:** `backend/features/groups/src/main/resources/db/migration/V4__add_group_games.sql`,
schema integration tests.
**Depends on:** T29.
**Reuses:** group/venue/user foreign keys, PostgreSQL local date/time,
`TIMESTAMPTZ`, UUID and optimistic-version patterns.
**Requirement:** `GAME-01..04`, `GRP-DEFAULT-03`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Series stores zone/local boundaries/revision lineage; slots store stable
  key, weekday/time/duration/venue/capacity/deadline lead/fee snapshots.
- [ ] Game stores local date/time/zone, resolved start/deadline, venue snapshot,
  capacity, optional fee/notes, lifecycle, optional series identity and version.
- [ ] Unique `(series_id, local_date, slot_key)` prevents duplicate bounded
  occurrences while detached overrides remain representable.
- [ ] Invalid status, limits, fee, date/deadline and cross-group references fail
  database constraints.
- [ ] Backend full gate passes; test count `Δ+16` or greater.

**Tests:** PostgreSQL migration/constraint integration (`Δ+16`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): migrate games and series`

### T31: Implement game values, default snapshots, and lifecycle rules

**What:** Define game aggregate values and organizer commands for create,
publish, edit, cancel, and complete with immutable copied defaults.
**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/domain/game/`,
application game commands and unit tests.
**Depends on:** T30.
**Reuses:** group default values, role policy, server clock, optimistic version
outcomes.
**Requirement:** `GAME-01`, `GAME-04`, `GRP-DEFAULT-03`,
`FIN-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Title/venue/start/duration/capacity/deadline/fee/notes validation matches
  exact limits and reports all fields before persistence.
- [ ] Creating from defaults copies values; subsequent default/venue mutations
  cannot change the game snapshot.
- [ ] Only valid role/status transitions succeed; invalid/stale transitions
  produce no attendance/finance/schedule side effect.
- [ ] Draft visibility and published/cancelled/completed mutability match the
  spec.
- [ ] Backend quick gate passes; test count `Δ+18` or greater.

**Tests:** domain/application unit (`Δ+18`).
**Gate:** Backend quick + Safety.
**Commit:** `feat(groups): model game lifecycle`

### T32: Implement deterministic recurrence and bounded materialization

**What:** Resolve multi-weekday weekly rules in the group zone and materialize
an idempotent rolling 12-week horizon with explicit DST behavior.
**Where:** Groups `domain/game/recurrence/`, application materializer, unit and
integration tests.
**Depends on:** T31.
**Reuses:** Java `ZoneId`/`ZoneRules`, V4 occurrence identity, server clock.
**Requirement:** `GAME-02`, `GAME-03`, `GRP-UI-02`.

**Tools:** MCP: none; official Java/Kotlin timezone docs already recorded.
Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Ordinary times use the sole offset; gaps advance by transition duration;
  overlaps choose the earlier offset, with exact fixtures for each.
- [ ] Multiple weekdays preserve each local wall-clock schedule across offset
  changes and store resolved instants/local fields.
- [ ] Create/replenish/retry yields one bounded occurrence per identity and no
  unbounded write or duplicate.
- [ ] Empty/invalid/end-before-start rules fail before writes.
- [ ] Backend full gate passes; test count `Δ+18` or greater.

**Tests:** domain unit + PostgreSQL materialization integration (`Δ+18`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): materialize weekly recurrence`

### T33: Implement one-time game persistence and private reads

**What:** Persist/list/read/update one-time game snapshots and derive confirmed,
available, and waitlist counts without client authority.
**Where:** Groups game application/JDBC occurrence repository and tests.
**Depends on:** T32.
**Reuses:** V4 schema, group role/privacy policy, ETag transaction pattern.
**Requirement:** `GAME-01`, `GAME-04`, `GRP-PRIVATE-01`,
`ATTEND-03`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Organizer creates a DRAFT one-time game from defaults/overrides; no
  attendance or charge row is created.
- [ ] Organizers see drafts; athletes see published/history only; non-members
  and unknown resources share `404`.
- [ ] Reads derive counts server-side and `availableSpots` never becomes
  negative after capacity reduction.
- [ ] Versioned updates retain old row on stale/invalid write and preserve
  immutable snapshots.
- [ ] Backend full gate passes; test count `Δ+12` or greater.

**Tests:** unit + PostgreSQL integration (`Δ+12`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): persist game occurrences`

### T34: Implement series revision and occurrence-boundary persistence

**What:** Persist series/revisions and enforce ONLY_THIS versus
THIS_AND_FUTURE edit/cancel boundaries without rewriting history.
**Where:** Groups series application/JDBC repository and tests.
**Depends on:** T33.
**Reuses:** recurrence materializer, V4 revision lineage/occurrence uniqueness,
game lifecycle/version rules.
**Requirement:** `GAME-02..04`, `GRP-DEFAULT-03`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] ONLY_THIS detaches/overrides exactly the selected occurrence.
- [ ] THIS_AND_FUTURE closes the old revision before the boundary and creates
  one successor with regenerated future occurrences.
- [ ] Past/completed occurrences plus their snapshots/history remain unchanged;
  cancelled future rows retain stable identities/history.
- [ ] Concurrent/retried edits cannot create overlapping revisions or duplicate
  occurrences; injected failure rolls back the entire boundary change.
- [ ] Backend full gate passes; test count `Δ+16` or greater.

**Tests:** unit + PostgreSQL integration (`Δ+16`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): persist series edit boundaries`

### T35: Expose one-time game and lifecycle HTTP contracts

**What:** Add group game create/list/read/update plus publish/cancel/complete
commands with privacy, ETags, stable problems, and Bruno requests.
**Where:** Groups game HTTP adapters, bootstrap tests, `bruno/Games/`.
**Depends on:** T34.
**Reuses:** actor resolver, game use cases, `ApiProblem`, UUID idempotency keys.
**Requirement:** `GAME-01`, `GAME-04`, `GRP-PRIVATE-01`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Each route accepts/returns exact fields, derived counts/status/version and
  quoted ETags; command DTOs cannot set server-owned target state/counts.
- [ ] Happy, validation, malformed, stale, role, hidden-resource, invalid
  transition, replay and rollback outcomes have exact HTTP assertions.
- [ ] Cancellation returns finance review information without claiming refund.
- [ ] Every explicit route has a Bruno request with method/path/status/body
  assertions.
- [ ] Backend HTTP gate passes; test count `Δ+20` or greater.

**Tests:** Spring HTTP + Bruno (`Δ+20`).
**Gate:** Backend HTTP + Safety.
**Commit:** `feat(groups): expose game lifecycle api`

### T36: Expose weekly series and boundary-command HTTP contracts

**What:** Add weekly series create/read plus occurrence/this-and-future
edit/cancel commands with bounded responses and exact boundary semantics.
**Where:** Groups series HTTP adapters, bootstrap tests, `bruno/Games/Series/`.
**Depends on:** T35.
**Reuses:** series use cases, recurrence DTO values, command idempotency/ETags.
**Requirement:** `GAME-02..04`, `GRP-PRIVATE-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Multi-slot local rule/zone/boundary DTOs round-trip without client-supplied
  resolved instants.
- [ ] ONLY_THIS and THIS_AND_FUTURE are explicit enum commands and reject
  impossible/past/completed/stale boundaries without partial changes.
- [ ] Responses are bounded to the designed horizon and retries are equivalent.
- [ ] Every series route has Bruno assertions and privacy-equivalent problems.
- [ ] Backend HTTP gate passes; test count `Δ+18` or greater.

**Tests:** Spring HTTP + Bruno (`Δ+18`).
**Gate:** Backend HTTP + Safety.
**Commit:** `feat(groups): expose recurring series api`

### T37: Add mobile game/series DTOs and gateway

**What:** Model game, series, derived availability, lifecycle, and boundary
commands in KMP with exact API serialization and errors.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/game/`,
matching MockEngine tests.
**Depends on:** T36.
**Reuses:** authenticated gateway/ETag/idempotency patterns, common date/currency
formatters.
**Requirement:** `GAME-01..04`, `GRP-PRIVATE-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Every DTO/enum/nested slot and nullable fee/notes field round-trips with
  server-derived counts/instants read-only.
- [ ] Create/commands preserve stable request keys/ETags through auth refresh.
- [ ] Field, hidden-resource, conflict and lifecycle problems map to distinct
  presentation-safe outcomes.
- [ ] Mobile quick gate passes; test count `Δ+16` or greater.

**Tests:** KMP gateway unit (`Δ+16`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): model game transport`

### T38: Implement GamesViewModel

**What:** Coordinate selected-group upcoming/past game loading, refresh,
organizer create entry, and role/status-aware navigation.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/games/list/`,
matching tests.
**Depends on:** T37.
**Reuses:** AD-025, selected group route input, game gateway, one-shot navigation
effects.
**Requirement:** `GAME-01..04`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] State distinguishes loading/empty/content/error/refresh without exposing
  previous-group data during selection change.
- [ ] Organizer can open create; athlete cannot; all members can open allowed
  game detail.
- [ ] List order/time/availability/status use authoritative DTOs and pt-BR
  formatting.
- [ ] Duplicate refresh/open intents remain single-flight/non-replayed.
- [ ] Mobile quick gate passes; test count `Δ+12` or greater.

**Tests:** KMP ViewModel unit (`Δ+12`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): coordinate games list`

### T39: Build upcoming/past games screens

**What:** Add accessible list/empty/loading/error presentations for upcoming
and historical games, availability, waitlist count, status, and organizer CTA.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/games/list/`,
resources and tests.
**Depends on:** T38.
**Reuses:** Saqz state host/cards/badges/list items, shared formatters.
**Requirement:** `GAME-01..04`, `GRP-UI-01`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Cards expose title, local date/time, venue, status and derived
  spots/waitlist without enum/cents/timezone identifiers.
- [ ] Drafts/organizer CTA are hidden from athletes and no prior-group content
  flashes while loading.
- [ ] Empty/error/retry and upcoming/past navigation are semantically ordered
  with 48 dp targets under compact/max-text conditions.
- [ ] Mobile quick gate passes; test count `Δ+12` or greater.

**Tests:** common Compose UI (`Δ+12`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): build games list ui`

### T40: Implement GameEditorViewModel and restorable drafts

**What:** Coordinate one-time/weekly game forms, copied defaults, overrides,
multi-slot recurrence, idempotent create, and edit-boundary choice.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/games/editor/`,
draft schema and tests.
**Depends on:** T39.
**Reuses:** GroupSetupViewModel validation/draft/single-flight pattern, group
profile defaults, game gateway.
**Requirement:** `GAME-01..04`, `GRP-DEFAULT-03`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] New form copies current defaults once and later group/default state cannot
  rewrite the draft or existing game.
- [ ] One-time/weekly mode, one-or-more weekly slots, date range, venue,
  capacity, deadline, BRL fee and notes validate before request.
- [ ] Edit requires ONLY_THIS/THIS_AND_FUTURE choice; invalid/stale response
  preserves draft and offers authoritative reload.
- [ ] Restart/retry uses the same command key/version and cannot duplicate a
  game/series.
- [ ] Mobile quick gate passes; test count `Δ+18` or greater.

**Tests:** KMP ViewModel/draft unit (`Δ+18`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): coordinate game editor`

### T41: Build one-time/weekly game editor UI

**What:** Add accessible Compose forms for game fields, multiple recurrence
slots, defaults/overrides, and occurrence/future edit/cancel scope.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/games/editor/`,
resources and tests.
**Depends on:** T40.
**Reuses:** group venue/slot editors, date/time/BRL controls, Saqz dialogs/sheets.
**Requirement:** `GAME-01..04`, `GRP-UI-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Required game fields and optional fee/notes are discoverable, prefilled,
  editable, and display field errors without a request.
- [ ] Weekly mode supports multiple add/remove weekday/time/duration/venue
  slots and optional end date in the group-local presentation.
- [ ] Edit/cancel clearly asks `Somente este jogo` or `Este e os próximos` and
  never defaults silently.
- [ ] Compact/keyboard/max-text tests retain semantic order and 48 dp targets.
- [ ] Mobile quick gate passes; test count `Δ+18` or greater.

**Tests:** common Compose UI (`Δ+18`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): build game editor ui`

### T42: Implement game detail lifecycle route

**What:** Add the member game-detail state/screen with authoritative schedule,
availability, status and organizer publish/edit/cancel/complete actions, ready
for attendance integration.
**Where:** Groups game-detail presentation/UI packages and tests.
**Depends on:** T41.
**Reuses:** game gateway, AD-025 effects, Saqz confirmation dialogs/status
components.
**Requirement:** `GAME-01`, `GAME-04`, `GRP-PRIVATE-01`,
`GRP-UI-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Members see exact snapshot/status/derived availability; organizer actions
  appear only in valid role/status combinations.
- [ ] Publish/cancel/complete are single-flight versioned commands with confirm,
  conflict reload and non-replayed success effects.
- [ ] Cancelled/completed presentation becomes read-only and cancellation
  explains manual finance review without promising refund.
- [ ] Mobile quick gate passes; test count `Δ+14` or greater.

**Tests:** KMP ViewModel + common Compose UI (`Δ+14`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): add game detail lifecycle`

### T43: Add the manual-finance migration

**What:** Add V5 charge/status-audit and expense/audit storage with immutable
amount identities and organizer/member visibility indexes.
**Where:** `backend/features/groups/src/main/resources/db/migration/V5__add_group_finance.sql`,
schema integration tests.
**Depends on:** T42.
**Reuses:** group/game/member foreign keys, BIGINT cents, optimistic versions,
append-only event pattern.
**Requirement:** `FIN-01..07`, `GAME-04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Charges model GAME/MONTHLY identity, immutable positive BRL amount, due
  date, status, creator/changer/review/version/timestamps.
- [ ] Partial unique keys enforce one member/game and one member/group/month
  charge.
- [ ] Charge/expense event rows are append-only and expenses constrain exact
  fields/category/custom/status/version.
- [ ] Schema contains no processor/payment credential/webhook/settlement/
  partial/refund/balance/transfer field or dependency.
- [ ] Backend full gate passes; test count `Δ+18` or greater.

**Tests:** PostgreSQL migration/constraint integration (`Δ+18`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): migrate manual finance tracking`

### T44: Implement charge domain, status, and visibility rules

**What:** Define charge identities/kinds/status transitions, immutable amount,
audit event creation, and organizer versus athlete projections.
**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/domain/finance/charge/`,
application ports and tests.
**Depends on:** T43.
**Reuses:** money/default limits, role policy, server clock, optimistic outcome
style.
**Requirement:** `FIN-01..05`, `FIN-07`, `GAME-04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] PENDING/PAID/WAIVED/CANCELLED transitions reject invalid/stale/client-
  authored actor/timestamp/amount changes.
- [ ] Every successful status change emits actor, old/new state, optional note,
  and server time without overwriting prior events.
- [ ] Athlete projection contains only own charges; organizers see group charges
  and totals; non-member is privacy-hidden.
- [ ] No domain type models money movement, settlement, partials or refunds.
- [ ] Backend quick gate passes; test count `Δ+18` or greater.

**Tests:** domain/application unit (`Δ+18`).
**Gate:** Backend quick + Safety.
**Commit:** `feat(groups): model manual charges`

### T45: Implement game-charge and monthly-generation transactions

**What:** Persist idempotent pending game charges on confirmation/promotion and
organizer-reviewed monthly charges for selected active members.
**Where:** Groups finance charge use cases/JDBC adapters and tests.
**Depends on:** T44.
**Reuses:** V5 unique identities, group monthly defaults, transaction runner,
membership repository, stable command keys.
**Requirement:** `FIN-01..04`, `FIN-07`, `ATTEND-02`,
`GAME-04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Confirmed paid game creates one PENDING immutable charge; waitlisted,
  declined, no-response, free game and replay do not create extras.
- [ ] Promotion creates the same one charge; withdrawal leaves it pending;
  game cancellation cancels pending and flags paid/waived for review.
- [ ] Monthly generation validates month/amount/due date/selected active member,
  retries idempotently, and never rewrites existing amounts after default edits.
- [ ] Any injected charge/audit/member failure rolls back the command.
- [ ] Backend full gate passes; test count `Δ+18` or greater.

**Tests:** unit + PostgreSQL integration (`Δ+18`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): track game and monthly charges`

### T46: Expose charge query, generation, and status HTTP contracts

**What:** Add organizer charge list/monthly generation/status update and athlete
own-charge read endpoints with audit-safe problems and Bruno requests.
**Where:** Groups charge HTTP adapters, bootstrap tests, `bruno/Finance/Charges/`.
**Depends on:** T45.
**Reuses:** charge use cases, actor resolver, ETags/idempotency, `ApiProblem`.
**Requirement:** `FIN-01..05`, `FIN-07`, `GRP-PRIVATE-01`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Organizer and athlete routes return exactly allowed fields; athlete
  cannot infer other charges/totals/expenses.
- [ ] Monthly request selects member IDs and reviewed amount/due date under a
  stable key; retry returns equivalent charges.
- [ ] Status commands require `If-Match`, derive actor/time/old state, append
  audit, and reject amount/status fabrication or invalid transition.
- [ ] Every route has Bruno assertions and responses never claim processed or
  settled payment.
- [ ] Backend HTTP gate passes; test count `Δ+18` or greater.

**Tests:** Spring HTTP + Bruno (`Δ+18`).
**Gate:** Backend HTTP + Safety.
**Commit:** `feat(groups): expose manual charge api`

### T47: Implement expense domain, persistence, and audit

**What:** Add organizer-only expense create/edit/void use cases with exact
validation, optimistic versions, totals projection, and append-only events.
**Where:** Groups finance expense domain/application/JDBC packages and tests.
**Depends on:** T46.
**Reuses:** V5 expense tables, group-local date/zone, role policy, transaction
runner.
**Requirement:** `FIN-05..07`, `GRP-PRIVATE-01`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Description/amount/date/category/custom/notes rules match exact limits
  and contradictory custom category is cleared/rejected.
- [ ] OWNER/ADMIN list/create/edit/void; ATHLETE cannot read entries/totals;
  non-member is privacy-hidden.
- [ ] Every mutation appends actor/action/time and preserves all prior events;
  stale/failed write preserves current expense.
- [ ] No expense creates member debt, reimbursement, charge, or transfer.
- [ ] Backend full gate passes; test count `Δ+18` or greater.

**Tests:** domain unit + PostgreSQL integration (`Δ+18`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): track audited group expenses`

### T48: Expose expense and finance-total HTTP contracts

**What:** Add organizer-only expense list/create/update/void and aggregate total
endpoints with ETags, privacy, redaction, and Bruno requests.
**Where:** Groups expense HTTP adapters, bootstrap tests,
`bruno/Finance/Expenses/`.
**Depends on:** T47.
**Reuses:** expense use cases, actor resolver, version/problem conventions.
**Requirement:** `FIN-05..07`, `GRP-PRIVATE-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Exact DTOs use integer cents/local date/enum/custom fields and never
  expose audit internals beyond specified actor/action/time history.
- [ ] Organizer happy/validation/stale/void/retry outcomes and athlete/nonmember
  privacy paths have exact assertions.
- [ ] Totals include only defined manual charges/expenses and make no settlement
  or reimbursement claim.
- [ ] Every route has Bruno method/path/status/body assertions.
- [ ] Backend HTTP gate passes; test count `Δ+16` or greater.

**Tests:** Spring HTTP + Bruno (`Δ+16`).
**Gate:** Backend HTTP + Safety.
**Commit:** `feat(groups): expose expense tracking api`

### T49: Add the attendance and waitlist migration

**What:** Add V6 current-attendance, append-only override events, and monotonic
per-game waitlist sequence storage with concurrency constraints.
**Where:** `backend/features/groups/src/main/resources/db/migration/V6__add_game_attendance.sql`,
schema integration tests.
**Depends on:** T48.
**Reuses:** game/member foreign keys, optimistic versions, PostgreSQL partial
unique indexes and row locking.
**Requirement:** `ATTEND-01..04`, `FIN-01`, `GRP-PRIVATE-01`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] One row per game/member stores current CONFIRMED/DECLINED/WAITLISTED,
  optional waitlist sequence, response/update times and version.
- [ ] Waitlist sequence is non-null/unique only when WAITLISTED and never reused
  within a game; game keeps a monotonic allocator.
- [ ] Attendance event rows preserve actor/source/old/new/reason/server time and
  cannot be updated/deleted through adapters.
- [ ] Cross-group/non-member references and contradictory status/sequence writes
  fail constraints.
- [ ] Backend full gate passes; test count `Δ+14` or greater.

**Tests:** PostgreSQL migration/constraint integration (`Δ+14`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): migrate attendance waitlist`

### T50: Implement attendance transition and deadline rules

**What:** Define athlete self-response and organizer override decisions for
capacity, deadline, game lifecycle, and current attendance state.
**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/domain/attendance/`,
application command ports and tests.
**Depends on:** T49.
**Reuses:** game lifecycle/role policy, server clock, charge side-effect port.
**Requirement:** `ATTEND-01..04`, `FIN-01..02`, `GAME-04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Athlete can confirm/decline/withdraw only on published game before
  deadline; cancelled/completed closes self-service.
- [ ] Confirmation decision is CONFIRMED below capacity and WAITLISTED at/over
  capacity; capacity decrease never demotes.
- [ ] Organizer override accepts valid published attendance after deadline with
  mandatory audit source/reason, but cannot mutate frozen cancelled/completed
  history except where spec explicitly permits finance correction.
- [ ] Every old/new/no-response combination has an exact allowed/denied outcome.
- [ ] Backend quick gate passes; test count `Δ+22` or greater.

**Tests:** domain/application unit (`Δ+22`).
**Gate:** Backend quick + Safety.
**Commit:** `feat(groups): model attendance transitions`

### T51: Implement concurrency-safe confirmation and waitlisting

**What:** Serialize confirmation on the game row, allocate FIFO sequence, and
create the game charge atomically only for confirmed paid-game outcomes.
**Where:** Groups attendance command/JDBC adapter and deterministic concurrency
tests.
**Depends on:** T50.
**Reuses:** `SELECT ... FOR UPDATE`, V6 sequence, V5 unique charge identity,
transaction runner and deterministic latches.
**Requirement:** `ATTEND-01`, `FIN-01`, `FIN-03`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Game lock precedes capacity count/attendance mutation/charge creation in
  one READ_COMMITTED transaction.
- [ ] Simultaneous final-spot requests produce exactly one CONFIRMED and the
  rest uniquely ordered WAITLISTED, never over capacity.
- [ ] Confirm retry/duplicate native delivery creates one attendance row and at
  most one charge; declined/waitlisted receive none.
- [ ] Injected attendance/charge failure rolls back response, sequence and
  charge together.
- [ ] Backend full gate passes; test count `Δ+14` or greater.

**Tests:** PostgreSQL transaction/concurrency integration (`Δ+14`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): confirm attendance atomically`

### T52: Implement withdrawal, FIFO promotion, and capacity adjustment

**What:** Atomically withdraw/decline, promote earliest valid waitlist entries,
adjust capacity, and create promotion charges without forgiving withdrawals.
**Where:** Groups attendance promotion/capacity use cases/JDBC adapter and tests.
**Depends on:** T51.
**Reuses:** locked game aggregate, FIFO sequence, charge service and event audit.
**Requirement:** `ATTEND-02..04`, `FIN-01..02`, `GAME-04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Confirmed withdrawal leaves existing charge PENDING, frees one spot and
  promotes exactly the earliest eligible waitlisted member in the same commit.
- [ ] Capacity increase promotes as many FIFO members as new spots permit and
  charges each paid-game promotion once.
- [ ] Capacity below confirmed count demotes nobody and blocks new confirms
  until count falls below capacity.
- [ ] Concurrent withdraw/increase/confirm operations preserve capacity, FIFO,
  unique sequence and unique charge invariants with rollback on failure.
- [ ] Backend full gate passes; test count `Δ+16` or greater.

**Tests:** unit + PostgreSQL concurrency integration (`Δ+16`).
**Gate:** Backend full + Safety.
**Commit:** `feat(groups): promote waitlist atomically`

### T53: Expose attendance self-service and override HTTP contracts

**What:** Add member attendance read/self-response and organizer override/
capacity command endpoints with version/idempotency, privacy, and Bruno tests.
**Where:** Groups attendance HTTP adapters, bootstrap tests,
`bruno/Games/Attendance/`.
**Depends on:** T52.
**Reuses:** attendance use cases, game detail projection, actor resolver,
`ApiProblem`.
**Requirement:** `ATTEND-01..04`, `FIN-01..02`,
`GRP-PRIVATE-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Athlete response targets only self and cannot author confirmed/waitlist
  position, charge, actor, timestamp or capacity.
- [ ] Organizer override explicitly names member/target response/reason and
  produces auditable output; capacity command uses game ETag.
- [ ] Exact happy, full, deadline, frozen, invalid transition, stale, retry,
  unauthorized and privacy-hidden responses are asserted.
- [ ] Game detail returns authoritative own response and aggregate counts
  without exposing other attendance beyond allowed member view.
- [ ] Every route has Bruno assertions; Backend HTTP gate passes with test count
  `Δ+20` or greater.

**Tests:** Spring HTTP + Bruno (`Δ+20`).
**Gate:** Backend HTTP + Safety.
**Commit:** `feat(groups): expose attendance waitlist api`

### T54: Add attendance/finance concurrency discrimination sensors

**What:** Add deterministic real-PostgreSQL stress scenarios that prove all
capacity, FIFO, charge, audit and rollback invariants under competing commands.
**Where:** `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/attendance/`.
**Depends on:** T53.
**Reuses:** Testcontainers fixture, explicit latches/barriers, production use
cases/adapters; no timing sleeps.
**Requirement:** `ATTEND-01..04`, `FIN-01..04`, `GAME-04`,
`GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Sensors race final-spot confirms, confirm/withdraw, two promotions,
  capacity increase/decrease, command retry and game cancellation.
- [ ] Assertions prove no overbooking, duplicate sequence/charge/audit,
  non-FIFO promotion, silent demotion, lost status, or partial rollback.
- [ ] Each sensor deterministically fails if its row lock, uniqueness guard or
  transaction boundary is removed in scratch mutation.
- [ ] Backend full gate passes; test count `Δ+10` or greater.

**Tests:** PostgreSQL concurrency integration (`Δ+10`).
**Gate:** Backend full + Safety.
**Commit:** `test(groups): harden attendance concurrency`

### T55: Add mobile attendance DTOs and gateway

**What:** Model own attendance, organizer override, capacity command, audit-safe
responses, derived counts and stable retry semantics in KMP.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/attendance/`,
matching MockEngine tests.
**Depends on:** T54.
**Reuses:** game gateway/ETags, authenticated/idempotent network patterns.
**Requirement:** `ATTEND-01..04`, `FIN-01..02`,
`GRP-PRIVATE-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] DTOs round-trip no-response/confirmed/declined/waitlisted and never permit
  client-authored queue position/charge/aggregate count.
- [ ] Self/override/capacity requests preserve command key/ETag through auth
  refresh and map full/deadline/frozen/stale/hidden outcomes distinctly.
- [ ] Athlete DTO contains only allowed own response and aggregate counts.
- [ ] Mobile quick gate passes; test count `Δ+12` or greater.

**Tests:** KMP gateway unit (`Δ+12`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): model attendance transport`

### T56: Integrate attendance into GameDetailViewModel

**What:** Coordinate RSVP/withdraw/retry, live waitlist promotion refresh,
deadline/frozen states, and organizer override/capacity intents in game detail.
**Where:** Groups game-detail/attendance presentation packages and tests.
**Depends on:** T55.
**Reuses:** existing GameDetailViewModel, attendance gateway, AD-025 effects.
**Requirement:** `ATTEND-01..04`, `FIN-01..02`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] State derives available action from authoritative role/status/deadline/
  response and exposes confirmed/waitlist position/count accurately.
- [ ] Double tap/recreation/retry issues one logical command and reconciles the
  returned game/charge state without optimistic overbooking claims.
- [ ] Withdrawal confirmation explains pending charge; promotion refresh shows
  confirmed and at-most-one charge.
- [ ] Organizer overrides require explicit member/state/reason and capacity
  conflicts preserve local intent for reload/retry.
- [ ] Mobile quick gate passes; test count `Δ+16` or greater.

**Tests:** KMP ViewModel unit (`Δ+16`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): coordinate attendance responses`

### T57: Build RSVP, waitlist, and organizer attendance UI

**What:** Add member response controls, waitlist/availability/deadline/frozen
presentation, withdrawal charge warning, and organizer override/capacity UI.
**Where:** Groups game-detail attendance Compose components/resources/tests.
**Depends on:** T56.
**Reuses:** game detail screen, Saqz buttons/badges/dialogs/sheets/input controls.
**Requirement:** `ATTEND-01..04`, `FIN-01..02`,
`GRP-UI-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Member sees Confirmar/Não vou/withdraw actions only when legal plus exact
  confirmed/waitlisted position/available/deadline states.
- [ ] Withdrawal confirmation says the tracked charge remains pending; no copy
  claims automatic refund/payment processing.
- [ ] Organizer UI requires override reason and exposes capacity-below-confirmed
  warning without demotion controls.
- [ ] Compact/keyboard/max-text semantics retain ordered 48 dp controls.
- [ ] Mobile quick gate passes; test count `Δ+16` or greater.

**Tests:** common Compose UI (`Δ+16`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): build attendance waitlist ui`

### T58: Add mobile finance DTOs and gateway

**What:** Model organizer/all and athlete/own charges, monthly generation,
status audit, expenses, totals, ETags, command keys, and exact API errors.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/finance/`,
matching MockEngine tests.
**Depends on:** T57.
**Reuses:** authenticated gateway, common BRL/local-date formatting, charge and
expense endpoint contracts.
**Requirement:** `FIN-01..07`, `GRP-PRIVATE-01`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] DTOs preserve integer cents/kind/month/game/member/status/audit/expense
  fields and never model credentials/settlement/partial/refund/balance.
- [ ] Organizer and athlete gateway methods are separate so an athlete client
  cannot accidentally request group expense/total paths.
- [ ] Monthly/status/expense retries preserve idempotency/ETags and map
  validation/stale/hidden/forbidden outcomes.
- [ ] Mobile quick gate passes; test count `Δ+16` or greater.

**Tests:** KMP gateway unit (`Δ+16`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): model manual finance transport`

### T59: Implement FinanceViewModel for charges and monthly generation

**What:** Coordinate role-aware charge lists, athlete own charges, organizer
monthly selection/review/generation, and audited status updates.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/finance/charges/`,
draft schema and tests.
**Depends on:** T58.
**Reuses:** AD-025, membership summaries, finance gateway, stable draft/command
pattern.
**Requirement:** `FIN-01..05`, `FIN-07`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Athlete state loads only own charges and has no totals/status/monthly/
  expense intents; organizer state loads full permitted data.
- [ ] Monthly draft selects active members, reviews amount/due date, restores
  safely and generates once under a stable key.
- [ ] Status intent preserves ETag, shows mandatory system audit outcome,
  handles stale reload, and never edits amount/actor/time.
- [ ] States/copy consistently say manually tracked, not processed/settled.
- [ ] Mobile quick gate passes; test count `Δ+18` or greater.

**Tests:** KMP ViewModel/draft unit (`Δ+18`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): coordinate charge tracking`

### T60: Build charge and monthly-generation UI

**What:** Add athlete own-charge and organizer charge/month generation/status
screens with BRL/date presentation, visibility boundaries, and audit feedback.
**Where:** Groups finance charge Compose packages/resources/tests.
**Depends on:** T59.
**Reuses:** Saqz list/card/badge/input/dialog/sheet components, currency/date
formatters and membership selector patterns.
**Requirement:** `FIN-01..05`, `FIN-07`, `GRP-UI-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Athlete sees only own kind/subject/amount/due/status and no group total,
  other member, status action or expense entry point.
- [ ] Organizer selects month/members, reviews default amount/due date, excludes
  members or waives generated charges, and sees retry/equivalent success.
- [ ] Status controls clearly mean manual record; no payment CTA, processor,
  partial/refund/balance language or credential field exists.
- [ ] Compact/keyboard/max-text semantics retain ordered 48 dp actions.
- [ ] Mobile quick gate passes; test count `Δ+16` or greater.

**Tests:** common Compose UI (`Δ+16`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): build charge tracking ui`

### T61: Implement ExpenseViewModel

**What:** Coordinate organizer-only expense list/totals and restorable
create/edit/void forms with conditional category and optimistic versioning.
**Where:** `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/finance/expenses/`,
draft schema and tests.
**Depends on:** T60.
**Reuses:** finance gateway, GroupSetup conditional validation, AD-025 effects.
**Requirement:** `FIN-05..07`, `GRP-UI-02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] OWNER/ADMIN states load/list/create/edit/void; athlete route is absent and
  no request can be dispatched.
- [ ] Description/BRL/date/category/custom/notes validate locally and preserve
  draft/version/idempotency through restart/retry/conflict.
- [ ] Preset category clears custom value; void requires confirmation; success
  refreshes totals without erasing audit history.
- [ ] Mobile quick gate passes; test count `Δ+16` or greater.

**Tests:** KMP ViewModel/draft unit (`Δ+16`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): coordinate expense tracking`

### T62: Build expense and group-finance UI

**What:** Add organizer expense list/totals plus create/edit/void forms and
audit-safe presentation, with no athlete discoverability.
**Where:** Groups finance expense Compose packages/resources/tests.
**Depends on:** T61.
**Reuses:** Saqz form/list/dialog components, BRL/local-date formatters.
**Requirement:** `FIN-05..07`, `GRP-UI-01..02`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Organizer can reach every expense/total/form action and sees required
  validation plus create/edit/void audit feedback.
- [ ] OTHER reveals required custom category; other choices clear/hide it;
  values display as BRL/local date, never raw cents/enum.
- [ ] Copy never implies reimbursement, debt, settlement or money transfer; an
  athlete has no route/action/hidden semantics for expenses/totals.
- [ ] Compact/keyboard/max-text semantics retain ordered 48 dp actions.
- [ ] Mobile quick gate passes; test count `Δ+14` or greater.

**Tests:** common Compose UI (`Δ+14`).
**Gate:** Mobile quick + Safety.
**Commit:** `feat(groups): build expense tracking ui`

### T63: Persist non-sensitive group drafts on Android

**What:** Implement Android app-private versioned draft storage for setup,
game/series, monthly-charge, and expense drafts with atomic read/write/clear.
**Where:** `mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/groups/draft/`,
composition, JVM/instrumented tests.
**Depends on:** T62.
**Reuses:** current Android local access store pattern and Groups draft ports.
**Requirement:** `GRP-UI-02`, `GRP-REG-03`, `GAME-03`,
`FIN-03`, `FIN-06`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Drafts restore exact type/version/group/resource/ETag/command key and
  allowed form values after process recreation.
- [ ] Writes are atomic; corrupt/old schema returns typed discard/migration
  outcome and never dispatches a command automatically.
- [ ] Storage rejects/excludes bearer tokens, invite codes, photo bytes/handles,
  payment credentials and raw server errors.
- [ ] Confirmed success clears only its matching draft; logout/group loss clears
  scoped drafts.
- [ ] Android native gate passes; test count `Δ+10` or greater.

**Tests:** Android JVM + instrumented (`Δ+10`).
**Gate:** Android native + Safety.
**Commit:** `feat(android): persist safe group drafts`

### T64: Persist non-sensitive group drafts on iOS

**What:** Implement iOS app-container versioned draft storage with the same
setup/game/monthly/expense atomic read/write/clear contract.
**Where:** `mobile/ios-app/SaqzIOS/GroupsDraft/`, app composition,
XCTest/lifecycle UI tests.
**Depends on:** T63.
**Reuses:** current iOS local access store pattern and Groups draft ports.
**Requirement:** `GRP-UI-02`, `GRP-REG-03`, `GAME-03`,
`FIN-03`, `FIN-06`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Draft values/versions/keys round-trip across controller/app recreation and
  match Android/common contract fixtures.
- [ ] Atomic writes and corrupt/old schema handling never auto-dispatch or lose
  an unrelated draft.
- [ ] Tokens, invite codes, photo data/handles, payment credentials and raw
  errors are structurally absent.
- [ ] Matching success/logout/group-loss cleanup is exact and lifecycle-safe.
- [ ] iOS native gate passes; test count `Δ+10` or greater.

**Tests:** XCTest + targeted XCUITest (`Δ+10`).
**Gate:** iOS native + Safety.
**Commit:** `feat(ios): persist safe group drafts`

### T65: Integrate Groups navigation, selection, and incomplete-profile gating

**What:** Wire setup/home/people/games/game-detail/finance routes into the app
shell, reconcile selected group/session/invite success, and gate legacy groups
until profile completion.
**Where:** `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/`,
app dependencies/resource preflight and tests.
**Depends on:** T64.
**Reuses:** Navigation Compose route ownership, Access session/selected-group
state, Groups route factories, AD-025.
**Requirement:** `GRP-REG-02..05`, `GRP-DEFAULT-04`,
`GRP-PRIVATE-01`, `GRP-UI-01..02`, `INVITE-03`,
`GRP-REGRESSION-01`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] No-group opens setup; selected complete group opens private home; invite
  success selects/opens once; switch/remove/logout never flashes prior group.
- [ ] INCOMPLETE legacy group remains readable and routes organizer to profile
  completion while blocking game/attendance/finance mutations; athlete has no
  completion edit action.
- [ ] Role/status determines exact People/Games/Finance navigation visibility;
  athlete finance opens own charges only.
- [ ] Route ViewModels are lifecycle-scoped, effects do not replay, and Access
  retains only auth/session routing.
- [ ] Mobile integration gate passes; test count `Δ+18` or greater.

**Tests:** Compose app navigation/ViewModel/UI (`Δ+18`).
**Gate:** Mobile integration + Safety.
**Commit:** `feat(mobile): integrate private group routes`

### T66: Add cross-platform accessibility and lifecycle recovery journeys

**What:** Exercise end-user registration, invite, photo, game, RSVP, monthly
charge, expense, restart and role-visibility journeys on Android/iOS with
accessibility constraints.
**Where:** Android instrumented tests, `SaqzIOSTests`, `SaqzIOSUITests`, common
Compose journey fixtures.
**Depends on:** T65.
**Reuses:** platform fake adapters, semantic tags, test environment/session
fixtures, shared expected presentation fixtures.
**Requirement:** `GRP-UI-01..02`, `GRP-REG-01..05`, `GAME-01..04`,
`ATTEND-01..04`, `FIN-01..07`, `INVITE-01..04`.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] Compact/keyboard/max text/Dynamic Type journeys reach every action in
  semantic order with 48 dp-equivalent targets and useful labels.
- [ ] Rotation/background/process/controller restart restores allowed drafts
  and command keys without duplicate group/game/RSVP/charge/expense/invite.
- [ ] Camera/library cancellation, offline/retry, stale ETag, logout, group
  switch and membership loss recover without private-data flash/cache leak.
- [ ] Owner/admin/athlete journeys prove exact navigation and finance/privacy
  visibility on both platforms.
- [ ] Android native and iOS native gates pass; test count `Δ+16` or greater.

**Tests:** Android instrumented + XCTest/XCUITest (`Δ+16`).
**Gate:** Android native + iOS native + Safety.
**Commit:** `test(groups): cover mobile group journeys`

### T67: Update aggregate inventories, documentation, and run the full gate

**What:** Make Groups mandatory in repository gates/docs, prove route/resource/
test inventory mutation resistance, and run the complete clean feature gate.
**Where:** `scripts/check-gradle`, relevant `tests/scripts/`, `README.md`,
resource preflight/contracts, feature docs status only after evidence.
**Depends on:** T66.
**Reuses:** exact-inventory/zero-test/failure-propagation script patterns,
`scripts/check-all`.
**Requirement:** `GRP-REGRESSION-01`, all `GRP-*`, `GAME-*`, `ATTEND-*`,
`FIN-*`, `INVITE-*` as aggregate evidence.

**Tools:** MCP: none. Skill: `tlc-spec-driven`; `backprop` on failure.

**Done when:**

- [ ] `check-gradle` requires Groups backend unit/integration and mobile
  compile/allTests in exact order, propagates each failure, and rejects zero
  discovered tests.
- [ ] Script tests kill removal of Groups suites, Bruno coverage, architecture
  inventory, resource preflight, credential/scope/workspace checks and native
  gates.
- [ ] README documents exact group test commands/manual-tracking privacy and has
  no stale Access ownership, public-photo or payment-processing claim.
- [ ] Fresh `rtk scripts/check-all` passes with no skipped/weakened tests and
  no unrelated diff; discovered before/after counts are recorded.
- [ ] Tasks status may become `Done` only after the subsequent independent
  Verifier writes PASS `validation.md`.

**Tests:** shell mutation contracts + complete retained/new product suites
(`Δ+8` script cases; no suite count decrease).
**Gate:** Script contract + Gradle aggregate + Complete aggregate + Safety.
**Commit:** `chore(groups): enforce complete group delivery gate`

## Phase Execution Map

```text
Phase 1  T01 → T02 → T03
Phase 2  T03 → T08 → T09 → T13
Phase 3  T13 → T14 → T15 → T16 → T17 → T18 → T19 → T20 → T21 → T22
Phase 4  T22 → T23 → T24 → T25 → T26 → T27 → T28 → T29
Phase 5  T29 → T30 → T31 → T32 → T33 → T34 → T35 → T36
Phase 6  T36 → T37 → T38 → T39 → T40 → T41 → T42
Phase 7  T42 → T43 → T44 → T45 → T46 → T47 → T48
Phase 8  T48 → T49 → T50 → T51 → T52 → T53 → T54
Phase 9  T54 → T55 → T56 → T57 → T58 → T59 → T60 → T61 → T62
Phase 10 T62 → T63 → T64 → T65 → T66 → T67
```

Execution is strictly sequential; arrows at phase boundaries are intentionally
repeated to make the cross-phase dependency explicit. Whole phases are the
batching unit and are never split between workers.

## Requirement Traceability

| Requirements | Owning tasks |
| --- | --- |
| `GRP-REG-01..05` | T03, T09, T14..T22, T63..T67 |
| `GRP-DEFAULT-01..04` | T03, T14..T22, T31, T34, T65..T67 |
| `GRP-PHOTO-01..02` | T21, T23..T29, T63..T67 |
| `GRP-PRIVATE-01` | T03, T13, T17, T19, T23..T29, T33..T36, T43..T67 |
| `GAME-01..04` | T18, T30..T42, T54, T63..T67 |
| `ATTEND-01..04` | T30, T33, T45, T49..T57, T66..T67 |
| `FIN-01..07` | T17..T18, T31, T43..T48, T50..T62, T66..T67 |
| `GRP-UI-01..02` | T08, T09, T13, T20..T23, T26..T29, T32, T35..T42, T46, T48, T51..T67 |
| `GRP-REGRESSION-01` | T01..T03, T08..T13, T65..T67 |
| `INVITE-01..04` | T01, T03, T09, T13, T65..T67 |

## Task Granularity Check

Each row was checked as one revertible component, aggregate command/resource,
migration, route, platform adapter, or test/gate contract. Co-located tests and
necessary wiring are part of that deliverable, not deferred tasks.

| Task | Deliverable scope | Status |
| --- | --- | --- |
| T01: Shared actor/membership contracts | shared-kernel contract | ✅ Granular |
| T02: Backend Groups boundary | module boundary | ✅ Granular |
| T03: Complete backend ownership migration | atomic compatibility migration | ✅ Granular |
| T08: Mobile Groups boundary | module boundary | ✅ Granular |
| T09: Complete mobile ownership migration | atomic compatibility migration | ✅ Granular |
| T13: Invite/deep-link journey | cross-layer journey contract | ✅ Granular |
| T14: Profile/default migration | one Flyway migration | ✅ Granular |
| T15: Profile/default validation | domain validation aggregate | ✅ Granular |
| T16: Complete group registration | create aggregate transaction | ✅ Granular |
| T17: Private group reads | read projection | ✅ Granular |
| T18: Versioned group updates | update aggregate | ✅ Granular |
| T19: Group profile HTTP resource | one controller/resource contract | ✅ Granular |
| T20: Mobile setup contracts | transport/draft contract slice | ✅ Granular |
| T21: GroupSetupViewModel | one route ViewModel | ✅ Granular |
| T22: Group setup UI | one route screen flow | ✅ Granular |
| T23: Private media network | core network capability | ✅ Granular |
| T24: Private photo storage | photo storage component | ✅ Granular |
| T25: Photo HTTP resource | one controller/resource contract | ✅ Granular |
| T26: Mobile photo state | photo state/gateway slice | ✅ Granular |
| T27: Android photo adapter | one platform adapter | ✅ Granular |
| T28: iOS photo adapter | one platform adapter | ✅ Granular |
| T29: Photo editor UI | one UI component flow | ✅ Granular |
| T30: Games/series migration | one Flyway migration | ✅ Granular |
| T31: Game lifecycle domain | game aggregate | ✅ Granular |
| T32: Recurrence materializer | recurrence component | ✅ Granular |
| T33: Occurrence persistence | game repository component | ✅ Granular |
| T34: Series revision persistence | series repository component | ✅ Granular |
| T35: Game HTTP resource | game controller contract | ✅ Granular |
| T36: Series HTTP resource | series controller contract | ✅ Granular |
| T37: Mobile game gateway | transport component | ✅ Granular |
| T38: GamesViewModel | one route ViewModel | ✅ Granular |
| T39: Games list UI | one route screen | ✅ Granular |
| T40: GameEditorViewModel | one route ViewModel | ✅ Granular |
| T41: Game editor UI | one route screen | ✅ Granular |
| T42: Game detail route | one route state/screen slice | ✅ Granular |
| T43: Finance migration | one Flyway migration | ✅ Granular |
| T44: Charge domain | charge aggregate | ✅ Granular |
| T45: Charge generation transactions | charge-generation component | ✅ Granular |
| T46: Charge HTTP resource | charge controller contract | ✅ Granular |
| T47: Expense aggregate | expense component | ✅ Granular |
| T48: Expense HTTP resource | expense controller contract | ✅ Granular |
| T49: Attendance migration | one Flyway migration | ✅ Granular |
| T50: Attendance domain | attendance aggregate | ✅ Granular |
| T51: Confirmation transaction | confirmation command component | ✅ Granular |
| T52: Promotion/capacity transaction | promotion component | ✅ Granular |
| T53: Attendance HTTP resource | attendance controller contract | ✅ Granular |
| T54: Concurrency sensors | one discrimination suite | ✅ Granular |
| T55: Mobile attendance gateway | transport component | ✅ Granular |
| T56: Attendance detail state | route-state extension | ✅ Granular |
| T57: Attendance UI | one UI component flow | ✅ Granular |
| T58: Mobile finance gateway | transport component | ✅ Granular |
| T59: FinanceViewModel | one route ViewModel | ✅ Granular |
| T60: Charge/monthly UI | one route screen flow | ✅ Granular |
| T61: ExpenseViewModel | one route ViewModel | ✅ Granular |
| T62: Expense UI | one route screen flow | ✅ Granular |
| T63: Android draft adapter | one platform adapter | ✅ Granular |
| T64: iOS draft adapter | one platform adapter | ✅ Granular |
| T65: Groups app navigation | navigation integration | ✅ Granular |
| T66: Mobile recovery journeys | cross-platform journey suite | ✅ Granular |
| T67: Aggregate delivery gate | repository gate contract | ✅ Granular |

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T01 | None | Entry node | ✅ Match |
| T02 | T01 | T01 → T02 | ✅ Match |
| T03 | T02 | T02 → T03 | ✅ Match |
| T08 | T03 | T03 → T08 | ✅ Match |
| T09 | T08 | T08 → T09 | ✅ Match |
| T13 | T09 | T09 → T13 | ✅ Match |
| T14 | T13 | T13 → T14 | ✅ Match |
| T15 | T14 | T14 → T15 | ✅ Match |
| T16 | T15 | T15 → T16 | ✅ Match |
| T17 | T16 | T16 → T17 | ✅ Match |
| T18 | T17 | T17 → T18 | ✅ Match |
| T19 | T18 | T18 → T19 | ✅ Match |
| T20 | T19 | T19 → T20 | ✅ Match |
| T21 | T20 | T20 → T21 | ✅ Match |
| T22 | T21 | T21 → T22 | ✅ Match |
| T23 | T22 | T22 → T23 | ✅ Match |
| T24 | T23 | T23 → T24 | ✅ Match |
| T25 | T24 | T24 → T25 | ✅ Match |
| T26 | T25 | T25 → T26 | ✅ Match |
| T27 | T26 | T26 → T27 | ✅ Match |
| T28 | T27 | T27 → T28 | ✅ Match |
| T29 | T28 | T28 → T29 | ✅ Match |
| T30 | T29 | T29 → T30 | ✅ Match |
| T31 | T30 | T30 → T31 | ✅ Match |
| T32 | T31 | T31 → T32 | ✅ Match |
| T33 | T32 | T32 → T33 | ✅ Match |
| T34 | T33 | T33 → T34 | ✅ Match |
| T35 | T34 | T34 → T35 | ✅ Match |
| T36 | T35 | T35 → T36 | ✅ Match |
| T37 | T36 | T36 → T37 | ✅ Match |
| T38 | T37 | T37 → T38 | ✅ Match |
| T39 | T38 | T38 → T39 | ✅ Match |
| T40 | T39 | T39 → T40 | ✅ Match |
| T41 | T40 | T40 → T41 | ✅ Match |
| T42 | T41 | T41 → T42 | ✅ Match |
| T43 | T42 | T42 → T43 | ✅ Match |
| T44 | T43 | T43 → T44 | ✅ Match |
| T45 | T44 | T44 → T45 | ✅ Match |
| T46 | T45 | T45 → T46 | ✅ Match |
| T47 | T46 | T46 → T47 | ✅ Match |
| T48 | T47 | T47 → T48 | ✅ Match |
| T49 | T48 | T48 → T49 | ✅ Match |
| T50 | T49 | T49 → T50 | ✅ Match |
| T51 | T50 | T50 → T51 | ✅ Match |
| T52 | T51 | T51 → T52 | ✅ Match |
| T53 | T52 | T52 → T53 | ✅ Match |
| T54 | T53 | T53 → T54 | ✅ Match |
| T55 | T54 | T54 → T55 | ✅ Match |
| T56 | T55 | T55 → T56 | ✅ Match |
| T57 | T56 | T56 → T57 | ✅ Match |
| T58 | T57 | T57 → T58 | ✅ Match |
| T59 | T58 | T58 → T59 | ✅ Match |
| T60 | T59 | T59 → T60 | ✅ Match |
| T61 | T60 | T60 → T61 | ✅ Match |
| T62 | T61 | T61 → T62 | ✅ Match |
| T63 | T62 | T62 → T63 | ✅ Match |
| T64 | T63 | T63 → T64 | ✅ Match |
| T65 | T64 | T64 → T65 | ✅ Match |
| T66 | T65 | T65 → T66 | ✅ Match |
| T67 | T66 | T66 → T67 | ✅ Match |

No task depends on a later phase, and every arrow has exactly one matching
immediate dependency. Transitive dependencies follow from the strict sequence.

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T01 | shared-kernel contract | unit/architecture | unit Δ+4 | ✅ OK |
| T02 | module boundary | unit/architecture | architecture Δ+4 | ✅ OK |
| T03 | atomic compatibility migration | unit + integration + HTTP | moved suites Δ0 + HTTP Δ+4 | ✅ OK |
| T08 | module boundary | unit/build | unit Δ+1 | ✅ OK |
| T09 | atomic compatibility migration | unit + Compose UI | moved suites Δ0 | ✅ OK |
| T13 | cross-layer journey contract | HTTP + KMP + native | journey Δ+12 | ✅ OK |
| T14 | one Flyway migration | PostgreSQL integration | integration Δ+14 | ✅ OK |
| T15 | domain validation aggregate | unit | unit Δ+24 | ✅ OK |
| T16 | create aggregate transaction | unit + integration | unit + integration Δ+14 | ✅ OK |
| T17 | read projection | unit + integration | unit + integration Δ+10 | ✅ OK |
| T18 | update aggregate | unit + integration | unit + integration Δ+14 | ✅ OK |
| T19 | one controller/resource contract | HTTP + Bruno | HTTP + Bruno Δ+18 | ✅ OK |
| T20 | transport/draft contract slice | unit | unit Δ+14 | ✅ OK |
| T21 | one route ViewModel | unit | unit Δ+18 | ✅ OK |
| T22 | one route screen flow | Compose UI | Compose UI Δ+24 | ✅ OK |
| T23 | core network capability | unit | unit Δ+12 | ✅ OK |
| T24 | photo storage component | unit + integration | unit + integration Δ+18 | ✅ OK |
| T25 | one controller/resource contract | HTTP + Bruno | HTTP + Bruno Δ+16 | ✅ OK |
| T26 | photo state/gateway slice | unit | unit Δ+14 | ✅ OK |
| T27 | one platform adapter | JVM + instrumented | Android Δ+10 | ✅ OK |
| T28 | one platform adapter | XCTest + XCUITest | iOS Δ+10 | ✅ OK |
| T29 | one UI component flow | Compose UI | Compose UI Δ+14 | ✅ OK |
| T30 | one Flyway migration | PostgreSQL integration | integration Δ+16 | ✅ OK |
| T31 | game aggregate | unit | unit Δ+18 | ✅ OK |
| T32 | recurrence component | unit + integration | unit + integration Δ+18 | ✅ OK |
| T33 | game repository component | unit + integration | unit + integration Δ+12 | ✅ OK |
| T34 | series repository component | unit + integration | unit + integration Δ+16 | ✅ OK |
| T35 | game controller contract | HTTP + Bruno | HTTP + Bruno Δ+20 | ✅ OK |
| T36 | series controller contract | HTTP + Bruno | HTTP + Bruno Δ+18 | ✅ OK |
| T37 | transport component | unit | unit Δ+16 | ✅ OK |
| T38 | one route ViewModel | unit | unit Δ+12 | ✅ OK |
| T39 | one route screen | Compose UI | Compose UI Δ+12 | ✅ OK |
| T40 | one route ViewModel | unit | unit Δ+18 | ✅ OK |
| T41 | one route screen | Compose UI | Compose UI Δ+18 | ✅ OK |
| T42 | one route state/screen slice | unit + Compose UI | unit + Compose UI Δ+14 | ✅ OK |
| T43 | one Flyway migration | PostgreSQL integration | integration Δ+18 | ✅ OK |
| T44 | charge aggregate | unit | unit Δ+18 | ✅ OK |
| T45 | charge-generation component | unit + integration | unit + integration Δ+18 | ✅ OK |
| T46 | charge controller contract | HTTP + Bruno | HTTP + Bruno Δ+18 | ✅ OK |
| T47 | expense component | unit + integration | unit + integration Δ+18 | ✅ OK |
| T48 | expense controller contract | HTTP + Bruno | HTTP + Bruno Δ+16 | ✅ OK |
| T49 | one Flyway migration | PostgreSQL integration | integration Δ+14 | ✅ OK |
| T50 | attendance aggregate | unit | unit Δ+22 | ✅ OK |
| T51 | confirmation command component | PostgreSQL integration | integration Δ+14 | ✅ OK |
| T52 | promotion component | unit + integration | unit + integration Δ+16 | ✅ OK |
| T53 | attendance controller contract | HTTP + Bruno | HTTP + Bruno Δ+20 | ✅ OK |
| T54 | one discrimination suite | PostgreSQL integration | integration Δ+10 | ✅ OK |
| T55 | transport component | unit | unit Δ+12 | ✅ OK |
| T56 | route-state extension | unit | unit Δ+16 | ✅ OK |
| T57 | one UI component flow | Compose UI | Compose UI Δ+16 | ✅ OK |
| T58 | transport component | unit | unit Δ+16 | ✅ OK |
| T59 | one route ViewModel | unit | unit Δ+18 | ✅ OK |
| T60 | one route screen flow | Compose UI | Compose UI Δ+16 | ✅ OK |
| T61 | one route ViewModel | unit | unit Δ+16 | ✅ OK |
| T62 | one route screen flow | Compose UI | Compose UI Δ+14 | ✅ OK |
| T63 | one platform adapter | JVM + instrumented | Android Δ+10 | ✅ OK |
| T64 | one platform adapter | XCTest + XCUITest | iOS Δ+10 | ✅ OK |
| T65 | navigation integration | unit + Compose UI | app integration Δ+18 | ✅ OK |
| T66 | cross-platform journey suite | instrumented + XCUITest | native journey Δ+16 | ✅ OK |
| T67 | repository gate contract | script + aggregate | script Δ+8 + all suites | ✅ OK |

No implementation test is deferred to another task. T13, T54, T66, and T67 add
higher-level discrimination/aggregate coverage in addition to—not instead of—
the unit/integration tests co-located with the behavior-producing tasks.

## Pre-Approval Result

- Task granularity: **60/60 pass**.
- Diagram/definition consistency: **60/60 match**.
- Test co-location: **60/60 pass**.
- Acceptance-criteria traceability: **34/34 criteria owned**.
- More than eight tasks: execution must first offer sequential whole-phase
  task-budgeted batches and wait for explicit user confirmation before spawning
  workers.
- Task tools/skills still require user confirmation before Execute.
