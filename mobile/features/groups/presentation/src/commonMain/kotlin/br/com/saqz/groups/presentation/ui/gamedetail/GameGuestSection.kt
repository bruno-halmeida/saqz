package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameGuestHint
import br.com.saqz.groups.presentation.gamedetail.GameGuestRemovalUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestRowUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_guest_add
import br.com.saqz.groups.resources.game_guest_add_failed
import br.com.saqz.groups.resources.game_guest_hint_closed
import br.com.saqz.groups.resources.game_guest_hint_default
import br.com.saqz.groups.resources.game_guest_hint_need_answer
import br.com.saqz.groups.resources.game_guest_info_fee
import br.com.saqz.groups.resources.game_guest_info_queue
import br.com.saqz.groups.resources.game_guest_info_scope
import br.com.saqz.groups.resources.game_guest_joined
import br.com.saqz.groups.resources.game_guest_name_label
import br.com.saqz.groups.resources.game_guest_name_placeholder
import br.com.saqz.groups.resources.game_guest_of
import br.com.saqz.groups.resources.game_guest_remove
import br.com.saqz.groups.resources.game_guest_remove_confirm
import br.com.saqz.groups.resources.game_guest_remove_confirmed
import br.com.saqz.groups.resources.game_guest_remove_confirmed_fee
import br.com.saqz.groups.resources.game_guest_remove_failed
import br.com.saqz.groups.resources.game_guest_remove_keep
import br.com.saqz.groups.resources.game_guest_remove_title
import br.com.saqz.groups.resources.game_guest_remove_waitlisted
import br.com.saqz.groups.resources.game_guest_removed
import br.com.saqz.groups.resources.game_guest_sheet_title
import br.com.saqz.groups.resources.game_guest_submit
import br.com.saqz.groups.resources.game_guest_submitting
import br.com.saqz.groups.resources.game_guest_yours
import br.com.saqz.groups.resources.game_guest_yours_fee
import org.jetbrains.compose.resources.stringResource

internal object GameGuestTags {
    const val Add = "game-guest-add"
    const val Hint = "game-guest-hint"
    const val Sheet = "game-guest-sheet"
    const val Name = "game-guest-name"
    const val Submit = "game-guest-submit"
    const val AddFailed = "game-guest-add-failed"
    const val RemoveSheet = "game-guest-remove-sheet"
    const val RemoveConfirm = "game-guest-remove-confirm"
    const val RemoveFailed = "game-guest-remove-failed"
    const val Notice = "game-guest-notice"

    fun remove(rowId: String) = "game-guest-remove-$rowId"
    fun row(rowId: String) = "game-guest-row-$rowId"
}

/** Botão "Levar convidado" + a dica do porquê (regras A e C). Some fora de jogo publicado. */
@Composable
internal fun GameGuestAction(guest: GameGuestUi, onIntent: (GameDetailIntent) -> Unit, modifier: Modifier = Modifier) {
    if (!guest.visible) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaqzButton(
            label = stringResource(Res.string.game_guest_add),
            onClick = { onIntent(GameDetailIntent.OpenGuestSheet) },
            modifier = Modifier.testTag(GameGuestTags.Add),
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            enabled = guest.enabled,
            leadingContent = { tint -> SaqzIcon(SaqzIcons.Users, tint = tint, size = GuestButtonIconSize) },
        )
        Text(
            text = stringResource(
                when (guest.hint) {
                    GameGuestHint.Default -> Res.string.game_guest_hint_default
                    GameGuestHint.NeedAnswer -> Res.string.game_guest_hint_need_answer
                    GameGuestHint.Closed -> Res.string.game_guest_hint_closed
                },
            ),
            color = SaqzTheme.colors.textSecondary,
            style = SaqzTheme.typography.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(GameGuestTags.Hint),
        )
    }
}

/** A segunda linha de uma pessoa da lista quando ela é convidada. `null` = não é convidado. */
@Composable
internal fun GameGuestRowUi.metaLabel(feeLabel: String?, confirmed: Boolean): String = when {
    isYours && confirmed && feeLabel != null -> stringResource(Res.string.game_guest_yours_fee, feeLabel)
    isYours -> stringResource(Res.string.game_guest_yours)
    else -> stringResource(Res.string.game_guest_of, hostName)
}

/** Avatar do convidado: círculo tracejado na cor da marca, para não se passar por membro. */
@Composable
internal fun GameGuestAvatar(modifier: Modifier = Modifier) {
    val color = SaqzTheme.colors.primary
    Box(
        modifier = modifier.size(GuestAvatarSize).drawBehind {
            drawCircle(
                color = color,
                radius = size.minDimension / 2 - GuestAvatarStroke.toPx(),
                style = Stroke(
                    width = GuestAvatarStroke.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(GuestAvatarDash.toPx(), GuestAvatarDash.toPx())),
                ),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        SaqzIcon(SaqzIcons.Users, tint = color, size = GuestButtonIconSize)
    }
}

@Composable
internal fun GameGuestRemoveAction(rowId: String, guest: GameGuestRowUi, onIntent: (GameDetailIntent) -> Unit) {
    if (!guest.canRemove) return
    SaqzButton(
        label = stringResource(Res.string.game_guest_remove),
        onClick = { onIntent(GameDetailIntent.RequestRemoveGuest(rowId)) },
        modifier = Modifier.testTag(GameGuestTags.remove(rowId)),
        variant = SaqzButtonVariant.Ghost,
        size = SaqzButtonSize.Sm,
        contentColor = SaqzTheme.colors.textSecondary,
    )
}

@Composable
internal fun GameGuestAddSheet(guest: GameGuestUi, onIntent: (GameDetailIntent) -> Unit) = SaqzBottomSheet(
    open = true,
    onClose = { onIntent(GameDetailIntent.DismissGuestSheet) },
    modifier = Modifier.testTag(GameGuestTags.Sheet),
    title = stringResource(Res.string.game_guest_sheet_title),
    footer = {
        SaqzButton(
            label = stringResource(
                if (guest.adding) Res.string.game_guest_submitting else Res.string.game_guest_submit,
            ),
            onClick = { onIntent(GameDetailIntent.SubmitGuest) },
            modifier = Modifier.testTag(GameGuestTags.Submit),
            fullWidth = true,
            enabled = guest.canSubmit,
            loading = guest.adding,
        )
    },
) {
    SaqzInput(
        value = guest.name,
        onValueChange = { onIntent(GameDetailIntent.UpdateGuestName(it)) },
        label = stringResource(Res.string.game_guest_name_label),
        placeholder = stringResource(Res.string.game_guest_name_placeholder),
        enabled = !guest.adding,
        modifier = Modifier.testTag(GameGuestTags.Name),
    )
    GuestInfoLine(SaqzIcons.Clock, stringResource(Res.string.game_guest_info_queue))
    guest.feeLabel?.let { GuestInfoLine(SaqzIcons.CreditCard, stringResource(Res.string.game_guest_info_fee, it)) }
    GuestInfoLine(SaqzIcons.Users, stringResource(Res.string.game_guest_info_scope))
    if (guest.addFailed) {
        Text(
            text = stringResource(Res.string.game_guest_add_failed),
            color = SaqzTheme.colors.errorForeground,
            style = SaqzTheme.typography.support,
            modifier = Modifier.testTag(GameGuestTags.AddFailed),
        )
    }
}

@Composable
private fun GuestInfoLine(icon: ImageVector, text: String) = Row(
    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    verticalAlignment = Alignment.Top,
) {
    SaqzIcon(icon, tint = SaqzTheme.colors.textSecondary, size = GuestButtonIconSize)
    Text(
        text = text,
        color = SaqzTheme.colors.textSecondary,
        style = SaqzTheme.typography.support,
        modifier = Modifier.weight(1f),
    )
}

@Composable
internal fun GameGuestRemoveSheet(guest: GameGuestUi, removal: GameGuestRemovalUi, onIntent: (GameDetailIntent) -> Unit) =
    SaqzBottomSheet(
        open = true,
        onClose = { onIntent(GameDetailIntent.DismissRemoveGuest) },
        modifier = Modifier.testTag(GameGuestTags.RemoveSheet),
        title = stringResource(Res.string.game_guest_remove_title, removal.name),
        description = when {
            guest.removeFailed -> stringResource(Res.string.game_guest_remove_failed)
            !removal.confirmed -> stringResource(Res.string.game_guest_remove_waitlisted)
            guest.feeLabel != null -> stringResource(Res.string.game_guest_remove_confirmed_fee, guest.feeLabel)
            else -> stringResource(Res.string.game_guest_remove_confirmed)
        },
        splitFooter = {
            SaqzButton(
                stringResource(Res.string.game_guest_remove_keep),
                { onIntent(GameDetailIntent.DismissRemoveGuest) },
                Modifier.weight(1f),
                SaqzButtonVariant.Secondary,
                enabled = !guest.removing,
            )
            SaqzButton(
                stringResource(Res.string.game_guest_remove_confirm),
                { onIntent(GameDetailIntent.ConfirmRemoveGuest) },
                Modifier.weight(1f).testTag(GameGuestTags.RemoveConfirm),
                loading = guest.removing,
                enabled = !guest.removing,
            )
        },
    ) {}

@Composable
internal fun GameGuestNotice(guest: GameGuestUi, onIntent: (GameDetailIntent) -> Unit, modifier: Modifier = Modifier) {
    val name = guest.noticeName ?: return
    SaqzToast(
        visible = true,
        onDismiss = { onIntent(GameDetailIntent.DismissGuestNotice) },
        modifier = modifier.padding(SaqzTheme.metrics.horizontalPadding).testTag(GameGuestTags.Notice),
    ) {
        SaqzToastText(
            text = stringResource(
                if (guest.noticeJoined) Res.string.game_guest_joined else Res.string.game_guest_removed,
                name,
            ),
        )
    }
}

private val GuestButtonIconSize = 20.dp
private val GuestAvatarSize = 40.dp
private val GuestAvatarStroke = 1.5.dp
private val GuestAvatarDash = 4.dp
