package br.com.saqz.access.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.access.presentation.newpassword.NewPasswordIntent
import br.com.saqz.access.presentation.newpassword.NewPasswordState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.login_error_password
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NewPasswordScreenTest {

    /**
     * O caso do ticket: **um** olho na tela, e ele é o do campo de cima. O export tira o
     * do "Confirmar nova senha" de propósito, e a simetria é justamente o erro plausível
     * — dois campos de senha lado a lado pedem para receber o mesmo tratamento.
     */
    @Test
    fun `only the first field offers the eye`() = runComposeUiTest {
        content()
        onNodeWithTag(NewPasswordTags.Password).assertExists()
        onNodeWithTag(NewPasswordTags.Confirmation).assertExists()
        onAllNodesWithContentDescription("Mostrar senha").assertCountEquals(1)
    }

    @Test
    fun `the hint lives in the confirmation field`() = runComposeUiTest {
        content()
        onNodeWithText("Mínimo de 8 caracteres.").assertExists()
    }

    // O mesmo slot: a recusa entra no lugar da dica, e a coluna não pula de altura.
    @Test
    fun `the refusal replaces the hint`() = runComposeUiTest {
        content(NewPasswordState(confirmationError = UiText.Res(Res.string.login_error_password)))
        onNodeWithText("A senha não confere.").assertExists()
        onAllNodesWithContentDescription("Mínimo de 8 caracteres.").assertCountEquals(0)
    }

    @Test
    fun `submit reaches the view model`() = runComposeUiTest {
        var intent: NewPasswordIntent? = null
        content(onIntent = { intent = it })
        onNodeWithTag(NewPasswordTags.Submit).performClick()
        assertEquals(NewPasswordIntent.Submit, intent)
    }

    @Test
    fun `done on the confirmation field submits`() = runComposeUiTest {
        var intent: NewPasswordIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performImeAction()
        assertEquals(NewPasswordIntent.Submit, intent)
    }

    @Test
    fun `saving locks the primary action`() = runComposeUiTest {
        content(NewPasswordState(isSaving = true))
        onNodeWithTag(NewPasswordTags.Submit).assertIsNotEnabled()
    }

    // A promessa do SPEC_DEVIATION do arquivo: o vão entre os campos é o `ampliado` do
    // contrato, não o `padrao` de 12 das outras telas.
    @Test
    fun `the field gap is the widened one from the contract`() = runTest {
        val gap = Json.parseToJsonElement(Res.readBytes("files/ui-contract.json").decodeToString())
            .jsonObject.getValue("fluxo1").jsonObject
            .getValue("gapDosCampos").jsonObject
            .getValue("ampliado").jsonPrimitive.float
        assertEquals(NEW_PASSWORD_FIELD_GAP, gap.dp)
    }

    private fun ComposeUiTest.content(
        state: NewPasswordState = NewPasswordState(),
        onIntent: (NewPasswordIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            NewPasswordScreen(state = state, onIntent = onIntent, onBack = {})
        }
    }
}
