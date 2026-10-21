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
    private var loadInFlight = false

    // "A aba reapareceu" que chegou com uma carga em voo: o dado dessa carga pode ser anterior ao que motivou o
    // pedido (edição salva), então a recarga roda quando ela termina em vez de ser descartada.
    private var refreshPending = false

    init {
        load()
    }

    override fun handleIntent(intent: OwnProfileIntent) {
        when (intent) {
            OwnProfileIntent.Refresh -> load()
            OwnProfileIntent.Appeared -> appeared()
            OwnProfileIntent.EditData -> emit(OwnProfileEffect.OpenEditor)
            OwnProfileIntent.ChangePassword -> emit(OwnProfileEffect.OpenPasswordRecovery)
            OwnProfileIntent.DeleteAccount -> emit(OwnProfileEffect.OpenAccountDeletion)
            OwnProfileIntent.SignOut -> emit(OwnProfileEffect.SignedOut)
            OwnProfileIntent.OpenMyPlan -> emit(OwnProfileEffect.OpenMyPlan)
            OwnProfileIntent.OpenPrivacy -> emit(OwnProfileEffect.OpenPrivacy)
            OwnProfileIntent.OpenTerms -> emit(OwnProfileEffect.OpenTerms)
            OwnProfileIntent.OpenHelp -> emit(OwnProfileEffect.OpenHelp)
            OwnProfileIntent.OpenSettings -> emit(OwnProfileEffect.OpenSettings)

            OwnProfileIntent.OpenReceipts -> emit(OwnProfileEffect.OpenReceipts)
            OwnProfileIntent.OpenMonthlyPayments -> emit(OwnProfileEffect.OpenMonthlyPayments)

            OwnProfileIntent.OpenNotifications -> emit(OwnProfileEffect.OpenNotifications)

            is OwnProfileIntent.OpenGroup -> emit(OwnProfileEffect.OpenAthleteProfile(intent.groupId))
        }
    }

    /**
     * A aba reaparece (troca de aba, volta de filha, primeiro plano, edição salva): recarga por
     * baixo, sem o indicador do puxar-para-atualizar e sem tela de erro — falha mantém o perfil
     * que está na tela. Com carga em voo fica pendente e roda quando ela termina; sem conteúdo (erro)
     * carrega por inteiro.
     */
    private fun appeared() {
        if (isFirstAppearance()) return
        val current = state.value
        if (loadInFlight || current.isLoading) {
            refreshPending = true
            return
        }
        load(softRefresh = !current.loadError)
    }

    private fun load(softRefresh: Boolean = false) {
        val generation = ++loadGeneration
        loadInFlight = true
        if (!softRefresh) update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            try {
                fetchAndShow(generation, softRefresh)
            } finally {
                // Dentro da própria carga (e não num completion handler): o estado novo e o fim da carga são
                // observados juntos, sem janela em que o perfil já aparece mas o pedido de recarga ainda é descartado.
                if (generation == loadGeneration) finishLoad()
            }
        }
    }

    private fun finishLoad() {
        loadInFlight = false
        if (!refreshPending) return
        refreshPending = false
        load(softRefresh = !state.value.loadError)
    }

    private suspend fun fetchAndShow(generation: Int, softRefresh: Boolean) {
        val profileResult = gateway.bootstrap()
        val statsResult = gateway.stats()
        val athleteResult = gateway.athleteProfile()
        if (generation != loadGeneration) return

        if (profileResult is SaqzResult.Failure ||
            statsResult is SaqzResult.Failure ||
            athleteResult is SaqzResult.Failure
        ) {
            if (!softRefresh) update { it.copy(isLoading = false, loadError = true) }
            return
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
    // Integração futura (Fluxo 3 · 3j): substituir o papel pelo nível do cadastro do atleta quando o contrato o expuser.
    // O contrato atual traz `role`, não `level`; esta substituição é consciente e não inventa um nível.
    details = listOfNotNull(position?.toPositionLabel(), role.toRoleLabel())
        .joinToString(" · ")
        .takeIf(String::isNotBlank),
    membershipLabel = membershipType.toMembershipLabel(),
    isMonthly = membershipType.equals("MENSALISTA", ignoreCase = true),
    photoUrl = groupPhotoVersion?.let { "/api/groups/${groupId.value}/photo?v=$it" },
)

private fun String.toPositionLabel(): String? = when (uppercase()) {
    "LIBERO" -> "Líbero"
    "PONTA" -> "Ponteiro"
    "CENTRAL" -> "Central"
    "OPOSTO" -> "Oposto"
    "LEVANTADOR" -> "Levantador"
    else -> takeIf(String::isNotBlank)
}

private fun String.toRoleLabel(): String? = when (uppercase()) {
    "OWNER" -> "Organizador"
    "ADMIN" -> "Administrador"
    "ATHLETE" -> "Atleta"
    else -> takeIf(String::isNotBlank)
}

private fun String.toMembershipLabel(): String = when (uppercase()) {
    "MENSALISTA" -> "Mensalista"
    else -> "Avulso"
}
