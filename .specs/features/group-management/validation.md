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

---

# Group Photo and Controlled Input Validation

**Date:** 2026-07-20
**Verdict:** PASS
**Implementation commit:** `bed23c9d157cd9f1c818098340b334ed2555985d`
**Route-test commit:** `164da9596d2e2c42d9f154cb35dc4d3280a56b4d`
**Verifier:** independent sub-agent (author != verifier)

## Scope and root cause

- B103/V52: the group-registration UI exposed camera and gallery actions, but
  the production route did not supply the native photo runtime dependencies and
  discarded the post-create upload effect. The route now owns the real photo
  coordinator, forwards both source intents, renders the selected preview, and
  binds/uploads the encoded photo only after the group is created.
- B104/V53: `SetupInput` recreated `TextFieldValue` from plain text after each
  ViewModel recomposition, resetting the cursor/selection and causing subsequent
  keystrokes to be inserted before earlier ones. It now retains the local
  `TextFieldValue`, including selection and composition while parent text is
  unchanged.

**Spec anchors:** `.specs/features/group-management/spec.md:929-936`
(B103/B104) and lines `1056-1063` (V52/V53).

## Acceptance evidence

| Invariant | Exact evidence | Result |
| --- | --- | --- |
| V52 — production camera/gallery actions reach native selection and the post-create upload effect is consumed | `AuthenticatedAccessRoot.kt:148-170,206-218,258-265` composes the real coordinator, handles `UploadPhoto`, forwards source intents, and uses the runtime preview port. `AuthenticatedAccessRootTest.kt:221-248` mounts `AuthenticatedAccessRoute`, independently observes gallery and camera calls, submits creation, and asserts the uploaded `groupId`, `etag`, and encoded bytes. | PASS |
| V53 — controlled input preserves cursor/selection/composition across ViewModel recomposition | `GroupSetupScreen.kt:376-392` retains `TextFieldValue` locally and only rebuilds it for a real external text change. `GroupSetupScreenTest.kt:217-243` forces recomposition after every character and asserts the final value is exactly `"abc"`. | PASS |

The independent verifier reported no Blocker, Major, or Minor findings. It also
confirmed that removing either source-intent forwarding or the
`UploadPhoto -> BindTarget -> Upload` chain makes the new route test fail.

## Command evidence

| Command | Result |
| --- | --- |
| `rtk mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test --tests 'br.com.saqz.composeapp.navigation.AuthenticatedAccessRootTest' --console=plain` | PASS — `BUILD SUCCESSFUL` in 26s; 24 tests, 0 failures/errors/skips. |
| `rtk mobile/gradlew -p mobile :features:groups:allTests :compose-app:allTests --console=plain` | PASS — `BUILD SUCCESSFUL` in 45s; 103 tasks. |
| Independent verifier: `rtk mobile/gradlew -p mobile :compose-app:allTests --console=plain` | PASS — `BUILD SUCCESSFUL` in 37s; 90 tasks. |
| `rtk mobile/gradlew -p mobile :android-app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.saqz.androidapp.AndroidGroupPhotoAdapterTest --console=plain` | PASS — 5 Android native photo-adapter tests. |
| `rtk scripts/check-ios` | PASS — dev 106 unit + 13 UI tests, release build, and prod 106 tests. |
| `rtk scripts/check-credentials` | PASS — `ok - credential safety`. |
| `rtk scripts/check-scope` | PASS — `ok - mobile-first scope`. |
| `rtk git diff --check` | PASS — no whitespace errors. |

## Aggregate Android note

The feature-specific Android adapter tests pass. The broader
`scripts/check-gradle` gate still stops in pre-existing instrumented-test
harness failures outside B103/B104: an API 30 `ApplicationInfoFlags.of`
`NoSuchMethodError` and FirebaseAuth access before FirebaseApp initialization in
`SignedOutAccessRule`. No production or feature assertion for these fixes
failed.

## Summary

**Overall:** Ready — PASS. Camera and gallery are connected through the
production route on Android and iOS, the photo upload is sequenced after group
creation, previews are available, and controlled typing remains in keyboard
order across recompositions.

---

# Novo Grupo Mobile Redesign Validation

**Date:** 2026-07-20
**Verdict:** PASS
**Implementation commits:** `b41162d`, `65a2f80`
**Integration-test follow-up:** `1e50f48`
**Verifier:** independent sub-agent (author != verifier)

## Acceptance evidence

| Requirement | Evidence | Result |
| --- | --- | --- |
| Four-section mobile hierarchy and fixed create action | `GroupSetupScreen.kt:130-344` keeps one keyboard-safe scrolling body, renders identity, sports, routine, and billing surfaces, and places `StickySubmit` outside the scroll body. `GroupSetupScreenTest.kt:44-70,326-352` checks equal section widths, copy, sticky disabled/loading states, 360x800 at font scale 2, and 44 dp controls. | PASS |
| Friendly editable defaults | `GroupSetupViewModel.kt:403-409` seeds court volleyball, mixed composition, all levels, 12 players, and 360 minutes while leaving photo, venue, slots, and fees absent. `GroupSetupViewModelTest.kt:49-56` checks the complete initial form; screen assertions are at `GroupSetupScreenTest.kt:73-83`. | PASS |
| Centered prominent photo placeholder | `GroupPhotoEditor.kt:117-149,275-319` centers a 112 dp placeholder inside a 128 dp control, uses the muted gray camera icon, and overlays a 30 dp outlined plus inside a 44 dp target at the lower-right. `GroupSetupScreenTest.kt:85-105` checks centering, minimum size, absence of `SG`, overlay position, hidden source actions, and the progressive sheet. Android screenshot `/tmp/saqz-new-group-final.png` confirms the intended visual hierarchy. | PASS |
| Saqz-blue button outlines and selected state | `GroupSetupScreen.kt:511-585,673-691,799-890` applies the primary outline to segmented choices, selectors, routine actions, selection-sheet rows, and every enabled `SetupButton`; active choices also use blue fill, white text, selected semantics, and a check icon. `GroupPhotoEditor.kt:130-145,218-271` applies the same outline to the add control and photo-sheet actions. | PASS |
| Progressive selectors and complete registration capability | `GroupSetupScreen.kt:197-317,346-390,603-827` retains modality, composition, optional/custom level and court style, venue name/address/court, repeat weekday/time/duration, capacity, human-readable confirmation presets/custom value, per-game fee, monthly switch/fee/due day, and friendly timezone fallback. Tests at `GroupSetupScreenTest.kt:118-277` exercise conditional and nested paths. | PASS |
| Numeric currency behavior | `GroupSetupScreen.kt:743-768,970-993` requests `KeyboardType.Decimal`, strips non-numeric content, limits two decimal positions, preserves the pt-BR mask, and emits integer cents. `GroupSetupScreenTest.kt:257-262,362-376` checks input-to-cents conversion, rejection, sanitization, and formatting. | PASS |
| Submit, loading, draft, and error behavior | `GroupSetupScreen.kt:319-343,829-843` shows errors only from state, keeps recovery actions, disables submit until name is nonblank, and renders button loading feedback. Existing ViewModel draft/idempotency tests remain green. | PASS |
| Post-create next action | `GroupContextScreens.kt:94-114` renders `Convidar a galera` as the primary authorized action; `GroupContextScreensTest.kt:24-31` proves visibility and `OpenInvite` dispatch. | PASS |
| Production route matches the progressive UI | `AuthenticatedAccessRootTest.kt:223-234,247-257`, amended by `1e50f48`, checks current section names and reaches camera/gallery only after opening the photo sheet, satisfying V55. | PASS |

## Command evidence

| Command | Result |
| --- | --- |
| `rtk mobile/gradlew -p mobile :features:groups:iosSimulatorArm64Test --tests 'br.com.saqz.groups.ui.setup.GroupSetupScreenTest' --tests 'br.com.saqz.groups.ui.GroupContextScreensTest' --tests 'br.com.saqz.groups.presentation.setup.GroupSetupViewModelTest' --console=plain` | PASS — build successful; reports contain 36 setup-screen, 13 context-screen, and 22 setup-ViewModel tests with zero failures/errors/skips. |
| `rtk mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test --tests 'br.com.saqz.composeapp.navigation.AuthenticatedAccessRootTest' --console=plain` | PASS — build successful in 23s; 25 tests, zero failures/errors/skips. |
| `rtk mobile/gradlew -p mobile :android-app:assembleDevDebug --console=plain` | PASS — installable dev APK assembled successfully and reviewed on the 360 x 800-equivalent Android viewport. |
| `JAVA_HOME=<JDK21> rtk scripts/check-gradle` | PARTIAL — backend, scope, credentials, KMP/iOS feature suites, and `compose-app:allTests` passed. The connected Android phase failed outside this change on three configured devices: Firebase was not initialized in the test process, API 30 lacked `ApplicationInfoFlags.of`, and a physical-device activity stayed stopped. No group-setup assertion failed; the already-failing multi-device run was stopped after the failures were conclusive. |
| `rtk git diff --check b41162d^ b41162d`; same check for `65a2f80` and `1e50f48` | PASS — no whitespace errors. |

## Discrimination sensor

In detached worktree `/private/tmp/saqz-verify-65a2f80`, changed only the compact
photo call from `photoIconFallback = true` to `false`. The focused command
`rtk mobile/gradlew -p mobile :features:groups:iosSimulatorArm64Test --tests
'br.com.saqz.groups.ui.setup.GroupSetupScreenTest.photo stays compact and opens
source actions in a sheet' --console=plain` failed exactly 1/1 test. The mutant
was therefore killed: the test detects regression from the photo icon back to
the `SG` initials.

## Residual risk

Compose UI tests cannot introspect the platform keyboard layout itself. The
numeric-decimal request is verified by direct production-code inspection while
sanitization, masking, and emitted cents are automated. No blocker, major, or
minor finding remains. The aggregate Android harness failures above remain an
independent repository-infrastructure risk and are not caused by this feature.

## Summary

**Overall:** Ready — PASS. GRP-UI-03 and the latest photo, outline, gray-icon,
numeric-value, progressive-flow, and invitation requirements are implemented
and covered by feature and production-route tests.

---

# Selected-Group Private Photo Validation

**Date:** 2026-07-21
**Verdict:** PASS
**Implementation commits:** `117e262`, `4c17e5c`
**Verifier:** independent sub-agent (author != verifier)

## Scope and spec anchors

- B110/V56 cover the selected-group home summary card only.
- The backend private photo endpoint contract stayed unchanged.
- The authenticated route now owns loading, reconciliation, session-private
  caching, and fallback rendering for the selected group photo.

**Spec anchors:** `.specs/features/group-management/spec.md:186-193`,
`.specs/features/group-management/spec.md:980-983`,
`.specs/features/group-management/spec.md:1110-1114`.

## Spec-anchored acceptance evidence

| Criterion | Exact evidence | Result |
| --- | --- | --- |
| V56 — selected-group home reads the photo only in matching authenticated group context | `compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/AuthenticatedAccessRoot.kt:221-238` gates loading to the selected home/group context. `compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/AuthenticatedAccessRootTest.kt:274-301` asserts the matching-group read, while lines `235-267` assert no read during the create flow. | PASS |
| V56 — cache bytes are revalidated with the photo ETag and reused only after `304` | `features/groups/src/commonMain/kotlin/br/com/saqz/groups/photo/GroupPhotoCoordinator.kt:97-135` issues generation-scoped loads and keeps cached bytes hidden until `304`. `features/groups/src/commonMain/kotlin/br/com/saqz/groups/photo/GroupPhotoApi.kt:65-78` sends `If-None-Match`. `features/groups/src/commonTest/kotlin/br/com/saqz/groups/photo/GroupPhotoCoordinatorTest.kt:54-65` asserts the cache-hit `304` path. | PASS |
| V56 — `200` replaces cache atomically; `404`, transient failure, invalid cache, logout, and membership loss do not expose stale bytes | `features/groups/src/commonTest/kotlin/br/com/saqz/groups/photo/GroupPhotoCoordinatorTest.kt:38-51` covers cache miss/`200`; lines `68-90` cover `404` and transient failure without stale bytes; lines `132-145,338-356` cover logout and membership loss cleanup. `android-app/src/commonMain/kotlin/br/com/saqz/androidapp/photo/CoilGroupPhotoCache.kt:12-115` provides bounded atomic cache replacement and invalid-handle rejection; `android-app/src/commonTest/kotlin/br/com/saqz/androidapp/photo/CoilGroupPhotoCacheTest.kt:27-89` verifies replacement, limits, and cleanup. | PASS |
| V56 — the summary slot stays stable and renders skeleton, cropped current photo, or initials fallback without previous-group flash | `compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/GroupsNavigationHost.kt:485-541` renders the 104 dp slot, shimmer/static skeleton, crop, and initials fallback. `compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/GroupsNavigationHostTest.kt:105-169` covers skeleton, valid image, decode failure fallback, stable dimensions, and no previous-group preview. `features/groups/src/commonMain/kotlin/br/com/saqz/groups/photo/GroupPhotoPreview.kt:42-59` uses `ContentScale.Crop`; `features/groups/src/commonTest/kotlin/br/com/saqz/groups/photo/GroupPhotoPreviewTest.kt:20-39` covers real decode and invalid bytes. | PASS |
| User-facing UAT — the MBJR group photo renders in the selected-group home | User confirmation in session after debugging the MBJR group photo. | PASS |

The independent verifier reported no Blocker, Major, or Minor findings against
`117e262^..4c17e5c`.

## Command evidence

| Command | Result |
| --- | --- |
| `rtk ./gradlew :features:groups:allTests` | PASS — focused Groups suite green. |
| `rtk ./gradlew :compose-app:allTests` | PASS — focused Compose app suite green. |
| `rtk ./gradlew :android-app:compileDevDebugKotlin --rerun-tasks --no-build-cache` | PASS — Android compile contract green. |
| `rtk ./gradlew :android-app:assembleDevDebug :android-app:testDevDebugUnitTest` | PASS — Android assembly and unit tests green. |
| `rtk scripts/check-ios` | PASS — iOS contract green, including 106 tests. |
| `rtk scripts/check-credentials` | PASS — `ok - credential safety`. |
| `rtk scripts/check-scope` | PASS — `ok - mobile-first scope`. |
| Independent verifier rerun: `rtk ./gradlew :features:groups:allTests :compose-app:allTests --no-daemon` | PASS — `BUILD SUCCESSFUL` in 53s; 104 tasks, 16 executed and 88 up-to-date. |
| `rtk git diff --check 117e262^ 4c17e5c` | PASS — no whitespace errors. |

## Aggregate gate note

`rtk scripts/check-gradle` remains intentionally deferred to the end of the
broader spec, per the agreed workflow for this feature stream. The prior local
attempt was inconclusive because unrelated backend integration tests cycle
Postgres Testcontainers slowly and were not needed to validate B110/V56.

## Discrimination sensor status

No mutation sensor was executed in this verifier pass because the independent
agent was constrained to read-only inspection. Evidence is therefore explicit
zero rather than inferred coverage.

## Summary

**Overall:** Ready — PASS. The selected-group home now reads the authenticated
private photo in the correct group context, revalidates the session-private
cache by photo ETag, and renders only the current skeleton, current cropped
photo, or initials fallback.
