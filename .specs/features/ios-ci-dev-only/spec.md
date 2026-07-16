# iOS CI Dev-only

**Status:** Verified
**Date:** 2026-07-16

## Goal

Reduce the iOS GitHub Actions wall time by running only the complete `SaqzDev`
unit + UI suite remotely, while retaining the existing `SaqzProd` Release build
and unit tests in the complete local gate.

## Acceptance Criteria

1. **IOSCI-01** — WHEN `scripts/check-ios --dev-only` runs THEN exactly one
   `xcodebuild` invocation SHALL execute the complete `SaqzDev` test action.
2. **IOSCI-02** — WHEN `scripts/check-ios` runs without arguments THEN the
   existing three invocations SHALL remain: SaqzDev full tests, SaqzProd
   Release build and SaqzProd Release unit tests.
3. **IOSCI-03** — WHEN the Dev suite fails or discovers zero tests THEN
   `--dev-only` SHALL fail; unknown arguments SHALL fail before `xcodebuild`.
4. **IOSCI-04** — WHEN initialization CI runs THEN its iOS job SHALL invoke
   `scripts/check-ios --dev-only`, remain blocking in the aggregate and require
   no production credential.
5. **IOSCI-05** — WHEN developers inspect the README THEN it SHALL distinguish
   the complete local iOS gate from the Dev-only CI policy and state that Prod
   CI is deferred.

## Out of Scope

- Removing or weakening local SaqzProd validation.
- Adding production credentials, signing or deployment.
- Designing the future production CI workflow.
- Changing Android, backend or landing gates.

## Verification

- `tests/scripts/check-ios.test.sh`
- `tests/scripts/check-ci.test.sh`
- `tests/scripts/check-readme.test.sh`
- `scripts/test-scripts`

## Verification Invariants

- **V1** — Signal-cleanup tests SHALL deliver `SIGINT`/`SIGTERM` to the
  `check-all` owner PID; child processes must terminate through the owner's
  cleanup trap. Process-group signaling is reserved for timeout recovery.

## Bug Log

| ID | Date | Root cause | Invariant |
| --- | --- | --- | --- |
| B1 | 2026-07-16 | The signal harness notified parent and child traps concurrently, intermittently exceeding its fixed timeout. | V1 |
