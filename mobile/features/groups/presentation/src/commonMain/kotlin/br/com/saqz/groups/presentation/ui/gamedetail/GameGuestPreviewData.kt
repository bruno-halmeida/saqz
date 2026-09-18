package br.com.saqz.groups.presentation.ui.gamedetail

import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.gamedetail.GameDetailConfirmedUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestHint
import br.com.saqz.groups.presentation.gamedetail.GameGuestRemovalUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestRowUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestUi

internal object GameGuestPreviewData {
    private val base = GameDetailPreviewData.admin.copy(isAdmin = false)
    val guestUi = GameGuestUi(visible = true, enabled = true, feeLabel = "R$ 25,00")
    val mine = GameGuestRowUi(hostId = "1", guestSeq = 1, hostName = "Bia Souza", isYours = true, canRemove = true)
    val theirs = mine.copy(isYours = false, canRemove = false)

    val empty = base.copy(guest = guestUi)
    val sheetEmpty = empty.copy(guest = guestUi.copy(sheetOpen = true))
    val sheetFilled = empty.copy(guest = guestUi.copy(sheetOpen = true, name = "Rafa Moreira"))
    val sheetAdding = empty.copy(guest = guestUi.copy(sheetOpen = true, name = "Rafa Moreira", adding = true))
    val sheetFailed = empty.copy(guest = guestUi.copy(sheetOpen = true, name = "Rafa Moreira", addFailed = true))
    val inQueue = empty.copy(
        guest = guestUi.copy(noticeName = "Rafa Moreira", noticeJoined = true),
        waitlist = base.waitlist + GameDetailWaitlistUi("1#1", "Rafa Moreira", 3, null, false, guest = mine),
    )
    val twoGuests = empty.copy(
        waitlist = base.waitlist + listOf(
            GameDetailWaitlistUi("1#1", "Rafa Moreira", 3, null, false, guest = mine),
            GameDetailWaitlistUi("1#2", "Ju Andrade", 4, null, false, guest = mine.copy(guestSeq = 2)),
        ),
    )
    val promoted = empty.copy(
        confirmedRoster = base.confirmedRoster + GameDetailConfirmedUi("1#1", "Rafa Moreira", false, "", guest = mine),
    )
    val removeWaitlisted = inQueue.copy(
        guest = guestUi.copy(
            removal = GameGuestRemovalUi(rowId = "1#1", hostId = "1", guestSeq = 1, name = "Rafa Moreira", confirmed = false),
        ),
    )
    val removeConfirmed = promoted.copy(
        guest = guestUi.copy(
            removal = GameGuestRemovalUi(rowId = "1#1", hostId = "1", guestSeq = 1, name = "Rafa Moreira", confirmed = true),
        ),
    )
    val othersView = empty.copy(
        waitlist = base.waitlist + GameDetailWaitlistUi("1#1", "Rafa Moreira", 3, null, false, guest = theirs),
    )
    val closed = empty.copy(guest = guestUi.copy(enabled = false, hint = GameGuestHint.Closed))
    val needAnswer = empty.copy(guest = guestUi.copy(enabled = false, hint = GameGuestHint.NeedAnswer))
    val noFee = empty.copy(guest = guestUi.copy(feeLabel = null, sheetOpen = true))
    val organizerQueue = base.copy(
        isAdmin = true,
        promotionMode = PromotionMode.MANUAL,
        guest = guestUi,
        waitlist = base.waitlist + GameDetailWaitlistUi(
            "1#1",
            "Rafa Moreira",
            3,
            null,
            false,
            guest = theirs.copy(canRemove = true),
        ),
    )
}
