package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SaqzMetrics(
    val grid: Dp,
    val subGrid: Dp,
    val horizontalPadding: Dp,
    val sectionVerticalPadding: Dp,
    val utilityCardPadding: Dp,
    val primaryButtonRadius: Dp,
    val compactControlRadius: Dp,
    val cardRadius: Dp,
    val bottomNavHeight: Dp,
    val bottomNavRadius: Dp,
    val minimumTouchTarget: Dp,
) {
    companion object {
        val Default = SaqzMetrics(
            grid = 8.dp,
            subGrid = 4.dp,
            horizontalPadding = 16.dp,
            sectionVerticalPadding = 48.dp,
            utilityCardPadding = 16.dp,
            primaryButtonRadius = 12.dp,
            compactControlRadius = 8.dp,
            cardRadius = 16.dp,
            bottomNavHeight = 56.dp,
            bottomNavRadius = 24.dp,
            minimumTouchTarget = 48.dp,
        )
    }
}
