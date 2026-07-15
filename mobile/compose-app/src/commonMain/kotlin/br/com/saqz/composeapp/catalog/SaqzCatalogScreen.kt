package br.com.saqz.composeapp.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.nav_components
import br.com.saqz.composeapp.resources.nav_home
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBottomNav
import br.com.saqz.designsystem.component.SaqzBottomNavItem
import br.com.saqz.designsystem.component.SaqzBottomSheet
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzCard
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzEmptyState
import br.com.saqz.designsystem.component.SaqzErrorState
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzListItem
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme

internal fun saqzCatalogSectionTag(key: String) = "saqz-catalog-section-$key"
internal fun saqzCatalogColorTag(name: String) = "saqz-catalog-color-$name"
internal fun saqzCatalogMetricTag(name: String) = "saqz-catalog-metric-$name"
internal fun saqzCatalogTypeTag(name: String) = "saqz-catalog-type-$name"
internal fun saqzCatalogVariantTag(name: String) = "saqz-catalog-variant-$name"
internal fun saqzCatalogStateTag(name: String) = "saqz-catalog-state-$name"
internal fun saqzCatalogOwnerItemTag(index: Int) = "saqz-catalog-owner-$index"
internal fun saqzCatalogAthleteItemTag(index: Int) = "saqz-catalog-athlete-$index"

// A 1:1 inspection surface for the design system. Every registry token, typography style,
// metric and component variant/state is rendered from CatalogFixtures. It hosts no avatar,
// login/logout, selectable role or business data — only the foundation, so a designer can
// verify the whole system from the Home entry.
@Composable
fun SaqzCatalogScreen(modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        Section("cores") {
            CatalogColors.forEach { entry ->
                Row(
                    modifier = Modifier.testTag(saqzCatalogColorTag(entry.name)),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
                ) {
                    Box(modifier = Modifier.size(24.dp).background(entry.color))
                    Text(entry.name, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textPrimary)
                }
            }
        }
        Section("tipografia") {
            CatalogTypography.forEach { entry ->
                Text(
                    text = entry.name,
                    style = entry.style,
                    color = SaqzTheme.colors.textPrimary,
                    modifier = Modifier.testTag(saqzCatalogTypeTag(entry.name)),
                )
            }
        }
        Section("metricas") {
            CatalogMetrics.forEach { entry ->
                Text(
                    text = "${entry.name} ${entry.value.value.toInt()}",
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textPrimary,
                    modifier = Modifier.testTag(saqzCatalogMetricTag(entry.name)),
                )
            }
        }
        Section("Button") {
            CatalogButtonVariants.forEach { variant ->
                SaqzButton(
                    label = variant.name,
                    onClick = {},
                    variant = variant,
                    modifier = Modifier.fillMaxWidth().testTag(saqzCatalogVariantTag("Button-${variant.name}")),
                )
            }
            // Disabled and loading states of the primary button.
            SaqzButton(
                label = "disabled",
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().testTag(saqzCatalogVariantTag("Button-disabled")),
            )
            SaqzButton(
                label = "loading",
                onClick = {},
                loading = true,
                modifier = Modifier.fillMaxWidth().testTag(saqzCatalogVariantTag("Button-loading")),
            )
        }
        Section("Input") {
            CatalogInputKinds.forEach { kind ->
                SaqzInput(
                    value = TextFieldValue(),
                    onValueChange = {},
                    label = kind.name,
                    kind = kind,
                    modifier = Modifier.testTag(saqzCatalogVariantTag("Input-${kind.name}")),
                )
            }
        }
        Section("Card") {
            SaqzCard(modifier = Modifier.fillMaxWidth().testTag(saqzCatalogVariantTag("Card-static"))) {
                Text("static", style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
            }
            SaqzCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth().testTag(saqzCatalogVariantTag("Card-interactive")),
            ) {
                Text("interactive", style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
            }
        }
        Section("ListItem") {
            SaqzListItem(
                headline = "static",
                modifier = Modifier.testTag(saqzCatalogVariantTag("ListItem-static")),
            )
            SaqzListItem(
                headline = "interactive",
                onClick = {},
                modifier = Modifier.testTag(saqzCatalogVariantTag("ListItem-interactive")),
            )
        }
        Section("Badge") {
            CatalogBadgeVariants.forEach { variant ->
                SaqzBadge(
                    label = variant.name,
                    variant = variant,
                    modifier = Modifier.testTag(saqzCatalogVariantTag("Badge-${variant.name}")),
                )
            }
        }
        Section("Dialog") {
            var open by remember { mutableStateOf(false) }
            SaqzButton(
                label = "Dialog",
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth().testTag(saqzCatalogVariantTag("Dialog")),
            )
            if (open) {
                SaqzDialog(
                    title = "Dialog",
                    onCloseRequest = { open = false },
                    primaryAction = { SaqzButton(label = "ok", onClick = { open = false }) },
                ) {
                    Text("body", style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                }
            }
        }
        Section("BottomSheet") {
            var open by remember { mutableStateOf(false) }
            SaqzButton(
                label = "BottomSheet",
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth().testTag(saqzCatalogVariantTag("BottomSheet")),
            )
            if (open) {
                SaqzBottomSheet(
                    title = "BottomSheet",
                    onCloseRequest = { open = false },
                    primaryAction = { SaqzButton(label = "ok", onClick = { open = false }) },
                ) {
                    Text("body", style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                }
            }
        }
        Section("estados") {
            Box(modifier = Modifier.testTag(saqzCatalogStateTag("loading"))) { SaqzLoadingState() }
            Box(modifier = Modifier.testTag(saqzCatalogStateTag("content"))) {
                Text("content", style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
            }
            Box(modifier = Modifier.testTag(saqzCatalogStateTag("empty"))) { SaqzEmptyState() }
            Box(modifier = Modifier.testTag(saqzCatalogStateTag("error"))) { SaqzErrorState(onRetry = {}) }
        }
        Section("BottomNav") {
            SaqzBottomNav(
                items = listOf(
                    SaqzBottomNavItem(
                        label = stringResource(Res.string.nav_home),
                        selected = true,
                        onClick = {},
                        icon = { Text("•") },
                    ),
                    SaqzBottomNavItem(
                        label = stringResource(Res.string.nav_components),
                        selected = false,
                        onClick = {},
                        icon = { Text("•") },
                    ),
                ),
            )
        }
        Section("menus") {
            // Owner and athlete menu fixtures: non-interactive labels, never destinations.
            CatalogOwnerMenu.forEachIndexed { index, label ->
                Text(
                    text = stringResource(label),
                    style = SaqzTheme.typography.body,
                    color = SaqzTheme.colors.textPrimary,
                    modifier = Modifier.testTag(saqzCatalogOwnerItemTag(index)),
                )
            }
            CatalogAthleteMenu.forEachIndexed { index, label ->
                Text(
                    text = stringResource(label),
                    style = SaqzTheme.typography.body,
                    color = SaqzTheme.colors.textPrimary,
                    modifier = Modifier.testTag(saqzCatalogAthleteItemTag(index)),
                )
            }
        }
    }
}

@Composable
private fun Section(key: String, content: @Composable () -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.fillMaxWidth().testTag(saqzCatalogSectionTag(key)),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        // The section key is a fixed catalog identifier (data from CatalogSectionOrder),
        // rendered so the inventory is inspectable and its order verifiable.
        Text(text = key, style = SaqzTheme.typography.bodyStrong, color = SaqzTheme.colors.textSecondary)
        content()
    }
}
