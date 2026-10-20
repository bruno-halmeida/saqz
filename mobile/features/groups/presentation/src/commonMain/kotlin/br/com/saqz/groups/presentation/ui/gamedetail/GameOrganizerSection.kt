package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameMemberRemovalUi
import br.com.saqz.groups.presentation.gamedetail.GameOrganizerFailure
import br.com.saqz.groups.presentation.gamedetail.GameOrganizerUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_member_failed
import br.com.saqz.groups.resources.game_member_failed_closed
import br.com.saqz.groups.resources.game_member_reason_label
import br.com.saqz.groups.resources.game_member_reason_other
import br.com.saqz.groups.resources.game_member_reason_placeholder
import br.com.saqz.groups.resources.game_member_remove
import br.com.saqz.groups.resources.game_member_remove_confirm
import br.com.saqz.groups.resources.game_member_remove_confirmed
import br.com.saqz.groups.resources.game_member_remove_confirmed_fee
import br.com.saqz.groups.resources.game_member_remove_confirmed_queue
import br.com.saqz.groups.resources.game_member_remove_confirmed_queue_fee
import br.com.saqz.groups.resources.game_member_remove_keep
import br.com.saqz.groups.resources.game_member_remove_notify
import br.com.saqz.groups.resources.game_member_remove_title
import br.com.saqz.groups.resources.game_member_remove_waitlisted
import br.com.saqz.groups.resources.game_place_action
import br.com.saqz.groups.resources.game_place_empty
import br.com.saqz.groups.resources.game_place_full
import br.com.saqz.groups.resources.game_place_pick
import br.com.saqz.groups.resources.game_place_submit
import br.com.saqz.groups.resources.game_place_title
import br.com.saqz.groups.resources.game_place_waitlist
import br.com.saqz.groups.resources.game_waitlist_promotion_reason
import org.jetbrains.compose.resources.stringResource
import br.com.saqz.groups.resources.position_place_full_note
import br.com.saqz.groups.resources.position_place_title

internal object GameOrganizerTags {
    const val RemoveSheet = "game-member-remove-sheet"
    const val RemoveConfirm = "game-member-remove-confirm"
    const val RemoveKeep = "game-member-remove-keep"
    const val RemoveReasonToggle = "game-member-remove-reason-toggle"
    const val RemoveReason = "game-member-remove-reason"
    const val RemoveNotify = "game-member-remove-notify"
    const val RemoveFailed = "game-member-remove-failed"
    const val PlaceAction = "game-place-action"
    const val PlaceSheet = "game-place-sheet"
    const val PlaceEmpty = "game-place-empty"
    const val PlaceNote = "game-place-note"
    const val PlaceSubmit = "game-place-submit"
    const val PlaceReasonToggle = "game-place-reason-toggle"
    const val PlaceReason = "game-place-reason"
    const val PlaceFailed = "game-place-failed"

    fun remove(rowId: String) = "game-member-remove-$rowId"
    fun candidate(memberId: String) = "game-place-candidate-$memberId"
}

/** "Tirar" à direita da linha de um membro (confirmado ou na espera), só para o gestor. */
@Composable
internal fun GameMemberRemoveAction(rowId: String, onIntent: (GameDetailIntent) -> Unit) = SaqzButton(
    label = stringResource(Res.string.game_member_remove),
    onClick = { onIntent(GameDetailIntent.RequestRemoveMember(rowId)) },
    modifier = Modifier.testTag(GameOrganizerTags.remove(rowId)),
    variant = SaqzButtonVariant.Ghost,
    size = SaqzButtonSize.Sm,
    contentColor = SaqzTheme.colors.textSecondary,
)

/** Botão abaixo da lista de confirmados. */
@Composable
internal fun GamePlaceAction(onIntent: (GameDetailIntent) -> Unit, modifier: Modifier = Modifier) = SaqzButton(
    label = stringResource(Res.string.game_place_action),
    onClick = { onIntent(GameDetailIntent.OpenPlaceSheet) },
    modifier = modifier.testTag(GameOrganizerTags.PlaceAction),
    variant = SaqzButtonVariant.Secondary,
    fullWidth = true,
)

@Composable
private fun GameOrganizerFailure.message() = stringResource(
    when (this) {
        GameOrganizerFailure.Generic -> Res.string.game_member_failed
        GameOrganizerFailure.Closed -> Res.string.game_member_failed_closed
    },
)

@Composable
internal fun GameMemberRemoveSheet(
    organizer: GameOrganizerUi,
    removal: GameMemberRemovalUi,
    feeLabel: String?,
    onIntent: (GameDetailIntent) -> Unit,
) {
    val defaultReason = stringResource(Res.string.game_waitlist_promotion_reason)
    SaqzBottomSheet(
        open = true,
        onClose = { onIntent(GameDetailIntent.DismissRemoveMember) },
        modifier = Modifier.testTag(GameOrganizerTags.RemoveSheet),
        title = stringResource(Res.string.game_member_remove_title, removal.name),
        description = when {
            !removal.confirmed -> stringResource(Res.string.game_member_remove_waitlisted)
            removal.feeCancelled && feeLabel != null && removal.queueMoves ->
                stringResource(Res.string.game_member_remove_confirmed_queue_fee, feeLabel)
            removal.feeCancelled && feeLabel != null -> stringResource(Res.string.game_member_remove_confirmed_fee, feeLabel)
            removal.queueMoves -> stringResource(Res.string.game_member_remove_confirmed_queue)
            else -> stringResource(Res.string.game_member_remove_confirmed)
        },
        splitFooter = {
            SaqzButton(
                stringResource(Res.string.game_member_remove_keep),
                { onIntent(GameDetailIntent.DismissRemoveMember) },
                Modifier.weight(1f).testTag(GameOrganizerTags.RemoveKeep),
                SaqzButtonVariant.Secondary,
                enabled = !organizer.removing,
            )
            SaqzButton(
                stringResource(Res.string.game_member_remove_confirm),
                { onIntent(GameDetailIntent.ConfirmRemoveMember(defaultReason)) },
                Modifier.weight(1f).testTag(GameOrganizerTags.RemoveConfirm),
                loading = organizer.removing,
                enabled = !organizer.removing,
            )
        },
    ) {
        Text(
            text = stringResource(Res.string.game_member_remove_notify, removal.name),
            color = SaqzTheme.colors.textSecondary,
            style = SaqzTheme.typography.support,
            modifier = Modifier.testTag(GameOrganizerTags.RemoveNotify),
        )
        if (removal.reasonOpen) {
            SaqzInput(
                value = removal.reason,
                onValueChange = { onIntent(GameDetailIntent.UpdateRemoveReason(it)) },
                label = stringResource(Res.string.game_member_reason_label),
                placeholder = stringResource(Res.string.game_member_reason_placeholder),
                enabled = !organizer.removing,
                modifier = Modifier.testTag(GameOrganizerTags.RemoveReason),
            )
        } else {
            SaqzButton(
                label = stringResource(Res.string.game_member_reason_other),
                onClick = { onIntent(GameDetailIntent.ToggleRemoveReason) },
                modifier = Modifier.testTag(GameOrganizerTags.RemoveReasonToggle),
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                enabled = !organizer.removing,
            )
        }
        organizer.removeFailure?.let {
            Text(
                text = it.message(),
                color = SaqzTheme.colors.errorForeground,
                style = SaqzTheme.typography.support,
                modifier = Modifier.testTag(GameOrganizerTags.RemoveFailed),
            )
        }
    }
}

@Composable
internal fun GamePlaceSheet(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) {
    val organizer = state.organizer
    val reason = stringResource(Res.string.game_waitlist_promotion_reason)
    val selected = organizer.selected
    SaqzBottomSheet(
        open = true,
        onClose = { onIntent(GameDetailIntent.DismissPlaceSheet) },
        modifier = Modifier.testTag(GameOrganizerTags.PlaceSheet),
        title = stringResource(Res.string.game_place_title),
        footer = {
            SaqzButton(
                label = selected?.let { stringResource(Res.string.game_place_submit, it.name) }
                    ?: stringResource(Res.string.game_place_pick),
                onClick = { onIntent(GameDetailIntent.ConfirmPlaceMember(reason)) },
                modifier = Modifier.testTag(GameOrganizerTags.PlaceSubmit),
                fullWidth = true,
                enabled = selected != null && !organizer.placing && !state.placeBlocked &&
                    (!state.hasPositions || organizer.placePosition != null),
                loading = organizer.placing,
            )
        },
    ) {
        when {
            state.placeBlocked -> PlaceNote(stringResource(Res.string.game_place_full))
            state.placeGoesToWaitlist -> PlaceNote(stringResource(Res.string.game_place_waitlist))
        }
        if (organizer.candidates.isEmpty()) {
            Text(
                text = stringResource(Res.string.game_place_empty),
                color = SaqzTheme.colors.textSecondary,
                style = SaqzTheme.typography.body,
                modifier = Modifier.testTag(GameOrganizerTags.PlaceEmpty),
            )
        }
        Column {
            organizer.candidates.forEach { candidate ->
                val isSelected = candidate.id == organizer.selectedId
                SaqzMemberRow(
                    name = candidate.name,
                    meta = stringResource(candidate.position.positionResource()),
                    modifier = Modifier
                        .testTag(GameOrganizerTags.candidate(candidate.id))
                        .semantics { this.selected = isSelected }
                        .then(if (isSelected) Modifier.background(SaqzTheme.colors.surfaceSoft) else Modifier),
                    onClick = if (organizer.placing) null else {
                        { onIntent(GameDetailIntent.SelectPlaceCandidate(candidate.id)) }
                    },
                    trailing = if (isSelected) {
                        { SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.primary) }
                    } else {
                        null
                    },
                )
            }
        }
        // DEB-102: em jogo com posições o gestor escolhe a posição de quem entra; posição cheia entra acima das vagas.
        if (state.hasPositions && selected != null) {
            Text(
                text = stringResource(Res.string.position_place_title),
                color = SaqzTheme.colors.textPrimary,
                style = SaqzTheme.typography.label,
            )
            GamePositionChoices(
                positions = state.positions,
                selected = organizer.placePosition,
                suggested = selected.position,
                enabled = !organizer.placing,
                tagPrefix = "game-place-position",
                onSelect = { onIntent(GameDetailIntent.SelectPlacePosition(it)) },
            )
            if (state.positions.any { it.position == organizer.placePosition && it.full }) {
                PlaceNote(stringResource(Res.string.position_place_full_note))
            }
        }
        if (organizer.placeReasonOpen) {
            SaqzInput(
                value = organizer.placeReason,
                onValueChange = { onIntent(GameDetailIntent.UpdatePlaceReason(it)) },
                label = stringResource(Res.string.game_member_reason_label),
                placeholder = stringResource(Res.string.game_member_reason_placeholder),
                enabled = !organizer.placing,
                modifier = Modifier.testTag(GameOrganizerTags.PlaceReason),
            )
        } else {
            SaqzButton(
                label = stringResource(Res.string.game_member_reason_other),
                onClick = { onIntent(GameDetailIntent.TogglePlaceReason) },
                modifier = Modifier.testTag(GameOrganizerTags.PlaceReasonToggle),
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                enabled = !organizer.placing,
            )
        }
        organizer.placeFailure?.let {
            Text(
                text = it.message(),
                color = SaqzTheme.colors.errorForeground,
                style = SaqzTheme.typography.support,
                modifier = Modifier.testTag(GameOrganizerTags.PlaceFailed),
            )
        }
    }
}

@Composable
private fun PlaceNote(text: String) = Text(
    text = text,
    color = SaqzTheme.colors.textSecondary,
    style = SaqzTheme.typography.support,
    modifier = Modifier.testTag(GameOrganizerTags.PlaceNote),
)
