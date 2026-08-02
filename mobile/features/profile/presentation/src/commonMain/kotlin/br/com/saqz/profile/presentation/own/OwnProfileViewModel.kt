package br.com.saqz.profile.presentation.own

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.AthleteMembership
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.ProfileStats
import br.com.saqz.profile.domain.ProfileUser
import kotlinx.coroutines.launch

class OwnProfileViewModel(
    private val gateway: ProfileGateway,
    initialState: OwnProfileState = OwnProfileState(),
) : MviViewModel<OwnProfileState, OwnProfileIntent, OwnProfileEffect>(initialState) {

    private var loadGeneration = 0

    init {
        load()
    }

    override fun onIntent(intent: OwnProfileIntent) {
        when (intent) {
            OwnProfileIntent.Refresh -> load()
            OwnProfileIntent.EditData -> emit(OwnProfileEffect.OpenEditor)
            OwnProfileIntent.ChangePassword -> emit(OwnProfileEffect.OpenPasswordRecovery)
            OwnProfileIntent.SignOut -> emit(OwnProfileEffect.SignedOut)
            OwnProfileIntent.OpenSettings -> {
                // TODO(Fluxo 7 · 7c): abrir a tela de notificações quando ela existir.
            }

            OwnProfileIntent.OpenMonthlyPayments -> {
                // TODO(Fluxo 5): abrir mensalidades quando o fluxo de cobrança existir.
            }

            OwnProfileIntent.OpenNotifications -> {
                // TODO(Fluxo 7 · 7c): abrir notificações quando a tela existir.
            }

            is OwnProfileIntent.OpenGroup -> {
                // TODO(Fluxo 3 · 3j): abrir o perfil do atleta no grupo.
            }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            val profileResult = gateway.bootstrap()
            val statsResult = gateway.stats()
            val athleteResult = gateway.athleteProfile()
            if (generation != loadGeneration) return@launch

            if (profileResult is SaqzResult.Failure ||
                statsResult is SaqzResult.Failure ||
                athleteResult is SaqzResult.Failure
            ) {
                update { it.copy(isLoading = false, loadError = true) }
                return@launch
            }

            val profile = (profileResult as SaqzResult.Success).value
            val stats = (statsResult as SaqzResult.Success).value
            val athleteProfile = (athleteResult as SaqzResult.Success).value
            update {
                it.copy(
                    isLoading = false,
                    loadError = false,
                    user = profile.user.toOwnProfileUserUi(),
                    stats = stats.toOwnProfileStatsUi(),
                    groups = athleteProfile.memberships
                        .filter(AthleteMembership::active)
                        .map(AthleteMembership::toOwnProfileGroupUi),
                )
            }
        }
    }
}

internal fun ProfileUser.toOwnProfileUserUi() = OwnProfileUserUi(
    displayName = displayName,
    subtitle = profileSubtitle(nickname, city),
    photoUrl = photoUrl,
)

internal fun ProfileStats.toOwnProfileStatsUi() = OwnProfileStatsUi(
    gamesLabel = games.toString(),
    attendanceLabel = attendanceRate?.let { "$it%" },
    groupsLabel = groups.toString(),
)

internal fun AthleteMembership.toOwnProfileGroupUi() = OwnProfileGroupUi(
    id = groupId.value,
    name = groupName,
    details = listOfNotNull(position?.toPositionLabel(), role.toRoleLabel())
        .joinToString(" · ")
        .takeIf(String::isNotBlank),
    membershipLabel = membershipType.toMembershipLabel(),
    isMonthly = membershipType.equals("MENSALISTA", ignoreCase = true),
)

private fun String.toPositionLabel(): String? = when (uppercase()) {
    "LIBERO" -> "Líbero"
    "PONTA" -> "Ponta"
    "CENTRAL" -> "Central"
    "OPOSTO" -> "Oposto"
    "LEVANTADOR" -> "Levantador"
    else -> takeIf(String::isNotBlank)
}

private fun String.toRoleLabel(): String? = when (uppercase()) {
    "OWNER" -> "Organizador"
    "ADMIN" -> "Admin"
    "ATHLETE" -> "Atleta"
    else -> takeIf(String::isNotBlank)
}

private fun String.toMembershipLabel(): String = when (uppercase()) {
    "MENSALISTA" -> "Mensalista"
    else -> "Avulso"
}
