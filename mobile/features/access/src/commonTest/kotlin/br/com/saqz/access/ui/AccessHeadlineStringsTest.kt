package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.forgot_headline
import br.com.saqz.access.resources.new_password_headline
import br.com.saqz.access.resources.register_headline
import br.com.saqz.access.resources.reset_code_headline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A quebra de linha dos títulos do fluxo 1 é fixa no desenho, não consequência da largura,
 * e por isso mora no `\n` do próprio texto de `strings.xml`. Este teste existe porque essa
 * quebra é fácil de perder sem ninguém notar — o parser de recursos poda espaço em volta
 * do texto, e um título que perde a quebra ainda renderiza, só que errado.
 *
 * O [AccessHeader] trata quebra ao final como "sem espaço antes do destaque": nos três
 * títulos em que o destaque abre a segunda linha (1d, 1e, 1g) o `\n` é o último caractere.
 */
@OptIn(ExperimentalTestApi::class)
class AccessHeadlineStringsTest {

    @Test
    fun `headlines whose emphasis opens the second line end in the break`() {
        listOf(Res.string.forgot_headline, Res.string.reset_code_headline, Res.string.new_password_headline)
            .forEach { headline ->
                val value = resolve(headline)
                assertTrue(value.endsWith('\n'), "esperava quebra ao final, veio \"$value\"")
            }
    }

    @Test
    fun `the register headline breaks between its two lines`() {
        assertEquals("Bora organizar\nsua", resolve(Res.string.register_headline))
    }

    private fun resolve(resource: StringResource): String {
        var resolved = ""
        runComposeUiTest { setContent { resolved = stringResource(resource) } }
        return resolved
    }
}
