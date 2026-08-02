# VUL-140 — Preview do convite para não-membro

## Acceptance criteria

- AC1: `POST /api/invites/preview` accepts `{ "code": "<43-char Base64URL>" }` without `Authorization`.
- AC2: a valid non-expired invite returns the complete card fields `groupName`, `city`, `composition`, `level`, `memberCount`, `regularSlots`, `inviterName`, `entryRequiresApproval`, `expiresAt` and `nextGame`, without `groupId`.
- AC3: malformed or unknown codes return `404` with `code=INVITE_INVALID`.
- AC4: a soft-deleted group is externally indistinguishable from an invalid invite.
- AC5: an expired invite returns `410` with `code=INVITE_EXPIRED` and `expiredAt` when the expiry column is available; this branch has no expiry column and returns `expiresAt=null`.
- AC6: authenticated invalid attempts use `invite_redemption_limits` with the redeem window (10 attempts/10 minutes); valid cards do not increment it.
- AC7: anonymous invalid attempts use an in-memory per-IP window of 30 attempts/10 minutes and return `429` with `retryAfterSeconds` after the limit.
- AC8: the preview path is the only additional unauthenticated `/api` path; another protected endpoint without a bearer returns `401`.

## Explicit branch constraints

- No migration is added.
- `entryRequiresApproval` is `false` until VUL-139 supplies the column.
- `expiresAt` is `null` until VUL-137 supplies the column; the expiry-specific branch remains represented in the application contract for the follow-up merge.
