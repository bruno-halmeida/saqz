package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test

/**
 * Bloco 0 do redesenho: comportamento de hoje da tela (banners e seções só de edição),
 * por tag e texto, sem depender de pixel.
 */
@OptIn(ExperimentalTestApi::class)
class GroupSetupCharacterizationScreenTest {
    private val offlineText = "Você está sem conexão. Vamos enviar sua resposta quando a internet voltar."

    private fun create(isOffline: Boolean = false, errors: Set<GroupSetupError> = emptySet()) =
        GroupSetupState(mode = GroupSetupMode.Create).copy(isOffline = isOffline, errors = errors)

    private fun edit(canDelete: Boolean = false, canManage: Boolean = false, errors: Set<GroupSetupError> = emptySet()) =
        GroupSetupState(mode = GroupSetupMode.Edit("grp-1"), form = PreviewCourtForm).copy(
            canDelete = canDelete,
            canManageGameConfig = canManage,
            errors = errors,
        )

    @Test
    fun offlineBannerShowsOnlyWhenOffline() {
        runComposeUiTest {
            setContent { SaqzTheme { GroupSetupScreen(create(isOffline = true), {}, {}) } }
            onNodeWithText(offlineText).assertExists()
        }
        runComposeUiTest {
            setContent { SaqzTheme { GroupSetupScreen(create(isOffline = false), {}, {}) } }
            onNodeWithText(offlineText).assertDoesNotExist()
        }
    }

    @Test
    fun errorBannerUsesTheSingularForOneErrorWhenCreating() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(create(errors = setOf(GroupSetupError.NameRequired)), {}, {}) } }
        onNodeWithTag(GroupSetupTags.ErrorBanner).assertExists()
        onNodeWithText("Falta 1 informação para criar o grupo. Ela está marcada abaixo.").assertExists()
    }

    @Test
    fun errorBannerUsesThePluralForManyErrorsWhenCreating() = runComposeUiTest {
        val errors = setOf(GroupSetupError.NameRequired, GroupSetupError.ModalityRequired)
        setContent { SaqzTheme { GroupSetupScreen(create(errors = errors), {}, {}) } }
        onNodeWithText("Faltam 2 informações para criar o grupo. Elas estão marcadas abaixo.").assertExists()
    }

    @Test
    fun errorBannerUsesTheSingularForOneErrorWhenEditing() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(edit(errors = setOf(GroupSetupError.NameRequired)), {}, {}) } }
        onNodeWithText("Falta 1 informação para salvar o grupo. Ela está marcada abaixo.").assertExists()
    }

    @Test
    fun errorBannerUsesThePluralForManyErrorsWhenEditing() = runComposeUiTest {
        val errors = setOf(GroupSetupError.NameRequired, GroupSetupError.DescriptionTooShort)
        setContent { SaqzTheme { GroupSetupScreen(edit(errors = errors), {}, {}) } }
        onNodeWithText("Faltam 2 informações para salvar o grupo. Elas estão marcadas abaixo.").assertExists()
    }

    @Test
    fun noErrorsMeansNoBanner() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(create(), {}, {}) } }
        onNodeWithTag(GroupSetupTags.ErrorBanner).assertDoesNotExist()
    }

    @Test
    fun editOnlySectionsShowWhenEditingAsManagerWithWhatsApp() = runComposeUiTest {
        setContent {
            SaqzTheme { GroupSetupScreen(edit(canDelete = true, canManage = true), {}, {}, onOpenWhatsApp = {}) }
        }
        onNodeWithText("Como preencher as vagas").assertExists()
        onNodeWithTag(GroupSetupTags.PixKey).assertExists()
        onNodeWithTag(GroupSetupTags.WhatsApp).assertExists()
        onNodeWithTag(GroupSetupTags.WhatsAppOpen).assertExists()
        onNodeWithTag(GroupSetupTags.Delete).assertExists()
    }

    @Test
    fun editWithoutPermissionsHidesGameConfigPixWhatsAppAndDelete() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(edit(canDelete = false, canManage = false), {}, {}) } }
        onNodeWithText("Como preencher as vagas").assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.PixKey).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.WhatsApp).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Delete).assertDoesNotExist()
    }

    @Test
    fun whatsAppEntryNeedsTheHostCallback() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(edit(canManage = true), {}, {}, onOpenWhatsApp = null) } }
        onNodeWithTag(GroupSetupTags.WhatsApp).assertDoesNotExist()
    }

    @Test
    fun creationNeverShowsTheEditOnlySections() = runComposeUiTest {
        val state = create().copy(canDelete = true, canManageGameConfig = true)
        setContent { SaqzTheme { GroupSetupScreen(state, {}, {}, onOpenWhatsApp = {}) } }
        onNodeWithText("Como preencher as vagas").assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.PixKey).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.WhatsApp).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Delete).assertDoesNotExist()
    }
}
