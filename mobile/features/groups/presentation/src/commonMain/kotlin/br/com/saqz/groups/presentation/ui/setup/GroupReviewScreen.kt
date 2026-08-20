package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupStep
import br.com.saqz.groups.presentation.ui.components.GroupVenueRow
import br.com.saqz.groups.presentation.ui.confirmationLeadLabel
import br.com.saqz.groups.presentation.ui.durationLabel
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_venue_edit
import br.com.saqz.groups.resources.group_review_capacity
import br.com.saqz.groups.resources.group_review_composition
import br.com.saqz.groups.resources.group_review_confirmation_lead
import br.com.saqz.groups.resources.group_review_create
import br.com.saqz.groups.resources.group_review_duration
import br.com.saqz.groups.resources.group_review_edit
import br.com.saqz.groups.resources.group_review_level
import br.com.saqz.groups.resources.group_review_modality
import br.com.saqz.groups.resources.group_review_play_style
import br.com.saqz.groups.resources.group_review_players
import br.com.saqz.groups.resources.group_review_title
import br.com.saqz.groups.resources.group_review_weekly_schedule
import br.com.saqz.groups.resources.group_system_creating
import org.jetbrains.compose.resources.stringResource

private const val HairlineDivisor = 4
private const val AvatarFactor = 3

/**
 * `2d` — revisão antes de criar. É passo, não rota: lê o mesmo `GroupSetupForm` e o
 * "Editar informações" devolve o `step` para o formulário com tudo preservado.
 */
@Composable
fun GroupReviewScreen(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val form = state.form
    val backToForm = { onIntent(GroupSetupIntent.BackToForm) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.group_review_title), onBack = backToForm)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
            verticalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
        ) {
            GroupReviewHeader(name = form.name)
            GroupReviewFacts(state)
            if (state.recurring && form.regularSlots.isNotEmpty()) {
                GroupReviewSchedule(form.regularSlots)
            }
            form.defaultVenue?.let { venue ->
                SaqzCard(tone = SaqzCardTone.Soft) {
                    GroupVenueRow(
                        name = venue.name,
                        address = venue.address,
                        actionLabel = stringResource(Res.string.group_details_venue_edit),
                        onAction = backToForm,
                    )
                }
            }
        }
        GroupReviewFooter(state, onIntent)
    }
}

@Composable
private fun GroupReviewHeader(name: String) {
    val metrics = SaqzTheme.metrics
    SaqzCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzAvatar(
                name = name,
                size = metrics.sectionGap * AvatarFactor - metrics.grid,
                background = SaqzTheme.colors.primary,
                initialsColor = SaqzTheme.colors.onPrimary,
            )
            // TODO(VUL-68): o export traz um subtítulo ("Terças e quintas em Tatuapé")
            // sem chave em strings.xml. Não inventamos a chave aqui — quem escreve nesse
            // arquivo é serializado fora da onda. A agenda semanal abaixo já diz o mesmo.
            Text(
                text = name,
                style = SaqzTheme.typography.subtitle,
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GroupReviewFacts(state: GroupSetupState) {
    val form = state.form
    SaqzCard(padded = false) {
        GroupReviewRow(stringResource(Res.string.group_review_modality), form.modality?.label())
        SaqzDivider()
        GroupReviewRow(stringResource(Res.string.group_review_composition), form.composition?.label())
        SaqzDivider()
        GroupReviewRow(stringResource(Res.string.group_review_level), form.level?.label())
        if (state.showsPlayStyle) {
            SaqzDivider()
            GroupReviewRow(stringResource(Res.string.group_review_play_style), form.playStyle?.label())
        }
        SaqzDivider()
        GroupReviewRow(
            label = stringResource(Res.string.group_review_capacity),
            value = stringResource(
                Res.string.group_review_players,
                form.defaultCapacity ?: GroupSetupDefaults.Capacity,
            ),
        )
        SaqzDivider()
        GroupReviewRow(stringResource(Res.string.group_review_duration), durationLabel(state.durationMinutes))
        SaqzDivider()
        GroupReviewRow(
            label = stringResource(Res.string.group_review_confirmation_lead),
            value = form.defaultConfirmationLeadMinutes?.let { confirmationLeadLabel(it) },
        )
    }
}

@Composable
private fun GroupReviewRow(label: String, value: String?) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.orEmpty(),
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Bold),
            color = SaqzTheme.colors.textPrimary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupReviewSchedule(slots: List<GroupRegularSlotForm>) {
    val metrics = SaqzTheme.metrics
    SaqzCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzIcon(SaqzIcons.Calendar, tint = SaqzTheme.colors.primary)
            Text(
                text = stringResource(Res.string.group_review_weekly_schedule),
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
            verticalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            slots.forEach { slot -> GroupSchedulePill(slot) }
        }
    }
}

/** A pílula da revisão é estática: o `GroupSlotPill` do formulário sempre traz o X. */
@Composable
private fun GroupSchedulePill(slot: GroupRegularSlotForm) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Text(
        text = "${slot.weekday.label()} · ${slot.startTime.replace(':', 'h')}",
        style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
        color = colors.textPrimary,
        modifier = Modifier
            .heightIn(min = metrics.iconButtonSize - metrics.subGrid)
            .clip(CircleShape)
            .drawBehind {
                drawRoundRect(
                    color = colors.border,
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(width = metrics.subGrid.toPx() / HairlineDivisor),
                )
            }
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
    )
}

@Composable
private fun GroupReviewFooter(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column {
        SaqzDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaqzTheme.colors.background)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            SaqzButton(
                label = stringResource(
                    if (state.isSaving) Res.string.group_system_creating else Res.string.group_review_create,
                ),
                onClick = { onIntent(GroupSetupIntent.ConfirmCreate) },
                enabled = !state.isSaving,
                loading = state.isSaving,
                fullWidth = true,
                modifier = Modifier.testTag(GroupSetupTags.ReviewCreate),
            )
            SaqzButton(
                label = stringResource(Res.string.group_review_edit),
                onClick = { onIntent(GroupSetupIntent.BackToForm) },
                variant = SaqzButtonVariant.Ghost,
                enabled = !state.isSaving,
                fullWidth = true,
                modifier = Modifier.testTag(GroupSetupTags.ReviewEdit),
            )
        }
    }
}

@Preview
@Composable
private fun GroupReviewScreenPreview() = SaqzTheme {
    Box(Modifier.fillMaxSize()) {
        GroupReviewScreen(
            state = GroupSetupState(
                mode = GroupSetupMode.Create,
                step = GroupSetupStep.Review,
                form = PreviewCourtForm,
            ),
            onIntent = {},
        )
    }
}
