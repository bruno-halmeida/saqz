package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import java.time.Clock
import java.util.UUID

/**
 * Cadastro, lista e ações do convite de mensalistas (CONV-010 a 012), só para dono e admin (`MANAGE_INVITE`).
 * Toda ação que manda o link (lote, copiar, reenviar) renova o link de mensalistas; as escritas passam pela trava
 * do plano. A fila ([dispatcher]) só existe com o WhatsApp ligado e é acordada depois de a transação gravar.
 */
@Suppress("TooManyFunctions")
class ManageMonthlyInvites(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val invitees: MonthlyInviteRepository,
    private val links: MonthlyInviteLinkRepository,
    private val renewLink: GetOrRenewMonthlyInviteLink,
    private val linkFactory: InviteLinkFactory,
    private val subscriptionLimits: SubscriptionLimits,
    private val writeAccess: GroupWriteAccess,
    private val dispatcher: MonthlyInviteDispatcher?,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    fun list(actor: UUID, groupId: UUID): MonthlyInviteResult<MonthlyInvitesView> = transactionRunner.inTransaction {
        authorized(actor, groupId) { group ->
            val now = clock.instant()
            invitees.deleteMatchingMembers(groupId)
            val link = links.find(groupId)
            val state = link?.state(now) ?: MonthlyInviteLinkState.NONE
            val linkView = MonthlyInviteLinkView(
                state = state,
                url = link?.shortCode?.takeIf { state == MonthlyInviteLinkState.ACTIVE }
                    ?.let { linkFactory.create(InviteCode.fromShort(it)) },
                expiresAt = link?.expiresAt?.takeIf {
                    state == MonthlyInviteLinkState.ACTIVE || state == MonthlyInviteLinkState.EXPIRED
                },
            )
            val rows = invitees.list(groupId).map { it.toView(state) }
            MonthlyInviteResult.Success(MonthlyInvitesView(linkView, rows, dispatcher?.pausedUntil(), group.defaultFee()))
        }
    }

    fun check(actor: UUID, groupId: UUID, rawPhone: String): MonthlyInviteResult<MonthlyInvitePhoneCheckView> =
        transactionRunner.inTransaction {
            authorized(actor, groupId) {
                val phone = MonthlyInviteePhone.parse(rawPhone) ?: return@authorized MonthlyInviteResult.InvalidPhone
                val member = invitees.membersByPhone(groupId, listOf(phone.value))[phone.value]
                val invited = invitees.invitedByPhone(groupId, listOf(phone.value))[phone.value]
                MonthlyInviteResult.Success(
                    when {
                        member?.membershipType == AthleteMembershipType.MENSALISTA ->
                            MonthlyInvitePhoneCheckView(MonthlyInvitePhoneCheck.ALREADY_MENSALISTA, member.name, member.userId)
                        member != null ->
                            MonthlyInvitePhoneCheckView(MonthlyInvitePhoneCheck.ALREADY_AVULSO, member.name, member.userId)
                        invited != null -> MonthlyInvitePhoneCheckView(MonthlyInvitePhoneCheck.ALREADY_INVITED, invited.name, null)
                        else -> MonthlyInvitePhoneCheckView(MonthlyInvitePhoneCheck.FREE, null, null)
                    },
                )
            }
        }

    fun create(actor: UUID, groupId: UUID, command: CreateMonthlyInvitesCommand): MonthlyInviteResult<CreatedMonthlyInvites> {
        var enqueue: List<UUID> = emptyList()
        val result = transactionRunner.inTransaction {
            authorized(actor, groupId) { group ->
                writeAccess.requireWrite(groupId)
                // Trava o grupo: dois toques ao mesmo tempo esperam um pelo outro e o segundo cai no requestId.
                links.lockAndFind(groupId)
                if (invitees.hasRequest(groupId, command.requestId)) {
                    // Toque duplo: o mesmo lote já foi gravado e enfileirado; devolve o link atual sem repetir nada.
                    val count = invitees.countRequest(groupId, command.requestId)
                    val link = links.find(groupId)?.takeIf { it.shortCode != null }
                    return@authorized MonthlyInviteResult.Success(
                        if (link != null) {
                            CreatedMonthlyInvites(count, linkFactory.create(InviteCode.fromShort(link.shortCode!!)), link.expiresAt)
                        } else {
                            renewLink.execute(groupId, actor).let { CreatedMonthlyInvites(count, it.url, it.expiresAt) }
                        },
                    )
                }
                if (group.defaultFee() == null) return@authorized MonthlyInviteResult.MonthlyFeeRequired
                val queue = dispatcher ?: return@authorized MonthlyInviteResult.WhatsAppUnavailable
                if (command.people.isEmpty()) return@authorized MonthlyInviteResult.EmptyBatch

                val parsed = validate(groupId, command.people)
                if (parsed.errors.isNotEmpty()) return@authorized MonthlyInviteResult.InvalidPeople(parsed.errors)
                planLimit(groupId, parsed.people.size)?.let { return@authorized MonthlyInviteResult.PlanLimit(it) }

                val now = clock.instant()
                invitees.insert(groupId, parsed.people, queuedStatus(queue), actor, command.requestId, now)
                val link = renewLink.execute(groupId, actor)
                enqueue = parsed.people.map { it.id }
                MonthlyInviteResult.Success(CreatedMonthlyInvites(parsed.people.size, link.url, link.expiresAt))
            }
        }
        if (enqueue.isNotEmpty()) dispatcher?.enqueue(enqueue)
        return result
    }

    /** "Copiar link" e o modo sem WhatsApp: conta como envio e renova, sem gravar linhas. */
    fun copyLink(actor: UUID, groupId: UUID): MonthlyInviteResult<CopiedMonthlyInviteLink> = transactionRunner.inTransaction {
        authorized(actor, groupId) { group ->
            writeAccess.requireWrite(groupId)
            if (group.defaultFee() == null) return@authorized MonthlyInviteResult.MonthlyFeeRequired
            val link = renewLink.execute(groupId, actor)
            MonthlyInviteResult.Success(CopiedMonthlyInviteLink(link.url, link.expiresAt))
        }
    }

    /** Reenviar uma linha: volta ao fim da fila e renova o link. Linha já na fila fica como está. */
    fun resend(actor: UUID, groupId: UUID, inviteeId: UUID): MonthlyInviteResult<Int> =
        requeue(actor, groupId) { invitees.find(groupId, inviteeId)?.let(::listOf) }

    /** Reenviar para todos: Aguardando, Falhou, Pausado e Venceu (que é Aguardando com o link vencido). */
    fun resendAll(actor: UUID, groupId: UUID): MonthlyInviteResult<Int> =
        requeue(actor, groupId) { invitees.list(groupId).filter { it.status in RESEND_ALL } }

    fun remove(actor: UUID, groupId: UUID, inviteeId: UUID): MonthlyInviteResult<Unit> = transactionRunner.inTransaction {
        authorized(actor, groupId) {
            writeAccess.requireWrite(groupId)
            invitees.delete(groupId, inviteeId)
            MonthlyInviteResult.Success(Unit)
        }
    }

    private fun requeue(actor: UUID, groupId: UUID, select: () -> List<MonthlyInvitee>?): MonthlyInviteResult<Int> {
        var enqueue: List<UUID> = emptyList()
        val result = transactionRunner.inTransaction {
            authorized(actor, groupId) { group ->
                writeAccess.requireWrite(groupId)
                val selected = select() ?: return@authorized MonthlyInviteResult.InviteeNotFound
                if (group.defaultFee() == null) return@authorized MonthlyInviteResult.MonthlyFeeRequired
                val queue = dispatcher ?: return@authorized MonthlyInviteResult.WhatsAppUnavailable
                val eligible = selected.filter { it.status != MonthlyInviteeStatus.QUEUED }.map { it.id }
                if (eligible.isEmpty()) return@authorized MonthlyInviteResult.Success(0)
                invitees.requeue(groupId, eligible, queuedStatus(queue))
                renewLink.execute(groupId, actor)
                enqueue = eligible
                MonthlyInviteResult.Success(eligible.size)
            }
        }
        if (enqueue.isNotEmpty()) dispatcher?.enqueue(enqueue)
        return result
    }

    /** Com a fila pausada, a linha volta à fila já como "Pausado" e não fura a pausa (CONV-012). */
    private fun queuedStatus(queue: MonthlyInviteDispatcher) =
        if (queue.pausedUntil() != null) MonthlyInviteeStatus.PAUSED else MonthlyInviteeStatus.QUEUED

    private fun validate(groupId: UUID, people: List<MonthlyInvitePerson>): ParsedBatch {
        val phones = people.map { person -> person.phone?.let(MonthlyInviteePhone::parse) }
        val known = phones.mapNotNull { it?.value }
        val members = invitees.membersByPhone(groupId, known)
        val invited = invitees.invitedByPhone(groupId, known)
        val seen = mutableSetOf<String>()
        val errors = mutableListOf<MonthlyInvitePersonError>()
        val accepted = mutableListOf<NewMonthlyInvitee>()
        people.forEachIndexed { index, person ->
            val name = person.name?.trim().orEmpty()
            val phone = phones[index]
            val reason = when {
                name.count(Char::isLetter) < MIN_NAME_LETTERS || name.length > MAX_NAME_LENGTH ->
                    MonthlyInviteRejection.INVALID_NAME
                phone == null -> MonthlyInviteRejection.INVALID_PHONE
                !seen.add(phone.value) -> MonthlyInviteRejection.DUPLICATE_IN_BATCH
                members[phone.value]?.membershipType == AthleteMembershipType.MENSALISTA ->
                    MonthlyInviteRejection.ALREADY_MENSALISTA
                members[phone.value] != null -> MonthlyInviteRejection.ALREADY_AVULSO
                invited[phone.value] != null -> MonthlyInviteRejection.ALREADY_INVITED
                else -> null
            }
            if (reason != null) {
                errors += MonthlyInvitePersonError(index, reason)
            } else {
                accepted += NewMonthlyInvitee(newId(), name, requireNotNull(phone))
            }
        }
        return ParsedBatch(accepted, errors)
    }

    /** Convidado pendente não reserva vaga: compara o lote com as vagas livres de agora (SAI-005). */
    private fun planLimit(groupId: UUID, requested: Int): MonthlyInvitePlanLimit? {
        val occupancy = invitees.occupancy(groupId) ?: return null
        val limit = subscriptionLimits.athleteLimitFor(occupancy.ownerUserId) ?: return null
        val free = (limit - occupancy.memberCount).coerceAtLeast(0)
        return MonthlyInvitePlanLimit(limit, occupancy.memberCount, requested, free).takeIf { requested > free }
    }

    private fun <T> authorized(
        actor: UUID,
        groupId: UUID,
        block: (GroupReadSnapshot) -> MonthlyInviteResult<T>,
    ): MonthlyInviteResult<T> {
        val group = readRepository.find(GroupReadKey(actor, groupId)) ?: return MonthlyInviteResult.GroupNotFound
        return when (accessPolicy.authorize(group.role, GroupAction.MANAGE_INVITE)) {
            GroupAccessDecision.GroupNotFound -> MonthlyInviteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> MonthlyInviteResult.AccessForbidden
            GroupAccessDecision.Allowed -> block(group)
        }
    }

    private fun GroupReadSnapshot.defaultFee(): MonthlyFeeDefault? {
        val fee = financeDefaults?.monthlyFeeCents ?: return null
        val day = financeDefaults.monthlyDueDay ?: return null
        return MonthlyFeeDefault(fee, day)
    }

    private fun MonthlyInvitee.toView(linkState: MonthlyInviteLinkState) = MonthlyInviteeView(
        id = id,
        name = name,
        phone = phone.national,
        status = if (status == MonthlyInviteeStatus.WAITING && linkState == MonthlyInviteLinkState.EXPIRED) {
            MonthlyInviteeDisplayStatus.EXPIRED
        } else {
            MonthlyInviteeDisplayStatus.valueOf(status.name)
        },
        createdAt = createdAt,
    )

    private data class ParsedBatch(val people: List<NewMonthlyInvitee>, val errors: List<MonthlyInvitePersonError>)

    private companion object {
        const val MIN_NAME_LETTERS = 2
        const val MAX_NAME_LENGTH = 80
        val RESEND_ALL = setOf(MonthlyInviteeStatus.WAITING, MonthlyInviteeStatus.FAILED, MonthlyInviteeStatus.PAUSED)
    }
}
