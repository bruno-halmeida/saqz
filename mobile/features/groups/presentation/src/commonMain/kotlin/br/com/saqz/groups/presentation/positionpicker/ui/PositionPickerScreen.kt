package br.com.saqz.groups.presentation.positionpicker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSegmentedColumn
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.positionpicker.PositionOptionUi
import br.com.saqz.groups.presentation.positionpicker.PositionPickerAnswerUi
import br.com.saqz.groups.presentation.positionpicker.PositionPickerGameUi
import br.com.saqz.groups.presentation.positionpicker.PositionPickerIntent
import br.com.saqz.groups.presentation.positionpicker.PositionPickerState
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.components.DateTile
import br.com.saqz.groups.presentation.ui.durationLabel
import br.com.saqz.groups.presentation.ui.singularLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.attendance_link_open_game
import br.com.saqz.groups.resources.attendance_link_waitlisted_title
import br.com.saqz.groups.resources.game_detail_duration
import br.com.saqz.groups.resources.game_response_confirmed
import br.com.saqz.groups.resources.position_picker_already_going
import br.com.saqz.groups.resources.position_picker_already_waiting
import br.com.saqz.groups.resources.game_response_request_failed
import br.com.saqz.groups.resources.group_system_retry
import br.com.saqz.groups.resources.position_picker_all_full_body
import br.com.saqz.groups.resources.position_picker_all_full_queue
import br.com.saqz.groups.resources.position_picker_all_full_title
import br.com.saqz.groups.resources.position_picker_count
import br.com.saqz.groups.resources.position_picker_full
import br.com.saqz.groups.resources.position_picker_full_body
import br.com.saqz.groups.resources.position_picker_full_title
import br.com.saqz.groups.resources.position_picker_join_waitlist
import br.com.saqz.groups.resources.position_picker_open
import br.com.saqz.groups.resources.position_picker_open_many
import br.com.saqz.groups.resources.position_picker_open_one
import br.com.saqz.groups.resources.position_picker_pick
import br.com.saqz.groups.resources.position_picker_raced_body
import br.com.saqz.groups.resources.position_picker_raced_title
import br.com.saqz.groups.resources.position_picker_screen_hint
import br.com.saqz.groups.resources.position_picker_screen_title
import br.com.saqz.groups.resources.position_picker_sending
import br.com.saqz.groups.resources.position_picker_submit
import br.com.saqz.groups.resources.position_picker_suggested
import br.com.saqz.groups.resources.position_picker_title
import br.com.saqz.groups.resources.position_picker_waiting
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

object PositionPickerTags {
    const val Screen = "position-picker"
    const val GameCard = "position-picker-game"
    const val Options = "position-picker-options"
    const val TopNotice = "position-picker-top-notice"
    const val Notice = "position-picker-notice"
    const val Submit = "position-picker-submit"
    const val LoadFailure = "position-picker-load-failure"
    const val Answered = "position-picker-answered"
    const val ViewGame = "position-picker-view-game"

    fun option(position: AthletePosition) = "position-picker-option-${position.name}"
}

/**
 * Quadro `Main` do desenho aprovado em 20/10: o jogo em cima, a pergunta com as posições na escolha única em coluna do
 * design system (a escolhida azul cheia, sem rádio) e a barra fixa com o aviso e o botão. A lista rola por cima da
 * barra, nunca por baixo dela.
 */
@Composable
fun PositionPickerScreen(
    state: PositionPickerState,
    onBack: () -> Unit,
    onIntent: (PositionPickerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(modifier.fillMaxSize().background(colors.background).testTag(PositionPickerTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.position_picker_screen_title), onBack = onBack)
        when {
            state.loadError != null -> GroupLoadFailure(
                error = state.loadError,
                onRetry = { onIntent(PositionPickerIntent.Retry) },
                modifier = Modifier.fillMaxWidth().padding(metrics.horizontalPadding),
                testTag = PositionPickerTags.LoadFailure,
            )
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { SaqzSpinner() }
            else -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = metrics.horizontalPadding)
                        .padding(top = metrics.grid, bottom = metrics.horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
                ) {
                    state.game?.let { GameCard(it) }
                    state.answered?.let { AnsweredCard(it) }
                    // Sem posição com vaga na leitura, a tela confirma como jogo comum: não há o que escolher.
                    if (!state.withoutPositions && state.answered == null) PositionQuestion(state, onIntent)
                }
                if (state.answered != null) AnsweredBar(onIntent) else PositionPickerBar(state, onIntent)
            }
        }
    }
}

@Composable
private fun GameCard(game: PositionPickerGameUi) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzCard(padded = false, modifier = Modifier.testTag(PositionPickerTags.GameCard)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            DateTile(day = game.day, month = game.month)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                Text(
                    game.title,
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(700)),
                    color = colors.textPrimary,
                )
                Text(
                    stringResource(Res.string.game_detail_duration, durationLabel(game.durationMinutes)),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
            }
        }
        SaqzDivider()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            if (game.venue.isNotBlank()) IconLine(SaqzIcons.Pin, game.venue)
            if (game.groupName.isNotBlank()) IconLine(SaqzIcons.Users, game.groupName)
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        SaqzIcon(icon, tint = SaqzTheme.colors.textSecondary, size = IconSize)
        Text(text, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary)
    }
}

@Composable
private fun PositionQuestion(state: PositionPickerState, onIntent: (PositionPickerIntent) -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val options = state.options
    SaqzSegmentedColumn(
        count = options.size,
        selected = options.indexOfFirst { it.position == state.selected },
        onSelect = { onIntent(PositionPickerIntent.Select(options[it].position)) },
        enabled = !state.sending,
        optionTags = options.map { PositionPickerTags.option(it.position) },
        modifier = Modifier.testTag(PositionPickerTags.Options),
        header = {
            Column(
                Modifier.fillMaxWidth().padding(start = metrics.blockGap, end = metrics.blockGap, top = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                Text(
                    stringResource(Res.string.position_picker_title),
                    style = SaqzTheme.typography.title,
                    color = colors.textPrimary,
                )
                Text(
                    stringResource(Res.string.position_picker_screen_hint),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
                if (state.allFull) {
                    val chosen = state.selectedOption
                    Notice(
                        title = stringResource(Res.string.position_picker_all_full_title),
                        body = stringResource(Res.string.position_picker_all_full_body),
                        extra = chosen?.let {
                            stringResource(Res.string.position_picker_all_full_queue, it.position.singularLabel(), it.queue)
                        },
                        color = colors.warningForeground,
                        modifier = Modifier.padding(top = metrics.grid).testTag(PositionPickerTags.TopNotice),
                    )
                }
            }
        },
    ) { index, selected -> PositionOption(options[index], selected) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionOption(option: PositionOptionUi, selected: Boolean) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val text = if (selected) colors.onPrimary else colors.textPrimary
    val meta = if (selected) colors.onPrimary.copy(alpha = MutedAlpha) else colors.textSecondary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
            // Fonte grande: o selo desce para baixo do nome em vez de espremer a linha.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(metrics.grid),
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option.position.singularLabel(),
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(600)),
                    color = text,
                )
                if (option.suggested) {
                    SaqzStatusChip(
                        stringResource(Res.string.position_picker_suggested),
                        tone = if (selected) SaqzChipTone.Inverse else SaqzChipTone.Brand,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
                SlotDots(option, selected)
                Text(
                    stringResource(Res.string.position_picker_count, option.confirmed, option.slots),
                    style = SaqzTheme.typography.support,
                    color = meta,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            if (option.full) {
                SaqzStatusChip(
                    stringResource(Res.string.position_picker_full),
                    tone = if (selected) SaqzChipTone.WarningInverse else SaqzChipTone.Warning,
                )
            } else {
                Text(
                    pluralStringResource(Res.plurals.position_picker_open, option.open, option.open),
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight(600)),
                    color = if (selected) colors.onPrimary else colors.primary,
                )
            }
            if (option.waitlisted > 0) {
                Text(
                    stringResource(Res.string.position_picker_waiting, option.waitlisted),
                    style = SaqzTheme.typography.caption,
                    color = meta,
                )
            }
        }
    }
}

/** Uma bolinha por vaga; cheia = confirmada. Decorativa: o "3 de 4" ao lado diz o mesmo em texto. */
@Composable
private fun SlotDots(option: PositionOptionUi, selected: Boolean) {
    val colors = SaqzTheme.colors
    val taken = if (selected) colors.accent else colors.primary
    val empty = if (selected) colors.onPrimary.copy(alpha = MutedAlpha) else colors.textSecondary
    Row(horizontalArrangement = Arrangement.spacedBy(DotGap)) {
        repeat(option.slots) { index ->
            val filled = index < option.confirmed
            Box(
                Modifier.size(DotSize)
                    .then(if (filled) Modifier.background(taken, CircleShape) else Modifier.border(DotStroke, empty, CircleShape)),
            )
        }
    }
}

/** Quem já respondeu: "Você já vai como Central" ou "Você está na lista de espera de Central, em 2º". */
@Composable
private fun AnsweredCard(answered: PositionPickerAnswerUi) {
    val position = answered.position?.singularLabel()
    val turn = answered.waitlistPosition?.toInt()
    val text = when {
        answered.status == AttendanceStatus.Waitlisted && position != null && turn != null ->
            stringResource(Res.string.position_picker_already_waiting, position, turn)
        answered.status == AttendanceStatus.Waitlisted -> stringResource(Res.string.attendance_link_waitlisted_title)
        position != null -> stringResource(Res.string.position_picker_already_going, position)
        else -> stringResource(Res.string.game_response_confirmed)
    }
    SaqzCard(modifier = Modifier.testTag(PositionPickerTags.Answered)) {
        Text(text, style = SaqzTheme.typography.subtitle, color = SaqzTheme.colors.textPrimary)
    }
}

@Composable
private fun AnsweredBar(onIntent: (PositionPickerIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().background(SaqzTheme.colors.chrome)) {
        SaqzDivider()
        SaqzButton(
            label = stringResource(Res.string.attendance_link_open_game),
            onClick = { onIntent(PositionPickerIntent.ViewGame) },
            fullWidth = true,
            modifier = Modifier
                .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap)
                .testTag(PositionPickerTags.ViewGame),
        )
    }
}

@Composable
private fun PositionPickerBar(state: PositionPickerState, onIntent: (PositionPickerIntent) -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val chosen = state.selectedOption
    Column(Modifier.fillMaxWidth().background(colors.chrome)) {
        SaqzDivider()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            BarNotice(state, chosen)
            val label = when {
                state.sending -> ""
                state.submitFailed -> stringResource(Res.string.group_system_retry)
                state.withoutPositions -> stringResource(Res.string.position_picker_submit)
                chosen == null -> stringResource(Res.string.position_picker_pick)
                chosen.full -> stringResource(Res.string.position_picker_join_waitlist)
                else -> stringResource(Res.string.position_picker_submit)
            }
            val sendingLabel = stringResource(Res.string.position_picker_sending)
            SaqzButton(
                label = label,
                onClick = { onIntent(PositionPickerIntent.Submit) },
                fullWidth = true,
                enabled = chosen != null || state.withoutPositions,
                loading = state.sending,
                modifier = Modifier.testTag(PositionPickerTags.Submit)
                    .then(if (state.sending) Modifier.semantics { contentDescription = sendingLabel } else Modifier),
            )
        }
    }
}

@Composable
private fun BarNotice(state: PositionPickerState, chosen: PositionOptionUi?) {
    val colors = SaqzTheme.colors
    val openLabels = state.openPositions.map { it.singularLabel() }
    val openText = when (openLabels.size) {
        0 -> null
        1 -> stringResource(Res.string.position_picker_open_one, openLabels.single())
        else -> stringResource(
            Res.string.position_picker_open_many,
            openLabels.dropLast(1).joinToString(", "),
            openLabels.last(),
        )
    }
    val modifier = Modifier.testTag(PositionPickerTags.Notice)
    when {
        state.submitFailed -> Notice(
            title = stringResource(Res.string.game_response_request_failed),
            body = null,
            extra = null,
            color = colors.errorForeground,
            modifier = modifier,
        )
        chosen != null && state.raced == chosen.position -> Notice(
            title = stringResource(Res.string.position_picker_raced_title, chosen.position.singularLabel()),
            body = stringResource(Res.string.position_picker_raced_body, chosen.queue),
            extra = openText,
            color = colors.errorForeground,
            modifier = modifier,
        )
        // Todas cheias: o aviso fica no topo, junto da pergunta.
        chosen != null && chosen.full && !state.allFull -> Notice(
            title = stringResource(Res.string.position_picker_full_title, chosen.position.singularLabel()),
            body = stringResource(Res.string.position_picker_full_body, chosen.queue),
            extra = openText,
            color = colors.warningForeground,
            modifier = modifier,
        )
    }
}

@Composable
private fun Notice(title: String, body: String?, extra: String?, color: Color, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = color, size = NoticeIconSize)
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(title, style = SaqzTheme.typography.support.copy(fontWeight = FontWeight(600)), color = color)
            if (body != null) Text(body, style = SaqzTheme.typography.support, color = colors.textPrimary)
            if (extra != null) Text(extra, style = SaqzTheme.typography.support, color = colors.textSecondary)
        }
    }
}

private const val MutedAlpha = 0.88f
private val DotSize = 10.dp
private val DotGap = 4.dp
private val DotStroke = 1.5.dp
private val IconSize = 18.dp
private val NoticeIconSize = 20.dp

internal object PositionPickerPreviewData {
    val game = PositionPickerGameUi("28", "JUL", "Terça, 19h30", 120, "CERET — Quadra 2", "Vôlei do CERET")

    fun options(centralFull: Boolean = false, allFull: Boolean = false, suggested: Boolean = true) = listOf(
        PositionOptionUi(AthletePosition.PONTA, 4, 4, 1, full = true, suggested = false),
        PositionOptionUi(
            AthletePosition.CENTRAL, 4, if (centralFull || allFull) 4 else 3, 0, full = centralFull || allFull, suggested = suggested,
        ),
        PositionOptionUi(AthletePosition.OPOSTO, 2, if (allFull) 2 else 1, 0, full = allFull, suggested = false),
        PositionOptionUi(AthletePosition.LEVANTADOR, 2, 2, 0, full = true, suggested = false),
    )

    val main = PositionPickerState(loading = false, game = game, options = options(), selected = AthletePosition.CENTRAL)
}

@Preview
@Composable
private fun PositionPickerScreenPreview() = SaqzTheme {
    PositionPickerScreen(PositionPickerPreviewData.main, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun PositionPickerScreenFullPreview() = SaqzTheme {
    PositionPickerScreen(PositionPickerPreviewData.main.copy(selected = AthletePosition.PONTA), onBack = {}, onIntent = {})
}
