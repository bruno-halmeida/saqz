package br.com.saqz.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.LocalSaqzChrome
import br.com.saqz.designsystem.theme.SaqzTheme

internal const val SaqzBottomNavBarTag = "saqz-bottom-nav-bar"

internal fun saqzBottomNavItemTag(index: Int) = "saqz-bottom-nav-item-$index"

// A generic nav item. This chrome never knows about SaqzDestination — the app maps its
// own routes to items. `icon` is a caller-supplied composable so no icon dependency leaks.
data class SaqzBottomNavItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
)

@Composable
fun SaqzBottomNav(
    items: List<SaqzBottomNavItem>,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    val chrome = LocalSaqzChrome.current
    val metrics = SaqzTheme.metrics
    val colors = SaqzTheme.colors
    // Bottom inset applied once, below the 56dp bar (safe-area breathing room).
    val bottomInset = with(LocalDensity.current) {
        contentWindowInsets.only(WindowInsetsSides.Bottom).getBottom(this).toDp()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = metrics.bottomNavRadius,
                    topEnd = metrics.bottomNavRadius,
                ),
            )
            .background(chrome.surface)
            .testTag(SaqzBottomNavBarTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.bottomNavHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .sizeIn(
                            minWidth = metrics.minimumTouchTarget,
                            minHeight = metrics.minimumTouchTarget,
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClickLabel = item.label,
                            role = Role.Tab,
                        ) { item.onClick() }
                        .semantics { selected = item.selected }
                        .testTag(saqzBottomNavItemTag(index)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    item.icon()
                    Text(
                        text = item.label,
                        style = SaqzTheme.typography.navigation,
                        color = if (item.selected) colors.primary else colors.textSecondary,
                    )
                }
            }
        }
        if (bottomInset > 0.dp) {
            Box(modifier = Modifier.fillMaxWidth().height(bottomInset))
        }
    }
}

@Preview
@Composable
private fun SaqzBottomNavPreview() = SaqzTheme {
    SaqzBottomNav(
        items = listOf(
            SaqzBottomNavItem("Início", true, {}) { Text("⌂") },
            SaqzBottomNavItem("Perfil", false, {}) { Text("◉") },
        ),
    )
}
