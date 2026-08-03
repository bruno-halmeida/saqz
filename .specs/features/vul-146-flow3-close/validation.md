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

## VUL-146 Round 3 — coordinator-owned invite lifecycle

**Date**: 2026-08-03
**Diff range**: `5379d501..9aabb050`
**Verifier**: independent scratch-worktree sensor; author worktree was not mutated

| Finding | Evidence | Result |
| --- | --- | --- |
| Cold authenticated landing must not remain below the redeemed destination | `GroupInviteCoordinator` queues each effect with its generation and filters stale envelopes at `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinator.kt:65-73`; `cold landing buffered before authentication is discarded after redeem` at `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinatorTest.kt:85-99` | ✅ PASS |
| Landing success must clear persisted invite through the coordinator | `clearPendingInvite(code, onCleared)` owns the read/write and only invokes navigation after cleanup at `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinator.kt:135-159`; idempotence test at `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinatorTest.kt:101-115`; NavHost callback at `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt:393-402` | ✅ PASS |
| Signed-out relaunch must restore invite context before auth | Initial coordinator recovery is outside the Ready branch at `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt:119-132`; code plus preview-before-redeem test at `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinatorTest.kt:132-152` | ✅ PASS |
| Storage failure must become visible landing error | `PendingInviteStorageFailed(code)` is emitted with known code and mapped to serialized `InviteLandingRouteError.Network` at `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt:169-171, 496-502`; coordinator and route tests at `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/invite/GroupInviteCoordinatorTest.kt:117-130` and `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHostTest.kt:69-81` | ✅ PASS |

### Round 3 discrimination sensor

Four independent detached worktrees were mutated and discarded:

1. Disabled generation filtering — the cold buffered landing test failed.
2. Disabled pending cleanup — six coordinator tests, including idempotent landing cleanup, failed.
3. Returned `null` from pending-code recovery — the signed-out relaunch preview test failed.
4. Removed the serialized Network error — the visible storage-error route test failed.

**Result**: 4/4 mutations killed — PASS.

### Round 3 gates

- PASS — JDK 21: `:features:groups:presentation:allTests :features:groups:allTests :features:access:allTests :compose-app:allTests`.
- PASS — detekt type-resolution for touched modules: `:features:groups:detektAll :features:groups:presentation:detektAll :compose-app:detektAll`.
- PASS — Android: `:android-app:compileDevDebugKotlin`.
- PASS — Roborazzi: `:android-app:recordRoborazziDevDebug`; no golden changed.
- PASS — final grep: no `TODO(Fluxo 3)` or `JoinWithCode` in `mobile`.
- KNOWN PRE-EXISTING — full detekt remains blocked only by the three unchanged `ReturnCount` findings in `features/groups/data/.../KtorAthleteGateway.kt` already documented above.
