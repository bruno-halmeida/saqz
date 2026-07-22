package br.com.saqz.groups.data.attendance

import br.com.saqz.network.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class AttendanceStatusDto { CONFIRMED, DECLINED, WAITLISTED }
@Serializable enum class AttendanceIntentDto { CONFIRM, DECLINE }
@Serializable data class AttendanceEntryDto(val memberId:String,val status:AttendanceStatusDto,val waitlistPosition:Long?=null,val version:Long)
@Serializable data class AttendanceAuditDto(val actorId:String,val source:String,val oldStatus:AttendanceStatusDto?=null,val newStatus:AttendanceStatusDto,val reason:String?=null,val occurredAt:String)
@Serializable data class AttendanceDetailDto(val ownAttendance:AttendanceEntryDto?=null,val confirmedCount:Int,val availableSpots:Int,val waitlistCount:Int,val capacity:Int)
@Serializable data class AttendanceMutationDto(val attendance:AttendanceEntryDto,val audit:AttendanceAuditDto?=null,val promotedCount:Int,val detail:AttendanceDetailDto)
@Serializable data class CapacityDto(val capacity:Int,val version:Long,val promotedCount:Int,val detail:AttendanceDetailDto)
data class VersionedAttendanceMutationDto(val value:AttendanceMutationDto,val etag:String)
data class VersionedCapacityDto(val value:CapacityDto,val etag:String)

@Serializable data class SelfAttendanceCommand(val requestId:String,val intent:AttendanceIntentDto)
@Serializable data class OverrideAttendanceCommand(val requestId:String,val memberId:String,val intent:AttendanceIntentDto,val reason:String)
@Serializable data class CapacityCommand(val requestId:String,val capacity:Int)

sealed interface AttendanceGatewayFailure {
    data class Validation(val fields:Map<String,List<String>>):AttendanceGatewayFailure
    data object HiddenResource:AttendanceGatewayFailure
    data object DeadlinePassed:AttendanceGatewayFailure
    data object Frozen:AttendanceGatewayFailure
    data object Conflict:AttendanceGatewayFailure
    data object Authentication:AttendanceGatewayFailure
    data object Temporary:AttendanceGatewayFailure
    data object InvalidResponse:AttendanceGatewayFailure
}

fun NetworkError.toAttendanceGatewayFailure():AttendanceGatewayFailure=when(this){
    is NetworkError.ApiProblemError->when(problem.code){
        "VALIDATION_FAILED"->AttendanceGatewayFailure.Validation(problem.fieldErrors.orEmpty())
        "GAME_NOT_FOUND","GROUP_NOT_FOUND"->AttendanceGatewayFailure.HiddenResource
        "ATTENDANCE_DEADLINE_PASSED"->AttendanceGatewayFailure.DeadlinePassed
        "ATTENDANCE_FROZEN","INVALID_GAME_TRANSITION"->AttendanceGatewayFailure.Frozen
        "VERSION_CONFLICT"->AttendanceGatewayFailure.Conflict
        "AUTHENTICATION_REQUIRED"->AttendanceGatewayFailure.Authentication
        else->if(problem.status>=500)AttendanceGatewayFailure.Temporary else AttendanceGatewayFailure.InvalidResponse
    }
    NetworkError.InvalidResponse->AttendanceGatewayFailure.InvalidResponse
    NetworkError.Unknown->AttendanceGatewayFailure.InvalidResponse
    is NetworkError.HttpStatus,NetworkError.Timeout,NetworkError.Connectivity,NetworkError.Unavailable,NetworkError.PayloadTooLarge->AttendanceGatewayFailure.Temporary
}

interface AttendanceGateway {
    suspend fun read(groupId:String,gameId:String):NetworkResult<AttendanceDetailDto>
    suspend fun respond(groupId:String,gameId:String,command:SelfAttendanceCommand):NetworkResult<VersionedAttendanceMutationDto>
    suspend fun override(groupId:String,gameId:String,command:OverrideAttendanceCommand):NetworkResult<VersionedAttendanceMutationDto>
    suspend fun capacity(groupId:String,gameId:String,etag:String,command:CapacityCommand):NetworkResult<VersionedCapacityDto>
}

class AttendanceApi(private val network:AuthenticatedNetworkClient):AttendanceGateway {
    private val json=Json{explicitNulls=false}
    override suspend fun read(groupId:String,gameId:String)=network.execute(HttpMethod.Get,path(groupId,gameId),AttendanceDetailDto.serializer())
    override suspend fun respond(groupId:String,gameId:String,command:SelfAttendanceCommand)=network.execute(HttpMethod.Put,path(groupId,gameId),AttendanceMutationDto.serializer(),NetworkRequest(json.encodeToString(command))).versionedMutation()
    override suspend fun override(groupId:String,gameId:String,command:OverrideAttendanceCommand)=network.execute(HttpMethod.Post,"${path(groupId,gameId)}/override",AttendanceMutationDto.serializer(),NetworkRequest(json.encodeToString(command))).versionedMutation()
    override suspend fun capacity(groupId:String,gameId:String,etag:String,command:CapacityCommand)=network.execute(HttpMethod.Put,"api/groups/$groupId/games/$gameId/capacity",CapacityDto.serializer(),NetworkRequest(json.encodeToString(command),mapOf(HttpHeaders.IfMatch to etag))).versionedCapacity()
    private fun path(groupId:String,gameId:String)="api/groups/$groupId/games/$gameId/attendance"
    private fun NetworkResult<AttendanceMutationDto>.versionedMutation(): NetworkResult<VersionedAttendanceMutationDto> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)?.let {
            NetworkResult.Success(VersionedAttendanceMutationDto(value, it), metadata)
        } ?: NetworkResult.Failure(NetworkError.InvalidResponse)
    }
    private fun NetworkResult<CapacityDto>.versionedCapacity(): NetworkResult<VersionedCapacityDto> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)?.let {
            NetworkResult.Success(VersionedCapacityDto(value, it), metadata)
        } ?: NetworkResult.Failure(NetworkError.InvalidResponse)
    }
}
