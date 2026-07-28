package br.com.saqz.designsystem.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

// Os rótulos que o próprio design system publica: nenhuma tela precisa reescrever
// "Carregando" nem os dois estados do toggle de senha.
class ResourceCatalogTest {
    private val catalog: Map<StringResource, String> = mapOf(
        Res.string.state_loading to "Carregando",
        Res.string.action_show_password to "Mostrar senha",
        Res.string.action_hide_password to "Ocultar senha",
        Res.string.attendance_going to "Vou",
        Res.string.attendance_maybe to "Talvez",
        Res.string.attendance_out to "Não vou",
        // Contagem do grupo × resposta de quem lê: "Não vão" e "Não vou" são rótulos
        // diferentes, e o par abaixo existe para impedir que voltem a se confundir.
        Res.string.game_stat_going to "Confirmados",
        Res.string.game_stat_maybe to "Talvez",
        Res.string.game_stat_out to "Não vão",
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
