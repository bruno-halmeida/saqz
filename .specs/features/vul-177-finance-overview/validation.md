# VUL-177 Independent Validation

## Verifier

Fresh-eyes validation was performed after implementation and before delivery. The verifier
reviewed the specification, the complete diff against `origin/main`, the changed-file boundary,
and the tests without changing the production branch.

## Evidence

- `git diff --check origin/main...HEAD`: passed.
- Diff size: 1,519 changed lines against `origin/main`, below the 2,000-line ticket limit.
- No changed file contains `statement`/`extrato`; existing statement surfaces were not modified.
- Bruno changes are four new files under `bruno/Finance/Overview/`; no existing request was edited.
- No changed test is disabled or ignored.
- `:bootstrap:compileKotlin` passed after Spring bean wiring.

## Acceptance traceability

| Requirement | Evidence | Result |
| --- | --- | --- |
| OWNER/ADMIN authorization and empty 200 | `ADMINISTERED_GROUPS` SQL CTE; integration fixtures cover owner, admin, athlete, unknown actor and soft-delete | PASS |
| Accumulated balance formula | Independent correlated sums for PAID, active IN and active OUT; mixed-fixture assertions | PASS |
| Period totals and pending charges | Month/year boundaries and event/date filters; integration assertions for August and empty September | PASS |
| Structured group health | `pendingMonthlyCount` and `hasBillingConfigured` response fields; integration assertions | PASS |
| Recent activity | Paid monthly events plus active IN/OUT launches, merged and limited to five; integration assertions | PASS |
| Default/month/year filters | Clock/fuso unit tests and controller response tests | PASS |
| Invalid filters | Portuguese `fieldErrors` for invalid and mutually exclusive parameters | PASS |

## Test gates

1. `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ./gradlew :features:groups:test` — PASS.
2. `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock && export TESTCONTAINERS_RYUK_DISABLED=true && cd backend && ./gradlew :features:groups:integrationTest` — PASS, 10m36s.

The integration command was run as the full suite, not only the new test.

## Mutation sensor

In a disposable detached worktree, the first accumulated-balance `IN` predicate was mutated to
`OUT`. The focused `JdbcFinanceOverviewRepositoryIntegrationTest` then failed all three tests,
including the balance assertion. The disposable worktree was removed; the production branch was
unchanged and remains green.
