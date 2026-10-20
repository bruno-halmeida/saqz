package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.PublicInviteLinkFactory
import br.com.saqz.groups.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.groups.application.monthlyinvite.CreateMonthlyInvitesCommand
import br.com.saqz.groups.application.monthlyinvite.GetOrRenewMonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.ManageMonthlyInvites
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteDispatcher
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitePerson
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteResult
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteePhone
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteeStatus
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitesView
import br.com.saqz.groups.application.monthlyinvite.NewMonthlyInvitee
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** CONV-010: tabela de convidados, casamento com membros, requestId do lote e a lista pelo caso de uso. */
class JdbcMonthlyInviteRepositoryIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcMonthlyInviteRepository(source)
    private val now = Instant.parse("2026-10-20T15:00:00Z")
    private val owner = user("Dona", null)
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at,
                   monthly_fee_cents, monthly_due_day)
            VALUES (:id, :owner, :key, 'Vôlei', 'America/Sao_Paulo', now(), now(), 8000, 10)""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
    }

    @Test
    fun `linhas voltam na ordem do lote e o telefone e unico por grupo`() {
        val requestId = UUID.randomUUID()
        insert(requestId, "Ana" to "+5511988880001", "Bia" to "+5511988880002", "Caio" to "+5511988880003")

        assertEquals(listOf("Ana", "Bia", "Caio"), repository.list(group).map { it.name })
        assertTrue(repository.list(group).all { it.status == MonthlyInviteeStatus.QUEUED && it.createdByUserId == owner })
        assertEquals(3, repository.countRequest(group, requestId))
        assertTrue(repository.hasRequest(group, requestId))
        assertFalse(repository.hasRequest(group, UUID.randomUUID()))
        assertFailsWith<DataIntegrityViolationException> { insert(UUID.randomUUID(), "Ana 2" to "+5511988880001") }
    }

    @Test
    fun `casa com membros pelo telefone da conta e apaga as linhas de quem ja entrou`() {
        val mel = member("Mel", "+5511977770001", "MENSALISTA")
        member("Nina", "+5511977770002", "AVULSO")
        insert(UUID.randomUUID(), "Mel" to "+5511977770001", "Otto" to "+5511977770003")

        val matches = repository.membersByPhone(group, listOf("+5511977770001", "+5511977770002", "+5511977770009"))
        assertEquals(mel, matches.getValue("+5511977770001").userId)
        assertEquals(AthleteMembershipType.MENSALISTA, matches.getValue("+5511977770001").membershipType)
        assertEquals(AthleteMembershipType.AVULSO, matches.getValue("+5511977770002").membershipType)
        assertEquals(setOf("+5511977770003", "+5511977770001"), repository.invitedByPhone(group, listOf("+5511977770001", "+5511977770003")).keys)

        transaction.inTransaction { repository.deleteMatchingMembers(group) }

        assertEquals(listOf("Otto"), repository.list(group).map { it.name })
    }

    @Test
    fun `reenfileirar zera tentativas e remover apaga so a linha`() {
        insert(UUID.randomUUID(), "Ana" to "+5511988880001", "Bia" to "+5511988880002")
        val (ana, bia) = repository.list(group)
        jdbc.sql("UPDATE group_monthly_invitees SET status = 'FAILED', attempts = 3 WHERE id = :id").param("id", ana.id).update()

        transaction.inTransaction { repository.requeue(group, listOf(ana.id), MonthlyInviteeStatus.PAUSED) }
        val requeued = repository.find(group, ana.id)!!
        assertEquals(MonthlyInviteeStatus.PAUSED, requeued.status)
        assertEquals(0, requeued.attempts)

        assertTrue(transaction.inTransaction { repository.delete(group, bia.id) })
        assertFalse(transaction.inTransaction { repository.delete(group, bia.id) })
        assertEquals(listOf(ana.id), repository.list(group).map { it.id })
    }

    @Test
    fun `ocupacao conta os membros sem o dono`() {
        member("Mel", null, "AVULSO")
        member("Nina", null, "AVULSO")

        val occupancy = repository.occupancy(group)!!

        assertEquals(owner, occupancy.ownerUserId)
        assertEquals(2, occupancy.memberCount)
    }

    @Test
    fun `lote pelo caso de uso grava, renova o link, aceita toque duplo concorrente e lista sem quem virou membro`() {
        val enqueued = mutableListOf<List<UUID>>()
        val manage = ManageMonthlyInvites(
            transaction, JdbcGroupReadRepository(source), GroupAccessPolicy(), repository, JdbcMonthlyInviteLinkRepository(source),
            GetOrRenewMonthlyInviteLink(
                JdbcMonthlyInviteLinkRepository(source), JcaSecureTokenGenerator(), PublicInviteLinkFactory(URI("https://join.test")),
                Clock.systemUTC(),
            ),
            PublicInviteLinkFactory(URI("https://join.test")),
            object : SubscriptionLimits {
                override fun groupLimitFor(ownerId: UUID): Int? = null
                override fun athleteLimitFor(ownerId: UUID): Int? = null
            },
            GroupWriteAccess { true },
            object : MonthlyInviteDispatcher {
                override fun pausedUntil(): Instant? = null
                override fun enqueue(ids: List<UUID>) {
                    synchronized(enqueued) { enqueued += ids }
                }
            },
            Clock.systemUTC(),
        )
        val command = CreateMonthlyInvitesCommand(
            UUID.randomUUID(),
            listOf(MonthlyInvitePerson("Ana", "11988880001"), MonthlyInvitePerson("Bia", "11988880002")),
        )

        val pool = Executors.newFixedThreadPool(2)
        val results = pool.invokeAll(List(2) { Callable { manage.create(owner, group, command) } }).map { it.get() }
        pool.shutdown()

        assertTrue(results.all { it is MonthlyInviteResult.Success })
        assertEquals(2, repository.list(group).size)
        assertEquals(1, enqueued.size)
        member("Ana", "+5511988880001", "MENSALISTA")
        val view = assertIs<MonthlyInviteResult.Success<MonthlyInvitesView>>(manage.list(owner, group)).value
        assertEquals(listOf("Bia"), view.invitees.map { it.name })
        assertEquals(1, jdbc.sql("SELECT count(*) FROM group_monthly_invite_links WHERE group_id = :g").param("g", group).query(Int::class.java).single())
    }

    @Test
    fun `excluir o grupo de vez leva os convidados junto`() {
        insert(UUID.randomUUID(), "Ana" to "+5511988880001")

        jdbc.sql("DELETE FROM access_groups WHERE id = :g").param("g", group).update()

        assertEquals(0, jdbc.sql("SELECT count(*) FROM group_monthly_invitees").query(Int::class.java).single())
    }

    private fun insert(requestId: UUID, vararg people: Pair<String, String>) = transaction.inTransaction {
        repository.insert(
            group,
            people.map { NewMonthlyInvitee(UUID.randomUUID(), it.first, MonthlyInviteePhone.parse(it.second)!!) },
            MonthlyInviteeStatus.QUEUED,
            owner,
            requestId,
            now,
        )
    }

    private fun member(name: String, phone: String?, type: String) = user(name, phone).also {
        jdbc.sql(
            "INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at) " +
                "VALUES (:g, :u, 'ATHLETE', CAST(:t AS athlete_membership_type), true, now(), now())",
        ).param("g", group).param("u", it).param("t", type).update()
    }

    private fun user(name: String, phone: String?): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, :phone, now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).param("phone", phone).update()
    }
}
