package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLinkState
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteePhone
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteeStatus
import br.com.saqz.groups.application.monthlyinvite.NewMonthlyInvitee
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CONV-012: o estado de cada convidado no banco, lido e gravado pela fila em memória. */
class JdbcMonthlyInviteQueueStoreIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val store = JdbcMonthlyInviteQueueStore(source)
    private val invitees = JdbcMonthlyInviteRepository(source)
    private val now = Instant.parse("2026-10-20T15:00:00Z")
    private val owner = user("Dona Lu")
    private val admin = user("Rafa Admin")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now())""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        transaction.inTransaction {
            JdbcMonthlyInviteLinkRepository(source).replaceCode(group, "44DF8SA8", now.plus(Duration.ofDays(7)), admin, now)
        }
    }

    @Test
    fun `contexto traz nome, grupo, quem cadastrou e o link, e sem a conta de quem cadastrou vai o dono`() {
        val (ana, bia) = insert(admin, "Ana" to "+5511988880001", "Bia" to "+5511988880002")
        jdbc.sql("UPDATE group_monthly_invitees SET created_by_user_id = NULL WHERE id = :id").param("id", bia).update()

        val context = store.context(ana)!!
        assertEquals("Ana", context.name)
        assertEquals("+5511988880001", context.phone)
        assertEquals("Vôlei de Quinta", context.groupName)
        assertEquals("Rafa Admin", context.senderName)
        assertEquals(MonthlyInviteLinkState.ACTIVE, context.link!!.state(now))
        assertEquals("Dona Lu", store.context(bia)!!.senderName)
        assertNull(store.context(UUID.randomUUID()))
    }

    @Test
    fun `marcas de envio, falha, sem whatsapp e pausa so mexem em quem ainda espera`() {
        val (ana, bia, caio, davi) = insert(owner, "Ana" to "+5511988880001", "Bia" to "+5511988880002", "Caio" to "+5511988880003", "Davi" to "+5511988880004")

        store.markSent(ana, 2, now)
        store.markFailed(bia, 3)
        store.markNoWhatsApp(caio)
        store.markPending(listOf(ana, bia, caio, davi), MonthlyInviteeStatus.PAUSED)

        val rows = invitees.list(group).associateBy { it.id }
        assertEquals(MonthlyInviteeStatus.WAITING, rows.getValue(ana).status)
        assertEquals(now, rows.getValue(ana).sentAt)
        assertEquals(2, rows.getValue(ana).attempts)
        assertEquals(MonthlyInviteeStatus.FAILED, rows.getValue(bia).status)
        assertEquals(MonthlyInviteeStatus.NO_WHATSAPP, rows.getValue(caio).status)
        assertEquals(MonthlyInviteeStatus.PAUSED, rows.getValue(davi).status)
    }

    @Test
    fun `ao subir voltam Na fila e Pausado, na ordem de chegada, e Aguardando nao`() {
        val (ana, bia, caio) = insert(owner, "Ana" to "+5511988880001", "Bia" to "+5511988880002", "Caio" to "+5511988880003")
        store.markSent(bia, 1, now)
        store.markPending(listOf(caio), MonthlyInviteeStatus.PAUSED)

        assertEquals(listOf(ana, caio), store.pendingAtStartup())

        jdbc.sql("UPDATE access_groups SET deleted_at = now() WHERE id = :g").param("g", group).update()
        assertEquals(emptyList(), store.pendingAtStartup())
    }

    private fun insert(createdBy: UUID, vararg people: Pair<String, String>): List<UUID> {
        val rows = people.map { NewMonthlyInvitee(UUID.randomUUID(), it.first, MonthlyInviteePhone.parse(it.second)!!) }
        transaction.inTransaction { invitees.insert(group, rows, MonthlyInviteeStatus.QUEUED, createdBy, UUID.randomUUID(), now) }
        return rows.map { it.id }
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, created_at, updated_at)
            VALUES (:id, :subject, true, :name, now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
