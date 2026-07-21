# WhatsApp Attendance Sharing Specification

**Status:** Confirmed

## Problem Statement

Volleyball organizers coordinate attendance in existing WhatsApp groups, while
Saqz already owns the authoritative game, confirmation, capacity, and waitlist
state. Requiring athletes to leave that conversation and manually find the
correct game creates avoidable friction, and manually maintained WhatsApp lists
can diverge from Saqz.

This feature lets an owner share one game-scoped Saqz link in the existing
WhatsApp group. A registered athlete opens the correct game, explicitly
confirms in Saqz, and the owner can later share an image generated from the
authoritative confirmed, waitlisted, and declined lists.

## Goals

- [ ] An owner or admin can share a privacy-safe link that opens the exact published game.
- [ ] An authenticated group member can explicitly confirm from that link without manually navigating to the game.
- [ ] Installation or authentication does not lose the pending game destination.
- [ ] An owner or admin can share one image with the authoritative confirmed, waitlisted, and declined names.
- [ ] The flow does not require storing a WhatsApp phone number or username.

## Out of Scope

| Feature | Reason |
| --- | --- |
| A Saqz number or bot inside a normal WhatsApp group | Current public WhatsApp Business Platform documentation does not establish this as a generally available supported integration. |
| WhatsApp phone number or `@username` collection | The authenticated Saqz account identifies the athlete after link opening; collecting an external identifier adds privacy risk without helping this flow. |
| Automatic WhatsApp group membership synchronization | WhatsApp membership is not authoritative for Saqz group access. |
| Confirmation caused only by opening a link | A deliberate in-app action is required to prevent accidental attendance changes. |
| Sending messages through WhatsApp Cloud API | MVP uses the operating-system share sheet and existing WhatsApp group. |
| Browser-based attendance | There is no authenticated browser product; the link resumes in the Android/iOS app. |
| Displaying members with no response in the shared image | The requested three lists are confirmed, waitlisted, and explicitly declined. |
| Scheduled or automatic image posting | The owner explicitly generates and shares each snapshot. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Primary channel | A game link is manually shared into the existing WhatsApp group. | It is supported without relying on undocumented group-bot behavior. | Yes |
| Attendance action | Link opening shows the game and requires one explicit confirmation action. | Prevents accidental mutations and preserves current attendance rules. | Yes |
| Installation/authentication | Preserve the pending destination through install and login, then resume it. | The user explicitly requested deferred continuation. | Yes |
| Shared image | Show nominal confirmed, waitlisted, and declined sections. | Matches the organizer's requested WhatsApp artifact. | Yes |
| WhatsApp identity | Store neither phone nor username for this feature. | Saqz authentication and membership provide stronger internal identity. | Yes |
| Link manager | `OWNER` and `ADMIN` create, copy, or rotate a game attendance link. | Both organizer roles manage the workflow. | Yes |
| Image manager | `OWNER` and `ADMIN` generate the nominal image. | Both organizer roles manage the workflow. | Yes |
| Link lifetime | Valid only while the target game is `PUBLISHED` and its attendance deadline has not passed; rotation invalidates the prior link. | Prevents stale links from remaining actionable. | Yes |
| Image format | Generate one vertically sized image containing all three nominal lists; do not paginate. | The organizer wants one WhatsApp artifact. | Yes |
| Image timestamp | Do not display a snapshot timestamp. | The organizer does not need one in the shared artifact. | Yes |
| Member names | Use current Saqz display names, ordered by attendance rules. | No WhatsApp identity is available or needed. | Yes |

**Open questions:** none - all unresolved choices are recorded as assumptions above.

---

## User Stories

### P1: Share a game attendance link - MVP

**User Story:** As a group owner or admin, I want to share a link for one published game
in my WhatsApp group so that members reach the correct confirmation flow.

**Why P1:** The link is the bridge between the group's current coordination
channel and Saqz's authoritative attendance state.

**Acceptance Criteria:**

1. WHEN an `OWNER` or `ADMIN` requests an attendance link for a `PUBLISHED` game at or before its confirmation deadline THEN Saqz SHALL return one HTTPS Branch Long Link containing only an opaque attendance capability.
2. WHEN Saqz creates that link THEN neither the URL nor provider metadata SHALL contain a group ID, game ID, group name, game title, member identity, email, phone number, WhatsApp username, attendance state, or finance data.
3. WHEN an `ATHLETE`, non-member, or unauthenticated caller requests link management THEN the backend SHALL reject it without creating or exposing a capability.
4. WHEN an owner or admin rotates the attendance link THEN the prior capability SHALL become invalid immediately and the new link SHALL target the same game.
5. WHEN the game is no longer `PUBLISHED` or its attendance deadline has passed THEN Saqz SHALL refuse new link creation and SHALL treat an existing capability as terminally non-actionable.
6. WHEN the native share sheet is unavailable, cancelled, or fails THEN Saqz SHALL preserve all game and attendance state and allow the owner to retry sharing the same active link.

**Independent Test:** Create a published game as owner or admin, share its link, inspect
that the URL contains only an opaque capability, rotate it, and verify the old
link no longer resolves.

---

### P1: Resume the exact game from WhatsApp - MVP

**User Story:** As a registered athlete, I want a shared link to open the exact
game and let me confirm deliberately so that I do not need to navigate or
identify myself by WhatsApp data.

**Why P1:** This is the athlete-facing value and removes the main coordination
friction without weakening authentication or group authorization.

**Acceptance Criteria:**

1. WHEN Android or iOS receives a valid attendance link from cold start, warm open, Universal/App Link, or Branch install-deferred delivery THEN Saqz SHALL normalize it into one pending opaque capability.
2. WHEN no verified Saqz session exists THEN Saqz SHALL preserve only the opaque capability through safe restart, complete installation/registration/login, and resume the destination afterward.
3. WHEN an authenticated current group member resolves a valid capability THEN Saqz SHALL select the target group and display the target game's current authoritative attendance state without changing it.
4. WHEN the member explicitly confirms at or before the confirmation deadline THEN the existing authoritative attendance rules SHALL produce `CONFIRMED` when capacity exists or `WAITLISTED` with the stable FIFO position when full.
5. WHEN the member is already `CONFIRMED` or `WAITLISTED` and repeats the confirmation flow THEN Saqz SHALL return the existing equivalent state without a duplicate attendance record, position, event, or charge.
6. WHEN a non-member, expired/rotated/unknown capability, cancelled/completed/draft game, or passed deadline is resolved THEN Saqz SHALL reveal no private group/game data and SHALL not mutate attendance.
7. WHEN direct and Branch delivery report the same link event THEN Saqz SHALL open at most one game destination and SHALL not submit attendance without the explicit member action.
8. WHEN capability resolution fails temporarily THEN Saqz SHALL keep the pending capability retryable; WHEN it fails terminally, the user discards it, or the user logs out THEN Saqz SHALL clear it.

**Independent Test:** Open a game link on a device without an active session,
complete authentication, verify the exact game opens unchanged, tap confirm,
and observe either confirmed or the authoritative waitlist position.

---

### P1: Share an authoritative attendance image - MVP

**User Story:** As a group owner or admin, I want to share a readable image of attendance
in WhatsApp so that the group sees a consistent snapshot without manually
rewriting names.

**Why P1:** The shared artifact closes the coordination loop in WhatsApp while
keeping Saqz authoritative.

**Acceptance Criteria:**

1. WHEN an `OWNER` or `ADMIN` requests a share snapshot for a game in their group THEN the backend SHALL return one consistent authoritative snapshot containing the current game identity needed for presentation and nominal `CONFIRMED`, `WAITLISTED`, and `DECLINED` lists.
2. WHEN an `ATHLETE` or non-member requests the nominal snapshot THEN the backend SHALL return `403` or privacy-preserving `404` without member data.
3. WHEN the snapshot is returned THEN confirmed members SHALL be ordered by display name, waitlisted members SHALL be ordered by stable FIFO position and display that position, and declined members SHALL be ordered by display name.
4. WHEN a member has no attendance response THEN that member SHALL not appear in any of the three image sections.
5. WHEN the app renders the image THEN it SHALL include the game title, local date/time, venue, capacity, the three labeled sections, and each section count in pt-BR, without a snapshot timestamp.
6. WHEN a section is empty THEN the image SHALL show its heading, a zero count, and an explicit empty-state label rather than omit the section.
7. WHEN the owner or admin chooses to share THEN Saqz SHALL show a privacy notice that names will leave Saqz, require explicit continuation, and then open the native share sheet with the generated image.
8. WHEN image generation, temporary file creation, or native sharing fails or is cancelled THEN Saqz SHALL show a retryable outcome, remove abandoned temporary files, and leave attendance unchanged.
9. WHEN the image is rendered on supported Android or iOS devices THEN Saqz SHALL generate one vertically sized image containing every returned name and waitlist position in the three sections without clipping or pagination.

**Independent Test:** Populate all three attendance states, generate the
snapshot as owner or admin, verify ordering and content, accept the privacy notice,
and share the generated image through the native sheet.

---

## Edge Cases

- WHEN capacity changes or a waitlisted member is promoted during image generation THEN the exported data SHALL represent one consistent authoritative server snapshot.
- WHEN two members have the same display name THEN both SHALL appear as separate rows with deterministic backend ordering that does not expose internal IDs.
- WHEN a display name contains supported diacritics or reaches its valid maximum length THEN image rendering SHALL preserve the name and wrap or ellipsize deterministically without overlapping adjacent rows.
- WHEN the nominal content grows THEN the image height SHALL grow to preserve every returned row in one image without reducing the established export typography size.
- WHEN Branch is unavailable during link creation THEN no capability SHALL be reported as shareable and the owner SHALL receive a retryable failure.
- WHEN an invalid capability is submitted repeatedly THEN the existing limit of ten invalid attempts per authenticated user in ten minutes SHALL apply with `Retry-After`, without revealing whether any group or game exists.
- WHEN the app receives a link for a different group than the currently selected group THEN private state for the old selection SHALL be invalidated before the target group is displayed.

---

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --- | --- |
| Input validation and bounds | Opaque capability only; game lifecycle/deadline validation; existing display-name bounds; one image sized from the bounded membership result set. |
| Failure and partial-failure states | Branch, resolution, rendering, temporary-file, and share-sheet failures are explicit and never mutate attendance by themselves. |
| Idempotency, retry, and duplicates | Link deliveries are deduplicated; attendance uses existing idempotent commands; terminal versus retryable capability outcomes are explicit. |
| Auth boundaries and rate limits | Owner-only link/image management; authenticated same-group member resolution; existing invalid-capability throttle. |
| Concurrency and ordering | Existing locked attendance aggregate remains authoritative; one read produces a consistent snapshot; waitlist is FIFO and other lists deterministic. |
| Data lifecycle and expiry | Capability expires with rotation, non-published lifecycle, or deadline; pending local capability clears on terminal outcome/discard/logout; temporary images are deleted after sharing/cancellation. |
| Observability | Record capability create/rotate/resolve outcome, snapshot generation, and share launch outcome without logging capabilities, names, contact data, or private game fields. |
| External-dependency failure | Branch and native share failures are retryable and isolated; WhatsApp itself is not a backend dependency. |
| State-transition integrity | Link resolution is read-only; only explicit confirmation enters the existing attendance state machine. |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| WA-01 | P1: Create privacy-safe game link | Tasks | In Tasks |
| WA-02 | P1: Restrict and rotate game link | Tasks | In Tasks |
| WA-03 | P1: Share link through native sheet | Tasks | In Tasks |
| WA-04 | P1: Normalize and persist deferred destination | Tasks | In Tasks |
| WA-05 | P1: Authorize and resolve exact game | Tasks | In Tasks |
| WA-06 | P1: Explicit idempotent confirmation | Tasks | In Tasks |
| WA-07 | P1: Handle terminal/retryable/deduplicated delivery | Tasks | In Tasks |
| WA-08 | P1: Read organizer-only authoritative nominal snapshot | Tasks | In Tasks |
| WA-09 | P1: Render one complete readable image | Tasks | In Tasks |
| WA-10 | P1: Require privacy acknowledgement and share images | Tasks | In Tasks |
| WA-11 | Cross-cutting privacy, lifecycle, and observability | Tasks | In Tasks |

**Coverage:** 11 total, 11 mapped to tasks, 0 unmapped.

---

## Success Criteria

- [ ] A registered same-group member can go from a WhatsApp-shared link to the exact game and submit an explicit confirmation without manual game navigation.
- [ ] Deferred install/authentication resumes the same game in automated Android and iOS journeys.
- [ ] Capability URLs and logs contain no group, game, member, contact, attendance, or finance data.
- [ ] Repeated delivery or confirmation creates no duplicate attendance, waitlist position, audit event, or charge.
- [ ] Owner/admin-generated images exactly match one authoritative backend snapshot for all three included states.
- [ ] Non-owner and non-member tests prove nominal attendance data remains private.
