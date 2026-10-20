package br.com.saqz.groups.presentation.attendancelink

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzProgressBar
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// O círculo do ícone usa o mesmo tom do `SaqzStatusChip`, só com o alfa um pouco mais baixo.
private const val ResultIconTintAlpha = 0.14f
private const val NeutralIconAlpha = 0.5f
private val StepBadgeSize = 28.dp
private val StepBadgeBorder = 2.dp
private val StepBadgeIcon = 16.dp

/**
 * O cartão de resultado da jornada do link: círculo de ícone, título, texto e, quando a fase pede, a barra de
 * vagas ou a vez na fila. Nasce aqui e não no design system: só esta jornada o usa.
 */
@Composable
internal fun AttendanceResultCard(state: AttendanceLinkState, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val game = state.game
    val (icon, tint, background) = when (state.phase) {
        AttendanceLinkPhase.Confirmed -> Triple(SaqzIcons.Check, colors.success, colors.success.copy(alpha = ResultIconTintAlpha))
        AttendanceLinkPhase.Waitlisted ->
            Triple(SaqzIcons.Clock, colors.warningForeground, colors.warning.copy(alpha = ResultIconTintAlpha))
        else -> Triple(SaqzIcons.Close, colors.textSecondary, colors.border.copy(alpha = NeutralIconAlpha))
    }
    val title = stringResource(
        when (state.phase) {
            AttendanceLinkPhase.Confirmed -> Res.string.attendance_link_confirmed_title
            AttendanceLinkPhase.Waitlisted -> Res.string.attendance_link_waitlisted_title
            AttendanceLinkPhase.RequestClosed -> Res.string.attendance_link_closed_title
            else -> Res.string.attendance_link_declined_title
        },
    )
    val body = when (state.phase) {
        AttendanceLinkPhase.Confirmed -> stringResource(Res.string.attendance_link_confirmed_body)
        AttendanceLinkPhase.RequestClosed -> stringResource(Res.string.attendance_link_closed_body)
        AttendanceLinkPhase.Waitlisted -> stringResource(Res.string.attendance_link_waitlisted_body)
        else -> stringResource(Res.string.attendance_link_declined_body)
    }
    SaqzCard(modifier = modifier.testTag(AttendanceLinkTags.ResultCard)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid * 2)) {
            Box(
                Modifier.size(SaqzTheme.metrics.grid * 6).clip(CircleShape).background(background),
                contentAlignment = Alignment.Center,
            ) { SaqzIcon(icon, tint = tint) }
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(title, style = SaqzTheme.typography.title, color = colors.textPrimary)
                Text(body, style = SaqzTheme.typography.body, color = colors.textSecondary)
            }
            val capacity = game?.capacity
            val confirmed = game?.confirmedCount
            if (state.phase == AttendanceLinkPhase.Confirmed && capacity != null && confirmed != null && capacity > 0) {
                SpotsMeter(confirmed, capacity)
            }
            val position = state.waitlistPosition
            if (state.phase == AttendanceLinkPhase.Waitlisted && position != null) WaitlistTurn(position)
        }
    }
}

@Composable
private fun SpotsMeter(confirmed: Int, capacity: Int) {
    Column(
        Modifier.fillMaxWidth().testTag(AttendanceLinkTags.Spots),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(Res.string.attendance_link_spots_label),
                style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary,
            )
            Text(
                stringResource(Res.string.attendance_link_spots_value, confirmed, capacity),
                style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary,
            )
        }
        SaqzProgressBar(value = confirmed.toFloat() / capacity)
    }
}

@Composable
private fun WaitlistTurn(position: Long) {
    val ahead = (position - 1).toInt().coerceAtLeast(0)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(AttendanceLinkTags.WaitlistTurn)
            .clip(RoundedCornerShape(SaqzTheme.metrics.inputRadius))
            .background(SaqzTheme.colors.background)
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.attendance_link_waitlist_position, position),
            style = SaqzTheme.typography.display, color = SaqzTheme.colors.textPrimary,
        )
        Column {
            Text(
                stringResource(Res.string.attendance_link_waitlist_turn),
                style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary,
            )
            Text(
                if (ahead == 0) stringResource(Res.string.attendance_link_waitlist_nobody_ahead)
                else pluralStringResource(Res.plurals.attendance_link_waitlist_ahead, ahead, ahead),
                style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

/** Fase `Pending`: onde o pedido está, em quatro passos. */
@Composable
internal fun AttendancePendingCard(state: AttendanceLinkState, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val groupName = state.pendingGroupName
    SaqzCard(modifier = modifier.testTag(AttendanceLinkTags.PendingCard)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid * 2)) {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(
                    stringResource(Res.string.attendance_link_pending_heading),
                    style = SaqzTheme.typography.subtitle, color = colors.textPrimary,
                )
                Text(
                    if (groupName == null) stringResource(Res.string.attendance_link_pending_body_no_group)
                    else stringResource(Res.string.attendance_link_pending_body, groupName),
                    style = SaqzTheme.typography.body, color = colors.textSecondary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap + SaqzTheme.metrics.subGrid / 2)) {
                PendingStep(StepBadge.Done, stringResource(Res.string.attendance_link_step_sent))
                PendingStep(
                    StepBadge.Active,
                    stringResource(Res.string.attendance_link_step_approval),
                    subtitle = state.pendingInviterName?.let { stringResource(Res.string.attendance_link_step_approval_inviter, it) },
                    chip = stringResource(Res.string.attendance_link_step_approval_chip),
                )
                if (state.pendingRegistrationSaved) {
                    PendingStep(
                        StepBadge.Done,
                        stringResource(Res.string.attendance_link_step_registration),
                        subtitle = stringResource(Res.string.attendance_link_step_registration_saved),
                        modifier = Modifier.testTag(AttendanceLinkTags.RegistrationDone),
                    )
                } else {
                    PendingStep(
                        StepBadge.Number(3, colors.primary),
                        stringResource(Res.string.attendance_link_step_registration),
                        subtitle = stringResource(Res.string.attendance_link_step_registration_todo),
                    )
                }
                PendingStep(
                    StepBadge.Number(4, colors.textSecondary),
                    state.game?.weekdayName()
                        ?.let { stringResource(Res.string.attendance_link_step_presence_day, it) }
                        ?: stringResource(Res.string.attendance_link_step_presence),
                    subtitle = stringResource(Res.string.attendance_link_step_presence_hint),
                )
            }
        }
    }
}

private sealed interface StepBadge {
    data object Done : StepBadge
    data object Active : StepBadge
    data class Number(val value: Int, val color: Color) : StepBadge
}

@Composable
private fun PendingStep(
    badge: StepBadge,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chip: String? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        StepBadgeView(badge)
        Column(
            Modifier.weight(1f).padding(top = SaqzTheme.metrics.subGrid),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid / 2),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
                if (chip != null) SaqzStatusChip(chip, tone = SaqzChipTone.Warning)
            }
            if (subtitle != null) Text(subtitle, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun StepBadgeView(badge: StepBadge) {
    val colors = SaqzTheme.colors
    when (badge) {
        StepBadge.Done -> RoundBadge(SaqzIcons.Check, colors.success, colors.success.copy(alpha = ResultIconTintAlpha))
        StepBadge.Active -> RoundBadge(SaqzIcons.Clock, colors.warningForeground, colors.warning.copy(alpha = ResultIconTintAlpha))
        is StepBadge.Number -> Box(
            Modifier.size(StepBadgeSize).border(BorderStroke(StepBadgeBorder, colors.border), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("${badge.value}", style = SaqzTheme.typography.caption, color = badge.color) }
    }
}

@Composable
private fun RoundBadge(icon: ImageVector, tint: Color, background: Color, size: Dp = StepBadgeSize) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        SaqzIcon(icon, tint = tint, size = StepBadgeIcon)
    }
}

/** "quinta, 21h00"; só o dia da semana quando a hora não veio, só a data quando nem o dia veio. */
@Composable
internal fun AttendanceLinkGame.weekdayAndTime(): String {
    val day = weekdayName() ?: date
    return if (time.isBlank()) day else "$day, $time"
}

/** "quinta", "sábado"…; vazio quando o cartão não traz o dia da semana. */
@Composable
internal fun AttendanceLinkGame.weekdayName(): String? = when (weekdayCode) {
    "SUNDAY" -> Res.string.home_weekday_sunday
    "MONDAY" -> Res.string.home_weekday_monday
    "TUESDAY" -> Res.string.home_weekday_tuesday
    "WEDNESDAY" -> Res.string.home_weekday_wednesday
    "THURSDAY" -> Res.string.home_weekday_thursday
    "FRIDAY" -> Res.string.home_weekday_friday
    "SATURDAY" -> Res.string.home_weekday_saturday
    else -> null
}?.let { stringResource(it) }
