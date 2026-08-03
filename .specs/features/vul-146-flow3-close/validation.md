# VUL-146 Round 2 Validation

**Date**: 2026-08-02
**Spec**: `.specs/features/vul-146-flow3-close/spec.md`
**Diff range**: `aa5730ee..979e927f`
**Verifier**: independent scratch-worktree pass; author worktree was not mutated

## Spec-anchored acceptance criteria

| Criterion | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| AC-03: authenticated PENDING redemption navigates to the request-sent landing | Exact invite code and PENDING status survive in the navigation outcome | `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinatorTest.kt:97-99` — `assertEquals(InviteRedeemStatus.PENDING, effect.status)` and `assertEquals("invite-relaunch", effect.inviteCode)`; host consumes `effect.inviteCode` at `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt:153-154` | ✅ PASS |
| AC-03: terminal/transient redeem failure reaches the landing error UI | The landing opens with the exact error state and does not require persisted invite storage | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/invite/InviteLandingViewModelTest.kt:143-145` — `assertFalse(viewModel.state.value.isLoading)`, `assertNull(viewModel.state.value.preview)`, and `assertEquals(InviteLandingError.RateLimited(23), viewModel.state.value.error)`; host maps the effect into `redeemError` at `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt:157-162` | ✅ PASS |
| AC-01/AC-07: restored landing routes retain required route state | Nullable landing error survives route serialization/recreation | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/navigation/GroupsRouteTest.kt:37-48` — `assertEquals(route, restored)` and `assertEquals(InviteLandingRouteError.RateLimited(23), (restored as GroupsRoute.InviteLanding).redeemError)` | ✅ PASS |
| AC-03: authentication handoff is safe across repeated session emissions | A duplicate `onAuthenticated()` does not create a second redeem generation | `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinatorTest.kt:118-127` — `assertEquals(listOf("preview", "redeem"), fixture.gateway.actions)`, exact single navigation effect, and `assertNull(withTimeoutOrNull(1) { fixture.coordinator.effects.first() })`; coordinator guard is at `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinator.kt:98-103` | ✅ PASS |

## Discrimination sensor

Three behavior-level mutations were applied only in `/tmp/vul-146-round2-sensor.ry4ypi` and discarded:

1. Replaced the emitted `inviteCode` with an empty string — PENDING payload test failed.
2. Replaced initial route-error mapping with `error = null` — landing error test failed.
3. Disabled the coordinator authentication guard — duplicate-authentication test failed.

**Result**: 3/3 mutations killed — PASS.

## Gate check

- PASS — JDK 21: `:features:groups:presentation:allTests :features:groups:allTests :features:access:allTests :compose-app:allTests`.
- PASS — detekt for touched modules: `:compose-app:detektAll :features:groups:detektAll :features:groups:domain:detektAll :features:groups:presentation:detektAll :android-app:detektAll`.
- PASS — Android: `:android-app:compileDevDebugKotlin :android-app:compileDevDebugAndroidTestKotlin`.
- PASS — Roborazzi: `:features:groups:presentation:recordRoborazziAndroidHostTest`; no visual change was introduced.
- KNOWN PRE-EXISTING — including `:features:groups:data:detektAll` still fails only on the three `ReturnCount` findings in unchanged `KtorAthleteGateway.kt` lines 261, 293, and 323.
- PASS — final worktree grep has no `TODO(Fluxo 3)`, `JoinWithCode`, `OpenJoinWithCode`, or `onJoinWithCode` references.

**Overall**: ✅ Round 2 correction ready; deferred deep-link manual execution remains blocked by the previously documented missing Branch test key.
