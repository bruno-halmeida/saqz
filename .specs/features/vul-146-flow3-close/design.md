# Design

## Ownership

`SaqzNavHost` owns routes, cross-feature callbacks, auth/deep-link effect consumption, and
registration context. `SaqzKoinBootstrap` installs feature modules and starts the coordinator
after platform bindings exist. Android/iOS app composition supplies native invite adapters to the
platform dependency graph. `SaqzAppShell` and `settings.gradle.kts` change only if compilation or
shell-level routing requires it.

## Event sequence

```text
native link adapter -> GroupInviteCoordinator.start(listener)
                       -> pending invite storage
                       -> NavHost effect collector
auth session change -> coordinator.onAuthenticated()
                    -> redeem pending -> NavigateToGroup(status)
```

The coordinator is started during platform bootstrap, before `SaqzApp` composes and before the
access gate chooses its first destination. The NavHost remains the sole navigation owner.

## Route arguments

Invite routes carry strings rather than ViewModels: group id, invite code, group/invite preview
values, and member user id. All concrete keys are registered in the tolerant navigation
serializer so rotation/recents can restore them.

## Platform bindings

`GroupsRuntimeDependencies` carries the native invite URL store and share/clipboard ports. The
existing Android and iOS adapter implementations are reused; the bootstrap binds them by port
type alongside the already wired group-link and photo ports.
