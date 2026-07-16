# Fundação Mobile de Interface e Design System — Validation

**Date:** 2026-07-15  
**Spec:** `.specs/features/frontend-design-system-foundation/spec.md`  
**Diff range:** `8f54dfc..5e16f63`  
**Commits:** 43 atomic commits, `b3e4fbe` through `5e16f63`  
**Verifier:** standalone fresh-eyes fallback; no sub-agent tool was available  
**Overall:** PASS

## Task Completion

| Phase | Tasks | Status | Evidence |
| --- | --- | --- | --- |
| 0 | T01–T07 | PASS | Scope, KMP modules, resource sentinels and typed navigation committed and green. |
| 1–2 | T08–T19 | PASS | State, formatters, exact theme contract, resources, brand and accessibility preferences committed and green. |
| 3–4 | T20–T28 | PASS | Complete `Saqz*` component inventory committed; design-system suite reached 130 tests. |
| 5–6 | T29–T37 | PASS | Home, catalog, shell, native launchers and platform accessibility committed and green on Android/iOS. |
| 7 | T38–T43 | PASS | Complete local/CI gates, workspace isolation, stable API 35 promotion and README handoff committed and green. |

All T01–T43 checkboxes are complete in `tasks.md`; `git log --reverse
8f54dfc..5e16f63` contains exactly 43 feature commits.

## Spec-Anchored Acceptance Criteria

Evidence-or-zero was applied to all 60 requirements. Every row names the exact
retained test or gate assertion used by the verifier.

| Requirement | `file:line` + assertion expression | Result |
| --- | --- | --- |
| VIS-01 | `mobile/core/common/build.gradle.kts:15-17`, `core/design-system/build.gradle.kts:16-18`, `compose-app/build.gradle.kts:17-21` declare Android/iOS KMP targets; `tests/scripts/check-scope.test.sh:118` proves a Wasm target is rejected. | PASS |
| VIS-02 | `SaqzColorTokensTest.kt:59-72` asserts exactly 28 tokens and every spec value. | PASS |
| VIS-03 | `tests/scripts/check-workspace-isolation.test.sh:148-176` runs all mobile suites from a mobile-only scratch and rejects sibling workspace access. | PASS |
| VIS-04 | `InterPackagingTest.kt:49-91` asserts four pinned local fonts/weights; `SaqzFontFamilyTest.kt:15-18` asserts iOS `FontFamily.Default`. | PASS |
| VIS-05 | `tests/scripts/check-mobile-brand-assets.test.sh:30-54,87-108` asserts source paths/viewboxes/colors and kills asset drift; `LaunchContractTests.swift:39-58` asserts source hash/path reuse. | PASS |
| VIS-06 | `SaqzThemeTest.kt:63-106` asserts Material colors, typography and shapes derive from Saqz semantic registries. | PASS |
| VIS-07 | `SaqzAppShellTest.kt:53-121` asserts safe inset, IME, landscape and scroll reachability; `MainActivityTest.kt:40-63` repeats platform behavior. | PASS |
| VIS-08 | `SaqzMetricsTest.kt:40-64`, `SaqzTypographyTest.kt:63-92` assert exact registries; `SaqzCatalogScreenTest.kt:50-80` asserts every color/type/metric is rendered. | PASS |
| CMP-01 | `SaqzCatalogScreenTest.kt:81-110` asserts every component variant and async state appears in the catalog. | PASS |
| CMP-02 | `SaqzButtonTest.kt:60-176` asserts 3:1 focus, press timing/scale, one commit, disabled/loading semantics, width/name and reduced-motion feedback. | PASS |
| CMP-03 | `SaqzCardTest.kt:29-95` and `SaqzListItemTest.kt:28-115` distinguish static/interactive variants, 48dp targets, pre-release feedback and one activation. | PASS |
| CMP-04 | `SaqzBadgeTest.kt:24-80` asserts six variants, exact foreground pairs and AA contrast, including `accent/on-accent`. | PASS |
| CMP-05 | `SaqzDialogTest.kt:43-164` and `SaqzBottomSheetTest.kt:64-113` assert blocked background, title/action/close semantics and dismissibility. | PASS |
| CMP-06 | `tests/scripts/check-scope.test.sh:114-115` mutates an `AisButton` and requires the scope gate to fail. | PASS |
| CMP-07 | `SaqzInputTest.kt:81-186` asserts associated/announced error and password value, selection, focus and 48dp toggle preservation. | PASS |
| CMP-08 | `SaqzDialogTest.kt:168-207` and `SaqzBottomSheetTest.kt:122-147` assert scrolling, visible actions and bottom anchoring; dismissibility tests cover back/outside behavior. | PASS |
| NAV-01 | `SaqzHomeScreenTest.kt:20-59` asserts wordmark, `Saqz`, `Explorar componentes`, reading order and one activation. | PASS |
| NAV-02 | `SaqzBottomNavTest.kt:41-92` asserts 56dp + inset, 48dp items, selected semantics, non-color signal and order. | PASS |
| NAV-03 | `SaqzCatalogScreenTest.kt:114-134` asserts owner/athlete fixtures are non-interactive and production exposes no profile/session labels. | PASS |
| NAV-04 | `SaqzNavHostTest.kt:53-106` asserts back, idempotent reselection, no duplicates, restoration and exactly two destinations. | PASS |
| NAV-05 | `SaqzDestinationTest.kt:27` and `SaqzAppShellTest.kt:188` assert exactly Home/Catalog; `check-scope.test.sh:80-84,123-126` rejects auth/OpenAPI UI. | PASS |
| LAUNCH-01 | `AndroidLaunchContractTest.kt:78-96` asserts background/local symbol; `LaunchContractTests.swift:23-58` asserts static iOS assets and provenance. | PASS |
| LAUNCH-02 | `AndroidColdStartTest.kt:21-32` asserts direct Home and no Compose splash; `SaqzIOSUITests.swift:31-38` asserts iOS cold start reaches Home. | PASS |
| LAUNCH-03 | `SaqzAppEnvironmentTest.kt:27-47` asserts startup Loading/Empty/Error render through `SaqzStateHost`. | PASS |
| LAUNCH-04 | `AndroidLaunchContractTest.kt:61-76` asserts legacy and v31 themes; `LaunchContractTests.swift:23-28` asserts `UILaunchScreen`; API 35 gate ran green three times. | PASS |
| STATE-01 | `SaqzUiStateTest.kt:11-37` asserts singleton Loading, typed Content, distinct Empty/Error and exhaustive four-state `when`. | PASS |
| STATE-02 | `SaqzStateHostTest.kt:23-89` asserts all four states and replaceable slots. | PASS |
| STATE-03 | `SaqzStateHostTest.kt:60-75` clicks retry once and asserts count `1`. | PASS |
| STATE-04 | `SaqzStateHost.kt:42-47` centers full-slot state content; `SaqzAppShellTest.kt:53-121` asserts nav/inset/IME clearance. | PASS |
| STATE-05 | `SaqzStateHostTest.kt:93-100` asserts 220ms normal transition and zero reduced-motion translation. | PASS |
| FMT-01 | `SaqzDateTimeFormatterTest.kt:18-31` uses an injected IANA zone for date/time/date-time. | PASS |
| FMT-02 | `SaqzDateTimeFormatterTest.kt:18-21` asserts `15/05/2025`. | PASS |
| FMT-03 | `SaqzDateTimeFormatterTest.kt:23-26` asserts `20:00`. | PASS |
| FMT-04 | `SaqzDateTimeFormatterTest.kt:28-31` asserts `15/05/2025 20:00`. | PASS |
| FMT-05 | `SaqzCurrencyFormatterTest.kt:12-30` asserts zero, negative zero, positive and negative canonical BRL outputs. | PASS |
| FMT-06 | `SaqzDateTimeFormatterTest.kt:66` plus pure formatter suites assert deterministic offline behavior. | PASS |
| FMT-07 | `SaqzDateTimeFormatterTest.kt:48-51` rejects invalid zones; `SaqzCurrencyFormatterTest.kt:37-55` asserts safe limits and explicit overflow failure. | PASS |
| L10N-01 | `ResourceCatalogTest.kt:37-42` and app `ResourceCatalogTest.kt:34-39` assert all visible labels remain pt-BR. | PASS |
| L10N-02 | Design/app `ResourceCatalogTest.kt:32-58` resolve labels through Compose resources; isolated mobile gate proves workspace ownership. | PASS |
| L10N-03 | `SaqzDateTimeFormatterTest.kt:54-63` and `SaqzCurrencyFormatterTest.kt:57-60` assert device locale does not alter outputs. | PASS |
| L10N-04 | Design/app `ResourceCatalogTest.kt:45-58` asserts visible label and accessible name use the same resource. | PASS |
| A11Y-01 | `SaqzColorTokensTest.kt:75-108`, `SaqzButtonTest.kt:60-65` and `SaqzBadgeTest.kt:33-80` assert AA text pairs and 3:1 control/focus contrast. | PASS |
| A11Y-02 | `SaqzColorTokensTest.kt:95-108` asserts `accent/on-accent` and decorative lines are not control indicators; `SaqzBottomNavTest.kt:75-81` asserts a non-color selected signal. | PASS |
| A11Y-03 | `SaqzBottomNavTest.kt:58-92`, `SaqzInputTest.kt:57-68,186-198` and `SaqzListItemTest.kt:39-78` assert names, roles, state/order and 48dp targets. | PASS |
| A11Y-04 | `SaqzButtonTest.kt:68-109`, `SaqzCardTest.kt:77-103` and `SaqzListItemTest.kt:95-129` assert press feedback precedes one release commit. | PASS |
| A11Y-05 | `SaqzAccessibilityPreferencesTest.kt:21-78` asserts reduced motion/transparency behavior; `AccessibilityPreferencesObserverTests.swift:24-85` asserts the two-boolean native adapter. | PASS |
| A11Y-06 | `ModernAndroidBehaviorTest.kt:48-55`, `SaqzCatalogScreenTest.kt:139-150` and `AccessibilityUITests.swift:14-28` assert 2.0/largest text reflow and single scaling. | PASS |
| A11Y-07 | `SaqzDialogTest.kt:43-107`, `SaqzBottomNavTest.kt:68-92`, `SaqzStateHostTest.kt:103-112` and `SaqzInputTest.kt:57-104` assert coherent semantics/order. | PASS |
| GATE-01 | `tests/scripts/check-scope.test.sh:76-142` applies and kills persistence, auth, OpenAPI, navigation, web target, legacy naming and workspace-coupling mutations. | PASS |
| GATE-02 | `tests/scripts/check-workspace-isolation.test.sh:148-176` executes all KMP/Android/SaqzDev/SaqzProd suites from mobile-only scratch. | PASS |
| GATE-03 | `scripts/check-gradle:27-49` fails missing device/zero tests; `check-gradle.test.sh:138-196` proves every suite is mandatory; isolation gate rejects zero XCTest. | PASS |
| GATE-04 | `tests/scripts/check-ci.test.sh:108-233` asserts four blocking jobs, result bindings and ignored manual checklist; aggregate run `29463568482` passed; this report is PASS. | PASS |
| EDGE-01 | `SaqzAppShellTest.kt:188-197` asserts production nav has only `Início` and `Componentes`. | PASS |
| EDGE-02 | `SaqzDateTimeFormatterTest.kt:33-46` asserts previous/next-day timezone boundaries. | PASS |
| EDGE-03 | `SaqzCurrencyFormatterTest.kt:27-30,42-45` asserts the minus sign precedes `R$`. | PASS |
| EDGE-04 | `ResourceSentinelTest.kt:17-35`, `ResourcePreflightTest.kt:17-35` and brand missing-output mutation assert required local resources fail rather than fetch. | PASS |
| EDGE-05 | `MainActivityTest.kt:63-74`, `ModernAndroidBehaviorTest.kt:57-71` and `FirebaseAuthBootstrapTest.kt:58-69` assert rotation restoration/closed overlay and one Firebase initialization. | PASS |
| EDGE-06 | `SaqzNavHostTest.kt:62-92` asserts repeated reselection leaves no duplicate destination. | PASS |
| EDGE-07 | Dialog `:38-39,129-164` and sheet `:59-60,104-121` tests assert non-dismissible back/outside are ignored and explicit close works. | PASS |
| EDGE-08 | `SaqzBottomNavTest.kt:115-130` and `SaqzAccessibilityPreferencesTest.kt:68-78` assert a continuous 1dp hairline in translucent/opaque chrome. | PASS |

**Spec-anchored status:** 60/60 requirements verified; 0 uncovered
criteria; 0 spec-precision gaps.

## Gate Check

| Gate | Result | Counts / notes |
| --- | --- | --- |
| `scripts/test-scripts` | PASS (exit 0) | 142 named contract/isolation cases: check-all 8, CI 36, credentials 10, Gradle 19, iOS 12, landing 3, brand 6, README 20, scope 22, two isolation cases and emulator fixture 4. |
| `scripts/check-all` on `5e16f63` | PASS (exit 0) | Backend gate, KMP `24 + 130 + 52`, Android unit 8, connected 21, SaqzDev 12 unit + 8 UI, SaqzProd Release build + 12 unit, landing; 0 failed/skipped tests. |
| API 35 blocking evidence | PASS | Runs `29449093693`, `29463089984`, `29463088994` passed on `bf5cc79`; post-promotion aggregate `29463568482` passed all four jobs. |
| Structural | PASS | `git diff --check` clean; feature diff has 129 files, 7,382 insertions and 95 deletions; unrelated `.gitignore` modification was not staged or changed. |

## Discrimination Sensor

All mutations were applied only in detached worktree
`/tmp/saqz-verifier-5e16f63`, restored there, then the clean worktree was
removed. The implementation tree was never mutated.

| Mutation | Behavioral fault | Covering test and observed kill | Result |
| --- | --- | --- | --- |
| M1 | Renamed `android-api35-gate` back to a non-blocking probe identity. | `tests/scripts/check-ci.test.sh` exited 1 with `missing workflow contract: api35 gate job`. | KILLED |
| M2 | Removed the minus sign from negative BRL output. | `:core:common:allTests` exited 1; `canonicalNegative` and `negativeSafeLimit` failed. | KILLED |
| M3 | Changed normal press scale from `0.95` to `1.0`. | `:core:design-system:allTests` exited 1; button/motion contract tests failed. | KILLED |

**Sensor result:** 3/3 behavior mutations killed — PASS.

## Decision Consistency

The verifier found one documentation-only drift: AD-016 still named
`google_atd`, while T41/T42 and the three successful runs fixed API 35 to
`google_apis`. AD-016 is now superseded by AD-023, which records the exact
evidence-backed tuple. This was not a spec deviation: LAUNCH-04 requires API
31+ coverage, and that behavior is green and blocking.

## Code Quality

| Principle | Status | Evidence |
| --- | --- | --- |
| Atomic scope | PASS | 43 tasks produced 43 focused commits; no unrelated `.gitignore` change was staged. |
| Existing architecture | PASS | KMP modules, Compose resources, typed navigation, shell gates and native adapters follow repository boundaries. |
| Test integrity | PASS | No test was deleted, skipped or weakened; suites grew to 24 common, 130 design-system, 52 app, 8 Android unit, 21 Android connected and 20 distinct iOS tests. |
| Platform parity | PASS | Shared behavior is tested on iOS simulator/KMP and Android; native launch/accessibility edges have platform tests. |
| Scope control | PASS | No product web, auth UI, persistence, network client or business placeholder entered the feature. |

## Fix Plans / Ranked Gaps

None. No failed AC, surviving mutant, spec-precision gap, gate failure or
`SPEC_DEVIATION` was found.

The slow iOS CI wall time is an optimization opportunity, not a validation
gap. It should be handled in a separate task by parallelizing Dev/Prod gates
and then evaluating build-for-testing/test-without-building and caching.

## Lessons Self-Check

Clean PASS: no grounded lesson signal exists, so no lesson was recorded, as
required by `references/lessons.md`.

## Summary

**Overall:** Ready — PASS.  
**Spec-anchored check:** 60/60, 0 precision gaps.  
**Gate:** script contracts + full local native gate + API 35 aggregate passed.  
**Sensor:** 3/3 mutations killed.
