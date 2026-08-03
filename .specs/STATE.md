# State

## Decisions

- AD-146-001: `e4d09065` is the base; VUL-149 is already merged. Preserve the existing `onPickPhoto` wiring in `SaqzNavHost.kt`.
- AD-146-002: The navigation host remains the single owner of cross-feature callbacks and is the consumer of `GroupInviteCoordinator.effects`.
- AD-146-003: Existing Fluxo 3 resources are authoritative. No new string key is introduced by the closeout.

## Handoff

- Feature: VUL-146
- Status: implementation in progress
- Branch: `vul-146-fecho`
