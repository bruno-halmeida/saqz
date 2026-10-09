package br.com.saqz.access.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionIntent
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionState
import br.com.saqz.access.presentation.normalizedBrMobilePhone
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IdentityCompletionScreenTest {

    @Test fun `the provider name arrives filled in`() = runComposeUiTest {
        content(IdentityCompletionState(name = "Ana Costa"))
        onNodeWithText("Ana Costa").assertExists()
        // O campo preenchido não mostra mais o placeholder do 1b no lugar do nome.
        onNodeWithText("Seu nome").assertDoesNotExist()
    }

    @Test fun `the screen offers exactly the two fields of the export`() = runComposeUiTest {
        content()
        onNodeWithText("Quase", substring = true).assertExists()
        onNodeWithText("Confirme seu nome e adicione um telefone para a galera te encontrar.").assertExists()
        onNodeWithText("Seu telefone fica visível só para os grupos que você participa.").assertExists()
        onNodeWithText("Concluir cadastro").assertExists()
        assertEquals(2, onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `the name field emits a controlled value`() = runComposeUiTest {
        var intent: IdentityCompletionIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Ana")
        assertEquals(IdentityCompletionIntent.UpdateName("Ana"), intent)
    }

    @Test fun `the phone field emits a controlled value`() = runComposeUiTest {
        var intent: IdentityCompletionIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("11")
        assertEquals(IdentityCompletionIntent.UpdatePhone("11"), intent)
    }

    // Revisão da Apple de 09/10/2026 (2.1(a), "erro ao entrar com a Apple"): o revisor
    // digitou o exemplo do campo com um zero a mais. A tela mostrava "(11) 99999-0000", o
    // valor guardava 12 dígitos e "Concluir cadastro" respondia "Telefone incompleto" sem
    // chamar o servidor. Tecla a tecla, como ele: o número que sobe é o da tela, com +55.
    @Test fun `a digit beyond the mask never blocks the registration`() = runComposeUiTest {
        var state by mutableStateOf(IdentityCompletionState(name = "John Apple"))
        var sent: String? = null
        setContent {
            SaqzTheme {
                IdentityCompletionScreen(
                    state = state,
                    onIntent = { intent ->
                        when (intent) {
                            is IdentityCompletionIntent.UpdatePhone -> state = state.copy(phone = intent.value)
                            // A mesma validação que a máquina de sessão faz no "Concluir cadastro".
                            IdentityCompletionIntent.Submit -> {
                                sent = normalizedBrMobilePhone(state.phone)
                                state = state.copy(invalidPhone = sent == null)
                            }
                            else -> Unit
                        }
                    },
                    onPickPhoto = {},
                )
            }
        }
        val phone = onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1]
        "119999900000".forEach { digit ->
            phone.performTextInput(digit.toString())
            waitForIdle()
        }
        onNodeWithTag(Identity1cTags.Submit).performClick()
        waitForIdle()

        assertEquals("+5511999990000", sent)
        onNodeWithText("Telefone incompleto. Use DDD + 9 dígitos.").assertDoesNotExist()
    }

    @Test fun `the primary action completes the registration`() = runComposeUiTest {
        var intent: IdentityCompletionIntent? = null
        content(onIntent = { intent = it })
        onNodeWithTag(Identity1cTags.Submit).performClick()
        assertEquals(IdentityCompletionIntent.Submit, intent)
    }

    @Test fun `done on the phone field completes the registration`() = runComposeUiTest {
        var intent: IdentityCompletionIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performImeAction()
        assertEquals(IdentityCompletionIntent.Submit, intent)
    }

    @Test fun `the photo picker asks to choose a source`() = runComposeUiTest {
        var picked = false
        content(onPickPhoto = { picked = true })
        onNodeWithTag(Identity1cTags.Photo).performClick()
        assertEquals(true, picked)
    }

    @Test fun `the back control leaves the incomplete identity`() = runComposeUiTest {
        var intent: IdentityCompletionIntent? = null
        content(onIntent = { intent = it })
        onNodeWithTag(Identity1cTags.Back).performClick()
        assertEquals(IdentityCompletionIntent.Back, intent)
    }

    // A recusa por campo do backend chega com os dois marcados de uma vez, e cada mensagem
    // tem de aparecer na linha do seu campo — a 1c mostra os dois ao mesmo tempo.
    @Test fun `each refused field carries its own message`() = runComposeUiTest {
        content(IdentityCompletionState(invalidName = true, invalidPhone = true))
        onNodeWithText("Informe seu nome.").assertExists()
        onNodeWithText("Telefone incompleto. Use DDD + 9 dígitos.").assertExists()
    }

    @Test fun `a refused name leaves the phone message alone`() = runComposeUiTest {
        content(IdentityCompletionState(invalidName = true))
        onNodeWithText("Informe seu nome.").assertExists()
        onNodeWithText("Telefone incompleto. Use DDD + 9 dígitos.").assertDoesNotExist()
    }

    // A foto é opcional: o aviso de envio recusado é aviso, e não trava o botão.
    @Test fun `the photo warning never disables the primary action`() = runComposeUiTest {
        content(IdentityCompletionState(name = "Ana Costa", photoFailed = true))
        onNodeWithText("Não conseguimos enviar sua foto", substring = true).assertExists()
        onNodeWithTag(Identity1cTags.Submit).assertHasClickAction()
    }

    @Test fun `no photo means no warning`() = runComposeUiTest {
        content(IdentityCompletionState(name = "Ana Costa"))
        onNodeWithTag(Identity1cTags.PhotoAlert).assertDoesNotExist()
    }

    @Test fun `the chosen photo replaces the empty circle`() = runComposeUiTest {
        content(IdentityCompletionState(photo = ImageBitmap(8, 8)))
        onNodeWithTag(Identity1cTags.Photo).assertHasClickAction()
    }

    @Test fun `a general failure is announced above the fields`() = runComposeUiTest {
        content(IdentityCompletionState(error = UiText.Res(Res.string.auth_error_network)))
        onNodeWithTag(Identity1cTags.Error).assertExists()
    }

    // Enviando, os dois campos param de aceitar entrada — o que está subindo não muda no
    // meio do caminho.
    @Test fun `submitting locks the fields it is sending`() = runComposeUiTest {
        content(IdentityCompletionState(isLoading = true))
        assertEquals(0, onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `the controls keep the minimum touch target`() = runComposeUiTest {
        content()
        onNodeWithTag(Identity1cTags.Back).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(Identity1cTags.Name).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(Identity1cTags.Phone).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(Identity1cTags.Submit).assertHeightIsAtLeast(48.dp)
    }

    @Test fun `the photo action is reachable by name`() = runComposeUiTest {
        content()
        onNodeWithText("Adicionar foto").assertExists()
        onNodeWithTag(Identity1cTags.Photo).assertHasClickAction()
    }

    private fun ComposeUiTest.content(
        state: IdentityCompletionState = IdentityCompletionState(),
        onIntent: (IdentityCompletionIntent) -> Unit = {},
        onPickPhoto: () -> Unit = {},
    ) = setContent { SaqzTheme { IdentityCompletionScreen(state, onIntent, onPickPhoto) } }
}
