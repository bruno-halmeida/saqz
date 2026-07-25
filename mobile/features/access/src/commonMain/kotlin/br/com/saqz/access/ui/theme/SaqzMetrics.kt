package br.com.saqz.access.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SaqzMetrics(
    val grid: Dp,
    val subGrid: Dp,
    val horizontalPadding: Dp,
    val sectionVerticalPadding: Dp,
    val primaryButtonRadius: Dp,
    val compactControlRadius: Dp,
    val minimumTouchTarget: Dp,
) {
    companion object {
        val Default = SaqzMetrics(
            grid = 8.dp,
            subGrid = 4.dp,
            horizontalPadding = 16.dp,
            sectionVerticalPadding = 48.dp,
            primaryButtonRadius = 12.dp,
            compactControlRadius = 8.dp,
            minimumTouchTarget = 48.dp,
        )
    }
}
