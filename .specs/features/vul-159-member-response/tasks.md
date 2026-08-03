# VUL-159 Tasks

## Execution Plan

### Phase 1: Contracts and transport

```text
T1 → T2
```

### Phase 2: Presentation state

```text
T3
```

### Phase 3: UI and evidence

```text
T4 → T5
```

## Task Breakdown

### T1: Attendance roster and opt-in contracts

**What**: Add domain models/commands and gateway methods for the member roster and auto-confirmation update.
**Where**: `mobile/features/groups/domain/.../attendance/Attendance.kt`
**Depends on**: None
**Requirement**: VUL159-01, VUL159-02, VUL159-05

**Done when**:

- [ ] Domain exposes confirmed/waitlisted roster entries and opt-in result.
- [ ] Gateway exposes read roster and update opt-in without Compose/Ktor dependencies.

**Tests**: none — domain data/port only
**Gate**: build

### T2: Ktor attendance roster and opt-in transport

**What**: Implement the two transport routes and regression tests using MockEngine.
**Where**: `mobile/features/groups/data/src/commonMain/.../KtorAttendanceGateway.kt`, `.../commonTest/.../KtorAttendanceGatewayTest.kt`
**Depends on**: T1
**Requirement**: VUL159-01, VUL159-02, VUL159-05, VUL159-08

**Done when**:

- [ ] Roster GET maps member names, order and nullable positions.
- [ ] Opt-in PUT uses the exact group member route and `enabled` JSON field.
- [ ] Existing response ETag/error/retry tests remain green.

**Tests**: integration/transport
**Gate**: full

### T3: Game detail response state and orchestration

**What**: Extend Contract/ViewModel with response loading, backend result mapping, membership gating, optimistic rollback and monotonic generation guards.
**Where**: `mobile/features/groups/presentation/src/commonMain/.../gamedetail/GameDetailContract.kt`, `GameDetailViewModel.kt`, tests and DI wiring
**Depends on**: T2
**Requirement**: VUL159-01 through VUL159-07

**Done when**:

- [ ] Initial state reads own attendance and roster, deriving waitlist position from roster order.
- [ ] Response actions send CONFIRM/DECLINE, apply success status, and rollback failures.
- [ ] Expired deadlines disable response actions; deadline errors also lock them.
- [ ] Switch visibility is MENSALISTA plus group feature flag, with optimistic rollback.
- [ ] Newer response/switch generations discard older completions.

**Tests**: unit
**Gate**: quick

### T4: Isolated response UI and strings

**What**: Add the response section, wire it into the existing detail screen, and add screen tests.
**Where**: `ui/gamedetail/GameResponseSection.kt`, minimal `GameDetailScreen.kt`, `strings_game_response.xml`, UI tests
**Depends on**: T3
**Requirement**: VUL159-01, VUL159-03, VUL159-04, VUL159-05, VUL159-07

**Done when**:

- [ ] Only Vou and Não vou appear; feedback and reserve ordinal are visible.
- [ ] Locked state and AVULSO notice use resource strings.
- [ ] Switch is present only in the permitted state and uses a labeled test tag.

**Tests**: unit/Compose screen
**Gate**: quick

### T5: Roborazzi evidence and full gate

**What**: Add/refresh visual cases, inspect generated PNGs, and run the mandatory local gates.
**Where**: `androidHostTest` screenshot test and generated local images only (images remain outside code PR)
**Depends on**: T4
**Requirement**: VUL159-01 through VUL159-08

**Done when**:

- [ ] Captures cover open response, waitlist feedback, locked deadline, error and mensalista switch.
- [ ] PNGs are re-recorded and visually read before delivery.
- [ ] Required presentation, detekt, compose-app and groups-data iOS gates pass.

**Tests**: visual
**Gate**: build

## Gate Check Commands

| Gate | Command |
| --- | --- |
| Quick | `cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test` |
| Full | `cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test :features:groups:data:iosSimulatorArm64Test` |
| Build | `cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test :features:groups:presentation:detektAll :compose-app:iosSimulatorArm64Test :features:groups:data:iosSimulatorArm64Test` |

## Requirement Traceability

| Requirement | Task | Status |
| --- | --- | --- |
| VUL159-01 | T1–T4 | Pending |
| VUL159-02 | T1–T3 | Pending |
| VUL159-03 | T3–T4 | Pending |
| VUL159-04 | T4 | Pending |
| VUL159-05 | T1–T4 | Pending |
| VUL159-06 | T3 | Pending |
| VUL159-07 | T4 | Pending |
| VUL159-08 | T2, T5 | Pending |
