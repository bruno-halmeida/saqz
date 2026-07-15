package br.com.saqz.composeapp.catalog

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.nav_components
import br.com.saqz.composeapp.resources.nav_home
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInputKind
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMetrics
import br.com.saqz.designsystem.theme.SaqzTypography
import org.jetbrains.compose.resources.StringResource

// Ordered section keys of the catalog. This list is the single source of truth for the
// section order the screen renders and the tests assert against the contract.
val CatalogSectionOrder: List<String> = listOf(
    "cores",
    "tipografia",
    "metricas",
    "Button",
    "Input",
    "Card",
    "ListItem",
    "Badge",
    "Dialog",
    "BottomSheet",
    "estados",
    "BottomNav",
    "menus",
)

// Registry inventory shown by the catalog. Names mirror the ui-contract.json keys 1:1 so
// a missing/extra token is caught by the contract test; values come from the real registry.
data class CatalogColor(val name: String, val color: Color)
data class CatalogMetric(val name: String, val value: Dp)
data class CatalogTypeStyle(val name: String, val style: TextStyle)

private val tokens = SaqzColorTokens.Light
private val metrics = SaqzMetrics.Default
private val type = SaqzTypography.Default

val CatalogColors: List<CatalogColor> = listOf(
    CatalogColor("background", tokens.background),
    CatalogColor("surface", tokens.surface),
    CatalogColor("surfaceSubtle", tokens.surfaceSubtle),
    CatalogColor("surfacePearl", tokens.surfacePearl),
    CatalogColor("surfaceDark", tokens.surfaceDark),
    CatalogColor("onDark", tokens.onDark),
    CatalogColor("textMutedOnDark", tokens.textMutedOnDark),
    CatalogColor("primary", tokens.primary),
    CatalogColor("onPrimary", tokens.onPrimary),
    CatalogColor("accent", tokens.accent),
    CatalogColor("onAccent", tokens.onAccent),
    CatalogColor("textPrimary", tokens.textPrimary),
    CatalogColor("textSecondary", tokens.textSecondary),
    CatalogColor("textMuted", tokens.textMuted),
    CatalogColor("controlBorder", tokens.controlBorder),
    CatalogColor("border", tokens.border),
    CatalogColor("hairline", tokens.hairline),
    CatalogColor("dividerSoft", tokens.dividerSoft),
    CatalogColor("infoSurface", tokens.infoSurface),
    CatalogColor("infoForeground", tokens.infoForeground),
    CatalogColor("successSurface", tokens.successSurface),
    CatalogColor("successForeground", tokens.successForeground),
    CatalogColor("warningSurface", tokens.warningSurface),
    CatalogColor("warningForeground", tokens.warningForeground),
    CatalogColor("errorSurface", tokens.errorSurface),
    CatalogColor("errorForeground", tokens.errorForeground),
    CatalogColor("disabledSurface", tokens.disabledSurface),
    CatalogColor("disabledForeground", tokens.disabledForeground),
)

val CatalogMetrics: List<CatalogMetric> = listOf(
    CatalogMetric("grid", metrics.grid),
    CatalogMetric("subGrid", metrics.subGrid),
    CatalogMetric("horizontalPadding", metrics.horizontalPadding),
    CatalogMetric("sectionVerticalPadding", metrics.sectionVerticalPadding),
    CatalogMetric("utilityCardPadding", metrics.utilityCardPadding),
    CatalogMetric("primaryButtonRadius", metrics.primaryButtonRadius),
    CatalogMetric("compactControlRadius", metrics.compactControlRadius),
    CatalogMetric("cardRadius", metrics.cardRadius),
    CatalogMetric("bottomNavHeight", metrics.bottomNavHeight),
    CatalogMetric("minimumTouchTarget", metrics.minimumTouchTarget),
)

val CatalogTypography: List<CatalogTypeStyle> = listOf(
    CatalogTypeStyle("heroDisplay", type.heroDisplay),
    CatalogTypeStyle("displayLarge", type.displayLarge),
    CatalogTypeStyle("displayMedium", type.displayMedium),
    CatalogTypeStyle("lead", type.lead),
    CatalogTypeStyle("body", type.body),
    CatalogTypeStyle("bodyStrong", type.bodyStrong),
    CatalogTypeStyle("caption", type.caption),
    CatalogTypeStyle("navigation", type.navigation),
)

// Every applicable variant/state of the inventory, so the catalog and its test enumerate
// them from one place.
val CatalogButtonVariants: List<SaqzButtonVariant> = SaqzButtonVariant.entries
val CatalogInputKinds: List<SaqzInputKind> = SaqzInputKind.entries
val CatalogBadgeVariants: List<SaqzBadgeVariant> = SaqzBadgeVariant.entries

// Menu fixtures: configurable pt-BR labels drawn from the catalog, shown as non-interactive
// items. They are neither navigation destinations nor role sessions (NAV-03).
val CatalogOwnerMenu: List<StringResource> = listOf(Res.string.nav_home, Res.string.nav_components)
val CatalogAthleteMenu: List<StringResource> = listOf(Res.string.nav_home)
