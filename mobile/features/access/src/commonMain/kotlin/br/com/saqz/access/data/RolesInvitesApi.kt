package br.com.saqz.access.data

import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MembershipDto(
    val userId: String,
    val displayName: String,
    val role: GroupRoleDto,
)

@Serializable
enum class PersistedRoleDto {
    ADMIN,
    ATHLETE,
}

@Serializable
data class InviteUrlDto(val inviteUrl: String)

@Serializable
data class RedeemedInviteDto(
    val groupId: String,
    val role: GroupRoleDto,
)

@Serializable
private data class ChangeRoleRequestDto(val role: PersistedRoleDto)

@Serializable
private data class RedeemInviteRequestDto(val code: String)

class RolesInvitesApi(
    private val network: AuthenticatedNetworkClient,
) {
    private val json = Json { explicitNulls = false }

    suspend fun listMemberships(groupId: String): NetworkResult<List<MembershipDto>> = network.execute(
        HttpMethod.Get,
        "api/groups/$groupId/memberships",
        ListSerializer(MembershipDto.serializer()),
    )

    suspend fun changeRole(
        groupId: String,
        userId: String,
        role: PersistedRoleDto,
    ): NetworkResult<MembershipDto> = network.execute(
        HttpMethod.Put,
        "api/groups/$groupId/memberships/$userId/role",
        MembershipDto.serializer(),
        NetworkRequest(json.encodeToString(ChangeRoleRequestDto(role))),
    )

    suspend fun rotateInvite(groupId: String): NetworkResult<InviteUrlDto> = network.execute(
        HttpMethod.Post,
        "api/groups/$groupId/invite",
        InviteUrlDto.serializer(),
    )

    suspend fun expireInvite(groupId: String): NetworkResult<Unit> = network.executeNoContent(
        HttpMethod.Delete,
        "api/groups/$groupId/invite",
    )

    suspend fun redeem(code: String): NetworkResult<RedeemedInviteDto> = network.execute(
        HttpMethod.Post,
        "api/invites/redeem",
        RedeemedInviteDto.serializer(),
        NetworkRequest(json.encodeToString(RedeemInviteRequestDto(code))),
    )
}
