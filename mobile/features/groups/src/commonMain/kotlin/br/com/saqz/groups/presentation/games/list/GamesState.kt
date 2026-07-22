package br.com.saqz.groups.presentation.games.list

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.group.GroupRole

@Immutable
data class GamesState(
    val groupId: String? = null,
    val role: GroupRole? = null,
    val upcoming: List<GameListItem> = emptyList(),
    val past: List<GameListItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: GamesLoadError? = null,
) {
    val canCreate: Boolean
        get() = role == GroupRole.OWNER || role == GroupRole.ADMIN
}
