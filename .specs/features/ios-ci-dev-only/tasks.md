# iOS CI Dev-only — Tasks

**Status:** Verified — validation PASS
**Date:** 2026-07-16

| Task | Change | Verification | Status |
| --- | --- | --- | --- |
| T01 | Add `--dev-only`, preserve default full gate, point CI to Dev-only and document deferred Prod CI | iOS/CI/README contract tests + script aggregate | Done |

## Fixed Contract

- No argument: three existing xcodebuild invocations.
- `--dev-only`: one SaqzDev full-test invocation.
- Any other argument: usage failure before simulator/build work.
- GitHub Actions: Dev-only and still blocking.
- Local `scripts/check-all`: unchanged and therefore still includes Prod.
