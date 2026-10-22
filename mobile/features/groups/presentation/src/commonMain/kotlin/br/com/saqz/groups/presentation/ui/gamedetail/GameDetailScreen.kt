package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.resources.Res as DsRes
import br.com.saqz.designsystem.resources.action_back
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.designsystem.SaqzPullToRefresh
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.gamedetail.GameDetailAttendanceUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailConfirmedUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailHeaderUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailHeroUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailTab
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestRowUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionUi
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.RefreshFailedToast
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_detail_cancel
import br.com.saqz.groups.resources.game_detail_cancel_body
import br.com.saqz.groups.resources.game_detail_cancel_failed
import br.com.saqz.groups.resources.game_detail_cancel_title
import br.com.saqz.groups.resources.game_detail_finance_review_action
import br.com.saqz.groups.resources.game_detail_finance_review_body
import br.com.saqz.groups.resources.game_detail_title
import br.com.saqz.groups.resources.game_load_access_denied
import br.com.saqz.groups.resources.game_load_failure_title
import br.com.saqz.groups.resources.game_load_not_found
import br.com.saqz.groups.resources.game_more_actions
import org.jetbrains.compose.resources.stringResource

internal object GameDetailTags {
    const val FinanceReview = "game-detail-finance-review"
    const val FinanceReviewAction = "game-detail-finance-review-action"
    const val Screen = "game-detail"
    const val CancelSheet = "game-detail-cancel-sheet"
    const val Publish = "game-detail-publish"
    const val Settle = "game-detail-settle"
    const val SettleView = "game-detail-settle-view"
    const val TabNoShow = "game-tab-no-show"
    const val NoShowFooter = "game-no-show-footer"
    const val NoShowFailed = "game-no-show-failed"
    fun noShowTag(id: String) = "game-no-show-tag-$id"
    fun noShowMark(id: String) = "game-no-show-mark-$id"
    fun noShowUndo(id: String) = "game-no-show-undo-$id"
    const val Hero = "game-detail-hero"
    const val Kicker = "game-detail-kicker"
    const val Map = "game-detail-map"
    const val MapFailure = "game-detail-map-failure"
    const val FeeNote = "game-detail-fee-note"
    const val LimeLine = "game-detail-lime-line"
    const val NotifyPanel = "game-detail-notify"
    const val NotifyPending = "game-detail-notify-pending"
    const val Menu = "game-detail-menu"
    const val MenuSheet = "game-detail-menu-sheet"
    const val MenuEdit = "game-detail-menu-edit"
    const val MenuCapacity = "game-detail-menu-capacity"
    const val MenuHistory = "game-detail-menu-history"
    const val MenuCancel = "game-detail-menu-cancel"
    const val PersonSheet = "game-detail-person-sheet"
    const val TabGoing = "game-tab-going"
    const val TabWaiting = "game-tab-waiting"
    const val TabDeclined = "game-tab-declined"
    const val List = "game-detail-list"
    const val OpenSlot = "game-detail-open-slot"
    const val MoreSlots = "game-detail-more-slots"
    const val WaitlistNote = "game-detail-waitlist-note"
    const val WaitlistEmpty = "game-detail-waitlist-empty"
    const val DeclinedEmpty = "game-detail-declined-empty"
    const val DeclinedCount = "game-detail-declined-count"

    fun row(rowId: String) = "game-detail-row-$rowId"

    fun more(rowId: String) = "game-detail-more-$rowId"

    fun openSlot(index: Int) = "$OpenSlot-$index"
}

/**
 * 4c — a tela do jogo em tela cheia (DEB-116): barra de cima como em todo o app, cabeçalho azul de borda a
 * borda com o placar que também é aba, e a lista branca da aba escolhida. A aba é estado de tela: abre em
 * Na espera para quem está na espera.
 */
@Composable
internal fun GameDetailScreen(
    state: GameDetailState,
    onBack: () -> Unit,
    onIntent: (GameDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(GameDetailTags.Screen)) {
        Column(Modifier.fillMaxSize()) {
            SaqzTopAppBar(
                title = state.groupName ?: stringResource(Res.string.game_detail_title),
                onBack = onBack,
                actions = {
                    if (state.hasGameMenu) {
                        SaqzIconButton(
                            onClick = { onIntent(GameDetailIntent.OpenGameMenu) },
                            contentDescription = stringResource(Res.string.game_more_actions),
                            modifier = Modifier.testTag(GameDetailTags.Menu),
                        ) {
                            SaqzIcon(SaqzIcons.MoreHorizontal, tint = SaqzTheme.colors.primary)
                        }
                    }
                },
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
                state.loadFailed -> GroupLoadFailure(
                    error = state.error,
                    onRetry = { onIntent(GameDetailIntent.Retry) },
                    modifier = Modifier.fillMaxSize(),
                    failureTitle = stringResource(Res.string.game_load_failure_title),
                    accessDeniedTitle = stringResource(Res.string.game_load_access_denied),
                    notFoundTitle = stringResource(Res.string.game_load_not_found),
                )
                else -> SaqzPullToRefresh(
                    refreshing = state.isRefreshing,
                    onRefresh = { onIntent(GameDetailIntent.PullRefresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    GameDetailContent(state, onIntent)
                }
            }
        }
        if (state.gameMenuOpen) GameMenuSheet(state, onIntent)
        state.personActionsId?.let { GamePersonActionsSheet(state, it, onIntent) }
        if (state.cancelDialogOpen) GameDetailCancelSheet(state, onIntent)
        if (state.capacitySheetOpen) GameWaitlistCapacitySheet(state, onIntent)
        if (state.guest.sheetOpen) GameGuestAddSheet(state.guest, onIntent, positions = state.positions)
        state.positionPicker?.let { GamePositionPickerSheet(it, onIntent) }
        state.guest.removal?.let { GameGuestRemoveSheet(state.guest, it, onIntent) }
        state.organizer.removal?.let { GameMemberRemoveSheet(state.organizer, it, state.guest.feeLabel, onIntent) }
        if (state.organizer.placeOpen) GamePlaceSheet(state, onIntent)
        GameGuestNotice(state.guest, onIntent, Modifier.align(Alignment.BottomCenter))
        RefreshFailedToast(
            visible = state.refreshFailed,
            onDismiss = { onIntent(GameDetailIntent.DismissRefreshFailed) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun GameDetailContent(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(state.initialTab) }
    val tabs = state.availableTabs()
    val selected = tab.takeIf { it in tabs } ?: GameDetailTab.Going
    Column(
        Modifier.fillMaxSize().background(SaqzTheme.colors.surface).verticalScroll(rememberScrollState()),
    ) {
        GameDetailHero(state = state, selectedTab = selected, tabs = tabs, onSelectTab = { tab = it }, onIntent = onIntent)
        if (state.canReviewFinance) {
            GameFinanceReviewNotice(
                onIntent = onIntent,
                modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
            )
        }
        // Rascunho não tem placar nem lista: ninguém respondeu a um jogo que ninguém vê.
        if (tabs.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().testTag(GameDetailTags.List)) {
                when (selected) {
                    GameDetailTab.Going -> GameGoingList(state, onIntent)
                    GameDetailTab.Waiting -> GameWaitingList(state, onIntent)
                    GameDetailTab.NoShow -> GameNoShowList(state, onIntent)
                    GameDetailTab.Declined -> GameDeclinedList(state)
                }
            }
        }
    }
}

/**
 * Realizado: "Jogaram" e "Não foram", e para o gestor "Faltaram" no meio quando alguém foi marcado (PRES-016);
 * cancelado: "Confirmados" e "Não iam"; rascunho: sem placar.
 */
internal fun GameDetailState.availableTabs(): List<GameDetailTab> = when (header?.statusTone) {
    GameDetailStatusTone.Draft, null -> emptyList()
    GameDetailStatusTone.Completed -> listOfNotNull(
        GameDetailTab.Going,
        GameDetailTab.NoShow.takeIf { noShowRoster.isNotEmpty() },
        GameDetailTab.Declined,
    )
    GameDetailStatusTone.Cancelled -> listOf(GameDetailTab.Going, GameDetailTab.Declined)
    GameDetailStatusTone.Published -> listOf(GameDetailTab.Going, GameDetailTab.Waiting, GameDetailTab.Declined)
}

@Composable
private fun GameFinanceReviewNotice(onIntent: (GameDetailIntent) -> Unit, modifier: Modifier = Modifier) = SaqzCard(
    modifier = modifier.testTag(GameDetailTags.FinanceReview),
    tone = SaqzCardTone.Soft,
) {
    Text(
        text = stringResource(Res.string.game_detail_finance_review_body),
        style = SaqzTheme.typography.body,
        color = SaqzTheme.colors.textPrimary,
    )
    SaqzButton(
        stringResource(Res.string.game_detail_finance_review_action),
        { onIntent(GameDetailIntent.OpenSettlement) },
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
        modifier = Modifier.testTag(GameDetailTags.FinanceReviewAction),
    )
}

@Composable
private fun GameDetailCancelSheet(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) = SaqzBottomSheet(
    open = true,
    onClose = { onIntent(GameDetailIntent.DismissCancel) },
    modifier = Modifier.testTag(GameDetailTags.CancelSheet),
    title = stringResource(Res.string.game_detail_cancel_title),
    description = stringResource(
        if (state.cancelFailed) Res.string.game_detail_cancel_failed else Res.string.game_detail_cancel_body,
    ),
    splitFooter = {
        SaqzButton(
            stringResource(DsRes.string.action_back),
            { onIntent(GameDetailIntent.DismissCancel) },
            Modifier.weight(1f),
            SaqzButtonVariant.Secondary,
            enabled = !state.cancelling,
        )
        SaqzButton(
            stringResource(Res.string.game_detail_cancel),
            { onIntent(GameDetailIntent.ConfirmCancel) },
            Modifier.weight(1f),
            SaqzButtonVariant.Danger,
            loading = state.cancelling,
            enabled = !state.cancelling,
        )
    },
)

internal object GameDetailPreviewData {
    val header = GameDetailHeaderUi(
        GameDetailStatusTone.Published,
        "17h30",
        GroupWeekday.TUESDAY,
        "28/07/2026 · 19h30",
        "CERET — Quadra 2",
        120,
        3,
    )
    val hero = GameDetailHeroUi(
        title = "Terça, 19h30",
        date = "28 de julho",
        endTime = "21h30",
        address = "R. Canuto Abreu, s/n · Tatuapé",
        menuTitle = "Jogo de terça, 28/07",
        deadlineLine = "Prioridade dos mensalistas até hoje às 17h30.",
    )
    val attendance = GameDetailAttendanceUi(9, 12, 3, 3, 4)
    val confirmed = listOf(
        GameDetailConfirmedUi("1", "Lucas Prado", false, AthletePosition.LEVANTADOR, isMensalista = true),
        GameDetailConfirmedUi("2", "Bia Souza", false, AthletePosition.PONTA, isMensalista = true),
        GameDetailConfirmedUi("3", "Thiago Melo", false, AthletePosition.CENTRAL),
        GameDetailConfirmedUi("4", "Carla Mendes", false, AthletePosition.OPOSTO),
        GameDetailConfirmedUi("5", "Felipe Nunes", false, AthletePosition.PONTA),
        GameDetailConfirmedUi("6", "Gabi Torres", false, AthletePosition.LIBERO),
        GameDetailConfirmedUi("7", "Igor Santos", false, AthletePosition.CENTRAL),
        GameDetailConfirmedUi(
            "2#1",
            "Júlia Rocha",
            false,
            null,
            guest = GameGuestRowUi("2", 1, "Bia Souza", false, false),
        ),
        GameDetailConfirmedUi("9", "Marcos Lima", false, AthletePosition.PONTA),
    )
    val waitlist = listOf(
        GameDetailWaitlistUi("wait-1", "Caio Lima", 1, AthletePosition.CENTRAL, false),
        GameDetailWaitlistUi("wait-2", "Duda Alves", 2, AthletePosition.PONTA, false),
    )
    val declined = listOf(
        GameDetailConfirmedUi("out-1", "Rafa Dias", false, AthletePosition.PONTA),
        GameDetailConfirmedUi("out-2", "Marina Costa", false, AthletePosition.CENTRAL),
        GameDetailConfirmedUi("out-3", "Pedro Henrique", false, AthletePosition.LEVANTADOR),
    )

    /** Atleta sem resposta, jogo sem posições, 3 vagas: a fileira "Main". */
    val athlete = GameDetailState(
        isLoading = false,
        groupName = "Vôlei do CERET",
        header = header,
        hero = hero,
        attendance = attendance,
        confirmedRoster = confirmed,
        waitlist = waitlist,
        declinedRoster = declined,
        response = GameDetailResponseUi(visible = true),
        mensalistaPriority = true,
    )

    val admin = athlete.copy(
        isAdmin = true,
        promotionMode = PromotionMode.MANUAL,
        confirmedRoster = confirmed.map { it.copy(canRemove = it.guest == null) },
        waitlist = waitlist.map { it.copy(canRemove = true) },
    )

    /** DEB-102: jogo de 6 vagas por posição (ponta 2, central 2, oposto 1, levantador 1), faltando 1 central. */
    val positionsGame = admin.copy(
        attendance = GameDetailAttendanceUi(5, 6, 1, 2, 4),
        header = header.copy(availableSpots = 1),
        positions = listOf(
            GamePositionUi(AthletePosition.PONTA, 2, 2, 0, 1),
            GamePositionUi(AthletePosition.CENTRAL, 2, 1, 1, 1),
            GamePositionUi(AthletePosition.OPOSTO, 1, 1, 0, 0),
            GamePositionUi(AthletePosition.LEVANTADOR, 1, 1, 0, 0),
        ),
        confirmedRoster = listOf(
            GameDetailConfirmedUi("2", "Bia Souza", false, AthletePosition.PONTA, canRemove = true, isMensalista = true),
            GameDetailConfirmedUi("5", "Felipe Nunes", false, AthletePosition.PONTA, canRemove = true),
            GameDetailConfirmedUi("3", "Thiago Melo", false, AthletePosition.CENTRAL, canRemove = true, isMensalista = true),
            GameDetailConfirmedUi("4", "Carla Mendes", false, AthletePosition.OPOSTO, canRemove = true),
            GameDetailConfirmedUi("1", "Lucas Prado", true, AthletePosition.LEVANTADOR, isMensalista = true),
        ),
        waitlist = listOf(
            GameDetailWaitlistUi("wait-2", "Duda Alves", 1, AthletePosition.PONTA, false, canRemove = true),
            GameDetailWaitlistUi("wait-1", "Caio Lima", 1, AthletePosition.CENTRAL, false, canRemove = true),
        ),
        response = GameDetailResponseUi(
            visible = true,
            status = AttendanceStatus.Confirmed,
        ),
    )
}

@Preview
@Composable
private fun GameDetailPreview() = SaqzTheme { GameDetailScreen(GameDetailPreviewData.athlete, {}, {}) }

@Preview
@Composable
private fun GameDetailOrganizerPreview() = SaqzTheme { GameDetailScreen(GameDetailPreviewData.positionsGame, {}, {}) }
