package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType

data class GroupEntryRequest(
    val userId: String,
    val displayName: String,
    val requestedAt: String,
)

sealed interface EntryRequestError : SaqzError {
    data class DataFailure(val error: DataError) : EntryRequestError

    /** 422 `MONTHLY_FEE_REQUIRED`: aceitar como mensalista com o grupo sem valor e dia padrão (DEB-112); o pedido fica. */
    data object MonthlyFeeRequired : EntryRequestError
}

interface GroupEntryRequestGateway {
    suspend fun list(groupId: GroupId): SaqzResult<List<GroupEntryRequest>, EntryRequestError>

    /** [membershipType] avulso (padrão) vai sem corpo, como nos apps até 1.0.9; mensalista exige a mensalidade do grupo. */
    suspend fun approve(
        groupId: GroupId,
        userId: String,
        membershipType: AthleteMembershipType = AthleteMembershipType.AVULSO,
    ): SaqzResult<GroupMembership, EntryRequestError>

    suspend fun reject(groupId: GroupId, userId: String): EmptyResult<EntryRequestError>

    /** A pessoa desiste do próprio pedido de entrada; repetir não dá erro (o servidor responde 204 de novo). */
    suspend fun cancelOwn(groupId: GroupId): EmptyResult<EntryRequestError>
}
