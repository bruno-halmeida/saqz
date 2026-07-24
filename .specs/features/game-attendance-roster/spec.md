# Game Attendance Roster Specification

**Status:** Confirmed
**Linear:** VUL-9 (Épico 05 — Gestão de Jogos)

## Problem Statement

Attendance already stores an authoritative confirmed/waitlisted state per game,
but the only nominal roster in the product is the organizer-restricted WhatsApp
share snapshot. A group member opening a game sees counts and their own
response, never who else is confirmed or queued.

Historical attendance also has to survive renames: a name displayed next to a
past game must be the name the athlete had when the response was recorded.

## Goals

- [ ] Every attendance write records the athlete name at response time.
- [ ] A group member reads the confirmed and waitlist rosters of a game, by name.
- [ ] The waitlist is presented in arrival order (`waitlist_sequence`).
- [ ] Existing attendance rows carry a name snapshot.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Mobile roster UI | Dependent ticket VUL-14. |
| Mensalista/avulso attendance rule | Parallel ticket VUL-8. |
| WhatsApp share snapshot changes | The share flow stays exactly as delivered. |
| Declined list in the roster | The requested lists are confirmed and waitlist. |

## Existing Behavior Reused

`V9__add_athlete_attributes.sql` (Épico 04) already added
`game_attendance.member_display_name` as `NOT NULL` with a backfill from
`access_users`, and `JdbcAttendanceCommandRepository.SAVE` already writes it on
insert only. Every attendance write path — self response, organizer override,
withdrawal promotion (`RespondAttendance.promoteOne`) and capacity promotion
(`AdjustGameCapacity`) — funnels through that single `save`, so the snapshot
requirement needed test evidence rather than a second migration.

Roster names reuse the `AttendanceShareSnapshotPerson` shape (`displayName`,
`waitlistPosition`) and its ordering conventions, sourced from the attendance
row snapshot instead of the live membership name.

## Acceptance Criteria

| ID | Criterion |
| --- | --- |
| AC-1 | A new attendance row stores the athlete display name at response time, for self response, organizer override, and both automatic promotions. |
| AC-2 | Renaming an athlete never changes an existing attendance row's stored name; a later response on another game stores the new name. |
| AC-3 | A group member reads `GET /api/groups/{groupId}/games/{gameId}/attendance/roster` and receives confirmed members by name and waitlisted members in `waitlist_sequence` order with their positions. |
| AC-4 | The owner sees the roster; a non-member and a cross-group game are privacy hidden (404). |
| AC-5 | Rows created before the snapshot column exist with a backfilled name. |
| AC-6 | Gates green: `:features:groups:test`, `:features:groups:integrationTest`, `scripts/check-credentials`, `scripts/check-scope`, `scripts/check-gradle`. |

## API

`GET /api/groups/{groupId}/games/{gameId}/attendance/roster`

```json
{
  "confirmed": [{ "memberId": "…", "displayName": "Ana", "waitlistPosition": null }],
  "waitlisted": [{ "memberId": "…", "displayName": "Zeca", "waitlistPosition": 1 }]
}
```

Confirmed members are ordered case-insensitively by name; waitlisted members by
arrival order. Any actor without group membership receives the same 404 the
other attendance reads return.
