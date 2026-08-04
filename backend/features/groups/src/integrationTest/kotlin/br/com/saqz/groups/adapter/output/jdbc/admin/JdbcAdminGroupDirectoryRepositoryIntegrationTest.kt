package br.com.saqz.groups.adapter.output.jdbc.admin

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
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminGroupDirectoryRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcAdminGroupDirectoryRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        // O diretório junta o plano do organizador: precisa das migrações de subscriptions também.
        Flyway.configure()
            .dataSource(dataSource)
            .locations(
                "filesystem:" + migrations("access"),
                "filesystem:" + migrations("groups"),
                "filesystem:" + migrations("subscriptions"),
            )
            .load()
            .migrate()
        repository = JdbcAdminGroupDirectoryRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE game_attendance, games, subscriptions, group_memberships, access_groups, access_users CASCADE")
    }

    @Test
    fun `lista busca por nome do grupo ou do dono com plano e contagens`() {
        val dona = insertUser("dona", name = "Camila Rocha")
        val outra = insertUser("outra", name = "Pessoa Outra")
        val grupo = insertGroup(dona, name = "Volei do CERET", createdAt = now.minusSeconds(200))
        insertGroup(outra, name = "Racha da Rede", createdAt = now.minusSeconds(100))
        insertMembership(grupo, dona)
        insertSubscription(dona, plan = "ORGANIZADOR")
        insertGame(grupo, startsAt = now.minusSeconds(86_400), status = "PUBLISHED")

        val porGrupo = repository.list(query = "ceret", status = null, page = 1, size = 10)
        val porDona = repository.list(query = "camila", status = null, page = 1, size = 10)

        assertEquals(1, porGrupo.total)
        val item = porGrupo.items.single()
        assertEquals("Volei do CERET", item.name)
        assertEquals("Camila Rocha", item.ownerName)
        assertEquals("ORGANIZADOR", item.ownerPlan)
        assertEquals(1, item.members)
        assertEquals(1, item.gamesPlayed)
        assertEquals(listOf("Volei do CERET"), porDona.items.map { it.name })
    }

    @Test
    fun `dono sem assinatura aparece sem plano`() {
        val dono = insertUser("gratis", name = "Sem Plano")
        insertGroup(dono, name = "Grupo Gratis", createdAt = now)

        assertNull(repository.list(null, null, 1, 10).items.single().ownerPlan)
    }

    @Test
    fun `lista default esconde apagados e o filtro deleted os expoe`() {
        val dono = insertUser("dono", name = "Dono Comum")
        insertGroup(dono, name = "Grupo Vivo", createdAt = now)
        val apagado = insertGroup(dono, name = "Grupo Apagado", createdAt = now)
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$apagado'")

        assertEquals(listOf("Grupo Vivo"), repository.list(null, null, 1, 10).items.map { it.name })
        assertEquals(
            listOf("Grupo Apagado"),
            repository.list(null, status = "deleted", page = 1, size = 10).items.map { it.name },
        )
    }

    @Test
    fun `pagina alem do fim preserva o total`() {
        val dono = insertUser("dono-p", name = "Dono Paginas")
        insertGroup(dono, name = "Grupo Unico", createdAt = now)

        val page = repository.list(null, null, page = 4, size = 10)

        assertEquals(1, page.total)
        assertEquals(0, page.items.size)
    }

    @Test
    fun `detalhe traz ultimos jogos com confirmados, sem rascunho`() {
        val dono = insertUser("dono", name = "Dona Detalhe")
        val membro = insertUser("membro", name = "Membro Um")
        val grupo = insertGroup(dono, name = "Grupo Detalhe", createdAt = now)
        insertMembership(grupo, dono)
        insertMembership(grupo, membro)
        val jogo = insertGame(grupo, startsAt = now.minusSeconds(86_400), status = "PUBLISHED")
        insertGame(grupo, startsAt = now.minusSeconds(172_800), status = "DRAFT")
        // Futuro em relação ao relógio do banco (a query usa now() real, não o fixture).
        insertGame(grupo, startsAt = Instant.now().plusSeconds(30 * 86_400), status = "PUBLISHED")
        insertAttendance(grupo, jogo, dono, status = "CONFIRMED")
        insertAttendance(grupo, jogo, membro, status = "DECLINED")

        val detail = repository.find(grupo)

        assertNotNull(detail)
        assertEquals(2, detail.members)
        assertEquals(1, detail.lastGames.size)
        assertEquals(1, detail.lastGames.single().confirmed)
        assertEquals("PUBLISHED", detail.lastGames.single().status)
        assertNull(repository.find(UUID.randomUUID()))
    }

    private fun insertUser(subject: String, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject', true, '$name', now(), now())",
        )
        return id
    }

    private fun insertGroup(ownerId: UUID, name: String, createdAt: Instant): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$ownerId', '${UUID.randomUUID()}', '$name', 'America/Sao_Paulo', '$createdAt', '$createdAt')",
        )
        return id
    }

    private fun insertMembership(groupId: UUID, userId: UUID) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$groupId', '$userId', 'ATHLETE', now(), now())",
        )
    }

    private fun insertSubscription(ownerId: UUID, plan: String) {
        execute(
            "INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, " +
                "asaas_subscription_id, current_period_end, created_at, updated_at) " +
                "VALUES ('$ownerId', '$plan', 'MONTHLY', 'ACTIVE', 'cus-$ownerId', 'sub-$ownerId', " +
                "'${now.plusSeconds(2_592_000)}', now(), now())",
        )
    }

    private fun insertGame(groupId: UUID, startsAt: Instant, status: String): UUID {
        val id = UUID.randomUUID()
        execute(
            """
            INSERT INTO games (
                id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at
            ) VALUES (
                '$id', '$groupId', 'Jogo da Semana', '2026-07-01', '19:30', 'America/Sao_Paulo',
                '$startsAt', 90, '$startsAt', 'Quadra Central', 'Rua dos Esportes, 100', 12, '$status', now(), now()
            )
            """.trimIndent(),
        )
        return id
    }

    private fun insertAttendance(groupId: UUID, gameId: UUID, userId: UUID, status: String) {
        execute(
            "INSERT INTO game_attendance (game_id, group_id, member_user_id, member_display_name, status, " +
                "responded_at, updated_at) " +
                "VALUES ('$gameId', '$groupId', '$userId', 'Nome Presenca', '$status', now(), now())",
        )
    }

    private fun migrations(feature: String): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        val candidates = listOf(
            workingDirectory.resolve("backend/features/$feature/src/main/resources/db/migration"),
            workingDirectory.resolve("features/$feature/src/main/resources/db/migration"),
            workingDirectory.resolve("../$feature/src/main/resources/db/migration").normalize(),
        )
        return candidates.firstOrNull(Files::isDirectory)
            ?: error("Cannot find $feature migrations from $workingDirectory")
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
