package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import br.com.saqz.groups.resources.game_capacity_below_confirmed
import br.com.saqz.groups.resources.game_waitlist_adjust_capacity
import br.com.saqz.groups.resources.game_waitlist_capacity_cancel
import br.com.saqz.groups.resources.game_waitlist_capacity_description
import br.com.saqz.groups.presentation.GameCapacityLimits
import br.com.saqz.groups.resources.game_waitlist_capacity_legacy
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
import br.com.saqz.groups.resources.game_waitlist_reason_avulso
import br.com.saqz.groups.resources.game_waitlist_reason_full
import br.com.saqz.groups.resources.game_waitlist_reason_late
import br.com.saqz.groups.resources.game_waitlist_reason_manual
import br.com.saqz.groups.resources.game_waitlist_reason_started
import br.com.saqz.groups.resources.game_waitlist_promotion_failed
import br.com.saqz.groups.resources.game_waitlist_promote
import br.com.saqz.groups.resources.game_waitlist_queue_position
import br.com.saqz.groups.resources.game_waitlist_queue_unknown
import br.com.saqz.groups.resources.game_waitlist_section
import br.com.saqz.groups.resources.position_force_action
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GameWaitlistTags {
    const val Section = "game-detail-waitlist"
    const val CapacityAction = "game-detail-capacity-action"
    const val CapacitySheet = "game-detail-capacity-sheet"

    fun promote(memberId: String) = "game-detail-promote-$memberId"

    fun reason(memberId: String) = "game-detail-waitlist-reason-$memberId"
}

@Composable
internal fun AthletePosition?.positionResource() = when (this) {
    AthletePosition.LIBERO -> Res.string.game_waitlist_position_libero
    AthletePosition.PONTA -> Res.string.game_waitlist_position_ponta
    AthletePosition.CENTRAL -> Res.string.game_waitlist_position_central
    AthletePosition.OPOSTO -> Res.string.game_waitlist_position_oposto
    AthletePosition.LEVANTADOR -> Res.string.game_waitlist_position_levantador
    null -> Res.string.game_waitlist_position_unknown
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
    description = run {
        val confirmed = state.attendance?.confirmed ?: 0
        val overflow = confirmed - state.capacityDraft
        when {
            state.capacityFailed -> stringResource(Res.string.game_waitlist_capacity_failed)
            overflow > 0 -> pluralStringResource(
                Res.plurals.game_capacity_below_confirmed,
                overflow,
                confirmed,
                state.capacityDraft,
                overflow,
            )
            (state.attendance?.capacity ?: GameCapacityLimits.Min) < GameCapacityLimits.Min -> stringResource(
                Res.string.game_waitlist_capacity_legacy,
                state.attendance?.capacity ?: 0,
                GameCapacityLimits.Min,
            )
            else -> stringResource(Res.string.game_waitlist_capacity_description)
        }
    },
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
            min = GameCapacityLimits.Min,
            max = GameCapacityLimits.Max,
            label = stringResource(Res.string.game_waitlist_capacity_label),
        )
    },
)
