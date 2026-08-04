package br.com.saqz.groups.presentation.home

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val displayName: String? = null,
    val home: HomeReadModel? = null,
)

sealed interface HomeAction {
    data object Retry : HomeAction
}
