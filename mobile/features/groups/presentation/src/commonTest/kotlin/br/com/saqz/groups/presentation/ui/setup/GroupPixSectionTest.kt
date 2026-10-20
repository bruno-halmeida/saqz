package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.pix.PixKeyType
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupPixSectionTest {
    private fun edit() = GroupSetupState(mode = GroupSetupMode.Edit("grp-1"), form = PreviewCourtForm, canManageGameConfig = true)

    @Test
    fun typeSegmentedComesWithFourOptionsAndReportsTheChoice() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent { SaqzTheme { GroupSetupScreen(edit(), { intents += it }, {}) } }

        listOf("E-mail", "Aleatória", "CPF", "Telefone").forEach { onNodeWithText(it).performScrollTo().assertExists() }
        onNodeWithText("Tipo de chave").assertExists()
        onNodeWithTag(GroupSetupTags.pixTypeOption(PixKeyType.PHONE)).performScrollTo().performClick()
        assertEquals(GroupSetupIntent.SelectPixType(PixKeyType.PHONE), intents.last())
    }

    @Test
    fun keyFieldAdaptsToTheTypeWithMaskAndHint() = runComposeUiTest {
        val state = edit().copy(pixType = PixKeyType.CPF, pixKey = "52998224725", pixTouched = true)
        setContent { SaqzTheme { GroupSetupScreen(state, {}, {}) } }

        onNodeWithTag(GroupSetupTags.pixTypeOption(PixKeyType.CPF)).performScrollTo().assertIsSelected()
        onNodeWithText("Só números, 11 dígitos.").performScrollTo().assertExists()
        onNodeWithText("529.982.247-25").performScrollTo().assertExists()
    }

    @Test
    fun legacyKeyWithoutTypeLeavesNoOptionSelectedAndShowsTheKeyAsIs() = runComposeUiTest {
        val state = edit().copy(pixKey = "chave antiga", pixType = null)
        setContent { SaqzTheme { GroupSetupScreen(state, {}, {}) } }

        onNodeWithText("Escolha o tipo antes de digitar a chave.").performScrollTo().assertExists()
        onNodeWithText("chave antiga").performScrollTo().assertExists()
    }

    @Test
    fun invalidKeyShowsTheTypeSpecificError() = runComposeUiTest {
        val state = edit().copy(
            pixType = PixKeyType.PHONE,
            pixKey = "123",
            pixTouched = true,
            errors = setOf(GroupSetupError.PixKeyInvalid),
        )
        setContent { SaqzTheme { GroupSetupScreen(state, {}, {}) } }

        onNodeWithText("Informe o celular com DDD.").performScrollTo().assertExists()
    }
}
