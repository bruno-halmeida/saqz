# VUL-146 — Fecho do Fluxo 3

## Scope

Close the mobile Fluxo 3 integration on the KMP/Compose app by wiring its routes, invite
coordinator, Koin modules, platform ports, member actions, and removal of the obsolete
JoinWithCode path.

## Acceptance criteria

### AC-01 — Invite management routes

The app exposes serializable routes for group invite management (3a, WhatsApp preview 3b,
QR 3c), invite landing (3d/3l), request-sent/error states (3e/3f), athlete registration
(3j/3k), and member editor (3g), each carrying the route arguments required by its Root.

### AC-02 — Group entry points

The group detail invite effect and the members invite action navigate to the invite management
root with the originating `groupId`. The members sheet editor action navigates to member editor
with both `groupId` and `userId`.

### AC-03 — Invite journey effects

An invite link captured before composition is delivered to the coordinator before access route
selection. Signed-out links persist and lead to the access journey; after authentication the
pending invite is redeemed automatically. Coordinator outcomes preserve the exact group id and
status: `JOINED` enters athlete registration first, while `PENDING` enters the existing request
sent UI and terminal/transient failures enter the existing landing error UI.

### AC-04 — Registration context

When pending invite preview data is available, the register Root receives a
`RegisterInviteContext` with group name, inviter name, and approval mode. When preview is not
available, registration receives the existing generic invite context.

### AC-05 — Koin graph

The bootstrap installs the per-ticket invite data/presentation modules and the coordinator
module. The platform graph provides the native invite URL store, share, clipboard, and group-link
ports on Android and iOS, without changing existing access/photo bindings.

### AC-06 — JoinWithCode removal

`JoinWithCode`, `OpenJoinWithCode`, the list button/callback, NavHost stub, and corresponding test
case/reference are absent. Group list APIs have no dead callback or parameter.

### AC-07 — Verification hygiene

There are no remaining Fluxo 3 TODOs in the closeout-owned navigation surface or JoinWithCode
references. Existing VUL-149 `onPickPhoto` wiring remains intact. Required tests/detekt gates run
with JDK 21; manual deferred deep-link limitations are documented honestly if the Branch test key
or emulator evidence is unavailable.
