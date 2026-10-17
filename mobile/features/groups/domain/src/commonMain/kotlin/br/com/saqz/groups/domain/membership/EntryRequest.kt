package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

data class GroupEntryRequest(
    val userId: String,
    val displayName: String,
    val requestedAt: String,
)

sealed interface EntryRequestError : SaqzError {
    data class DataFailure(val error: DataError) : EntryRequestError
}

interface GroupEntryRequestGateway {
    suspend fun list(groupId: GroupId): SaqzResult<List<GroupEntryRequest>, EntryRequestError>

    suspend fun approve(
        groupId: GroupId,
        userId: String,
    ): SaqzResult<GroupMembership, EntryRequestError>

    suspend fun reject(groupId: GroupId, userId: String): EmptyResult<EntryRequestError>

    /** A pessoa desiste do próprio pedido de entrada; repetir não dá erro (o servidor responde 204 de novo). */
    suspend fun cancelOwn(groupId: GroupId): EmptyResult<EntryRequestError>
}
