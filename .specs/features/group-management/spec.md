# Group Management Specification

**Status:** Confirmed
**Date:** 2026-07-19
**Context:** `.specs/features/group-management/context.md`

## Problem

Saqz currently creates a private group with only a name and raw timezone. It
cannot represent the group’s volleyball identity, reuse normal play settings,
schedule games, manage confirmations and waitlists, or track expected payments
and group expenses.

## Goal

Let a verified organizer register and maintain a private volleyball group,
configure reusable defaults without creating a game, later operate one-time or
recurring games, manage capacity and attendance, and manually track per-game and
monthly charges plus group expenses without processing money.

## Non-goals

- Sports other than court volleyball, beach volleyball, and footvolley.
- Public groups, search, discovery, or join requests.
- Group deletion, archive, ownership transfer, co-owners, or leaving a group.
- Team balancing, lineups, positions, score, statistics, tournament brackets,
  or player-performance history.
- Pix/card/bank integration, wallets, money custody, automatic reconciliation,
  invoices, tax documents, refunds, split payments, or partial payments.
- Reimbursements or expense settlement.
- Replacing the existing authentication, role, membership, or invitation model.

## Ownership and compatibility

- Authentication/access owns verified identity, account/session bootstrap, and
  selected-group reconciliation.
- Group Management owns the single `OWNER`, `ADMIN`/`ATHLETE` memberships,
  invitations, profile, reusable defaults, venues, game series, occurrences,
  attendance, charges, manual payment status, and expenses.
- Existing IDs, memberships, invitations, and creation idempotency survive the
  migration unchanged.
- Existing groups remain readable but are marked `INCOMPLETE` until an
  authorized editor supplies newly required modality and composition; migration
  never guesses them.
- No group registration creates a game, attendance record, charge, or expense.

## Group registration data

### Profile fields

| Field | Required | Type / values | Validation |
| --- | --- | --- | --- |
| `name` | Yes | Text | Trimmed; 2–80 characters; no control characters. |
| `modality` | Yes | `COURT_VOLLEYBALL`, `BEACH_VOLLEYBALL`, `FOOTVOLLEY` | Exactly one supported value. |
| `composition` | Yes | `WOMEN`, `MEN`, `MIXED` | Exactly one supported value. |
| `photo` | No | One square image | JPEG, PNG, or WebP; at most 5 MiB and 4096×4096 after decode; no animation. |
| `description` | No | Text | Blank becomes `null`; otherwise trimmed, 2–500 characters; no controls. |
| `city` | No | Text | Blank becomes `null`; otherwise trimmed, 2–80 characters; no controls. |
| `level` | No | `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `MIXED_LEVELS`, `CUSTOM` | `customLevel` required only for `CUSTOM`. |
| `customLevel` | Conditional | Text | Trimmed 2–40 characters for custom level; otherwise `null`. |
| `playStyle` | No; court only | `SIX_ZERO`, `FOUR_TWO`, `FIVE_ONE`, `CUSTOM` | Forbidden unless modality is court volleyball. |
| `customPlayStyle` | Conditional | Text | Trimmed 2–40 characters for custom play style; otherwise `null`. |

### Optional game defaults

| Field | Required | Type / values | Validation / meaning |
| --- | --- | --- | --- |
| `defaultVenue` | No | Nested venue | Name and address required when the venue block is used; court identifier optional. |
| `regularSlots` | No | List of weekday/time defaults | Each slot requires weekday, start time, and duration; at least one slot if block enabled. |
| `defaultCapacity` | No | Integer | 2–100 available confirmation spots. |
| `defaultConfirmationLeadMinutes` | No | Integer | 0–10,080 minutes before start; `0` means game start. |
| `defaultGameFeeCents` | No | Integer BRL cents | 1–99,999,999; absence means no per-game charge default. |

### Optional monthly finance defaults

| Field | Required | Type / values | Validation / meaning |
| --- | --- | --- | --- |
| `monthlyFeeCents` | No | Integer BRL cents | 1–99,999,999; absence means no monthly-fee default. |
| `monthlyDueDay` | Conditional | Integer | Required when monthly fee exists; day 1–28. |

### Venue fields

| Field | Required | Validation |
| --- | --- | --- |
| `name` | Yes | Trimmed 2–120 characters; e.g. `Arena Beach Sports`. |
| `address` | Yes | Trimmed 5–300 characters. |
| `court` | No | Blank becomes `null`; otherwise trimmed 1–80 characters; e.g. `Quadra 2`. |

### Regular-slot fields

| Field | Required | Validation |
| --- | --- | --- |
| `weekday` | Yes | Monday through Sunday. |
| `startTime` | Yes | Local wall-clock time in the group timezone. |
| `durationMinutes` | Yes | 15–480 minutes. |

### System-managed group fields

| Field | Source | Contract |
| --- | --- | --- |
| `id` | Backend | Stable UUID. |
| `ownerUserId` | Authenticated principal | Created atomically; exactly one owner. |
| `creationKey` | Mobile request | UUID idempotency key. |
| `timeZone` | Device/platform adapter | Detected automatically; technical identifier is not a normal form field. |
| `privacy` | Backend constant | Always `PRIVATE`. |
| `currency` | Backend constant | Always `BRL`. |
| `profileStatus` | Backend derivation | `COMPLETE` for valid new groups; legacy missing required profile data is `INCOMPLETE`. |
| `version` | Backend | Starts at 1; optimistic-concurrency version. |
| timestamps | Backend clock | Server-controlled creation/update times. |

## pt-BR presentation labels

| Value | Label |
| --- | --- |
| `COURT_VOLLEYBALL` | `Vôlei de quadra` |
| `BEACH_VOLLEYBALL` | `Vôlei de praia` |
| `FOOTVOLLEY` | `Futevôlei` |
| `WOMEN` / `MEN` / `MIXED` | `Feminino` / `Masculino` / `Misto` |
| `BEGINNER` / `INTERMEDIATE` / `ADVANCED` | `Iniciante` / `Intermediário` / `Avançado` |
| `MIXED_LEVELS` / level `CUSTOM` | `Todos os níveis` / `Personalizado` |
| `SIX_ZERO` / `FOUR_TWO` / `FIVE_ONE` | `6-0` / `4-2` / `5-1` |
| play-style `CUSTOM` | `Personalizado` |

Visible labels come from Compose resources and never expose enum names, object
keys, cents, or timezone identifiers.

## Group registration flow

1. A verified user opens `Criar grupo`.
2. One keyboard-safe, scrollable flow presents three clear sections: group
   profile, optional game defaults, and optional finance defaults.
3. Required fields are name, modality, and composition. Every other user field
   is optional or conditionally required.
4. Timezone is detected from the device. Detection failure uses a friendly
   region/timezone chooser, never raw technical input.
5. Court volleyball reveals optional play style. Beach volleyball or footvolley
   hides and clears both style fields.
6. Custom level/style reveals its required label. Selecting a preset clears the
   obsolete custom label.
7. Enabling default venue requires venue name and address. Regular slots can be
   added/removed independently and use the group timezone.
8. Enabling a monthly fee requires a due day. Fees are displayed/entered as BRL
   and converted to integer cents before transport.
9. Submit validates the complete visible/conditional form. Invalid input makes
   no request.
10. Backend atomically creates group, owner, scalar defaults, optional venue,
    and regular slots under one creation key. The group becomes selected.
11. Optional photo uploads separately after the group ID exists. Upload failure
    never rolls back or duplicates the group and remains retryable.
12. Success opens group context/profile. It creates no first game and may offer
    separate actions `Criar jogo` and `Fazer depois`.

## Group profile, defaults, and privacy

- All current members read the complete non-financial group profile and game
  defaults. Only `OWNER`/`ADMIN` read finance defaults and expense totals.
- `ATHLETE` sees no profile/default edit controls.
- `OWNER`/`ADMIN` edit profile, defaults, venue, photo, and finance defaults.
- Updates use optimistic concurrency. A stale update returns conflict, keeps the
  local draft, and offers reload/retry.
- Switching away from court volleyball atomically clears both style fields.
- Changing custom values to presets atomically clears custom labels.
- Group-default edits affect only future prefill; they never mutate existing
  series, games, attendance, charges, or expenses.
- A non-member receives the same `404` contract for group, photo, venue, game,
  attendance, and finance resources.
- There is no public listing, public photo URL, or unauthenticated invite preview
  of name, city, photo, schedule, members, or finance data.

## Private photo contract

- Native Android/iOS adapters provide camera/library selection; shared UI
  provides square preview/crop, replace, retry, and remove.
- Backend verifies membership role, declared/decoded type, byte size, pixel
  dimensions, successful decoding, and non-animation.
- Invalid media never replaces the current photo.
- Replacement publishes the new private reference atomically before safely
  removing the old object. Removal is idempotent and shows a deterministic
  fallback mark.
- Storage provider and raw object key never become durable public API fields.
- Production media is private; only current members may retrieve it.

## Invitation links and deep links

- `OWNER`/`ADMIN` create or rotate the group's single active invitation, share
  its Branch Long Link, and explicitly expire it. Rotation immediately
  invalidates the previous code.
- The link contains only an opaque invite capability. It never contains a group
  ID, name, city, photo, schedule, member, email address, or finance data, and
  it provides no unauthenticated group preview.
- Android/iOS accept the same verified HTTPS link from cold start, warm open,
  Universal/App Link, or Branch install-deferred delivery. Direct and Branch
  copies of the same event redeem only once.
- If no verified session exists, the opaque code survives safe app restart and
  waits for authentication. Successful redemption creates at most one
  `ATHLETE` membership, preserves an existing role, clears the pending code,
  selects the group, and opens its private context.
- Explicit discard/logout clears a pending code. Invalid, expired, or rotated
  codes clear terminal pending state and reveal no group existence; temporary
  failures remain retryable with the same code.
- Redemption attempts retain the existing abuse limit of ten invalid codes per
  user in ten minutes and return a retry interval without disclosing which
  group, if any, a code once represented.

## Game model

### Game fields

| Field | Required | Contract |
| --- | --- | --- |
| `title` | Yes | Trimmed 2–120 characters; may be prefilled from group/slot context. |
| `venue` | Yes | Existing group venue or a new valid venue snapshot/reference. |
| `startsAt` | Yes | Zoned instant resolved from local date/time and group timezone. |
| `durationMinutes` | Yes | 15–480 minutes. |
| `capacity` | Yes | 2–100 confirmation spots. |
| `confirmationDeadline` | Yes | At or before game start; may be prefilled from lead minutes. |
| `gameFeeCents` | No | Integer BRL cents; copied from default and independently editable. |
| `notes` | No | Blank becomes `null`; otherwise trimmed 2–500 characters. |
| `status` | System | `DRAFT`, `PUBLISHED`, `CANCELLED`, `COMPLETED`. |
| `version` | System | Optimistic-concurrency version. |

### Recurrence

- A game is one-time or belongs to a weekly series.
- A weekly series has a start date, optional end date, and one or more schedule
  slots, each with weekday, start time, duration, and venue.
- Series creation materializes/serves bounded future occurrences; it never
  requires an unbounded database write.
- Editing an occurrence offers `Somente este jogo` or `Este e os próximos`.
- `Somente este jogo` detaches/overrides that occurrence without changing its
  siblings.
- `Este e os próximos` preserves past and completed occurrences and changes
  only the selected occurrence and later occurrences.
- Cancelling a series follows the same past/future boundary and preserves all
  historical attendance and finance records.

### Game permissions and lifecycle

- `OWNER`/`ADMIN` create, edit, publish, cancel, and complete games.
- `ATHLETE` reads published games and manages only their own attendance before
  the deadline.
- Draft games are organizer-only and create no attendance or charge.
- Publishing makes the game visible and opens attendance.
- Cancelled/completed games are read-only except for authorized finance status
  correction; they are never hard-deleted.
- Cancelling a game cancels its pending per-game charges. Paid/waived charges
  remain historical and are flagged for organizer review; Saqz issues no refund.

## Attendance and waitlist

### Attendance states

| State | Meaning |
| --- | --- |
| No response | No attendance record yet. |
| `CONFIRMED` | Occupies one capacity spot. |
| `DECLINED` | Does not occupy a spot. |
| `WAITLISTED` | Ordered queue after capacity is full. |

- Confirming while a spot exists creates/changes attendance to `CONFIRMED`.
- Confirming at capacity creates/changes it to `WAITLISTED` with a stable FIFO
  position.
- A confirmed withdrawal keeps any existing charge pending by default and frees
  a spot. The first valid waitlisted member is promoted atomically.
- Capacity increase promotes as many waitlisted members as new spots permit.
- Capacity decrease never demotes existing confirmed members; it blocks new
  confirmations until confirmed count falls below capacity.
- After the confirmation deadline, athletes cannot self-change attendance;
  `OWNER`/`ADMIN` may override with an audit record.
- Cancellation freezes attendance history and prevents further responses.
- Concurrent confirmations/promotions never exceed capacity or give duplicate
  waitlist positions.

## Manual charges and payment status

### Charge kinds and identity

- `GAME`: at most one charge per member per game.
- `MONTHLY`: at most one charge per member per group and calendar month.
- Every charge stores amount in integer BRL cents, kind, subject, member,
  status, created-by, status-changed-by, and timestamps.
- Charges represent tracking records only. Saqz never stores payment
  credentials, generates a payment transaction, or claims that funds settled.

### Per-game charges

- Publishing a paid game does not charge every member. A per-game charge is
  created idempotently when a member becomes confirmed.
- Waitlisted, declined, and no-response members receive no game charge.
- Promotion from waitlist creates the charge once.
- Withdrawal does not automatically forgive the charge; it stays pending until
  an organizer marks it paid, waived, or cancelled.

### Monthly charges

- `OWNER`/`ADMIN` choose a month and the active members to include, review the
  default amount/due date, and generate charges idempotently.
- Organizers may exempt owner/admin/athlete members by excluding them or marking
  the generated charge waived.
- Changing the group monthly default never rewrites an already generated charge.

### Status and visibility

| Status | Meaning |
| --- | --- |
| `PENDING` | Expected but not marked received. |
| `PAID` | Organizer manually recorded receipt. |
| `WAIVED` | Organizer decided no payment is due. |
| `CANCELLED` | Charge was voided without payment. |

- `OWNER`/`ADMIN` read all charges and change statuses with a mandatory audit
  record. Corrections never erase previous audit entries.
- `ATHLETE` reads only their own charges and cannot change status.
- No partial amount, refund, credit balance, or negative charge exists.

## Manual group expenses

| Field | Required | Contract |
| --- | --- | --- |
| `description` | Yes | Trimmed 2–160 characters. |
| `amountCents` | Yes | Integer BRL cents, 1–99,999,999. |
| `expenseDate` | Yes | Group-local calendar date. |
| `category` | Yes | `VENUE`, `EQUIPMENT`, `REFEREE`, `OTHER`. |
| `customCategory` | Conditional | Trimmed 2–40 characters for `OTHER`; otherwise `null`. |
| `notes` | No | Blank becomes `null`; otherwise trimmed 2–500 characters. |
| audit fields | System | Creator, last editor, version, and timestamps. |

- `OWNER`/`ADMIN` create, edit, and void expenses; expenses are not hard-deleted.
- Only `OWNER`/`ADMIN` read expense entries and aggregate finance totals in this
  version.
- An expense never creates a member debt, reimbursement, or money transfer.

## Acceptance criteria

### Registration and migration

1. **GRP-REG-01** — WHEN registration opens THEN required profile and all
   optional profile/game-default/finance-default fields SHALL be discoverable in
   one accessible flow without creating a game.
2. **GRP-REG-02** — WHEN valid data is submitted THEN group, single owner,
   defaults, optional venue, and regular slots SHALL commit once under the
   creation key and the group SHALL become selected.
3. **GRP-REG-03** — WHEN the creation key is retried THEN no group, owner,
   venue, slot, photo, game, charge, or expense SHALL duplicate.
4. **GRP-REG-04** — WHEN existing groups migrate THEN identity/access data SHALL
   remain valid and missing required profile values SHALL produce
   `profileStatus=INCOMPLETE`, never guessed values.
5. **GRP-REG-05** — WHEN timezone detection succeeds THEN no technical timezone
   input SHALL appear; failure SHALL use a friendly selector.

### Conditional profile/default behavior

6. **GRP-DEFAULT-01** — WHEN modality is not court volleyball THEN play-style
   fields SHALL be hidden, cleared, and rejected if sent.
7. **GRP-DEFAULT-02** — WHEN level or play style is not custom THEN obsolete
   custom text SHALL be cleared and contradictory payloads rejected.
8. **GRP-DEFAULT-03** — WHEN group defaults change THEN existing series, games,
   attendance, charges, and expenses SHALL remain byte-for-byte semantically
   unchanged.
9. **GRP-DEFAULT-04** — WHEN an athlete edits or a non-member reads/writes THEN
   backend SHALL return `403` or privacy-preserving `404` without mutation.

### Photo and privacy

10. **GRP-PHOTO-01** — WHEN optional photo upload succeeds/fails THEN group
    creation SHALL remain singular and failure SHALL be retryable without a
    broken/public reference.
11. **GRP-PHOTO-02** — WHEN media is corrupt, animated, spoofed, oversized, or
    unauthorized THEN it SHALL never replace/publish a photo.
12. **GRP-PRIVATE-01** — WHEN any non-member or unauthenticated actor probes
    group-related resources THEN no profile, schedule, member, attendance,
    finance, photo, or storage data SHALL be disclosed.

### Games and recurrence

13. **GAME-01** — WHEN an organizer creates a game from defaults THEN the game
    SHALL copy those values and permit overrides without linking historical
    values back to mutable defaults.
14. **GAME-02** — WHEN a weekly series uses multiple slots THEN each occurrence
    SHALL resolve the correct local weekday/time across timezone offset changes.
15. **GAME-03** — WHEN an organizer edits/cancels one occurrence or this-and-
    future THEN the chosen boundary SHALL apply exactly and past/completed data
    SHALL remain unchanged.
16. **GAME-04** — WHEN game lifecycle or role is invalid THEN publish/edit/
    cancel/complete SHALL fail without partial schedule, attendance, or finance
    mutation.

### Attendance

17. **ATTEND-01** — WHEN capacity is available/full THEN confirmation SHALL
    atomically produce `CONFIRMED`/`WAITLISTED` with no overbooking.
18. **ATTEND-02** — WHEN a spot opens or capacity increases THEN FIFO waitlisted
    members SHALL promote atomically and receive at most one game charge.
19. **ATTEND-03** — WHEN capacity decreases below confirmed count THEN no member
    SHALL be silently demoted and new confirmations SHALL remain blocked.
20. **ATTEND-04** — WHEN deadline passes or game is cancelled/completed THEN
    athlete self-service SHALL close while authorized overrides remain audited
    where specified.

### Manual finance

21. **FIN-01** — WHEN a member becomes confirmed for a paid game THEN exactly one
    pending game charge SHALL exist; waitlisted/declined members SHALL have none.
22. **FIN-02** — WHEN confirmed attendance withdraws THEN its charge SHALL remain
    pending until an organizer records paid, waived, or cancelled.
23. **FIN-03** — WHEN monthly charges are generated/retried THEN selected active
    members SHALL receive at most one immutable-amount charge for that month.
24. **FIN-04** — WHEN a status changes THEN actor, old/new status, and timestamp
    SHALL append to audit history; no prior event SHALL be overwritten.
25. **FIN-05** — WHEN an athlete reads finance data THEN only their charges SHALL
    be visible; group totals and expenses SHALL remain organizer-only.
26. **FIN-06** — WHEN an expense is created/edited/voided THEN integer-cents
    amount, category, date, version, and audit history SHALL remain consistent
    without creating a payment or reimbursement.
27. **FIN-07** — WHEN implementation is inspected THEN no payment SDK, payment
    credential, webhook, settlement claim, partial payment, or refund model SHALL
    exist.

### Mobile and regression

28. **GRP-UI-01** — WHEN viewport is compact, keyboard visible, or text scale is
    maximal THEN every group/default/game/attendance/finance action SHALL remain
    reachable in semantic order with at least 48 dp targets.
29. **GRP-UI-02** — WHEN a request fails, app rotates/restarts, or retry occurs
    THEN non-sensitive drafts SHALL survive safely and no creation, RSVP,
    promotion, charge, or expense SHALL execute twice.
30. **GRP-REGRESSION-01** — WHEN delivered THEN existing auth, selection,
    membership, invitation, logout, credential, workspace isolation, backend,
    Android, and iOS gates SHALL remain mandatory and green.

### Invitation and deep-link journey

31. **INVITE-01** — WHEN an owner/admin generates, rotates, expires, or shares
    an invitation THEN exactly one opaque active capability SHALL be managed
    without placing private group or user data in the link or preview.
32. **INVITE-02** — WHEN the active link opens cold, warm, or after installation
    before/after authentication THEN its code SHALL be delivered once, survive
    safe restart until a verified session is ready, and never bypass auth.
33. **INVITE-03** — WHEN a valid code is redeemed or retried THEN membership
    SHALL be created at most once without downgrading an existing role, pending
    state SHALL clear, and the redeemed group SHALL become selected and open.
34. **INVITE-04** — WHEN a code is invalid, expired, rotated, duplicated, or
    rate-limited THEN no group data or existence SHALL leak, no duplicate
    membership/selection SHALL occur, and only retryable outcomes SHALL retain
    pending state.

## Verification requirements

- Backend unit/domain tests cover every enum, conditional rule, transition,
  authorization decision, amount rule, idempotency key, and recurrence boundary.
- PostgreSQL integration tests cover additive migration, constraints,
  transactions, locking, optimistic concurrency, FIFO promotion, unique charges,
  and audit append-only behavior.
- HTTP integration tests assert exact contracts, stable field errors,
  privacy-preserving `404`, `403`, conflicts, media validation, and redaction.
- KMP tests cover serialization, copied defaults, timezone recurrence, dependent
  cleanup, draft restoration, upload retry, attendance, and finance state.
- Compose tests cover registration sections, venue/slot editors, game lifecycle,
  RSVP/waitlist, charge visibility, expense forms, accessibility, and large text.
- Android/iOS tests cover image permissions/cancellation and lifecycle recovery.
- Backend/KMP/native journey tests cover invite generation/share, rotation,
  expiry, cold/warm/install-deferred delivery, authentication waiting, restart,
  idempotent redemption, selection, invalid codes, and attempt limiting.
- Security gates prove no production media/payment credential, public object,
  client-authoritative charge/status, or sensitive log enters the repository.

## Regression backprop

- **B1 | 2026-07-19** — The T08 mobile Groups module was added without updating
  the repository scope allowlist, so the mandatory scope gate rejected the
  approved feature path. Covered by V1.
- **V1** — The scope gate SHALL permit the approved `mobile/features/groups/`
  feature surface, including invitation behavior, while its mutation suite
  SHALL continue to reject any other unapproved mobile feature path.
- **B2 | 2026-07-19** — The T09 Android accessibility gate depended on the
  default app composition to imply the signed-out login screen, and the visible
  login brand image was marked decorative, so TalkBack could not announce the
  label under the intended fixture. Covered by V2.
- **V2** — Native accessibility tests SHALL render the explicit route/screen
  fixture they claim to verify when the app composition has authenticated,
  bootstrapping, or group-routing dependencies, and visible brand images SHALL
  expose exactly one accessible brand label.
- **B3 | 2026-07-19** — The T09 iOS native gate implemented new Swift Groups
  adapters with source-level Kotlin parameter labels instead of the
  Kotlin/Native-generated Swift protocol labels, so conformance failed at build
  time. Covered by V3.
- **V3** — iOS adapters that implement KMP Groups ports SHALL compile against
  the generated Swift protocols in `check-ios`, including external argument
  labels for callbacks and listeners.
- **B4 | 2026-07-19** — The invite attempt-limit HTTP problem exposed retry
  seconds only in the body, so clients and intermediaries could miss the
  required `Retry-After` contract. Covered by V4.
- **V4** — Every invite attempt-limit HTTP response SHALL include matching
  `retryAfterSeconds` body data and `Retry-After` header value, while terminal
  invalid/expired/rotated invite responses SHALL omit `Retry-After`.
- **B5 | 2026-07-19** — The first T14 migration was placed on a Groups-only
  resource classpath while the existing baseline schema still lived in the
  Access migration classpath, so Flyway attempted V2 before V1 in Groups
  integration tests. Covered by V5.
- **V5** — Every new database migration SHALL be discoverable in the same
  Flyway location sequence as the current baseline migration until the baseline
  is explicitly moved by a separate passing migration-order change.
- **B6 | 2026-07-19** — A T16 repository test expected blank optional
  `defaultVenue.court` to be rejected even though the accepted profile field
  contract says blank optional venue court normalizes to `null`. No new
  invariant added; the existing venue field contract and T15 domain validation
  tests already cover the required behavior.
- **B7 | 2026-07-19** — The first T16 HTTP create DTO modeled the optional
  `regularSlots` collection as a non-null Kotlin parameter with a default, so
  clients that omitted optional defaults hit a server error before domain
  validation. Covered by V6.
- **V6** — Optional transport collections in group create/update/profile APIs
  SHALL deserialize when omitted, normalize to empty domain lists before
  validation, and retain endpoint tests that omit those optional collections.
- **B8 | 2026-07-19** — Groups integration tests loaded Access-owned
  migrations by adding a runtime project dependency on `:features:access`,
  causing backend architecture gates to reject the Groups feature boundary.
  Covered by V7.
- **V7** — Groups integration tests MAY load Access-owned baseline migrations
  by explicit migration resource or filesystem location, but SHALL NOT add any
  backend feature-to-feature project dependency to make migrations visible.
- **B9 | 2026-07-19** — The first T19 focused HTTP gate inherited the shell's
  JDK 17 even though the repository gate and active handoff require JDK 21, so
  Gradle rejected the invocation before compilation. No new invariant added;
  the existing deterministic JDK 21 gate and handoff already detect and
  document this environment prerequisite.
- **B10 | 2026-07-19** — The first T20 common-test compile omitted imports for
  JSON numeric accessors and the draft failure enum. No new invariant added;
  the existing Android/iOS KMP compilation gate caught the mechanical import
  omissions before any test could run.
- **B11 | 2026-07-19** — T20 followed its original `domain/` mobile package
  instruction, but the repository scope contract reserves domain/application
  source segments for the authoritative backend; the pre-commit safety run
  also missed the new untracked path because it inspected only `git ls-files`.
  T20 now uses `model/`, and the scope gate includes non-ignored untracked
  candidates. Covered by V8.
- **V8** — Repository scope checks SHALL inspect tracked plus non-ignored
  untracked candidate files, and mobile production/test source SHALL reject
  `domain`, `usecase`, or `application` path segments before staging or commit.
- **B12 | 2026-07-19** — The first T22 compile passed nullable values from a
  reusable optional choice control directly to required modality/composition
  intents. No new invariant added; the existing Android/iOS KMP compilation
  gate deterministically rejects the type mismatch before tests run.
- **B13 | 2026-07-19** — The first T23 compile gave raw-media request and
  binary-decode helpers the same name with only lambda shape distinguishing
  them, producing ambiguous overload resolution on Android and iOS. No new
  invariant added; the existing cross-target compile gate catches this API
  design error deterministically.
- **B14 | 2026-07-19** — The first T23 media-test compile used a no-open fixture
  lambda without the channel return required by its contract and compared a
  nullable header list to an inferred non-null list. No new invariant added;
  common-test compilation already catches both fixture type errors.
- **B15 | 2026-07-19** — A T23 oversized upload closed its input but left the
  multipart destination open when the stream exceeded its declaration, so the
  consumer waited until timeout; the declared-length read fixture also sent a
  body inconsistent with its own header and was rejected by MockEngine before
  the client bound ran. Covered by V9.
- **V9** — Bounded media writers SHALL close both source and destination with
  the original cause on size mismatch or cancellation, and binary reads SHALL
  reject a coherent declared `Content-Length` above the configured limit before
  consuming the response channel.
- **B16 | 2026-07-19** — The first T25 HTTP fixture named the existing
  `GroupReadSnapshot.id` constructor argument `groupId`, so bootstrap test
  compilation stopped before execution. No new invariant added; Kotlin named
  argument checking already catches this fixture drift.
- **B17 | 2026-07-19** — The first T26 common photo handles used the JVM-only
  `JvmInline` annotation, so Android compilation passed while the iOS target
  failed before tests. Covered by V10.
- **V10** — Every provider-neutral Groups photo contract in `commonMain` SHALL
  compile for both Android and iOS targets and SHALL NOT depend on platform-only
  annotations or types.
- **B18 | 2026-07-19** — T27 encoder design exposed that the first shared crop
  contract used one normalized side length on unequal source axes, which could
  not unambiguously describe a physical square. Covered by V11.
- **V11** — Shared photo crop state SHALL express provider-neutral center and
  zoom values; every native encoder SHALL derive, clamp, and encode one equal-
  width/equal-height pixel region for portrait, landscape, and square sources.
- **B19 | 2026-07-19** — The first Android photo encoder could not compile
  because the provider-neutral encoded-source port exposed Ktor's
  `ByteReadChannel`, leaking transport internals into the native adapter.
  Covered by V12.
- **V12** — Native photo selection/encoding ports SHALL expose only project
  value types and Kotlin standard-library values; Ktor channel construction
  SHALL remain inside the Groups data/network adapter.
- **B20 | 2026-07-19** — The first T28 Swift compile used Kotlin-style `in`
  syntax for a byte-count range guard. No new invariant added; the iOS native
  compile gate deterministically catches Swift syntax errors before tests run.
- **B21 | 2026-07-19** — The second T28 Swift compile returned the Boolean
  cleanup result from two `Void` guard branches. No new invariant added; the
  existing iOS native compile gate deterministically catches return-type drift.
- **B22 | 2026-07-19** — The third T28 Swift compile accessed a non-Sendable
  notification observer token from the main-actor adapter's nonisolated
  `deinit`. No new invariant added; strict-concurrency iOS compilation already
  rejects actor-isolation violations.
- **B23 | 2026-07-19** — The first T29 common-test compile compared sealed photo
  intents with inferred singleton and empty lists that Kotlin/Native could not
  type consistently. No new invariant added; common-test compilation already
  catches assertion type inference errors before execution.
- **B24 | 2026-07-19** — The first T29 UI run mounted the square photo editor
  without the production scroll container, leaving lower actions outside the
  test viewport with zero visible bounds and making clicks no-ops. No new
  invariant added; the existing compact/keyboard semantic-flow criterion and
  Compose UI gate cover scroll reachability.
- **B25 | 2026-07-19** — Adding a default photo callback after the setup test
  fixture's established intent callback rebound existing trailing lambdas to
  the wrong sealed-intent type. Covered by V13.
- **V13** — Shared UI and test helper APIs with multiple callback parameters
  SHALL keep the established primary trailing-lambda callback last, or callers
  SHALL name every callback at the call site.
- **B26 | 2026-07-19** — T29 fresh-eyes review found that post-create photo
  upload emitted only a group ID even though the private photo mutation requires
  the confirmed group ETag, making a valid `If-Match` handoff impossible.
  Covered by V14.
- **V14** — A confirmed create with pending private media SHALL retain or derive
  the quoted group ETag from the authoritative returned version and include it
  in the deferred upload handoff; the media upload SHALL never guess from draft
  or pre-confirmation state.
- **B27 | 2026-07-19** — T29 fresh-eyes review found that successful photo
  upload/removal cleared the returned group ETag while leaving consecutive
  replace/remove controls visible, so the next valid organizer action failed as
  target unavailable until an external refetch. Covered by V15.
- **V15** — Every successful versioned private-media mutation SHALL publish its
  returned group ETag into shared state before enabling another mutation; a
  visible consecutive replace/remove action SHALL be immediately executable.
- **B28 | 2026-07-19** — The first T30 integration-test invocation used the
  shell's JDK 17 and the backend gate stopped before compilation because the
  repository requires JDK 21. No new invariant added; the existing backend
  toolchain gate rejects an incompatible JVM deterministically.
- **B29 | 2026-07-19** — T34 boundary design exposed that nulling an
  `ONLY_THIS` occurrence's series identity would free its bounded unique key,
  allowing the next replenishment to recreate the overridden or cancelled
  occurrence. Covered by V16.
- **V16** — A detached one-occurrence override/cancellation SHALL retain its
  stable `(series_id, local_date, slot_key)` identity as a materialization
  tombstone while being explicitly marked detached; replenishment SHALL never
  recreate it.
- **B30 | 2026-07-19** — T34 boundary design found that editing from a series'
  first occurrence must close the prior revision at `local_start_date - 1`, but
  V4 rejected every active-through value before the start. Covered by V17.
- **V17** — A superseded series revision MAY close exactly one day before its
  local start when `THIS_AND_FUTURE` selects the first occurrence; no earlier
  active-through boundary is valid.
- **B31 | 2026-07-19** — T34 successor materialization exposed that V4 stored
  every recurring slot snapshot except its title, so a new revision could not
  regenerate a complete immutable game snapshot without borrowing mutable
  occurrence data. Covered by V18.
- **V18** — Every series slot revision SHALL persist every value required to
  regenerate an immutable game occurrence snapshot, including its title;
  successor materialization SHALL use only the accepted revision rule.
- **B32 | 2026-07-19** — The first T34 boundary integration run attempted to
  insert a regenerated successor occurrence when the same stable identity was
  already occupied by a completed historical game, and PostgreSQL correctly
  rejected the duplicate. No new invariant added; the accepted recurrence and
  boundary requirements already require unique stable identities and forbid
  rewriting completed games.
- **B33 | 2026-07-19** — The first T35 Bruno gate rejected compact inline
  request blocks even though the same fields are valid in multiline Bruno
  syntax. No new product invariant added; the existing Bruno contract gate
  deterministically enforces the repository parser's supported request shape.
- **B34 | 2026-07-19** — The second T35 Bruno gate could not pair three
  concrete lifecycle requests with one regex-templated controller mapping.
  No new product invariant added; the accepted task already requires every
  explicit lifecycle route to have a discoverable matching contract.
- **B35 | 2026-07-19** — The first T37 Android compile parsed two compressed
  generic return declarations ending in `Dto>=` as an operator sequence;
  metadata compilation had skipped the common source. No new invariant added;
  the mandatory Android main compile in Mobile quick catches target-visible
  Kotlin syntax before task completion.
- **B36 | 2026-07-19** — The first T37 test command passed JVM-only `--tests`
  filtering to the KMP `allTests` aggregate, which rejected the unsupported
  option before executing tests. No new invariant added; the task's prescribed
  unfiltered Mobile quick command is already authoritative.
- **B37 | 2026-07-19** — The first unfiltered T37 KMP run reached the iOS test
  compiler and rejected a compressed auth-refresh fixture call whose token
  argument was placed after a trailing handler lambda. No new invariant added;
  common-test compilation on the iOS simulator target catches portable call
  syntax before execution.
- **B38 | 2026-07-19** — The first T38 KMP compile found that the selection
  helper accepted the sealed parent intent while reading subtype-only group,
  role, and date fields. No new invariant added; common compilation already
  rejects missing intent narrowing deterministically.
- **B39 | 2026-07-19** — The first executing T38 suite left an intentionally
  pending group-switch request incomplete after asserting the protected
  loading state, and `runTest` reported the leaked coroutine. No new invariant
  added; coroutine test completion already detects unfinished asynchronous
  work.
- **B40 | 2026-07-19** — The first T39 compile passed a message argument to the
  fixed shared `SaqzLoadingState` API. No new invariant added; Android and iOS
  common compilation already detect design-system signature drift.
- **B41 | 2026-07-19** — The second T39 compile found a test-only viewport
  helper unavailable on the common iOS target, a missing `dp` import, and
  companion initialization calling an instance fixture. No new invariant
  added; the cross-target Compose compile gate already enforces portable UI
  test APIs and fixture initialization.
- **B42 | 2026-07-19** — The first resumed T40 verification selected the
  nonexistent `compileKotlinAndroid` task and stopped before compiling product
  sources. No new invariant added; task execution must use the prescribed
  Mobile quick task names, including `compileAndroidMain` and `allTests`.
- **B43 | 2026-07-19** — The first valid T40 cross-target compile parsed a
  compressed generic fixture declaration ending in `Dto>=` as operators and
  found that a nested fake gateway called instance fixture helpers without an
  outer receiver. No new invariant added; Mobile quick's iOS common-test
  compilation already catches both portable test-source errors.
- **B44 | 2026-07-19** — The next T40 Kotlin/Native test compile rejected a
  boundary fixture class declared inside an `inner` fake gateway. No new
  invariant added; the existing cross-target test compilation catches this
  Kotlin portability constraint.
- **B45 | 2026-07-19** — T40 Mobile quick re-exposed two iOS-only flaky tests
  from T38/T39: a duplicate-refresh assertion left a deferred request alive,
  and a compact-list assertion depended on a fixed scroll position. No new
  product invariant added; deterministic coroutine completion and semantic
  per-item scrolling are required so the existing cross-target gate remains
  reliable.
- **B46 | 2026-07-19** — The repaired compact-list test still tried to locate
  offscreen LazyColumn children before they were materialized. No new product
  invariant added; the list now exposes a semantic container tag so tests can
  scroll by index and then assert each materialized card.
- **B47 | 2026-07-19** — The first T41 common-test compile imported
  `assertExists` and `assertDoesNotExist` as top-level extensions although the
  pinned Compose test API exposes them through interaction resolution. No new
  invariant added; cross-target test compilation already detects incompatible
  imports and the repository's established wildcard import is portable.
- **B48 | 2026-07-19** — The first executing T41 iOS suite asserted emitted
  intents immediately after queued Compose text/click actions, so callbacks
  had not run on the test dispatcher. No new invariant added; mutating common
  UI tests now synchronize with the Compose test clock before inspecting
  emitted intents.
- **B49 | 2026-07-19** — T41 test synchronization exposed that tagged
  `SaqzInput` wrappers merge semantics without the inner field's focus action,
  while below-fold buttons need scrolling before iOS clicks register. No new
  product invariant added; tests target unmerged labeled fields and explicitly
  scroll action controls into view.
- **B50 | 2026-07-19** — T41 fresh-eyes review found the composable generated
  recurrence slot keys such as `slot-1`, while the accepted backend contract
  parses each key as UUID. Covered by V19; slot identity generation moved to
  the ViewModel's injected stable key factory.
- **V19** — Every client-created game command, series revision, and recurrence
  slot identity SHALL be an opaque UUID generated outside the visual
  composable, persisted with the draft, and reused unchanged across
  recomposition, restart, validation failure, and retry.
- **B51 | 2026-07-19** — The first T42 common-test compile omitted coroutine
  imports for the experimental test annotation and bounded effect wait,
  cascading into inference errors. No new invariant added; cross-target test
  compilation already catches incomplete portable test imports.
- **B52 | 2026-07-19** — The first executing T42 suite expected a bare BRL
  value while the accessible detail copy prefixes it with `Valor:`. No new
  invariant added; the UI test now asserts the complete visible label.
- **B53 | 2026-07-20** — The first T45 compile exposed that Spring JdbcClient
  scalar UUID queries are nullable at the Kotlin boundary, even for NOT NULL
  columns. No new invariant added; JDBC adapters explicitly discard impossible
  null scalar results before passing identifiers into domain contracts.
- **B54 | 2026-07-20** — The next T45 integration-test compile imported both
  JUnit's `Test` and Kotlin's `Test` through wildcards, making annotations
  ambiguous. No new invariant added; integration tests use explicit JUnit
  lifecycle annotations and explicit Kotlin assertion imports.
- **B55 | 2026-07-20** — The first T46 controller-test compile constructed
  `RequestIdentity` positionally with the wrong field order. No new invariant
  added; authenticated HTTP fixtures use named identity arguments to remain
  stable as optional principal metadata evolves.
- **B56 | 2026-07-20** — The first combined T46 HTTP gate was denied before
  Gradle startup when the managed sandbox could not open its wrapper lock in
  the user Gradle cache. No new invariant added; the identical approved Gradle
  command must be rerun with the required filesystem permission.

## Success criteria

- [ ] Organizer registers a complete private group with required profile and any
  optional defaults without creating a first game.
- [ ] All confirmed modalities, compositions, levels, and court play styles work
  with exact conditional cleanup and custom values.
- [ ] Future games copy defaults, support one-time/weekly scheduling, and preserve
  occurrence history under single/future edits.
- [ ] Attendance never overbooks and FIFO waitlist promotion is concurrency-safe.
- [ ] Per-game/monthly charges and expenses are tracked manually, privately, and
  auditably without payment processing.
- [ ] Existing groups migrate without identity, owner, membership, invitation,
  or selection loss.
- [ ] One opaque invitation link works across cold, warm, authenticated, and
  install-deferred opens without private preview or duplicate redemption.
- [ ] Complete backend, mobile, native, safety, and aggregate gates pass with no
  skipped or weakened tests.
