package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.sampleGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupOnboardingTest {
    @Test fun emptyGroupOffersFirstGameOnlyToOrganizer() {
        assertEquals(GroupOnboarding.CreateGame, groupOnboarding(GroupRole.OWNER, emptyList(), null))
        assertNull(groupOnboarding(GroupRole.ATHLETE, emptyList(), null))
    }

    @Test fun firstPublishedGameOffersSharingWithoutCreatingADuplicate() {
        val game = sampleGame()
        assertEquals(GroupOnboarding.InviteAthletes(game.id), groupOnboarding(GroupRole.ADMIN, listOf(game), game.id))
    }

    @Test fun draftsAndPastGamesDoNotPretendAnUpcomingGameIsReady() {
        assertNull(groupOnboarding(GroupRole.OWNER, listOf(sampleGame().copy(status = GameStatus.Draft)), null))
        assertNull(groupOnboarding(GroupRole.OWNER, listOf(sampleGame()), null))
    }

    @Test fun firstCompletedGameOffersItsFinancesEvenWithNextGameScheduled() {
        val completed = sampleGame().copy(id = "finished", status = GameStatus.Completed)
        val next = sampleGame()
        assertEquals(GroupOnboarding.ReviewFinances("finished"),
            groupOnboarding(GroupRole.OWNER, listOf(next, completed), next.id))
        assertNull(groupOnboarding(GroupRole.ATHLETE, listOf(completed), null))
    }

    @Test fun matureGroupsStopShowingTheStarterGuide() {
        val game = sampleGame().copy(status = GameStatus.Completed)
        assertNull(groupOnboarding(GroupRole.OWNER, listOf(game, game.copy(id = "second")), null))
    }
}
