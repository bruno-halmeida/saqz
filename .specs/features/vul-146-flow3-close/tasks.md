# Tasks

## T1 — Route model and serializer (done)

- Files: `GroupsRoute.kt`, `SaqzLocalNavConfiguration.kt`
- Done when: all Fluxo 3 concrete routes exist with required arguments and are registered for
  state restoration.
- Verify: groups route tests plus compile of compose-app.
- Commit: `feat(groups): add flow three navigation routes`

## T2 — NavHost screen wiring (done)

- Files: `SaqzNavHost.kt`
- Done when: every Fluxo 3 Root is reachable, detail/members callbacks are wired, and existing
  VUL-149 `onPickPhoto` wiring is unchanged.
- Verify: navigation tests, grep for closeout TODOs, compose-app tests.
- Commit: `feat(navigation): wire flow three destinations`

## T3 — Coordinator/auth journey wiring (done)

- Files: `SaqzNavHost.kt`, `SaqzKoinBootstrap.kt`
- Done when: coordinator starts before composition, auth transitions call it, effects map to
  landing/access/athlete/group outcomes, and registration preview context is passed.
- Verify: coordinator tests plus NavHost journey tests.
- Commit: `feat(invite): connect deferred redemption to navigation`

## T4 — Koin feature graph and platform ports (done)

- Files: `SaqzKoinBootstrap.kt`, `SaqzPlatformDependencies.kt`, `AndroidAppComposition.kt`,
  `SaqzIOSApp.swift`, `IOSInviteAdapters.swift` only if signatures require it.
- Done when: all per-ticket modules resolve and Android/iOS native invite ports are bound.
- Verify: Koin graph tests, Android/iOS compile checks, detekt.
- Commit: `feat(di): install flow three modules and native ports`

## T5 — Remove JoinWithCode (done)

- Files: group-list contract/view/root/sections/tests and `SaqzNavHost.kt` as required.
- Done when: no `JoinWithCode` symbol/reference remains and the list snapshot reflects the
  removed action.
- Verify: group presentation tests, Roborazzi re-record.
- Commit: `refactor(groups): remove join with code`

## T6 — Full verification and evidence (done with documented blockers)

- Files: PR evidence only unless a failing gate requires a scoped fix.
- Done when: required gates, detekt, final greps, screenshot publication/URL checks, and manual
  deferred-link limitations/evidence are recorded.
- Verify: exact ticket gate commands with JDK 21.
- Commit: no source commit unless a scoped fix is needed.

## Gate commands

- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home ./gradlew :features:groups:presentation:allTests :features:groups:allTests :features:access:allTests :compose-app:allTests`
- Detekt tasks for every touched module, discovered from Gradle task graph.

## Evidence

- Roborazzi host re-record passed for the groups presentation module; screenshots are published
  under `vul-141/` through `vul-144/` and `vul-146/` on the `screenshots` branch, with all 24
  raw URLs checked at HTTP 200.
- `xcodebuild -list` did not complete in the local environment; Kotlin iOS simulator targets
  passed through Gradle.
