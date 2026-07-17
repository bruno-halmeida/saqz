package br.com.saqz.access.presentation

import br.com.saqz.access.data.GroupGateway
import br.com.saqz.access.data.VersionedGroupDto
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.SessionDto
import br.com.saqz.network.SessionMembershipDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GroupSelectionState {
    data object NoGroup : GroupSelectionState

    data class Selector(val memberships: List<SessionMembershipDto>) : GroupSelectionState

    data class Loading(val groupId: String) : GroupSelectionState

    data class Selected(val group: VersionedGroupDto) : GroupSelectionState

    data class LoadError(val groupId: String) : GroupSelectionState
}

sealed interface GroupSelectionIntent {
    data class Reconcile(val session: SessionDto) : GroupSelectionIntent

    data class Select(val groupId: String) : GroupSelectionIntent

    data object Retry : GroupSelectionIntent
}

class GroupSelectionStateMachine(
    private val localState: LocalAccessStatePort,
    private val groups: GroupGateway,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<GroupSelectionState>(GroupSelectionState.NoGroup)
    val state: StateFlow<GroupSelectionState> = mutableState.asStateFlow()
    private var memberships: List<SessionMembershipDto> = emptyList()
    private var operationGeneration = 0L

    fun onIntent(intent: GroupSelectionIntent) {
        when (intent) {
            is GroupSelectionIntent.Reconcile -> reconcile(intent.session)
            is GroupSelectionIntent.Select -> select(intent.groupId)
            GroupSelectionIntent.Retry -> retry()
        }
    }

    private fun reconcile(session: SessionDto) {
        val generation = nextGeneration()
        memberships = session.memberships
        when (memberships.size) {
            0 -> reconcileEmpty(generation)
            1 -> selectInternal(memberships.single().groupId, generation)
            else -> restoreOrSelect(generation)
        }
    }

    private fun select(groupId: String) {
        if (mutableState.value is GroupSelectionState.Loading) return
        if (memberships.none { it.groupId == groupId }) return
        selectInternal(groupId, nextGeneration())
    }

    private fun retry() {
        val error = mutableState.value as? GroupSelectionState.LoadError ?: return
        selectInternal(error.groupId, nextGeneration())
    }

    private fun reconcileEmpty(generation: Long) {
        localState.readSelectedGroupId(valueCallback { result ->
            if (generation != operationGeneration) return@valueCallback
            if ((result as? ValueResult.Success)?.value != null) writeSelection(null)
            mutableState.value = GroupSelectionState.NoGroup
        })
    }

    private fun restoreOrSelect(generation: Long) {
        localState.readSelectedGroupId(valueCallback { result ->
            if (generation != operationGeneration) return@valueCallback
            val stored = (result as? ValueResult.Success)?.value
            when {
                stored == null -> mutableState.value = GroupSelectionState.Selector(memberships)
                memberships.any { it.groupId == stored } -> selectInternal(stored, generation)
                else -> {
                    writeSelection(null)
                    mutableState.value = GroupSelectionState.Selector(memberships)
                }
            }
        })
    }

    private fun selectInternal(groupId: String, generation: Long) {
        mutableState.value = GroupSelectionState.Loading(groupId)
        writeSelection(groupId)
        scope.launch {
            val result = groups.read(groupId)
            if (generation != operationGeneration) return@launch
            mutableState.value = when (result) {
                is NetworkResult.Success -> GroupSelectionState.Selected(result.value)
                is NetworkResult.Failure -> GroupSelectionState.LoadError(groupId)
            }
        }
    }

    private fun nextGeneration(): Long = ++operationGeneration

    private fun writeSelection(value: String?) {
        localState.writeSelectedGroupId(value, resultCallback {})
    }

    private fun valueCallback(block: (ValueResult) -> Unit) = object : ValueCallback {
        override fun complete(result: ValueResult) = block(result)
    }

    private fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
        override fun complete(result: OperationResult) = block(result)
    }
}
