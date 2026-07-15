package br.com.saqz.designsystem.resources

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
        Res.string.state_loading to "Carregando",
        Res.string.state_empty to "Nada por aqui",
        Res.string.state_error to "Não foi possível carregar",
        Res.string.action_retry to "Tentar novamente",
        Res.string.action_close to "Fechar",
        Res.string.action_show_password to "Mostrar senha",
        Res.string.action_hide_password to "Ocultar senha",
    )

    @Test
    fun inventoryResolvesGenericLabels() = runTest {
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
            val label = stringResource(Res.string.action_close)
            Text(label, modifier = Modifier.semantics { contentDescription = label })
        }
        onNodeWithText("Fechar").assertIsDisplayed()
        onNodeWithContentDescription("Fechar").assertExists()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun visibleTextComesFromCatalog() = runComposeUiTest {
        setContent { Text(stringResource(Res.string.state_error)) }
        onNodeWithText("Não foi possível carregar").assertIsDisplayed()
    }
}
