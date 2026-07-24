# Mobile Navigation Architecture Validation

**Date**: 2026-07-23
**Spec**: `.specs/features/mobile-navigation-architecture/spec.md`
**Diff range**: `012295f^..HEAD` (HEAD = `be59042`, T01–T26, ~30 commits)
**Verifier**: independent sub-agent (author ≠ verifier), read-only over the real tree; discrimination mutations applied in scratch state (per-file `git checkout` revert) only.

**Verdict: PASS — 1 Major fix required (broken G1D fixture), 1 Minor spec-precision note (RESTORE-05).**

The feature is spec-conformant and behavior-correct: every sampled acceptance criterion maps to a spec-anchored assertion, 7/7 discrimination mutants were killed, and the mandatory aggregate `rtk scripts/check-all` is green. The one Major gap is a test-infrastructure defect (a required focused gate exits non-zero on a stale count assertion, while all its real cases pass); it does not affect product behavior.

---

## Task Completion

All 26 tasks are marked done in `tasks.md` with per-task evidence. Independent spot-checks below confirm the claimed artifacts exist and behave as described.

| Task | Status | Verifier note |
| ---- | ------ | ------------- |
| T01 baseline | ✅ | Post-Wave-2 counts pinned; G0 green. |
| T02 module/guards | ✅ | `:navigation` created (android+iosArm64+iosSimulatorArm64), guard script present. **G1D fixture red — see gaps.** |
| T03–T05 route keys | ✅ | Access(7)/Groups(15)/Finance(2) keys; `GameDetail` `init { require(gameId.isNotBlank()) }`. |
| T06 serialization | ✅ | 25-key explicit polymorphic config, reflection-free. |
| T07–T10 session/policies | ✅ | Session, reconciliation, pruning, restore/clear all verified. |
| T11–T15 adapters/factories | ✅ | Per-source adapters; verified via effect-handler + host tests. |
| T16 entry lifecycle | ✅ | `navigationEntryId` is a Bundle-safe namespaced String (platform-bug fix confirmed). |
| T17–T19 hosts | ✅ | Access/Groups/Finance installers + chrome + back chains. |
| T20 effect handlers | ✅ | Exhaustive, duplicate-safe, deep-link Games predecessor. |
| T21 product display | ✅ | Exactly one `NavDisplay` call site; topology enforced by tests. |
| T22 app-local nav3 | ✅ | Legacy `navigation-compose:2.9.2` removed; G7D `ok`. |
| T23 composition root | ✅ | Integrated; check-scope allowlist fix; check-all green. |
| T24 remove access legacy | ✅ | `AuthenticatedAccessRoot.kt` deleted; forbidden symbols absent from production (git grep). |
| T25 remove groups legacy | ✅ | `GroupsNavigationViewModel/State/Effect`, `GroupsRouteHost`, `GroupsDestinationContent` absent from production. |
| T26 final integration | ✅ | `check-all` green (re-run by Verifier); Android instrumented + iOS XCUITest lifecycle suites present and passing. |

---

## Spec-Anchored Acceptance Criteria

Evidence-or-zero. `file:line` paths are relative to `mobile/` unless noted. All test paths under `navigation/src/commonTest/kotlin/br/com/saqz/navigation/` abbreviated as `nav/…`.

### P1: `:navigation` module boundaries (MODNAV)

| Criterion | Spec outcome | Evidence | Result |
| --- | --- | --- | --- |
| MODNAV-01 hosts live in `:navigation` | Access/Groups/Finance/Product hosts + NavDisplay in module | `navigation/.../{access/AccessNavigationHost,groups/GroupsNavigationHost,finance/FinanceNavigationHost,ProductNavigationHost}.kt` exist; `ProductNavigationHostTest`, `GroupsNavigationHostTest:68` install & inventory entries | ✅ |
| MODNAV-02 no feature→`:navigation`/Nav3-UI | Feature build/imports rejected | `scripts/check-mobile-navigation-dependencies:50-60`; fixture cases 2–5 (feature-depends / imports-pkg / declares-nav3ui / imports-nav3ui) all reject | ✅ |
| MODNAV-03 compose-app sole framework export | No `:navigation` export/leak | guard `:64-80`; fixture cases 6–7; check-all iOS framework build green | ✅ |
| MODNAV-05 reflection-free polymorphic serialization | Every leaf registered explicitly | `nav/serialization/NavigationSavedStateConfigurationTest.kt:60-63` `assertEquals(25, allKeys.size)` + `assertEquals(key, roundTrip(key))`; `:76` unregistered key `assertFailsWith<SerializationException>` | ✅ |
| MODNAV-06 catalog pins + legacy nav2 removed | `navigation-compose:2.9.2` absent | `scripts/check-mobile-navigation-dependencies` `--require-no-legacy` + fixture cases 8 & 13; T22 G7D `ok` | ✅ |

### P1: AccessNavigationHost (ACCESSNAV)

| Criterion | Spec outcome | Evidence | Result |
| --- | --- | --- | --- |
| ACCESSNAV-01 seven serializable routes | 7 distinct entries | `nav/access/AccessNavigationHostTest.kt:44-60` distinct-entry inventory; serialization round-trip covers 7 Access keys | ✅ |
| ACCESSNAV-02 Registration/PasswordReset back→Login | Returns to Login | `AccessNavigationHostTest.kt:89-99` `assertEquals(listOf(AccessRoute.Login), stack)`; `:72-87` push-on-Login | ✅ |
| ACCESSNAV-03 idempotent reconcile | No duplicate entries | `AccessNavigationHostTest.kt:101-110` (`reference === stack`), `:112-133` canonicalization | ✅ |
| ACCESSNAV-04 Ready switches mode | Not pushed onto access stack | `AccessNavigationHostTest.kt:135-138` `assertFalse(isAccessSession(Ready))`; `ProductNavigationHostTest.kt:64-71` access mode shows only access | ✅ |
| ACCESSNAV-05 AccessPage/Destination(Stack) removed | Absent from production | `git grep` of production `.kt` → none; T24 guard `check-…-dependencies:103-105`; fixture cases 9–10; `AuthenticatedAccessRoot.kt` deleted | ✅ |

### P1: GroupsNavigationHost (GROUPNAV)

| Criterion | Spec outcome | Evidence | Result |
| --- | --- | --- | --- |
| GROUPNAV-01 AppHome + 15 keys | 16 distinct entries | `nav/groups/GroupsNavigationHostTest.kt:67-82` `assertEquals(16, entries…toSet().size)` | ✅ |
| GROUPNAV-02 Games→GameDetail back→Games | Returns to Games not GroupHome | `GroupsNavigationHostTest.kt:138-151`; `nav/effect/NavigationEffectHandlersTest.kt:36-48` | ✅ |
| GROUPNAV-03 TopBar==system back | Same handler | `NavigationSessionTest.kt:107-122` `assertEquals(topBarResult, systemBackResult)` + equal stacks | ✅ |
| GROUPNAV-04 single-membership no back | TopBar back hidden | `GroupsNavigationHostTest.kt:104-112` `assertFalse(groupsBackVisible(1))`; `:170-182` scaffold hides back | ✅ |
| GROUPNAV-05 showAppHome/handleGroupsIntent removed | Absent | `git grep` production → none; guard T24/T25 | ✅ |
| GROUPNAV-06 selection-state reconcile | Transient replaced in place | `nav/NavigationSessionGroupReconciliationTest.kt:52-134` (NoGroup/Selector/Loading/LoadError/Selected + flapping + no-op-past-root) | ✅ |

### P1: FinanceNavigationHost (FINNAV)

| Criterion | Spec outcome | Evidence | Result |
| --- | --- | --- | --- |
| FINNAV-01 Finance/OwnCharges routes | 2 distinct entries, RoutePage placeholder | `nav/finance/FinanceNavigationHostTest.kt:42-52`, `:83-98` placeholder-only content | ✅ |
| FINNAV-02 Finance/Expense screens disconnected | No real finance screen wired | Structural: `FinanceNavigationHost` takes a `content` slot, references no `FinanceScreen`/`ExpenseScreen`/gateway; `:83-98` composes only supplied placeholder | ⚠️ structural (no direct "no FinanceScreen composed" assertion; enforced by absence + dependency guard) |
| FINNAV-03 back reveals parent predecessor | GroupHome or More | `FinanceNavigationHostTest.kt:60-69` `assertEquals(GroupsRoute.GroupHome, …last())`; `:71-81` More | ✅ |

### P1: Back behavior & tab stacks (BACK / TAB)

| Criterion | Spec outcome | Evidence | Result |
| --- | --- | --- | --- |
| BACK-01 GameDetail back→Games | Not GroupHome | `NavigationSessionTest.kt:72-83`; `GroupsNavigationHostTest.kt:138-151` | ✅ |
| BACK-02 TopBar==system back handler | Same key removed | `NavigationSessionTest.kt:107-122`; `ProductNavigationHostTest.kt:95-107` shared back pops once | ✅ |
| BACK-03 no back when no previous | TopBar back hidden | `NavigationSessionTest.kt:124-135` `canGoBack`; `GroupsNavigationHostTest.kt:170-182` | ✅ |
| BACK-04 non-home root→Início; Início root→platform | Select Início / return false | `NavigationSessionTest.kt:85-105` (`assertEquals(HOME,selectedTab)` / `assertFalse(handled)`); `ProductNavigationHostTest.kt:82-93` | ✅ |
| TAB-01 independent per-tab stacks | Own stack each | `NavigationSessionTest.kt:48-62` | ✅ |
| TAB-02 reselect no duplicate | No duplicated root | `NavigationSessionTest.kt:41-46` `assertEquals(2,…size)`, `:64-70` | ✅ |
| TAB-03 restored tab stack | Restored from saved state | `nav/NavigationSessionRestorationTest.kt:34-41` | ✅ |

### P1: Reconciliation / authorization / lifecycle (STATE / AUTHZ / LIFE)

| Criterion | Spec outcome | Evidence | Result |
| --- | --- | --- | --- |
| STATE-01 transient replaces prior | New root replaces | `NavigationSessionGroupReconciliationTest.kt:52-85` | ✅ |
| STATE-02 back never shows Loading/LoadError | Never in back stack | `NavigationSessionGroupReconciliationTest.kt:106-123` `assertFalse(...contains(Loading))` | ✅ |
| STATE-03 idempotent under concurrency | No duplicate entries | `NavigationSessionTest.kt:41-46`; `NavigationSessionGroupReconciliationTest.kt:97-104` | ✅ |
| AUTHZ-01 pop to previous allowed | Prior allowed route | `nav/NavigationSessionPruningTest.kt:34-47` `assertEquals(listOf(groupHome), …)` | ✅ |
| AUTHZ-02 fallback GroupHome / Selector-Setup | Fallbacks applied | `NavigationSessionPruningTest.kt:49-62` & `:80-94` (`assertEquals(listOf(selector),…)`; select GROUPS) | ✅ |
| AUTHZ-03 blank gameId rejected | No screen composed | `features/groups/…/navigation/GroupsRoute.kt` `init { require(gameId.isNotBlank()) }`; `GroupsRouteTest` (T04) blank-id case | ✅ |
| LIFE-01 entry-scoped ViewModel | Distinct owner per stack | `nav/entry/StackScopedEntriesTest.kt:46-75` `assertNotSame(storeA, storeB)` | ✅ |
| LIFE-02 release on definitive pop | Store released | `StackScopedEntriesTest.kt:77-105` (`assertSame` inactive retain), `:107-136` (`assertNotSame` on removal) | ✅ |
| LIFE-03 typed effect, no `:navigation` import | Feature emits typed effect | guard `check-…-dependencies:52-53` (no feature imports `:navigation`); `NavigationEffectHandlers.kt` is the sole translator | ✅ |
| LIFE-04 handler mutates correct stack | Effect→session mutation | `NavigationEffectHandlersTest.kt:36-116` exhaustive matrix | ✅ |
| LIFE-05 stateless screens | state + onIntent only | Structural (Roots wrap screens; hosts pass content slots) | ⚠️ structural |

### P1: Restoration & regression (RESTORE / REG)

| Criterion | Spec outcome | Evidence | Result |
| --- | --- | --- | --- |
| RESTORE-01 restore tab+stacks | Saved state restored | `NavigationSessionRestorationTest.kt:34-41`; serialization round-trip; Android `AndroidAuthenticatedLifecycleTest.kt:88-107,151-160,209-225` recreate() | ✅ |
| RESTORE-02 logout clears auth stacks | All cleared, select Início | `NavigationSessionRestorationTest.kt:58-71` `clearAuthenticated()` | ✅ |
| RESTORE-03 group switch clears group scope | GROUPS/NOTICES/MORE reset | `NavigationSessionRestorationTest.kt:73-99` `clearGroupScope` (+ idempotent, different-group) | ✅ |
| RESTORE-04 unauthorized restored state pruned | AUTHZ applied | `NavigationSessionPruningTest.kt:96-112`; `NavigationSessionRestorationTest.kt:43-56` | ✅ |
| RESTORE-05 iOS cold-relaunch snapshot fallback | Conditional: snapshot IF Nav3 saved state does not survive complete iOS cold relaunch | Fallback deliberately NOT built; T26 evidence asserts Nav3 restoration sufficient. Evidence is `iosSimulatorArm64Test` (in-process Kotlin/Native on iOS runtime) + Android `recreate()`; NOT a packaged-app deep-stack cold-relaunch XCUITest. iOS XCUITest (`IOSAuthenticatedLifecycleUITests.swift`) covers cold launch of the access surface + single-semantics-tree only | ⚠️ Spec-precision (conditional premise assessed via in-process/Android evidence, not packaged deep-stack cold relaunch) |
| REG-01 preserve texts/chrome/permissions | No visual change | `GroupsNavigationHostTest.kt:84-102` chrome classification; migrated compose-app outcomes | ✅ |
| REG-02 tests not removed/weakened | Counts monotonic | tasks.md per-task count rule (156→…; navigation 1→77); no weakening observed | ✅ |
| REG-03 same graph Android/iOS, no platform nav | Common code only | check-all iOS gate (107 tests TEST SUCCEEDED); Android instrumented lifecycle green | ✅ |
| REG-04 focused tests per task | Narrow gates | Per-task evidence in tasks.md | ✅ |
| REG-05 final check-all green | Aggregate passes | Verifier re-ran `rtk scripts/check-all` → `ok - all local gates`, EXIT=0 | ✅ |

**Status**: All sampled ACs matched their spec-defined outcome. 2 ⚠️ spec-precision items (FINNAV-02 & LIFE-05 structural; RESTORE-05 conditional-premise) — flagged, not silently passed.

---

## Discrimination Sensor

Depth: lightweight+ (7 behavior-level mutations across the highest-risk new code). Each applied in scratch state, focused gate run, then reverted with per-file `git checkout`. Tree confirmed clean after each.

| # | File:line | Mutation | Focused gate | Killed? |
| - | --------- | -------- | ------------ | ------- |
| 1 | `NavigationSession.kt:69` | `if (stack.lastOrNull()==key) return` → `if (false) return` (drop duplicate suppression) | `:navigation:iosSimulatorArm64Test` | ✅ `NavigationSessionTest.push does not duplicate the active top key` FAILED |
| 2 | `NavigationSession.kt:84` | `if (selectedTab != HOME)` → `if (false)` (non-home root back does NOT select Início) | `:navigation:iosSimulatorArm64Test` | ✅ 3 FAILED: `NavigationSessionTest.goBack at a non-home tab root selects Inicio`, `ProductNavigationHostTest.non-home tab…root back selects inicio`, reconciliation back test |
| 3 | `authorization/RouteAuthorizationPruning.kt:23` | drop the no-allowed-entry fallback (`if(!isAllowed(last))` → `if(false)`) | `:navigation:iosSimulatorArm64Test` | ✅ `NavigationSessionPruningTest.AUTHZ-02 falls back to GroupHome…` + `RouteAuthorizationPruningTest.installs the fallback…` FAILED |
| 4 | `serialization/NavigationSavedStateConfiguration.kt:52` | remove `GameDetail` polymorphic registration | `:navigation:iosSimulatorArm64Test` | ✅ both `NavigationSavedStateConfigurationTest` round-trip cases FAILED |
| 5 | `entry/StackScopedEntries.kt:19` | `navigationEntryId` drops `stackId` (`"$stackId:$routeIdentity"` → `"$routeIdentity"`) | `:navigation:iosSimulatorArm64Test` | ✅ `StackScopedEntriesTest.equalKeyInTwoStacksGetsDistinctViewModelStores` FAILED |
| 6 | `effect/NavigationEffectHandlers.kt:82` | `OpenInvite` pushes `Settings` instead of `Invite` | `:navigation:iosSimulatorArm64Test` | ✅ `NavigationEffectHandlersTest.group home panel effects push their route…` FAILED |
| 7 | `scripts/check-mobile-navigation-dependencies:104` | remove `AccessPage` from the T24 reject pattern | `tests/scripts/check-mobile-navigation-dependencies.test.sh` | ✅ fixture aborted at `removed-access-page-reintroduced` ("expected … gate failure") |

**Result**: 7/7 killed, 0 survived. (Sensor 5's first attempt was a Verifier tooling error — a `$`-interpolation in the `perl` pattern left the source unmutated, GRADLE_EXIT=0; re-applied via exact edit and confirmed killed. Not a test gap.)

---

## Gate Check

- **Mandatory build gate**: `rtk scripts/check-all` (repo root) → `ok - all local gates`, **EXIT=0**. iOS `** TEST SUCCEEDED **` (107 tests, 0 failures); Gradle/Android + Landing gates green.
- **Focused navigation gate** (`:navigation:iosSimulatorArm64Test`): BUILD SUCCESSFUL baseline; used as the sensor gate (kills confirmed above).
- **Sibling structural fixtures**: G1S `check-scope.test.sh` EXIT=0 (40 cases), G1G `check-gradle.test.sh` EXIT=0 (49 cases) — both pass, confirming the stale-count bug is isolated to the nav-deps fixture.
- **G1D** `tests/scripts/check-mobile-navigation-dependencies.test.sh`: **EXIT=1** — all 13 discrimination cases print `ok 1..13`, then `[ "$count" -eq 9 ]` (line 124) fails because the guard was never bumped from 9→13 when T24/T25 added cases. **Broken required gate (see gaps).**

---

## Code Quality

| Principle | Status |
| --------- | ------ |
| Minimum code / no scope creep | ✅ pure refactor; `SPEC_DEVIATION` in `ProductNavigationHost.kt:33` (hoist session construction to T23) is documented and behavior-preserving |
| Surgical changes, matches patterns | ✅ MVI/adapter/entry patterns consistent with codebase |
| Spec-anchored outcome check | ✅ asserted values match spec outcomes (sampled) |
| Every test maps to a requirement | ✅ tests carry AC IDs in KDoc |
| No unclaimed tests | ✅ |
| Documented guidelines | ✅ AGENTS.md / tlc-spec-driven cadence followed (count rule, focused gates) |

---

## Edge Cases

- [x] Blank `gameId` rejected without composing (AUTHZ-03) — `GroupsRoute` `init { require(...) }`.
- [x] Loading↔LoadError flapping never retains transient (STATE-01/02) — reconciliation test `:87-95`.
- [x] Membership removed while deep → pop to Selector, release VM (AUTHZ-02, LIFE-02) — pruning `:80-94` + `StackScopedEntriesTest`.
- [x] Logout clears all tab stacks (RESTORE-02) — `clearAuthenticated`.
- [x] iOS route-key restoration uses explicit polymorphic serializer, no reflection (MODNAV-05).
- [⚠️] iOS complete cold relaunch of a deep authenticated stack — validated in-process (native iosSimulatorArm64Test) + Android recreate(); no packaged-app deep-stack cold-relaunch XCUITest (RESTORE-05 premise).

---

## Ranked Gaps / Fix Plans

### Fix 1 (Major): G1D fixture exits non-zero on a stale count assertion

- **Root cause**: `tests/scripts/check-mobile-navigation-dependencies.test.sh:124` asserts `[ "$count" -eq 9 ]`, but T24/T25 grew the suite from 9 to 13 cases without updating it. All 13 cases pass (`ok 1..13`), then the script exits 1.
- **Impact**: A documented required gate (G1D, owner T02) is red; `tasks.md` T02/T24/T25 evidence claiming the fixture is "green" (9/11/13-case) is inaccurate. No product/behavior impact — the guard script itself is fully functional (Sensor 7 independently proved its discrimination), and `check-all` does not invoke this fixture, so the aggregate stayed green.
- **Fix task**: change `9` → `13` on line 124 (keep it as a case-count sanity check). Re-run `sh tests/scripts/check-mobile-navigation-dependencies.test.sh` → expect EXIT=0. Consider adding this fixture to a CI meta-test step so it cannot silently regress again.
- **Priority**: Major.

### Fix 2 (Minor / Spec-precision): RESTORE-05 conditional premise not proven by a packaged cold-relaunch XCUITest

- **Root cause**: RESTORE-05 requires a snapshot fallback IF Nav3 saved state does not survive a *complete iOS cold relaunch*. T26 assessed Nav3 restoration as sufficient and skipped the fallback, but the supporting evidence is in-process `iosSimulatorArm64Test` (Kotlin/Native) + Android `recreate()`, not a packaged-app XCUITest that kills and relaunches with a deep authenticated stack.
- **Impact**: Low. The conditional fallback is legitimately unbuilt per engineering judgment; the risk is that "survives complete cold relaunch" is inferred rather than directly demonstrated on a packaged deep stack.
- **Fix task (optional)**: add one XCUITest that authenticates, navigates to a deep group route, terminates and relaunches the app, and asserts the deep route is restored — OR record the decision explicitly in the spec as an accepted limitation.
- **Priority**: Minor.

---

## Requirement Traceability Update

| Requirement | Previous | New |
| ----------- | -------- | --- |
| MODNAV-01..04,06 | Implementing | ✅ Verified |
| MODNAV-05 | Implementing | ✅ Verified |
| ACCESSNAV-01..05 | Implementing | ✅ Verified |
| GROUPNAV-01..06 | Implementing | ✅ Verified |
| FINNAV-01, 03 | Implementing | ✅ Verified |
| FINNAV-02 | Implementing | ✅ Verified (structural) |
| BACK-01..04 / TAB-01..03 | Implementing | ✅ Verified |
| STATE-01..03 / AUTHZ-01..03 | Implementing | ✅ Verified |
| LIFE-01..04 | Implementing | ✅ Verified |
| LIFE-05 | Implementing | ✅ Verified (structural) |
| RESTORE-01..04 | Implementing | ✅ Verified |
| RESTORE-05 | Implementing | ⚠️ Verified with spec-precision note (conditional fallback not built; premise assessed via in-process/Android evidence) |
| REG-01..05 | Implementing | ✅ Verified |

---

## Recommended Lesson (not recorded by Verifier — read-only mandate)

Grounded signal → suggested `scripts/lessons.py add` entry for the orchestrator:
> When a shared meta-test fixture uses a hardcoded trailing `[ "$count" -eq N ]` sanity check, bump N in the SAME commit that adds/removes cases; a later task that extends a fixture owned by an earlier task's gate can silently red it, and if the aggregate (`check-all`) doesn't invoke that fixture, the breakage stays invisible while task evidence falsely reports "green".

---

## Summary

**Overall**: ✅ Ready — with one Major fix (G1D fixture) and one Minor spec-precision note (RESTORE-05).

**Spec-anchored check**: all sampled ACs across 11 requirement families matched spec outcomes; 3 ⚠️ spec-precision items flagged (FINNAV-02, LIFE-05 structural; RESTORE-05 conditional).
**Sensor**: 7/7 mutations killed.
**Gate**: `rtk scripts/check-all` EXIT=0 (green); G1S/G1G green; **G1D red (stale count assertion)**.

**What works**: single app-owned back stack with correct GameDetail→Games back; unified TopBar/system back; independent restored tab stacks; reflection-free polymorphic iOS serialization; entry-scoped ViewModels with distinct-per-stack identity; transient/authorization reconciliation; legacy manual-navigation state fully removed; Android + iOS lifecycle suites pass.

**Issues**: (1) G1D fixture exits 1 on `[ "$count" -eq 9 ]` — fix to 13. (2) RESTORE-05 cold-relaunch premise not proven by a packaged deep-stack XCUITest.

**Next steps**: route Fix 1 (one-line, Major) to an implementer; decide Fix 2 (add XCUITest or record accepted limitation).
