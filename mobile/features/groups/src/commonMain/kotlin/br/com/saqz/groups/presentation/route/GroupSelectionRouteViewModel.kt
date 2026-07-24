package br.com.saqz.groups.presentation.route

import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.ui.GroupOnboardingIntent
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** GroupSelectionRouteViewModel requests navigation via a typed effect only; it never imports Nav3 UI. */
sealed interface GroupSelectionRouteEffect {
    data object OpenCreateGroup : GroupSelectionRouteEffect
}

/**
 * Reusable Groups selection route adapter (T12): every Setup/Selector/Loading/
 * LoadError entry constructs its own instance projecting the shared, singleton
 * [GroupSelectionStateMachine] -- selection business logic remains in that
 * machine (GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05).
 */
class GroupSelectionRouteViewModel(
    private val coordinator: GroupSelectionStateMachine,
) : MviViewModel<GroupSelectionState, GroupOnboardingIntent, GroupSelectionRouteEffect>(coordinator.state.value) {

    init {
        coordinator.state
            .onEach { current -> update { current } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: GroupOnboardingIntent) {
        when (intent) {
            is GroupOnboardingIntent.Select -> coordinator.onIntent(GroupSelectionIntent.Select(intent.groupId))
            GroupOnboardingIntent.Retry -> coordinator.onIntent(GroupSelectionIntent.Retry)
            GroupOnboardingIntent.OpenCreateGroup -> emit(GroupSelectionRouteEffect.OpenCreateGroup)
        }
    }
}
