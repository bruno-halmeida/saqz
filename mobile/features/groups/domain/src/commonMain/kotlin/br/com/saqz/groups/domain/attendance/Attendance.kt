package br.com.saqz.groups.domain.attendance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition
import kotlin.jvm.JvmInline

enum class AttendanceStatus {
    Confirmed,
    Declined,
    Waitlisted,
}

enum class AttendanceIntent {
    Confirm,
    Decline,
}

@JvmInline
value class AttendanceVersionToken(val value: String)

data class AttendanceEntry(
    val memberId: String,
    val status: AttendanceStatus,
    /** Com posições, a vez na fila da própria posição; sem posições, a fila única (DEB-102). */
    val waitlistPosition: Long? = null,
    val version: Long,
    /** Posição da vaga; nula em jogo sem posições ou resposta de backend antigo. */
    val position: AthletePosition? = null,
    /** O organizador colocou a pessoa acima das vagas da posição. */
    val forced: Boolean = false,
)

/** Vagas de uma posição num jogo (DEB-102); o total do jogo é a soma das posições. */
data class PositionSummary(
    val position: AthletePosition,
    val slots: Int,
    val confirmed: Int,
    val missing: Int,
    val waitlisted: Int,
) {
    val isFull: Boolean get() = missing == 0
}

data class AttendanceAudit(
    val actorId: String,
    val source: String,
    val oldStatus: AttendanceStatus? = null,
    val newStatus: AttendanceStatus,
    val reason: String? = null,
    val occurredAt: String,
)

data class AttendanceDetail(
    val ownAttendance: AttendanceEntry? = null,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val capacity: Int,
    val declinedCount: Int = 0,
    val pendingCount: Int = 0,
    /** Resumo por posição; vazio em jogo sem posições (total e uma espera só, como antes). */
    val positions: List<PositionSummary> = emptyList(),
) {
    val hasPositions: Boolean get() = positions.isNotEmpty()
}

data class AttendanceRosterMember(
    val memberId: String,
    val displayName: String,
    val waitlistPosition: Long? = null,
    /** 0 = o próprio membro; 1..n = convidado dele. Para convidado, [memberId] é o id do ANFITRIÃO. */
    val guestSeq: Int = 0,
    /** Nome do anfitrião; só vem quando [guestSeq] > 0. */
    val hostDisplayName: String? = null,
    val position: AthletePosition? = null,
    val forced: Boolean = false,
    /** Foto de perfil do membro (MEM-006); convidado nunca tem. */
    val photoTag: String? = null,
    /** PRES-016: confirmou e não apareceu. Só vem verdadeiro para dono e admin. */
    val noShow: Boolean = false,
    /** PROM-010: a diária deste jogo de quem está na espera; só vem para o gestor e `null` sem diária. */
    val charge: WaitlistCharge? = null,
) {
    val isGuest: Boolean get() = guestSeq > 0

    /** Chave única de linha: dois convidados do mesmo anfitrião dividem o [memberId]. */
    val rowKey: String get() = if (isGuest) "$memberId#$guestSeq" else memberId
}

/** A diária de quem está na espera (DEB-150): dá o "Pagou" / "Falta pagar" e o Recebi com a [version]. */
data class WaitlistCharge(
    val id: String,
    val status: br.com.saqz.groups.domain.finance.ChargeStatus,
    val amountCents: Long,
    val version: Long,
)

data class AttendanceRoster(
    val confirmed: List<AttendanceRosterMember>,
    val waitlisted: List<AttendanceRosterMember>,
    val positions: List<PositionSummary> = emptyList(),
    /** Quem respondeu "Não vou" e segue no grupo (DEB-116), em ordem alfabética; `null` = servidor antigo, sem a lista. */
    val declined: List<AttendanceRosterMember>? = null,
)

data class AttendanceMutation(
    val attendance: AttendanceEntry,
    val audit: AttendanceAudit? = null,
    val promotedCount: Int,
    val detail: AttendanceDetail,
)

data class VersionedAttendanceMutation(
    val value: AttendanceMutation,
    val version: AttendanceVersionToken,
)

data class AttendanceCapacity(
    val capacity: Int,
    val version: Long,
    val promotedCount: Int,
    val detail: AttendanceDetail,
)

data class VersionedAttendanceCapacity(
    val value: AttendanceCapacity,
    val version: AttendanceVersionToken,
)

data class SelfAttendanceCommand(
    val requestId: String,
    val intent: AttendanceIntent,
    /** Só no "Vou" de jogo com posições; sem ela o backend escolhe pelo perfil (apps 1.0.6 a 1.0.9). */
    val position: AthletePosition? = null,
)

data class OverrideAttendanceCommand(
    val requestId: String,
    val memberId: String,
    val intent: AttendanceIntent,
    val reason: String,
    val position: AthletePosition? = null,
    /** Entrar em posição cheia (só organizador). */
    val force: Boolean = false,
)

data class AttendanceCapacityCommand(
    val requestId: String,
    val capacity: Int,
)

data class AttendancePromotionCommand(
    val requestId: String,
    val memberId: String,
    val reason: String,
    val guestSeq: Int = 0,
    /** PROM-010 "Colocar, já pagou": promove e dá baixa na diária na mesma transação. */
    val markPaid: Boolean = false,
    val paidMethod: br.com.saqz.groups.domain.finance.PaidMethod? = null,
)

data class AddGuestCommand(
    val requestId: String,
    val displayName: String,
    val position: AthletePosition? = null,
)

sealed interface AttendanceError : SaqzError {
    data class Validation(val error: DataError.Validation) : AttendanceError
    data object HiddenResource : AttendanceError
    data object DeadlinePassed : AttendanceError
    data object Frozen : AttendanceError
    data object Conflict : AttendanceError
    data object Authentication : AttendanceError
    data class Data(val error: DataError) : AttendanceError
}

interface AttendanceGateway {
    suspend fun read(
        groupId: GroupId,
        gameId: String,
    ): SaqzResult<AttendanceDetail, AttendanceError>

    suspend fun respond(
        groupId: GroupId,
        gameId: String,
        command: SelfAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError>

    suspend fun roster(
        groupId: GroupId,
        gameId: String,
    ): SaqzResult<AttendanceRoster, AttendanceError>

    suspend fun promote(
        groupId: GroupId,
        gameId: String,
        command: AttendancePromotionCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError>

    suspend fun override(
        groupId: GroupId,
        gameId: String,
        command: OverrideAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError>

    suspend fun capacity(
        groupId: GroupId,
        gameId: String,
        version: AttendanceVersionToken,
        command: AttendanceCapacityCommand,
    ): SaqzResult<VersionedAttendanceCapacity, AttendanceError>

    // ponytail: corpo padrão para os fakes de teste existentes não mudarem; o único adapter real
    // (KtorAttendanceGateway) sobrescreve. Tirar o padrão se surgir um segundo adapter.
    suspend fun addGuest(
        groupId: GroupId,
        gameId: String,
        command: AddGuestCommand,
    ): SaqzResult<AttendanceRosterMember, AttendanceError> = SaqzResult.Failure(AttendanceError.HiddenResource)

    suspend fun removeGuest(
        groupId: GroupId,
        gameId: String,
        hostId: String,
        guestSeq: Int,
    ): SaqzResult<Unit, AttendanceError> = SaqzResult.Failure(AttendanceError.HiddenResource)
}
