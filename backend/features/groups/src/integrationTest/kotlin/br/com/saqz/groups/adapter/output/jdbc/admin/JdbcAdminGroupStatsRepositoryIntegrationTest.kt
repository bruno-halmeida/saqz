package br.com.saqz.groups.adapter.output.jdbc.admin

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminGroupStatsRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcAdminGroupStatsRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .load()
            .migrate()
        repository = JdbcAdminGroupStatsRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE games, access_groups, access_users CASCADE")
    }

    @Test
    fun `conta publicados e completados com inicio passado, dentro da janela`() {
        val group = insertGroup()
        insertGame(group, startsAt = now.minusSeconds(2 * DAY), status = "PUBLISHED")
        insertGame(group, startsAt = now.minusSeconds(5 * DAY), status = "COMPLETED")
        insertGame(group, startsAt = now.minusSeconds(3 * DAY), status = "CANCELLED")
        insertGame(group, startsAt = now.minusSeconds(4 * DAY), status = "DRAFT")
        insertGame(group, startsAt = now.minusSeconds(45 * DAY), status = "PUBLISHED")
        insertGame(group, startsAt = now.plusSeconds(2 * DAY), status = "PUBLISHED")

        assertEquals(2, repository.gamesPlayed(now.minusSeconds(30 * DAY), now))
        assertEquals(3, repository.gamesPlayed(null, now))
    }

    @Test
    fun `activeGroups ignora apagados e groupsCreated respeita a janela`() {
        // created_at explícito e amarrado ao "now" do fixture: com now() do banco o
        // teste apodrecia assim que o relógio real cruzasse a janela fixa.
        val vivo = insertGroup(createdAt = now.minusSeconds(60 * DAY))
        insertGroup(createdAt = now.minusSeconds(2 * DAY))
        val apagado = insertGroup(createdAt = now.minusSeconds(3 * DAY))
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$apagado'")

        assertEquals(2, repository.activeGroups())
        assertEquals(2, repository.groupsCreated(now.minusSeconds(30 * DAY), now.plusSeconds(DAY)))
        assertEquals(3, repository.groupsCreated(null, now.plusSeconds(DAY)))
    }

    private fun insertGroup(createdAt: Instant = now): UUID {
        val owner = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$owner', 'sub-$owner', true, 'Nome Valido', now(), now())",
        )
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Grupo Valido', 'America/Sao_Paulo', '$createdAt', '$createdAt')",
        )
        return group
    }

    private fun insertGame(groupId: UUID, startsAt: Instant, status: String) {
        execute(
            """
            INSERT INTO games (
                id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at
            ) VALUES (
                '${UUID.randomUUID()}', '$groupId', 'Jogo da Semana', '2026-07-01', '19:30', 'America/Sao_Paulo',
                '$startsAt', 90, '$startsAt', 'Quadra Central', 'Rua dos Esportes, 100', 12, '$status', now(), now()
            )
            """.trimIndent(),
        )
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    private companion object {
        const val DAY = 86_400L
    }
}
