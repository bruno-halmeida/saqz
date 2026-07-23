package br.com.saqz.groups.presentation.athlete

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.DeferredInviteStateMachine
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Immutable
data class PositionOnboardingState(
    val visible: Boolean = false,
    val groupId: String? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
)

sealed interface PositionOnboardingIntent {
    data class Choose(val position: AthletePosition) : PositionOnboardingIntent

    data object Skip : PositionOnboardingIntent
}

sealed interface PositionOnboardingEffect

/**
 * Skippable position step after an invite redeem lands the athlete in the group.
 * Dismissal is session-scoped: a fresh redeem re-arms it, process death simply skips it.
 */
class PositionOnboardingViewModel(
    invites: DeferredInviteStateMachine,
    selection: GroupSelectionStateMachine,
    private val athletes: AthleteGateway,
) : MviViewModel<PositionOnboardingState, PositionOnboardingIntent, PositionOnboardingEffect>(
    PositionOnboardingState(),
) {
    private var dismissed = false

    init {
        combine(invites.state, selection.state) { invite, selected ->
            val groupId = (selected as? GroupSelectionState.Selected)?.group?.group?.id?.value
            PositionOnboardingState(
                visible = !dismissed && invite.redeemedRole == GroupRole.ATHLETE && groupId != null,
                groupId = groupId,
            )
        }
            .onEach { computed ->
                update { current ->
                    if (current.saving) current.copy(groupId = computed.groupId ?: current.groupId)
                    else computed.copy(failed = current.failed && computed.visible)
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: PositionOnboardingIntent) {
        when (intent) {
            is PositionOnboardingIntent.Choose -> choose(intent.position)
            PositionOnboardingIntent.Skip -> dismiss()
        }
    }

    private fun choose(position: AthletePosition) {
        val current = state.value
        val groupId = current.groupId ?: return
        if (current.saving) return
        update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            when (athletes.updateOwnPosition(GroupId(groupId), position)) {
                is SaqzResult.Success -> dismiss()
                is SaqzResult.Failure -> update { it.copy(saving = false, failed = true) }
            }
        }
    }

    private fun dismiss() {
        dismissed = true
        update { it.copy(visible = false, saving = false, failed = false) }
    }
}
