package br.com.saqz.composeapp.resources

import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceCatalogTest {
    private val catalog: Map<StringResource, String> = mapOf(
        Res.string.home_title to "Saqz",
        Res.string.home_explore_components to "Explorar componentes",
        Res.string.nav_home to "Início",
        Res.string.nav_components to "Componentes",
    )

    @Test
    fun inventoryResolvesShellLabels() = runTest {
        catalog.keys.forEach { key -> assertTrue(getString(key).isNotBlank()) }
    }

    @Test
    fun labelsStayPtBr() = runTest {
        // Only the default values/ exists (no values-en), so pt-BR resolves under
        // any device locale.
        catalog.forEach { (key, expected) -> assertEquals(expected, getString(key)) }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun labelFeedsAccessibleName() = runComposeUiTest {
        setContent {
            val label = stringResource(Res.string.nav_home)
            Text(label, modifier = Modifier.semantics { contentDescription = label })
        }
        onNodeWithText("Início").assertIsDisplayed()
        onNodeWithContentDescription("Início").assertExists()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun visibleTextComesFromCatalog() = runComposeUiTest {
        setContent { Text(stringResource(Res.string.home_explore_components)) }
        onNodeWithText("Explorar componentes").assertIsDisplayed()
    }
}
