package br.com.saqz.network

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class SessionUserDto(
    val id: String,
    val email: String?,
    val displayName: String,
)

@Serializable
data class SessionMembershipDto(
    val groupId: String,
    val groupName: String,
    val role: String,
)

@Serializable
data class SessionDto(
    val user: SessionUserDto,
    val memberships: List<SessionMembershipDto>,
)

interface SessionGateway {
    suspend fun bootstrap(): NetworkResult<SessionDto>
}

class SessionApi(
    private val network: AuthenticatedNetworkClient,
) : SessionGateway {
    override suspend fun bootstrap(): NetworkResult<SessionDto> =
        network.execute(HttpMethod.Put, "api/session", SessionDto.serializer())
}
