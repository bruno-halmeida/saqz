package br.com.saqz.groups.application.create

import java.util.UUID

interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}

interface GroupCreationRepository {
    fun findByCreationKey(ownerUserId: UUID, creationKey: UUID): StoredGroup?

    fun countOwnedGroups(ownerUserId: UUID): Int

    fun create(command: CreateGroupCommand): StoredGroup
}
