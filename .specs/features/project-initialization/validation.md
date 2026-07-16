# Project Initialization Backprop Validation

**Date**: 2026-07-15
**Spec**: `.specs/features/project-initialization/spec.md`
**Diff range**: `a372dee..3bdc759`
**Verifier**: standalone fresh-eyes fallback; repository instructions disabled sub-agent delegation

---

## Task Completion

| Task | Status | Notes |
| --- | --- | --- |
| Post-T24 / B6 | ✅ Done | Identity emulator test now delegates lifecycle to `firebase/session-fixture`. |

## Spec-Anchored Acceptance Criteria

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| AUTH-11: shared fixture, no direct launcher | Identity adapter emulator test consumes `session.fixture` and contains no direct `firebase-tools` launch. | `tests/scripts/check-gradle.test.sh:115-121` — `grep -Fq 'systemProperty("session.fixture"'`, `grep -Fq 'System.getProperty("session.fixture")'`, and direct-launch rejection | ✅ PASS |
| AUTH-11: account and token cleanup | Fixture account marker exists and token file is removed. | `FirebaseAdminTokenVerifierEmulatorTest.kt:76,78` — `assertTrue(Files.exists(state.resolve("account-deleted")))`; `assertFalse(Files.exists(state.resolve("id-token")))` | ✅ PASS |
| AUTH-11: process-tree cleanup | Fixture exits and every recorded child PID is dead. | `FirebaseAdminTokenVerifierEmulatorTest.kt:75,79-80` — `assertTrue(process.waitFor(...))`; `assertFalse(ProcessHandle.of(...).map(ProcessHandle::isAlive).orElse(false))` | ✅ PASS |
| AUTH-11: port cleanup before next task | Fixture reports bindable port; bootstrap task reuses port 9099 in same forced Gradle invocation. | `FirebaseAdminTokenVerifierEmulatorTest.kt:77` — `assertTrue(Files.exists(state.resolve("port-bindable")))`; combined emulator tasks exited zero | ✅ PASS |

**Status**: ✅ AUTH-11 outcome fully covered; no spec-precision gap.

## Discrimination Sensor

Scratch worktree: `/private/tmp/saqz-auth11-29388202882` at `3bdc759`; removed clean after validation.

| Mutation | Description | Killed? |
| --- | --- | --- |
| 1 | Changed Gradle property `session.fixture` to an invalid name. | ✅ Case 6 failed |
| 2 | Changed identity test property lookup to an invalid name. | ✅ Case 6 failed |
| 3 | Removed `SAQZ_FIXTURE_HOLD` lifecycle mode. | ✅ Case 6 failed |
| 4 | Removed the `port-bindable` cleanup proof. | ✅ Case 6 failed |
| 5 | Restored a direct `npx firebase-tools` launcher. | ✅ Case 6 failed with explicit lifecycle error |

**Sensor depth**: auth-critical, five targeted mutations
**Result**: 5/5 killed — PASS ✅

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code; existing fixture reused | ✅ |
| Surgical three-file change | ✅ |
| No scope creep or unrelated edits | ✅ |
| Matches bootstrap lifecycle pattern | ✅ |
| Tests map to AUTH-09/AUTH-11 and assert resulting state | ✅ |
| Guidelines followed: `coding-principles.md` | ✅ |

## Edge Cases

- [x] Normal fixture exit cleans account, token, children, and port.
- [x] Fixture failure cleans account, token, children, and port.
- [x] `SIGINT` cleans account, token, children, and port.
- [x] `SIGTERM` cleans account, token, children, and port.

## Gate Check

- `rtk tests/scripts/check-gradle.test.sh`: 6 passed, 0 failed.
- JDK 21 forced `:features:identity:emulatorTest :bootstrap:emulatorTest --rerun-tasks`: build successful, 14 tasks executed.
- `rtk scripts/test-scripts`: all groups passed, including four fixture lifecycle scenarios and backend/mobile isolation builds.
- GitHub Actions run `29388202882`: landing, Angular, Gradle, iOS, and aggregate initialization jobs all passed at `3bdc759`.
- Test delta: shell contract 5 → 6; Kotlin emulator test 1 → 1; fixture lifecycle scenarios 4 → 4; no deleted or skipped test.

## Requirement Traceability Update

| Requirement | Previous Status | New Status |
| --- | --- | --- |
| AUTH-11 | Mapped | ✅ Verified |

## Summary

**Overall**: ✅ Ready

**Spec-anchored check**: 1/1 changed AC matched exact spec outcome
**Sensor**: 5/5 mutations killed
**Gate**: all local and remote gates passed
**Issues found**: none; clean PASS, so no lesson entry recorded
