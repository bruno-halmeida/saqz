package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupVenueForm
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import br.com.saqz.groups.model.AttendanceUpdatesMode
import br.com.saqz.groups.model.MonthlyLateJoinPolicy
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupSetupScreenTest {
    @Test
    fun newGroupKeepsPhotoAndBasicsButDefersAdvancedSettings() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}) } }
        onNodeWithTag(GroupSetupTags.Photo).assertExists()
        onNodeWithTag(GroupSetupTags.Name).assertExists()
        onNodeWithTag(GroupSetupTags.Modality).assertExists()
        onNodeWithTag(GroupSetupTags.Composition).assertExists()
        onNodeWithTag(GroupSetupTags.Level).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        onNodeWithTag(GroupSetupTags.Level).assertExists()
        onNodeWithTag(GroupSetupTags.Capacity).assertExists()
    }

    @Test
    fun theGameFeeFieldAppearsWhenCreatingAndEditingWithTheHelpText() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}) } }
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        onNodeWithTag(GroupSetupTags.GameFee).performScrollTo().assertExists()
        onNodeWithText("Valor por jogo").performScrollTo().assertExists()
        onNodeWithText("R$").assertExists()
        onNodeWithText("Para avulsos. Cada jogo pode ter outro valor.").assertExists()
    }

    @Test
    fun typingTheGameFeeSendsTheCents() = runComposeUiTest {
        val received = mutableListOf<GroupSetupIntent>()
        setContent { SaqzTheme { GroupSetupScreen(PreviewErrorState, { received += it }, {}) } }

        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(GroupSetupTags.GameFee)), useUnmergedTree = true)
            .performScrollTo().performTextInput("25,50")

        assertEquals(GroupSetupIntent.UpdateDefaultGameFee(2550), received.last { it is GroupSetupIntent.UpdateDefaultGameFee })
    }

    @Test
    fun durationCounterSendsTheNeighbourOnTheGrid() = runComposeUiTest {
        val received = mutableListOf<GroupSetupIntent>()
        setContent {
            SaqzTheme {
                GroupSetupScreen(
                    GroupSetupState(mode = GroupSetupMode.Create).copy(durationMinutes = 180),
                    { received += it },
                    {},
                )
            }
        }
        openAdvancedIfCollapsed()

        onNodeWithTag(GroupSetupTags.Duration).performScrollTo().assertExists()
        onNodeWithContentDescription("Aumentar a duração").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.SelectDuration(240), received.last { it is GroupSetupIntent.SelectDuration })
    }

    @Test
    fun durationOutsideTheGridShowsTheValueAndAnError() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(
                    GroupSetupState(mode = GroupSetupMode.Create).copy(
                        durationMinutes = 45,
                        errors = setOf(GroupSetupError.DurationOutOfRange),
                    ),
                    {},
                    {},
                )
            }
        }
        openAdvancedIfCollapsed()
        onNodeWithText("De 1h a 8h. Até 3h, de 30 em 30 minutos.").performScrollTo().assertExists()
    }

    private fun ComposeUiTest.openAdvancedIfCollapsed() {
        if (onAllNodesWithTag(GroupSetupTags.Advanced).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        }
    }

    private fun ComposeUiTest.deleteSheetShows(memberCount: Int?, text: String) {
        val state = GroupSetupState(mode = GroupSetupMode.Edit("grp-1"), canDelete = true, memberCount = memberCount)
            .copy(sheet = GroupSetupSheet.ConfirmDelete)
        setContent { SaqzTheme { GroupSetupSheets(state, {}) } }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun deleteSheetCountsTheMembersWhoLoseAccess() {
        runComposeUiTest {
            deleteSheetShows(
                8,
                "Os 8 membros perdem o acesso e o histórico de jogos, avisos e caixa do grupo somem. Isso não pode ser desfeito.",
            )
        }
        runComposeUiTest {
            deleteSheetShows(
                1,
                "O 1 membro perde o acesso e o histórico de jogos, avisos e caixa do grupo somem. Isso não pode ser desfeito.",
            )
        }
    }

    @Test
    fun deleteSheetSaysSoWhenOnlyTheOwnerIsInTheGroup() = runComposeUiTest {
        deleteSheetShows(0, "Só você está no grupo. O histórico de jogos, avisos e caixa do grupo some. Isso não pode ser desfeito.")
    }

    @Test
    fun deleteSheetDoesNotInventANumberWhenTheCountIsUnknown() = runComposeUiTest {
        deleteSheetShows(
            null,
            "Os membros perdem o acesso e o histórico de jogos, avisos e caixa do grupo somem. Isso não pode ser desfeito.",
        )
    }

    @Test
    fun aZeroGameFeeShowsTheError() = runComposeUiTest {
        val state = GroupSetupState(mode = GroupSetupMode.Edit("grp-1")).let {
            it.copy(form = it.form.copy(defaultGameFeeCents = 0), errors = setOf(GroupSetupError.GameFeeInvalid))
        }
        setContent { SaqzTheme { GroupSetupScreen(state, {}, {}) } }

        onNodeWithText("Informe um valor maior que R$ 0,00.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun capacityBelowTheMinimumShowsTheMinimumOf4() = runComposeUiTest {
        val state = GroupSetupState(mode = GroupSetupMode.Create).let {
            val form = it.form.copy(defaultCapacity = 3)
            it.copy(form = form, errors = setOf(GroupSetupError.CapacityOutOfRange))
        }
        setContent { SaqzTheme { GroupSetupScreen(state, {}, {}) } }

        onNodeWithText("Mínimo de 4 jogadores.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editingAndValidationKeepAdvancedFieldsAccessible() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(PreviewErrorState, {}, {}) } }
        onNodeWithTag(GroupSetupTags.CustomLevel).assertExists()
        onNodeWithTag(GroupSetupTags.VenueAddress).assertExists()
    }

    // ---- JOGO-007: o endereço da quadra é sempre opcional ----

    private fun withoutAddress(mode: GroupSetupMode) = GroupSetupState(
        mode = mode,
        form = PreviewCourtForm.copy(defaultVenue = GroupVenueForm(name = "Quadra do Parque", address = "")),
    )

    @Test
    fun aVenueWithoutAddressShowsNoErrorWhenEditing() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(withoutAddress(GroupSetupMode.Edit("grp-1")), {}, {}) } }

        onNodeWithTag(GroupSetupTags.VenueAddress).performScrollTo().assertExists()
        onNodeWithText("Informe o endereço da quadra.").assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.SaveFailure).assertDoesNotExist()
    }

    @Test
    fun aShortAddressStillShowsTheMinimumError() = runComposeUiTest {
        val state = withoutAddress(GroupSetupMode.Edit("grp-1")).copy(errors = setOf(GroupSetupError.VenueAddressNotFound))
        setContent { SaqzTheme { GroupSetupScreen(state, {}, {}) } }

        onNodeWithText("Não encontramos esse endereço. Confira a rua e o bairro.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editingWithoutErrorsKeepsAdvancedFieldsExpanded() = runComposeUiTest {
        setContent {
            SaqzTheme { GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Edit("existing")), {}, {}) }
        }
        onNodeWithTag(GroupSetupTags.Advanced).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Level).assertExists()
        onNodeWithTag(GroupSetupTags.Capacity).assertExists()
    }

    @Test
    fun firstTrialGroupExplainsStartWithoutPayment() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}, showTrialOffer = true)
            }
        }
        onNodeWithTag(GroupSetupTags.TrialOffer).assertExists()
        onNodeWithText("Teste grátis com até 3 grupos, sem cobrança automática.").assertExists()
    }

    @Test
    fun editingDoesNotPromiseANewTrial() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Edit("existing")), {}, {}, showTrialOffer = true)
            }
        }
        onNodeWithTag(GroupSetupTags.TrialOffer).assertDoesNotExist()
    }
    @Test
    fun paidGroupDoesNotPromiseANewTrial() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}, showTrialOffer = false)
            }
        }
        onNodeWithTag(GroupSetupTags.TrialOffer).assertDoesNotExist()
    }

    @Test
    fun savingDisablesTheFormSubmit() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent {
            SaqzTheme {
                GroupSetupScreen(
                    state = GroupSetupState(
                        mode = GroupSetupMode.Edit(groupId = "grp-1"),
                        form = PreviewCourtForm,
                        isSaving = true,
                    ),
                    onIntent = intents::add,
                    onBack = {},
                )
            }
        }

        onNodeWithTag(GroupSetupTags.Submit).assertIsNotEnabled()
        onNodeWithTag(GroupSetupTags.Submit).performClick()
        waitForIdle()
        assertTrue(intents.isEmpty())
    }

    @Test
    fun creatingDisablesTheFormSubmitAndShowsProgress() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent {
            SaqzTheme {
                GroupSetupScreen(
                    state = GroupSetupState(
                        mode = GroupSetupMode.Create,
                        form = PreviewCourtForm,
                        isSaving = true,
                    ),
                    onIntent = intents::add,
                    onBack = {},
                )
            }
        }

        onNodeWithTag(GroupSetupTags.Submit).assertIsNotEnabled()
        onNodeWithTag(GroupSetupTags.Submit).performClick()
        waitForIdle()
        assertTrue(intents.isEmpty())
    }

    @Test
    fun editSaveFailureUsesSaveCopy() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(
                    state = GroupSetupState(
                        mode = GroupSetupMode.Edit(groupId = "grp-1"),
                        form = PreviewCourtForm,
                        saveFailed = true,
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }
        waitForIdle()

        onNodeWithText("Não foi possível salvar o grupo").assertExists()
        onNodeWithText("Não foi possível criar o grupo").assertDoesNotExist()
    }

    @Test
    fun monthlyFeeCardShowsAmountAndDueDayStepper() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        val state = monthlyState(fee = 8_000).let { it.copy(form = it.form.copy(monthlyDueDay = 10)) }
        setContent { SaqzTheme { GroupSetupScreen(state, { intents += it }, {}) } }

        onNodeWithTag(GroupSetupTags.MonthlyFee).performScrollTo().assertExists()
        onNodeWithText("Mensalidade").assertExists()
        onNodeWithText("Vale para quem entra como mensalista. Dá pra ajustar por pessoa depois.").assertExists()
        onNodeWithText("Dia do vencimento").assertExists()
        onNodeWithContentDescription("Dia seguinte").performScrollTo().performClick()
        waitForIdle()

        assertTrue(GroupSetupIntent.UpdateMonthlyDueDay(11) in intents)
    }

    private fun monthlyState(
        policy: MonthlyLateJoinPolicy? = MonthlyLateJoinPolicy.GROUP_DUE_DATE,
        fee: Long? = 5_000,
        rosterHasMensalista: Boolean = false,
    ) = GroupSetupState(
        mode = GroupSetupMode.Edit(groupId = "grp-1"),
        form = PreviewCourtForm.copy(monthlyFeeCents = fee, monthlyLateJoinPolicy = policy),
        canManageGameConfig = true,
        rosterHasMensalista = rosterHasMensalista,
    )

    @Test
    fun whatsAppListModeShowsScheduledByDefaultOnlyToTheOwnerAndReportsTheChoice() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        val owner = monthlyState().copy(
            canDelete = true,
            form = monthlyState().form.copy(attendanceUpdatesMode = AttendanceUpdatesMode.SCHEDULED),
        )
        var state by mutableStateOf(owner)
        setContent { SaqzTheme { GroupSetupScreen(state, { intents += it }, {}) } }

        onNodeWithTag(GroupSetupTags.AttendanceUpdatesMode).performScrollTo().assertExists()
        onNodeWithText("Por horário").assertIsSelected()
        onNodeWithText("Envia a lista às 9h, 12h30 e 18h.").assertExists()
        onNodeWithText("A cada confirmação").performClick()
        waitForIdle()
        assertTrue(GroupSetupIntent.SelectAttendanceUpdatesMode(AttendanceUpdatesMode.ON_CHANGE) in intents)

        state = owner.copy(canDelete = false)
        waitForIdle()
        onNodeWithTag(GroupSetupTags.AttendanceUpdatesMode).assertDoesNotExist()
    }

    @Test
    fun whatsAppListModeHintsFitOneLineAt390() = runComposeUiTest {
        var state by mutableStateOf(
            monthlyState().copy(
                canDelete = true,
                form = monthlyState().form.copy(attendanceUpdatesMode = AttendanceUpdatesMode.SCHEDULED),
            ),
        )
        setContent { SaqzTheme { Box(Modifier.width(390.dp)) { GroupSetupScreen(state, {}, {}) } } }

        fun lines(text: String): Int {
            val node = onNodeWithText(text).performScrollTo().fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            return layouts.single().lineCount
        }
        assertEquals(1, lines("Envia a lista às 9h, 12h30 e 18h."))
        state = state.copy(form = state.form.copy(attendanceUpdatesMode = AttendanceUpdatesMode.ON_CHANGE))
        waitForIdle()
        assertEquals(1, lines("Envia quando a lista muda, das 8h às 22h."))
    }

    @Test
    fun gameConfigHasNoAutoConfirmationOption() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(monthlyState(), {}, {}) } }

        onNodeWithTag(GroupSetupTags.MonthlyLateJoinPolicy).performScrollTo().assertExists()
        onNodeWithText("Confirmar mensalistas automaticamente").assertDoesNotExist()
        onNodeWithText("Mensalistas podem ativar confirmação automática de presença.").assertDoesNotExist()
    }

    @Test
    fun monthlyPolicyShowsThreeOptionsAndReportsTheChosenOne() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent { SaqzTheme { GroupSetupScreen(monthlyState(), { intents += it }, {}) } }

        onNodeWithTag(GroupSetupTags.MonthlyLateJoinPolicy).performScrollTo().assertExists()
        onNodeWithText("Quem vira mensalista depois do vencimento").assertExists()
        onNodeWithText("Paga este mês, já vencida").assertExists()
        onNodeWithText("Paga este mês, sem atraso").assertExists()
        onNodeWithText("Começa a pagar no mês seguinte").performScrollTo().performClick()
        waitForIdle()

        assertTrue(GroupSetupIntent.SelectMonthlyLateJoinPolicy(MonthlyLateJoinPolicy.NEXT_MONTH) in intents)
    }

    @Test
    fun monthlyPolicyShowsWhenOnlyAMensalistaHasTheirOwnFee() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(monthlyState(fee = null, rosterHasMensalista = true), {}, {}) } }
        onNodeWithTag(GroupSetupTags.MonthlyLateJoinPolicy).performScrollTo().assertExists()
    }

    @Test
    fun monthlyPolicyIsHiddenWhenTheGroupChargesNoMonthlyFeeAtAll() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(monthlyState(fee = null, rosterHasMensalista = false), {}, {}) } }
        onNodeWithTag(GroupSetupTags.MonthlyLateJoinPolicy).assertDoesNotExist()
    }

    @Test
    fun monthlyPolicyIsHiddenWhenTheBackendDoesNotSendIt() = runComposeUiTest {
        setContent {
            SaqzTheme { GroupSetupScreen(monthlyState(policy = null, rosterHasMensalista = true), {}, {}) }
        }
        onNodeWithTag(GroupSetupTags.MonthlyLateJoinPolicy).assertDoesNotExist()
    }

    @Test
    fun tappingASlotPillSendsTheEditAndTheRemoveXSendsOnlyTheRemoval() = runComposeUiTest {
        val received = mutableListOf<GroupSetupIntent>()
        val state = GroupSetupState(mode = GroupSetupMode.Create, form = PreviewCourtForm, recurring = true)
        setContent { SaqzTheme { GroupSetupScreen(state, { received += it }, {}) } }

        onAllNodesWithContentDescription("Remover dia e horário")[0].performScrollTo().performClick()
        assertEquals(listOf<GroupSetupIntent>(GroupSetupIntent.RemoveSlot(PreviewCourtForm.regularSlots[0])), received)

        onNodeWithContentDescription("Editar terça às 19h30").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = 0)), received.last())
    }
}
