package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzBottomSheet
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
                onClick = { onIntent(GameDetailIntent.RequestRemoveMember(rowId)) },
                destructive = true,
                modifier = Modifier.testTag(GameOrganizerTags.remove(rowId)),
            )
        }
        if (waiting != null && state.canForce(waiting)) {
            SaqzSheetAction(
                icon = SaqzIcons.UserPlus,
                label = stringResource(Res.string.position_force_action),
                onClick = { onIntent(GameDetailIntent.RequestForceEntry(rowId)) },
                modifier = Modifier.testTag(GamePositionTags.force(rowId)),
            )
        }
        if (guest?.canRemove == true) {
            SaqzSheetAction(
                icon = SaqzIcons.CircleX,
                label = stringResource(Res.string.game_person_remove_guest),
                onClick = { onIntent(GameDetailIntent.RequestRemoveGuest(rowId)) },
                destructive = true,
                modifier = Modifier.testTag(GameGuestTags.remove(rowId)),
            )
        }
    }
}
