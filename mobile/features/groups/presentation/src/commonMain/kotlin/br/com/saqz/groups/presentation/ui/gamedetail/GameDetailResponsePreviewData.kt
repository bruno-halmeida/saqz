package br.com.saqz.groups.presentation.ui.gamedetail

import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameGuestHint
import br.com.saqz.groups.presentation.home.HomeWaitlistKind

/**
 * Quem olha é um atleta do grupo, num jogo publicado que NÃO é o próximo: o caso que a tela do jogo
 * resolve. Só o que o `GameDetailViewModel` produz — a resposta e o "Levar convidado" andam juntos:
 * ele só habilita para quem disse "Vou" (confirmado ou na espera).
 */
internal object GameDetailResponsePreviewData {
    val pending = GameDetailResponseUi(visible = true)
    val confirmed = pending.copy(status = AttendanceStatus.Confirmed)
    val declined = pending.copy(status = AttendanceStatus.Declined)
    val reserva = pending.copy(
        status = AttendanceStatus.Waitlisted,
        waitlistKind = HomeWaitlistKind.Reserva,
        waitlistPosition = 3,
    )
    val avulso = reserva.copy(waitlistKind = HomeWaitlistKind.AvulsoList)

    private val base = GameDetailPreviewData.admin.copy(
        isAdmin = false,
        guest = GameGuestPreviewData.guestUi.copy(enabled = false, hint = GameGuestHint.NeedAnswer),
    )
    private val going = base.copy(guest = GameGuestPreviewData.guestUi)

    val memberPending = base.copy(response = pending)
    val memberConfirmed = going.copy(response = confirmed)
    val memberDeclined = base.copy(response = declined)
    val memberReserva = going.copy(response = reserva)
    val memberAvulso = going.copy(response = avulso)
    val memberResponding = base.copy(response = pending.copy(status = AttendanceStatus.Confirmed, responding = true))
    val memberFailed = base.copy(response = pending.copy(failed = true))
    val memberClosed: GameDetailState = base.copy(
        header = GameDetailPreviewData.header.copy(confirmationOpen = false),
        guest = GameGuestPreviewData.guestUi.copy(enabled = false, hint = GameGuestHint.Closed),
        response = pending,
    )
    val memberClosedConfirmed: GameDetailState = memberClosed.copy(response = confirmed)
    val admin = GameDetailPreviewData.admin.copy(response = pending)
}
