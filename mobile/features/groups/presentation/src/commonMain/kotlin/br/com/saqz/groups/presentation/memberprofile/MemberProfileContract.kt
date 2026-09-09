package br.com.saqz.groups.presentation.memberprofile

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class MemberProfileState(
    val loading: Boolean = true,
    val error: GroupUiError? = null,
    val name: String = "",
    val attributes: List<String> = emptyList(),
    val phone: String? = null,
    val games: String? = null,
    val attendance: String? = null,
    val absences: String? = null,
    val statsFailed: Boolean = false,
)

sealed interface MemberProfileIntent {
    data object Retry : MemberProfileIntent
}

sealed interface MemberProfileEffect
