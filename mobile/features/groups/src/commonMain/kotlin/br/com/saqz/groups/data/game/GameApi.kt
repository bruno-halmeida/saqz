package br.com.saqz.groups.data.game

import br.com.saqz.network.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class GameStatusDto { DRAFT, PUBLISHED, CANCELLED, COMPLETED }
@Serializable enum class SeriesBoundaryScopeDto { ONLY_THIS, THIS_AND_FUTURE }
@Serializable enum class SeriesBoundaryActionDto { EDIT, CANCEL }
@Serializable enum class WeekdayDto { MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY }
@Serializable data class GameVenueDto(val venueId:String?=null,val name:String,val address:String,val court:String?=null)
@Serializable data class GameDto(val id:String,val groupId:String,val title:String,val venue:GameVenueDto,val localDate:String,val localTime:String,val zoneId:String,val startsAt:String,val durationMinutes:Int,val capacity:Int,val confirmationDeadline:String,val gameFeeCents:Long?=null,val notes:String?=null,val status:GameStatusDto,val version:Long,val confirmedCount:Int,val availableSpots:Int,val waitlistCount:Int,val financeReviewRequired:Boolean=false)
@Serializable data class WeeklySlotDto(val slotKey:String,val weekday:WeekdayDto,val localTime:String,val durationMinutes:Int,val venue:GameVenueDto,val capacity:Int,val confirmationLeadMinutes:Int,val gameFeeCents:Long?=null,val title:String)
@Serializable data class SeriesOccurrenceDto(val id:String,val localDate:String,val localTime:String,val startsAt:String,val status:GameStatusDto,val version:Long)
@Serializable data class WeeklySeriesDto(val id:String,val revisionId:String,val revisionNumber:Int,val zoneId:String,val localStartDate:String,val localEndDate:String?=null,val activeThroughDate:String?=null,val slots:List<WeeklySlotDto>,val occurrences:List<SeriesOccurrenceDto>,val version:Long)
data class VersionedGameDto(val game:GameDto,val etag:String)
data class VersionedSeriesDto(val series:WeeklySeriesDto,val etag:String)

@Serializable data class GameWriteCommand(val requestId:String?=null,val title:String?=null,val venue:GameVenueDto?=null,val localDate:String?,val localTime:String?,val zoneId:String?,val startsAt:String?,val durationMinutes:Int?=null,val capacity:Int?=null,val confirmationDeadline:String?=null,val gameFeeCents:Long?=null,val useDefaultGameFee:Boolean=true,val notes:String?=null)
@Serializable data class WeeklySeriesWriteCommand(val requestId:String?,val revisionId:String?,val zoneId:String?,val localStartDate:String?,val localEndDate:String?=null,val slots:List<WeeklySlotDto>?)
@Serializable data class SeriesBoundaryCommand(val requestId:String,val scope:SeriesBoundaryScopeDto,val action:SeriesBoundaryActionDto,val gameId:String?=null,val boundary:String?=null,val currentRevisionId:String?=null,val successor:WeeklySeriesWriteCommand?=null,val replacement:GameWriteCommand?=null)

sealed interface GameGatewayFailure { data class Validation(val fields:Map<String,List<String>>):GameGatewayFailure;data object HiddenResource:GameGatewayFailure;data object Conflict:GameGatewayFailure;data object InvalidLifecycle:GameGatewayFailure;data object Authentication:GameGatewayFailure;data object Temporary:GameGatewayFailure;data object InvalidResponse:GameGatewayFailure }
fun NetworkError.toGameGatewayFailure():GameGatewayFailure=when(this){
    is NetworkError.ApiProblemError->when(problem.code){"VALIDATION_FAILED"->GameGatewayFailure.Validation(problem.fieldErrors.orEmpty());"GAME_NOT_FOUND","GROUP_NOT_FOUND"->GameGatewayFailure.HiddenResource;"VERSION_CONFLICT"->GameGatewayFailure.Conflict;"INVALID_GAME_TRANSITION"->GameGatewayFailure.InvalidLifecycle;"AUTHENTICATION_REQUIRED"->GameGatewayFailure.Authentication;else->if(problem.status>=500)GameGatewayFailure.Temporary else GameGatewayFailure.InvalidResponse}
    NetworkError.InvalidResponse->GameGatewayFailure.InvalidResponse
    is NetworkError.HttpStatus,NetworkError.Timeout,NetworkError.Unavailable,NetworkError.PayloadTooLarge->GameGatewayFailure.Temporary
}

interface GameGateway {
    suspend fun list(groupId:String):NetworkResult<List<GameDto>>
    suspend fun read(groupId:String,gameId:String):NetworkResult<VersionedGameDto>
    suspend fun create(groupId:String,command:GameWriteCommand):NetworkResult<VersionedGameDto>
    suspend fun edit(groupId:String,gameId:String,etag:String,command:GameWriteCommand):NetworkResult<VersionedGameDto>
    suspend fun lifecycle(groupId:String,gameId:String,etag:String,mutation:String):NetworkResult<VersionedGameDto>
    suspend fun createSeries(groupId:String,command:WeeklySeriesWriteCommand):NetworkResult<VersionedSeriesDto>
    suspend fun readSeries(groupId:String,seriesId:String):NetworkResult<VersionedSeriesDto>
    suspend fun boundary(groupId:String,seriesId:String,etag:String,command:SeriesBoundaryCommand):NetworkResult<VersionedSeriesDto>
}

class GameApi(private val network:AuthenticatedNetworkClient):GameGateway {
    private val json=Json{explicitNulls=false}
    override suspend fun list(groupId:String)=network.execute(HttpMethod.Get,"api/groups/$groupId/games",kotlinx.serialization.builtins.ListSerializer(GameDto.serializer()))
    override suspend fun read(groupId:String,gameId:String)=network.execute(HttpMethod.Get,"api/groups/$groupId/games/$gameId",GameDto.serializer()).versionedGame()
    override suspend fun create(groupId:String,command:GameWriteCommand)=network.execute(HttpMethod.Post,"api/groups/$groupId/games",GameDto.serializer(),NetworkRequest(json.encodeToString(command))).versionedGame()
    override suspend fun edit(groupId:String,gameId:String,etag:String,command:GameWriteCommand)=network.execute(HttpMethod.Put,"api/groups/$groupId/games/$gameId",GameDto.serializer(),NetworkRequest(json.encodeToString(command),mapOf(HttpHeaders.IfMatch to etag))).versionedGame()
    override suspend fun lifecycle(groupId:String,gameId:String,etag:String,mutation:String)=network.execute(HttpMethod.Post,"api/groups/$groupId/games/$gameId/$mutation",GameDto.serializer(),NetworkRequest(headers=mapOf(HttpHeaders.IfMatch to etag))).versionedGame()
    override suspend fun createSeries(groupId:String,command:WeeklySeriesWriteCommand)=network.execute(HttpMethod.Post,"api/groups/$groupId/game-series",WeeklySeriesDto.serializer(),NetworkRequest(json.encodeToString(command))).versionedSeries()
    override suspend fun readSeries(groupId:String,seriesId:String)=network.execute(HttpMethod.Get,"api/groups/$groupId/game-series/$seriesId",WeeklySeriesDto.serializer()).versionedSeries()
    override suspend fun boundary(groupId:String,seriesId:String,etag:String,command:SeriesBoundaryCommand)=network.execute(HttpMethod.Post,"api/groups/$groupId/game-series/$seriesId/boundaries",WeeklySeriesDto.serializer(),NetworkRequest(json.encodeToString(command),mapOf(HttpHeaders.IfMatch to etag))).versionedSeries()
    private fun NetworkResult<GameDto>.versionedGame(): NetworkResult<VersionedGameDto> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)?.let {
            NetworkResult.Success(VersionedGameDto(value, it), metadata)
        } ?: NetworkResult.Failure(NetworkError.InvalidResponse)
    }
    private fun NetworkResult<WeeklySeriesDto>.versionedSeries(): NetworkResult<VersionedSeriesDto> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)?.let {
            NetworkResult.Success(VersionedSeriesDto(value, it), metadata)
        } ?: NetworkResult.Failure(NetworkError.InvalidResponse)
    }
}
