package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.ChurnStats
import br.com.saqz.subscriptions.application.PlanSplitEntry
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import br.com.saqz.subscriptions.testing.startAndAwaitJdbc
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
class JdbcAdminRevenueStatsRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcAdminRevenueStatsRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure()
            .dataSource(dataSource)
            .locations(*allSubscriptionsFeatureMigrationLocations())
            .load()
            .migrate()
        repository = JdbcAdminRevenueStatsRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE subscription_events, coupon_redemptions, subscriptions, coupons, access_users CASCADE")
    }

    @Test
    fun `revenueCents soma so pagamentos confirmados processados na janela`() {
        insertEvent(type = "PAYMENT_CONFIRMED", valueReais = "39.90", processedAt = now.minusSeconds(2 * DAY))
        insertEvent(type = "PAYMENT_CONFIRMED", valueReais = "59.90", processedAt = now.minusSeconds(3 * DAY))
        insertEvent(type = "PAYMENT_CONFIRMED", valueReais = "89.90", processedAt = now.minusSeconds(45 * DAY))
        insertEvent(type = "PAYMENT_OVERDUE", valueReais = "39.90", processedAt = now.minusSeconds(2 * DAY))
        insertEvent(type = "PAYMENT_CONFIRMED", valueReais = "39.90", processedAt = null)
        // Formatos que o ListReceipts aceita também contam: string decimal e científica.
        insertEvent(type = "PAYMENT_CONFIRMED", valueReais = "\".50\"", processedAt = now.minusSeconds(4 * DAY))
        insertEvent(type = "PAYMENT_CONFIRMED", valueReais = "\"1.999E1\"", processedAt = now.minusSeconds(5 * DAY))
        // Texto não numérico fica de fora sem derrubar a consulta.
        insertEvent(type = "PAYMENT_CONFIRMED", valueReais = "\"abc\"", processedAt = now.minusSeconds(6 * DAY))

        assertEquals(9_980 + 50 + 1_999, repository.revenueCents(now.minusSeconds(30 * DAY), now))
        assertEquals(18_970 + 50 + 1_999, repository.revenueCents(null, now))
    }

    @Test
    fun `churn conta cancelamentos na janela e ativos no inicio dela`() {
        val start = now.minusSeconds(30 * DAY)
        subscription(plan = "ORGANIZADOR", createdAt = now.minusSeconds(90 * DAY))
        subscription(plan = "ORGANIZADOR", createdAt = now.minusSeconds(90 * DAY), canceledAt = now.minusSeconds(5 * DAY))
        subscription(plan = "TITULAR", createdAt = now.minusSeconds(90 * DAY), canceledAt = now.minusSeconds(60 * DAY))
        subscription(plan = "TITULAR", createdAt = now.minusSeconds(10 * DAY))

        // Checkout abandonado: linha PAST_DUE criada no checkout, nunca confirmada.
        subscription(plan = "TITULAR", status = "PAST_DUE", createdAt = now.minusSeconds(90 * DAY), firstConfirmedAt = null)
        // Cancelar um checkout nunca pago também não é churn.
        subscription(
            plan = "TITULAR",
            status = "PAST_DUE",
            createdAt = now.minusSeconds(90 * DAY),
            canceledAt = now.minusSeconds(4 * DAY),
            firstConfirmedAt = null,
        )

        assertEquals(ChurnStats(canceled = 1, activeAtStart = 2), repository.churn(start, now))
        assertEquals(ChurnStats(canceled = 2, activeAtStart = 4), repository.churn(null, now))
    }

    @Test
    fun `planSplit mensaliza anual, aplica cupom ativo e ignora cancelada`() {
        subscription(plan = "ORGANIZADOR", cycle = "MONTHLY", createdAt = now.minusSeconds(DAY))
        subscription(plan = "ORGANIZADOR", cycle = "ANNUAL", createdAt = now.minusSeconds(DAY))
        val cupom = insertCoupon(discountPercent = 10)
        subscription(
            plan = "TITULAR",
            cycle = "MONTHLY",
            createdAt = now.minusSeconds(DAY),
            couponId = cupom,
            couponCyclesRemaining = 2,
        )
        // Estado real pós-esgotamento (ProcessAsaasWebhook): remaining vira NULL, coupon_id fica.
        subscription(
            plan = "TITULAR",
            cycle = "MONTHLY",
            createdAt = now.minusSeconds(DAY),
            couponId = cupom,
            couponCyclesRemaining = null,
        )
        subscription(plan = "ILIMITADO", cycle = "MONTHLY", createdAt = now.minusSeconds(DAY), status = "CANCELED")
        // Checkout abandonado (PAST_DUE sem confirmação) não é assinante no split.
        subscription(plan = "ILIMITADO", cycle = "MONTHLY", status = "PAST_DUE", createdAt = now.minusSeconds(DAY), firstConfirmedAt = null)
        // Cancelada localmente (canceled_at antes do webhook) também não.
        subscription(plan = "ILIMITADO", cycle = "MONTHLY", createdAt = now.minusSeconds(DAY), canceledAt = now.minusSeconds(3_600))

        val split = repository.planSplit()

        assertEquals(
            listOf(
                // 3591 = 3990 com 10%; 3990 = cupom esgotado (remaining NULL, duração finita) volta ao cheio
                PlanSplitEntry(Plan.TITULAR, subscribers = 2, mrrCents = 3_591 + 3_990),
                // 5990 mensal + 59900/12 = 4991 do anual mensalizado
                PlanSplitEntry(Plan.ORGANIZADOR, subscribers = 2, mrrCents = 5_990 + 4_991),
            ),
            split,
        )
    }

    @Test
    fun `subscribedCohort agrupa pela semana de cadastro do usuario`() {
        val monday = LocalDate.parse("2026-08-03")
        val previousWeekUser = insertUser(createdAt = Instant.parse("2026-07-28T09:00:00Z"))
        subscription(plan = "ORGANIZADOR", createdAt = now.minusSeconds(DAY), ownerId = previousWeekUser)
        insertUser(createdAt = Instant.parse("2026-07-29T09:00:00Z"))
        // Checkout nunca pago não vira "pagante" no cohort.
        val abandonado = insertUser(createdAt = Instant.parse("2026-07-28T10:00:00Z"))
        subscription(plan = "TITULAR", status = "PAST_DUE", createdAt = now.minusSeconds(DAY), firstConfirmedAt = null, ownerId = abandonado)
        // Cancelada que chegou a pagar conta como conversão histórica.
        val pagouECancelou = insertUser(createdAt = Instant.parse("2026-07-28T11:00:00Z"))
        subscription(plan = "TITULAR", createdAt = now.minusSeconds(DAY), canceledAt = now.minusSeconds(3_600), ownerId = pagouECancelou)

        val cohort = repository.subscribedCohort(weeksBack = 5, now = now)

        assertEquals(5, cohort.size)
        assertEquals(monday.minusWeeks(1), cohort[3].weekStart)
        assertEquals(2, cohort[3].subscribed)
        assertEquals(0, cohort[4].subscribed)
    }

    private fun insertUser(createdAt: Instant = now.minusSeconds(60 * DAY)): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'sub-$id', true, 'Nome Valido', '$createdAt', '$createdAt')",
        )
        return id
    }

    private fun subscription(
        plan: String,
        cycle: String = "MONTHLY",
        status: String = "ACTIVE",
        createdAt: Instant,
        canceledAt: Instant? = null,
        couponId: UUID? = null,
        couponCyclesRemaining: Int? = null,
        firstConfirmedAt: Instant? = createdAt,
        ownerId: UUID = insertUser(),
    ) {
        execute(
            """
            INSERT INTO subscriptions (
                owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id,
                current_period_end, canceled_at, coupon_id, coupon_cycles_remaining,
                first_confirmed_at, created_at, updated_at
            ) VALUES (
                '$ownerId', '$plan', '$cycle', '$status', 'cus-$ownerId', 'sub-$ownerId',
                '${now.plusSeconds(30 * DAY)}', ${canceledAt?.let { "'$it'" } ?: "NULL"},
                ${couponId?.let { "'$it'" } ?: "NULL"}, ${couponCyclesRemaining ?: "NULL"},
                ${firstConfirmedAt?.let { "'$it'" } ?: "NULL"}, '$createdAt', '$createdAt'
            )
            """.trimIndent(),
        )
    }

    private fun insertCoupon(discountPercent: Int): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO coupons (id, code, discount_percent, duration_cycles, valid_until, created_at) " +
                "VALUES ('$id', 'CUPOM$discountPercent', $discountPercent, 6, '${now.plusSeconds(90 * DAY)}', '$now')",
        )
        return id
    }

    private fun insertEvent(type: String, valueReais: String, processedAt: Instant?) {
        execute(
            "INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at) VALUES (" +
                "'${UUID.randomUUID()}', 'evt-${UUID.randomUUID()}', '$type', " +
                "'{\"payment\": {\"value\": $valueReais}}', ${processedAt?.let { "'$it'" } ?: "NULL"}, '$now')",
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
