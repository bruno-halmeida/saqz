package br.com.saqz.groups.presentation.ui.gamedetail
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.shortLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_detail_cancel
import br.com.saqz.groups.resources.game_detail_cancel_body
import br.com.saqz.groups.resources.game_detail_cancel_failed
import br.com.saqz.groups.resources.game_detail_cancel_title
import br.com.saqz.groups.resources.game_detail_confirmation_deadline
import br.com.saqz.groups.resources.game_detail_confirmed_summary
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
import br.com.saqz.groups.resources.game_detail_status_published
import br.com.saqz.groups.resources.game_detail_title
import br.com.saqz.groups.resources.game_detail_you
import br.com.saqz.groups.resources.group_details_pending
import org.jetbrains.compose.resources.stringResource
internal object GameDetailTags {
    const val Screen = "game-detail"
}
@Composable
internal fun GameDetailScreen(
    state: GameDetailState,
    onBack: () -> Unit,
    onIntent: (GameDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier.fillMaxSize().testTag(GameDetailTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.game_detail_title), onBack = onBack)
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
            state.loadFailed -> GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(GameDetailIntent.Retry) },
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.blockGap,
                ),
                verticalArrangement = Arrangement.spacedBy(metrics.grid * 2),
            ) {
                state.header?.let { GameDetailHeader(it) }
                state.attendance?.let { GameDetailAttendance(it) }
                state.confirmedRoster.takeIf { it.isNotEmpty() }?.let { GameDetailConfirmedList(it) }
                if (state.isAdmin) GameDetailAdminActions(state.cancelling, onIntent)
            }
        }
        if (state.cancelDialogOpen) GameDetailCancelSheet(state, onIntent)
    }
}
@Composable
private fun GameDetailHeader(header: GameDetailHeaderUi) {
    val colors = SaqzTheme.colors
    SaqzCard {
        SaqzStatusChip(
            text = stringResource(header.statusTone.resource()),
            tone = header.statusTone.chipTone(),
        )
        Text(
            stringResource(Res.string.game_detail_confirmation_deadline, header.confirmationDeadline),
            color = colors.textSecondary,
            style = SaqzTheme.typography.support,
        )
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
                stringResource(Res.string.game_detail_spots, header.availableSpots),
                color = colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
        }
    }
}
@Composable
private fun GameDetailAttendance(attendance: GameDetailAttendanceUi) {
    val colors = SaqzTheme.colors
    SaqzCard {
        Text(
            stringResource(
                Res.string.game_detail_confirmed_summary,
                attendance.confirmed,
                attendance.capacity,
                attendance.availableSpots,
            ),
            color = colors.textPrimary,
            style = SaqzTheme.typography.body,
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = SaqzTheme.metrics.blockGap)) {
            Counter(
                attendance.confirmed,
                stringResource(Res.string.game_detail_stat_going),
                colors.success,
                Modifier.weight(1f),
            )
            listOfNotNull(
                attendance.out?.let { it to DsRes.string.attendance_out },
                attendance.pending?.let { it to Res.string.group_details_pending },
            ).forEach { (value, label) ->
                SaqzDivider(vertical = true)
                Counter(value, stringResource(label), colors.textSecondary, Modifier.weight(1f))
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
    Text("$value", color = color, style = SaqzTheme.typography.title)
    Text(label, color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.caption)
}
@Composable
private fun GameDetailConfirmedList(confirmed: List<GameDetailConfirmedUi>) {
    SaqzCard(padded = false) {
        Box(Modifier.padding(SaqzTheme.metrics.horizontalPadding)) {
            SaqzSectionHeader(stringResource(Res.string.game_detail_confirmed_section))
        }
        SaqzDivider()
        confirmed.forEachIndexed { index, person ->
            if (index > 0) SaqzDivider()
            SaqzMemberRow(
                name = if (person.isYou) {
                    "${person.name} ${stringResource(Res.string.game_detail_you)}"
                } else {
                    person.name
                },
                meta = person.position,
            )
        }
    }
}
@Composable
private fun GameDetailAdminActions(cancelling: Boolean, onIntent: (GameDetailIntent) -> Unit) = Column(
    Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzButton(
        stringResource(Res.string.game_detail_edit),
        { onIntent(GameDetailIntent.Edit) },
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
    )
    SaqzButton(
        stringResource(Res.string.game_detail_cancel),
        { onIntent(GameDetailIntent.RequestCancel) },
        variant = SaqzButtonVariant.Danger,
        fullWidth = true,
        enabled = !cancelling,
        loading = cancelling,
    )
}
@Composable
private fun GameDetailCancelSheet(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) = SaqzBottomSheet(
    open = true,
    onClose = { onIntent(GameDetailIntent.DismissCancel) },
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
    content = {},
)
private fun GameDetailStatusTone.chipTone() = when (this) {
    GameDetailStatusTone.Draft -> SaqzChipTone.Neutral
    GameDetailStatusTone.Published -> SaqzChipTone.Brand
    GameDetailStatusTone.Cancelled -> SaqzChipTone.Error
    GameDetailStatusTone.Completed -> SaqzChipTone.Success
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
    val attendance = GameDetailAttendanceUi(4, 12, 8)
    val confirmed = listOf(
        GameDetailConfirmedUi("1", "Lucas Prado", true, "Levantadora"),
        GameDetailConfirmedUi("2", "Bia Souza", false, "Ponteira"),
        GameDetailConfirmedUi("3", "Thiago Melo", false, "Central"),
    )
    val admin = GameDetailState(false, header = header, attendance = attendance, confirmedRoster = confirmed, isAdmin = true)
}
@Preview
@Composable
private fun GameDetailPreview() = SaqzTheme { GameDetailScreen(GameDetailPreviewData.admin, {}, {}) }
