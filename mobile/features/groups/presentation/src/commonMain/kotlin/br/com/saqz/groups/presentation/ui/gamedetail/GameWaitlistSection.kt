package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_waitlist_adjust_capacity
import br.com.saqz.groups.resources.game_waitlist_capacity_cancel
import br.com.saqz.groups.resources.game_waitlist_capacity_description
import br.com.saqz.groups.resources.game_waitlist_capacity_failed
import br.com.saqz.groups.resources.game_waitlist_capacity_label
import br.com.saqz.groups.resources.game_waitlist_capacity_save
import br.com.saqz.groups.resources.game_waitlist_capacity_title
import br.com.saqz.groups.resources.game_waitlist_mensalista
import br.com.saqz.groups.resources.game_waitlist_position_central
import br.com.saqz.groups.resources.game_waitlist_position_levantador
import br.com.saqz.groups.resources.game_waitlist_position_libero
import br.com.saqz.groups.resources.game_waitlist_position_oposto
import br.com.saqz.groups.resources.game_waitlist_position_ponta
import br.com.saqz.groups.resources.game_waitlist_position_unknown
import br.com.saqz.groups.resources.game_waitlist_promotion_reason
import br.com.saqz.groups.resources.game_waitlist_promotion_failed
import br.com.saqz.groups.resources.game_waitlist_promote
import br.com.saqz.groups.resources.game_waitlist_queue_position
import br.com.saqz.groups.resources.game_waitlist_queue_unknown
import br.com.saqz.groups.resources.game_waitlist_section
import org.jetbrains.compose.resources.stringResource

internal object GameWaitlistTags {
    const val Section = "game-detail-waitlist"
    const val CapacityAction = "game-detail-capacity-action"
    const val CapacitySheet = "game-detail-capacity-sheet"

    fun promote(memberId: String) = "game-detail-promote-$memberId"
}

@Composable
internal fun GameWaitlistSection(
    state: GameDetailState,
    onIntent: (GameDetailIntent) -> Unit,
) {
    val promotionReason = stringResource(Res.string.game_waitlist_promotion_reason)
    SaqzCard(modifier = Modifier.testTag(GameWaitlistTags.Section), padded = false) {
        Box(Modifier.padding(SaqzTheme.metrics.horizontalPadding)) {
            SaqzSectionHeader(stringResource(Res.string.game_waitlist_section))
        }
        SaqzDivider()
        state.waitlist.forEachIndexed { index, person ->
            if (index > 0) SaqzDivider()
            GameWaitlistRow(
                state = state,
                person = person,
                onPromote = { onIntent(GameDetailIntent.Promote(person.id, promotionReason)) },
            )
        }
        if (state.promotionFailed) {
            Text(
                text = stringResource(Res.string.game_waitlist_promotion_failed),
                color = SaqzTheme.colors.errorForeground,
                style = SaqzTheme.typography.support,
                modifier = Modifier.padding(
                    horizontal = SaqzTheme.metrics.horizontalPadding,
                    vertical = SaqzTheme.metrics.blockGap,
                ),
            )
        }
    }
}

@Composable
private fun GameWaitlistRow(
    state: GameDetailState,
    person: GameDetailWaitlistUi,
    onPromote: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Text(
            text = person.queuePosition?.let { stringResource(Res.string.game_waitlist_queue_position, it) }
                ?: stringResource(Res.string.game_waitlist_queue_unknown),
            color = SaqzTheme.colors.primary,
            style = SaqzTheme.typography.caption,
            modifier = Modifier.width(metrics.iconButtonSize),
        )
        SaqzAvatar(name = person.name, initialsColor = SaqzTheme.colors.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(
                text = person.name,
                color = SaqzTheme.colors.textPrimary,
                style = SaqzTheme.typography.body.copy(fontWeight = SaqzTheme.typography.body.fontWeight),
            )
            Text(
                text = stringResource(person.athletePosition.positionResource()),
                color = SaqzTheme.colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
            if (state.mensalistaPriority && person.isMensalista) {
                SaqzStatusChip(
                    text = stringResource(Res.string.game_waitlist_mensalista),
                    tone = SaqzChipTone.Brand,
                )
            }
        }
        if (
            state.isAdmin &&
            state.header?.statusTone == GameDetailStatusTone.Published &&
            state.promotionMode == PromotionMode.MANUAL
        ) {
            SaqzButton(
                label = stringResource(Res.string.game_waitlist_promote),
                onClick = onPromote,
                modifier = Modifier.testTag(GameWaitlistTags.promote(person.id)),
                variant = SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
                loading = state.promotingMemberId == person.id,
                enabled = state.promotingMemberId == null,
            )
        }
    }
}

@Composable
private fun AthletePosition?.positionResource() = when (this) {
    AthletePosition.LIBERO -> Res.string.game_waitlist_position_libero
    AthletePosition.PONTA -> Res.string.game_waitlist_position_ponta
    AthletePosition.CENTRAL -> Res.string.game_waitlist_position_central
    AthletePosition.OPOSTO -> Res.string.game_waitlist_position_oposto
    AthletePosition.LEVANTADOR -> Res.string.game_waitlist_position_levantador
    null -> Res.string.game_waitlist_position_unknown
}

@Composable
internal fun GameWaitlistAdminActions(
    onIntent: (GameDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzButton(
            label = stringResource(Res.string.game_waitlist_adjust_capacity),
            onClick = { onIntent(GameDetailIntent.OpenCapacitySheet) },
            modifier = Modifier.testTag(GameWaitlistTags.CapacityAction),
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
        )
    }
}

@Composable
internal fun GameWaitlistCapacitySheet(
    state: GameDetailState,
    onIntent: (GameDetailIntent) -> Unit,
) = SaqzBottomSheet(
    open = state.capacitySheetOpen,
    onClose = { onIntent(GameDetailIntent.DismissCapacitySheet) },
    modifier = Modifier.testTag(GameWaitlistTags.CapacitySheet),
    title = stringResource(Res.string.game_waitlist_capacity_title),
    description = stringResource(
        if (state.capacityFailed) Res.string.game_waitlist_capacity_failed
        else Res.string.game_waitlist_capacity_description,
    ),
    splitFooter = {
        SaqzButton(
            label = stringResource(Res.string.game_waitlist_capacity_cancel),
            onClick = { onIntent(GameDetailIntent.DismissCapacitySheet) },
            modifier = Modifier.weight(1f),
            variant = SaqzButtonVariant.Secondary,
            enabled = !state.savingCapacity,
        )
        SaqzButton(
            label = stringResource(Res.string.game_waitlist_capacity_save),
            onClick = { onIntent(GameDetailIntent.SaveCapacity) },
            modifier = Modifier.weight(1f),
            loading = state.savingCapacity,
            enabled = !state.savingCapacity,
        )
    },
    content = {
        SaqzStepper(
            value = state.capacityDraft,
            onValueChange = { onIntent(GameDetailIntent.UpdateCapacity(it)) },
            min = 2,
            max = 100,
            label = stringResource(Res.string.game_waitlist_capacity_label),
        )
    },
)
