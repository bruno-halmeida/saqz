package br.com.saqz.groups.domain

enum class GroupAction {
    READ_GROUP,
    UPDATE_SETTINGS,
    MANAGE_INVITE,
    MANAGE_ATTENDANCE_SHARE,
    MANAGE_ROLES,
    MANAGE_ATHLETES,
}

sealed interface GroupAccessDecision {
    data object Allowed : GroupAccessDecision

    data object Forbidden : GroupAccessDecision

    data object GroupNotFound : GroupAccessDecision
}

class GroupAccessPolicy {
    fun authorize(role: GroupRole?, action: GroupAction): GroupAccessDecision {
        if (role == null) return GroupAccessDecision.GroupNotFound

        val allowed = when (action) {
            GroupAction.READ_GROUP -> true
            GroupAction.UPDATE_SETTINGS,
            GroupAction.MANAGE_INVITE,
            GroupAction.MANAGE_ATTENDANCE_SHARE,
            GroupAction.MANAGE_ATHLETES,
            -> role == GroupRole.OWNER || role == GroupRole.ADMIN
            GroupAction.MANAGE_ROLES -> role == GroupRole.OWNER
        }
        return if (allowed) GroupAccessDecision.Allowed else GroupAccessDecision.Forbidden
    }
}
