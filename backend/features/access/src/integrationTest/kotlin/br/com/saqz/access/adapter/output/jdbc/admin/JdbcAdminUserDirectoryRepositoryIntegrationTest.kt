package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.testing.allAdminDirectoryMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
    private val database = TestPostgres.migrated(*allAdminDirectoryMigrationLocations())
    private lateinit var repository: JdbcAdminUserDirectoryRepository
    private lateinit var sessions: JdbcSessionRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        repository = JdbcAdminUserDirectoryRepository(database.dataSource)
        sessions = JdbcSessionRepository(database.dataSource)
    }

    @BeforeEach
    fun clearData() {
        courtesyAdmin = null
        execute("TRUNCATE subscriptions, plan_courtesies, app_store_subscriptions, google_play_subscriptions, organizer_trials, group_memberships, access_groups, access_users CASCADE")
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
    fun `checkout nunca pago tambem e FREE na lista`() {
        val abandonou = insertUser("abandonou", name = "Nunca Pagou", email = "np@saqz.test")
        insertSubscription(abandonou, plan = "TITULAR", status = "PAST_DUE")
        execute("UPDATE subscriptions SET first_confirmed_at = NULL WHERE owner_user_id = '$abandonou'")

        val amadores = repository.list("Nunca Pagou", plan = "FREE", status = null, page = 1, size = 10)

        assertEquals(listOf("Nunca Pagou"), amadores.items.map { it.displayName })
        // No detalhe também não aparece como assinatura (nunca foi assinante) —
        // diferente da cancelada-que-pagou, que segue visível como CANCELED.
        assertNull(repository.find(abandonou)?.subscription)
    }

    @Test
    fun `cancelamento local sem webhook ainda da acesso pelo site e detalhe mostra CANCELED`() {
        val cancelado = insertUser("cancel-local", name = "Cancelou Local", email = "cl@saqz.test")
        insertSubscription(cancelado, plan = "TITULAR")
        execute("UPDATE subscriptions SET canceled_at = now() WHERE owner_user_id = '$cancelado'")

        // Segue findEntitlingPlan: status ACTIVE da acesso mesmo com canceled_at.
        assertEquals("TITULAR", repository.list(null, null, null, 1, 10).items.single().plan)
        assertEquals("CANCELED", repository.find(cancelado)?.subscription?.status)
    }

    @Test
    fun `assinatura do site cancelada no periodo pago aparece e atraso congelado vira FREE`() {
        val noPeriodo = insertUser("no-periodo", name = "No Periodo", email = "np1@saqz.test")
        val congelado = insertUser("congelado", name = "Congelado", email = "cg@saqz.test")
        insertSubscription(noPeriodo, plan = "ORGANIZADOR", status = "CANCELED")
        insertSubscription(congelado, plan = "ORGANIZADOR", status = "PAST_DUE")
        execute("UPDATE subscriptions SET past_due_since = now() - interval '30 days' WHERE owner_user_id = '$congelado'")
        execute("UPDATE subscriptions SET current_period_end = now() + interval '5 days' WHERE owner_user_id = '$noPeriodo'")

        assertEquals(listOf("No Periodo"), repository.list(null, "ORGANIZADOR", null, 1, 10).items.map { it.displayName })
        assertEquals(listOf("Congelado"), repository.list(null, "FREE", null, 1, 10).items.map { it.displayName })
    }

    @Test
    fun `plano efetivo com cortesia filtra por plano, nao entra em FREE e a contagem bate`() {
        val cortesia = insertUser("cortesia", name = "So Cortesia", email = "c@saqz.test")
        val teste = insertUser("teste", name = "So Teste", email = "t@saqz.test")
        insertUser("gratis", name = "Gratis", email = "g@saqz.test")
        val fim = Instant.parse("2099-11-30T15:00:00Z")
        insertCourtesy(cortesia, plan = "ILIMITADO", endsAt = "'$fim'")
        execute("INSERT INTO organizer_trials (owner_user_id, started_at, ends_at) VALUES ('$teste', '2099-11-16T15:00:00Z', '$fim')")

        val ilimitado = repository.list(null, "ILIMITADO", null, 1, 10)
        val free = repository.list(null, "FREE", null, 1, 10)
        val organizador = repository.list(null, "ORGANIZADOR", null, 1, 10)

        assertEquals(listOf("So Cortesia"), ilimitado.items.map { it.displayName })
        assertEquals(1, ilimitado.total)
        assertEquals(Triple("ILIMITADO", "COURTESY", fim), ilimitado.items.single().let { Triple(it.plan, it.planSource, it.planEndsAt) })
        assertEquals(listOf("Gratis"), free.items.map { it.displayName })
        assertEquals(1, free.total)
        assertEquals(Triple("ORGANIZADOR", "TRIAL", fim), organizador.items.single().let { Triple(it.plan, it.planSource, it.planEndsAt) })
        assertEquals(3, repository.list(null, null, null, 1, 10).total)
        assertNull(repository.list("Gratis", null, null, 1, 10).items.single().planSource)
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
                "asaas_subscription_id, current_period_end, first_confirmed_at, created_at, updated_at) " +
                "VALUES ('$ownerId', '$plan', 'MONTHLY', '$status', 'cus-$ownerId', 'sub-$ownerId', " +
                "'${now.plusSeconds(2_592_000)}', '$now', '$now', '$now')",
        )
    }

    private var courtesyAdmin: UUID? = null

    private fun insertCourtesy(ownerId: UUID, plan: String, endsAt: String) {
        val admin = courtesyAdmin ?: insertUser("admin", name = "Admin", email = "admin@saqz.test", deleted = true).also { courtesyAdmin = it }
        execute(
            "INSERT INTO plan_courtesies (id, owner_user_id, plan, ends_at, reason, granted_by) " +
                "VALUES ('${UUID.randomUUID()}', '$ownerId', '$plan', $endsAt, 'teste', '$admin')",
        )
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
