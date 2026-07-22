# Validation — mobile SOLID refactor wave 2

## Scope
Feature: `mobile-solid-refactor-wave-2`  
Tasks covered: T07–T08 (HTTP→domain failure mapping centralization and presentation consumption).  
Date: 2026-07-22  
Verifier: self (author performed explicit fresh-eyes pass after cooling down).

## Commits
- `b0345e0` — `refactor(groups): centralize http-to-domain failure mapping in the data layer`
- `f8d0d15` — `refactor(groups): consume data-layer failure mappers from presentation`
- `c7e09ff` — `docs(specs): mark T08 completed`

## Acceptance criteria evidence

### T07
- [x] Created `NetworkError.toSetupFailure()`, `toAdministrationFailure()`, `toPhotoFailure()`, and `toDeferredLinkFailure()` in `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/data/NetworkErrorMappers.kt`.
- [x] Created `NetworkErrorMappersTest.kt` with 14 unit tests covering each mapped status/code plus unknown/transport cases.
- [x] Promoted `isProblem` to a single top-level helper in `core/network` (`NetworkModels.kt`); the two duplicated wrappers in `GroupSetupSupport.kt` and `GroupAdministrationCoordinator.kt` now delegate to it.
- [x] Gate passed:
  ```
  rtk ./gradlew :core:network:allTests :features:groups:allTests --console=plain
  BUILD SUCCESSFUL
  ```

### T08
- [x] `GroupSetupViewModel`, `GroupAdministrationCoordinator`, `GroupPhotoCoordinator`, `DeferredInviteCoordinator`, and `DeferredAttendanceLinkCoordinator` no longer inspect `ApiProblemError` by status:
  ```
  $ rtk rg "ApiProblemError" mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation
  (no output)
  ```
- [x] `SetupFailure.Validation` and `AdministrationFailure.Validation` now carry `fieldErrors` so presentation can still populate form errors without touching `ApiProblemError`.
- [x] Existing suites (`GroupSetupViewModelTest`, `GroupPhotoCoordinatorTest`, `DeferredInviteStateMachineTest`, `DeferredAttendanceLinkStateMachineTest`, `GroupAdministrationStateMachineTest`) pass unchanged.
- [x] Gate passed:
  ```
  rtk ./gradlew :features:groups:allTests :compose-app:allTests --console=plain
  BUILD SUCCESSFUL
  ```

## Notes
- The `isProblem` helper is intentionally a plain top-level function rather than an extension because the same extension was not visible across KMP module boundaries to `:features:groups` (compiler reported `Unresolved reference 'isProblem'`). A comment in `NetworkModels.kt` records this.
- `rtk scripts/check-credentials` passed.
- `rtk scripts/check-scope` failed on a pre-existing false positive: the word "persistence" appears in a test-name string inside `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/AccessViewModelTest.kt:345` (`fun 'session invalidator exposed to runtime invalidator'...`). That file is unchanged by this feature and the match is in `commonTest`, which the scope script currently treats as production code. This failure is unrelated to T07/T08.

## Conclusion
T07 and T08 acceptance criteria are met; the presentation layer no longer inspects `ApiProblemError` status codes directly, and all targeted mobile tests pass.
