package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.VenueUi
import br.com.saqz.groups.presentation.ui.components.GroupVenueRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_map_failure
import br.com.saqz.groups.resources.group_details_venue_edit
import br.com.saqz.groups.resources.group_details_venue_map
import org.jetbrains.compose.resources.stringResource

/**
 * Andaime (T): tudo que é do próximo jogo, ainda com as peças antigas — guia do gestor, card do
 * jogo, "Você vai jogar?", intro do atleta, contadores e a quadra. O C1 troca o corpo inteiro
 * pelo hero azul.
 *
 * Os contadores entram com `isAdmin = false` de propósito: o botão "Avisar quem falta
 * confirmar" mora no [GroupWaitingBlock], para o C1 e o C5 não dividirem arquivo.
 */
@Composable
internal fun GroupHeroBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasContent = state.nextGame != null || state.venue != null || (state.isAdmin && state.onboarding != null)
    if (!hasContent) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        if (state.isAdmin) state.onboarding?.let { GroupOnboardingCard(it, onIntent) }
        state.nextGame?.let { GroupNextGameCard(nextGame = it, onIntent = onIntent) }
        // Dono e admin respondem presença no mesmo lugar que o atleta: o papel
        // administrativo muda o que ele gerencia, não o fato de que ele joga.
        if (state.nextGame != null) GroupGameResponseSection(state = state, onIntent = onIntent)
        if (state.athleteIntroVisible && !state.isAdmin && !state.responding) {
            AthleteOnboardingCard(state.athleteShareFailed, onIntent)
        }
        state.attendance?.let { GroupAttendanceStats(attendance = it, isAdmin = false, onIntent = onIntent) }
        state.venue?.let { GroupVenueCard(venue = it, isAdmin = state.isAdmin, onIntent = onIntent) }
        if (state.mapFailed) {
            Text(stringResource(Res.string.group_details_map_failure), color = SaqzTheme.colors.textPrimary)
        }
    }
}

/**
 * A quadra: o `GroupVenueRow` do VUL-66 dentro do card branco do export. A ação é a única
 * diferença entre as duas visões — "Ver no mapa" no `2e`, "Editar" no `2f`.
 */
@Composable
private fun GroupVenueCard(
    venue: VenueUi,
    isAdmin: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
) = SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.Venue)) {
    GroupVenueRow(
        name = venue.name,
        address = venue.address,
        actionLabel = stringResource(
            if (isAdmin) Res.string.group_details_venue_edit else Res.string.group_details_venue_map,
        ),
        onAction = {
            onIntent(if (isAdmin) GroupDetailsIntent.EditVenue else GroupDetailsIntent.OpenVenueMap)
        },
    )
}
