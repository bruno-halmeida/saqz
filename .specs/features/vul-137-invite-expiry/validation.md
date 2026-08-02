# VUL-137 Independent Validation

## Review

- The expiry comparison remains in `RedeemInvite` and uses its injected `Clock`; expired invitations use the existing `InvalidOrExpired` result and call `recordInvalidAttempt`.
- The metadata use case checks group existence and `MANAGE_INVITE` before reading invite metadata, so the controller preserves the 404/403 authorization boundary.
- The metadata response intentionally contains no invite URL or digest. Expired metadata strips creation fields while retaining `expiresAt`.
- Existing membership upsert behavior is preserved and covered for existing ADMIN and ATHLETE roles.

## Mutation sensor

In a detached scratch worktree, the expiration comparator in `RedeemInvite` was changed from `now.isAfter(invite.expiresAt)` to `now.isBefore(invite.expiresAt)`. The focused `RedeemInviteTest` then failed with 16 failures out of 26 tests, including the expired-invite and non-expired-invite cases. The scratch worktree was removed after the check.

## Gates

- JDK 21 `:features:groups:test :bootstrap:test`: passed with the Colima Docker socket and `TESTCONTAINERS_RYUK_DISABLED=true`.
- JDK 21 `:features:groups:integrationTest`: passed in 7m48s with the Colima Docker socket and `TESTCONTAINERS_RYUK_DISABLED=true`.
- `./gradlew detekt`: unavailable; this repository has no `detekt` task in the root project or subprojects.
- `git diff --check`: passed.
