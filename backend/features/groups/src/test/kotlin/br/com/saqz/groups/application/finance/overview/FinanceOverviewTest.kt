package br.com.saqz.groups.application.finance.overview

import br.com.saqz.groups.domain.finance.expense.ExpenseDirection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class FinanceOverviewTest {
    private val actor = UUID.randomUUID()
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val clock = Clock.fixed(Instant.parse("2026-08-01T02:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun `missing filters use current month in billing zone`() {
        val repository = RecordingRepository()

        val result = FinanceOverviewQuery(repository, clock, zone).execute(actor, null, null)

        assertEquals(YearMonth.of(2026, 7), assertIs<FinanceOverviewQueryResult.Success>(result).value.period.let {
            assertIs<FinanceOverviewPeriod.Month>(it).value
        })
        assertEquals(actor, repository.actorId)
    }

    @Test
    fun `month filter selects exact month`() {
        val result = FinanceOverviewQuery(RecordingRepository(), clock, zone)
            .execute(actor, "2026-06", null)

        assertEquals(
            YearMonth.of(2026, 6),
            assertIs<FinanceOverviewPeriod.Month>(
                assertIs<FinanceOverviewQueryResult.Success>(result).value.period,
            ).value,
        )
    }

    @Test
    fun `year filter selects the complete calendar year`() {
        val result = FinanceOverviewQuery(RecordingRepository(), clock, zone)
            .execute(actor, null, "2025")

        val period = assertIs<FinanceOverviewPeriod.CalendarYear>(
            assertIs<FinanceOverviewQueryResult.Success>(result).value.period,
        )
        assertEquals(LocalDateAssertions.startOf(2025), period.startDate)
        assertEquals(LocalDateAssertions.endOf(2025), period.endDate)
    }

    @Test
    fun `month and year are mutually exclusive`() {
        val result = FinanceOverviewQuery(RecordingRepository(), clock, zone)
            .execute(actor, "2026-06", "2026")

        val invalid = assertIs<FinanceOverviewQueryResult.Invalid>(result)
        assertEquals(
            mapOf(
                "month" to listOf("não pode ser informado junto com year"),
                "year" to listOf("não pode ser informado junto com month"),
            ),
            invalid.fieldErrors,
        )
    }

    @Test
    fun `invalid month and year formats return their field errors`() {
        val query = FinanceOverviewQuery(RecordingRepository(), clock, zone)

        val month = assertIs<FinanceOverviewQueryResult.Invalid>(query.execute(actor, "2026-13", null))
        val year = assertIs<FinanceOverviewQueryResult.Invalid>(query.execute(actor, null, "26"))

        assertEquals(mapOf("month" to listOf("deve usar o formato YYYY-MM")), month.fieldErrors)
        assertEquals(mapOf("year" to listOf("deve usar o formato YYYY")), year.fieldErrors)
    }

    private class RecordingRepository : FinanceOverviewRepository {
        var actorId: UUID? = null

        override fun find(actorId: UUID, period: FinanceOverviewPeriod) =
            FinanceOverviewReadModel(
                period = period,
                totals = FinanceOverviewTotals(0, 0, 0, 0),
                groups = emptyList(),
                recentTransactions = emptyList(),
            ).also { this.actorId = actorId }
    }

    private object LocalDateAssertions {
        fun startOf(year: Int) = java.time.LocalDate.of(year, 1, 1)
        fun endOf(year: Int) = java.time.LocalDate.of(year, 12, 31)
    }
}
