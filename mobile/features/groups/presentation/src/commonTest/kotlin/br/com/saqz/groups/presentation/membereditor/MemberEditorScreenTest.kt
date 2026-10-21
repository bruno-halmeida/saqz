package br.com.saqz.groups.presentation.membereditor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.ui.LocalOpenSubscriptionPlans
import br.com.saqz.groups.presentation.ui.SubscriptionRequiredTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MemberEditorScreenTest {
    @Test
    fun `operation error is visible in the normal form`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(error = GroupUiError.Conflict),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag(MemberEditorTags.OperationError).assertExists()
        onNodeWithText("Não foi possível concluir a alteração. Tente novamente.").assertExists()
    }

    @Test
    fun `legacy group without modality hides conditional fields`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(modality = null),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        onNodeWithText("Posição").assertDoesNotExist()
        onNodeWithTag(MemberEditorTags.position(AthletePosition.PONTA)).assertDoesNotExist()
        onNodeWithTag(MemberEditorTags.side(AthletePreferredSide.DIREITA)).assertDoesNotExist()
        onNodeWithTag(MemberEditorTags.level(AthleteLevel.AVANCADO)).assertExists()
    }

    @Test
    fun `switching a monthly member with a pending monthly charge to single game says the charge continues`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(
                        persistedMembershipType = AthleteMembershipType.MENSALISTA,
                        membershipType = AthleteMembershipType.AVULSO,
                        hasPendingMonthly = true,
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag(MemberEditorTags.MonthlyPendingNotice).performScrollTo().assertExists()
        onNodeWithText("A mensalidade pendente continua. Resolva no financeiro.").assertExists()
    }

    @Test
    fun `no pending monthly charge or no switch means no notice`() = runComposeUiTest {
        var state by mutableStateOf(
            memberEditorPreviewState.copy(
                persistedMembershipType = AthleteMembershipType.MENSALISTA,
                membershipType = AthleteMembershipType.AVULSO,
                hasPendingMonthly = false,
            ),
        )
        setContent { SaqzTheme { MemberEditorScreen(state = state, onIntent = {}, onBack = {}) } }

        onNodeWithTag(MemberEditorTags.MonthlyPendingNotice).assertDoesNotExist()

        state = state.copy(hasPendingMonthly = true, membershipType = AthleteMembershipType.MENSALISTA)
        waitForIdle()
        onNodeWithTag(MemberEditorTags.MonthlyPendingNotice).assertDoesNotExist()
    }

    @Test
    fun `exemption switch only for monthly members and the billing card goes away when it is on`() = runComposeUiTest {
        var state by mutableStateOf(
            memberEditorPreviewState.copy(
                persistedMembershipType = AthleteMembershipType.MENSALISTA,
                membershipType = AthleteMembershipType.MENSALISTA,
                monthlyFeeExempt = false,
            ),
        )
        setContent { SaqzTheme { MemberEditorScreen(state = state, onIntent = {}, onBack = {}) } }

        onNodeWithTag(MemberEditorTags.Exemption).performScrollTo().assertExists()
        onNodeWithText("Continua mensalista, sem mensalidade.").assertExists()
        onNodeWithTag(MemberEditorTags.Billing).assertExists()

        state = state.copy(monthlyFeeExempt = true)
        waitForIdle()
        onNodeWithTag(MemberEditorTags.Exemption).assertExists()
        onNodeWithTag(MemberEditorTags.Billing).assertDoesNotExist()

        state = state.copy(
            persistedMembershipType = AthleteMembershipType.AVULSO,
            membershipType = AthleteMembershipType.AVULSO,
            monthlyFeeExempt = false,
        )
        waitForIdle()
        onNodeWithTag(MemberEditorTags.Exemption).assertDoesNotExist()
    }

    @Test
    fun `exemption confirmation names the person and confirms with Dar isencao`() = runComposeUiTest {
        val intents = mutableListOf<MemberEditorIntent>()
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(
                        persistedMembershipType = AthleteMembershipType.MENSALISTA,
                        membershipType = AthleteMembershipType.MENSALISTA,
                        exemptionConfirmOpen = true,
                    ),
                    onIntent = { intents += it },
                    onBack = {},
                )
            }
        }

        onNodeWithText("Dar isenção da mensalidade?").assertExists()
        onNodeWithText(
            "${memberEditorPreviewState.name} continua mensalista e não recebe mais mensalidade. A pendente deste mês será dispensada.",
        ).assertExists()
        onNodeWithText("Voltar").assertExists()
        onNodeWithTag(MemberEditorTags.ExemptionConfirm).performClick()
        assertEquals(MemberEditorIntent.ConfirmExemption, intents.last())
    }

    @Test
    fun `owner editor hides the remove action`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(
                        role = br.com.saqz.groups.domain.group.GroupRole.OWNER,
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag(MemberEditorTags.Remove).assertDoesNotExist()
    }

    @Test
    fun `admin editor hides remove for a viewer who is not the owner and shows it for the owner`() = runComposeUiTest {
        var canManageRoles by mutableStateOf(false)
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(
                        role = br.com.saqz.groups.domain.group.GroupRole.ADMIN,
                        canManageRoles = canManageRoles,
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag(MemberEditorTags.Remove).assertDoesNotExist()
        canManageRoles = true
        waitForIdle()
        onNodeWithTag(MemberEditorTags.Remove).assertExists()
    }

    // ---- Backend v.0.0.17: promover durante o teste responde 403 SUBSCRIPTION_REQUIRED ----

    @Test
    fun `refused promotion opens the plan sheet that offers the plans`() = runComposeUiTest {
        val intents = mutableListOf<MemberEditorIntent>()
        var plansOpened = 0
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalOpenSubscriptionPlans provides { plansOpened++ }) {
                    MemberEditorScreen(
                        state = memberEditorPreviewState.copy(subscriptionRequired = true),
                        onIntent = { intents += it },
                        onBack = {},
                    )
                }
            }
        }

        onNodeWithTag(SubscriptionRequiredTags.Dialog).assertExists()
        onNodeWithText("Seu plano não está ativo").assertExists()
        onNodeWithTag(SubscriptionRequiredTags.ViewPlans).performClick()
        assertEquals(1, plansOpened)
        assertEquals(listOf<MemberEditorIntent>(MemberEditorIntent.DismissSubscriptionRequired), intents)
    }

    @Test
    fun `plan sheet after the trial ended says so and not now only closes it`() = runComposeUiTest {
        val intents = mutableListOf<MemberEditorIntent>()
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(subscriptionRequired = true, trialEnded = true),
                    onIntent = { intents += it },
                    onBack = {},
                )
            }
        }

        onNodeWithText("Seu teste terminou").assertExists()
        onNodeWithTag(SubscriptionRequiredTags.NotNow).performClick()
        assertEquals(listOf<MemberEditorIntent>(MemberEditorIntent.DismissSubscriptionRequired), intents)
    }

    @Test
    fun `without a plan failure there is no plan sheet`() = runComposeUiTest {
        setContent {
            SaqzTheme { MemberEditorScreen(state = memberEditorPreviewState, onIntent = {}, onBack = {}) }
        }

        onNodeWithTag(SubscriptionRequiredTags.Dialog).assertDoesNotExist()
    }
}
