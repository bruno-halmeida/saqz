package br.com.saqz.profile.presentation.edit.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.edit.EditProfileIntent
import br.com.saqz.profile.presentation.edit.EditProfileState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EditProfileScreenTest {
    @Test
    fun `e mail fica desabilitado e nao aceita edicao`() = runComposeUiTest {
        content()

        assertEquals(
            4,
            onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `toque na foto sai pela lambda da tela`() = runComposeUiTest {
        var picked = false
        content(onPickPhoto = { picked = true })

        onNodeWithTag(EditProfileTags.Photo).assertHasClickAction().performClick()

        assertTrue(picked)
    }

    @Test
    fun `cada pilula envia a visibilidade escolhida`() = runComposeUiTest {
        var intent: EditProfileIntent? = null
        content(onIntent = { intent = it })

        onNodeWithTag(EditProfileTags.visibility(PhoneVisibility.EVERYONE)).performClick()

        assertEquals(EditProfileIntent.SelectPhoneVisibility(PhoneVisibility.EVERYONE), intent)
    }

    private fun ComposeUiTest.content(
        onIntent: (EditProfileIntent) -> Unit = {},
        onPickPhoto: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            EditProfileScreen(
                state = loadedState,
                onIntent = onIntent,
                onPickPhoto = onPickPhoto,
                onBack = {},
            )
        }
    }

    private val loadedState = EditProfileState.loaded(FakeProfileGateway().profile)
}
