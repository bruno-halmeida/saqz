package br.com.saqz.sharedkernel.group

import java.util.UUID

data class GroupAdministrator(val userId: UUID, val displayName: String, val groupNames: List<String>)

interface GroupAdministrationDirectory {
    fun administrators(ownerUserId: UUID): List<GroupAdministrator>
    fun ownerOf(groupId: UUID): UUID?
    fun isAdministrator(ownerUserId: UUID, userId: UUID): Boolean
}

/** Called inside the transaction that removes an administrator's authority. */
fun interface GroupAdministrationRevocation {
    fun revoked(groupId: UUID, userId: UUID)
}
