package br.com.saqz.groups.adapter.output.jdbc.admin

import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
    // O diretório junta o plano do organizador: precisa das migrações de subscriptions também.
    private val database = TestPostgres.migrated(
        "filesystem:" + migrations("access"),
        "filesystem:" + migrations("groups"),
        "filesystem:" + migrations("subscriptions"),
    )
    private lateinit var repository: JdbcAdminGroupDirectoryRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        repository = JdbcAdminGroupDirectoryRepository(database.dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE game_attendance, games, subscriptions, plan_courtesies, app_store_subscriptions, google_play_subscriptions, organizer_trials, group_memberships, access_groups, access_users CASCADE")
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
    fun `dono com checkout nunca pago fica sem plano`() {
        val dono = insertUser("nunca-pagou", name = "Nunca Pagou")
        insertGroup(dono, name = "Grupo Sem Pagamento", createdAt = now)
        insertSubscription(dono, plan = "ORGANIZADOR")
        execute("UPDATE subscriptions SET status = 'PAST_DUE', first_confirmed_at = NULL WHERE owner_user_id = '$dono'")

        assertNull(repository.list(null, null, 1, 10).items.single().ownerPlan)
    }

    @Test
    fun `assinatura do site cancelada ainda no periodo pago aparece`() {
        val dono = insertUser("cancelada-no-periodo", name = "Cancelou no Periodo")
        val grupo = insertGroup(dono, name = "Grupo Periodo", createdAt = now)
        insertSubscription(dono, plan = "ORGANIZADOR")
        execute("UPDATE subscriptions SET status = 'CANCELED', current_period_end = now() + interval '5 days' WHERE owner_user_id = '$dono'")

        assertEquals("ORGANIZADOR", repository.list(null, null, 1, 10).items.single().ownerPlan)
        assertEquals("SITE", repository.find(grupo)?.ownerPlanSource)
    }

    @Test
    fun `assinatura do site em atraso ja congelado fica sem plano`() {
        val dono = insertUser("congelada", name = "Atraso Congelado")
        insertGroup(dono, name = "Grupo Congelado", createdAt = now)
        insertSubscription(dono, plan = "ORGANIZADOR")
        execute("UPDATE subscriptions SET status = 'PAST_DUE', past_due_since = now() - interval '30 days' WHERE owner_user_id = '$dono'")

        assertNull(repository.list(null, null, 1, 10).items.single().ownerPlan)
    }

    @Test
    fun `plano efetivo traz a origem e o fim da cortesia e do teste`() {
        val comCortesia = insertUser("cortesia", name = "Com Cortesia")
        val semPrazo = insertUser("sem-prazo", name = "Cortesia Sem Prazo")
        val emTeste = insertUser("teste", name = "Em Teste")
        val grupoA = insertGroup(comCortesia, name = "Grupo A", createdAt = now)
        val grupoB = insertGroup(semPrazo, name = "Grupo B", createdAt = now)
        val grupoC = insertGroup(emTeste, name = "Grupo C", createdAt = now)
        val fim = Instant.parse("2099-11-30T15:00:00Z")
        insertCourtesy(comCortesia, plan = "ORGANIZADOR", endsAt = "'$fim'")
        insertCourtesy(semPrazo, plan = "ILIMITADO", endsAt = "NULL")
        execute(
            "INSERT INTO organizer_trials (owner_user_id, started_at, ends_at) VALUES " +
                "('$emTeste', '2099-11-16T15:00:00Z', '2099-11-30T15:00:00Z')",
        )

        val a = repository.find(grupoA)!!
        val b = repository.find(grupoB)!!
        val c = repository.list(null, null, 1, 10).items.single { it.groupId == grupoC }

        assertEquals(Triple("ORGANIZADOR", "COURTESY", fim), Triple(a.ownerPlan, a.ownerPlanSource, a.ownerPlanEndsAt))
        assertEquals(Triple("ILIMITADO", "COURTESY", null), Triple(b.ownerPlan, b.ownerPlanSource, b.ownerPlanEndsAt))
        assertEquals(Triple("ORGANIZADOR", "TRIAL", fim), Triple(c.ownerPlan, c.ownerPlanSource, c.ownerPlanEndsAt))
    }

    @Test
    fun `site TITULAR com cortesia ILIMITADO vale a cortesia e empate fica com o site`() {
        val maior = insertUser("maior", name = "Cortesia Maior")
        val empate = insertUser("empate", name = "Empate")
        val grupoMaior = insertGroup(maior, name = "Grupo Maior", createdAt = now)
        val grupoEmpate = insertGroup(empate, name = "Grupo Empate", createdAt = now)
        insertSubscription(maior, plan = "TITULAR")
        insertCourtesy(maior, plan = "ILIMITADO", endsAt = "NULL")
        insertSubscription(empate, plan = "ORGANIZADOR")
        insertCourtesy(empate, plan = "ORGANIZADOR", endsAt = "NULL")

        assertEquals("ILIMITADO" to "COURTESY", repository.find(grupoMaior)!!.let { it.ownerPlan to it.ownerPlanSource })
        assertEquals("ORGANIZADOR" to "SITE", repository.find(grupoEmpate)!!.let { it.ownerPlan to it.ownerPlanSource })
    }

    @Test
    fun `cortesia revogada ou vencida e dono sem nada ficam sem plano`() {
        val revogada = insertUser("revogada", name = "Revogada")
        val vencida = insertUser("vencida", name = "Vencida")
        insertGroup(revogada, name = "Grupo Revogada", createdAt = now)
        insertGroup(vencida, name = "Grupo Vencida", createdAt = now)
        insertCourtesy(revogada, plan = "ILIMITADO", endsAt = "NULL", revoked = true)
        insertCourtesy(vencida, plan = "ILIMITADO", endsAt = "now() - interval '1 day'")

        repository.list(null, null, 1, 10).items.forEach {
            assertNull(it.ownerPlan)
            assertNull(it.ownerPlanSource)
        }
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
                "asaas_subscription_id, current_period_end, first_confirmed_at, created_at, updated_at) " +
                "VALUES ('$ownerId', '$plan', 'MONTHLY', 'ACTIVE', 'cus-$ownerId', 'sub-$ownerId', " +
                "'${now.plusSeconds(2_592_000)}', now(), now(), now())",
        )
    }

    private fun insertCourtesy(ownerId: UUID, plan: String, endsAt: String, revoked: Boolean = false) {
        val admin = insertUser("admin-${UUID.randomUUID()}", name = "Admin")
        execute(
            "INSERT INTO plan_courtesies (id, owner_user_id, plan, ends_at, reason, granted_by, revoked_at, revoked_by) " +
                "VALUES ('${UUID.randomUUID()}', '$ownerId', '$plan', $endsAt, 'teste', '$admin', " +
                "${if (revoked) "now()" else "NULL"}, ${if (revoked) "'$admin'" else "NULL"})",
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
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
