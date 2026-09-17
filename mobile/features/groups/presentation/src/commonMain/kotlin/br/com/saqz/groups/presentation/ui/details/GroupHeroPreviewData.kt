package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.details.AttendanceSummaryUi
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import br.com.saqz.groups.presentation.details.GroupDetailsToast
import br.com.saqz.groups.presentation.details.GroupOnboarding
import br.com.saqz.groups.presentation.details.GroupWaitlistUi
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi

/**
 * Os estados do hero, um por prancha do mock (`_mock-grupo/png`). Tudo deriva de
 * [GroupDetailsPreviewData] com `.copy(...)` e só descreve o que o `GroupDetailsViewModel`
 * produz — por isso "respondendo" já traz a resposta otimista: o ViewModel troca a resposta
 * ANTES de ligar `responding`.
 */
internal object GroupHeroPreviewData {
    val game = GroupDetailsPreviewData.nextGame.copy(
        display = "Terça, 19h30",
        meta = "28 de julho · CERET — Quadra 2",
        address = "R. Canuto Abreu, s/n · Tatuapé",
        deadlineLine = "As confirmações encerram hoje às 18h00.",
        deadlineShort = "Encerra 28/07 · 18h00",
        bellLabel = "Avisamos você se abrir vaga até 18h00 de 28/07.",
    )
    private val closedGame = game.copy(confirmationOpen = false)
    private val fullGame = game.copy(confirmedCount = 12, availableSpots = 0)
    private val confirmedResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Confirmed)

    // Membro
    val confirmed = GroupDetailsPreviewData.member.copy(nextGame = game, memberResponse = confirmedResponse)
    val pending = confirmed.copy(memberResponse = null, autoConfirmationEnabled = false)
    val confirmedToast = confirmed.copy(toast = GroupDetailsToast.Confirmed)
    val responding = confirmed.copy(responding = true, autoConfirmationEnabled = false)
    val declined = pending.copy(memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Declined))
    val responseFailed = pending.copy(responseFailed = true)
    val closed = confirmed.copy(nextGame = closedGame)
    val closedPending = pending.copy(nextGame = closedGame)
    val noAddress = pending.copy(nextGame = game.copy(address = ""))
    val dayMemberFee = pending.copy(
        membershipType = AthleteMembershipType.AVULSO,
        autoConfirmationVisible = false,
        nextGame = game.copy(hasGameFee = true),
    )
    val dayMemberNoFee = dayMemberFee.copy(nextGame = game)
    val rosterStale = confirmed.copy(rosterStale = true)
    val rosterRefreshing = rosterStale.copy(rosterRefreshing = true)
    val mapFailed = confirmed.copy(mapFailed = true)
    val autoConfirmationFailed = confirmed.copy(autoConfirmationEnabled = false, autoConfirmationFailed = true)
    val autoConfirmationUpdating = confirmed.copy(autoConfirmationUpdating = true)
    val athleteIntro = confirmed.copy(athleteIntroVisible = true, autoConfirmationVisible = false)
    val noGame = GroupDetailsPreviewData.memberNoGame

    val reserve = confirmed.copy(
        nextGame = fullGame,
        memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Waitlisted, waitlistPosition = 1),
        waitlist = GroupWaitlistUi(
            kind = HomeWaitlistKind.Reserva,
            rows = listOf(HomeWaitlistRowUi("Bruna Silva", 1, isSelf = true)),
        ),
        autoConfirmationVisible = false,
    )
    val dayMemberList = reserve.copy(
        nextGame = game,
        membershipType = AthleteMembershipType.AVULSO,
        memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Waitlisted, waitlistPosition = 2),
        waitlist = GroupWaitlistUi(
            kind = HomeWaitlistKind.AvulsoList,
            rows = listOf(
                HomeWaitlistRowUi("Lucas Pereira", 1, isSelf = false),
                HomeWaitlistRowUi("Bruna Silva", 2, isSelf = true),
                HomeWaitlistRowUi("Tiago Moraes", 3, isSelf = false),
            ),
        ),
    )

    // Gestor
    val adminPending = GroupDetailsPreviewData.admin.copy(nextGame = game)
    val adminConfirmed = adminPending.copy(
        memberResponse = confirmedResponse,
        attendance = AttendanceSummaryUi(confirmedCount = 10, capacity = 12, going = 10, notGoing = 1, pending = 1, availableSpots = 2),
        membershipType = AthleteMembershipType.MENSALISTA,
        autoConfirmationVisible = true,
        autoConfirmationEnabled = true,
    )
    val adminClosed = adminPending.copy(
        nextGame = closedGame,
        memberResponse = confirmedResponse,
        attendance = AttendanceSummaryUi(confirmedCount = 10, capacity = 12, going = 10, notGoing = 2, pending = 0, availableSpots = 2),
    )
    val adminNoGame = GroupDetailsPreviewData.adminNoGame
    val adminFirstGame = adminNoGame.copy(onboarding = GroupOnboarding.CreateGame)
    val adminInviteGuide = adminPending.copy(
        onboarding = GroupOnboarding.InviteAthletes("game-1"),
        attendance = AttendanceSummaryUi(confirmedCount = 0, capacity = 12, going = 0, notGoing = 0, pending = 1, availableSpots = 12),
    )
    val adminFinanceGuide = adminNoGame.copy(onboarding = GroupOnboarding.ReviewFinances("game-0"))
}
