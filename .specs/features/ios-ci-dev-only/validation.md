# iOS CI Dev-only — Validation

**Date:** 2026-07-16
**Overall:** PASS
**Verifier:** standalone fresh-eyes fallback; no sub-agent tool was available

## Acceptance Criteria

| Requirement | Evidence | Result |
| --- | --- | --- |
| IOSCI-01 | `tests/scripts/check-ios.test.sh` `devOnlyRunsExactlyDev` asserts one SaqzDev full-test invocation and no SaqzProd/unit-only filter. | PASS |
| IOSCI-02 | `exactThreeInvocations`, `prodBuildIsRelease` and `prodOnlyRunsUnit` retain the three default local invocations. | PASS |
| IOSCI-03 | `unknownModeFailsBeforeBuild`, `zeroTestsFails` and `devOnlyFailurePropagates` assert strict failures. | PASS |
| IOSCI-04 | `tests/scripts/check-ci.test.sh` requires exact `scripts/check-ios --dev-only`; the unchanged aggregate still blocks on `ios-gate`. | PASS |
| IOSCI-05 | `tests/scripts/check-readme.test.sh` requires both the Dev-only command and deferred Prod CI policy. | PASS |

## Verification

- iOS contract: 15/15 PASS.
- CI contract: 36/36 PASS.
- README contract: 21/21 PASS.
- `scripts/test-scripts`: PASS.
- Real `scripts/check-ios --dev-only`: PASS, 12 unit + 8 UI tests, 0 failed,
  120.20 seconds local wall time.
- `sh -n` and `git diff --check`: PASS.

## Backprop Signal

The first aggregate run exposed the recurring signal-harness race: notifying
the entire process group caused parent and child traps to compete. V1/B1 were
recorded, the harness now signals only the aggregate owner, and the signal
suite passed three parallel repetitions plus the full script aggregate.

## Lessons

The repository has no `scripts/lessons.py`; degraded local bookkeeping records
the grounded candidate in `.specs/LESSONS.md`.

## Summary

The remote iOS gate now runs only SaqzDev. The complete local gate still runs
SaqzDev, SaqzProd Release build and SaqzProd Release unit tests. Production CI
remains explicitly deferred.

