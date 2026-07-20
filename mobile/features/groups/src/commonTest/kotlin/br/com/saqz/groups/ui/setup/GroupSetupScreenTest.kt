package br.com.saqz.groups.ui.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoSelection
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.newGroupDefaults
import br.com.saqz.groups.ui.photo.GroupPhotoTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupSetupScreenTest {
    @Test fun `registration presents four equal width sections in one flow`() = runComposeUiTest {
        setup()
        val tags = listOf(GroupSetupTags.Profile, GroupSetupTags.Sports, GroupSetupTags.GameDefaults, GroupSetupTags.FinanceDefaults)
        val widths = tags.map { onNodeWithTag(it).performScrollTo().fetchSemanticsNode().boundsInRoot.width }
        assertTrue(widths.all { kotlin.math.abs(it - widths.first()) < 1f })
    }

    @Test fun `create top bar stays centered on new group and exposes both actions`() = runComposeUiTest {
        var backs = 0
        var mores = 0
        setup(state = state(form = requiredForm()), onBack = { backs++ }, onMoreOptions = { mores++ })
        onNodeWithTag(GroupSetupTags.Title).assertTextEquals("Novo grupo")
        onNodeWithTag(GroupSetupTags.Back).assertHasClickAction().performClick()
        onNodeWithTag(GroupSetupTags.More).assertHasClickAction().performClick()
        assertEquals(1, backs)
        assertEquals(1, mores)
    }

    @Test fun `guidance and all four section descriptions are discoverable`() = runComposeUiTest {
        setup()
        listOf(
            "Você poderá alterar essas informações depois.",
            "Escolha como sua galera será identificada.",
            "Essas informações ajudam novos membros a entender o grupo.",
            "Defina os padrões usados na criação de novos jogos.",
            "Configure apenas se o grupo possuir mensalidade ou contribuição recorrente.",
        ).forEach { onNodeWithText(it).assertExists() }
    }

    @Test fun `initial suggestions are selected and fees remain absent`() = runComposeUiTest {
        setup()
        onNodeWithText("Vôlei de quadra").assertIsSelected()
        onNodeWithText("Vôlei de praia").assertIsNotSelected()
        onNodeWithText("Misto").assertIsSelected()
        onNodeWithTag(GroupSetupTags.LevelSelector).assertTextContains("Todos os níveis")
        onNodeWithTag(GroupSetupTags.CapacityValue, useUnmergedTree = true).assertTextEquals("12")
        onNodeWithTag(GroupSetupTags.ConfirmationSelector).assertTextContains("6 horas antes")
        onNodeWithText("Sem cobrança").assertExists()
        onNodeWithText("Não").performScrollTo().assertExists()
    }

    @Test fun `photo stays compact and opens source actions in a sheet`() = runComposeUiTest {
        setup()
        onNodeWithText("Foto do grupo").assertDoesNotExist()
        onNodeWithText("Opcional").assertDoesNotExist()
        onNodeWithText("SG").assertDoesNotExist()
        val previewBounds = onNodeWithTag(GroupPhotoTags.Preview, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val addBounds = onNodeWithTag(GroupPhotoTags.Add).assertExists().assertHasClickAction()
            .assertHeightIsAtLeast(44.dp).fetchSemanticsNode().boundsInRoot
        val pickerBounds = onNodeWithTag(GroupPhotoTags.Picker).assertHasClickAction().fetchSemanticsNode().boundsInRoot
        onNodeWithTag(GroupPhotoTags.Preview, useUnmergedTree = true).assertWidthIsAtLeast(112.dp)
        val sectionBounds = onNodeWithTag(GroupSetupTags.Profile).fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(previewBounds.center.x - sectionBounds.center.x) < 1f)
        assertTrue(addBounds.center.x > previewBounds.center.x)
        assertTrue(addBounds.center.y > previewBounds.center.y)
        assertTrue(pickerBounds.contains(previewBounds.center))
        onNodeWithTag(GroupPhotoTags.Camera).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Library).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Picker).performClick()
        onNodeWithText("Tirar foto").assertExists()
        onNodeWithText("Escolher da galeria").assertExists()
        onNodeWithText("Remover foto").assertExists()
        onNodeWithText("Cancelar").assertExists()
    }

    @Test fun `photo sheet emits camera and gallery intents`() = runComposeUiTest {
        var photoIntent: GroupPhotoIntent? = null
        setup(onPhotoIntent = { photoIntent = it })
        onNodeWithTag(GroupPhotoTags.Add).performClick()
        onNodeWithTag(GroupPhotoTags.Camera).performClick()
        assertEquals(GroupPhotoIntent.ChooseCamera, photoIntent)
        onNodeWithTag(GroupPhotoTags.Add).performClick()
        onNodeWithTag(GroupPhotoTags.Library).performClick()
        assertEquals(GroupPhotoIntent.ChooseLibrary, photoIntent)
    }

    @Test fun `level options stay progressive and emit selection from sheet`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithText("Iniciante").assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.LevelSelector).performScrollTo().performClick()
        onNodeWithText("Iniciante").assertExists().performClick()
        assertEquals(GroupSetupIntent.UpdateLevel(GroupLevel.BEGINNER), intent)
    }

    @Test fun `custom level and custom court style reveal their labels`() = runComposeUiTest {
        setup(state = state(form = requiredForm().copy(level = GroupLevel.CUSTOM, playStyle = GroupPlayStyle.CUSTOM)))
        onNodeWithText("Nome do nível").assertExists()
        onNodeWithText("Nome do esquema").assertExists()
    }

    @Test fun `beach volleyball hides court play style`() = runComposeUiTest {
        setup(state = state(form = requiredForm(GroupModality.BEACH_VOLLEYBALL)))
        onNodeWithText("Esquema de jogo").assertDoesNotExist()
    }

    @Test fun `court play style opens a progressive selector`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state = state(form = requiredForm())) { intent = it }
        onNodeWithText("5-1").assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.PlayStyleSelector).performScrollTo().performClick()
        onNodeWithText("5-1").performClick()
        assertEquals(GroupSetupIntent.UpdatePlayStyle(GroupPlayStyle.FIVE_ONE), intent)
    }

    @Test fun `venue add action emits an empty nested venue`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.AddVenue).performScrollTo().performClick()
        assertEquals(GroupSetupIntent.UpdateVenue(GroupVenueForm(name = "", address = "")), intent)
    }

    @Test fun `configured venue is summarized and can be edited`() = runComposeUiTest {
        setup(state = state(form = requiredForm().copy(defaultVenue = GroupVenueForm(name = "Quadra do Parque", address = "Rua 1"))))
        onNodeWithText("Quadra do Parque").performScrollTo().assertExists()
        onNodeWithText("Nome do local").assertDoesNotExist()
        onNodeWithText("Editar").performClick()
        onNodeWithText("Nome do local").assertExists()
        onNodeWithText("Endereço").assertExists()
        onNodeWithText("Quadra").assertExists()
    }

    @Test fun `venue name remains a controlled intent`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state = state(form = requiredForm().copy(defaultVenue = GroupVenueForm(name = "", address = "Rua 1")))) { intent = it }
        onNodeWithContentDescription("Nome do local", useUnmergedTree = true).performTextInput("Arena")
        assertEquals("Arena", assertIs<GroupSetupIntent.UpdateVenue>(intent).value?.name)
    }

    @Test fun `controlled name preserves incremental keyboard order across recomposition`() = runComposeUiTest {
        var observed = ""
        setContent {
            var form by remember { mutableStateOf(newGroupDefaults()) }
            SaqzTheme {
                GroupSetupScreen(
                    state = state(form = form),
                    photoState = GroupPhotoState(),
                    onPhotoIntent = {},
                    onIntent = { intent ->
                        if (intent is GroupSetupIntent.UpdateName) {
                            observed = intent.value
                            form = form.copy(name = intent.value)
                        }
                    },
                )
            }
        }
        val name = onNodeWithContentDescription("Nome do grupo", useUnmergedTree = true)
        listOf("a", "b", "c").forEach { name.performTextInput(it); waitForIdle() }
        assertEquals("abc", observed)
    }

    @Test fun `slot add action emits local defaults`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.AddSlot).performScrollTo().performClick()
        val slot = assertIs<GroupSetupIntent.UpdateSlots>(intent).value.single()
        assertEquals(GroupWeekday.MONDAY, slot.weekday)
        assertEquals("", slot.startTime)
        assertEquals(60, slot.durationMinutes)
    }

    @Test fun `configured slot is summarized and editor preserves every field`() = runComposeUiTest {
        setup(state = state(form = requiredForm().copy(regularSlots = listOf(slot))))
        onNodeWithText("19:30").performScrollTo().assertExists()
        onNodeWithText("Dia da semana").assertDoesNotExist()
        onNodeWithText("Editar").performClick()
        onNodeWithText("Dia da semana").assertExists()
        onNodeWithText("Horário de início").assertExists()
        onNodeWithText("Duração em minutos").assertExists()
    }

    @Test fun `regular slot can be removed independently`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state = state(form = requiredForm().copy(regularSlots = listOf(slot))), onIntent = { intent = it })
        onNodeWithText("Editar").performScrollTo().performClick()
        onNodeWithText("Remover horário").performScrollTo().performClick()
        assertEquals(emptyList(), assertIs<GroupSetupIntent.UpdateSlots>(intent).value)
    }

    @Test fun `capacity stepper emits one player increments`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.CapacityValue, useUnmergedTree = true).performScrollTo()
        val addButtons = onAllNodesWithContentDescription("Limite de jogadores")
        addButtons[2].performClick()
        assertEquals(GroupSetupIntent.UpdateDefaultCapacity(13), intent)
    }

    @Test fun `confirmation selector emits human readable preset`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.ConfirmationSelector).performScrollTo().performClick()
        onNodeWithText("12 horas antes").performClick()
        assertEquals(GroupSetupIntent.UpdateConfirmationLeadMinutes(720), intent)
    }

    @Test fun `game fee is absent until explicitly enabled and converts BRL cents`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithText("Adicionar valor").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.UpdateDefaultGameFeeCents(0), intent)
    }

    @Test fun `monthly switch is off then reveals fee and due date`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(onIntent = { intent = it })
        onNodeWithText("Valor da mensalidade").assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.MonthlySwitch).performScrollTo().performClick()
        assertEquals(GroupSetupIntent.UpdateMonthlyFee(0, 10), intent)
        setup(state = state(form = requiredForm().copy(monthlyFeeCents = 0, monthlyDueDay = 10)))
        onNodeWithText("Valor da mensalidade").performScrollTo().assertExists()
        onNodeWithText("Todo dia 10").assertExists()
    }

    @Test fun `monthly input converts to integer cents`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state = state(form = requiredForm().copy(monthlyFeeCents = 0, monthlyDueDay = 10)), onIntent = { intent = it })
        onNodeWithContentDescription("Valor da mensalidade", useUnmergedTree = true).performScrollTo().performTextInput("12,34")
        assertEquals(1234L, assertIs<GroupSetupIntent.UpdateMonthlyFee>(intent).cents)
    }

    @Test fun `detected timezone stays hidden and fallback uses friendly regions`() = runComposeUiTest {
        setup()
        onNodeWithTag(GroupSetupTags.TimeZone).assertDoesNotExist()
        setup(state = state(timezoneSelectionRequired = true))
        onNodeWithText("Em qual região você está?").assertExists()
        onNodeWithText("America/Sao_Paulo").assertDoesNotExist()
    }

    @Test fun `friendly timezone selection emits hidden identifier`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state = state(timezoneSelectionRequired = true), onIntent = { intent = it })
        onNodeWithText("Amazonas").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.SelectFallbackTimeZone("America/Manaus"), intent)
    }

    @Test fun `athletes read game defaults without finance or edit actions`() = runComposeUiTest {
        setup(access = GroupSetupAccess.ATHLETE)
        onNodeWithTag(GroupSetupTags.GameDefaults).assertExists()
        onNodeWithTag(GroupSetupTags.FinanceDefaults).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Submit).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Add).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Picker).assertDoesNotExist()
    }

    @Test fun `edit mode exposes stable title and save action`() = runComposeUiTest {
        setup(state = state(mode = GroupSetupMode.EDIT, form = requiredForm()))
        onNodeWithTag(GroupSetupTags.Title).assertTextEquals("Perfil e padrões do grupo")
        onNodeWithText("Salvar alterações").assertExists()
    }

    @Test fun `field errors appear only when supplied by state`() = runComposeUiTest {
        setup()
        onNodeWithText("Revise este campo.").assertDoesNotExist()
        setup(state = state(fieldErrors = mapOf("name" to listOf("invalid"))))
        onNodeWithText("Revise este campo.").assertExists()
    }

    @Test fun `conflict and photo failure keep recovery actions`() = runComposeUiTest {
        var intent: GroupSetupIntent? = null
        setup(state = state(mode = GroupSetupMode.EDIT, conflict = true), onIntent = { intent = it })
        onNodeWithText("Recarregar dados").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.ReloadConflict, intent)
        setup(state = state(successGroupId = "group-1", photoRetryAvailable = true), onIntent = { intent = it })
        onNodeWithText("Tentar enviar foto novamente").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.RetryPhotoUpload, intent)
    }

    @Test fun `prepared registration photo remains deferred`() = runComposeUiTest {
        var setupIntent: GroupSetupIntent? = null
        val photoIntents = mutableListOf<GroupPhotoIntent>()
        setup(
            onIntent = { setupIntent = it },
            photoState = GroupPhotoState(
                selection = GroupPhotoSelection(GroupPhotoSourceHandle("source"), GroupPhotoPreviewHandle("preview"), 200, 100),
                stage = GroupPhotoStage.CROPPING,
            ),
            onPhotoIntent = photoIntents::add,
        )
        onNodeWithTag(GroupPhotoTags.Confirm).performScrollTo().performClick()
        assertEquals(GroupSetupIntent.SetPhotoPending(true), setupIntent)
        assertEquals(emptyList<GroupPhotoIntent>(), photoIntents)
    }

    @Test fun `submit is sticky disabled for blank name and active after a name`() = runComposeUiTest {
        setup()
        onNodeWithTag(GroupSetupTags.Submit).assertIsDisplayed().assertIsNotEnabled().assertHeightIsAtLeast(54.dp)
        var intent: GroupSetupIntent? = null
        setup(state = state(form = requiredForm()), onIntent = { intent = it })
        onNodeWithTag(GroupSetupTags.Submit).assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(GroupSetupIntent.Submit, intent)
    }

    @Test fun `loading keeps sticky submit reachable and disabled`() = runComposeUiTest {
        setup(state = state(form = requiredForm(), isLoading = true))
        onNodeWithTag(GroupSetupTags.Submit).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun `maximum text scale keeps sticky action and controls reachable`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.size(360.dp, 800.dp)) {
                    SaqzTheme { GroupSetupScreen(state(), photoState = GroupPhotoState(), onPhotoIntent = {}) {} }
                }
            }
        }
        onNodeWithTag(GroupSetupTags.Submit).assertIsDisplayed().assertHeightIsAtLeast(54.dp)
        listOf(GroupPhotoTags.Add, GroupSetupTags.AddVenue, GroupSetupTags.AddSlot).forEach { tag ->
            onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(44.dp).assertHasClickAction()
        }
    }

    @Test fun `normal labels never expose enum cents or raw timezone identifiers`() = runComposeUiTest {
        setup(state = state(form = requiredForm().copy(defaultGameFeeCents = 1234)))
        onNodeWithText("COURT_VOLLEYBALL").assertDoesNotExist()
        onNodeWithText("1234").assertDoesNotExist()
        onNodeWithText("12,34").performScrollTo().assertExists()
        onNodeWithText("America/Sao_Paulo").assertDoesNotExist()
    }

    @Test fun `invalid BRL input does not manufacture cents`() {
        assertNull(parseBrlCents("12,345"))
        assertNull(parseBrlCents("valor"))
    }

    @Test fun `BRL input accepts only numeric decimal content`() {
        assertEquals("12,34", sanitizeBrlInput("R$ 12a,3x4"))
        assertEquals("12345678,90", sanitizeBrlInput("123456789,901"))
        assertEquals("1234,05", sanitizeBrlInput("1.234,05"))
        assertEquals("12,34", sanitizeBrlInput("12.34"))
    }

    @Test fun `BRL formatter uses reais and two decimal places`() {
        assertEquals("1.234,05", formatBrlInput(123405))
        assertEquals("", formatBrlInput(null))
    }

    private fun ComposeUiTest.setup(
        state: GroupSetupState = state(),
        access: GroupSetupAccess = GroupSetupAccess.ORGANIZER,
        photoState: GroupPhotoState = GroupPhotoState(),
        onPhotoIntent: (GroupPhotoIntent) -> Unit = {},
        onBack: () -> Unit = {},
        onMoreOptions: () -> Unit = {},
        onIntent: (GroupSetupIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            GroupSetupScreen(
                state = state,
                access = access,
                photoState = photoState,
                onPhotoIntent = onPhotoIntent,
                onBack = onBack,
                onMoreOptions = onMoreOptions,
                onIntent = onIntent,
            )
        }
    }

    private companion object {
        val slot = GroupRegularSlotForm(weekday = GroupWeekday.WEDNESDAY, startTime = "19:30", durationMinutes = 90)

        fun requiredForm(modality: GroupModality = GroupModality.COURT_VOLLEYBALL) = newGroupDefaults().copy(
            name = "Vôlei de terça",
            modality = modality,
            composition = GroupComposition.MIXED,
        )

        fun state(
            mode: GroupSetupMode = GroupSetupMode.CREATE,
            form: GroupSetupForm = newGroupDefaults(),
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
