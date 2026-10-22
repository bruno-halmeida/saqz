package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.Text
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailConfirmedUi
import br.com.saqz.groups.resources.game_no_show_action
import br.com.saqz.groups.resources.game_no_show_action_hint
import br.com.saqz.groups.resources.game_no_show_cancel
import br.com.saqz.groups.resources.game_no_show_confirmed_for
import br.com.saqz.groups.resources.game_no_show_failed
import br.com.saqz.groups.resources.game_no_show_kind_guest
import br.com.saqz.groups.resources.game_no_show_note
import br.com.saqz.groups.resources.game_no_show_note_fee
import br.com.saqz.groups.resources.game_no_show_undo
import br.com.saqz.groups.resources.game_no_show_undo_hint
import br.com.saqz.groups.resources.group_member_membership_monthly
import br.com.saqz.groups.resources.group_member_membership_single_game
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSheetAction
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.canForce
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_detail_cancel
import br.com.saqz.groups.resources.game_detail_edit
import br.com.saqz.groups.resources.game_detail_title
import br.com.saqz.groups.resources.game_menu_cancel_hint
import br.com.saqz.groups.resources.game_menu_capacity_hint
import br.com.saqz.groups.resources.game_menu_edit_hint
import br.com.saqz.groups.resources.game_person_remove
import br.com.saqz.groups.resources.game_person_remove_guest
import br.com.saqz.groups.resources.game_waitlist_adjust_capacity
import br.com.saqz.groups.resources.position_force_action
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Menu ⋯ da barra (dono/admin, DEB-116): editar, ajustar vagas (publicado, por começar, sem posições) e cancelar
 * (só publicado). Cada item abre a folha que já existia.
 */
@Composable
internal fun GameMenuSheet(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) = SaqzBottomSheet(
    open = true,
    onClose = { onIntent(GameDetailIntent.DismissGameMenu) },
    modifier = Modifier.testTag(GameDetailTags.MenuSheet),
    title = state.hero?.menuTitle ?: stringResource(Res.string.game_detail_title),
) {
    SaqzSheetAction(
        icon = SaqzIcons.Pencil,
        label = stringResource(Res.string.game_detail_edit),
        hint = stringResource(Res.string.game_menu_edit_hint),
        onClick = { onIntent(GameDetailIntent.Edit) },
        divider = false,
        modifier = Modifier.testTag(GameDetailTags.MenuEdit),
    )
    if (state.canAdjustCapacity) {
        val capacity = state.attendance?.capacity ?: 0
        SaqzSheetAction(
            icon = SaqzIcons.Users,
            label = stringResource(Res.string.game_waitlist_adjust_capacity),
            hint = pluralStringResource(Res.plurals.game_menu_capacity_hint, capacity, capacity),
            onClick = { onIntent(GameDetailIntent.OpenCapacitySheet) },
            modifier = Modifier.testTag(GameDetailTags.MenuCapacity),
        )
    }
    if (state.header?.statusTone == GameDetailStatusTone.Published) {
        SaqzSheetAction(
            icon = SaqzIcons.CircleX,
            label = stringResource(Res.string.game_detail_cancel),
            hint = stringResource(Res.string.game_menu_cancel_hint),
            onClick = { onIntent(GameDetailIntent.RequestCancel) },
            destructive = true,
            modifier = Modifier.testTag(GameDetailTags.MenuCancel),
        )
    }
}

/**
 * O ⋮ de uma pessoa: as ações dela no molde da folha de Membros. "Tirar do jogo", "Forçar entrada" e
 * "Tirar convidado(a)" abrem as confirmações de sempre.
 */
@Composable
internal fun GamePersonActionsSheet(state: GameDetailState, rowId: String, onIntent: (GameDetailIntent) -> Unit) {
    val confirmed = state.confirmedRoster.firstOrNull { it.id == rowId }
    val waiting = state.waitlist.firstOrNull { it.id == rowId }
    val name = confirmed?.name ?: waiting?.name ?: return
    val guest = confirmed?.guest ?: waiting?.guest
    if (confirmed != null && state.canMarkNoShow) {
        GameNoShowSheet(state, confirmed, onIntent)
        return
    }
    SaqzBottomSheet(
        open = true,
        onClose = { onIntent(GameDetailIntent.DismissPersonActions) },
        modifier = Modifier.testTag(GameDetailTags.PersonSheet),
        title = name,
    ) {
        if (confirmed?.canRemove == true || waiting?.canRemove == true) {
            SaqzSheetAction(
                icon = SaqzIcons.CircleX,
                label = stringResource(Res.string.game_person_remove),
                divider = false,
                onClick = { onIntent(GameDetailIntent.RequestRemoveMember(rowId)) },
                destructive = true,
                modifier = Modifier.testTag(GameOrganizerTags.remove(rowId)),
            )
        }
        val removable = confirmed?.canRemove == true || waiting?.canRemove == true
        if (waiting != null && state.canForce(waiting)) {
            SaqzSheetAction(
                divider = removable,
                icon = SaqzIcons.UserPlus,
                label = stringResource(Res.string.position_force_action),
                onClick = { onIntent(GameDetailIntent.RequestForceEntry(rowId)) },
                modifier = Modifier.testTag(GamePositionTags.force(rowId)),
            )
        }
        if (guest?.canRemove == true) {
            SaqzSheetAction(
                divider = removable || (waiting != null && state.canForce(waiting)),
                icon = SaqzIcons.CircleX,
                label = stringResource(Res.string.game_person_remove_guest),
                onClick = { onIntent(GameDetailIntent.RequestRemoveGuest(rowId)) },
                destructive = true,
                modifier = Modifier.testTag(GameGuestTags.remove(rowId)),
            )
        }
    }
}

/**
 * PRES-016 · o ⋮ de quem jogou no jogo encerrado (gestor, dentro do prazo): "Marcar falta", com o aviso de que só o
 * gestor vê, a pessoa não é avisada e a diária continua; em quem já faltou, "Desfazer". Uma ação por vez.
 */
@Composable
private fun GameNoShowSheet(state: GameDetailState, person: GameDetailConfirmedUi, onIntent: (GameDetailIntent) -> Unit) {
    val sending = state.noShow.sendingId == person.id
    val kind = when {
        person.guest != null -> stringResource(Res.string.game_no_show_kind_guest, person.guest.hostName)
        person.isMensalista -> stringResource(Res.string.group_member_membership_monthly)
        else -> stringResource(Res.string.group_member_membership_single_game)
    }
    // Mensalista não tem diária; convidado paga pelo anfitrião.
    val fee = state.noShow.feeLabel?.takeIf { !person.isMensalista }
    SaqzBottomSheet(
        open = true,
        onClose = { if (!sending) onIntent(GameDetailIntent.DismissPersonActions) },
        modifier = Modifier.testTag(GameDetailTags.PersonSheet),
        title = person.name,
        description = stringResource(Res.string.game_no_show_confirmed_for, kind, state.noShow.gameDay),
        footer = {
            SaqzButton(
                label = stringResource(Res.string.game_no_show_cancel),
                onClick = { onIntent(GameDetailIntent.DismissPersonActions) },
                variant = SaqzButtonVariant.Secondary,
                enabled = !sending,
                fullWidth = true,
            )
        },
    ) {
        if (person.noShow) {
            SaqzSheetAction(
                icon = SaqzIcons.Check,
                label = stringResource(Res.string.game_no_show_undo),
                hint = stringResource(Res.string.game_no_show_undo_hint),
                divider = false,
                onClick = { if (!sending) onIntent(GameDetailIntent.SetNoShow(person.id, marked = false)) },
                modifier = Modifier.testTag(GameDetailTags.noShowUndo(person.id)),
            )
        } else {
            SaqzSheetAction(
                icon = SaqzIcons.CircleX,
                label = stringResource(Res.string.game_no_show_action),
                hint = stringResource(Res.string.game_no_show_action_hint),
                divider = false,
                onClick = { if (!sending) onIntent(GameDetailIntent.SetNoShow(person.id, marked = true)) },
                destructive = true,
                modifier = Modifier.testTag(GameDetailTags.noShowMark(person.id)),
            )
            Text(
                text = fee?.let { stringResource(Res.string.game_no_show_note_fee, it) }
                    ?: stringResource(Res.string.game_no_show_note),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        if (state.noShow.failed) {
            Text(
                text = stringResource(Res.string.game_no_show_failed),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(GameDetailTags.NoShowFailed),
            )
        }
    }
}
