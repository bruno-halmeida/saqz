# VUL-158 Validation

**Date**: 2026-08-03  
**Spec**: `.specs/features/vul-158-reserva/spec.md`  
**Diff range**: `origin/main..HEAD`  
**Verifier**: fresh-eyes pass in a detached scratch worktree

## Task Completion

| Task | Status | Evidence |
| --- | --- | --- |
| T1 | ✅ Done | Ktor roster/promotion/capacity transport and tests |
| T2 | ✅ Done | Detail load, group config, optimistic state and generation guards |
| T3 | ✅ Done | Dedicated waitlist section, PT-BR resources and admin controls |
| T4 | ✅ Done | UI/ViewModel tests, Roborazzi PNGs and final gate |

## Spec-Anchored Acceptance Criteria

| Criterion | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| R1 — non-empty roster | `Reserva`, queue position, avatar/initials, name and athlete position render | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/gamedetail/GameDetailScreenTest.kt:70-74` — asserts `Reserva`, `1º na fila`, `Central`, `Mensalista` and `Promover` | ✅ PASS |
| R2 — backend order and priority | received order is preserved; mensalista indicator is conditional on priority | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/gamedetail/GameDetailViewModelTest.kt:258-264` — asserts `wait-1, wait-2` order and config; `mobile/features/groups/data/src/commonTest/kotlin/br/com/saqz/groups/data/attendance/KtorAttendanceGatewayTest.kt:95-98` — asserts backend order | ✅ PASS |
| R3 — manual promotion only | manual admin action renders and sends route fields; FIFO does not render it | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/gamedetail/GameDetailScreenTest.kt:78-83`; `mobile/features/groups/data/src/commonTest/kotlin/br/com/saqz/groups/data/attendance/KtorAttendanceGatewayTest.kt:159-166` — exact route, requestId, memberId and reason | ✅ PASS |
| R4 — capacity and conflict | stepper opens; write sends exact `If-Match`; conflict closes sheet and reloads | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/gamedetail/GameDetailScreenTest.kt:105-108`; `mobile/features/groups/data/src/commonTest/kotlin/br/com/saqz/groups/data/attendance/KtorAttendanceGatewayTest.kt:187-195`; `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/gamedetail/GameDetailViewModelTest.kt:326-344` | ✅ PASS |
| R5 — empty/error/optimistic state | empty section omitted; load failure remains retryable; writes rollback and stale generations cannot overwrite | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/gamedetail/GameDetailScreenTest.kt:86-90`; `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/gamedetail/GameDetailViewModelTest.kt:280-323`; `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/gamedetail/GameDetailViewModel.kt:180,241` | ✅ PASS |
| R6 — strings | new UI copy is PT-BR and isolated from shared strings | `mobile/features/groups/presentation/src/commonMain/composeResources/values/strings_game_waitlist.xml:1-24` | ✅ PASS |

### Edge cases

- Missing waitlist position maps to `Posição não informada` in `GameWaitlistSection.kt:112-116,156-162`.
- Successful capacity writes with automatic promotions refresh the roster in `GameDetailViewModel.kt:254`.
- Load and write responses carry `loadGeneration`/`operationGeneration` guards in `GameDetailViewModel.kt:180,241`.

## Build-Level Gate

Command:

```text
cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test :features:groups:presentation:detektAll :compose-app:iosSimulatorArm64Test :features:groups:data:iosSimulatorArm64Test
```

Result: `BUILD SUCCESSFUL`; presentation reported 234 tests with 0 failures, data and compose-app tasks passed, and presentation detekt passed. Roborazzi host recording also passed.

## Discrimination Sensor

| Mutation | File | Description | Result |
| --- | --- | --- | --- |
| 1 | `GameWaitlistSection.kt:140` | Changed the promotion condition from `MANUAL` to `FIFO` | ✅ Killed — presentation test failed in both manual and FIFO UI cases |
| 2 | `GameDetailViewModel.kt:194` | Changed promotion failure rollback from `previousWaitlist` to `emptyList()` | ✅ Killed — `promotion rolls back waitlist on failure` failed |

**Sensor depth**: lightweight, 2 behavior-level mutations.  
**Result**: 2/2 killed — PASS ✅. The real checkout was not mutated; the scratch worktree was removed afterward.

## Visual Review

Roborazzi was recorded with `:features:groups:presentation:recordRoborazziAndroidHostTest`. The admin and capacity PNGs were opened and reviewed:

- `mobile/features/groups/presentation/screenshots/vul-158/game-detail-admin.png`
- `mobile/features/groups/presentation/screenshots/vul-158/game-detail-capacity.png`

Both show the DS card/row language, queue labels, initials, priority chip, manual actions and the bottom-sheet stepper in PT-BR.

## Coding Quality

- Scope is limited to the VUL-158 mobile presentation/data/domain paths, tests, own strings and screenshots, plus the required spec evidence.
- Backend ordering is consumed as received; no mobile priority sorting was introduced.
- Existing DS components and generation-guard patterns are reused.
- No unrelated formatting or cleanup was included.

**Validation**: ✅ PASS
