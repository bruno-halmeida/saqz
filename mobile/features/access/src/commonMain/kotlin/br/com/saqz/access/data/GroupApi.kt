package br.com.saqz.access.data

import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class GroupRoleDto {
    OWNER,
    ADMIN,
    ATHLETE,
}

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val timeZone: String,
    val version: Long,
    val role: GroupRoleDto,
)

data class VersionedGroupDto(
    val group: GroupDto,
    val etag: String,
)

@Serializable
private data class CreateGroupRequestDto(
    val requestId: String,
    val name: String,
    val timeZone: String,
)

@Serializable
private data class UpdateGroupSettingsRequestDto(
    val name: String,
    val timeZone: String,
)

class GroupApi(
    private val network: AuthenticatedNetworkClient,
) {
    private val json = Json { explicitNulls = false }

    suspend fun create(requestId: String, name: String, timeZone: String): NetworkResult<GroupDto> =
        network.execute(
            HttpMethod.Post,
            "api/groups",
            GroupDto.serializer(),
            NetworkRequest(json.encodeToString(CreateGroupRequestDto(requestId, name, timeZone))),
        )

    suspend fun read(groupId: String): NetworkResult<VersionedGroupDto> =
        network.execute(HttpMethod.Get, "api/groups/$groupId", GroupDto.serializer()).versioned()

    suspend fun update(
        groupId: String,
        etag: String,
        name: String,
        timeZone: String,
    ): NetworkResult<VersionedGroupDto> = network.execute(
        HttpMethod.Put,
        "api/groups/$groupId/settings",
        GroupDto.serializer(),
        NetworkRequest(
            body = json.encodeToString(UpdateGroupSettingsRequestDto(name, timeZone)),
            headers = mapOf(HttpHeaders.IfMatch to etag),
        ),
    ).versioned()

    private fun NetworkResult<GroupDto>.versioned(): NetworkResult<VersionedGroupDto> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> {
            val etag = metadata.header(HttpHeaders.ETag)
                ?: return NetworkResult.Failure(NetworkError.InvalidResponse)
            NetworkResult.Success(VersionedGroupDto(value, etag), metadata)
        }
    }
}
