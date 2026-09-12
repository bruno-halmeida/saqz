package br.com.saqz.sharedkernel.group

import java.util.UUID

interface GroupAdministrationDirectory {
    fun ownerOf(groupId: UUID): UUID?
    fun isAdministrator(ownerUserId: UUID, userId: UUID): Boolean
}

/** Called inside the transaction that removes an administrator's authority. */
fun interface GroupAdministrationRevocation {
    fun revoked(groupId: UUID, userId: UUID)
}
