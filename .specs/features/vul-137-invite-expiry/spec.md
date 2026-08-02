# VUL-137 — Invite expiry and metadata

## Requirements

- **VUL-137-01**: Add `group_invites.expires_at` as a non-null `timestamptz`, backfilling existing rows with `created_at + interval '7 days'` in one Flyway migration.
- **VUL-137-02**: Rotate writes and returns an expiration exactly seven days after the injected clock instant.
- **VUL-137-03**: Redeem treats an invite with `expires_at < now` as `InvalidOrExpired` and records an invalid attempt; a non-expired invite succeeds.
- **VUL-137-04**: GET invite metadata is admin-only, hides non-members as 404 and athletes as 403, never returns the invite link, and returns active/expired/no-invite metadata according to the contract.
- **VUL-137-05**: Redeem is idempotent for existing OWNER, ADMIN, and ATHLETE memberships and returns the current role.

## Explicit boundaries

- Do not implement the VUL-139 approval/status changes.
- Keep digest-only token persistence.
- Use an injected `java.time.Clock`; do not call `Instant.now()` directly.
