# Athlete Management Validation

**Date:** 2026-07-23
**Branch:** `feature/athlete-management` (worktree `.worktrees/athlete-management`)
**Spec:** `.specs/features/athlete-management/spec.md`

## Delivery Summary

All 15 tasks delivered. T01–T04 via subagent implement/review/fix cycles
(each reviewed and approved); T05–T06 recovered from stopped-agent WIP and
completed solo; T07–T15 implemented solo by the controller with per-step
compile verification and a single consolidated test gate at the end (per
user directive to defer test validation to final delivery).

## Gate Evidence (final run)

| Gate | Command | Result |
| --- | --- | --- |
| Backend full | `:features:access:test :features:groups:test :features:access:integrationTest :features:groups:integrationTest :bootstrap:test :architecture-tests:test` | BUILD SUCCESSFUL (8m38s) |
| Mobile common | `:features:access:allTests :features:groups:allTests :compose-app:allTests` | BUILD SUCCESSFUL (final run 37s after fixes) |
| API contract | `scripts/check-bruno` | ok — Bruno covers explicit backend routes |
| Credentials | `scripts/check-credentials` | ok — credential safety |
| Scope | `scripts/check-scope` | ok — mobile-first scope |

Not run in this delivery (deferred, flagged for follow-up before release):
Android instrumented (`connectedDevDebugAndroidTest`), iOS native
(`check-ios`), aggregate `check-gradle`/`check-all`, and end-to-end native
journeys (signup → completion → redeem → onboarding). The plan's T14 journey
coverage is the main open verification gap.

## Requirement Status

| Requirement | Status | Notes |
| --- | --- | --- |
| ATH-01 phone completion | Delivered | V8 migration, PhoneNumber (mobile-only, leading 9), session `phone`/`phoneRequired`, `PATCH /api/session/profile`, CompletingPhone gate + screen |
| ATH-02 invite entry + position onboarding | Delivered | AVULSO column default on redeem; skippable position sheet post-redeem (session-scoped dismissal) |
| ATH-03 roster | Delivered | `ListAthletes` (accent-insensitive search, AND filters, financial derivation with DESCONHECIDO degradation), roster screen in PEOPLE destination |
| ATH-04 manage/remove | Delivered | UpdateAthlete/RemoveAthlete (owner-immutable, idempotent), edit sheet + removal dialog naming history preservation |
| ATH-05 snapshots | Delivered | `member_display_name` NOT NULL + backfill on attendance/charges; production write paths populate via subquery; FK repoints (game_attendance + attendance_events) |
| ATH-06 own profile | Delivered | `GET /api/athletes/me` + profile section in MORE (per-group cards) |

## Regression Backprop

- B1/V1 (Flyway global sequence) recorded in spec.md during delivery.
- B2 | 2026-07-23 — T03's NOT NULL snapshot migration initially missed that
  two production write paths (attendance SAVE, charge inserts) and the
  `attendance_events` FK also had to change; caught by full-suite failures
  and task review respectively. Lesson: a NOT NULL/FK migration's "done"
  includes every producer of the affected rows.
- B3 | 2026-07-23 — `singleOf(::KtorSessionGateway)` broke when the gateway
  gained a defaulted `Json` constructor param (Koin resolves all params);
  replaced with an explicit `single { }` builder.
- B4 | 2026-07-23 — An unconditional `koinViewModel()` call added under
  GROUP_CONTEXT broke Compose tests that render without Koin; hoisted
  behind a nullable slot (same pattern as GameDetail's hoisted state).

## T14 Android Journey Follow-up (VUL-5, 2026-07-24)

Android end-to-end journey coverage added in `AndroidAthleteJourneyTest`
(real MainActivity + Koin graph + Ktor gateways against a scripted loopback
API): phone-completion gate once and never again (ATH-01), pending invite
surviving the gate and redeeming only after completion, position onboarding
shown once / skip without request / no re-show on re-redeem (ATH-02), and
roster entry points + management controls per role with pt-BR labels only
(ATH-03/04). `connectedDevDebugAndroidTest` green locally (API 30 emulator,
60/60). iOS journeys remain open (VUL-6); aggregate closeout is VUL-7.

- B5 | 2026-07-24 — The journeys exposed three navigation defects, fixed at
  the root with regression tests: `GroupSelectionIntent.Select`'s
  stale-membership guard silently dropped server-confirmed joins (redeem,
  creation) → new `SelectJoined` intent; `NavigationSession.selectedTab`
  was a plain var, so tab switches only rendered after an unrelated
  recomposition → snapshot-backed; `clearGroupScope` ran after
  `reconcileGroupSelection`, resetting the freshly reconciled GroupHome
  root back to Selector → scope clears before the root reconcile in one
  effect. Lesson: state-machine wiring validated only through fakes needs
  at least one journey through the real composition.
