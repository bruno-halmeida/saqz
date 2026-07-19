package br.com.saqz.groups.application.create

interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}

interface GroupCreationRepository {
    fun create(command: CreateGroupCommand): StoredGroup
}
