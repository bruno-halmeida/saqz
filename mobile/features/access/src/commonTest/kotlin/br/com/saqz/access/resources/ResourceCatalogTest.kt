package br.com.saqz.access.resources

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Os rótulos genéricos que sobreviveram ao design system apagado (VUL-36): o
// estado de carregamento e os dois do toggle de senha do SaqzInput.
class ResourceCatalogTest {
    private val catalog: Map<StringResource, String> = mapOf(
        Res.string.state_loading to "Carregando",
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
}
