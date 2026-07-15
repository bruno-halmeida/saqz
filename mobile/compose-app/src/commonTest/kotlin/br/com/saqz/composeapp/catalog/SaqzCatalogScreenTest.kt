package br.com.saqz.composeapp.catalog

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzCatalogScreenTest {

    private suspend fun contractKeys(section: String): Set<String> {
        val root = Json.parseToJsonElement(
            Res.readBytes("files/ui-contract.json").decodeToString(),
        ).jsonObject
        return root.getValue(section).jsonObject.keys
    }

    @Test
    fun sectionOrderMatchesContract() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        val tops = CatalogSectionOrder.map { key ->
            onNodeWithTag(saqzCatalogSectionTag(key)).getUnclippedBoundsInRoot().top.value
        }
        // Sections render top-to-bottom in exactly the fixed contract order.
        for (i in 1 until tops.size) {
            assertTrue(tops[i - 1] < tops[i], "section ${CatalogSectionOrder[i]} must follow ${CatalogSectionOrder[i - 1]}")
        }
    }

    @Test
    fun allColorsShown() = runComposeUiTest {
        val keys = contractKeys("colors")
        // Runtime inventory equals the contract JSON — no missing, no extra token.
        assertEquals(keys, CatalogColors.map { it.name }.toSet())
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        CatalogColors.forEach { entry ->
            onNodeWithTag(saqzCatalogColorTag(entry.name)).assertExists()
        }
    }

    @Test
    fun allTypographyShown() = runComposeUiTest {
        val keys = contractKeys("typography")
        assertEquals(keys, CatalogTypography.map { it.name }.toSet())
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        CatalogTypography.forEach { entry ->
            onNodeWithTag(saqzCatalogTypeTag(entry.name)).assertExists()
        }
    }

    @Test
    fun allMetricsShown() = runComposeUiTest {
        val keys = contractKeys("metrics")
        assertEquals(keys, CatalogMetrics.map { it.name }.toSet())
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        CatalogMetrics.forEach { entry ->
            onNodeWithTag(saqzCatalogMetricTag(entry.name)).assertExists()
        }
    }

    @Test
    fun allComponentVariantsShown() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        val variants = buildList {
            CatalogButtonVariants.forEach { add("Button-${it.name}") }
            add("Button-disabled")
            add("Button-loading")
            CatalogInputKinds.forEach { add("Input-${it.name}") }
            add("Card-static"); add("Card-interactive")
            add("ListItem-static"); add("ListItem-interactive")
            CatalogBadgeVariants.forEach { add("Badge-${it.name}") }
            add("Dialog"); add("BottomSheet")
        }
        variants.forEach { onNodeWithTag(saqzCatalogVariantTag(it)).assertExists() }
        // The overlay variants actually open their modal, not just a trigger.
        onNodeWithTag(saqzCatalogVariantTag("Dialog")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("saqz-modal-title").assertExists()
    }

    @Test
    fun allStatesShown() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        onNodeWithTag(saqzCatalogStateTag("loading")).assertExists()
        onNodeWithTag(saqzCatalogStateTag("content")).assertExists()
        onNodeWithTag(saqzCatalogStateTag("empty")).assertExists()
        onNodeWithTag(saqzCatalogStateTag("error")).assertExists()
        // Their pt-BR default views resolve from resources.
        onNodeWithContentDescription("Carregando").assertExists()
        onNodeWithText("Nada por aqui").assertExists()
        onNodeWithText("Não foi possível carregar").assertExists()
    }

    @Test
    fun ownerFixtureIsNonInteractive() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        CatalogOwnerMenu.indices.forEach { index ->
            assertNotClickable(saqzCatalogOwnerItemTag(index))
        }
    }

    @Test
    fun athleteFixtureIsNonInteractive() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        CatalogAthleteMenu.indices.forEach { index ->
            assertNotClickable(saqzCatalogAthleteItemTag(index))
        }
    }

    @Test
    fun productionHasNoForbiddenContent() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen() } }
        // No profile/session surface leaks into the catalog.
        listOf("Sair", "Entrar", "Logout", "Login", "Avatar", "Perfil").forEach { forbidden ->
            onNodeWithText(forbidden).assertDoesNotExist()
        }
    }

    @Test
    fun fontScale2ReflowsWithoutCuttingActions() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                SaqzTheme { SaqzCatalogScreen() }
            }
        }
        val rootRight = onRoot().getUnclippedBoundsInRoot().right.value
        val button = onNodeWithTag(saqzCatalogVariantTag("Button-Primary"))
        button.assertExists()
        val bounds = button.getUnclippedBoundsInRoot()
        // At the largest font scale the action still fits within the viewport — not cut off.
        assertTrue(bounds.right.value <= rootRight + 1f, "action must stay within the viewport")
    }

    private fun ComposeUiTest.assertNotClickable(tag: String) {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        assertFalse(
            node.config.contains(SemanticsActions.OnClick),
            "menu fixture $tag must not be clickable",
        )
    }
}
