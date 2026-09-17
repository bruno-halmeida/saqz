package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.details.AttendanceSummaryUi
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupHeaderUi
import br.com.saqz.groups.presentation.details.GroupOwnDebtUi
import br.com.saqz.groups.presentation.details.GroupSummaryChipUi
import br.com.saqz.groups.presentation.details.MemberPreviewUi
import br.com.saqz.groups.presentation.details.MemberStatusUi
import br.com.saqz.groups.presentation.details.NextGameUi
import br.com.saqz.groups.presentation.details.NoticeUi
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.details.VenueUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi

/**
 * Os estados-base das previews e das capturas. Regra (AGENTS.md §11): só entra aqui o que o
 * `GroupDetailsViewModel` produz — com jogo, `nextGame` e `attendance` chegam JUNTOS; sem
 * jogo, os dois são `null`. Os estados de cada bloco derivam daqui com `.copy(...)` no
 * arquivo de preview do próprio bloco, nunca neste.
 */
internal object GroupDetailsPreviewData {
    val header = GroupHeaderUi(
        name = "Vôlei do CERET",
        subtitle = "Tatuapé · Misto · Intermediário",
    )
    val memberHeader = header.copy(
        summaryChips = listOf(
            GroupSummaryChipUi("Vôlei de quadra"),
            GroupSummaryChipUi("Terças e quintas", highlighted = true),
        ),
    )
    val venue = VenueUi(name = "CERET — Quadra 2", address = "R. Canuto Abreu, s/n · Tatuapé")

    val nextGame = NextGameUi(
        gameId = "game-1",
        date = "Ter, 28/07 · 19h30",
        venue = "CERET — Quadra 2 · Tatuapé",
        deadline = "Encerra hoje · 18h",
        display = "Terça, 19h30",
        meta = "28 de julho · CERET — Quadra 2",
        address = "R. Canuto Abreu, s/n · Tatuapé",
        deadlineLine = "As confirmações encerram hoje às 18h00.",
        deadlineShort = "Encerra 28/07 · 18h00",
        bellLabel = "Avisamos você se abrir vaga até 18h00 de 28/07.",
        confirmedCount = 9,
        capacity = 12,
        confirmedNames = listOf(
            "Lucas Prado",
            "Bia Souza",
            "Thiago Melo",
            "Ana Lima",
            "Caio Reis",
            "Duda Nunes",
            "Eva Rocha",
            "Fábio Sá",
            "Gil Matos",
        ),
        availableSpots = 3,
    )

    val attendance = AttendanceSummaryUi(
        confirmedCount = 9,
        capacity = 12,
        going = 9,
        notGoing = 1,
        pending = 2,
        availableSpots = 3,
    )

    // VUL-203 — uma pendente vencida, uma a vencer e o histórico com os três desfechos.
    val ownCharges = OwnChargesUi(
        pending = listOf(
            OwnChargeUi(
                id = "c-1",
                title = "Mensalidade · Agosto",
                dueLabel = "Venceu em 10/08",
                amountLabel = "R$ 70,00",
                status = OwnChargeStatusUi.Pending,
            ),
            OwnChargeUi(
                id = "c-2",
                title = "Jogo avulso",
                dueLabel = "Vence em 28/08",
                amountLabel = "R$ 25,00",
                status = OwnChargeStatusUi.Pending,
            ),
        ),
        history = listOf(
            OwnChargeUi(
                id = "c-3",
                title = "Mensalidade · Julho",
                dueLabel = "Vencimento 10/07",
                amountLabel = "R$ 70,00",
                status = OwnChargeStatusUi.Paid,
            ),
            OwnChargeUi(
                id = "c-4",
                title = "Mensalidade · Junho",
                dueLabel = "Vencimento 10/06",
                amountLabel = "R$ 70,00",
                status = OwnChargeStatusUi.Waived,
            ),
            OwnChargeUi(
                id = "c-5",
                title = "Jogo avulso",
                dueLabel = "Vencimento 03/06",
                amountLabel = "R$ 25,00",
                status = OwnChargeStatusUi.Cancelled,
            ),
        ),
        pix = PixUi(key = "ceret@volei.com.br", label = "Lucas Prado"),
        debt = GroupOwnDebtUi(
            eyebrow = "Mensalidade · Agosto",
            totalLabel = "R$ 95,00",
            dueLabel = "Venceu em 10/08",
            overdue = true,
            countLabel = "2 cobranças em aberto",
            receiverLabel = "Pix de Lucas Prado",
        ),
    )

    /** Dono COM jogo marcado e ainda sem resposta: o que o gestor real vê. */
    val admin = GroupDetailsState(
        isLoading = false,
        isAdmin = true,
        isOwner = true,
        header = header,
        nextGame = nextGame,
        attendance = attendance,
        cashbox = CashboxUi(summary = "Saldo R$ 380,00"),
        venue = venue,
        memberCount = 26,
        scheduleSummary = "Ter, Qui",
    )

    /** Dono sem jogo marcado: `nextGame` e `attendance` somem juntos, como no ViewModel. */
    val adminNoGame = admin.copy(nextGame = null, attendance = null)

    val member = GroupDetailsState(
        isLoading = false,
        isAdmin = false,
        header = memberHeader,
        nextGame = nextGame,
        attendance = attendance,
        memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Confirmed),
        membershipType = AthleteMembershipType.MENSALISTA,
        autoConfirmationVisible = true,
        autoConfirmationEnabled = true,
        venue = venue,
        latestNotice = NoticeUi(
            author = "Lucas",
            authorIsAdmin = true,
            body = "Cheguem 15 min antes para montar a rede.",
            timestamp = "Hoje, 10h30",
        ),
        memberPreview = listOf(
            MemberPreviewUi("1", "Lucas Prado", "Organizador · levantador", MemberStatusUi.Admin),
            MemberPreviewUi("2", "Bia Souza", "Ponteira", MemberStatusUi.Going),
            MemberPreviewUi("3", "Thiago Melo", "Central"),
        ),
        memberCount = 26,
        ownCharges = ownCharges,
    )

    val memberNoGame = member.copy(
        nextGame = null,
        attendance = null,
        memberResponse = null,
        autoConfirmationVisible = false,
    )

    val memberOwnChargesLoading = member.copy(ownCharges = OwnChargesUi(isLoading = true))

    val memberOwnChargesFailed = member.copy(ownCharges = OwnChargesUi(failed = true))

    /** Sem pendência não há Pix: a seção fica só com o histórico. */
    val memberOwnChargesSettled = member.copy(
        ownCharges = ownCharges.copy(pending = emptyList(), pix = null, debt = null),
    )
}
