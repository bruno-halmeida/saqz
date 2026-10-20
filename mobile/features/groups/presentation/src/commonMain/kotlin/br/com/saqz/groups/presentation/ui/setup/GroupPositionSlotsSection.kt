package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.SaqzTextLink
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.setup.GroupPositionDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.pluralLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.position_slots_hint
import br.com.saqz.groups.resources.position_slots_over
import br.com.saqz.groups.resources.position_slots_short
import br.com.saqz.groups.resources.position_slots_suggest
import br.com.saqz.groups.resources.position_slots_suggest_failed
import br.com.saqz.groups.resources.position_slots_sum
import br.com.saqz.groups.resources.position_slots_title
import br.com.saqz.groups.resources.position_slots_total_ok
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GroupPositionSlotsTags {
    const val Section = "group-position-slots"
    const val Suggest = "group-position-slots-suggest"
    const val Total = "group-position-slots-total"
    const val Sum = "group-position-slots-sum"
    fun position(position: AthletePosition) = "group-position-slots-${position.name.lowercase()}"
}

// Ordem do desenho: levantadores, pontas, centrais, opostos, líberos.
private val PositionOrder = listOf(
    AthletePosition.LEVANTADOR,
    AthletePosition.PONTA,
    AthletePosition.CENTRAL,
    AthletePosition.OPOSTO,
    AthletePosition.LIBERO,
)

/**
 * Painel das vagas por posição do 5-1, dentro de "Regras dos jogos" logo abaixo de "Vagas por jogo". A soma tem de bater
 * com o total: a diferença ("Faltam 2 vagas para fechar 14.") aparece aqui, mas o botão de criar/salvar não fica
 * desabilitado — tocar com a soma errada recusa e conta no aviso geral.
 */
@Composable
internal fun GroupPositionSlotsSection(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val difference = state.positionsDifference
    val closed = difference == 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.background)
            .padding(metrics.blockGap)
            .testTag(GroupPositionSlotsTags.Section),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.position_slots_title),
                style = SaqzTheme.typography.label,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.position_slots_sum, state.positionsTotal, state.positionsTarget),
                style = SaqzTheme.typography.label,
                color = if (closed) colors.success else colors.errorForeground,
                modifier = Modifier.testTag(GroupPositionSlotsTags.Sum),
            )
        }
        Text(
            text = stringResource(Res.string.position_slots_hint),
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
        )
        PositionOrder.forEach { position ->
            SaqzStepper(
                value = state.positionSlots[position] ?: 0,
                onValueChange = { onIntent(GroupSetupIntent.UpdatePositionSlots(position, it)) },
                min = 0,
                max = GroupPositionDefaults.MaxPerPosition,
                label = position.pluralLabel().replaceFirstChar(Char::uppercase),
                modifier = Modifier.testTag(GroupPositionSlotsTags.position(position)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
            if (!closed) SaqzIcon(SaqzIcons.CircleAlert, tint = colors.errorForeground)
            Text(
                text = when {
                    difference < 0 ->
                        pluralStringResource(Res.plurals.position_slots_short, -difference, -difference, state.positionsTarget)
                    difference > 0 ->
                        pluralStringResource(Res.plurals.position_slots_over, difference, difference, state.positionsTarget)
                    else -> stringResource(Res.string.position_slots_total_ok, state.positionsTarget)
                },
                style = SaqzTheme.typography.support,
                color = if (closed) colors.textSecondary else colors.errorForeground,
                modifier = Modifier.weight(1f).testTag(GroupPositionSlotsTags.Total),
            )
        }
        if (state.positionSuggestionFailed) {
            Text(
                text = stringResource(Res.string.position_slots_suggest_failed),
                style = SaqzTheme.typography.support,
                color = colors.errorForeground,
            )
        }
        SaqzTextLink(
            label = stringResource(Res.string.position_slots_suggest, state.positionsTarget),
            onClick = { onIntent(GroupSetupIntent.SuggestPositionSlots) },
            touchTarget = true,
            modifier = Modifier.testTag(GroupPositionSlotsTags.Suggest),
        )
    }
}
