package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.communication.AttendanceUpdatesMode
import br.com.saqz.groups.application.game.PastGames
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GetGroupResult
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupGameConfigReadModel
import br.com.saqz.groups.application.read.GroupProfileReadModel
import br.com.saqz.groups.application.read.GroupRegularSlotReadModel
import br.com.saqz.groups.application.read.GroupVenueReadModel
import br.com.saqz.groups.application.read.GroupView
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.group.GroupReceivables
import java.time.DayOfWeek
import java.time.LocalTime
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class GroupReadResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val role: GroupRole,
    val version: Long,
    val profileStatus: GroupProfileStatus,
    val privacy: String = "PRIVATE",
    val currency: String = "BRL",
    val profile: GroupProfileReadResponse?,
    val financeDefaults: GroupFinanceDefaultsReadResponse?,
    val gameConfig: GroupGameConfigReadResponse,
    val entryRequiresApproval: Boolean,
    /** JOGO-011: o grupo já teve jogo anterior (encerrado ou cancelado publicado). Só para dono e admin; nulo para o atleta. */
    val hasPastGames: Boolean? = null,
    /** FIN-009, aditivo: o grupo recebe pelo app. O app esconde a chave Pix pessoal e mostra "Pagar". */
    val receivablesActive: Boolean = false,
)

data class GroupProfileReadResponse(
    val modality: GroupModality?,
    val composition: GroupComposition?,
    val description: String?,
    val city: String?,
    val level: GroupLevel?,
    val customLevel: String?,
    val playStyle: CourtPlayStyle?,
    val customPlayStyle: String?,
    val defaultVenue: GroupVenueReadResponse?,
    val regularSlots: List<GroupRegularSlotReadResponse>,
    val defaultCapacity: Int?,
    val defaultConfirmationLeadMinutes: Int?,
    val pixKey: String?,
    val pixLabel: String?,
    val defaultDurationMinutes: Int? = null,
)

data class GroupVenueReadResponse(
    val id: UUID,
    val name: String,
    val address: String,
    val court: String?,
)

data class GroupRegularSlotReadResponse(
    val id: UUID,
    val weekday: DayOfWeek,
    val startTime: LocalTime,
    val durationMinutes: Int,
)

data class GroupFinanceDefaultsReadResponse(
    val defaultGameFeeCents: Long?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
    val monthlyLateJoinPolicy: MonthlyLateJoinPolicy,
    /** "Sem diária" do grupo (DEB-146). */
    val defaultNoGameFee: Boolean = false,
    /** Valor vazio e sem marca: o cartão azul do gestor (DEB-146). */
    val gameFeeUndefined: Boolean = false,
)

data class GroupGameConfigReadResponse(
    val mensalistaPriority: Boolean,
    val promotionMode: PromotionMode,
    // Lista no grupo do WhatsApp: por horário (SCHEDULED, o de sempre) ou a cada confirmação (ON_CHANGE).
    val attendanceUpdatesMode: AttendanceUpdatesMode,
    // Sempre false (DEB-040): os apps 1.0.4–1.0.6 ainda leem o campo.
    val autoConfirmEnabled: Boolean,
    /** Vaga só com pagamento antecipado (DEB-150). */
    val advancePaymentRequired: Boolean = false,
)

class GroupNotFoundException : RuntimeException()

class AccessForbiddenException : RuntimeException()

@RestController
class AccessGroupReadController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val getGroup: GetGroup,
    private val pastGames: PastGames? = null,
    private val receivables: GroupReceivables = GroupReceivables.NONE,
) {
    @GetMapping("/api/groups/{groupId}")
    fun get(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): ResponseEntity<GroupReadResponse> {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = runCatching { UUID.fromString(groupId) }.getOrNull()
            ?: throw GroupNotFoundException()
        return when (val result = getGroup.execute(actor, parsedGroupId)) {
            GetGroupResult.GroupNotFound -> throw GroupNotFoundException()
            GetGroupResult.AccessForbidden -> throw AccessForbiddenException()
            is GetGroupResult.Success -> ResponseEntity
                .ok()
                .eTag(result.group.version.toString())
                .body(
                    result.group.toResponse().copy(
                        hasPastGames = pastGames
                            ?.takeIf { result.group.role != GroupRole.ATHLETE }
                            ?.hasPastGames(result.group.id, result.group.role),
                        receivablesActive = receivables.active(result.group.id),
                    ),
                )
        }
    }
}

fun GroupView.toResponse() = GroupReadResponse(
    id = id,
    name = name.value,
    timeZone = timeZone.value,
    role = role,
    version = version,
    profileStatus = profileStatus,
    privacy = "PRIVATE",
    currency = "BRL",
    profile = profile?.toResponse(),
    financeDefaults = financeDefaults?.toResponse(),
    gameConfig = GroupGameConfigReadResponse(
        mensalistaPriority = gameConfig?.mensalistaPriority ?: true,
        promotionMode = gameConfig?.promotionMode ?: PromotionMode.FIFO,
        attendanceUpdatesMode = gameConfig?.attendanceUpdatesMode ?: AttendanceUpdatesMode.SCHEDULED,
        autoConfirmEnabled = false,
        advancePaymentRequired = gameConfig?.advancePaymentRequired ?: false,
    ),
    entryRequiresApproval = entryRequiresApproval,
)

private fun GroupProfileReadModel.toResponse() = GroupProfileReadResponse(
    modality = modality,
    composition = composition,
    description = description,
    city = city,
    level = level,
    customLevel = customLevel,
    playStyle = playStyle,
    customPlayStyle = customPlayStyle,
    defaultVenue = defaultVenue?.toResponse(),
    regularSlots = regularSlots.map { it.toResponse() },
    defaultCapacity = defaultCapacity,
    defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
    pixKey = pixKey,
    pixLabel = pixLabel,
    defaultDurationMinutes = defaultDurationMinutes,
)

private fun GroupVenueReadModel.toResponse() = GroupVenueReadResponse(id, name, address, court)

private fun GroupRegularSlotReadModel.toResponse() =
    GroupRegularSlotReadResponse(id, weekday, startTime, durationMinutes)

private fun GroupFinanceDefaultsReadModel.toResponse() =
    GroupFinanceDefaultsReadResponse(
        defaultGameFeeCents, monthlyFeeCents, monthlyDueDay, monthlyLateJoinPolicy,
        defaultNoGameFee = defaultNoGameFee,
        gameFeeUndefined = defaultGameFeeCents == null && !defaultNoGameFee,
    )
