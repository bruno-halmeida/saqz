package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.game.series.*
import br.com.saqz.groups.domain.game.GameDraftValidation
import br.com.saqz.groups.domain.game.GameDraftValidator
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameVenueInput
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class WeeklySlotRequest @JsonCreator constructor(
    @JsonProperty("slotKey") val slotKey: UUID?, @JsonProperty("weekday") val weekday: DayOfWeek?,
    @JsonProperty("localTime") val localTime: LocalTime?, @JsonProperty("durationMinutes") val durationMinutes: Int?,
    @JsonProperty("venue") val venue: GameVenueRequest?, @JsonProperty("capacity") val capacity: Int?,
    @JsonProperty("confirmationLeadMinutes") val confirmationLeadMinutes: Int?, @JsonProperty("gameFeeCents") val gameFeeCents: Long? = null,
    @JsonProperty("title") val title: String?,
)
data class WeeklySeriesWriteRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?, @JsonProperty("revisionId") val revisionId: UUID?,
    @JsonProperty("zoneId") val zoneId: String?, @JsonProperty("localStartDate") val localStartDate: LocalDate?,
    @JsonProperty("localEndDate") val localEndDate: LocalDate? = null, @JsonProperty("slots") val slots: List<WeeklySlotRequest>?,
)
data class SeriesBoundaryRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?, @JsonProperty("scope") val scope: SeriesBoundaryScope?,
    @JsonProperty("action") val action: SeriesBoundaryAction?, @JsonProperty("gameId") val gameId: UUID?,
    @JsonProperty("boundary") val boundary: LocalDate?, @JsonProperty("currentRevisionId") val currentRevisionId: UUID?,
    @JsonProperty("successor") val successor: WeeklySeriesWriteRequest? = null,
    @JsonProperty("replacement") val replacement: GameWriteRequest? = null,
)
data class WeeklySlotResponse(val slotKey:UUID,val weekday:DayOfWeek,val localTime:LocalTime,val durationMinutes:Int,val venue:GameVenueResponse,val capacity:Int,val confirmationLeadMinutes:Int,val gameFeeCents:Long?,val title:String)
data class SeriesOccurrenceResponse(val id:UUID,val localDate:LocalDate,val localTime:LocalTime,val startsAt:java.time.Instant,val status:String,val version:Long)
data class WeeklySeriesResponse(val id:UUID,val revisionId:UUID,val revisionNumber:Int,val zoneId:String,val localStartDate:LocalDate,val localEndDate:LocalDate?,val activeThroughDate:LocalDate?,val slots:List<WeeklySlotResponse>,val occurrences:List<SeriesOccurrenceResponse>,val version:Long)

@RestController
class WeeklySeriesController(private val actors:VerifiedGroupActorResolver,private val series:WeeklySeriesService,private val boundaries:ApplySeriesBoundary) {
    @PostMapping("/api/groups/{groupId}/game-series")
    fun create(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String,@RequestBody request:WeeklySeriesWriteRequest):ResponseEntity<WeeklySeriesResponse> {
        val group=uuid(groupId);val rule=request.rule(group)
        return when(val result=series.create(actors.resolve(identity),rule)) {
            is WeeklySeriesResult.Success->ResponseEntity.status(if(result.replay)HttpStatus.OK else HttpStatus.CREATED).eTag(result.series.version.toString()).body(result.series.response())
            else->throwResult(result)
        }
    }
    @GetMapping("/api/groups/{groupId}/game-series/{seriesId}")
    fun read(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String,@PathVariable seriesId:String):ResponseEntity<WeeklySeriesResponse> = when(val result=series.read(actors.resolve(identity),uuid(groupId),uuid(seriesId))){
        is WeeklySeriesResult.Success->ResponseEntity.ok().eTag(result.series.version.toString()).body(result.series.response())
        else->throwResult(result)
    }
    @PostMapping("/api/groups/{groupId}/game-series/{seriesId}/boundaries")
    fun boundary(@AuthenticationPrincipal identity:RequestIdentity,@PathVariable groupId:String,@PathVariable seriesId:String,@RequestHeader("If-Match",required=false)ifMatch:String?,@RequestBody request:SeriesBoundaryRequest):ResponseEntity<WeeklySeriesResponse>{
        val actor=actors.resolve(identity);val group=uuid(groupId);val lineage=uuid(seriesId)
        series.authorizeOrganizer(actor,group)?.let(::throwResult)
        val scope=request.scope?:invalid("scope","is required");val action=request.action?:invalid("action","is required");val expected=version(ifMatch)
        val result=when(scope){
            SeriesBoundaryScope.ONLY_THIS->{
                val game=request.gameId?:invalid("gameId","is required")
                val snapshot=if(action==SeriesBoundaryAction.EDIT) request.replacement?.snapshot()?:invalid("replacement","is required") else null
                boundaries.onlyThis(OnlyThisBoundaryCommand(group,game,expected,LocalDate.now(),action,snapshot))
            }
            SeriesBoundaryScope.THIS_AND_FUTURE->{
                val boundary=request.boundary?:invalid("boundary","is required");val current=request.currentRevisionId?:invalid("currentRevisionId","is required")
                val successor=request.successor?:invalid("successor","is required");val rule=successor.rule(group,lineage)
                boundaries.thisAndFuture(group,current,expected,rule,expected.toInt()+1,boundary,action)
            }
        }
        when(result){SeriesBoundaryResult.Applied,SeriesBoundaryResult.Replay->Unit;SeriesBoundaryResult.NotFound->throw GameNotFoundException();SeriesBoundaryResult.VersionConflict->throw VersionConflictException();SeriesBoundaryResult.InvalidBoundary->invalid("boundary","is invalid for the selected occurrence");is SeriesBoundaryResult.Invalid->throw InvalidGroupRequestException(result.errors.groupBy({it.field},{it.message}))}
        return when(val loaded=series.read(actor,group,lineage)){is WeeklySeriesResult.Success->ResponseEntity.ok().eTag(loaded.series.version.toString()).body(loaded.series.response());else->throwResult(loaded)}
    }
    private fun version(value:String?):Long{if(value==null)throw PreconditionRequiredException();return Regex("\"([1-9][0-9]*)\"").matchEntire(value)?.groupValues?.get(1)?.toLong()?:invalid("ifMatch","must be a quoted positive version")}
    private fun uuid(value:String)=runCatching{UUID.fromString(value)}.getOrElse{throw GameNotFoundException()}
    private fun throwResult(result:WeeklySeriesResult):Nothing=when(result){WeeklySeriesResult.NotFound->throw GameNotFoundException();WeeklySeriesResult.Forbidden->throw AccessForbiddenException();WeeklySeriesResult.Conflict->throw VersionConflictException();is WeeklySeriesResult.Invalid->throw InvalidGroupRequestException(result.errors.groupBy({it.field},{it.message}));is WeeklySeriesResult.Success->error("success")}
    private fun invalid(field:String,message:String):Nothing=throw InvalidGroupRequestException(mapOf(field to listOf(message)))
}
private fun WeeklySeriesWriteRequest.rule(group:UUID,lineage:UUID=requestId?:throw InvalidGroupRequestException(mapOf("requestId" to listOf("is required")))):WeeklySeriesRule{
    val revision=revisionId?:throw InvalidGroupRequestException(mapOf("revisionId" to listOf("is required")));val zone=zoneId?:"";val start=localStartDate?:throw InvalidGroupRequestException(mapOf("localStartDate" to listOf("is required")))
    return WeeklySeriesRule(group,lineage,revision,zone,start,localEndDate,slots=slots.orEmpty().mapIndexed{i,s->WeeklySlotRule(s.slotKey?:throw InvalidGroupRequestException(mapOf("slots[$i].slotKey" to listOf("is required"))),s.weekday?:throw InvalidGroupRequestException(mapOf("slots[$i].weekday" to listOf("is required"))),s.localTime?:throw InvalidGroupRequestException(mapOf("slots[$i].localTime" to listOf("is required"))),s.durationMinutes?:0,s.venue?.let{GameVenueSnapshot(it.venueId,it.name.orEmpty(),it.address.orEmpty(),it.court)}?:GameVenueSnapshot(null,"","",null),s.capacity?:0,s.confirmationLeadMinutes?:-1,s.gameFeeCents,s.title.orEmpty())})
}
private fun GameWriteRequest.snapshot()=when(val v=GameDraftValidator.validate(br.com.saqz.groups.domain.game.GameDraftInput(title,venue?.let{GameVenueInput(it.venueId,it.name,it.address,it.court)},localDate,localTime,zoneId,startsAt,durationMinutes,capacity,confirmationDeadline,gameFeeCents,notes))){is GameDraftValidation.Valid->v.snapshot;is GameDraftValidation.Invalid->throw InvalidGroupRequestException(v.errors.groupBy({it.field},{it.message}))}
private fun WeeklySeriesView.response()=WeeklySeriesResponse(rule.seriesId,rule.revisionId,revisionNumber,rule.zoneId,rule.localStartDate,rule.localEndDate,rule.activeThroughDate,rule.slots.map{WeeklySlotResponse(it.slotKey,it.weekday,it.localTime,it.durationMinutes,GameVenueResponse(it.venue.venueId,it.venue.name,it.venue.address,it.venue.court),it.capacity,it.confirmationLeadMinutes,it.gameFeeCents,it.title)},occurrences.map{SeriesOccurrenceResponse(it.id,it.localDate,it.localTime,it.startsAt,it.status.name,it.version)},version)
