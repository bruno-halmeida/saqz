package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.testing.allAdminDirectoryMigrationLocations
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminUserDirectoryRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcAdminUserDirectoryRepository
    private lateinit var sessions: JdbcSessionRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure()
            .dataSource(dataSource)
            .locations(*allAdminDirectoryMigrationLocations())
            .load()
            .migrate()
        repository = JdbcAdminUserDirectoryRepository(dataSource)
        sessions = JdbcSessionRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE subscriptions, group_memberships, access_groups, access_users CASCADE")
    }

    @Test
    fun `lista busca por nome ou email com paginacao e total`() {
        insertUser("a", name = "Bianca Souza", email = "bianca@saqz.test", createdAt = now.minusSeconds(300))
        insertUser("b", name = "Thiago Melo", email = "thiago@saqz.test", createdAt = now.minusSeconds(200))
        insertUser("c", name = "Outra Pessoa", email = "bianca.dois@saqz.test", createdAt = now.minusSeconds(100))

        val page = repository.list(query = "bianca", plan = null, status = null, page = 1, size = 1)

        assertEquals(2, page.total)
        assertEquals(1, page.items.size)
        assertEquals("Outra Pessoa", page.items.single().displayName)

        val secondPage = repository.list(query = "bianca", plan = null, status = null, page = 2, size = 1)
        assertEquals("Bianca Souza", secondPage.items.single().displayName)
    }

    @Test
    fun `filtra por plano real e por FREE`() {
        val pagante = insertUser("pagante", name = "Paga Plano", email = "paga@saqz.test")
        insertUser("gratis", name = "Sem Plano", email = "gratis@saqz.test")
        insertSubscription(pagante, plan = "ORGANIZADOR")

        val organizadores = repository.list(null, plan = "ORGANIZADOR", status = null, page = 1, size = 10)
        val amadores = repository.list(null, plan = "FREE", status = null, page = 1, size = 10)

        assertEquals(listOf("Paga Plano"), organizadores.items.map { it.displayName })
        assertEquals("ORGANIZADOR", organizadores.items.single().plan)
        assertEquals(listOf("Sem Plano"), amadores.items.map { it.displayName })
        assertNull(amadores.items.single().plan)
    }

    @Test
    fun `assinatura cancelada conta como FREE na lista`() {
        val cancelado = insertUser("cancelado", name = "Foi Pagante", email = "ex@saqz.test")
        insertSubscription(cancelado, plan = "TITULAR", status = "CANCELED")

        val amadores = repository.list(null, plan = "FREE", status = null, page = 1, size = 10)

        assertEquals(listOf("Foi Pagante"), amadores.items.map { it.displayName })
    }

    @Test
    fun `filtra por status de suspensao e lista ignora conta apagada`() {
        val suspenso = insertUser("suspenso", name = "Pessoa Suspensa", email = "sus@saqz.test")
        insertUser("ativo", name = "Pessoa Ativa", email = "ativa@saqz.test")
        insertUser("apagado", name = "Pessoa Apagada", email = "bye@saqz.test", deleted = true)
        repository.suspend(suspenso)

        val suspensos = repository.list(null, null, status = "suspended", page = 1, size = 10)
        val ativos = repository.list(null, null, status = "active", page = 1, size = 10)

        assertEquals(listOf("Pessoa Suspensa"), suspensos.items.map { it.displayName })
        assertTrue(suspensos.items.single().suspended)
        assertEquals(listOf("Pessoa Ativa"), ativos.items.map { it.displayName })
    }

    @Test
    fun `detalhe traz grupos com papel derivado e assinatura`() {
        val dono = insertUser("dono", name = "Dona do Grupo", email = "dona@saqz.test", phone = "+5511987654321")
        val membro = insertUser("membro", name = "Pessoa Membro", email = "membro@saqz.test")
        val grupo = insertGroup(dono, name = "Volei da Firma")
        insertMembership(grupo, dono, role = "ADMIN")
        insertMembership(grupo, membro, role = "ATHLETE")
        insertSubscription(dono, plan = "ORGANIZADOR")

        val detail = repository.find(dono)

        assertNotNull(detail)
        assertEquals("Dona do Grupo", detail.displayName)
        assertEquals("+5511987654321", detail.phone)
        assertEquals(1, detail.groups.size)
        assertEquals("OWNER", detail.groups.single().role)
        assertEquals(2, detail.groups.single().members)
        assertEquals("ORGANIZADOR", detail.subscription?.plan)

        val memberDetail = repository.find(membro)
        assertEquals("ATHLETE", memberDetail?.groups?.single()?.role)
        assertNull(memberDetail?.subscription)
    }

    @Test
    fun `suspender bloqueia a sessao e reativar libera`() {
        val user = insertUser("alvo", name = "Pessoa Alvo", email = "alvo@saqz.test")

        assertTrue(repository.suspend(user))
        assertNotNull(sessions.suspendedAt("alvo"))
        assertTrue(repository.find(user)!!.suspendedAt != null)

        assertTrue(repository.reactivate(user))
        assertNull(sessions.suspendedAt("alvo"))
        assertFalse(repository.suspend(UUID.randomUUID()))
    }

    @Test
    fun `membership em grupo apagado nao conta na lista`() {
        val pessoa = insertUser("na-lista", name = "Pessoa Lista", email = "pl@saqz.test")
        val dono = insertUser("dono-x", name = "Dono Xis", email = "dx@saqz.test")
        val vivo = insertGroup(dono, name = "Grupo Vivo")
        val morto = insertGroup(dono, name = "Grupo Morto")
        insertMembership(vivo, pessoa, role = "ATHLETE")
        insertMembership(morto, pessoa, role = "ATHLETE")
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$morto'")

        val row = repository.list("Pessoa Lista", null, null, 1, 10).items.single()

        assertEquals(1, row.memberships)
    }

    @Test
    fun `pagina alem do fim preserva o total`() {
        insertUser("um", name = "Pessoa Um", email = "um@saqz.test")
        insertUser("dois", name = "Pessoa Dois", email = "dois@saqz.test")

        val page = repository.list(null, null, null, page = 5, size = 10)

        assertEquals(2, page.total)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun `suspensao nao mexe no ultimo acesso`() {
        val alvo = insertUser("acesso", name = "Pessoa Acesso", email = "pa@saqz.test")
        val antes = repository.find(alvo)!!.lastSeenAt

        repository.suspend(alvo)
        repository.reactivate(alvo)

        assertEquals(antes, repository.find(alvo)!!.lastSeenAt)
    }

    @Test
    fun `suspender duas vezes preserva o instante original`() {
        val user = insertUser("dupla", name = "Pessoa Dupla", email = "dupla@saqz.test")

        repository.suspend(user)
        val first = repository.find(user)!!.suspendedAt
        repository.suspend(user)

        assertEquals(first, repository.find(user)!!.suspendedAt)
    }

    private fun insertUser(
        subject: String,
        name: String,
        email: String,
        phone: String? = null,
        createdAt: Instant = now.minusSeconds(600),
        deleted: Boolean = false,
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, phone, " +
                "created_at, updated_at, deleted_at) " +
                "VALUES ('$id', '$subject', '$email', true, '$name', ${phone?.let { "'$it'" } ?: "NULL"}, " +
                "'$createdAt', '$createdAt', ${if (deleted) "now()" else "NULL"})",
        )
        return id
    }

    private fun insertGroup(ownerId: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$ownerId', '${UUID.randomUUID()}', '$name', 'America/Sao_Paulo', now(), now())",
        )
        return id
    }

    private fun insertMembership(groupId: UUID, userId: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$groupId', '$userId', '$role', now(), now())",
        )
    }

    private fun insertSubscription(ownerId: UUID, plan: String, status: String = "ACTIVE") {
        execute(
            "INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, " +
                "asaas_subscription_id, current_period_end, created_at, updated_at) " +
                "VALUES ('$ownerId', '$plan', 'MONTHLY', '$status', 'cus-$ownerId', 'sub-$ownerId', " +
                "'${now.plusSeconds(2_592_000)}', '$now', '$now')",
        )
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
