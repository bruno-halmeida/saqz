package br.com.saqz.groups.presentation.ui.details

/**
 * Todas as tags do detalhe do grupo. Várias são CONTRATO do e2e Android
 * (`mobile/android-app/src/e2e`): `group-details`, `-leave`, `-shortcut-chat`,
 * `-shortcut-notices`, `-notify-pending`, `-cashbox`, `-manage-members`,
 * `-view-all-members`, `-own-charge-<id>`, `-own-charges-pix` e as duas
 * `group-game-response-going` / `-not-going`. Renomear qualquer uma quebra um cenário que
 * não roda no gate do PR — e o harness exige EXATAMENTE um nó por tag na árvore.
 */
internal object GroupDetailsTags {
    const val Screen = "group-details"
    const val Content = "group-details-content"
    const val Skeleton = "group-details-skeleton"
    const val Toast = "group-details-toast"

    // topo
    const val EditGroup = "group-details-edit-group"
    const val PhotoFailed = "group-details-photo-failed"

    // hero
    const val Hero = "group-details-hero"
    const val HeroMap = "group-details-hero-map"
    const val HeroMapFailure = "group-details-hero-map-failure"
    const val HeroRosterStale = "group-details-hero-roster-stale"
    const val HeroRosterRetry = "group-details-hero-roster-retry"
    const val HeroFeeNote = "group-details-hero-fee-note"
    const val CreateNextGame = "group-details-create-next-game"
    const val HeroInvite = "group-details-hero-invite"
    const val ViewGame = "group-details-view-game"
    const val ConfirmAttendance = "group-details-confirm-attendance"
    const val Venue = "group-details-venue"

    // minhas cobranças
    const val OwnCharges = "group-details-own-charges"
    const val OwnDebt = "group-details-own-debt"
    const val OwnChargesPending = "group-details-own-charges-pending"
    const val OwnChargesHistory = "group-details-own-charges-history"
    const val OwnChargesHistoryToggle = "group-details-own-charges-history-toggle"
    const val OwnChargesSettled = "group-details-own-charges-settled"
    const val OwnChargesPix = "group-details-own-charges-pix"
    const val OwnChargesPixCopy = "group-details-own-charges-pix-copy"
    const val OwnChargesSkeleton = "group-details-own-charges-skeleton"
    const val OwnChargesFailure = "group-details-own-charges-failure"
    const val OwnChargesRetry = "group-details-own-charges-retry"

    // esperando você
    const val Waiting = "group-details-waiting"
    const val WaitingQuorum = "group-details-waiting-quorum"
    const val NotifyPending = "group-details-notify-pending"
    const val NotifyFeedback = "group-details-notify-feedback"
    const val WaitingEntryRequests = "group-details-waiting-entry-requests"
    const val WaitingMonthly = "group-details-waiting-monthly"
    const val WaitingSettle = "group-details-waiting-settle"

    // agenda
    const val Agenda = "group-details-agenda"
    const val AgendaCreate = "group-details-agenda-create"
    const val AgendaMore = "group-details-agenda-more"

    // mural
    const val Mural = "group-details-mural"
    const val ShortcutNotices = "group-details-shortcut-notices"
    const val ShortcutCashbox = "group-details-shortcut-cashbox"
    const val ShortcutSchedule = "group-details-shortcut-schedule"
    const val ShortcutChat = "group-details-shortcut-chat"
    const val Notice = "group-details-notice"

    // pessoas e gestão
    const val People = "group-details-people"
    const val ViewAllMembers = "group-details-view-all-members"
    const val Invite = "group-details-invite"
    const val Manage = "group-details-manage"
    const val Cashbox = "group-details-cashbox"
    const val ManageMembers = "group-details-manage-members"
    const val ManageSchedule = "group-details-manage-schedule"
    const val ManageInviteLink = "group-details-manage-invite-link"
    const val HomeCourt = "group-details-home-court"
    const val HomeCourtMap = "group-details-home-court-map"
    const val Leave = "group-details-leave"

    fun ownCharge(chargeId: String) = "group-details-own-charge-$chargeId"

    fun agendaGame(gameId: String) = "group-details-agenda-game-$gameId"
}

internal object GroupGameResponseTags {
    const val Section = "group-game-response-section"
    const val Going = "group-game-response-going"
    const val NotGoing = "group-game-response-not-going"
    const val Change = "group-game-response-change"
    const val Cancel = "group-game-response-cancel"
    const val Error = "group-game-response-error"
    const val AutoConfirmation = "group-game-response-auto-confirmation"
}
