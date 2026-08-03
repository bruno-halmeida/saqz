package br.com.saqz.groups.presentation.list

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch

class GroupListViewModel(
    private val athleteGateway: AthleteGateway,
    private val groupGateway: GroupGateway,
    private val entitlement: GroupCreationEntitlement,
) : MviViewModel<GroupListState, GroupListIntent, GroupListEffect>(GroupListState()) {

    private var loadGeneration = 0

    // O segundo toque enquanto a consulta do plano está em voo é descartado — rotear duas
    // vezes para a mesma decisão só piscaria a tela.
    private var checkingPlan = false

    init {
        load()
    }

    override fun onIntent(intent: GroupListIntent) {
        when (intent) {
            is GroupListIntent.OpenGroup -> emit(GroupListEffect.OpenGroup(intent.id))
            GroupListIntent.CreateGroup -> checkCreateGroup()
            is GroupListIntent.AcceptInvite -> dismissInvite(intent.id)
            is GroupListIntent.DeclineInvite -> dismissInvite(intent.id)
            GroupListIntent.Retry -> load()
        }
    }

    private fun checkCreateGroup() {
        if (checkingPlan) return
        checkingPlan = true
        viewModelScope.launch {
            try {
                emit(
                    if (entitlement.canCreateGroup()) GroupListEffect.OpenCreateGroup
                    else GroupListEffect.OpenPlans,
                )
            } finally {
                checkingPlan = false
            }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            when (val result = athleteGateway.ownProfile()) {
                is SaqzResult.Failure -> showFailure(generation, result.error.toUiError())
                is SaqzResult.Success -> {
                    val memberships = result.value.memberships.filter(OwnAthleteMembership::active)
                    val cards = memberships.map { membership ->
                        // ponytail: N+1 por grupo; agregado quando houver endpoint de lista.
                        groupGateway.read(membership.groupId)
                    }
                    if (generation != loadGeneration) return@launch
                    val failure = cards.firstNotNullOfOrNull { card ->
                        (card as? SaqzResult.Failure)?.error?.toUiError()
                    }
                    if (failure != null) {
                        showFailure(generation, failure)
                    } else {
                        update {
                            it.copy(
                                isLoading = false,
                                loadFailed = false,
                                error = null,
                                groups = memberships.zip(cards).map { (membership, card) ->
                                    val group = (card as SaqzResult.Success).value.group
                                    group.toCard(membership)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun dismissInvite(id: String) {
        if (state.value.invite?.id != id) return
        update { it.copy(invite = null) }
    }
}

private fun Group.toCard(membership: OwnAthleteMembership) = GroupCardUi(
    id = id.value,
    name = name,
    meta = listOfNotNull(
        profile?.modality?.label(),
        profile?.city?.takeIf(String::isNotBlank),
    ).ifEmpty { listOf("Grupo") }.joinToString(" · "),
    modality = profile?.modality?.toPresentation() ?: br.com.saqz.groups.model.GroupModality.COURT_VOLLEYBALL,
    isAdmin = membership.role != br.com.saqz.groups.domain.group.GroupRole.ATHLETE,
    // Group photo is binary and has no URL in the group read model; the avatar falls back
    // to the modality glyph until the image-loader flow consumes GroupPhotoGateway.
    nextGame = null,
)

private fun GroupModality.label(): String = when (this) {
    GroupModality.COURT_VOLLEYBALL -> "Quadra"
    GroupModality.BEACH_VOLLEYBALL -> "Areia"
    GroupModality.FOOTVOLLEY -> "Futevôlei"
}

private fun GroupModality.toPresentation() = when (this) {
    GroupModality.COURT_VOLLEYBALL -> br.com.saqz.groups.model.GroupModality.COURT_VOLLEYBALL
    GroupModality.BEACH_VOLLEYBALL -> br.com.saqz.groups.model.GroupModality.BEACH_VOLLEYBALL
    GroupModality.FOOTVOLLEY -> br.com.saqz.groups.model.GroupModality.FOOTVOLLEY
}
