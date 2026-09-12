package br.com.saqz.groups.domain.group

import br.com.saqz.domain.GroupId

/** Read-only trial facts supplied by the app composition root without a feature dependency. */
data class GroupTrialAccessInfo(val endsAt: String?, val readOnly: Boolean, val isOwner: Boolean)

fun interface GroupTrialAccessPort {
    suspend fun read(groupId: GroupId): GroupTrialAccessInfo?
}
