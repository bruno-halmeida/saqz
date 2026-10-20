package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.domain.AthleteMembershipType
import java.net.URI
import java.time.Instant
import java.util.UUID

/** Estado guardado de uma linha de convidado (CONV-012). "Venceu" é derivado: link vencido + linha Aguardando. */
enum class MonthlyInviteeStatus { QUEUED, WAITING, FAILED, NO_WHATSAPP, PAUSED }

/** Estado mostrado no app, com o derivado [EXPIRED]. */
enum class MonthlyInviteeDisplayStatus { QUEUED, WAITING, FAILED, NO_WHATSAPP, PAUSED, EXPIRED }

data class MonthlyInvitee(
    val id: UUID,
    val groupId: UUID,
    val name: String,
    val phone: MonthlyInviteePhone,
    val status: MonthlyInviteeStatus,
    val attempts: Int,
    val sentAt: Instant?,
    val createdByUserId: UUID?,
    val createdAt: Instant,
)

data class NewMonthlyInvitee(val id: UUID, val name: String, val phone: MonthlyInviteePhone)

/** Membro do grupo cujo telefone da conta (não verificado) casa com o número digitado. */
data class MonthlyInviteMemberMatch(val userId: UUID, val name: String, val membershipType: AthleteMembershipType)

data class MonthlyInviteOccupancy(val ownerUserId: UUID, val memberCount: Int)

interface MonthlyInviteRepository {
    fun list(groupId: UUID): List<MonthlyInvitee>

    fun find(groupId: UUID, id: UUID): MonthlyInvitee?

    /** Apaga as linhas cujo telefone já é de um membro do grupo (a pessoa entrou por outro caminho). */
    fun deleteMatchingMembers(groupId: UUID)

    fun membersByPhone(groupId: UUID, phones: Collection<String>): Map<String, MonthlyInviteMemberMatch>

    fun invitedByPhone(groupId: UUID, phones: Collection<String>): Map<String, MonthlyInvitee>

    fun hasRequest(groupId: UUID, requestId: UUID): Boolean

    fun countRequest(groupId: UUID, requestId: UUID): Int

    fun insert(
        groupId: UUID,
        people: List<NewMonthlyInvitee>,
        status: MonthlyInviteeStatus,
        createdByUserId: UUID,
        requestId: UUID,
        now: Instant,
    )

    /** Devolve as linhas ao fim da fila: estado [status], tentativas zeradas. */
    fun requeue(groupId: UUID, ids: List<UUID>, status: MonthlyInviteeStatus)

    fun delete(groupId: UUID, id: UUID): Boolean

    fun occupancy(groupId: UUID): MonthlyInviteOccupancy?
}

/** A fila de envio (B4). Só existe com o WhatsApp ligado. */
interface MonthlyInviteDispatcher {
    /** Instante da próxima tentativa quando a fila está pausada; nulo quando anda. */
    fun pausedUntil(): Instant?

    /** Põe as linhas no fim da fila; chamado depois de a transação gravar. */
    fun enqueue(ids: List<UUID>)
}

data class MonthlyInviteLinkView(val state: MonthlyInviteLinkState, val url: URI?, val expiresAt: Instant?)

data class MonthlyInviteeView(
    val id: UUID,
    val name: String,
    val phone: String,
    val status: MonthlyInviteeDisplayStatus,
    val createdAt: Instant,
)

data class MonthlyFeeDefault(val monthlyFeeCents: Long, val monthlyDueDay: Int)

data class MonthlyInvitesView(
    val link: MonthlyInviteLinkView,
    val invitees: List<MonthlyInviteeView>,
    val pausedRetryAt: Instant?,
    val defaultFee: MonthlyFeeDefault?,
)

enum class MonthlyInvitePhoneCheck { FREE, ALREADY_MENSALISTA, ALREADY_AVULSO, ALREADY_INVITED }

data class MonthlyInvitePhoneCheckView(val result: MonthlyInvitePhoneCheck, val name: String?, val userId: UUID?)

data class MonthlyInvitePerson(val name: String?, val phone: String?)

data class CreateMonthlyInvitesCommand(val requestId: UUID, val people: List<MonthlyInvitePerson>)

enum class MonthlyInviteRejection {
    INVALID_NAME,
    INVALID_PHONE,
    DUPLICATE_IN_BATCH,
    ALREADY_INVITED,
    ALREADY_MENSALISTA,
    ALREADY_AVULSO,
}

data class MonthlyInvitePersonError(val index: Int, val reason: MonthlyInviteRejection)

data class MonthlyInvitePlanLimit(val limit: Int, val current: Int, val requested: Int, val free: Int)

data class CreatedMonthlyInvites(val enqueued: Int, val url: URI, val expiresAt: Instant)

data class CopiedMonthlyInviteLink(val url: URI, val expiresAt: Instant)

sealed interface MonthlyInviteResult<out T> {
    data class Success<T>(val value: T) : MonthlyInviteResult<T>

    data object GroupNotFound : MonthlyInviteResult<Nothing>

    data object AccessForbidden : MonthlyInviteResult<Nothing>

    data object InviteeNotFound : MonthlyInviteResult<Nothing>

    data object MonthlyFeeRequired : MonthlyInviteResult<Nothing>

    data object WhatsAppUnavailable : MonthlyInviteResult<Nothing>

    data object InvalidPhone : MonthlyInviteResult<Nothing>

    data object EmptyBatch : MonthlyInviteResult<Nothing>

    data class InvalidPeople(val errors: List<MonthlyInvitePersonError>) : MonthlyInviteResult<Nothing>

    data class PlanLimit(val limit: MonthlyInvitePlanLimit) : MonthlyInviteResult<Nothing>
}
