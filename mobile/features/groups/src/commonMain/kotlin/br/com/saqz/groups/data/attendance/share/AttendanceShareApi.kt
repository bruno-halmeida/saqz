package br.com.saqz.groups.data.attendance.share

import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AttendanceLinkUrlDto(val url: String)

@Serializable
data class ResolvedAttendanceLinkDto(
    val groupId: String,
    val gameId: String,
)

@Serializable
data class AttendanceShareSnapshotPersonDto(
    val displayName: String,
    val waitlistPosition: Long? = null,
)

@Serializable
data class AttendanceShareSnapshotDto(
    val title: String,
    val startsAt: String,
    val timeZone: String,
    val venue: String,
    val capacity: Int,
    val confirmed: List<AttendanceShareSnapshotPersonDto>,
    val waitlisted: List<AttendanceShareSnapshotPersonDto>,
    val declined: List<AttendanceShareSnapshotPersonDto>,
)

@Serializable
private data class ResolveAttendanceLinkRequestDto(val code: String)

interface AttendanceShareGateway {
    suspend fun rotateLink(groupId: String, gameId: String): NetworkResult<AttendanceLinkUrlDto>

    suspend fun resolveLink(code: String): NetworkResult<ResolvedAttendanceLinkDto>

    suspend fun readSnapshot(groupId: String, gameId: String): NetworkResult<AttendanceShareSnapshotDto>
}

class AttendanceShareApi(
    private val network: AuthenticatedNetworkClient,
) : AttendanceShareGateway {
    private val json = Json { explicitNulls = false }

    override suspend fun rotateLink(groupId: String, gameId: String): NetworkResult<AttendanceLinkUrlDto> = network.execute(
        HttpMethod.Post,
        "api/groups/$groupId/games/$gameId/attendance-link",
        AttendanceLinkUrlDto.serializer(),
    )

    override suspend fun resolveLink(code: String): NetworkResult<ResolvedAttendanceLinkDto> = network.execute(
        HttpMethod.Post,
        "api/attendance-links/resolve",
        ResolvedAttendanceLinkDto.serializer(),
        NetworkRequest(json.encodeToString(ResolveAttendanceLinkRequestDto(code))),
    )

    override suspend fun readSnapshot(groupId: String, gameId: String): NetworkResult<AttendanceShareSnapshotDto> = network.execute(
        HttpMethod.Get,
        "api/groups/$groupId/games/$gameId/attendance-share",
        AttendanceShareSnapshotDto.serializer(),
    )
}
