# Retirada do Frontend Angular — Validation

**Date:** 2026-07-15  
**Spec:** `.specs/features/retire-angular-frontend/spec.md`  
**Diff range:** `3bdc759..8f54dfc`  
**Commits:** `d17c472`, `d77afbd`, `0f0a8e0`, `4ceaaf3`, `8f54dfc`  
**Verifier:** independent sub-agent (author != verifier)  
**Overall:** PASS

## Task Completion

| Task | Commit | Status | Evidence |
| --- | --- | --- | --- |
| T1 | `d17c472` | PASS | `frontend/` absent on disk; `git ls-files frontend` empty. |
| T2 | `d77afbd` | PASS | Scope, workspace-isolation and `ARCH-08` contracts passed. |
| T3 | `0f0a8e0` | PASS | Local aggregate contract passed all 7 cases. |
| T4 | `4ceaaf3` | PASS | CI/evaluator contract passed all 21 cases. |
| T5 | `8f54dfc` | PASS | README, script aggregate and retained-tooling contracts passed. |

All five tasks are marked complete in `tasks.md`; no partial or blocked task was found.

## Spec-Anchored Acceptance Criteria

Evidence-or-zero was applied to each requirement. The assertion expression below is the exact retained test assertion or the exact verifier assertion used for ignored specification artifacts.

| Requirement | Spec-defined outcome | `file:line` + assertion expression | Result |
| --- | --- | --- | --- |
| RET-01 | No tracked path exists below `frontend/`. | `tests/scripts/check-scope.test.sh:41-49,78-79` — stage `frontend/README.md`, require `scripts/check-scope` to return non-zero, then `grep -q 'retired frontend workspace'`; direct verifier assertions: `git ls-files frontend` = empty and `stat frontend` = expected ENOENT. | PASS |
| RET-02 | The complete local gate runs Gradle/Android, iOS and landing, in that order, with no Angular/npm step. | `tests/scripts/check-all.test.sh:85-90` — exact `diff -u` against `check-gradle\ncheck-ios\ncheck-landing`; `:214-225` — success, fail-fast and signal cases total exactly 7. Production calls are `scripts/check-all:54-56`. | PASS |
| RET-03 | CI has exactly `gradle-gate`, `ios-gate`, `landing-gate` and aggregate; evaluator takes exactly three results and rejects every failure/cancellation. | `tests/scripts/check-ci.test.sh:27-44,46-64,110-126,140-170` — require the three native jobs, reject Angular markers, assert exact `needs`, reject arity 2/4, and assert non-zero for `failure`/`cancelled` in each position. `scripts/evaluate-ci-gates:4-13` asserts arity 3 and `result == success`. Verifier `awk` over `.github/workflows/initialization-gate.yml:13-92` returned only the four specified job keys. | PASS |
| RET-04 | Clean scope passes; any tracked `frontend/` path fails with the specific diagnostic; mobile auth UI, OpenAPI, unapproved navigation, backend-domain/application and workspace coupling remain prohibited. | `tests/scripts/check-scope.test.sh:41-52,55-84` — one clean case plus explicit failing mutations for auth, OpenAPI, navigation, coupling, domain/application and `frontend/`; `scripts/check-scope:53-86` contains the corresponding reject assertions. `tests/scripts/check-workspace-isolation.test.sh:87-131` and `BackendArchitectureTest.kt:111-157` assert `ARCH-08` rejects four sibling-coupling forms. | PASS |
| RET-05 | README, `.gitignore` and script contracts describe backend/mobile/landing; operational Angular references are absent; Node/npm remain only for Firebase CLI/session tooling. | `tests/scripts/check-readme.test.sh:22-51,85-92` — require landing/native gates/Node/npm/Firebase and reject `Angular|frontend/|npm --prefix frontend|Web app ID`; `README.md:3-18,73-90` describes the mobile-first surfaces and future-web decision; `.gitignore:16-19` labels Node tooling for Firebase. Repository search found Angular/frontend references only in negative guard tests and the scope diagnostic. | PASS |
| RET-06 | Active interface `spec.md`, `context.md`, and `design.md` define only Android/iOS and defer any future product web app. | `.specs/features/frontend-design-system-foundation/spec.md:9-17,26-31,43-50`; `context.md:10-26`; `design.md:11-21,44`. Six verifier assertions (`rg -q` for Android+iOS plus future-web-out-of-scope in each document) all exited 0. | PASS |
| RET-07 | Angular-prescriptive decisions remain in history but are superseded by the active mobile-first decision. | `.specs/STATE.md:13-19,45-59,93-99,109-115` — AD-002/006/007/012/014 each retain their historical text and assert `Status: superseded by AD-017`; `.specs/STATE.md:133-139` asserts AD-017 is active and defines backend + Android/iOS + static landing, with future web requiring a new spec/decision. Six multiline `rg -U -q` assertions exited 0. | PASS |
| RET-08 | Credential, scope, script, backend/mobile, iOS and landing gates stay green; landing and Pages workflow have no feature diff. | `scripts/check-gradle:11-35` invokes credentials, scope, all backend suites, KMP/unit and connected Android; `scripts/test-scripts:14-24` invokes all contract surfaces. `tests/scripts/check-landing.test.sh:84-92` kills landing/Pages drift. `git diff --quiet 3bdc759..8f54dfc -- landing-page .github/workflows/deploy-pages.yml` exited 0, and both full gates passed. | PASS |

**Spec-anchored status:** 8/8 requirements match their precise outcomes; 0 uncovered criteria; 0 spec-precision gaps.

## Edge Cases

| Edge case | Evidence | Result |
| --- | --- | --- |
| A lone tracked `frontend/README.md` must fail. | `tests/scripts/check-scope.test.sh:78-79`; the scope mutant sensor also proved this case discriminates. | PASS |
| `cancelled` from any native job must fail the aggregate. | `tests/scripts/check-ci.test.sh:156-170`; all three positions passed, and the evaluator mutant was killed at the first cancellation. | PASS |
| Removing Node/npm documentation must fail because Firebase tooling still needs it. | `tests/scripts/check-readme.test.sh:27-34`; `README.md:14-18`; actual uses remain in `firebase/session-fixture`. | PASS |
| Backend HTTP/landing uses of “web” must not be mistaken for the removed product workspace. | Repository-wide search found only Spring HTTP `web`, the static landing, the explicit future-web decision, and negative Angular guards; all scope/README contracts passed. | PASS |

## Gate Check

| Gate | Result | Counts / notes |
| --- | --- | --- |
| `scripts/test-scripts` | PASS (exit 0) | 78 named contract/isolation cases, 0 failed: check-all 7, CI 21, credentials 10, Gradle contract 6, iOS contract 2, landing 3, README 13, scope 10, backend isolation 1, mobile isolation 1, emulator fixture 4. Both isolation builds passed. |
| `scripts/check-all` | PASS (exit 0) | Gradle/backend 49 tests, mobile KMP/unit/instrumented 4 tests, iOS XCTest/XCUITest 3 tests; 56 native test cases total, 0 failed, 0 skipped. Landing preservation passed. |
| Structural/diff checks | PASS | `git diff --check` clean; no tracked/on-disk `frontend/`; no diff in `landing-page/` or `.github/workflows/deploy-pages.yml`; tracked worktree clean before report. |

### Test-count integrity

- Script aggregate before feature: 44 executed contract cases.
- Script aggregate after feature: 78 executed contract cases.
- Delta: +34 (CI and README contracts were added to `scripts/test-scripts`; scope gained the retired-path case while check-all dropped the obsolete Angular failure case).
- Native/product test cases before feature: 61, including 5 Angular unit tests.
- Native/product test cases after feature: 56.
- Delta: -5, exactly the intentionally removed Angular-only tests (`app.spec.ts` 3; `local-firebase.spec.ts` 2). No retained test was deleted or weakened.
- Skipped tests: none. Gradle `SKIPPED` configuration-check tasks and `NO-SOURCE` tasks are not skipped test cases; all generated test reports record `skipped=0`.

## Discrimination Sensor

All mutations were applied only in detached worktree `/tmp/saqz-retire-angular-verifier`, restored there, and the worktree was verified clean and removed. The real implementation tree was never mutated.

| Mutation | Scratch file:line | Behavioral fault | Covering test and observed kill | Result |
| --- | --- | --- | --- | --- |
| M1 | `scripts/check-scope:53` | Removed the retired-`frontend/` path rejection. | `tests/scripts/check-scope.test.sh` exited 1 with `expected scope gate failure for retired-frontend-workspace`. | KILLED |
| M2 | `scripts/evaluate-ci-gates:10` | Rejected only literal `failure`, thereby accepting `cancelled`. | `tests/scripts/check-ci.test.sh` exited 1 with `aggregate accepted gradle cancellation`. | KILLED |
| M3 | `scripts/check-all:55-56` | Swapped landing before iOS. | `tests/scripts/check-all.test.sh` exited 1 with an exact invocation-order diff. | KILLED |

**Sensor depth:** lightweight, three highest-risk behavior mutations.  
**Sensor result:** 3/3 killed — PASS.

## Code Quality

| Principle | Status | Evidence |
| --- | --- | --- |
| Minimum/surgical change | PASS | Diff is limited to deleting `frontend/` and removing/updating its gates, boundaries and documentation. |
| No scope creep | PASS | Backend/mobile behavior and landing/Pages content are unchanged. |
| Existing patterns/style | PASS | Existing shell fail-fast helpers, contract-test fixtures and Gradle/Xcode ownership remain intact. |
| Test integrity | PASS | Retained assertions are exact; deleted tests belong only to the deleted Angular product surface. |
| Spec-anchored outcomes | PASS | All eight RET outcomes are asserted above with file/line evidence. |
| Per-layer coverage | PASS | Structural, shell-contract, Kotlin architecture, isolated workspace, native build and landing layers all passed. |
| No unclaimed tests in feature diff | PASS | Every changed test maps to RET-02/03/04/05/08 or to its task done-when contract. |
| Project guidance | PASS | `RTK.md`, `tasks.md`, `validate.md` and `coding-principles.md` followed; all shell commands were prefixed with `rtk`. |

## Requirement Traceability

| Requirement | Previous | Validation |
| --- | --- | --- |
| RET-01 | Implemented | Verified |
| RET-02 | Implemented | Verified |
| RET-03 | Implemented | Verified |
| RET-04 | Implemented | Verified |
| RET-05 | Implemented | Verified |
| RET-06 | Implemented | Verified |
| RET-07 | Implemented | Verified |
| RET-08 | Implemented | Verified |

## Fix Plans / Ranked Gaps

None. No failed AC, surviving mutant, spec-precision gap, gate failure or `SPEC_DEVIATION` was found.

## Lessons Self-Check

Clean PASS: no grounded signal exists, so no lesson was recorded, as required by `references/lessons.md`.

## Summary

**Overall:** Ready — PASS.  
**Spec-anchored check:** 8/8, 0 precision gaps.  
**Gate:** contracts + full native build + landing passed.  
**Sensor:** 3/3 mutations killed.  
**Landing/Pages:** unchanged across `3bdc759..8f54dfc`.
