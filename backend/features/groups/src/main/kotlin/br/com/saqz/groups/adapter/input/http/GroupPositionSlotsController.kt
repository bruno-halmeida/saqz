package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.settings.GroupPositionSlotsService
import br.com.saqz.groups.application.settings.PositionSlotsConfig
import br.com.saqz.groups.application.settings.PositionSlotsReadResult
import br.com.saqz.groups.application.settings.PositionSlotsWriteResult
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.GameSystem
import br.com.saqz.groups.domain.attendance.PositionSlotSuggestion
import br.com.saqz.groups.domain.attendance.PositionSlotsError
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class PositionSlotRequest @JsonCreator constructor(
    @JsonProperty("position") val position: AthletePosition?,
    @JsonProperty("slots") val slots: Int?,
)

data class PositionSlotsRequest @JsonCreator constructor(
    @JsonProperty("gameSystem") val gameSystem: GameSystem? = null,
    @JsonProperty("positionSlots") val positionSlots: List<PositionSlotRequest>?,
    /** Opcional: se vier, tem de ser igual à soma das posições. */
    @JsonProperty("total") val total: Int? = null,
)

data class PositionSlotResponse(val position: AthletePosition, val slots: Int)

data class PositionSlotsResponse(
    val gameSystem: GameSystem?,
    val positionSlots: List<PositionSlotResponse>,
    val total: Int,
    val version: Long,
)

data class PositionSlotSuggestionResponse(
    val gameSystem: GameSystem,
    val total: Int,
    val positionSlots: List<PositionSlotResponse>,
)

@RestController
class GroupPositionSlotsController(
    private val actors: VerifiedGroupActorResolver,
    private val service: GroupPositionSlotsService,
) {
    @GetMapping("/api/groups/{groupId}/position-slots")
    fun read(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
    ): ResponseEntity<PositionSlotsResponse> =
        when (val result = service.read(actors.resolve(identity), group(groupId))) {
            PositionSlotsReadResult.GroupNotFound -> throw GroupNotFoundException()
            is PositionSlotsReadResult.Success ->
                ResponseEntity.ok().eTag(result.config.version.toString()).body(result.config.toResponse())
        }

    @PutMapping("/api/groups/{groupId}/position-slots")
    fun write(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestHeader(name = "If-Match", required = false) ifMatch: String?,
        @RequestBody request: PositionSlotsRequest,
    ): ResponseEntity<PositionSlotsResponse> {
        val actor = actors.resolve(identity)
        val version = QuotedResourceVersion.parseRequired(ifMatch)
        val rows = request.positionSlots ?: throw InvalidGroupRequestException(mapOf("positionSlots" to listOf("required")))
        val slots = rows.map {
            (it.position ?: throw InvalidGroupRequestException(mapOf("positionSlots" to listOf("position required")))) to
                (it.slots ?: throw InvalidGroupRequestException(mapOf("positionSlots" to listOf("slots required"))))
        }
        return when (val result = service.write(actor, group(groupId), version, request.gameSystem, slots, request.total)) {
            PositionSlotsWriteResult.GroupNotFound -> throw GroupNotFoundException()
            PositionSlotsWriteResult.AccessForbidden -> throw AccessForbiddenException()
            PositionSlotsWriteResult.VersionConflict -> throw VersionConflictException()
            is PositionSlotsWriteResult.Invalid ->
                throw InvalidGroupRequestException(
                    mapOf((if (result.error == PositionSlotsError.TOTAL_MISMATCH) "total" else "positionSlots") to listOf(result.error.message())),
                    status = 422,
                )
            is PositionSlotsWriteResult.Success ->
                ResponseEntity.ok().eTag(result.config.version.toString()).body(result.config.toResponse())
        }
    }

    /** Sugestão pelo total de vagas (tabela em [PositionSlotSuggestion]); 6-0 devolve lista vazia (sem posições). */
    @GetMapping("/api/position-slots/suggestion")
    fun suggestion(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam("system") system: String?,
        @RequestParam("total", required = false) total: Int?,
    ): PositionSlotSuggestionResponse {
        actors.resolve(identity)
        val parsed = system?.let { runCatching { GameSystem.valueOf(it) }.getOrNull() }
            ?: throw InvalidGroupRequestException(mapOf("system" to listOf("must be FIVE_ONE or SIX_ZERO")))
        val count = total ?: throw InvalidGroupRequestException(mapOf("total" to listOf("is required")))
        val slots = PositionSlotSuggestion.of(parsed, count)
            ?: throw InvalidGroupRequestException(mapOf("total" to listOf("must be between 4 and 100")))
        val rows = slots.entries.sortedBy { it.key.ordinal }.map { PositionSlotResponse(it.key, it.value) }
        return PositionSlotSuggestionResponse(parsed, count, rows)
    }

    private fun group(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull() ?: throw GroupNotFoundException()
}

private fun PositionSlotsConfig.toResponse() = PositionSlotsResponse(
    gameSystem, slots.map { PositionSlotResponse(it.first, it.second) }, total, version,
)

private fun PositionSlotsError.message() = when (this) {
    PositionSlotsError.NEGATIVE_SLOTS -> "slots must not be negative"
    PositionSlotsError.DUPLICATED_POSITION -> "position repeated"
    PositionSlotsError.NO_SLOTS -> "at least one position needs slots"
    PositionSlotsError.TOTAL_OUT_OF_RANGE -> "total must be between 4 and 100"
    PositionSlotsError.POSITIONS_REQUIRED -> "FIVE_ONE requires the slots per position"
    PositionSlotsError.POSITIONS_NOT_ALLOWED -> "only FIVE_ONE uses positions; send an empty list"
    PositionSlotsError.TOTAL_MISMATCH -> "total must equal the sum of the positions"
}
