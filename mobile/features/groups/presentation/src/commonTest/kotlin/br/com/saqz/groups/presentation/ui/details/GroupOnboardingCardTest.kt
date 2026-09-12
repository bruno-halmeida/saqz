package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupOnboarding
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupOnboardingCardTest {
    @Test fun firstGameHasOneCreationAction() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setContent { SaqzTheme { GroupOnboardingCard(GroupOnboarding.CreateGame, intents::add) } }
        onNodeWithText("Marcar primeiro jogo").assertExists()
        onNodeWithTag(GroupOnboardingTags.Action).performClick()
        onNodeWithTag(GroupOnboardingTags.Responses).assertDoesNotExist()
        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OnboardingAction), intents)
    }

    @Test fun publishedGameLetsOrganizerInviteAndFollowResponses() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setContent { SaqzTheme { GroupOnboardingCard(GroupOnboarding.InviteAthletes("game"), intents::add) } }
        onNodeWithText("Preparar convite para WhatsApp").assertExists()
        onNodeWithTag(GroupOnboardingTags.Action).performClick()
        onNodeWithTag(GroupOnboardingTags.Responses).performClick()
        assertEquals(listOf(GroupDetailsIntent.OnboardingAction, GroupDetailsIntent.ViewGame), intents)
    }

    @Test fun completedGameOffersFinancialReview() = runComposeUiTest {
        setContent { SaqzTheme { GroupOnboardingCard(GroupOnboarding.ReviewFinances("game"), {}) } }
        onNodeWithText("Ver acerto do primeiro jogo").assertExists()
        onNodeWithTag(GroupOnboardingTags.Responses).assertDoesNotExist()
    }
}
