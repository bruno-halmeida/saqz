package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.access.resources.Res
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O 1h. Os números do selo saem de `fluxo1.senhaAlterada` do `ui-contract.json` — o mesmo
 * arquivo que o `AccessMetricsTest` lê —, e a animação obedece à política de movimento.
 */
@OptIn(ExperimentalTestApi::class)
class PasswordChangedScreenTest {
    private suspend fun senhaAlterada(): JsonObject =
        Json.parseToJsonElement(Res.readBytes("files/ui-contract.json").decodeToString())
            .jsonObject.getValue("fluxo1").jsonObject
            .getValue("senhaAlterada").jsonObject

    @Test
    fun `the badge matches the contract`() = runTest {
        val badge = senhaAlterada()
        assertEquals(PasswordChangedMetrics.circle, badge.getValue("circulo").jsonPrimitive.float.dp)
        assertEquals(PasswordChangedMetrics.check, badge.getValue("check").jsonPrimitive.float.dp)
        assertEquals(PasswordChangedMetrics.bottomSlack, badge.getValue("folgaInferior").jsonPrimitive.float.dp)
        // O fundo é `--saqz-success` a 12%: o export escreve `rgba(23,178,106,.12)`, e o
        // que a tela aplica é o alfa sobre o token.
        val fundo = badge.getValue("fundo").jsonPrimitive.content
        assertEquals(".${fundo.substringAfterLast(",.").removeSuffix(")")}".toFloat(), PasswordChangedMetrics.CIRCLE_TINT)
    }

    @Test
    fun `the entrance is the four tenths of a second from the contract`() = runTest {
        val entrada = Json.parseToJsonElement(Res.readBytes("files/ui-contract.json").decodeToString())
            .jsonObject.getValue("fluxo1").jsonObject
            .getValue("entrada").jsonObject
        assertEquals(
            entrada.getValue("checkDurationMillis").jsonPrimitive.float.toInt(),
            PasswordChangedMetrics.CHECK_DURATION_MILLIS,
        )
    }

    /**
     * Reduce Motion tira o crescimento e **não** a opacidade: quem não quer movimento
     * continua vendo o selo aparecer, que é o sinal de que a senha acabou de mudar.
     */
    @Test
    fun `reduce motion drops the growth only`() {
        assertEquals(1f, passwordChangedCheckScale(entrance = 0f, motion = SaqzMotionPolicy.Reduced))
        assertEquals(1f, passwordChangedCheckScale(entrance = 1f, motion = SaqzMotionPolicy.Reduced))
        assertTrue(passwordChangedCheckScale(entrance = 0f, motion = SaqzMotionPolicy.Normal) < 1f)
        assertEquals(1f, passwordChangedCheckScale(entrance = 1f, motion = SaqzMotionPolicy.Normal))
    }

    @Test
    fun `the screen is the badge and the headline over one way out`() = runComposeUiTest {
        setContent { SaqzTheme { PasswordChangedScreen(onSignIn = {}, onBack = {}) } }
        onNodeWithTag(PasswordChangedTags.Check).assertExists()
        onNodeWithText("Senha alterada!", substring = true).assertExists()
        onNodeWithText("Tudo certo. Use a nova senha para entrar e voltar pra sua galera.").assertExists()
        onNodeWithTag(PasswordChangedTags.Submit).assertExists()
    }

    @Test
    fun `sign in is the primary action`() = runComposeUiTest {
        var signedIn = false
        setContent { SaqzTheme { PasswordChangedScreen(onSignIn = { signedIn = true }, onBack = {}) } }
        onNodeWithTag(PasswordChangedTags.Submit).performClick()
        assertTrue(signedIn, "\"Entrar agora\" é quem encerra o fluxo")
    }
}
