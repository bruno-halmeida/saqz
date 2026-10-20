package br.com.saqz.groups.presentation.ui.gamedetail
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import br.com.saqz.designsystem.SaqzLargeFontScale
import br.com.saqz.designsystem.saqzColumnsFit
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.designsystem.resources.Res as DsRes
import br.com.saqz.designsystem.resources.action_back
import br.com.saqz.designsystem.resources.attendance_out
import br.com.saqz.groups.presentation.gamedetail.GameDetailAttendanceUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailConfirmedUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailHeaderUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.shortLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_detail_cancel
import br.com.saqz.groups.resources.game_detail_cancel_body
import br.com.saqz.groups.resources.game_detail_cancel_failed
import br.com.saqz.groups.resources.game_detail_cancel_title
import br.com.saqz.groups.resources.game_detail_publish
import br.com.saqz.groups.resources.game_detail_publish_after_plan_end
import br.com.saqz.groups.resources.game_detail_publish_failed
import br.com.saqz.groups.resources.game_detail_publish_hint
import br.com.saqz.groups.resources.game_detail_confirmation_deadline
import br.com.saqz.groups.resources.game_detail_confirmed_full
import br.com.saqz.groups.resources.game_detail_open_spots
import br.com.saqz.groups.resources.game_response_confirmed_summary
import br.com.saqz.groups.resources.game_detail_confirmed_over_capacity
import br.com.saqz.groups.resources.game_detail_confirmed_section
import br.com.saqz.groups.resources.game_detail_duration
import br.com.saqz.groups.resources.game_detail_duration_hours
import br.com.saqz.groups.resources.game_detail_duration_minutes
import br.com.saqz.groups.resources.game_detail_edit
import br.com.saqz.groups.resources.game_detail_spots
import br.com.saqz.groups.resources.game_detail_stat_going
import br.com.saqz.groups.resources.game_detail_status_cancelled
import br.com.saqz.groups.resources.game_detail_status_completed
import br.com.saqz.groups.resources.game_detail_status_draft
import br.com.saqz.groups.resources.game_detail_status_deadline_closed
import br.com.saqz.groups.resources.game_detail_status_in_progress
import br.com.saqz.groups.resources.game_detail_status_published
import br.com.saqz.groups.resources.game_detail_title
import br.com.saqz.groups.resources.game_load_access_denied
import br.com.saqz.groups.resources.game_load_failure_title
import br.com.saqz.groups.resources.game_load_not_found
import br.com.saqz.groups.resources.game_detail_you
import br.com.saqz.groups.resources.game_settlement_open
import br.com.saqz.groups.resources.game_detail_finance_review_action
import br.com.saqz.groups.resources.game_detail_finance_review_body
import br.com.saqz.groups.resources.group_details_pending
import br.com.saqz.groups.presentation.gamedetail.GamePositionUi
import br.com.saqz.groups.resources.position_forced_tag
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
internal object GameDetailTags {
    const val FinanceReview = "game-detail-finance-review"
    const val FinanceReviewAction = "game-detail-finance-review-action"
    const val Screen = "game-detail"
    const val CancelSheet = "game-detail-cancel-sheet"
    const val Publish = "game-detail-publish"
}
@Composable
internal fun GameDetailScreen(
    state: GameDetailState,
    onBack: () -> Unit,
    onIntent: (GameDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(GameDetailTags.Screen)) {
        Column(Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.game_detail_title), onBack = onBack)
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
                else -> Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                        horizontal = metrics.horizontalPadding,
                        vertical = metrics.blockGap,
                    ),
                    verticalArrangement = Arrangement.spacedBy(metrics.grid * 2),
                ) {
                    state.header?.let { GameDetailHeader(it) }
                    state.header?.let { GameDetailResponse(
                        header = it,
                        response = state.response,
                        onIntent = onIntent,
                        spotsOpen = (state.attendance?.availableSpots ?: 0) > 0,
                        promotionMode = state.promotionMode,
                    ) }
                    state.attendance?.let { GameDetailAttendance(it) }
                    GameGuestAction(
                        state.guest,
                        onIntent,
                        gameStarted = state.header?.gameStarted == true,
                    )
                    state.confirmedRoster.takeIf { it.isNotEmpty() || state.hasPositions }?.let {
                        GameDetailConfirmedList(it, state.positions, state.guest.feeLabel, onIntent)
                    }
                    if (state.canManageAttendance) GamePlaceAction(onIntent)
                    state.waitlist.takeIf { it.isNotEmpty() }?.let { GameWaitlistSection(state, onIntent) }
                    if (state.canReviewFinance) GameFinanceReviewNotice(onIntent)
                    if (state.isAdmin) {
                        // Com posições o total é a soma delas e não se ajusta por aqui (DEB-102).
                        if (state.header?.statusTone == GameDetailStatusTone.Published && state.header?.gameStarted != true &&
                            !state.hasPositions
                        ) {
                            GameWaitlistAdminActions(onIntent = onIntent)
                        }
                        GameDetailAdminActions(
                            cancelling = state.cancelling,
                            canCancel = state.header?.statusTone == GameDetailStatusTone.Published,
                            canSettle = state.header?.statusTone == GameDetailStatusTone.Completed,
                            canPublish = state.header?.statusTone == GameDetailStatusTone.Draft,
                            publishing = state.publishing,
                            publishFailed = state.publishFailed,
                            publishAfterPlanEnd = state.publishAfterPlanEnd,
                            onIntent = onIntent,
                        )
                    }
                }
            }
        }
        if (state.cancelDialogOpen) GameDetailCancelSheet(state, onIntent)
        if (state.capacitySheetOpen) GameWaitlistCapacitySheet(state, onIntent)
        if (state.guest.sheetOpen) GameGuestAddSheet(state.guest, onIntent, positions = state.positions)
        state.positionPicker?.let { GamePositionPickerSheet(it, onIntent) }
        state.guest.removal?.let { GameGuestRemoveSheet(state.guest, it, onIntent) }
        state.organizer.removal?.let { GameMemberRemoveSheet(state.organizer, it, state.guest.feeLabel, onIntent) }
        if (state.organizer.placeOpen) GamePlaceSheet(state, onIntent)
        GameGuestNotice(state.guest, onIntent, Modifier.align(Alignment.BottomCenter))
    }
}
@Composable
private fun GameFinanceReviewNotice(onIntent: (GameDetailIntent) -> Unit) = SaqzCard(
    modifier = Modifier.testTag(GameDetailTags.FinanceReview),
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
private fun GameDetailHeader(header: GameDetailHeaderUi) {
    val colors = SaqzTheme.colors
    SaqzCard {
        SaqzStatusChip(
            text = stringResource(header.statusResource()),
            tone = header.statusTone.chipTone(),
        )
        // Jogo em andamento: o prazo não faz sentido; o selo "Em andamento" e o cartão de resposta dizem o estado.
        val started = header.statusTone == GameDetailStatusTone.Published && !header.responseOpen
        if (!started) {
            Text(
                stringResource(Res.string.game_detail_confirmation_deadline, header.deadlineLabel()),
                color = colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
        }
        Text(
            header.weekday?.shortLabel()?.let { "$it, ${header.dateTime}" } ?: header.dateTime,
            color = colors.textPrimary,
            style = SaqzTheme.typography.title,
        )
        Text(header.venue, color = colors.textPrimary, style = SaqzTheme.typography.body)
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            Text(
                stringResource(Res.string.game_detail_duration, durationLabel(header.durationMinutes)),
                color = colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
            Text(
                pluralStringResource(Res.plurals.game_detail_spots, header.availableSpots, header.availableSpots),
                color = colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
        }
    }
}
/** "Qua, 17:30" quando o prazo cai noutro dia que o jogo; só a hora quando é no mesmo dia. */
@Composable
internal fun GameDetailHeaderUi.deadlineLabel(): String =
    confirmationDeadlineWeekday?.shortLabel()?.let { "$it, $confirmationDeadline" } ?: confirmationDeadline

@Composable
private fun GameDetailAttendance(attendance: GameDetailAttendanceUi) {
    val colors = SaqzTheme.colors
    SaqzCard {
        Text(
            when {
                // Vagas reduzidas abaixo do número de confirmados: "3 de 2" não faz sentido.
                attendance.confirmed > attendance.capacity -> pluralStringResource(
                    Res.plurals.game_detail_confirmed_over_capacity,
                    attendance.capacity,
                    attendance.confirmed,
                    attendance.capacity,
                )
                attendance.availableSpots <= 0 -> pluralStringResource(
                    Res.plurals.game_detail_confirmed_full,
                    attendance.capacity,
                    attendance.confirmed,
                    attendance.capacity,
                )
                else -> {
                    // Cada número concorda com o seu substantivo: "0 de 1 confirmado · 1 vaga aberta".
                    val confirmedOf = pluralStringResource(
                        Res.plurals.game_response_confirmed_summary,
                        attendance.capacity,
                        attendance.confirmed,
                        attendance.capacity,
                    )
                    val open = pluralStringResource(
                        Res.plurals.game_detail_open_spots,
                        attendance.availableSpots,
                        attendance.availableSpots,
                    )
                    "$confirmedOf · $open"
                }
            },
            color = colors.textPrimary,
            style = SaqzTheme.typography.body,
        )
        val goingLabel = stringResource(Res.string.game_detail_stat_going)
        val declinedLabel = stringResource(DsRes.string.attendance_out)
        val pendingLabel = stringResource(Res.string.group_details_pending)
        val fontScale = LocalDensity.current.fontScale
        // A largura disponível decide, não só o fontScale: no iPhone 17e "Sem resposta" quebrava em 3 colunas.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = fontScale >= SaqzLargeFontScale || !saqzColumnsFit(
                maxWidth,
                columns = 3,
                longestLabelChars = maxOf(goingLabel.length, declinedLabel.length, pendingLabel.length),
                fontScale = fontScale,
            )
            Column(
                modifier = Modifier.padding(vertical = SaqzTheme.metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            ) {
                if (stacked) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Counter(attendance.confirmed, goingLabel, colors.success, Modifier.weight(1f))
                        SaqzDivider(vertical = true)
                        Counter(attendance.declined, declinedLabel, colors.textSecondary, Modifier.weight(1f))
                    }
                    Counter(attendance.pending, pendingLabel, colors.textSecondary, Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Counter(attendance.confirmed, goingLabel, colors.success, Modifier.weight(1f))
                        SaqzDivider(vertical = true)
                        Counter(attendance.declined, declinedLabel, colors.textSecondary, Modifier.weight(1f))
                        SaqzDivider(vertical = true)
                        Counter(attendance.pending, pendingLabel, colors.textSecondary, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
@Composable
private fun Counter(value: Int, label: String, color: Color, modifier: Modifier = Modifier) = Column(
    modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
) {
    Text("$value", color = color, style = SaqzTheme.typography.title, textAlign = TextAlign.Center)
    Text(label, color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.caption, textAlign = TextAlign.Center)
}
@Composable
private fun GameDetailConfirmedList(
    confirmed: List<GameDetailConfirmedUi>,
    positions: List<GamePositionUi>,
    feeLabel: String?,
    onIntent: (GameDetailIntent) -> Unit,
) {
    SaqzCard(padded = false) {
        Box(Modifier.padding(SaqzTheme.metrics.horizontalPadding)) {
            SaqzSectionHeader(stringResource(Res.string.game_detail_confirmed_section))
        }
        SaqzDivider()
        if (positions.isEmpty()) {
            confirmed.forEachIndexed { index, person ->
                if (index > 0) SaqzDivider()
                GameConfirmedRow(person, feeLabel, onIntent)
            }
        } else {
            GameConfirmedByPosition(confirmed, positions, feeLabel, onIntent)
        }
    }
}

@Composable
internal fun GameConfirmedRow(person: GameDetailConfirmedUi, feeLabel: String?, onIntent: (GameDetailIntent) -> Unit) {
    val meta = person.guest?.metaLabel(feeLabel, confirmed = true) ?: stringResource(person.position.positionResource())
    SaqzMemberRow(
        name = if (person.isYou) {
            "${person.name} ${stringResource(Res.string.game_detail_you)}"
        } else {
            person.name
        },
        avatarName = person.name,
        modifier = person.guest?.let { Modifier.testTag(GameGuestTags.row(person.id)) } ?: Modifier,
        meta = if (person.forced) "$meta · ${stringResource(Res.string.position_forced_tag)}" else meta,
        photo = person.guest?.let { { GameGuestAvatar() } },
        trailing = when {
            person.guest != null -> { { GameGuestRemoveAction(person.id, person.guest, onIntent) } }
            person.canRemove -> { { GameMemberRemoveAction(person.id, onIntent) } }
            else -> null
        },
    )
}
@Composable
private fun GameDetailAdminActions(
    cancelling: Boolean,
    canCancel: Boolean,
    canSettle: Boolean,
    canPublish: Boolean,
    publishing: Boolean,
    publishFailed: Boolean,
    publishAfterPlanEnd: Boolean,
    onIntent: (GameDetailIntent) -> Unit,
) = Column(
    Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    if (canPublish) {
        SaqzButton(
            stringResource(Res.string.game_detail_publish),
            { onIntent(GameDetailIntent.Publish) },
            Modifier.testTag(GameDetailTags.Publish),
            fullWidth = true,
            enabled = !publishing,
            loading = publishing,
        )
        val blocked = publishFailed || publishAfterPlanEnd
        Text(
            stringResource(
                when {
                    publishAfterPlanEnd -> Res.string.game_detail_publish_after_plan_end
                    publishFailed -> Res.string.game_detail_publish_failed
                    else -> Res.string.game_detail_publish_hint
                },
            ),
            style = SaqzTheme.typography.support,
            color = if (blocked) SaqzTheme.colors.errorForeground else SaqzTheme.colors.textSecondary,
        )
    }
    SaqzButton(
        stringResource(Res.string.game_detail_edit),
        { onIntent(GameDetailIntent.Edit) },
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
    )
    if (canCancel) {
        SaqzButton(
            stringResource(Res.string.game_detail_cancel),
            { onIntent(GameDetailIntent.RequestCancel) },
            variant = SaqzButtonVariant.Danger,
            fullWidth = true,
            enabled = !cancelling,
            loading = cancelling,
        )
    }
    if (canSettle) {
        SaqzButton(
            stringResource(Res.string.game_settlement_open),
            { onIntent(GameDetailIntent.OpenSettlement) },
            fullWidth = true,
        )
    }
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
private fun GameDetailStatusTone.chipTone() = when (this) {
    GameDetailStatusTone.Draft -> SaqzChipTone.Neutral
    GameDetailStatusTone.Published -> SaqzChipTone.Brand
    GameDetailStatusTone.Cancelled -> SaqzChipTone.Error
    GameDetailStatusTone.Completed -> SaqzChipTone.Success
}
private fun GameDetailHeaderUi.statusResource() = when {
    statusTone == GameDetailStatusTone.Published && !responseOpen -> Res.string.game_detail_status_in_progress
    statusTone == GameDetailStatusTone.Published && !confirmationOpen -> Res.string.game_detail_status_deadline_closed
    else -> statusTone.resource()
}
private fun GameDetailStatusTone.resource() = when (this) {
    GameDetailStatusTone.Draft -> Res.string.game_detail_status_draft
    GameDetailStatusTone.Published -> Res.string.game_detail_status_published
    GameDetailStatusTone.Cancelled -> Res.string.game_detail_status_cancelled
    GameDetailStatusTone.Completed -> Res.string.game_detail_status_completed
}
@Composable
private fun durationLabel(minutes: Int) = when {
    minutes % 60 == 0 -> stringResource(Res.string.game_detail_duration_hours, minutes / 60)
    minutes < 60 -> stringResource(Res.string.game_detail_duration_minutes, minutes)
    else -> stringResource(Res.string.game_detail_duration_minutes, minutes)
}
internal object GameDetailPreviewData {
    val header = GameDetailHeaderUi(
        GameDetailStatusTone.Published,
        "17h30",
        GroupWeekday.TUESDAY,
        "28/07 · 19h30",
        "CERET — Quadra 2",
        120,
        12,
    )
    val attendance = GameDetailAttendanceUi(4, 12, 8, 1, 2)
    val confirmed = listOf(
        GameDetailConfirmedUi("1", "Lucas Prado", true, AthletePosition.LEVANTADOR),
        GameDetailConfirmedUi("2", "Bia Souza", false, AthletePosition.PONTA, canRemove = true),
        GameDetailConfirmedUi("3", "Thiago Melo", false, AthletePosition.CENTRAL, canRemove = true),
    )
    val waitlist = listOf(
        GameDetailWaitlistUi("wait-1", "Caio Lima", 1, AthletePosition.CENTRAL, true, canRemove = true),
        GameDetailWaitlistUi("wait-2", "Duda Alves", 2, AthletePosition.PONTA, false, canRemove = true),
    )
    val admin = GameDetailState(
        isLoading = false,
        header = header,
        attendance = attendance,
        confirmedRoster = confirmed,
        waitlist = waitlist,
        mensalistaPriority = true,
        promotionMode = PromotionMode.MANUAL,
        isAdmin = true,
    )

    /** DEB-102: jogo de 5 vagas por posição (ponta 2, central 2, levantador 1) com a ponta cheia e uma pessoa esperando nela. */
    val positionsGame = admin.copy(
        attendance = GameDetailAttendanceUi(4, 5, 1, 1, 2),
        positions = listOf(
            GamePositionUi(AthletePosition.PONTA, 2, 2, 0, 1),
            GamePositionUi(AthletePosition.CENTRAL, 2, 1, 1, 0),
            GamePositionUi(AthletePosition.LEVANTADOR, 1, 1, 0, 0),
        ),
        confirmedRoster = listOf(
            GameDetailConfirmedUi("1", "Lucas Prado", true, AthletePosition.LEVANTADOR),
            GameDetailConfirmedUi("2", "Bia Souza", false, AthletePosition.PONTA, canRemove = true),
            GameDetailConfirmedUi("4", "Rafa Dias", false, AthletePosition.PONTA, canRemove = true, forced = true),
            GameDetailConfirmedUi("3", "Thiago Melo", false, AthletePosition.CENTRAL, canRemove = true),
        ),
        waitlist = listOf(GameDetailWaitlistUi("wait-2", "Duda Alves", 1, AthletePosition.PONTA, false, canRemove = true)),
    )
}
@Preview
@Composable
private fun GameDetailPreview() = SaqzTheme { GameDetailScreen(GameDetailPreviewData.admin, {}, {}) }
