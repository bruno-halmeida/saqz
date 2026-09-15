package br.com.saqz.groups.data.group

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.*
import br.com.saqz.network.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ScheduleSlotTransport(val weekday: GroupWeekdayDto, val startTime: String)
@Serializable
internal data class ScheduleTransport(
    val recurring: Boolean,
    val slots: List<ScheduleSlotTransport>,
    val durationMinutes: Int,
    val confirmationLeadMinutes: Int,
    val paused: Boolean,
)
class KtorGroupScheduleGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json,
) : GroupScheduleGateway {
    override suspend fun readSchedule(groupId: GroupId): SaqzResult<VersionedGroupSchedule, GroupProfileError> =
        retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "api/groups/${groupId.value}/schedule", ScheduleTransport.serializer())
        }.toScheduleResult()

    override suspend fun updateSchedule(
        groupId: GroupId,
        versionToken: GroupVersionToken,
        schedule: GroupSchedule,
    ): SaqzResult<VersionedGroupSchedule, GroupProfileError> = network.execute(
        HttpMethod.Put,
        "api/groups/${groupId.value}/schedule",
        ScheduleTransport.serializer(),
        NetworkRequest(
            json.encodeToString(ScheduleTransport(
                schedule.recurring,
                schedule.slots.map { ScheduleSlotTransport(GroupWeekdayDto.valueOf(it.weekday.name), it.startTime) },
                schedule.durationMinutes,
                schedule.confirmationLeadMinutes,
                schedule.paused,
            )),
            mapOf(HttpHeaders.IfMatch to versionToken.value),
        ),
    ).toScheduleResult()
}
private fun NetworkResult<ScheduleTransport>.toScheduleResult(): SaqzResult<VersionedGroupSchedule, GroupProfileError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
        is NetworkResult.Success -> {
            val token = metadata.header(HttpHeaders.ETag)
            if (token.isNullOrBlank()) {
                SaqzResult.Failure(GroupProfileError.DataFailure(DataError.InvalidResponse))
            } else {
                SaqzResult.Success(VersionedGroupSchedule(
                    GroupSchedule(
                        value.recurring,
                        value.slots.map { GroupScheduleSlot(GroupWeekday.valueOf(it.weekday.name), it.startTime.take(5)) },
                        value.durationMinutes,
                        value.confirmationLeadMinutes,
                        value.paused,
                    ),
                    GroupVersionToken(token),
                ))
            }
        }
    }
