package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import br.com.saqz.groups.presentation.details.GroupSettleRowUi
import br.com.saqz.groups.presentation.details.GroupWaitingRowUi
import br.com.saqz.groups.presentation.details.GroupWaitingUi

/**
 * Os estados do bloco "Esperando você" para os testes e as capturas. Derivam de
 * [GroupDetailsPreviewData.admin]; só entra aqui o que o `GroupDetailsViewModel` produz.
 */
internal object GroupWaitingPreviewData {
    private val nextGame = GroupDetailsPreviewData.nextGame.copy(deadlineShort = "Prioridade até 28/07 · 18h00")

    val entryRequests = GroupWaitingRowUi(
        title = "3 pedidos para entrar",
        meta = "Rafa, Júlia e mais 1",
        contentDescription = "3 pedidos para entrar. Rafa, Júlia e mais 1",
        count = 3,
    )

    val waiting = GroupWaitingUi(
        entryRequests = entryRequests,
        monthly = GroupWaitingRowUi(
            title = "2 mensalidades a receber",
            meta = "R$ 140,00 · AGO",
            contentDescription = "2 mensalidades a receber. R$ 140,00 · AGO",
            count = 2,
        ),
        settle = GroupSettleRowUi(
            gameId = "game-0",
            title = "Acertar o jogo de 21/07",
            meta = "4 avulsos · R$ 100,00 a receber",
            contentDescription = "Acertar o jogo de 21/07. 4 avulsos · R$ 100,00 a receber",
        ),
    )

    /** As quatro linhas, aviso parado. */
    // O gestor já respondeu: os 2 pendentes são os 2 que recebem o lembrete.
    val idle = GroupDetailsPreviewData.admin.copy(
        nextGame = nextGame,
        waiting = waiting,
        memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Confirmed),
    )

    /** O gestor também não respondeu: 3 pendentes no servidor, 2 recebem o lembrete. */
    val viewerPending = idle.copy(
        memberResponse = null,
        attendance = GroupDetailsPreviewData.attendance.copy(pending = 3),
    )

    val notifying = idle.copy(notifying = true)

    val notified = idle.copy(notifiedCount = "2")

    val failed = idle.copy(notificationFailed = true)

    /** Prazo vencido com o jogo por começar: o "Avisar" continua, sem linha de prazo (DEB-137). */
    val closed = idle.copy(nextGame = nextGame.copy(confirmationOpen = false))

    /** Jogo já começou: a linha de quórum some. */
    val started = idle.copy(nextGame = nextGame.copy(confirmationOpen = false, responseOpen = false))

    val refused = idle.copy(notificationRefused = true)

    /** Nenhuma pendência do V2: o card fica só com o quórum. */
    val quorumOnly = idle.copy(waiting = null)

    /** Finanças falharam: o V2 só entrega os pedidos de entrada (mock `GestorFinancasFalha`). */
    val financeFailed = idle.copy(waiting = GroupWaitingUi(entryRequests = entryRequests))
}
