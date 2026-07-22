package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.GroupGateway
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GroupSelectionState {
    data object NoGroup : GroupSelectionState

    data class Selector(val memberships: List<GroupSelectionMembership>) : GroupSelectionState

    data class Loading(val groupId: String) : GroupSelectionState

    data class Selected(val group: VersionedGroupDto) : GroupSelectionState

    data class LoadError(val groupId: String) : GroupSelectionState
}

sealed interface GroupSelectionIntent {
    data class Reconcile(val memberships: List<GroupSelectionMembership>) : GroupSelectionIntent

    data class Select(val groupId: String) : GroupSelectionIntent

    data object Retry : GroupSelectionIntent
}

data class GroupSelectionMembership(
    val groupId: String,
    val groupName: String,
    val role: String,
)

class GroupSelectionStateMachine(
    private val localState: LocalGroupStatePort,
    private val groups: GroupGateway,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<GroupSelectionState>(GroupSelectionState.NoGroup)
    val state: StateFlow<GroupSelectionState> = mutableState.asStateFlow()
    private var memberships: List<GroupSelectionMembership> = emptyList()
    private var operationGeneration = 0L

    fun onIntent(intent: GroupSelectionIntent) {
        when (intent) {
            is GroupSelectionIntent.Reconcile -> reconcile(intent.memberships)
            is GroupSelectionIntent.Select -> select(intent.groupId)
            GroupSelectionIntent.Retry -> retry()
        }
    }

    private fun reconcile(newMemberships: List<GroupSelectionMembership>) {
        val generation = nextGeneration()
        memberships = newMemberships
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
            if ((result as? GroupValueResult.Success)?.value != null) writeSelection(null)
            mutableState.value = GroupSelectionState.NoGroup
        })
    }

    private fun restoreOrSelect(generation: Long) {
        localState.readSelectedGroupId(valueCallback { result ->
            if (generation != operationGeneration) return@valueCallback
            val stored = (result as? GroupValueResult.Success)?.value
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

    private fun valueCallback(block: (GroupValueResult) -> Unit) = object : GroupValueCallback {
        override fun complete(result: GroupValueResult) = block(result)
    }

    private fun resultCallback(block: (GroupOperationResult) -> Unit) = object : GroupResultCallback {
        override fun complete(result: GroupOperationResult) = block(result)
    }
}
