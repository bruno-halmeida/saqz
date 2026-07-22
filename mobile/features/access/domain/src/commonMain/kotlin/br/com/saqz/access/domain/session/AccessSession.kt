package br.com.saqz.access.domain.session

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import kotlin.jvm.JvmInline

data class AccessUser(
    val id: String,
    val email: String?,
    val displayName: String,
)

@JvmInline
value class AccessMembershipRole(val value: String)

data class AccessMembership(
    val groupId: GroupId,
    val groupName: String,
    val role: AccessMembershipRole,
)

data class AccessSession(
    val user: AccessUser,
    val memberships: List<AccessMembership>,
)

sealed interface AccessError : SaqzError {
    data object Unauthenticated : AccessError
    data object Forbidden : AccessError
    data object EmailNotVerified : AccessError
    data class Validation(val details: ValidationDetails) : AccessError
    data class DataFailure(val error: DataError) : AccessError
}

fun interface SessionGateway {
    suspend fun bootstrap(): SaqzResult<AccessSession, AccessError>
}
