# Execution tasks

## T1 — Application preview and anonymous limiter

- Files: `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/invite/preview/*`, matching unit tests.
- Done when: valid, malformed, unknown, deleted, expiry outcome, and next-game/no-next-game paths have exact result assertions; authenticated valid requests do not increment attempts; anonymous limit is 30/10min.
- Gate: `:features:groups:test`.

## T2 — JDBC preview repository

- Files: `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/invite/JdbcInvitePreviewRepository.kt`, matching integration test.
- Done when: SQL maps profile, memberships, inviter, regular slots, next game, deleted group, and existing invite limit rows without exposing an id in the response model.
- Gate: `:features:groups:integrationTest`.

## T3 — HTTP contract and optional security

- Files: preview controller, `ErrorCode`, safe problem writer/model/handler, bearer filter, security configuration, bootstrap wiring.
- Done when: the exact preview JSON and error shapes are serializable; only the preview path is permit-all with optional bearer verification; redeem code is untouched.
- Gate: `:bootstrap:test` plus compile/lint.

## T4 — Endpoint integration and security regression coverage

- Files: bootstrap preview endpoint integration test and any narrowly scoped security test changes.
- Done when: anonymous 200, anonymous 429, malformed/unknown 404, and another protected endpoint 401 are asserted.
- Gate: bootstrap test.

## T5 — Feature validation and delivery

- Done when: JDK 21 unit tests, integration tests, detekt, independent evidence review, commit, push, ready PR, and Linear review update are complete.
- Gate: requested full local gates.
