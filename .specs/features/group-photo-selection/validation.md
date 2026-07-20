# Group Setup Photo and Money Input Validation

**Date**: 2026-07-20
**Spec**: inline acceptance criteria from the user request
**Diff range**: `abe0da5^..0c28f15`
**Verifier**: independent sub-agent (author != verifier)

## Acceptance Criteria

| Criterion | Expected outcome | Evidence | Result |
| --- | --- | --- | --- |
| AC1 full photo card click | Any point in the compact card opens photo sources | `GroupSetupScreenTest.kt:93` asserts a clickable full-card node contains the preview center; `:102` clicks it and `:103-106` assert sheet actions | PASS |
| AC2 automatic centered crop | No manual controls; new selections use centered `GroupPhotoCrop()` | `GroupPhotoEditorTest.kt:84-86` asserts all six controls absent; `GroupPhotoCoordinatorTest.kt:41` asserts `GroupPhotoCrop()` | PASS |
| AC3 confirm selected photo | Confirm marks prepared and returns to compact replaceable preview | `GroupPhotoEditorTest.kt:94-95` asserts prepared without upload; `:117-120` asserts confirm disappears and compact picker remains | PASS |
| AC4 matching selected shape | Selected and empty compact previews share 112 dp rounded clipping | `GroupPhotoEditor.kt:132` supplies 112 dp in both states; `:291-295` clips content with the same themed card shape | UAT pending |
| AC5 incremental money typing | `1`, `2`, `3` remains `123` and emits 12300 cents | `GroupSetupScreenTest.kt:287-289` asserts visible text and cents | PASS |
| AC5 external reconciliation | A genuine external change replaces local text | `GroupSetupScreenTest.kt:305-307` asserts `1,00` changes to `25,00` | PASS |

## Discrimination Sensor

| Mutation | Target | Result |
| --- | --- | --- |
| Disable full-card picker clickability | Compact photo picker | Killed by `GroupSetupScreenTest` |
| Restore unconditional money reformat on recomposition | `MoneyInput` | Killed by incremental-order test |

The mutations ran in a temporary worktree. The real dirty worktree was not modified.

## Gates

- `rtk ./gradlew :features:groups:allTests`: PASS, 499 tests, 0 failed.
- `rtk ./gradlew :features:groups:compileAndroidMain`: PASS.
- `rtk scripts/check-gradle`: did not reach feature assertions because the first run inherited JDK 17 while the backend requires JDK 21; the approved JDK 21 rerun was interrupted by the user.

## Code Quality

- Changes are limited to the group photo editor, setup state wiring, shared money input, resources, and derived tests.
- Game and monthly values share the verified `MoneyInput` behavior.
- No unrelated worktree changes are included in either implementation commit.

## Summary

**Automated result**: PASS

**UAT remaining**: confirm on device that a selected photo now renders with the same rounded corners as the empty placeholder.
