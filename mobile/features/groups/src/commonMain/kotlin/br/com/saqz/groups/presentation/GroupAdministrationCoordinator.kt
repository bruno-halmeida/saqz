package br.com.saqz.groups.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
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
    val group: VersionedGroup? = null,
    val memberships: List<GroupMembership> = emptyList(),
    val actions: GroupActions = GroupActions(false, false, false),
    val isLoading: Boolean = false,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val versionConflict: Boolean = false,
    val error: GroupAdministrationError? = null,
)

sealed interface GroupAdministrationIntent {
    data class SetGroup(val group: VersionedGroup) : GroupAdministrationIntent

    data class CreateGroup(
        val requestId: String,
        val name: String,
        val timeZone: String,
    ) : GroupAdministrationIntent

    data class UpdateSettings(val name: String, val timeZone: String) : GroupAdministrationIntent

    data object LoadMemberships : GroupAdministrationIntent

    data class ChangeRole(val userId: String, val role: AssignableGroupRole) : GroupAdministrationIntent
}

class GroupAdministrationStateMachine(
    private val groups: GroupGateway,
    private val roles: GroupMembershipGateway,
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

    private fun setGroup(group: VersionedGroup) {
        mutableState.value = GroupAdministrationState(
            group = group,
            actions = actionsFor(group.group.role),
        )
    }

    private fun createGroup(requestId: String, name: String, timeZone: String) {
        if (!begin()) return
        scope.launch {
            val command = CreateGroupCommand(requestId, name, GroupTimeZone(timeZone))
            when (val result = groups.create(command)) {
                is SaqzResult.Success -> {
                    finish()
                    selectCreatedGroup(result.value.id.value)
                }
                is SaqzResult.Failure -> failGroup(result.error)
            }
        }
    }

    private fun updateSettings(name: String, timeZone: String) {
        val current = mutableState.value
        val group = current.group ?: return
        if (!current.actions.canEditSettings || !begin()) return
        scope.launch {
            val command = UpdateGroupSettingsCommand(
                group.group.id,
                group.versionToken,
                name,
                GroupTimeZone(timeZone),
            )
            when (val result = groups.update(command)) {
                is SaqzResult.Success -> setUpdatedGroup(result.value)
                is SaqzResult.Failure -> {
                    if (result.error is GroupProfileError.Conflict) reloadAfterConflict(group.group.id)
                    else failGroup(result.error)
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
                is SaqzResult.Success -> mutableState.value = mutableState.value.copy(
                    memberships = result.value,
                    isLoading = false,
                )
                is SaqzResult.Failure -> failMembership(result.error)
            }
        }
    }

    private fun changeRole(userId: String, role: AssignableGroupRole) {
        val current = mutableState.value
        val groupId = current.group?.group?.id ?: return
        if (!current.actions.canManageRoles || !begin()) return
        scope.launch {
            when (val result = roles.changeRole(ChangeMembershipRoleCommand(groupId, userId, role))) {
                is SaqzResult.Success -> {
                    val updated = mutableState.value.memberships.map { member ->
                        if (member.userId == result.value.userId) result.value else member
                    }
                    mutableState.value = mutableState.value.copy(memberships = updated, isLoading = false)
                }
                is SaqzResult.Failure -> failMembership(result.error)
            }
        }
    }

    private suspend fun reloadAfterConflict(groupId: GroupId) {
        when (val fresh = groups.read(groupId)) {
            is SaqzResult.Success -> mutableState.value = mutableState.value.copy(
                group = fresh.value,
                actions = actionsFor(fresh.value.group.role),
                isLoading = false,
                versionConflict = true,
            )
            is SaqzResult.Failure -> failGroup(fresh.error)
        }
    }

    private fun setUpdatedGroup(group: VersionedGroup) {
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

    private fun failMembership(error: GroupMembershipError) {
        when (error) {
            is GroupMembershipError.Validation -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                fieldErrors = error.details.fieldMessages,
                error = null,
            )
            GroupMembershipError.InvalidOrExpired,
            is GroupMembershipError.AttemptLimit,
            -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                fieldErrors = emptyMap(),
                error = GroupAdministrationError.UNAVAILABLE,
            )
            is GroupMembershipError.DataFailure -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                fieldErrors = emptyMap(),
                error = when (error.error) {
                    DataError.Forbidden -> GroupAdministrationError.FORBIDDEN
                    DataError.NotFound -> GroupAdministrationError.NOT_FOUND
                    else -> GroupAdministrationError.UNAVAILABLE
                },
            )
        }
    }

    private fun failGroup(error: GroupProfileError) {
        when (error) {
            is GroupProfileError.Validation -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                fieldErrors = error.details.fieldMessages,
                error = null,
            )
            is GroupProfileError.Conflict -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                versionConflict = true,
            )
            is GroupProfileError.DataFailure -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                error = when (error.error) {
                    DataError.Forbidden -> GroupAdministrationError.FORBIDDEN
                    DataError.NotFound -> GroupAdministrationError.NOT_FOUND
                    else -> GroupAdministrationError.UNAVAILABLE
                },
            )
        }
    }
}

private fun actionsFor(role: GroupRole): GroupActions = when (role) {
    GroupRole.OWNER -> GroupActions(true, true, true)
    GroupRole.ADMIN -> GroupActions(true, false, true)
    GroupRole.ATHLETE -> GroupActions(false, false, false)
}
