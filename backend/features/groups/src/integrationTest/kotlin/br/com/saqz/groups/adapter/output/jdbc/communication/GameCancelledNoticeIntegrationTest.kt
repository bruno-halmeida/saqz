package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GameCancelledNotice
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.GameSideEffects
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Cancelar jogo publicado avisa confirmados e lista de espera; rascunho cancelado não avisa ninguém. */
class GameCancelledNoticeIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), JdbcGroupCommunicationRepository(source))

    private val owner = user("Owner")
    private val confirmed = user("Confirmed")
    private val waiting = user("Waiting")
    private val declined = user("Declined")
    private val silent = user("Silent")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality,
                composition, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (member in listOf(owner, confirmed, waiting, declined, silent)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", member).update()
    }

    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
    }

    @Test fun `cancelling a published game warns confirmed and waitlisted with the game text`() {
        val game = game("PUBLISHED")
        attend(game, owner, "CONFIRMED", null)
        attend(game, confirmed, "CONFIRMED", null)
        attend(game, waiting, "WAITLISTED", 1)
        attend(game, declined, "DECLINED", null)

        cancel(game)

        assertEquals(setOf(confirmed, waiting), recipients().toSet())
        val day = LocalDate.now().plusDays(2)
        val weekday = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")[day.dayOfWeek.value - 1]
        val expected = "O jogo de $weekday %02d/%02d às 19h00 foi cancelado.".format(day.dayOfMonth, day.monthValue)
        assertEquals(listOf(expected), jdbc.sql("SELECT body FROM group_messages WHERE channel = 'NOTICE' AND game_id = :g")
            .param("g", game).query(String::class.java).list())
    }

    @Test fun `cancelling a draft warns nobody`() {
        val game = game("DRAFT")

        cancel(game)

        assertEquals(0L, jdbc.sql("SELECT count(*) FROM group_messages").query(Long::class.java).single())
    }

    @Test fun `a linked whatsapp group receives the cancellation text through the queue`() {
        jdbc.sql("""
            INSERT INTO group_whatsapp_bindings (group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, created_by)
            VALUES (:g, '1203@g.us', 'invite', 'Vôlei', '5511@s.whatsapp.net', true, :owner)
        """).param("g", group).param("owner", owner).update()
        val game = game("PUBLISHED")
        attend(game, confirmed, "CONFIRMED", null)

        cancel(game)

        assertEquals(1L, jdbc.sql("SELECT count(*) FROM notification_whatsapp_group_queue q JOIN group_messages m ON m.id = q.message_id WHERE m.game_id = :g")
            .param("g", game).query(Long::class.java).single())
        jdbc.sql("DELETE FROM notification_whatsapp_group_queue").update()
        jdbc.sql("DELETE FROM group_whatsapp_bindings").update()
    }

    private fun cancel(game: UUID) {
        val lifecycle = ChangeGameLifecycle(
            transaction,
            JdbcGameOccurrenceRepository(source),
            GameSideEffects(listOf(GameCancelledNotice(service))),
        )
        assertIs<GameCommandResult.Success>(lifecycle.execute(owner, group, game, 1, GameMutation.CANCEL))
    }

    private fun recipients(): List<UUID> = jdbc.sql("""
        SELECT n.recipient_id FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
        WHERE m.channel = 'NOTICE' ORDER BY n.recipient_id
    """).query { rs, _ -> rs.getObject("recipient_id", UUID::class.java) }.list()

    private fun attend(game: UUID, member: UUID, status: String, sequence: Int?) {
        jdbc.sql("""
            INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, member_display_name)
            VALUES (:game, :g, :m, :status, :seq, now(), now(), 'Member')
        """).param("game", game).param("g", group).param("m", member)
            .param("status", status, java.sql.Types.OTHER).param("seq", sequence).update()
    }

    private fun game(status: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', CURRENT_DATE + 2, '19:00', 'America/Sao_Paulo', now() + interval '48 hours', 90,
                now() + interval '24 hours', 'Arena', 'Rua 100', 12, :status, now(), now())
        """).param("id", id).param("g", group).param("status", status, java.sql.Types.OTHER).update()
        return id
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
