package br.com.saqz.groups.presentation.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.schedule.UpcomingGameStatus
import br.com.saqz.groups.presentation.schedule.UpcomingGameUi
import br.com.saqz.groups.presentation.ui.components.GroupChoiceChipRow
import br.com.saqz.groups.presentation.ui.confirmationLeadLabel
import br.com.saqz.groups.presentation.ui.durationLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_schedule_pause
import br.com.saqz.groups.resources.group_schedule_published
import br.com.saqz.groups.resources.group_schedule_resume
import br.com.saqz.groups.resources.group_schedule_scheduled
import br.com.saqz.groups.resources.group_schedule_upcoming
import br.com.saqz.groups.resources.group_setup_confirmation_lead_hint
import br.com.saqz.groups.resources.group_setup_confirmation_lead_label
import br.com.saqz.groups.resources.group_setup_duration_label
import br.com.saqz.groups.resources.groups_no_game
import org.jetbrains.compose.resources.stringResource

/**
 * O export escreve `padding:14px` nas linhas do 2m e no rodapé — meio passo entre
 * `blockGap` (12) e `horizontalPadding` (16), que a grade de 4 do design system não
 * nomeia. Fica derivado dos tokens em vez de `14.dp` cru.
 */
internal val GroupScheduleRowPadding: Dp
    @Composable get() = SaqzTheme.metrics.blockGap + SaqzTheme.metrics.subGrid / 2

internal val DURATION_OPTIONS = listOf(60, 90, 120, 150)
internal val CONFIRMATION_LEAD_OPTIONS = listOf(180, 360, 720, 1_440)

/** `letter-spacing:.06em` do mês, entre o `eyebrow` (.08) e o zero das demais escalas. */
private val MonthTracking = 0.06.em

@Composable
internal fun GroupScheduleTimingCard(
    durationMinutes: Int,
    confirmationLeadMinutes: Int,
    onSelectDuration: (Int) -> Unit,
    onSelectConfirmationLead: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Um card só com dois blocos titulados: é o que o export desenha, e card dentro de
    // card não existe no design system — por isso não é um GroupFormCard por bloco.
    SaqzCard(modifier = modifier) {
        Text(
            text = stringResource(Res.string.group_setup_duration_label),
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
        )
        GroupChoiceChipRow(
            values = DURATION_OPTIONS,
            selectedValue = durationMinutes,
            label = { durationLabel(it) },
            onSelect = onSelectDuration,
        )
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = stringResource(Res.string.group_setup_confirmation_lead_label),
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.group_setup_confirmation_lead_hint),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        GroupChoiceChipRow(
            values = CONFIRMATION_LEAD_OPTIONS,
            selectedValue = confirmationLeadMinutes,
            label = { confirmationLeadLabel(it) },
            onSelect = onSelectConfirmationLead,
        )
    }
}

@Composable
internal fun GroupUpcomingGamesSection(
    games: List<UpcomingGameUi>,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_schedule_upcoming))
        // Card flush: as linhas encostam na borda e a divisória vai de ponta a ponta.
        SaqzCard(padded = false) {
            if (games.isEmpty()) {
                // Sem isto o card fica sem filho nenhum e colapsa numa borda solta embaixo
                // do cabeçalho. Grupo sem jogo marcado é caso comum, não erro.
                SaqzEmptyState(title = stringResource(Res.string.groups_no_game))
            } else {
                games.forEachIndexed { index, game ->
                    if (index > 0) SaqzDivider()
                    GroupUpcomingGameRow(game = game, onClick = { onOpenGame(game.id) })
                }
            }
        }
    }
}

@Composable
private fun GroupUpcomingGameRow(
    game: UpcomingGameUi,
    onClick: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val colors = SaqzTheme.colors
    val typography = SaqzTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${GroupScheduleTags.UpcomingGame}:${game.id}")
            .clickable(role = Role.Button, onClickLabel = game.label, onClick = onClick)
            .padding(horizontal = metrics.horizontalPadding, vertical = GroupScheduleRowPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            // Coluna de data de 46px no export; `iconButtonSize` (44) é o token mais
            // próximo e a folga que falta é meio `subGrid`.
            modifier = Modifier.width(metrics.iconButtonSize + metrics.subGrid / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = game.day,
                // 18sp peso 800 com o tracking negativo que só o `title` carrega.
                style = typography.subtitle.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = typography.title.letterSpacing,
                ),
                color = colors.textPrimary,
            )
            Text(
                text = game.month,
                style = typography.navigation.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = MonthTracking,
                ),
                color = colors.textSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = game.label, style = typography.label, color = colors.textPrimary)
            Text(text = game.venue, style = typography.support, color = colors.textSecondary)
        }
        when (game.status) {
            UpcomingGameStatus.Published -> SaqzStatusChip(
                text = stringResource(Res.string.group_schedule_published),
                tone = SaqzChipTone.Success,
                dot = true,
            )

            UpcomingGameStatus.Scheduled -> SaqzStatusChip(
                text = stringResource(Res.string.group_schedule_scheduled),
                tone = SaqzChipTone.Neutral,
            )
        }
    }
}

@Composable
internal fun GroupPauseScheduleCard(
    isPaused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (isPaused) Res.string.group_schedule_resume else Res.string.group_schedule_pause,
    )
    SaqzCard(
        modifier = modifier
            .clip(RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onToggle)
            .testTag(GroupScheduleTags.Pause),
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** O carregando não está desenhado: skeleton na forma dos três cards da tela. */
@Composable
internal fun GroupScheduleLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GroupScheduleRowPadding),
    ) {
        repeat(SKELETON_CARDS) {
            SaqzCard {
                SaqzSkeleton(width = SaqzTheme.metrics.bottomNavHeight)
                SaqzSkeleton(height = SaqzTheme.metrics.sectionGap)
            }
        }
    }
}

private const val SKELETON_CARDS = 3
