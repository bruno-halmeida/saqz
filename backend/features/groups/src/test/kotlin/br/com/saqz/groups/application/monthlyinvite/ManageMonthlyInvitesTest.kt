package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.InviteToken
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ManageMonthlyInvitesTest {
    private val now = Instant.parse("2026-10-20T15:00:00Z")
    private val actor = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val clock = MutableClock(now)
    private val links = InMemoryMonthlyInviteLinkRepository()
    private val invitees = InMemoryMonthlyInviteRepository(owner)
    private val dispatcher = RecordingDispatcher()
    private var role: GroupRole? = GroupRole.OWNER
    private var fee: Long? = 8_000
    private var athleteLimit: Int? = null
    private var writeAllowed = true

    private fun manage(withDispatcher: Boolean = true) = ManageMonthlyInvites(
        transactionRunner = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        },
        readRepository = object : GroupReadRepository {
            override fun find(key: GroupReadKey) = role?.let {
                GroupReadSnapshot(
                    groupId, AccessName.from("Vôlei de Quinta"), IanaTimeZone.from("UTC"), it, 1,
                    financeDefaults = GroupFinanceDefaultsReadModel(null, fee, 10),
                )
            }
        },
        accessPolicy = GroupAccessPolicy(),
        invitees = invitees,
        links = links,
        renewLink = GetOrRenewMonthlyInviteLink(links, Generator(), LinkFactory, clock),
        linkFactory = LinkFactory,
        subscriptionLimits = object : SubscriptionLimits {
            override fun groupLimitFor(ownerId: UUID): Int? = null
            override fun athleteLimitFor(ownerId: UUID): Int? = athleteLimit
        },
        writeAccess = GroupWriteAccess { writeAllowed },
        dispatcher = dispatcher.takeIf { withDispatcher },
        clock = clock,
    )

    private fun people(vararg pairs: Pair<String?, String?>) = pairs.map { MonthlyInvitePerson(it.first, it.second) }

    private val three = people("Ana" to "11988880001", "Bia" to "(11) 98888-0002", "Caio" to "+55 11 98888-0003")

    @Test
    fun `owner cadastra tres pessoas, as tres vao para a fila e o link nasce`() {
        val created = assertIs<MonthlyInviteResult.Success<CreatedMonthlyInvites>>(
            manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three)),
        ).value

        assertEquals(3, created.enqueued)
        assertEquals(now.plus(Duration.ofDays(7)), created.expiresAt)
        assertEquals(listOf("+5511988880001", "+5511988880002", "+5511988880003"), invitees.rows.map { it.phone.value })
        assertTrue(invitees.rows.all { it.status == MonthlyInviteeStatus.QUEUED && it.createdByUserId == actor })
        assertEquals(listOf(invitees.rows.map { it.id }), dispatcher.batches)
        val view = assertIs<MonthlyInviteResult.Success<MonthlyInvitesView>>(manage().list(actor, groupId)).value
        assertEquals(MonthlyInviteLinkState.ACTIVE, view.link.state)
        assertEquals(listOf("11988880001", "11988880002", "11988880003"), view.invitees.map { it.phone })
        assertEquals(MonthlyFeeDefault(8_000, 10), view.defaultFee)
    }

    @Test
    fun `admin cadastra como o dono e atleta recebe proibido`() {
        role = GroupRole.ADMIN
        assertIs<MonthlyInviteResult.Success<*>>(manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three)))

        role = GroupRole.ATHLETE
        val command = CreateMonthlyInvitesCommand(UUID.randomUUID(), people("Davi" to "11988880004"))
        assertSame(MonthlyInviteResult.AccessForbidden, manage().create(actor, groupId, command))
        assertSame(MonthlyInviteResult.AccessForbidden, manage().list(actor, groupId))
        assertSame(MonthlyInviteResult.AccessForbidden, manage().copyLink(actor, groupId))
        role = null
        assertSame(MonthlyInviteResult.GroupNotFound, manage().list(actor, groupId))
    }

    @Test
    fun `toque duplo com o mesmo requestId nao repete linhas nem mensagens`() {
        val command = CreateMonthlyInvitesCommand(UUID.randomUUID(), three)
        manage().create(actor, groupId, command)

        val again = assertIs<MonthlyInviteResult.Success<CreatedMonthlyInvites>>(manage().create(actor, groupId, command)).value

        assertEquals(3, again.enqueued)
        assertEquals(3, invitees.rows.size)
        assertEquals(1, dispatcher.batches.size)
    }

    @Test
    fun `cada recusa sai na posicao da pessoa e nada e gravado`() {
        invitees.members["+5511977770001"] = MonthlyInviteMemberMatch(UUID.randomUUID(), "Mel", AthleteMembershipType.MENSALISTA)
        invitees.members["+5511977770002"] = MonthlyInviteMemberMatch(UUID.randomUUID(), "Nina", AthleteMembershipType.AVULSO)
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), people("Otto" to "11977770003")))
        dispatcher.batches.clear()

        val result = manage().create(
            actor,
            groupId,
            CreateMonthlyInvitesCommand(
                UUID.randomUUID(),
                people(
                    "A" to "11988880001",
                    "Ana" to "1198888000",
                    "Ana" to "11088880001",
                    "Ana" to "11888880001",
                    "Bia" to "11988880002",
                    "Bia de novo" to "11988880002",
                    "Mel" to "11977770001",
                    "Nina" to "11977770002",
                    "Otto" to "11977770003",
                    null to "11988880009",
                ),
            ),
        )

        val errors = assertIs<MonthlyInviteResult.InvalidPeople>(result).errors
        assertEquals(
            listOf(
                0 to MonthlyInviteRejection.INVALID_NAME,
                1 to MonthlyInviteRejection.INVALID_PHONE,
                2 to MonthlyInviteRejection.INVALID_PHONE,
                3 to MonthlyInviteRejection.INVALID_PHONE,
                5 to MonthlyInviteRejection.DUPLICATE_IN_BATCH,
                6 to MonthlyInviteRejection.ALREADY_MENSALISTA,
                7 to MonthlyInviteRejection.ALREADY_AVULSO,
                8 to MonthlyInviteRejection.ALREADY_INVITED,
                9 to MonthlyInviteRejection.INVALID_NAME,
            ),
            errors.map { it.index to it.reason },
        )
        assertEquals(1, invitees.rows.size)
        assertTrue(dispatcher.batches.isEmpty())
    }

    @Test
    fun `lote maior que as vagas livres nao envia nada e informa limite, atual, pedido e livres`() {
        athleteLimit = 5
        invitees.memberCount = 5
        val full = manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        assertEquals(MonthlyInvitePlanLimit(5, 5, 3, 0), assertIs<MonthlyInviteResult.PlanLimit>(full).limit)

        invitees.memberCount = 3
        val partial = manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        assertEquals(MonthlyInvitePlanLimit(5, 3, 3, 2), assertIs<MonthlyInviteResult.PlanLimit>(partial).limit)
        assertTrue(invitees.rows.isEmpty())
        assertNull(links.find(groupId))

        invitees.memberCount = 2
        assertIs<MonthlyInviteResult.Success<*>>(manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three)))
    }

    @Test
    fun `convidado pendente nao reserva vaga`() {
        athleteLimit = 3
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))

        val next = people("Davi" to "11988880004", "Eva" to "11988880005", "Fê" to "11988880006")
        assertIs<MonthlyInviteResult.Success<*>>(manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), next)))
    }

    @Test
    fun `sem mensalidade padrao o cadastro, o link e o reenvio sao bloqueados`() {
        fee = null

        assertSame(MonthlyInviteResult.MonthlyFeeRequired, manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three)))
        assertSame(MonthlyInviteResult.MonthlyFeeRequired, manage().copyLink(actor, groupId))
        assertSame(MonthlyInviteResult.MonthlyFeeRequired, manage().resendAll(actor, groupId))
        assertNull(assertIs<MonthlyInviteResult.Success<MonthlyInvitesView>>(manage().list(actor, groupId)).value.defaultFee)
    }

    @Test
    fun `whatsapp desligado nao enfileira, mas o link para copiar funciona e nao grava linhas`() {
        assertSame(
            MonthlyInviteResult.WhatsAppUnavailable,
            manage(withDispatcher = false).create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three)),
        )

        val copied = assertIs<MonthlyInviteResult.Success<CopiedMonthlyInviteLink>>(manage(withDispatcher = false).copyLink(actor, groupId)).value

        assertEquals(now.plus(Duration.ofDays(7)), copied.expiresAt)
        assertTrue(invitees.rows.isEmpty())
    }

    @Test
    fun `plano vencido bloqueia as escritas`() {
        writeAllowed = false
        assertFailsWith<SubscriptionRequiredException> {
            manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        }
        assertFailsWith<SubscriptionRequiredException> { manage().copyLink(actor, groupId) }
        assertTrue(invitees.rows.isEmpty())
    }

    @Test
    fun `checagem do numero diz livre, ja mensalista, ja avulso ou ja convidado`() {
        val mel = UUID.randomUUID()
        invitees.members["+5511977770001"] = MonthlyInviteMemberMatch(mel, "Mel", AthleteMembershipType.MENSALISTA)
        invitees.members["+5511977770002"] = MonthlyInviteMemberMatch(UUID.randomUUID(), "Nina", AthleteMembershipType.AVULSO)
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), people("Otto" to "11977770003")))

        fun check(phone: String) = assertIs<MonthlyInviteResult.Success<MonthlyInvitePhoneCheckView>>(
            manage().check(actor, groupId, phone),
        ).value

        assertEquals(MonthlyInvitePhoneCheckView(MonthlyInvitePhoneCheck.ALREADY_MENSALISTA, "Mel", mel), check("11977770001"))
        assertEquals(MonthlyInvitePhoneCheck.ALREADY_AVULSO, check("11977770002").result)
        assertEquals(MonthlyInvitePhoneCheckView(MonthlyInvitePhoneCheck.ALREADY_INVITED, "Otto", null), check("11977770003"))
        assertEquals(MonthlyInvitePhoneCheck.FREE, check("11977770004").result)
        assertSame(MonthlyInviteResult.InvalidPhone, manage().check(actor, groupId, "119777"))
    }

    @Test
    fun `reenviar para todos pega aguardando, falhou, pausado e venceu e deixa na fila e sem whatsapp de fora`() {
        val six = people(
            "Ana" to "11988880001", "Bia" to "11988880002", "Caio" to "11988880003",
            "Davi" to "11988880004", "Eva" to "11988880005", "Fê" to "11988880006",
        )
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), six))
        invitees.setStatus(0, MonthlyInviteeStatus.WAITING)
        invitees.setStatus(1, MonthlyInviteeStatus.FAILED)
        invitees.setStatus(2, MonthlyInviteeStatus.PAUSED)
        invitees.setStatus(3, MonthlyInviteeStatus.WAITING)
        invitees.setStatus(4, MonthlyInviteeStatus.QUEUED)
        invitees.setStatus(5, MonthlyInviteeStatus.NO_WHATSAPP)
        clock.current = now.plus(Duration.ofDays(8))
        val expired = assertIs<MonthlyInviteResult.Success<MonthlyInvitesView>>(manage().list(actor, groupId)).value
        assertEquals(MonthlyInviteeDisplayStatus.EXPIRED, expired.invitees[0].status)
        val oldCode = links.find(groupId)!!.shortCode
        dispatcher.batches.clear()

        val result = manage().resendAll(actor, groupId)

        assertEquals(4, assertIs<MonthlyInviteResult.Success<Int>>(result).value)
        assertEquals(listOf(invitees.rows.take(4).map { it.id }), dispatcher.batches)
        assertEquals(MonthlyInviteeStatus.NO_WHATSAPP, invitees.rows[5].status)
        assertTrue(invitees.rows.take(5).all { it.status == MonthlyInviteeStatus.QUEUED })
        assertTrue(oldCode != links.find(groupId)!!.shortCode)
    }

    @Test
    fun `so ha linhas na fila ou sem whatsapp, reenviar para todos nao faz nada`() {
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        invitees.setStatus(2, MonthlyInviteeStatus.NO_WHATSAPP)
        dispatcher.batches.clear()

        assertEquals(0, assertIs<MonthlyInviteResult.Success<Int>>(manage().resendAll(actor, groupId)).value)
        assertTrue(dispatcher.batches.isEmpty())
    }

    @Test
    fun `reenviar uma linha volta ao fim da fila sem linha repetida e renova o link`() {
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        invitees.setStatus(1, MonthlyInviteeStatus.WAITING)
        clock.current = now.plus(Duration.ofDays(3))
        dispatcher.batches.clear()
        val id = invitees.rows[1].id

        assertEquals(1, assertIs<MonthlyInviteResult.Success<Int>>(manage().resend(actor, groupId, id)).value)

        assertEquals(listOf(listOf(id)), dispatcher.batches)
        assertEquals(3, invitees.rows.size)
        assertEquals(clock.current.plus(Duration.ofDays(7)), links.find(groupId)!!.expiresAt)
        assertSame(MonthlyInviteResult.InviteeNotFound, manage().resend(actor, groupId, UUID.randomUUID()))
    }

    @Test
    fun `com a fila pausada as linhas reenviadas voltam como pausado`() {
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        invitees.setStatus(0, MonthlyInviteeStatus.FAILED)
        dispatcher.paused = now.plus(Duration.ofMinutes(10))

        manage().resend(actor, groupId, invitees.rows[0].id)

        assertEquals(MonthlyInviteeStatus.PAUSED, invitees.rows[0].status)
        val view = assertIs<MonthlyInviteResult.Success<MonthlyInvitesView>>(manage().list(actor, groupId)).value
        assertEquals(now.plus(Duration.ofMinutes(10)), view.pausedRetryAt)
    }

    @Test
    fun `remover apaga a linha e nao mexe no link`() {
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        val link = links.find(groupId)

        assertIs<MonthlyInviteResult.Success<Unit>>(manage().remove(actor, groupId, invitees.rows[0].id))

        assertEquals(2, invitees.rows.size)
        assertEquals(link, links.find(groupId))
    }

    @Test
    fun `invalidar nao muda as linhas, que seguem aguardando`() {
        manage().create(actor, groupId, CreateMonthlyInvitesCommand(UUID.randomUUID(), three))
        invitees.setStatus(0, MonthlyInviteeStatus.WAITING)
        links.invalidate(groupId, now)

        val view = assertIs<MonthlyInviteResult.Success<MonthlyInvitesView>>(manage().list(actor, groupId)).value

        assertEquals(MonthlyInviteLinkState.INVALIDATED, view.link.state)
        assertNull(view.link.url)
        assertEquals(MonthlyInviteeDisplayStatus.WAITING, view.invitees[0].status)
    }

    private object LinkFactory : InviteLinkFactory {
        override fun create(code: InviteCode): URI = URI("https://saqz.app/?saqz_invite=${code.value}")
    }

    private class Generator : SecureTokenGenerator {
        private var seed = 0

        override fun generate(): InviteToken {
            val code = InviteCode.from(InviteCode.SHORT_ALPHABET.substring(seed, seed + 8))
            seed += 1
            return InviteToken(code, InviteTokenDigest.sha256(code))
        }
    }

    private class RecordingDispatcher : MonthlyInviteDispatcher {
        val batches = mutableListOf<List<UUID>>()
        var paused: Instant? = null

        override fun pausedUntil(): Instant? = paused

        override fun enqueue(ids: List<UUID>) {
            batches += ids
        }
    }
}

class InMemoryMonthlyInviteRepository(private val owner: UUID) : MonthlyInviteRepository {
    val rows = mutableListOf<MonthlyInvitee>()
    val members = mutableMapOf<String, MonthlyInviteMemberMatch>()
    val requests = mutableMapOf<UUID, Int>()
    var memberCount = 0

    fun setStatus(index: Int, status: MonthlyInviteeStatus) {
        rows[index] = rows[index].copy(status = status)
    }

    override fun list(groupId: UUID) = rows.filter { it.groupId == groupId }

    override fun find(groupId: UUID, id: UUID) = rows.firstOrNull { it.groupId == groupId && it.id == id }

    override fun deleteMatchingMembers(groupId: UUID) {
        rows.removeIf { it.phone.value in members }
    }

    override fun membersByPhone(groupId: UUID, phones: Collection<String>) = members.filterKeys { it in phones }

    override fun invitedByPhone(groupId: UUID, phones: Collection<String>) =
        rows.filter { it.phone.value in phones }.associateBy { it.phone.value }

    override fun hasRequest(groupId: UUID, requestId: UUID) = requestId in requests

    override fun countRequest(groupId: UUID, requestId: UUID) = requests[requestId] ?: 0

    override fun insert(
        groupId: UUID,
        people: List<NewMonthlyInvitee>,
        status: MonthlyInviteeStatus,
        createdByUserId: UUID,
        requestId: UUID,
        now: Instant,
    ) {
        requests[requestId] = people.size
        people.forEach { rows += MonthlyInvitee(it.id, groupId, it.name, it.phone, status, 0, null, createdByUserId, now) }
    }

    override fun requeue(groupId: UUID, ids: List<UUID>, status: MonthlyInviteeStatus) {
        rows.replaceAll { if (it.id in ids) it.copy(status = status, attempts = 0) else it }
    }

    override fun delete(groupId: UUID, id: UUID) = rows.removeIf { it.id == id }

    override fun occupancy(groupId: UUID) = MonthlyInviteOccupancy(owner, memberCount)
}
