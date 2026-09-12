package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditions
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditionsPublisher
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class FinancialConditionsPublicationIntegrationTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z")

    @Test
    fun `concurrent publication retries commit one immutable version with attributable audit`() {
        val f = fixture()
        val request = FinancialRequest(UUID.randomUUID(), UUID.randomUUID())
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val attempts = (1..2).map { pool.submit<FinancialResult<PublishedFinancialCondition>> {
                start.await()
                f.publisher.terms(request, "v1", "Test terms only", now, now)
            } }
            start.countDown()
            val results = attempts.map { it.get(10, TimeUnit.SECONDS) }
            assertIs<FinancialResult.Success<*>>(results.first())
            assertEquals(results.first(), results.last())
            assertEquals(results.first(), f.publisher.terms(request, "v1", "Test terms only", now, now.plusSeconds(100)))
        }
        assertEquals(1L, f.count("receivable_terms"))
        assertEquals(1L, f.count("receivable_condition_publications"))
        assertEquals(request.actorUserId, f.jdbc.sql("SELECT actor_user_id FROM receivable_condition_publications")
            .query(UUID::class.java).single())
        assertEquals("Test terms only", f.conditions.terms("v1", now)!!.content)
        assertEquals(64, f.conditions.terms("v1", now)!!.contentSha256.length)
        assertFails { f.jdbc.sql("DELETE FROM receivable_condition_publications").update() }
        assertFails { f.jdbc.sql("UPDATE receivable_terms SET content='changed'").update() }
    }

    @Test
    fun `different actor content or version cannot reuse a publication request and conflicts leave no audit`() {
        val f = fixture()
        val request = FinancialRequest(UUID.randomUUID(), UUID.randomUUID())
        assertIs<FinancialResult.Success<*>>(f.publisher.terms(request, "v1", "Original", now, now))
        fun conflict(result: FinancialResult<*>) = assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(result).error)
        conflict(f.publisher.terms(request.copy(actorUserId = UUID.randomUUID()), "v1", "Original", now, now))
        conflict(f.publisher.terms(request, "v1", "Changed", now, now))
        conflict(f.publisher.terms(request, "v2", "Original", now, now))
        conflict(f.publisher.terms(request.copy(requestId = UUID.randomUUID()), "v1", "Original", now, now))
        assertEquals(1L, f.count("receivable_condition_publications"))
        assertEquals(1L, f.count("receivable_terms"))
        assertEquals("Original", f.conditions.terms("v1", now)!!.content)
    }

    @Test
    fun `published future rates take effect at their boundary and preserve previously accepted schedules`() {
        val f = fixture()
        val actor = UUID.randomUUID()
        assertIs<FinancialResult.Success<*>>(f.publisher.terms(FinancialRequest(UUID.randomUUID(), actor), "v1", "Terms", now, now))
        val firstRequest = FinancialRequest(UUID.randomUUID(), actor)
        val first = schedule(firstRequest.requestId, "v1")
        assertIs<FinancialResult.Success<*>>(f.publisher.fees(firstRequest, first, now, now))
        val future = now.plusSeconds(100)
        assertIs<FinancialResult.Success<*>>(f.publisher.terms(FinancialRequest(UUID.randomUUID(), actor), "v2", "New terms", future, now))
        val nextRequest = FinancialRequest(UUID.randomUUID(), actor)
        val next = schedule(nextRequest.requestId, "v2").copy(commissionFixedCents = 500)
        assertEquals(FinancialError.INVALID_INPUT,
            assertIs<FinancialResult.Failure>(f.publisher.fees(nextRequest, next, future.minusSeconds(1), now)).error)
        assertIs<FinancialResult.Success<*>>(f.publisher.fees(nextRequest, next, future, now))
        assertEquals(first.id, f.conditions.current(setOf(PaymentMethod.PIX), future.minusNanos(1000)).single().id)
        assertEquals(next.id, f.conditions.current(setOf(PaymentMethod.PIX), future).single().id)
        assertEquals(100L, f.jdbc.sql("SELECT commission_fixed_cents FROM receivable_fee_schedules WHERE id=:id")
            .param("id", first.id).query(Long::class.java).single())
        assertEquals(2L, f.count("receivable_fee_schedules"))
        assertFails { f.jdbc.sql("UPDATE receivable_fee_schedules SET commission_fixed_cents=0").update() }
    }

    @Test
    fun `invalid dates missing terms and rates that would be rounded create no publication`() {
        val f = fixture()
        val request = FinancialRequest(UUID.randomUUID(), UUID.randomUUID())
        fun invalid(result: FinancialResult<*>) = assertEquals(FinancialError.INVALID_INPUT,
            assertIs<FinancialResult.Failure>(result).error)
        invalid(f.publisher.terms(request, "v1", "Terms", now.minusSeconds(1), now))
        invalid(f.publisher.terms(request, "v1", " ", now, now))
        invalid(f.publisher.terms(request, "v/1", "Terms", now, now))
        invalid(f.publisher.fees(request, schedule(request.requestId, "missing"), now, now))
        invalid(f.publisher.fees(request, schedule(request.requestId, "missing").copy(providerRate = BigDecimal("0.12345678901")), now, now))
        assertEquals(0L, f.count("receivable_condition_publications"))
        assertEquals(0L, f.count("receivable_terms"))
        assertEquals(0L, f.count("receivable_fee_schedules"))
    }

    private fun schedule(id: UUID, terms: String) = FeeSchedule(id, PaymentMethod.PIX, BigDecimal("0.0299"),
        39, BigDecimal("0.02"), 100, terms)
    private fun fixture(): Fixture {
        val database = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner = this)
        return Fixture(JdbcClient.create(database.dataSource), JdbcFinancialConditionsPublisher(database.dataSource),
            JdbcFinancialConditions(database.dataSource))
    }
    private class Fixture(val jdbc: JdbcClient, val publisher: JdbcFinancialConditionsPublisher, val conditions: JdbcFinancialConditions) {
        fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    }
}
