package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.input.http.FinancialConditionsController
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditions
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class FinancialConditionsIntegrationTest {
    private val at = Instant.parse("2026-09-12T12:00:00Z")
    private val mapper = jacksonObjectMapper()

    @Test
    fun `simulation has no fallback fee and creates no account acceptance or debt`() {
        val f = fixture()
        val request = UUID.randomUUID()
        assertEquals(FinancialResult.Failure(FinancialError.CONFIGURATION_UNAVAILABLE, request),
            f.service.execute(request, 10000, setOf(PaymentMethod.PIX), at))
        f.terms("v1")
        val schedule = f.fee("PIX", "v1", at.minusSeconds(1))
        val quote = assertIs<FinancialResult.Success<ChargeSimulation>>(
            f.service.execute(request, 10000, setOf(PaymentMethod.PIX), at)).value
        assertEquals("Taxas de serviço e pagamento", quote.feesLabel)
        assertEquals(schedule, quote.quotes.single().feeScheduleId)
        assertEquals(10658, quote.quotes.single().totalCents)
        assertEquals(10000, quote.quotes.single().expectedNetCents)
        assertEquals(300, quote.quotes.single().commissionCents)
        assertEquals(FinancialResult.Failure(FinancialError.CONFIGURATION_UNAVAILABLE, request),
            f.service.execute(request, 10000, PaymentMethod.entries.toSet(), at))
        for (table in listOf("receivable_accounts", "receivable_terms_acceptances", "receivable_orders", "receivable_operations")) {
            assertEquals(0L, f.jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single())
        }
    }

    @Test
    fun `new quotes use effective published fees and keep historical terms accessible`() {
        val f = fixture()
        f.terms("v1")
        f.terms("v2", effective = at.plusSeconds(10))
        f.terms("unpublished", published = at.plusSeconds(100))
        val old = f.fee("PIX", "v1", at.minusSeconds(10))
        val next = f.fee("PIX", "v2", at.plusSeconds(10))
        f.fee("PIX", "unpublished", at.plusSeconds(11))
        f.fee("PIX", "v1", at.plusSeconds(12), published = at.plusSeconds(100))
        assertEquals(old, f.conditions.current(setOf(PaymentMethod.PIX), at).single().id)
        assertEquals(next, f.conditions.current(setOf(PaymentMethod.PIX), at.plusSeconds(10)).single().id)
        assertEquals(next, f.conditions.current(setOf(PaymentMethod.PIX), at.plusSeconds(20)).single().id)
        assertEquals("v1", f.conditions.terms("v1", at.plusSeconds(20))!!.version)
        assertEquals(at.plusSeconds(10), f.conditions.terms("v2", at)!!.effectiveAt)
        assertNull(f.conditions.terms("unpublished", at))
    }

    @Test
    fun `HTTP rejects fractional missing and overflowing cents and returns the request identifier`() {
        val f = fixture()
        f.terms("v1")
        f.fee("PIX", "v1", at.minusSeconds(1))
        val mvc = MockMvcBuilders.standaloneSetup(FinancialConditionsController(f.conditions, f.service,
            Clock.fixed(at, ZoneOffset.UTC))).build()
        val requestId = UUID.randomUUID()
        for (base in listOf("0", "-1", "100.5", "9223372036854775808", "9223372036854775807", "null")) {
            val response = mvc.perform(post("/api/receivables/charges/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"requestId":"$requestId","baseCents":$base,"methods":["PIX"]}""")).andReturn().response
            assertEquals(400, response.status, base)
            assertEquals("INVALID_INPUT", mapper.readTree(response.contentAsString)["error"].asText())
            assertEquals(requestId.toString(), mapper.readTree(response.contentAsString)["requestId"].asText())
        }
        for (payload in listOf("{}", """{"requestId":"$requestId","baseCents":10000,"methods":[]}""")) {
            assertEquals(400, mvc.perform(post("/api/receivables/charges/simulate")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().response.status)
        }
        val success = mvc.perform(post("/api/receivables/charges/simulate").contentType(MediaType.APPLICATION_JSON)
            .content("""{"requestId":"$requestId","baseCents":10000,"methods":["PIX"]}""")).andReturn().response
        assertEquals(200, success.status)
        val quote = mapper.readTree(success.contentAsString)["value"]["quotes"][0]
        assertEquals(10658, quote["totalCents"].asLong())
        assertEquals("v1", quote["termsVersion"].asText())
        assertEquals(200, mvc.perform(get("/api/receivables/terms/v1")).andReturn().response.status)
        assertEquals(404, mvc.perform(get("/api/receivables/terms/unknown")).andReturn().response.status)
        assertEquals(503, mvc.perform(post("/api/receivables/charges/simulate").contentType(MediaType.APPLICATION_JSON)
            .content("""{"requestId":"$requestId","baseCents":10000,"methods":["CARD"]}""")).andReturn().response.status)
    }

    private fun fixture(): Fixture {
        val database = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner = this)
        return Fixture(JdbcClient.create(database.dataSource), JdbcFinancialConditions(database.dataSource))
    }

    private inner class Fixture(val jdbc: JdbcClient, val conditions: JdbcFinancialConditions) {
        val service = SimulateFinancialCharge(conditions)
        fun terms(version: String, effective: Instant = at.minusSeconds(100), published: Instant = at.minusSeconds(100)) {
            jdbc.sql("INSERT INTO receivable_terms VALUES (:version,'Test conditions only',:hash,:effective,:published)")
                .param("version", version).param("hash", "a".repeat(64)).param("effective", Timestamp.from(effective))
                .param("published", Timestamp.from(published)).update()
        }
        fun fee(method: String, terms: String, effective: Instant, published: Instant = at.minusSeconds(100)): UUID {
            val id = UUID.randomUUID()
            jdbc.sql("""
                INSERT INTO receivable_fee_schedules VALUES (:id,:method,0.0299,39,0.02,100,:terms,:effective,:published,:actor)
            """.trimIndent()).param("id", id).param("method", method).param("terms", terms)
                .param("effective", Timestamp.from(effective)).param("published", Timestamp.from(published))
                .param("actor", UUID.randomUUID()).update()
            return id
        }
    }
}
