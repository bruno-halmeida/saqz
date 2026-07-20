package br.com.saqz.groups.ui.setup

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoSelection
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.ui.photo.GroupPhotoTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class GroupSetupScreenTest {
    @Test fun `registration presents the three sections in one flow`() = runComposeUiTest {
        setup()
        onNodeWithTag(GroupSetupTags.Profile).assertExists()
        onNodeWithTag(GroupSetupTags.GameDefaults).assertExists()
        onNodeWithTag(GroupSetupTags.FinanceDefaults).assertExists()
    }

    @Test fun `required profile fields are discoverable`() = runComposeUiTest {
        setup()
        onNodeWithText("Nome do grupo").assertExists()
        onNodeWithText("Modalidade").assertExists()
        onNodeWithText("Composição").assertExists()
    }

    @Test fun `optional profile fields are discoverable`() = runComposeUiTest {
        setup()
        onNodeWithText("Descrição").assertExists()
        onNodeWithText("Cidade").assertExists()
        onNodeWithText("Nível").assertExists()
    }

    @Test fun `detected timezone hides all timezone presentation`() = runComposeUiTest {
        setup()
        onNodeWithTag(GroupSetupTags.TimeZone).assertDoesNotExist()
        onNodeWithText("America/Sao_Paulo").assertDoesNotExist()
    }

    @Test fun `timezone failure exposes friendly regions without identifiers`() = runComposeUiTest {
        setup(state(timezoneSelectionRequired = true))
        onNodeWithText("Em qual região você está?").assertExists()
        onNodeWithText("Brasília, Sul e Sudeste").assertExists()
        onNodeWithText("America/Sao_Paulo").assertDoesNotExist()
    }

    @Test fun `friendly timezone selection emits its hidden identifier`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state(timezoneSelectionRequired = true)) { intent = it }
        onNodeWithText("Amazonas").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.SelectFallbackTimeZone("America/Manaus"), intent)
    }

    @Test fun `court volleyball reveals play style presets`() = runComposeUiTest {
        setup(state(form = requiredForm(modality = GroupModality.COURT_VOLLEYBALL)))
        onNodeWithText("Esquema de jogo").assertExists()
        onNodeWithText("5-1").assertExists()
    }

    @Test fun `beach volleyball hides play style`() = runComposeUiTest {
        setup(state(form = requiredForm(modality = GroupModality.BEACH_VOLLEYBALL)))
        onNodeWithText("Esquema de jogo").assertDoesNotExist()
        onNodeWithText("5-1").assertDoesNotExist()
    }

    @Test fun `custom level reveals its required label`() = runComposeUiTest {
        setup(state(form = requiredForm().copy(level = GroupLevel.CUSTOM)))
        onNodeWithText("Nome do nível").assertExists()
    }

    @Test fun `preset level hides obsolete custom label`() = runComposeUiTest {
        setup(state(form = requiredForm().copy(level = GroupLevel.ADVANCED)))
        onNodeWithText("Nome do nível").assertDoesNotExist()
    }

    @Test fun `custom court style reveals its required label`() = runComposeUiTest {
        setup(state(form = requiredForm().copy(playStyle = GroupPlayStyle.CUSTOM)))
        onNodeWithText("Nome do esquema").assertExists()
    }

    @Test fun `adding a venue emits an empty nested venue`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.AddVenue).performScrollTo().performClick()
        assertEquals(GroupSetupIntent.UpdateVenue(GroupVenueForm(name = "", address = "")), intent)
    }

    @Test fun `enabled venue exposes name address and court`() = runComposeUiTest {
        setup(state(form = requiredForm().copy(defaultVenue = GroupVenueForm(name = "Arena", address = "Rua 1"))))
        onNodeWithText("Nome do local").assertExists()
        onNodeWithText("Endereço").assertExists()
        onNodeWithText("Quadra").assertExists()
    }

    @Test fun `venue name remains a controlled intent`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state(form = requiredForm().copy(defaultVenue = GroupVenueForm(name = "", address = "Rua 1")))) { intent = it }
        onNodeWithContentDescription("Nome do local", useUnmergedTree = true).performTextInput("Arena")
        assertEquals("Arena", assertIs<GroupSetupIntent.UpdateVenue>(intent).value?.name)
    }

    @Test fun `adding a slot emits local weekday time and duration defaults`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.AddSlot).performScrollTo().performClick()
        val slot = assertIs<GroupSetupIntent.UpdateSlots>(intent).value.single()
        assertEquals(GroupWeekday.MONDAY, slot.weekday)
        assertEquals("", slot.startTime)
        assertEquals(60, slot.durationMinutes)
    }

    @Test fun `regular slot exposes weekday time and duration`() = runComposeUiTest {
        setup(state(form = requiredForm().copy(regularSlots = listOf(slot))))
        onNodeWithText("Dia da semana").assertExists()
        onNodeWithText("Horário de início").assertExists()
        onNodeWithText("Duração em minutos").assertExists()
    }

    @Test fun `regular slot can be removed independently`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state(form = requiredForm().copy(regularSlots = listOf(slot)))) { intent = it }
        onNodeWithText("Remover horário").performScrollTo().performClick()
        assertEquals(emptyList(), assertIs<GroupSetupIntent.UpdateSlots>(intent).value)
    }

    @Test fun `BRL monthly input converts to integer cents`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithContentDescription("Mensalidade", useUnmergedTree = true).performTextInput("12,34")
        assertEquals(1234L, assertIs<GroupSetupIntent.UpdateMonthlyFee>(intent).cents)
    }

    @Test fun `monthly due day appears only with a monthly fee`() = runComposeUiTest {
        setup(state(form = requiredForm().copy(monthlyFeeCents = 1234)))
        onNodeWithText("Dia do vencimento (1 a 28)").assertExists()
    }

    @Test fun `monthly due day stays hidden without a fee`() = runComposeUiTest {
        setup()
        onNodeWithText("Dia do vencimento (1 a 28)").assertDoesNotExist()
    }

    @Test fun `athletes can read game defaults but not finance defaults`() = runComposeUiTest {
        setup(access = GroupSetupAccess.ATHLETE)
        onNodeWithTag(GroupSetupTags.GameDefaults).assertExists()
        onNodeWithTag(GroupSetupTags.FinanceDefaults).assertDoesNotExist()
    }

    @Test fun `athletes never receive profile edit action`() = runComposeUiTest {
        setup(access = GroupSetupAccess.ATHLETE)
        onNodeWithTag(GroupSetupTags.Submit).assertDoesNotExist()
    }

    @Test fun `edit mode exposes save action`() = runComposeUiTest {
        setup(state(mode = GroupSetupMode.EDIT, form = requiredForm()))
        onNodeWithText("Perfil e padrões do grupo").assertExists()
        onNodeWithText("Salvar alterações").assertExists()
    }

    @Test fun `legacy incomplete edit explains profile completion`() = runComposeUiTest {
        setup(state(mode = GroupSetupMode.EDIT))
        onNodeWithText("Complete modalidade e composição para liberar as ações do grupo.").assertExists()
    }

    @Test fun `field errors are shown next to their visible field`() = runComposeUiTest {
        setup(state(fieldErrors = mapOf("name" to listOf("invalid"))))
        onNodeWithText("Revise este campo.").assertExists()
    }

    @Test fun `conflict state offers authoritative reload`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state(mode = GroupSetupMode.EDIT, conflict = true)) { intent = it }
        onNodeWithText("Recarregar dados").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.ReloadConflict, intent)
    }

    @Test fun `photo failure remains retryable after group success`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state(successGroupId = "group-1", photoRetryAvailable = true)) { intent = it }
        onNodeWithText("Tentar enviar foto novamente").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.RetryPhotoUpload, intent)
    }

    @Test fun `registration photo is visibly optional and skippable`() = runComposeUiTest {
        setup()
        onNodeWithText("Opcional. Você pode continuar sem foto e alterar depois.").assertExists()
        onNodeWithTag(GroupSetupTags.Submit).performScrollTo().assertExists()
    }

    @Test fun `prepared registration photo marks pending without uploading`() = runComposeUiTest {
        var setupIntent: GroupSetupIntent? = null
        val photoIntents = mutableListOf<GroupPhotoIntent>()
        setup(
            state = state(),
            onIntent = { setupIntent = it },
            photoState = GroupPhotoState(
                selection = GroupPhotoSelection(
                    GroupPhotoSourceHandle("source"), GroupPhotoPreviewHandle("preview"), 200, 100,
                ),
                stage = GroupPhotoStage.CROPPING,
            ),
            onPhotoIntent = photoIntents::add,
        )
        onNodeWithTag(GroupPhotoTags.Confirm).performScrollTo().performClick()
        assertEquals(GroupSetupIntent.SetPhotoPending(true), setupIntent)
        assertEquals(emptyList<GroupPhotoIntent>(), photoIntents)
    }

    @Test fun `athlete profile shows fallback without photo edit actions`() = runComposeUiTest {
        setup(access = GroupSetupAccess.ATHLETE)
        onNodeWithTag(GroupPhotoTags.Preview).assertExists()
        onNodeWithTag(GroupPhotoTags.Camera).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Library).assertDoesNotExist()
    }

    @Test fun `loading keeps submit reachable but disabled`() = runComposeUiTest {
        setup(state(isLoading = true))
        onNodeWithTag(GroupSetupTags.Submit).performScrollTo().assertIsNotEnabled()
    }

    @Test fun `maximum text scale keeps the final action in semantic flow`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    GroupSetupScreen(state()) {}
                }
            }
        }
        onNodeWithTag(GroupSetupTags.Submit).performScrollTo().assertExists()
    }

    @Test fun `server permission error uses role-safe copy`() = runComposeUiTest {
        setup(state(error = GroupSetupError.FORBIDDEN))
        onNodeWithText("Você não tem permissão para editar este grupo.").assertExists()
    }

    @Test fun `normal labels never expose enum or cents identifiers`() = runComposeUiTest {
        setup(state(form = requiredForm().copy(defaultGameFeeCents = 1234)))
        onNodeWithText("COURT_VOLLEYBALL").assertDoesNotExist()
        onNodeWithText("1234").assertDoesNotExist()
        onNodeWithText("12,34").assertExists()
    }

    @Test fun `create action emits submit intent`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.Submit).performScrollTo().performClick()
        assertEquals(GroupSetupIntent.Submit, intent)
    }

    @Test fun `invalid BRL input does not manufacture cents`() {
        assertNull(parseBrlCents("12,345"))
        assertNull(parseBrlCents("valor"))
    }

    @Test fun `BRL formatter uses reais and two decimal places`() {
        assertEquals("1.234,05", formatBrlInput(123405))
        assertEquals("", formatBrlInput(null))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setup(
        state: GroupSetupState = state(),
        access: GroupSetupAccess = GroupSetupAccess.ORGANIZER,
        photoState: GroupPhotoState = GroupPhotoState(),
        onPhotoIntent: (GroupPhotoIntent) -> Unit = {},
        onIntent: (GroupSetupIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            GroupSetupScreen(
                state = state,
                onIntent = onIntent,
                access = access,
                photoState = photoState,
                onPhotoIntent = onPhotoIntent,
            )
        }
    }

    private companion object {
        val slot = GroupRegularSlotForm(weekday = GroupWeekday.WEDNESDAY, startTime = "19:30", durationMinutes = 90)

        fun requiredForm(modality: GroupModality = GroupModality.COURT_VOLLEYBALL) = GroupSetupForm(
            name = "Vôlei de terça",
            modality = modality,
            composition = GroupComposition.MIXED,
        )

        fun state(
            mode: GroupSetupMode = GroupSetupMode.CREATE,
            form: GroupSetupForm = GroupSetupForm(),
            timezoneSelectionRequired: Boolean = false,
            fieldErrors: Map<String, List<String>> = emptyMap(),
            isLoading: Boolean = false,
            conflict: Boolean = false,
            successGroupId: String? = null,
            photoRetryAvailable: Boolean = false,
            error: GroupSetupError? = null,
        ) = GroupSetupState(
            mode = mode,
            form = form,
            commandKey = "command-1",
            timezoneSelectionRequired = timezoneSelectionRequired,
            fieldErrors = fieldErrors,
            isLoading = isLoading,
            conflict = conflict,
            successGroupId = successGroupId,
            photoRetryAvailable = photoRetryAvailable,
            error = error,
        )
    }
}
