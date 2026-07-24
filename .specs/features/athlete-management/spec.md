# Athlete Management Specification

**Status:** Confirmed
**Context:** `.specs/features/athlete-management/context.md`

## Problem Statement

Saqz groups already have memberships, roles, invites, games, attendance, and
manual finance, but a member is only a name and a role. The owner cannot record
whether an athlete is a monthly member or a drop-in, their preferred position,
or their contact phone, cannot filter the roster by those attributes or by
payment status, and removing a member would leave games and charges pointing at
a vanished identity. New accounts created through Google never carry a phone,
so the group has no reliable contact data at all.

This feature turns the existing membership into a full athlete record completed
by the athlete themselves, gives the owner roster management with search and
filters, and preserves history through name snapshots when an athlete is
removed.

## Goals

- [ ] Every new account has a name and a mandatory phone after the completion flow.
- [ ] An invited user becomes an `AVULSO` athlete of the group and chooses their position during group onboarding.
- [ ] The owner/admin switches an athlete between `MENSALISTA` and `AVULSO`, edits per-group data, and removes athletes.
- [ ] The owner/admin roster offers search and filters by type, position, and paid/pending financial status.
- [ ] Removing an athlete deletes only the group vínculo; the user account and readable history survive via name snapshots.
- [ ] The athlete views their own profile with per-group data and history.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Manual athlete creation or pre-registration by the owner | Every athlete authenticates and completes their own registration through the invite deep link; pre-registration is deferred until demand exists. |
| Per-group skill characteristics and team balancing | Future feature; the per-group data model deliberately leaves room for it. |
| Ranking or advanced statistics | Future. |
| Athlete profile photo | Future. |
| Charge creation or payment processing changes | Financial status is read-only from the existing charges model (Épico 06 owns finance). |
| Attendance confirmation flows | Owned by game management (Épico 05). |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Athlete storage | Extend `group_memberships` (`position`, `membership_type`, `active`) — no new table | The membership already is the one-user-one-group vínculo; per-group attributes support future balancing | Yes |
| Phone storage | `access_users.phone` — account-level, not per group | A phone does not vary per group | Yes |
| Phone collection | Existing completion flow extended; phone always required, name still conditional | No signup provider supplies a phone | Yes |
| Entry path | Invite deep link only; redeem creates `AVULSO` | Reuses Épico 03; owner switches type afterwards | Yes |
| Removal | Delete membership row; user account survives | History readability is preserved by snapshots | Yes |
| Existing users without phone | Same completion gate asks on next authenticated entry | No migration backfill is possible | Default |
| Position at onboarding | Skippable; editable later by athlete or owner | Do not block group entry on an optional preference | Default |
| `active=false` semantics | Owner pause: excluded from new charge generation and default roster view; vínculo kept | Distinct from destructive removal | Default |
| Paid/pending derivation | Any `PENDING` charge in the current reference period → pending; else paid/none | Read-only over existing charge statuses | Default |
| Positions | `LIBERO`, `PONTA`, `CENTRAL`, `OPOSTO`, `LEVANTADOR` | Product decision from the épico | Yes |

**Open questions:** none — unresolved choices are recorded as defaults above.

---

## User Stories

### P1: Mandatory phone in account completion - MVP

**User Story:** As a new Saqz user, I complete my account with name and phone
right after signup so that groups I join have reliable contact data.

**Acceptance Criteria:**

1. WHEN a new account finishes provider signup THEN the completion flow SHALL require a phone before the user proceeds, and SHALL require a name only when the provider supplied none.
2. WHEN a phone is submitted THEN it SHALL be validated as a plausible Brazilian mobile number, normalized before storage in `access_users.phone`, and rejected with a field-level error otherwise.
3. WHEN an existing account without a stored phone authenticates THEN the same completion gate SHALL request the phone once before continuing, without destroying any existing session, group selection, or pending invite capability.
4. WHEN completion succeeds THEN subsequent app entries SHALL NOT ask again.

**Independent Test:** Sign up with Google (no phone), verify the flow blocks on
phone, enter an invalid then a valid number, restart the app, and verify no
re-prompt.

---

### P1: Enter a group as an athlete via invite - MVP

**User Story:** As an invited user, I join the group through the existing deep
link and complete my athlete profile myself so that the owner never has to type
my data.

**Acceptance Criteria:**

1. WHEN a valid invite is redeemed by a user who is not yet a member THEN the created membership SHALL have `membership_type=AVULSO`, `active=true`, and no position.
2. WHEN redemption completes THEN group onboarding SHALL offer the position choice (`LIBERO`, `PONTA`, `CENTRAL`, `OPOSTO`, `LEVANTADOR`) as a skippable step, persisted to the membership when chosen.
3. WHEN an existing member re-redeems an invite THEN their current type, position, and active flag SHALL remain unchanged.
4. WHEN the athlete later edits their own position in that group THEN only their own membership row SHALL change.
5. WHEN invite redemption fails or is abandoned THEN no membership row or athlete attribute SHALL be created.

**Independent Test:** Redeem an invite with a fresh account, skip position,
verify roster shows the athlete as avulso without position, set position from
the athlete profile, verify persistence.

---

### P1: Owner manages the roster - MVP

**User Story:** As an owner or admin, I see and manage the athletes of my group
so that the roster reflects who plays and how.

**Acceptance Criteria:**

1. WHEN an `OWNER`/`ADMIN` opens the roster THEN it SHALL list athletes with name, type, position, active flag, and financial status (paid/pending) read from existing charges.
2. WHEN search text or filters (type, position, financial status) are applied THEN the list SHALL show only matching athletes and filters SHALL be combinable.
3. WHEN an `OWNER`/`ADMIN` switches an athlete between `MENSALISTA` and `AVULSO`, edits their per-group data, or toggles `active` THEN only that membership row SHALL change, with no effect on existing charges or attendance.
4. WHEN an `OWNER`/`ADMIN` removes an athlete THEN the membership row SHALL be deleted, the user account SHALL survive, and the group SHALL no longer list the athlete.
5. WHEN an `ATHLETE` or non-member calls any roster-management operation THEN the backend SHALL reject it with `403` or privacy-preserving `404` without mutation.
6. WHEN the removed user redeems a new invite for the same group THEN a fresh `AVULSO` membership SHALL be created with no attribute carry-over.

**Independent Test:** Populate a group with mixed athletes, filter by each
dimension, switch one athlete's type, remove another, verify the removal
survives refresh and the removed user can rejoin as avulso.

---

### P1: History survives removal via name snapshot - MVP

**User Story:** As an owner, I keep readable game and finance history even
after removing an athlete so that past records stay meaningful.

**Acceptance Criteria:**

1. WHEN an attendance record or a charge is created THEN it SHALL store the athlete's display name at creation time.
2. WHEN the athlete is removed from the group THEN existing attendance and charge records SHALL keep displaying the snapshot name.
3. WHEN a user renames their account THEN previously created records SHALL keep the historical snapshot name while new records use the new name.
4. WHEN pre-existing records created before this feature are displayed THEN they SHALL resolve a name from the still-linked user or a deterministic fallback, never an empty label.

**Independent Test:** Create attendance and a charge for an athlete, rename
the user, remove the athlete, verify old records show the original name.

---

### P2: Athlete views own profile and history

**User Story:** As an athlete, I see my own data and my history per group so
that I know how I participate in each group.

**Acceptance Criteria:**

1. WHEN an athlete opens their profile THEN it SHALL show account data (name, email, phone) and, per group, their type, position, and active flag.
2. WHEN the profile shows history THEN games and charges SHALL be grouped by group and readable with the athlete's own data only.
3. WHEN the athlete belongs to multiple groups THEN per-group attributes SHALL be displayed independently per group.

**Independent Test:** Join two groups with different positions, open the
profile, verify both groups appear with distinct attributes and separate
history.

---

## Edge Cases

- WHEN charge generation for a period runs THEN `active=false` athletes SHALL be excluded from new charges while keeping their existing ones.
- WHEN the same user is removed and re-invited repeatedly THEN each cycle SHALL produce exactly one live membership and no duplicate roster entries.
- WHEN the completion gate and a pending invite coexist on first entry THEN completion SHALL run first and the pending invite capability SHALL survive it.
- WHEN two athletes share a display name THEN the roster and snapshots SHALL keep them as separate rows without exposing internal IDs.
- WHEN the financial-status source (charges) is temporarily unavailable THEN the roster SHALL still render athletes with an explicit unknown financial state rather than fail.

---

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --- | --- |
| Input validation and bounds | Phone validated/normalized; position restricted to the five enum values; type restricted to `MENSALISTA`/`AVULSO`. |
| Failure and partial-failure states | Failed redeem creates nothing; failed edits leave the membership unchanged; roster renders with unknown finance state on charge-read failure. |
| Idempotency, retry, and duplicates | Re-redeem is idempotent for existing members; membership PK `(group_id, user_id)` prevents duplicates; removal is idempotent. |
| Auth boundaries | Roster management is `OWNER`/`ADMIN`; athletes mutate only their own position; non-members get privacy-preserving `404`. |
| Data lifecycle | Removal deletes the vínculo only; snapshots preserve history; `active` is a reversible pause. |
| Migration | Additive columns with safe defaults; existing rows get `membership_type=AVULSO`(default), `active=true`, null position/phone; no data is guessed. |
| Observability | Record type switches, removals, and completion outcomes without logging phone numbers. |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| ATH-01 | P1: Mandatory phone in completion | Done | Delivered |
| ATH-02 | P1: Invite entry as avulso + position onboarding | Done | Delivered |
| ATH-03 | P1: Roster list, search, and filters | Done | Delivered |
| ATH-04 | P1: Type switch, edit, active toggle, removal | Done | Delivered |
| ATH-05 | P1: Name snapshot in attendance and charges | Done | Delivered |
| ATH-06 | P2: Athlete profile and per-group history | Done | Delivered |

**Coverage:** 6 total, 6 delivered (see validation.md for gate evidence and open verification gaps).

---

## Success Criteria

- [ ] A Google signup cannot reach the app without a stored, validated phone.
- [ ] An invite redeem produces exactly one `AVULSO` membership and a skippable position step.
- [ ] The owner filters the roster by type, position, and paid/pending, and every management action is role-guarded.
- [ ] Removing an athlete never deletes the user or blanks historical names.
- [ ] All migrations are additive and existing memberships keep working unchanged.

---

## Regression Backprop

- **B1 | 2026-07-23** — T01 added `access`'s
  `V4__add_user_phone.sql`, but production (bootstrap's Flyway bean) and the
  groups module's cross-module integration-test helper merge the `access` and
  `groups` migration folders into one `classpath:db/migration` location, so
  Flyway sees a single global version sequence. `access`'s new `V4` collided
  with groups' pre-existing `V4__add_group_games.sql`, breaking every groups
  migration integration test that loads the merged location. Covered by V1.
- **V1** — Every new migration file SHALL be numbered as the next version in
  the single global Flyway sequence shared by `access` and `groups` (not the
  next version within its own module's folder), until the baseline is
  explicitly moved by a separate passing change.
