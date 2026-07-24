# Game Attendance Roster Validation

**Date:** 2026-07-24
**Scope:** VUL-9 — attendance name snapshot evidence + member-visible roster.

## Acceptance Criteria Evidence

| ID | Evidence |
| --- | --- |
| AC-1 | `JdbcAttendanceCommandRepositoryIntegrationTest`: `attendance row stores member display name snapshot at creation` (self), `organizer override records the target member display name snapshot` (override), `withdrawal promotion keeps the snapshot taken when the member joined the queue` (`RespondAttendance.promoteOne`), `capacity promotion keeps the snapshot taken when the member joined the queue` (`AdjustGameCapacity`). |
| AC-2 | `a status update never rewrites the attendance row's member display name snapshot`, `renaming a member changes only future attendance snapshots`, `roster names survive a member rename`. |
| AC-3 | `roster lists confirmed by name and waitlist in arrival order with snapshot names`, `roster omits declined members and keeps confirmed positions empty`, `roster of a game without responses is empty for a member`; controller wiring in `AttendanceControllerTest.member roster read returns confirmed and waitlisted names`. |
| AC-4 | `roster stays visible to the owner and hidden from nonmembers` (owner, outsider, cross-group), `AttendanceControllerTest.nonmember roster read is privacy hidden`. |
| AC-5 | Pre-existing `AthleteAttributesMigrationIntegrationTest` covers the `V9` backfill of `game_attendance.member_display_name`; no new migration was required. |
| AC-6 | Gate runs below. |

## Gate Runs

| Gate | Result |
| --- | --- |
| `./gradlew :features:groups:test` | pass |
| `./gradlew :features:groups:integrationTest` | pass — 44/44 in `JdbcAttendanceCommandRepositoryIntegrationTest`, 0 failures |
| `scripts/check-credentials` | `ok - credential safety` |
| `scripts/check-scope` | `ok - mobile-first scope` |
| `scripts/check-bruno` | `ok - Bruno covers explicit backend routes` (after adding the roster request) |
| `scripts/check-gradle` — backend stages | pass (`:shared-kernel:check`, `:features:identity`, `:features:access`, `:features:groups`, `:bootstrap`, `:architecture-tests`) |
| `scripts/check-gradle` — Android connected stage | not green locally; failures are pre-existing on `main` and untouched by this backend-only change. |

Local runs require JDK 21 and `DOCKER_HOST` pointing at the Colima socket. The
`:features:access:integrationTest` module additionally needs
`TESTCONTAINERS_RYUK_DISABLED=true` when the Colima VM is contended, otherwise
the Ryuk reaper container times out on start.

## Notes

The ticket's gap analysis predated the Épico 04 merge. `V9__add_athlete_attributes.sql`
had already added `game_attendance.member_display_name` (NOT NULL, backfilled) and
`JdbcAttendanceCommandRepository.SAVE` already populated it insert-only, so scope
item 1 was delivered as test evidence over the existing write path instead of a
duplicate column. The WhatsApp share snapshot flow is untouched.
