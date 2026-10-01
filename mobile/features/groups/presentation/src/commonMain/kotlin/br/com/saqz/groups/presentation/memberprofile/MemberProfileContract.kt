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
    /** O próprio perfil não oferece denunciar nem bloquear. */
    val isSelf: Boolean = false,
    /** Quem olha bloqueou esta pessoa: a ação vira "Desbloquear". */
    val blocked: Boolean = false,
) {
    /** Denunciar e bloquear só aparecem com o perfil carregado e de outra pessoa. */
    val moderationVisible: Boolean get() = !loading && error == null && !isSelf && name.isNotBlank()
}

sealed interface MemberProfileIntent {
    data object Retry : MemberProfileIntent
}

sealed interface MemberProfileEffect
