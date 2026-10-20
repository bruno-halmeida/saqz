package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
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
        onNodeWithText("Prioridade dos mensalistas até hoje às 18h00.").assertExists()
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
    fun afterTheDeadlineTheAnswerStaysOpenAndExplainsTheWaitlist() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closedPending)

        onNodeWithText("O prazo acabou: quem confirmar agora entra na lista de espera e sobe pela ordem se houver vaga.").assertExists()
        onAllNodesWithText("Em andamento. Não dá mais para responder.").assertCountEquals(0)
        onAllNodesWithText("Prioridade dos mensalistas até hoje às 18h00.").assertCountEquals(0)
        onNodeWithTag(GroupGameResponseTags.Going).assertIsEnabled()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertIsEnabled()
    }

    // ---- Backend v.0.0.17: depois do prazo o servidor recusa o "Vou"/"Não vou" ----

    @Test
    fun aDeadlineRefusedByTheServerClosesTheAnswerWithoutSayingTheGameStarted() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closedByDeadlinePending)

        onNodeWithText("As confirmações deste jogo encerraram. Fale com o organizador.").assertExists()
        onAllNodesWithText("Em andamento. Não dá mais para responder.").assertCountEquals(0)
        onAllNodesWithText("O prazo acabou: quem confirmar agora entra na lista de espera e sobe pela ordem se houver vaga.").assertCountEquals(0)
        onAllNodesWithText("Não foi possível salvar sua resposta. Tente novamente.").assertCountEquals(0)
        onNodeWithTag(GroupGameResponseTags.Going).assertIsNotEnabled()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertIsNotEnabled()
    }

    @Test
    fun aDeadlineRefusedByTheServerAlsoClosesChangeForWhoAnswered() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closedByDeadline)

        onNodeWithText("Sua presença está confirmada.").assertExists()
        onNodeWithText("As confirmações deste jogo encerraram. Fale com o organizador.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Change).assertIsNotEnabled()
    }

    @Test
    fun onceTheGameStartedTheAnswerIsClosed() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.startedPending)

        onNodeWithText("Em andamento. Não dá mais para responder.").assertExists()
        onAllNodesWithText("O prazo acabou: quem confirmar agora entra na lista de espera e sobe pela ordem se houver vaga.").assertCountEquals(0)
        onNodeWithTag(GroupGameResponseTags.Going).assertIsNotEnabled()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertIsNotEnabled()
    }

    @Test
    fun afterTheDeadlineChangeStaysEnabledForWhoAnswered() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closed)

        onNodeWithText("Sua presença está confirmada.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Change).assertIsEnabled()
    }

    @Test
    fun onceTheGameStartedChangeIsDisabledForWhoAnswered() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.started)

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
    fun feeNoteIsHiddenOnceTheGameStartedOrResponsesClosed() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberFee.copy(nextGame = GroupHeroPreviewData.game.copy(hasGameFee = true, confirmationOpen = false, responseOpen = false)))
        onAllNodesWithTag(GroupDetailsTags.HeroFeeNote).assertCountEquals(0)
    }

    @Test
    fun explicitMonthlyMemberNeverSeesTheFeeNote() = runComposeUiTest {
        setDetailsScreen(
            GroupHeroPreviewData.dayMemberFee.copy(membershipType = AthleteMembershipType.MENSALISTA),
        )
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

    /** DEB-109, quadro `Grupo`: quem administra um jogo com posições vê o placar compacto e a meia quadra. */
    @Test
    fun adminOfAGameWithPositionsSeesTheCompactScoreAndTheCourt() = runComposeUiTest {
        val court = br.com.saqz.groups.presentation.details.GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions())
        val state = GroupHeroPreviewData.adminPending.let { it.copy(attendance = it.attendance?.copy(court = court, waiting = 1)) }
        setDetailsScreen(state)

        onNodeWithTag(GroupPositionsPanelTags.Panel).performScrollTo()
        onNodeWithText("POSIÇÕES NA QUADRA", useUnmergedTree = true).assertExists()
        onNodeWithText("9 Vão", useUnmergedTree = true).assertExists()
        onNodeWithText("1 Não vão", useUnmergedTree = true).assertExists()
        onNodeWithText("1 Na espera", useUnmergedTree = true).assertExists()
        // Sai o placar de 4 colunas, com o "Sem resposta".
        onAllNodesWithTag(HomeAdminTags.ScorePending, useUnmergedTree = true).assertCountEquals(0)
        onNodeWithText("Líbero", useUnmergedTree = true).assertExists()
        onNodeWithText("3/4", useUnmergedTree = true).assertExists()
        onAllNodesWithText("Falta 1", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("Ponteiro(a)", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("4/4", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("Na espera: 1 Ponteiro(a)", useUnmergedTree = true).assertExists()
        onAllNodesWithTag(GroupPositionsPanelTags.AllComplete, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun tappingThePositionsPanelOpensTheGame() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        val court = br.com.saqz.groups.presentation.details.GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions())
        setDetailsScreen(GroupHeroPreviewData.adminPending.let { it.copy(attendance = it.attendance?.copy(court = court)) }) {
            intents += it
        }
        onNodeWithTag(GroupPositionsPanelTags.Panel).performScrollTo().assertHasClickAction().performClick()
        assertEquals(GroupDetailsIntent.ViewGame, intents.single())
    }

    @Test
    fun allPositionsCompleteShowTheCheck() = runComposeUiTest {
        val court = br.com.saqz.groups.presentation.details.GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions(complete = true))
        setDetailsScreen(GroupHeroPreviewData.adminPending.let { it.copy(attendance = it.attendance?.copy(court = court)) })
        onNodeWithTag(GroupPositionsPanelTags.Panel).performScrollTo()
        onNodeWithTag(GroupPositionsPanelTags.AllComplete, useUnmergedTree = true).assertExists()
        onNodeWithText("Todas completas", useUnmergedTree = true).assertExists()
    }

    @Test
    fun athleteOfAGameWithPositionsKeepsTheUsualCard() = runComposeUiTest {
        val court = br.com.saqz.groups.presentation.details.GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions())
        setDetailsScreen(GroupHeroPreviewData.pending.let { it.copy(attendance = it.attendance?.copy(court = court)) })
        onAllNodesWithTag(GroupPositionsPanelTags.Panel).assertCountEquals(0)
        onNodeWithText("9 de 12 confirmados").assertExists()
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
        onNodeWithText("Se você subir, sua presença aparece aqui. Confira antes do jogo.").assertExists()
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
        onAllNodesWithText("Confirmar presença automaticamente").assertCountEquals(0)
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
    fun firstGameHeroReplacesTheCreateGuideInTheOnboardingGroup() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminFirstGame, onboardingGroup = true) { intents += it }

        onNodeWithText("Grupo criado!").assertExists()
        onNodeWithText("Marcar primeiro jogo").assertExists()
        inHero(GroupDetailsTags.HeroInvite).assertCountEquals(0)
        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
        // A lista continua a da Início: grupo feito, jogo é o passo de agora, convite espera.
        onNodeWithTag(GroupOnboardingStepsTags.Steps).assertExists()
        onNodeWithText("Feito").assertExists()
        onNodeWithText("Agora").assertExists()
        inHero(GroupDetailsTags.CreateNextGame)[0].performScrollTo().performClick()
        onNodeWithTag(GroupOnboardingStepsTags.step(2)).performScrollTo().performClick()
        onNodeWithTag(GroupOnboardingStepsTags.step(3)).performScrollTo().performClick()

        assertEquals(
            listOf(GroupDetailsIntent.CreateNextGame, GroupDetailsIntent.CreateNextGame, GroupDetailsIntent.InviteByLink),
            intents,
        )
    }

    @Test
    fun firstGameHeroSaysTheTrialHasStarted() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminFirstGame.copy(trialDays = 14), onboardingGroup = true)

        onNodeWithText("Grupo criado!").assertExists()
        onNodeWithText("Seus 14 dias grátis começaram.", substring = true).assertExists()
    }

    @Test
    fun secondGroupHasNoGuideEvenWhenBrandNew() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminFirstGame)

        onAllNodesWithText("Grupo criado!").assertCountEquals(0)
        onNodeWithText("Sem jogo marcado").assertExists()
        inHero(GroupDetailsTags.HeroInvite).assertCountEquals(1)
        onAllNodesWithTag(GroupOnboardingStepsTags.Steps).assertCountEquals(0)
        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
    }

    @Test
    fun gameMarkedMovesTheGuideToTheInviteStepWithoutTheOldCard() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminInviteGuide, onboardingGroup = true) { intents += it }

        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
        onNodeWithTag(GroupOnboardingStepsTags.Steps).assertExists()
        onNodeWithTag(GroupOnboardingStepsTags.step(3)).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.InviteByLink, intents.single())
    }

    @Test
    fun stepsDisappearOnceTheGameIsMarkedAndSomeoneJoined() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminInviteGuide.copy(memberCount = 2), onboardingGroup = true)

        onAllNodesWithTag(GroupOnboardingStepsTags.Steps).assertCountEquals(0)
    }

    @Test
    fun financeGuideStaysAsACardInTheOnboardingGroup() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminFinanceGuide, onboardingGroup = true)

        onNodeWithTag(GroupOnboardingTags.Card).assertExists()
        onNodeWithText("Ver acerto do primeiro jogo").assertExists()
    }

    @Test
    fun guideNeverShowsForAMember() = runComposeUiTest {
        setDetailsScreen(
            GroupHeroPreviewData.adminFinanceGuide.copy(isAdmin = false, isOwner = false),
            onboardingGroup = true,
        )
        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
        onAllNodesWithTag(GroupOnboardingStepsTags.Steps).assertCountEquals(0)
    }

    @Test
    fun heroNeverOffersAutoConfirmation() = runComposeUiTest {
        listOf(GroupHeroPreviewData.confirmed, GroupHeroPreviewData.pending, GroupHeroPreviewData.dayMemberFee, GroupHeroPreviewData.adminConfirmed)
            .forEach { state ->
                setDetailsScreen(state)
                onAllNodesWithText("Confirmar presença automaticamente").assertCountEquals(0)
                onAllNodesWithText("Não foi possível atualizar a confirmação automática. Tente novamente.").assertCountEquals(0)
            }
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
