# Athlete Management Context

**Gathered:** 2026-07-23
**Spec:** `.specs/features/athlete-management/spec.md`
**Status:** Confirmed
**ClickUp:** Épico 04 — https://app.clickup.com/t/86ajh0q10

---

## Feature Boundary

The owner manages the athletes of a group, classifying each as monthly member
(`MENSALISTA`) or drop-in (`AVULSO`), while the athlete owns their account data
and completes their own registration. Athletes always enter a group through the
existing invite deep link; there is no manual athlete creation by the owner.
The athlete views their own profile and per-group history. Games and finance
records keep a snapshot of the athlete's name so removal never erases history.

---

## Implementation Decisions

### Data Model — extend, do not add tables

- The athlete IS the existing `group_memberships` row (one user × one group).
  It gains `position`, `membership_type` (`MENSALISTA`/`AVULSO`), and `active`.
- `access_users` gains `phone`. Phone is account-level data; position, type,
  and activity are per-group data.
- Per-group athlete attributes deliberately allow the same user to have a
  different position/type per group, and are the future seat for per-group
  skill characteristics used in team balancing (out of scope now).
- No separate `athletes` table; no pending pre-registration table.

### Account Registration

- Signup providers (Google/email) never supply a phone. The existing
  post-signup completion flow (today: conditional name) is extended to always
  require a phone; name remains conditional on the provider not supplying it.
- Phone is mandatory for every new account.

### Group Entry

- The only entry path is the existing group invite deep link (Épico 03).
- Redeeming an invite creates the membership as `AVULSO`.
- After redeem, the athlete chooses their preferred position in the group
  onboarding step.
- The owner may later switch the athlete between `MENSALISTA` and `AVULSO`.

### Owner Management

- Owner (and admin) edit an athlete's per-group data, switch type, and remove
  the athlete. Removal deletes the membership row; the user account remains.
- The athlete list offers search plus filters by type, position, and financial
  status (paid/pending) read from the existing charges model.

### History Snapshot

- Game attendance and finance charge records store the athlete's display name
  at creation time, so removing the athlete preserves readable history.

### Agent's Discretion

- Exact phone validation/mask (Brazilian format) and storage normalization.
- Exact copy and layout of the extended completion and onboarding steps,
  within the existing design system.
- Derivation rule presentation for paid/pending in the list, provided it reads
  the existing charge statuses and adds no new finance model.

### Declined / Undiscussed Gray Areas -> Assumptions

- Existing users without a phone are asked for it by the same completion gate
  on next authenticated entry; they are not blocked out of existing data.
- Position at onboarding is skippable; the owner or athlete can set it later.
- `active=false` means the owner paused the athlete without deleting the
  vínculo (kept out of new charge generation and default lists); removal
  remains the destructive path.
- Financial status in the list means: `PENDING` charge exists for the current
  reference period → pending; otherwise paid/none.

---

## Deferred Ideas

- Manual pre-registration of athletes by the owner before they install.
- Athlete skill characteristics per group and automatic team balancing.
- Ranking and advanced statistics.
- Athlete profile photo.
