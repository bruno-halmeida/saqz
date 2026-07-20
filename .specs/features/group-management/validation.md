# Group Setup Branded Hierarchy Validation

**Date:** 2026-07-20
**Verdict:** PASS
**Authored commits:** `358ddf8c1862e53935193fc1c9782fbfedb1b5a9`, `e71075b7b0b96806f4ae627edb628f08a9b3e1b6`
**Verifier:** independent sub-agent (author != verifier)
**Method:** fresh inspection and mutation testing in `/private/tmp/saqz-verify-e71075b`, detached at `e71075b`.

## Scope

The audit covers the exact authored diffs `358ddf8^..358ddf8` and
`e71075b^..e71075b`. Commit `bed23c9` sits between them but is concurrent,
out-of-scope work and is not attributed to this UI refinement.

- `358ddf8`: group-management spec, `GroupSetupScreen.kt`, and
  `GroupSetupScreenTest.kt` only.
- `e71075b`: group-management spec and `GroupSetupScreenTest.kt` only.
- No photo, input, or navigation behavior change appears in either authored
  diff.

## Acceptance evidence

| Criterion | Spec-defined outcome | Exact assertion evidence | Result |
| --- | --- | --- | --- |
| AC1 | Create/edit setup has a centered Saqz wordmark and centered heading. | `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/ui/setup/GroupSetupScreenTest.kt:74` — `assertTrue(abs(screen.center.x - wordmark.center.x) < 1f)`; line 75 repeats the exact center comparison for `title`. | PASS |
| AC2 / GRP-REG-01 | The three existing sections remain one keyboard-safe scroll flow, aligned as full-width card surfaces with clearer separation. | Lines 58, 60, and 62 call `performScrollTo()` for all three sections; lines 64-65 assert equal widths. Lines 80-82 and 114-129 assert required/optional fields and defaults remain discoverable and `assertEquals(emptyList(), intents)` proves opening the flow has no side effect. Production retains one `imePadding().verticalScroll(...)` container and wraps every section with the same full-width `SaqzCard`, shadow, border, and increased spacing. | PASS |
| AC3 | Selected modality, composition, and level choices expose selected semantics without changing their click behavior. | Lines 97-99 assert selected/unselected modality and selected composition. Line 100 asserts `onNodeWithText("Todos os níveis").assertIsSelected()`; line 101 asserts `onNodeWithText("Iniciante").assertIsNotSelected()`. Lines 387-392 retain scroll reachability, minimum target, and click-action assertions for every initial choice. | PASS |
| AC4 / GRP-UI-01 | At 320x420 and fontScale 2, all initial choices and the final CTA remain reachable with at least 48 dp click targets. | Lines 370-373 set `Density(1f, 2f)` and `Box(Modifier.size(320.dp, 420.dp))`; lines 378-385 include submit and assert `performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction()`; lines 387-392 apply 48 dp/click assertions to every initial modality, composition, and level choice. | PASS |
| AC5 | No unrelated photo/input/navigation work is included. | Exact name-status inspection of both authored diffs shows only the files listed under Scope. `bed23c9` is explicitly excluded. | PASS |

**Spec anchors:** `.specs/features/group-management/spec.md:337-339`
(`GRP-REG-01`) and lines 421-423 (`GRP-UI-01`).

## Test adequacy

- AC1 asserts the specified horizontal centers, not mere node existence.
- AC2 asserts scroll membership, aligned widths, discoverability, and absence of
  registration side effects; card depth remains a visual-review item below.
- AC3 now asserts both sides of level selection directly in addition to
  modality and composition. The level-specific mutant confirms these assertions
  discriminate the previously uncovered behavior.
- AC4 exactly encodes the requested viewport, font scale, semantic scroll
  reachability, 48 dp minimum, and click action.
- The clean targeted `GroupSetupScreenTest` run executed 45 tests with 0
  failures, 0 errors, and 0 skipped.

## Command evidence

| Command | Result |
| --- | --- |
| `rtk mobile/gradlew -p mobile :features:groups:iosSimulatorArm64Test --tests 'br.com.saqz.groups.ui.setup.GroupSetupScreenTest' --console=plain -q` at `e71075b` | PASS — exit 0; XML reports 45 tests, 0 failures, 0 errors, 0 skipped. |
| `rtk git diff --check 358ddf8^ 358ddf8` | PASS — exit 0, no output. |
| `rtk git diff --check e71075b^ e71075b` | PASS — exit 0, no output. |
| Prior independent isolated original gate: `rtk mobile/gradlew -p mobile :features:groups:compileAndroidMain :features:groups:allTests --console=plain` at `358ddf8` | PASS — exit 0; `BUILD SUCCESSFUL in 50s`; 69 actionable Gradle tasks. |
| Prior original safety gates: `rtk scripts/check-credentials`, `rtk scripts/check-scope` | PASS — `ok - credential safety`; `ok - mobile-first scope`. |

The author also reported the full Groups gate green after `e71075b`; the 45-test
screen-class run above is this verifier's independent post-fix confirmation.

## Discrimination sensor

| Mutation | Target | Result |
| --- | --- | --- |
| Change only the level `ChoiceField` call from `selected = form.level` to `selected = null`; modality and composition paths remain unchanged. | `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/setup/GroupSetupScreen.kt:156` | KILLED — the exact `selected profile choice exposes its state` filter completed 1 test with 1 failure; Gradle exited 1. |

**Sensor depth:** lightweight, level-specific.
**Result:** 1/1 mutant killed. The mutation was discarded and the isolated
source was restored to `e71075b`.

## Code quality

- Minimum, surgical fix: one test fixture change and two exact assertions.
- No production behavior was changed by `e71075b`.
- No authored test was skipped, deleted, or weakened.
- Every added assertion maps directly to AC3 and the prior verifier gap.
- Both authored diffs pass whitespace validation and remain within declared
  scope.

## Residual manual visual gap

No screenshot/golden or device UAT was performed. Automated geometry proves
horizontal centering, equal card widths, scrolling, selected semantics, and
minimum targets; the perceived quality of the 2 dp shadow, hairline border, and
increased spacing/depth still merits a brief visual review on Android and iOS.

## Summary

**Overall:** Ready — PASS.
**Spec-anchored check:** 5/5 ACs matched.
**Independent post-fix test:** 45/45 passed.
**Sensor:** 1/1 level-specific mutant killed.
**Ranked gaps:** none; only the non-blocking manual visual review remains.
