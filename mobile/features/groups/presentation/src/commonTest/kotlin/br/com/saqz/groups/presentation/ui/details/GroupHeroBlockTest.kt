package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.ui.home.HomeAdminTags
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupHeroBlockTest {
    // Enquanto o card de cabeçalho antigo existir (C2), `CreateNextGame` aparece duas vezes para o
    // gestor: o que é do hero se procura DENTRO do hero.
    private fun ComposeUiTest.inHero(tag: String) =
        onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(GroupDetailsTags.Hero)))

    @Test
    fun pendingShowsBothButtonsAndGoingAsksToConfirm() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.pending) { intents += it }

        onNodeWithTag(GroupGameResponseTags.NotGoing).assertExists()
        onNodeWithText("As confirmações encerram hoje às 18h00.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Going).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Confirm), intents.single())
        onAllNodesWithText("Talvez").assertCountEquals(0)
    }

    @Test
    fun confirmedShowsThePanelWithChangeAndNoButtons() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.confirmed)

        onNodeWithText("Sua presença está confirmada.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Change).assertExists()
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
        onAllNodesWithTag(GroupGameResponseTags.NotGoing).assertCountEquals(0)
    }

    @Test
    fun changeBringsTheButtonsBackWithCancel() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithTag(GroupGameResponseTags.Change).performScrollTo().performClick()

        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(GroupGameResponseTags.Cancel).assertExists()
        onNodeWithTag(GroupGameResponseTags.NotGoing).performClick()
        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Decline), intents.single())
    }

    @Test
    fun declinedShowsTheDeclinedPanel() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.declined)

        onNodeWithText("Você não vai jogar.").assertExists()
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
    }

    @Test
    fun closedDeadlineSaysSoAndDisablesTheAnswer() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closedPending)

        onNodeWithText("As confirmações estão encerradas.").assertExists()
        onAllNodesWithText("As confirmações encerram hoje às 18h00.").assertCountEquals(0)
        onNodeWithTag(GroupGameResponseTags.Going).assertIsNotEnabled()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertIsNotEnabled()
    }

    @Test
    fun closedDeadlineDisablesChangeForWhoAnswered() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closed)

        onNodeWithText("Sua presença está confirmada.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Change).assertIsNotEnabled()
    }

    @Test
    fun rosterRefreshLocksTheAnswer() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.pending.copy(rosterRefreshing = true))

        onNodeWithTag(GroupGameResponseTags.Going).assertIsNotEnabled()
    }

    @Test
    fun failedResponseShowsTheErrorLine() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.responseFailed)

        onNodeWithTag(GroupGameResponseTags.Error).assertExists()
        onNodeWithText("Não foi possível salvar sua resposta. Tente novamente.").assertExists()
    }

    @Test
    fun dayMemberSeesTheFeeNoteOnlyWhenTheGameHasAFee() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberFee)

        onNodeWithTag(GroupDetailsTags.HeroFeeNote).assertExists()
        onNodeWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertExists()
    }

    @Test
    fun feeNoteIsHiddenWithoutFeeAndForMonthlyMembers() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberNoFee)
        onAllNodesWithTag(GroupDetailsTags.HeroFeeNote).assertCountEquals(0)
    }

    @Test
    fun monthlyMemberNeverSeesTheFeeNote() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.pending.copy(nextGame = GroupHeroPreviewData.game.copy(hasGameFee = true)))
        onAllNodesWithTag(GroupDetailsTags.HeroFeeNote).assertCountEquals(0)
    }

    @Test
    fun adminSeesTheScoreBoardAndNoRosterRow() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminPending) { intents += it }

        onAllNodesWithText("9 de 12 confirmados").assertCountEquals(0)
        // Dono e admin também jogam: o seletor é o mesmo do atleta.
        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(HomeAdminTags.ScorePending, useUnmergedTree = true).assertExists()
        onNodeWithTag(HomeAdminTags.ScoreGoing, useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.ViewGame, intents.single())
    }

    @Test
    fun memberSeesTheRosterRowWithScarcityAndNoScoreBoard() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.pending)

        onNodeWithText("9 de 12 confirmados").assertExists()
        onNodeWithText("Restam 3 vagas").assertExists()
        onAllNodesWithTag(HomeAdminTags.ScoreGoing, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun scarcityChipIsOnlyForWhoHasNotAnswered() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.confirmed)

        onNodeWithText("9 de 12 confirmados").assertExists()
        onAllNodesWithText("Restam 3 vagas").assertCountEquals(0)
    }

    @Test
    fun viewGameLinkOpensTheGame() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithTag(GroupDetailsTags.ViewGame).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.ViewGame, intents.single())
    }

    @Test
    fun mapLinkOpensTheGameAddress() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithText("R. Canuto Abreu, s/n · Tatuapé").assertExists()
        onNodeWithTag(GroupDetailsTags.HeroMap).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.OpenVenueMap, intents.single())
        onAllNodesWithTag(GroupDetailsTags.HeroMapFailure).assertCountEquals(0)
    }

    @Test
    fun emptyAddressHidesTheAddressLine() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.noAddress)

        onNodeWithTag(GroupDetailsTags.Hero).assertExists()
        onAllNodesWithTag(GroupDetailsTags.HeroMap).assertCountEquals(0)
    }

    @Test
    fun mapFailureIsShownUnderTheAddress() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.mapFailed)

        onNodeWithTag(GroupDetailsTags.HeroMapFailure).assertExists()
    }

    @Test
    fun staleRosterOffersARetry() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.rosterStale) { intents += it }

        onNodeWithTag(GroupDetailsTags.HeroRosterStale).assertExists()
        onNodeWithTag(GroupDetailsTags.HeroRosterRetry).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.RetryRoster, intents.single())
    }

    @Test
    fun retryIsLockedWhileTheRosterRefreshes() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.rosterRefreshing)

        onNodeWithTag(GroupDetailsTags.HeroRosterRetry).assertIsNotEnabled()
    }

    @Test
    fun reserveShowsTheQueuePiecesAndNoGoingButton() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.reserve) { intents += it }

        onNodeWithText("Lista de espera · 1º").assertExists()
        onNodeWithTag(HomeWaitlistTags.ConfirmedSection).assertExists()
        onNodeWithText("Avisamos você se abrir vaga até 18h00 de 28/07.").assertExists()
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
        onAllNodesWithText("12 de 12 confirmados").assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.ReservaLeave).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Decline), intents.single())
    }

    @Test
    fun dayMemberListShowsTheQueueAndTheUpsell() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberList)

        onNodeWithTag(HomeWaitlistTags.AvulsoChip).assertExists()
        onNodeWithTag(HomeWaitlistTags.QueueSection).assertExists()
        onNodeWithTag(HomeWaitlistTags.queueRow(2)).assertExists()
        onAllNodesWithTag(HomeWaitlistTags.ConfirmedSection).assertCountEquals(0)
    }

    @Test
    fun memberWithoutGameGetsTheEmptyHeroWithoutActions() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.noGame)

        onNodeWithTag(GroupDetailsTags.Hero).assertExists()
        onNodeWithText("Sem jogo marcado").assertExists()
        inHero(GroupDetailsTags.CreateNextGame).assertCountEquals(0)
        inHero(GroupDetailsTags.HeroInvite).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.ViewGame).assertCountEquals(0)
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
        onAllNodesWithTag(GroupGameResponseTags.AutoConfirmation).assertCountEquals(0)
    }

    @Test
    fun adminWithoutGameCanCreateAndInviteFromTheHero() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminNoGame) { intents += it }

        onNodeWithText("Marque o próximo e a galera confirma em um toque.").assertExists()
        inHero(GroupDetailsTags.CreateNextGame).assertCountEquals(1)
        inHero(GroupDetailsTags.CreateNextGame)[0].performScrollTo().performClick()
        inHero(GroupDetailsTags.HeroInvite)[0].performScrollTo().performClick()

        assertEquals(listOf(GroupDetailsIntent.CreateNextGame, GroupDetailsIntent.InviteByLink), intents)
    }

    @Test
    fun firstGameHeroReplacesTheCreateGuide() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminFirstGame) { intents += it }

        onNodeWithText("Grupo criado!").assertExists()
        onNodeWithText("Marcar primeiro jogo").assertExists()
        inHero(GroupDetailsTags.HeroInvite).assertCountEquals(0)
        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
        inHero(GroupDetailsTags.CreateNextGame)[0].performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.CreateNextGame, intents.single())
    }

    @Test
    fun inviteAndFinanceGuidesStayAsCardsForTheAdminOnly() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminInviteGuide)
        onNodeWithTag(GroupOnboardingTags.Card).assertExists()
        onNodeWithTag(GroupOnboardingTags.Responses).assertExists()
    }

    @Test
    fun guideNeverShowsForAMember() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminFinanceGuide.copy(isAdmin = false, isOwner = false))
        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
    }

    @Test
    fun autoConfirmationSwitchAsksToToggle() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithTag(GroupGameResponseTags.AutoConfirmation).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.ToggleAutoConfirmation(false), intents.single())
    }

    @Test
    fun autoConfirmationFailureIsExplainedAndUpdatingLocksTheSwitch() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.autoConfirmationFailed.copy(autoConfirmationUpdating = true))

        onNodeWithText("Não foi possível atualizar a confirmação automática. Tente novamente.").assertExists()
        onNodeWithTag(GroupGameResponseTags.AutoConfirmation).assertIsNotEnabled()
    }

    @Test
    fun dayMemberHasNoAutoConfirmation() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberFee)
        onAllNodesWithTag(GroupGameResponseTags.AutoConfirmation).assertCountEquals(0)
    }

    @Test
    fun toastFollowsTheStateValue() = runComposeUiTest {
        var state by mutableStateOf(GroupHeroPreviewData.confirmedToast)
        setContent { SaqzTheme { GroupDetailsScreen(state = state, onBack = {}, onIntent = {}) } }

        onNodeWithTag(GroupDetailsTags.Toast).assertExists()
        onNodeWithText("Presença confirmada. Bom jogo!").assertExists()

        runOnIdle { state = state.copy(toast = null) }
        onAllNodesWithTag(GroupDetailsTags.Toast).assertCountEquals(0)
    }

    @Test
    fun toastAsksToBeDismissedAfterItsDwell() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmedToast) { intents += it }

        mainClock.advanceTimeBy(10_000)

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.DismissToast), intents)
    }
}
