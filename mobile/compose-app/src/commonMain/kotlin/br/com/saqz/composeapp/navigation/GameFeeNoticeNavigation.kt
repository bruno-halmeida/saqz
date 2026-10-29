package br.com.saqz.composeapp.navigation

import br.com.saqz.groups.presentation.navigation.GroupsRoute

/** DEB-146: aviso de sistema (canal FIRST_MEMBER) do grupo ainda sem valor por jogo. */
internal const val GAME_FEE_UNDEFINED_EVENT = "GAME_FEE_UNDEFINED"

/**
 * DEB-146: o toque no aviso do grupo sem valor (central ou push) abre o "Valor por jogo" de Editar grupo, com a
 * sugestão preenchida. `null` para os outros avisos, que seguem o caminho de sempre (o FIRST_MEMBER abre o grupo).
 */
internal fun gameFeeNoticeDestination(eventKind: String?, groupId: String?): GroupsRoute.Edit? =
    groupId?.takeIf { eventKind == GAME_FEE_UNDEFINED_EVENT }?.let { GroupsRoute.Edit(it, focusGameFee = true) }
