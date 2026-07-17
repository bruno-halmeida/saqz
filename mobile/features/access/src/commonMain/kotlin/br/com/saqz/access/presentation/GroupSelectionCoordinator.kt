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

class GroupSelectionCoordinator(
    private val localState: LocalAccessStatePort,
    private val groups: GroupGateway,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<GroupSelectionState>(GroupSelectionState.NoGroup)
    val state: StateFlow<GroupSelectionState> = mutableState.asStateFlow()
    private var memberships: List<SessionMembershipDto> = emptyList()

    fun reconcile(session: SessionDto) {
        memberships = session.memberships
        when (memberships.size) {
            0 -> reconcileEmpty()
            1 -> selectInternal(memberships.single().groupId)
            else -> restoreOrSelect()
        }
    }

    fun select(groupId: String) {
        if (mutableState.value is GroupSelectionState.Loading) return
        if (memberships.none { it.groupId == groupId }) return
        selectInternal(groupId)
    }

    fun retry() {
        val error = mutableState.value as? GroupSelectionState.LoadError ?: return
        selectInternal(error.groupId)
    }

    private fun reconcileEmpty() {
        localState.readSelectedGroupId(valueCallback { result ->
            if ((result as? ValueResult.Success)?.value != null) writeSelection(null)
            mutableState.value = GroupSelectionState.NoGroup
        })
    }

    private fun restoreOrSelect() {
        localState.readSelectedGroupId(valueCallback { result ->
            val stored = (result as? ValueResult.Success)?.value
            when {
                stored == null -> mutableState.value = GroupSelectionState.Selector(memberships)
                memberships.any { it.groupId == stored } -> selectInternal(stored)
                else -> {
                    writeSelection(null)
                    mutableState.value = GroupSelectionState.Selector(memberships)
                }
            }
        })
    }

    private fun selectInternal(groupId: String) {
        mutableState.value = GroupSelectionState.Loading(groupId)
        writeSelection(groupId)
        scope.launch {
            mutableState.value = when (val result = groups.read(groupId)) {
                is NetworkResult.Success -> GroupSelectionState.Selected(result.value)
                is NetworkResult.Failure -> GroupSelectionState.LoadError(groupId)
            }
        }
    }

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
