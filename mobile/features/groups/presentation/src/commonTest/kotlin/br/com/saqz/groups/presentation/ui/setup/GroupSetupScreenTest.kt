package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.MonthlyLateJoinPolicy
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test
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
    fun editingAndValidationKeepAdvancedFieldsAccessible() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(PreviewErrorState, {}, {}) } }
        onNodeWithTag(GroupSetupTags.CustomLevel).assertExists()
        onNodeWithTag(GroupSetupTags.VenueAddress).assertExists()
    }

    // ---- Backend v.0.0.17: quadra sem endereço volta como 400 `defaultVenue.address` ----

    private fun refusedAddress(mode: GroupSetupMode) = GroupSetupState(
        mode = mode,
        form = PreviewCourtForm.copy(defaultVenue = GroupVenueForm(name = "Quadra do Parque", address = "")),
        errors = setOf(GroupSetupError.VenueAddressRequired),
    )

    // O dispatcher v2 enfileira os efeitos como na produção: o pedido de rolagem espera o layout do quadro.
    @Test
    fun aRefusedVenueAddressOpensTheAdvancedCardsAndMarksTheAddressWhenCreating() =
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent { SaqzTheme { GroupSetupScreen(refusedAddress(GroupSetupMode.Create), {}, {}) } }
            waitForIdle()

            onNodeWithTag(GroupSetupTags.Advanced).assertDoesNotExist()
            onNodeWithText("Informe o endereço da quadra.").assertIsDisplayed()
            onNodeWithText("Falta 1 informação para criar o grupo. Ela está marcada abaixo.").assertExists()
            onNodeWithTag(GroupSetupTags.SaveFailure).assertDoesNotExist()
        }

    @Test
    fun aRefusedVenueAddressMarksTheAddressAndTalksAboutSavingWhenEditing() =
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent { SaqzTheme { GroupSetupScreen(refusedAddress(GroupSetupMode.Edit("grp-1")), {}, {}) } }
            waitForIdle()

            onNodeWithText("Informe o endereço da quadra.").assertIsDisplayed()
            onNodeWithText("Falta 1 informação para salvar o grupo. Ela está marcada abaixo.").assertExists()
            onNodeWithText("Falta 1 informação para criar o grupo. Ela está marcada abaixo.").assertDoesNotExist()
            onNodeWithTag(GroupSetupTags.SaveFailure).assertDoesNotExist()
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
        onNodeWithText("Teste o Organizador com até 3 grupos e atletas ilimitados. O prazo começa no primeiro grupo e é o mesmo para todos, sem cobrança automática.").assertExists()
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
}
