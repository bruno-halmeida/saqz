package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AthleteOnboardingCardTest {
    @Test fun savedAthleteResponseMountsIntroductionButPendingAndAdminDoNot() = runComposeUiTest {
        var state by mutableStateOf(GroupDetailsPreviewData.member.copy(athleteIntroVisible = true))
        setContent { SaqzTheme { GroupDetailsScreen(state, {}, {}) } }
        onNodeWithTag(AthleteOnboardingTags.Card).assertExists()
        runOnIdle { state = state.copy(responding = true) }
        onNodeWithTag(AthleteOnboardingTags.Card).assertDoesNotExist()
        runOnIdle { state = state.copy(responding = false, isAdmin = true) }
        onNodeWithTag(AthleteOnboardingTags.Card).assertDoesNotExist()
    }

    @Test fun athleteCanShareOrDismissWithoutAnotherSignup() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setContent { SaqzTheme { AthleteOnboardingCard(false, intents::add) } }
        onNodeWithTag(AthleteOnboardingTags.Share).performClick()
        onNodeWithTag(AthleteOnboardingTags.Dismiss).performClick()
        assertEquals(listOf(GroupDetailsIntent.ShareSaqz, GroupDetailsIntent.DismissAthleteIntro), intents)
        onNodeWithText("Sua resposta foi salva. Conheça o Saqz.").assertExists()
    }

    @Test fun shareFailureOffersRetryWithoutClaimingDelivery() = runComposeUiTest {
        setContent { SaqzTheme { AthleteOnboardingCard(true, {}) } }
        onNodeWithText("Não foi possível abrir o compartilhamento. Tente novamente.").assertExists()
        onNodeWithTag(AthleteOnboardingTags.Share).assertExists()
    }

    @Test fun introductionDoesNotAppearBeforeResponseOrForOrganizer() = runComposeUiTest {
        setContent { SaqzTheme { GroupDetailsScreen(GroupDetailsPreviewData.member, {}, {}) } }
        onNodeWithTag(AthleteOnboardingTags.Card).assertDoesNotExist()
    }
}
