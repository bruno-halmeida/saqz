# Group Management Context

**Status:** Confirmed
**Date:** 2026-07-19
**Feature:** `.specs/features/group-management/`

## Why this feature exists

Authentication and access already establish verified users, a single group
owner, memberships, roles, private invitations, and group selection. The group
itself is too thin to operate a real Saqz community: its only user-facing
persisted attributes are `name` and `time_zone`, and no Game, Attendance, or
Finance model exists.

This feature gives Group its own complete product contract. Registration
captures group identity plus reusable defaults, but does not force creation of
the first game. Later games copy those defaults and own their historical data.

## Current code evidence

- `backend/features/access/src/main/resources/db/migration/V1__create_access_schema.sql`
  persists only `id`, `owner_user_id`, `creation_key`, `name`, `time_zone`,
  `version`, and timestamps in `access_groups`.
- `AccessGroupController.kt` accepts only `requestId`, `name`, and `timeZone`.
- `AccessGroupReadController.kt` returns only `id`, `name`, `timeZone`, `role`,
  and `version`.
- `GroupOnboardingScreens.kt` asks the user to type name and a raw timezone.
- Memberships, roles, and invitations already exist as separate resources.
- No media storage, venue, game, recurrence, attendance, waitlist, charge,
  payment, or expense implementation exists.
- The public landing promises games, confirmations, payments, and expenses such
  as court rental, but those claims do not yet have product code.

## Confirmed product decisions

| Topic | Decision |
| --- | --- |
| Supported modalities | Court volleyball, beach volleyball, and footvolley. |
| Composition | Required: women, men, or mixed. |
| Court play style | Optional for court volleyball only: `6-0`, `4-2`, `5-1`, or custom. |
| Level | Optional: beginner, intermediate, advanced, mixed levels, or custom. |
| Optional profile fields | Photo/logo, description, and city appear during registration and remain editable. |
| Timezone | Required system data detected automatically; users never type an IANA identifier. |
| Privacy | Every group is private and invite-only; there is no public discovery. |
| Editing | `OWNER` and `ADMIN` edit group information; `ATHLETE` is read-only. |
| Lifecycle | Group deletion, ownership transfer, and leaving are out of scope. |
| First game | Registration does not create or require a game. |
| Group defaults | Venue, regular slots, capacity, confirmation deadline, per-game fee, monthly fee, and due day may be configured during registration. |
| Game scheduling | One-time or weekly recurring; recurrence may use multiple weekdays. |
| Attendance | Confirmed, declined, or waitlisted; waitlist promotion is automatic. |
| Payments | Saqz tracks per-game and monthly charges manually; it never receives or processes money. |
| Expenses | Manual group expense tracking is included; Saqz does not reimburse or settle expenses. |

## Clarifying defaults accepted for this draft

- A confirmed athlete who withdraws keeps the game charge pending until an
  organizer explicitly cancels or waives it.
- Currency is BRL only in this version.
- Partial payments and refunds are not modeled.
- Expenses and complete group finance data are visible only to `OWNER` and
  `ADMIN`; an athlete sees only their own charges.

## Domain ownership

| Concept | Owns | Must not own |
| --- | --- | --- |
| Group | Identity, private profile, reusable game/finance defaults. | Historical game, attendance, charge, or expense values. |
| Venue | Reusable place name, address, and optional court identifier. | Game date/time or attendance. |
| Game series | Weekly recurrence rule and future-occurrence editing boundary. | Group profile defaults after creation. |
| Game | One occurrence’s schedule, venue snapshot/reference, capacity, deadline, fee, and lifecycle. | Monthly membership fee. |
| Attendance | One member’s response to one game and waitlist order. | Payment truth. |
| Charge | Expected amount for one member and one game/month. | Actual money movement. |
| Expense | Organizer-recorded group cost and audit history. | Reimbursement or settlement processing. |

## Relationship to authentication/access

Authentication/access remains authoritative for identity, bootstrap, group
selection, owner/admin/athlete roles, memberships, and invitations. This feature
supersedes only the group metadata portions of `GROUP-01` and `GROUP-06`, while
retaining atomic owner creation, idempotency, authorization, and optimistic
concurrency.

## Open questions

None blocking. Field limits and lifecycle rules in `spec.md` are safe initial
contracts and may be amended before design.
