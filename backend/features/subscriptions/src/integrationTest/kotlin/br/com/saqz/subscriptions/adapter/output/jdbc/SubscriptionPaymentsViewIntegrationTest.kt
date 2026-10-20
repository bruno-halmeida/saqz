package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AdminRevenueCourtesyRow
import br.com.saqz.subscriptions.application.AdminRevenueRow
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DEB-100: a view `subscription_payments` e a receita mensal por origem, real x teste. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionPaymentsViewIntegrationTest {
    private lateinit var jdbc: JdbcClient
    private lateinit var reader: JdbcAdminRevenueReader
    private lateinit var stats: JdbcAdminRevenueStatsRepository

    private data class Pay(val source: String, val gross: Long?, val net: Long?, val currency: String?, val test: Boolean, val refunded: Boolean)

    @BeforeEach
    fun resetDatabase() {
        val dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(dataSource)
        reader = JdbcAdminRevenueReader(dataSource)
        stats = JdbcAdminRevenueStatsRepository(dataSource)
    }

    @Test
    fun `Pix so com RECEIVED conta como um pagamento`() {
        val dono = user()
        event(dono, "PAYMENT_RECEIVED", "pay_pix", value = "59.90", net = "58.91")

        assertEquals(listOf(Pay("SITE", 5_990, 5_891, "BRL", test = false, refunded = false)), payments(dono))
    }

    @Test
    fun `cartao com CONFIRMED e RECEIVED do mesmo payment id conta uma vez, na data do primeiro`() {
        val dono = user()
        event(dono, "PAYMENT_RECEIVED", "pay_card", value = "59.90", net = "57.00", processedAt = "now() - interval '1 day'")
        event(dono, "PAYMENT_CONFIRMED", "pay_card", value = "59.90", net = "57.00", processedAt = "now() - interval '3 days'")

        assertEquals(1, payments(dono).size)
        val pagoEm = jdbc.sql("SELECT paid_at < now() - interval '2 days' FROM subscription_payments WHERE owner_user_id = :o")
            .param("o", dono).query(Boolean::class.java).single()
        assertEquals(true, pagoEm)
    }

    @Test
    fun `estorno marca o pagamento e tira da receita`() {
        val dono = user()
        val outro = user()
        event(dono, "PAYMENT_CONFIRMED", "pay_a", value = "39.90")
        event(dono, "PAYMENT_REFUNDED", "pay_a", value = "39.90")
        event(outro, "PAYMENT_RECEIVED", "pay_b", value = "10.00")
        event(outro, "PAYMENT_RECEIVED_IN_CASH_UNDONE", "pay_b", value = "10.00")
        event(outro, "PAYMENT_CONFIRMED", "pay_c", value = "20.00")

        assertEquals(true, payments(dono).single().refunded)
        assertEquals(listOf(true, false), payments(outro).map { it.refunded }.sortedDescending())
        assertEquals(2_000, stats.revenueCents(null, Instant.now().plusSeconds(60)))
        assertEquals(listOf(2_000L), reader.monthly(12).real.map { it.grossCents })
    }

    @Test
    fun `cupom entra pelo valor cobrado`() {
        val dono = user()
        event(dono, "PAYMENT_CONFIRMED", "pay_cupom", value = "53.91", net = "51.00", extra = ", \"originalValue\": 59.90, \"discount\": {\"value\": 5.99}")

        assertEquals(5_391, payments(dono).single().gross)
    }

    @Test
    fun `valor nao numerico ou zero nao quebra nem conta`() {
        val dono = user()
        event(dono, "PAYMENT_CONFIRMED", "pay_texto", value = "\"abc\"")
        event(dono, "PAYMENT_CONFIRMED", "pay_zero", value = "0")
        event(dono, "PAYMENT_CONFIRMED", "pay_ok", value = "\"1.999E1\"", net = "\"x\"")

        val unico = payments(dono).single()
        assertEquals(1_999L, unico.gross)
        assertNull(unico.net)
        assertEquals(1_999, stats.revenueCents(null, Instant.now().plusSeconds(60)))
    }

    @Test
    fun `evento nao processado ou de outro tipo nao conta e site sem evento nao tem pagamento`() {
        val dono = user()
        val semEvento = user()
        event(dono, "PAYMENT_CONFIRMED", "pay_pendente", value = "10.00", processedAt = "NULL")
        event(dono, "PAYMENT_OVERDUE", "pay_atraso", value = "10.00")

        assertEquals(emptyList(), payments(dono))
        assertEquals(emptyList(), payments(semEvento))
    }

    @Test
    fun `App Store sandbox e teste, producao e real, revogada e estornada e moedas ficam separadas`() {
        val dono = user()
        appStoreSubscription(dono, "otx-1", environment = "PRODUCTION")
        appStoreTransaction(dono, "otx-1", "tx-1", priceMillis = 59_900, currency = "BRL", environment = "PRODUCTION")
        appStoreTransaction(dono, "otx-1", "tx-2", priceMillis = 9_990, currency = "USD", environment = "PRODUCTION")
        appStoreTransaction(dono, "otx-1", "tx-3", priceMillis = 59_900, currency = "BRL", environment = "SANDBOX")
        appStoreTransaction(dono, "otx-1", "tx-4", priceMillis = 59_900, currency = "BRL", environment = "PRODUCTION", revoked = true)

        assertEquals(
            setOf(
                Pay("APP_STORE", 5_990, null, "BRL", test = false, refunded = false),
                Pay("APP_STORE", 999, null, "USD", test = false, refunded = false),
                Pay("APP_STORE", 5_990, null, "BRL", test = true, refunded = false),
                Pay("APP_STORE", 5_990, null, "BRL", test = false, refunded = true),
            ),
            payments(dono).toSet(),
        )

        val relatorio = reader.monthly(12)
        assertEquals(setOf("BRL" to 5_990L, "USD" to 999L), relatorio.real.map { it.currency to it.grossCents }.toSet())
        assertEquals(listOf(1L to 5_990L), relatorio.test.map { it.payments to it.grossCents })
        assertEquals(5_990, stats.revenueCents(null, Instant.now().plusSeconds(60)))
    }

    @Test
    fun `Google Play test_purchase e teste, o resto e real, sem valor`() {
        val dono = user()
        googlePlaySubscription(dono, "tok-1")
        googlePlayOrder(dono, "tok-1", "GPA.1", test = false)
        googlePlayOrder(dono, "tok-1", "GPA.2", test = true)

        assertEquals(
            setOf(Pay("GOOGLE_PLAY", null, null, null, test = false, refunded = false), Pay("GOOGLE_PLAY", null, null, null, test = true, refunded = false)),
            payments(dono).toSet(),
        )
        val relatorio = reader.monthly(12)
        assertEquals(listOf(AdminRevenueRow(currentMonth(), "GOOGLE_PLAY", null, 1, null, null)), relatorio.real)
        assertEquals(1, relatorio.test.size)
    }

    @Test
    fun `mes no fuso de Sao Paulo e o liquido so aparece quando todos informam`() {
        val dono = user()
        // 31/10 23h30 em Sao Paulo = 01/11 02h30 UTC: ainda e outubro.
        event(dono, "PAYMENT_CONFIRMED", "pay_out", value = "50.00", net = "48.00", processedAt = "'2026-11-01T02:30:00Z'")
        event(dono, "PAYMENT_CONFIRMED", "pay_nov", value = "50.00", net = "48.00", processedAt = "'2026-11-01T04:00:00Z'")
        event(dono, "PAYMENT_CONFIRMED", "pay_nov2", value = "10.00", processedAt = "'2026-11-10T12:00:00Z'")

        val meses = reader.monthly(60).real.associateBy { it.month }

        assertEquals(AdminRevenueRow("2026-10", "SITE", "BRL", 1, 5_000, 4_800), meses["2026-10"])
        assertEquals(AdminRevenueRow("2026-11", "SITE", "BRL", 2, 6_000, null), meses["2026-11"])
    }

    @Test
    fun `cortesia entra por mes como linha propria com valor cedido e nao mexe na receita real`() {
        val dono = user()
        event(dono, "PAYMENT_CONFIRMED", "pay_1", value = "50.00", net = "48.00")
        val antes = reader.monthly(4).real
        val admin = user()
        // A atravessa meses (m-2 a m0); B revogada no meio de m-1; C venceu antes da janela; D sem fim desde m-1.
        courtesy(user(), admin, "ORGANIZADOR", created = mid(2), endsAt = mid(0))
        courtesy(user(), admin, "ILIMITADO", created = mid(2), revokedAt = mid(1))
        courtesy(user(), admin, "TITULAR", created = mid(6), endsAt = mid(4))
        courtesy(user(), admin, "TITULAR", created = mid(1), endsAt = null)

        val relatorio = reader.monthly(4)

        val org = Plan.ORGANIZADOR.monthlyPriceCents
        val ilim = Plan.ILIMITADO.monthlyPriceCents
        val tit = Plan.TITULAR.monthlyPriceCents
        assertEquals(
            listOf(
                AdminRevenueCourtesyRow(monthLabel(0), 2, org + tit),
                AdminRevenueCourtesyRow(monthLabel(1), 3, org + ilim + tit),
                AdminRevenueCourtesyRow(monthLabel(2), 2, org + ilim),
            ),
            relatorio.courtesy,
        )
        // Cortesia nunca entra em bruto, liquido nem na lista real.
        assertEquals(antes, relatorio.real)
        assertEquals(5_000, stats.revenueCents(null, Instant.now().plusSeconds(60)))
        assertEquals(emptyList(), reader.monthly(1).courtesy.filter { it.month != monthLabel(0) })
    }

    private fun currentMonth(): String =
        jdbc.sql("SELECT to_char(now() AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM')").query(String::class.java).single()

    private fun payments(dono: UUID): List<Pay> =
        jdbc.sql("SELECT source, gross_cents, net_cents, currency, test, refunded FROM subscription_payments WHERE owner_user_id = :o")
            .param("o", dono)
            .query { rs, _ ->
                Pay(
                    rs.getString("source"),
                    rs.getObject("gross_cents")?.let { (it as Number).toLong() },
                    rs.getObject("net_cents")?.let { (it as Number).toLong() },
                    rs.getString("currency"),
                    rs.getBoolean("test"),
                    rs.getBoolean("refunded"),
                )
            }
            .list()

    /** Dia 5 do mês de São Paulo `n` meses atrás, como expressão de timestamptz. */
    private fun mid(n: Int): String =
        "((date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo') - interval '$n month' + interval '5 days') AT TIME ZONE 'America/Sao_Paulo')"

    private fun monthLabel(n: Int): String =
        jdbc.sql("SELECT to_char(date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo') - interval '$n month', 'YYYY-MM')")
            .query(String::class.java).single()

    private fun courtesy(dono: UUID, admin: UUID, plan: String, created: String, endsAt: String? = null, revokedAt: String? = null) {
        jdbc.sql(
            "INSERT INTO plan_courtesies (id, owner_user_id, plan, ends_at, reason, granted_by, created_at, revoked_at, revoked_by) " +
                "VALUES (:id, :o, '$plan', ${endsAt ?: "NULL"}, 'teste', :a, $created, ${revokedAt ?: "NULL"}, ${if (revokedAt != null) ":a" else "NULL"})",
        ).param("id", UUID.randomUUID()).param("o", dono).param("a", admin).update()
    }

    private fun user(): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES (:id, :subject, true, 'Owner', now(), now())",
        ).param("id", id).param("subject", "subject-$id").update()
        return id
    }

    private fun event(
        dono: UUID,
        type: String,
        paymentId: String,
        value: String,
        net: String? = null,
        processedAt: String = "now()",
        extra: String = "",
    ) {
        val netPart = net?.let { ", \"netValue\": $it" } ?: ""
        jdbc.sql(
            "INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at, owner_user_id) VALUES " +
                "(:id, :evt, '$type', :payload, $processedAt, now(), :owner)",
        )
            .param("id", UUID.randomUUID())
            .param("evt", "evt-${UUID.randomUUID()}")
            .param("payload", "{\"event\": \"$type\", \"payment\": {\"id\": \"$paymentId\", \"value\": $value$netPart$extra}}")
            .param("owner", dono)
            .update()
    }

    private fun appStoreSubscription(dono: UUID, original: String, environment: String) {
        jdbc.sql(
            "INSERT INTO app_store_subscriptions (original_transaction_id, owner_user_id, environment, product_id, plan, cycle, " +
                "latest_transaction_id, latest_purchase_date, latest_signed_at, expires_at) " +
                "VALUES (:tx, :o, '$environment', 'p', 'ORGANIZADOR', 'MONTHLY', :tx, now(), now(), now() + interval '10 days')",
        ).param("tx", original).param("o", dono).update()
    }

    private fun appStoreTransaction(
        dono: UUID, original: String, id: String, priceMillis: Long, currency: String, environment: String, revoked: Boolean = false,
    ) {
        jdbc.sql(
            "INSERT INTO app_store_transactions (transaction_id, original_transaction_id, owner_user_id, product_id, purchase_date, " +
                "price_millis, currency, revoked_at, environment) " +
                "VALUES (:id, :orig, :o, 'p', now(), $priceMillis, '$currency', ${if (revoked) "now()" else "NULL"}, '$environment')",
        ).param("id", id).param("orig", original).param("o", dono).update()
    }

    private fun googlePlaySubscription(dono: UUID, token: String) {
        jdbc.sql(
            "INSERT INTO google_play_subscriptions (purchase_token, owner_user_id, product_id, base_plan_id, plan, cycle, state, expires_at) " +
                "VALUES (:t, :o, 'p', 'b', 'ORGANIZADOR', 'MONTHLY', 'ACTIVE', now() + interval '10 days')",
        ).param("t", token).param("o", dono).update()
    }

    private fun googlePlayOrder(dono: UUID, token: String, orderId: String, test: Boolean) {
        jdbc.sql(
            "INSERT INTO google_play_orders (order_id, purchase_token, owner_user_id, product_id, base_plan_id, test_purchase) " +
                "VALUES (:id, :t, :o, 'p', 'b', $test)",
        ).param("id", orderId).param("t", token).param("o", dono).update()
    }
}
