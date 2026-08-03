# State

## Decisions

- AD-146-001: `e4d09065` is the base; VUL-149 is already merged. Preserve the existing `onPickPhoto` wiring in `SaqzNavHost.kt`.
- AD-146-002: The navigation host remains the single owner of cross-feature callbacks and is the consumer of `GroupInviteCoordinator.effects`.
- AD-146-003: Existing Fluxo 3 resources are authoritative. No new string key is introduced by the closeout.

## Handoff

- Feature: VUL-146
- Status: ready for review (manual deferred-link step blocked by missing Branch test key)
- Branch: `vul-146-fecho`

## Verification notes

- Required Kotlin/Compose gates and Android devDebug compilation pass with JDK 21.
- Detekt passes for compose-app, groups, groups/domain, groups/presentation, and android-app.
- groups/data detekt remains red on three pre-existing ReturnCount findings in
  `KtorAthleteGateway.kt`, which is unchanged by VUL-146.
- Manual deferred deep-link execution is blocked by the fallback-only Branch test key; this was
  reported on VUL-146 before review.
