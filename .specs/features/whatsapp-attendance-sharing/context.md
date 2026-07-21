# WhatsApp Attendance Sharing Context

**Gathered:** 2026-07-21
**Spec:** `.specs/features/whatsapp-attendance-sharing/spec.md`
**Status:** Confirmed

---

## Feature Boundary

The owner posts a game-scoped Saqz link in an existing volleyball WhatsApp
group. Registered members open the exact game in Saqz and explicitly confirm;
install or authentication resumes the destination. The owner or admin can
generate and share one image containing the authoritative confirmed, waitlisted,
and declined names. The feature does not automate a WhatsApp group or store a
WhatsApp phone number/username.

---

## Implementation Decisions

### WhatsApp Entry Point

- MVP is link-first, not a bot inside the WhatsApp group.
- The owner or admin manually sends the Saqz link through the native share sheet to the existing group.
- WhatsApp is a distribution destination, not an authoritative identity or attendance system.
- The link contains only an opaque capability and must not expose group/game/member data.

### Athlete Confirmation

- Opening the link never changes attendance by itself.
- The app opens the exact game and the athlete performs one explicit confirmation action.
- Existing backend capacity and waitlist rules determine `CONFIRMED` versus `WAITLISTED`.
- A user without the app/session installs or authenticates and then resumes the same pending game.
- Saqz identity plus group membership identifies the athlete; no WhatsApp identifier is collected.

### Shared Attendance Image

- The export contains three nominal sections: confirmed, waitlisted, and declined (`Fora`).
- Waitlisted entries include their stable positions.
- No-response members are omitted.
- The export is one vertically sized image containing every returned name; it is not paginated.
- The image includes game context but does not display a snapshot timestamp.
- The owner or admin receives a privacy notice before names leave Saqz through the share sheet.

### Authorization

- Link management and nominal image export are available to `OWNER` and `ADMIN`.
- Athletes can resolve links only for groups in which they are current members.
- Invalid/non-member resolution reveals no private group or game details.

### Agent's Discretion

- Exact visual composition of the exported image, within existing Saqz visual language and specified content/readability constraints.
- Exact retry copy and progress treatment, using existing design-system patterns.
- Deterministic tie-breaker for duplicate display names, provided internal IDs are not exposed.
- Temporary image format and dimensions, provided Android/iOS sharing and readability criteria pass.

### Declined / Undiscussed Gray Areas -> Assumptions

- Active capability lifetime follows published game state and attendance deadline, with explicit owner/admin rotation.
- Members with no response are omitted from the image.
- The artifact is one image and does not display a snapshot timestamp.

---

## Specific References

- The workflow begins in the volleyball group's existing WhatsApp conversation.
- The desired result is a low-friction path from that conversation to the correct Saqz game confirmation.
- The return artifact must visibly separate `Confirmados`, `Lista de espera`, and `Fora`.
- WhatsApp usernames are optional and rolling out, but this feature intentionally does not depend on either username or phone number.

---

## Deferred Ideas

- Saqz bot in a private one-to-one WhatsApp Business conversation.
- Saqz number participating directly in ordinary WhatsApp groups, pending explicit official platform support and eligibility evidence.
- Automated outbound reminders, message templates, delivery tracking, and WhatsApp Cloud API pricing/consent work.
- Browser-based confirmation for users without the mobile app.
