package br.com.saqz.groups.data.group

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.GroupPositionSlots
import br.com.saqz.groups.domain.group.GroupPositionSlotsGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.PositionGameSystem
import br.com.saqz.groups.domain.group.PositionSlot
import br.com.saqz.groups.domain.group.VersionedGroupPositionSlots
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PositionSlotTransport(val position: String, val slots: Int)

@Serializable
internal data class PositionSlotsTransport(
    val gameSystem: String? = null,
    val positionSlots: List<PositionSlotTransport> = emptyList(),
)

class KtorGroupPositionSlotsGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
) : GroupPositionSlotsGateway {
    override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroupPositionSlots, GroupProfileError> =
        retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "api/groups/${groupId.value}/position-slots", PositionSlotsTransport.serializer())
        }.toVersioned()

    override suspend fun write(
        groupId: GroupId,
        versionToken: GroupVersionToken,
        config: GroupPositionSlots,
    ): SaqzResult<VersionedGroupPositionSlots, GroupProfileError> = network.execute(
        HttpMethod.Put,
        "api/groups/${groupId.value}/position-slots",
        PositionSlotsTransport.serializer(),
        NetworkRequest(
            json.encodeToString(
                PositionSlotsTransport(config.gameSystem?.name, config.slots.map { PositionSlotTransport(it.position.name, it.slots) }),
            ),
            mapOf(HttpHeaders.IfMatch to versionToken.value),
        ),
    ).toVersioned()

    override suspend fun suggestion(
        system: PositionGameSystem,
        teams: Int,
    ): SaqzResult<GroupPositionSlots, GroupProfileError> =
        retryTransport(RetrySafety.Read) {
            network.execute(
                HttpMethod.Get,
                "api/position-slots/suggestion",
                PositionSlotsTransport.serializer(),
                NetworkRequest(query = mapOf("system" to system.name, "teams" to teams.toString())),
            )
        }.let { result ->
            when (result) {
                is NetworkResult.Failure -> SaqzResult.Failure(result.error.toDomainError())
                is NetworkResult.Success -> SaqzResult.Success(result.value.toDomain(system))
            }
        }
}

private fun NetworkResult<PositionSlotsTransport>.toVersioned(): SaqzResult<VersionedGroupPositionSlots, GroupProfileError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
        is NetworkResult.Success -> {
            val token = metadata.header(HttpHeaders.ETag)
            if (token.isNullOrBlank()) {
                SaqzResult.Failure(GroupProfileError.DataFailure(DataError.InvalidResponse))
            } else {
                SaqzResult.Success(VersionedGroupPositionSlots(value.toDomain(null), GroupVersionToken(token)))
            }
        }
    }

private fun PositionSlotsTransport.toDomain(fallback: PositionGameSystem?) = GroupPositionSlots(
    gameSystem = gameSystem?.let { name -> PositionGameSystem.entries.firstOrNull { it.name == name } } ?: fallback,
    slots = positionSlots.mapNotNull { row ->
        AthletePosition.entries.firstOrNull { it.name == row.position }?.let { PositionSlot(it, row.slots) }
    },
)
