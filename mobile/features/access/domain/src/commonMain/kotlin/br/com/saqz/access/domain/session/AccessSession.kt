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
    val phone: String? = null,
    val phoneRequired: Boolean = false,
    val emailVerified: Boolean = false,
    val photoUrl: String? = null,
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

interface SessionGateway {
    suspend fun bootstrap(): SaqzResult<AccessSession, AccessError>

    suspend fun completeProfile(
        phone: String,
        displayName: String? = null,
    ): SaqzResult<AccessSession, AccessError>

    /**
     * A foto de perfil da 1c, multipart contra `api/session/photo`. Fica aqui e não numa
     * porta própria porque o endpoint é da mesma sessão que os outros dois: o backend
     * resolve o usuário pelo mesmo `BootstrapSession`, e um gateway a mais significaria
     * outro binding para o mesmo cliente HTTP.
     *
     * Devolve `Unit`: o `PUT` responde 204 com ETag, e a 1c não lê a foto de volta.
     */
    suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, AccessError>
}

interface SessionInvalidator {
    fun invalidate()
}
