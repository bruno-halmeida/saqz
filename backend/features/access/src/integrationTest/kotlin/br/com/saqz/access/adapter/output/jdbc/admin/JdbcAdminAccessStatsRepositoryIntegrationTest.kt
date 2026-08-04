package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.application.admin.CohortWeek
import br.com.saqz.access.testing.startAndAwaitJdbc
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
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminAccessStatsRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcAdminAccessStatsRepository

    /** Segunda-feira 12h UTC: a semana corrente do cohort começa em 2026-08-03. */
    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        repository = JdbcAdminAccessStatsRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE access_users CASCADE")
    }

    @Test
    fun `totalUsers ignora contas apagadas`() {
        insertUser("vivo", createdAt = now.minusDays(1))
        insertUser("apagado", createdAt = now.minusDays(1), deletedAt = now)

        assertEquals(1, repository.totalUsers())
    }

    @Test
    fun `newUsers respeita a janela e o desde-o-inicio`() {
        insertUser("dentro", createdAt = now.minusDays(5))
        insertUser("fora", createdAt = now.minusDays(45))

        assertEquals(1, repository.newUsers(now.minusDays(30), now))
        assertEquals(2, repository.newUsers(null, now))
    }

    @Test
    fun `activeUsers usa o updated_at como proxy e ignora apagados`() {
        insertUser("ativo", createdAt = now.minusDays(90), updatedAt = now.minusDays(2))
        insertUser("parado", createdAt = now.minusDays(90), updatedAt = now.minusDays(40))
        insertUser("apagado", createdAt = now.minusDays(90), updatedAt = now.minusDays(1), deletedAt = now)

        assertEquals(1, repository.activeUsers(now.minusDays(30)))
    }

    @Test
    fun `signupCohort conta cadastros e quem entrou em grupo por semana`() {
        val monday = LocalDate.parse("2026-08-03")
        val previousMonday = monday.minusWeeks(1)

        val comGrupo = insertUser("com-grupo", createdAt = instantAt(previousMonday, hour = 10))
        insertUser("sem-grupo", createdAt = instantAt(previousMonday.plusDays(2), hour = 9))
        val dono = insertUser("dono-da-semana", createdAt = instantAt(monday, hour = 8))
        insertUser("fora-do-cohort", createdAt = instantAt(monday.minusWeeks(6), hour = 8))

        val grupo = insertGroup(dono, createdAt = now.minusDays(1))
        insertMembership(grupo, comGrupo)

        val cohort = repository.signupCohort(weeksBack = 5, now = now)

        assertEquals(5, cohort.size)
        assertEquals(monday.minusWeeks(4), cohort.first().weekStart)
        assertEquals(CohortWeek(previousMonday, signups = 2, joinedGroup = 1), cohort[3])
        assertEquals(CohortWeek(monday, signups = 1, joinedGroup = 1), cohort[4])
    }

    private fun instantAt(date: LocalDate, hour: Int): Instant =
        Instant.parse("${date}T%02d:00:00Z".format(hour))

    private fun Instant.minusDays(days: Long): Instant = minusSeconds(days * 86_400)

    private fun insertUser(
        subject: String,
        createdAt: Instant,
        updatedAt: Instant = createdAt,
        deletedAt: Instant? = null,
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at, deleted_at) " +
                "VALUES ('$id', '$subject', true, 'Nome Valido', '$createdAt', '$updatedAt', " +
                "${deletedAt?.let { "'$it'" } ?: "NULL"})",
        )
        return id
    }

    private fun insertGroup(ownerId: UUID, createdAt: Instant): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$ownerId', '${UUID.randomUUID()}', 'Grupo Valido', 'America/Sao_Paulo', " +
                "'$createdAt', '$createdAt')",
        )
        return id
    }

    private fun insertMembership(groupId: UUID, userId: UUID) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$groupId', '$userId', 'ATHLETE', now(), now())",
        )
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
