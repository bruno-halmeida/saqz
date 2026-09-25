package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GameOpenOnPublish
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
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Publicar o próximo jogo do grupo avisa na hora; os demais esperam o cron das 14h (VUL-259). */
class GameOpenOnPublishIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), JdbcGroupCommunicationRepository(source))

    private val owner = user("Owner")
    private val athlete = user("Athlete")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality,
                composition, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (member in listOf(owner, athlete)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", member).update()
    }

    /** O banco é compartilhado entre os métodos: cada um começa sem jogo nem mensagem. */
    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
    }

    @Test fun `publishing the next game announces it right away to the whole group`() {
        val game = draftGame(hoursAhead = 48)

        publish(game, enabled = true)

        assertEquals(1L, count("group_messages WHERE channel = 'GAME_OPEN' AND game_id = '$game'"))
        assertEquals(setOf(owner, athlete), recipients().toSet())
    }

    @Test fun `publishing a game that is not the next one waits for the daily announcement`() {
        publish(draftGame(hoursAhead = 48), enabled = false)

        publish(draftGame(hoursAhead = 96), enabled = true)

        assertEquals(0L, count("group_messages WHERE channel = 'GAME_OPEN'"))
    }

    @Test fun `the reminder switch off keeps publishing silent`() {
        publish(draftGame(hoursAhead = 48), enabled = false)

        assertEquals(0L, count("group_messages WHERE channel = 'GAME_OPEN'"))
    }

    @Test fun `published_at is stamped on publish and kept on later writes`() {
        val game = draftGame(hoursAhead = 48)
        assertNull(publishedAt(game))

        publish(game, enabled = false)
        val stamped = assertNotNull(publishedAt(game))
        jdbc.sql("UPDATE games SET title = 'Outro título', status = 'PUBLISHED' WHERE id = :id").param("id", game).update()

        assertEquals(stamped, publishedAt(game))
    }

    private fun publish(game: UUID, enabled: Boolean) {
        val lifecycle = ChangeGameLifecycle(
            transaction,
            JdbcGameOccurrenceRepository(source),
            GameSideEffects(listOf(GameOpenOnPublish(service, enabled))),
        )
        assertIs<GameCommandResult.Success>(lifecycle.execute(owner, group, game, 1, GameMutation.PUBLISH))
    }

    private fun draftGame(hoursAhead: Int): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', CAST(now() + make_interval(hours => :hours) AS date), '19:00',
                'America/Sao_Paulo', now() + make_interval(hours => :hours), 90,
                now() + make_interval(hours => :hours - 2), 'Arena', 'Rua 100', 12, 'DRAFT', now(), now())
        """).param("id", id).param("g", group).param("hours", hoursAhead).update()
        return id
    }

    private fun recipients(): List<UUID> = jdbc.sql("""
        SELECT n.recipient_id FROM group_notifications n
        JOIN group_messages m ON m.id = n.message_id
        WHERE m.channel = 'GAME_OPEN' ORDER BY n.recipient_id
    """).query { rs, _ -> rs.getObject("recipient_id", UUID::class.java) }.list()

    private fun publishedAt(game: UUID): Instant? = jdbc.sql("SELECT published_at FROM games WHERE id = :id")
        .param("id", game).query { rs, _ -> rs.getTimestamp("published_at")?.toInstant() }.list().single()

    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
