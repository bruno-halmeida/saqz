package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.GroupGateway
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.MembershipDto
import br.com.saqz.groups.data.PersistedRoleDto
import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GroupActions(
    val canEditSettings: Boolean,
    val canManageRoles: Boolean,
    val canManageInvite: Boolean,
)

enum class GroupAdministrationError {
    FORBIDDEN,
    NOT_FOUND,
    UNAVAILABLE,
}

data class GroupAdministrationState(
    val group: VersionedGroupDto? = null,
    val memberships: List<MembershipDto> = emptyList(),
    val actions: GroupActions = GroupActions(false, false, false),
    val isLoading: Boolean = false,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val versionConflict: Boolean = false,
    val error: GroupAdministrationError? = null,
)

sealed interface GroupAdministrationIntent {
    data class SetGroup(val group: VersionedGroupDto) : GroupAdministrationIntent

    data class CreateGroup(
        val requestId: String,
        val name: String,
        val timeZone: String,
    ) : GroupAdministrationIntent

    data class UpdateSettings(val name: String, val timeZone: String) : GroupAdministrationIntent

    data object LoadMemberships : GroupAdministrationIntent

    data class ChangeRole(val userId: String, val role: PersistedRoleDto) : GroupAdministrationIntent
}

class GroupAdministrationStateMachine(
    private val groups: GroupGateway,
    private val roles: RolesInvitesGateway,
    private val scope: CoroutineScope,
    private val selectCreatedGroup: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(GroupAdministrationState())
    val state: StateFlow<GroupAdministrationState> = mutableState.asStateFlow()

    fun onIntent(intent: GroupAdministrationIntent) {
        when (intent) {
            is GroupAdministrationIntent.SetGroup -> setGroup(intent.group)
            is GroupAdministrationIntent.CreateGroup -> createGroup(intent.requestId, intent.name, intent.timeZone)
            is GroupAdministrationIntent.UpdateSettings -> updateSettings(intent.name, intent.timeZone)
            GroupAdministrationIntent.LoadMemberships -> loadMemberships()
            is GroupAdministrationIntent.ChangeRole -> changeRole(intent.userId, intent.role)
        }
    }

    private fun setGroup(group: VersionedGroupDto) {
        mutableState.value = GroupAdministrationState(
            group = group,
            actions = actionsFor(group.group.role),
        )
    }

    private fun createGroup(requestId: String, name: String, timeZone: String) {
        if (!begin()) return
        scope.launch {
            when (val result = groups.create(requestId, name, timeZone)) {
                is NetworkResult.Success -> {
                    finish()
                    selectCreatedGroup(result.value.id)
                }
                is NetworkResult.Failure -> fail(result.error)
            }
        }
    }

    private fun updateSettings(name: String, timeZone: String) {
        val current = mutableState.value
        val group = current.group ?: return
        if (!current.actions.canEditSettings || !begin()) return
        scope.launch {
            when (val result = groups.update(group.group.id, group.etag, name, timeZone)) {
                is NetworkResult.Success -> setUpdatedGroup(result.value)
                is NetworkResult.Failure -> {
                    if (result.error.isProblem(409, "VERSION_CONFLICT")) reloadAfterConflict(group.group.id)
                    else fail(result.error)
                }
            }
        }
    }

    private fun loadMemberships() {
        val current = mutableState.value
        val groupId = current.group?.group?.id ?: return
        if (!current.actions.canManageRoles || !begin()) return
        scope.launch {
            when (val result = roles.listMemberships(groupId)) {
                is NetworkResult.Success -> mutableState.value = mutableState.value.copy(
                    memberships = result.value,
                    isLoading = false,
                )
                is NetworkResult.Failure -> fail(result.error)
            }
        }
    }

    private fun changeRole(userId: String, role: PersistedRoleDto) {
        val current = mutableState.value
        val groupId = current.group?.group?.id ?: return
        if (!current.actions.canManageRoles || !begin()) return
        scope.launch {
            when (val result = roles.changeRole(groupId, userId, role)) {
                is NetworkResult.Success -> {
                    val updated = mutableState.value.memberships.map { member ->
                        if (member.userId == result.value.userId) result.value else member
                    }
                    mutableState.value = mutableState.value.copy(memberships = updated, isLoading = false)
                }
                is NetworkResult.Failure -> fail(result.error)
            }
        }
    }

    private suspend fun reloadAfterConflict(groupId: String) {
        when (val fresh = groups.read(groupId)) {
            is NetworkResult.Success -> mutableState.value = mutableState.value.copy(
                group = fresh.value,
                actions = actionsFor(fresh.value.group.role),
                isLoading = false,
                versionConflict = true,
            )
            is NetworkResult.Failure -> fail(fresh.error)
        }
    }

    private fun setUpdatedGroup(group: VersionedGroupDto) {
        mutableState.value = mutableState.value.copy(
            group = group,
            actions = actionsFor(group.group.role),
            isLoading = false,
        )
    }

    private fun begin(): Boolean {
        val current = mutableState.value
        if (current.isLoading) return false
        mutableState.value = current.copy(
            isLoading = true,
            fieldErrors = emptyMap(),
            versionConflict = false,
            error = null,
        )
        return true
    }

    private fun finish() {
        mutableState.value = mutableState.value.copy(isLoading = false)
    }

    private fun fail(error: NetworkError) {
        val problem = (error as? NetworkError.ApiProblemError)?.problem
        mutableState.value = mutableState.value.copy(
            isLoading = false,
            fieldErrors = problem?.fieldErrors.orEmpty(),
            error = when {
                problem?.status == 403 -> GroupAdministrationError.FORBIDDEN
                problem?.status == 404 -> GroupAdministrationError.NOT_FOUND
                problem?.status == 400 -> null
                else -> GroupAdministrationError.UNAVAILABLE
            },
        )
    }
}

private fun actionsFor(role: GroupRoleDto): GroupActions = when (role) {
    GroupRoleDto.OWNER -> GroupActions(true, true, true)
    GroupRoleDto.ADMIN -> GroupActions(true, false, true)
    GroupRoleDto.ATHLETE -> GroupActions(false, false, false)
}

private fun NetworkError.isProblem(status: Int, code: String): Boolean =
    br.com.saqz.network.isProblem(this, status, code)
