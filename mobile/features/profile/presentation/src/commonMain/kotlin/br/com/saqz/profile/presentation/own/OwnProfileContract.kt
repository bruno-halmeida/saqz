package br.com.saqz.profile.presentation.own

import androidx.compose.runtime.Immutable

@Immutable
data class OwnProfileState(
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    /** Puxar para atualizar em andamento (GER-002); o perfil continua na tela. */
    val isRefreshing: Boolean = false,
    /** O último puxar falhou: o perfil fica e o aviso aparece. */
    val refreshFailed: Boolean = false,
    val user: OwnProfileUserUi? = null,
    val stats: OwnProfileStatsUi? = null,
    val groups: List<OwnProfileGroupUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !isLoading && !loadError && groups.isEmpty()

    val showAttendance: Boolean
        get() = stats?.attendanceLabel != null
}

@Immutable
data class OwnProfileUserUi(
    val displayName: String,
    val subtitle: String?,
    val photoUrl: String?,
)

@Immutable
data class OwnProfileStatsUi(
    val gamesLabel: String,
    val attendanceLabel: String?,
    val groupsLabel: String,
)

@Immutable
data class OwnProfileGroupUi(
    val id: String,
    val name: String,
    val details: String?,
    val membershipLabel: String,
    val isMonthly: Boolean,
    /** Foto do grupo, a mesma da lista Grupos; nula sem foto (fica nas iniciais). */
    val photoUrl: String? = null,
)

sealed interface OwnProfileIntent {
    data object Refresh : OwnProfileIntent

    /** A aba reaparece: recarga por baixo, sem indicador nem tela de erro. */
    data object Appeared : OwnProfileIntent

    /** Puxar para atualizar (GER-002): recarga por baixo com a bola na tela e aviso se falhar. */
    data object PullRefresh : OwnProfileIntent
    data object DismissRefreshFailed : OwnProfileIntent
    data object EditData : OwnProfileIntent
    data object OpenPrivacy : OwnProfileIntent
    data object OpenTerms : OwnProfileIntent
    data object OpenHelp : OwnProfileIntent
    data object OpenSettings : OwnProfileIntent
    data object OpenMyPlan : OwnProfileIntent
    data object OpenReceipts : OwnProfileIntent
    data object OpenMonthlyPayments : OwnProfileIntent
    data object OpenNotifications : OwnProfileIntent
    data object ChangePassword : OwnProfileIntent
    data object DeleteAccount : OwnProfileIntent
    data object SignOut : OwnProfileIntent
    data class OpenGroup(val groupId: String) : OwnProfileIntent
}

sealed interface OwnProfileEffect {
    data object OpenEditor : OwnProfileEffect
    data object OpenPasswordRecovery : OwnProfileEffect
    data object OpenMyPlan : OwnProfileEffect
    data class OpenAthleteProfile(val groupId: String) : OwnProfileEffect
    data object OpenReceipts : OwnProfileEffect
    data object OpenMonthlyPayments : OwnProfileEffect
    data object OpenPrivacy : OwnProfileEffect
    data object OpenTerms : OwnProfileEffect

    /** "Ajuda e contato": o e-mail da equipe, publicado dentro do app (App Store 1.2). */
    data object OpenHelp : OwnProfileEffect
    data object OpenSettings : OwnProfileEffect
    data object OpenNotifications : OwnProfileEffect
    data object OpenAccountDeletion : OwnProfileEffect
    data object SignedOut : OwnProfileEffect
}

internal fun profileSubtitle(nickname: String?, city: String?): String? =
    listOfNotNull(nickname?.takeIf(String::isNotBlank), city?.takeIf(String::isNotBlank))
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
