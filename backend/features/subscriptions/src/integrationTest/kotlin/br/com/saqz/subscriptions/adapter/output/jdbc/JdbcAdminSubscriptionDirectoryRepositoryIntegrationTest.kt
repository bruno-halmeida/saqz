package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminSubscriptionDirectoryRepositoryIntegrationTest {
    private val database = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations())
    private lateinit var repository: JdbcAdminSubscriptionDirectoryRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        repository = JdbcAdminSubscriptionDirectoryRepository(database.dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE subscription_events, coupon_redemptions, subscriptions, coupons, access_users CASCADE")
    }

    @Test
    fun `lista busca pelo dono com preco efetivo e filtros de plano e status`() {
        val bianca = insertUser(name = "Bianca Souza", email = "bianca@saqz.test")
        val thiago = insertUser(name = "Thiago Melo", email = "thiago@saqz.test")
        insertSubscription(bianca, plan = "ORGANIZADOR", cycle = "MONTHLY", status = "PAST_DUE", createdAt = now.minusSeconds(100))
        insertSubscription(thiago, plan = "ILIMITADO", cycle = "ANNUAL", createdAt = now.minusSeconds(200))

        val todos = repository.list(null, null, null, page = 1, size = 10)
        val soPastDue = repository.list(null, null, status = "PAST_DUE", page = 1, size = 10)
        val porNome = repository.list("thiago", null, null, page = 1, size = 10)

        assertEquals(2, todos.total)
        assertEquals(listOf("Bianca Souza", "Thiago Melo"), todos.items.map { it.ownerName })
        assertEquals(5_990, todos.items[0].priceCents)
        assertEquals(89_900, todos.items[1].priceCents)
        assertEquals(listOf("Bianca Souza"), soPastDue.items.map { it.ownerName })
        assertEquals(listOf("ILIMITADO"), porNome.items.map { it.plan })
    }

    @Test
    fun `cupom ativo aplica desconto e esgotado volta ao preco cheio sem expor o codigo`() {
        val cupom = insertCoupon(code = "GALERA10", discountPercent = 10)
        val comDesconto = insertUser(name = "Com Desconto", email = "cd@saqz.test")
        val esgotado = insertUser(name = "Cupom Esgotado", email = "ce@saqz.test")
        insertSubscription(comDesconto, plan = "TITULAR", couponId = cupom, couponCyclesRemaining = 2, createdAt = now)
        // Estado real pós-esgotamento: remaining vira NULL, coupon_id fica.
        insertSubscription(esgotado, plan = "TITULAR", couponId = cupom, couponCyclesRemaining = null, createdAt = now)

        val detalheDesconto = repository.find(comDesconto)!!.summary
        val detalheEsgotado = repository.find(esgotado)!!.summary

        assertEquals(3_591, detalheDesconto.priceCents)
        assertEquals("GALERA10", detalheDesconto.couponCode)
        assertEquals(3_990, detalheEsgotado.priceCents)
        assertNull(detalheEsgotado.couponCode)
    }

    @Test
    fun `cancelamento local sem webhook ja aparece como cancelada`() {
        val dono = insertUser(name = "Cancelou Agora", email = "ca@saqz.test")
        insertSubscription(dono, plan = "TITULAR", createdAt = now, canceledAt = now.minusSeconds(600))

        val canceladas = repository.list(null, null, status = "CANCELED", page = 1, size = 10)
        val ativas = repository.list(null, null, status = "ACTIVE", page = 1, size = 10)

        assertEquals(listOf("Cancelou Agora"), canceladas.items.map { it.ownerName })
        assertEquals("CANCELED", repository.find(dono)!!.summary.status)
        assertEquals(0, ativas.total)
    }

    @Test
    fun `pagina alem do fim preserva o total`() {
        val dono = insertUser(name = "Uma Pessoa", email = "up@saqz.test")
        insertSubscription(dono, plan = "ORGANIZADOR", createdAt = now)

        val page = repository.list(null, null, null, page = 3, size = 10)

        assertEquals(1, page.total)
        assertEquals(0, page.items.size)
    }

    @Test
    fun `detalhe traz recibos confirmados do dono em ordem decrescente`() {
        val dona = insertUser(name = "Dona Recibos", email = "dr@saqz.test")
        val outra = insertUser(name = "Outra Pessoa", email = "op@saqz.test")
        insertSubscription(dona, plan = "ORGANIZADOR", createdAt = now)
        insertEvent(dona, valueReais = "59.90", processedAt = now.minusSeconds(200_000))
        // Payload malformado (valor textual) não pode derrubar a consulta.
        execute(
            "INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at, owner_user_id) " +
                "VALUES ('${UUID.randomUUID()}', 'evt-${UUID.randomUUID()}', 'PAYMENT_CONFIRMED', " +
                "'{\"payment\": {\"value\": \"abc\"}}', '${now.minusSeconds(300_000)}', '$now', '$dona')",
        )
        insertEvent(dona, valueReais = "59.90", processedAt = now.minusSeconds(100_000))
        insertEvent(outra, valueReais = "39.90", processedAt = now.minusSeconds(50_000))
        insertEvent(dona, valueReais = "59.90", processedAt = null)

        val detail = repository.find(dona)

        assertNotNull(detail)
        assertEquals(3, detail.receipts.size)
        assertEquals(5_990, detail.receipts.first().valueCents)
        assertNull(detail.receipts.last().valueCents)
        assertEquals(now.minusSeconds(100_000), detail.receipts.first().processedAt)
        assertNull(repository.find(UUID.randomUUID()))
    }

    private fun insertUser(name: String, email: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'sub-$id', '$email', true, '$name', '$now', '$now')",
        )
        return id
    }

    private fun insertSubscription(
        ownerId: UUID,
        plan: String,
        cycle: String = "MONTHLY",
        status: String = "ACTIVE",
        createdAt: Instant,
        canceledAt: Instant? = null,
        couponId: UUID? = null,
        couponCyclesRemaining: Int? = null,
    ) {
        execute(
            """
            INSERT INTO subscriptions (
                owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id,
                current_period_end, past_due_since, canceled_at, coupon_id, coupon_cycles_remaining,
                created_at, updated_at
            ) VALUES (
                '$ownerId', '$plan', '$cycle', '$status', 'cus-$ownerId', 'sub-$ownerId',
                '${now.plusSeconds(2_592_000)}', ${if (status == "PAST_DUE") "'$now'" else "NULL"},
                ${canceledAt?.let { "'$it'" } ?: "NULL"},
                ${couponId?.let { "'$it'" } ?: "NULL"}, ${couponCyclesRemaining ?: "NULL"},
                '$createdAt', '$createdAt'
            )
            """.trimIndent(),
        )
    }

    private fun insertCoupon(code: String, discountPercent: Int): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO coupons (id, code, discount_percent, duration_cycles, valid_until, created_at) " +
                "VALUES ('$id', '$code', $discountPercent, 6, '${now.plusSeconds(7_776_000)}', '$now')",
        )
        return id
    }

    private fun insertEvent(ownerId: UUID, valueReais: String, processedAt: Instant?) {
        execute(
            "INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at, owner_user_id) " +
                "VALUES ('${UUID.randomUUID()}', 'evt-${UUID.randomUUID()}', 'PAYMENT_CONFIRMED', " +
                "'{\"payment\": {\"value\": $valueReais}}', ${processedAt?.let { "'$it'" } ?: "NULL"}, '$now', '$ownerId')",
        )
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
