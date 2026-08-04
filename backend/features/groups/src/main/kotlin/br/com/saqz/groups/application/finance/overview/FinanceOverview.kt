package br.com.saqz.groups.application.finance.overview

import br.com.saqz.groups.domain.finance.expense.ExpenseDirection
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

sealed interface FinanceOverviewPeriod {
    val month: String?
    val year: Int?
    val startDate: LocalDate
    val endDate: LocalDate

    data class Month(val value: YearMonth) : FinanceOverviewPeriod {
        override val month: String = value.toString()
        override val year: Int? = null
        override val startDate: LocalDate = value.atDay(1)
        override val endDate: LocalDate = value.atEndOfMonth()
    }

    data class CalendarYear(val value: Int) : FinanceOverviewPeriod {
        override val month: String? = null
        override val year: Int = value
        override val startDate: LocalDate = LocalDate.of(value, 1, 1)
        override val endDate: LocalDate = LocalDate.of(value, 12, 31)
    }

    companion object {
        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT)

        fun resolve(
            month: String?,
            year: String?,
            currentMonth: YearMonth,
        ): FinanceOverviewPeriodResolution {
            if (month != null && year != null) {
                return FinanceOverviewPeriodResolution.Invalid(
                    mapOf(
                        "month" to listOf("não pode ser informado junto com year"),
                        "year" to listOf("não pode ser informado junto com month"),
                    ),
                )
            }
            if (month != null) return parseMonth(month)
            if (year != null) return parseYear(year)
            return FinanceOverviewPeriodResolution.Valid(Month(currentMonth))
        }

        private fun parseMonth(value: String): FinanceOverviewPeriodResolution {
            if (!Regex("\\d{4}-\\d{2}").matches(value)) {
                return invalidMonth()
            }
            return runCatching { YearMonth.parse(value, MONTH_FORMAT) }
                .fold(
                    onSuccess = { FinanceOverviewPeriodResolution.Valid(Month(it)) },
                    onFailure = { invalidMonth() },
                )
        }

        private fun parseYear(value: String): FinanceOverviewPeriodResolution {
            if (!Regex("\\d{4}").matches(value)) return invalidYear()
            val parsed = value.toIntOrNull()
                ?: return invalidYear()
            return try {
                LocalDate.of(parsed, 1, 1)
                FinanceOverviewPeriodResolution.Valid(CalendarYear(parsed))
            } catch (_: DateTimeException) {
                invalidYear()
            }
        }

        private fun invalidMonth() = FinanceOverviewPeriodResolution.Invalid(
            mapOf("month" to listOf("deve usar o formato YYYY-MM")),
        )

        private fun invalidYear() = FinanceOverviewPeriodResolution.Invalid(
            mapOf("year" to listOf("deve usar o formato YYYY")),
        )
    }
}

sealed interface FinanceOverviewPeriodResolution {
    data class Valid(val period: FinanceOverviewPeriod) : FinanceOverviewPeriodResolution

    data class Invalid(val fieldErrors: Map<String, List<String>>) : FinanceOverviewPeriodResolution
}

data class FinanceOverviewTotals(
    val balanceCents: Long,
    val inCents: Long,
    val outCents: Long,
    val pendingCents: Long,
)

data class FinanceOverviewGroup(
    val id: UUID,
    val name: String,
    val balanceCents: Long,
    val pendingMonthlyCount: Int,
    val hasBillingConfigured: Boolean,
)

enum class FinanceOverviewTransactionKind { MONTHLY, LAUNCH }

data class FinanceOverviewTransaction(
    val id: UUID,
    val groupId: UUID,
    val groupName: String,
    val kind: FinanceOverviewTransactionKind,
    val direction: ExpenseDirection?,
    val memberName: String?,
    val description: String?,
    val amountCents: Long,
    val occurredAt: java.time.Instant,
)

data class FinanceOverviewReadModel(
    val period: FinanceOverviewPeriod,
    val totals: FinanceOverviewTotals,
    val groups: List<FinanceOverviewGroup>,
    val recentTransactions: List<FinanceOverviewTransaction>,
)

interface FinanceOverviewRepository {
    fun find(actorId: UUID, period: FinanceOverviewPeriod): FinanceOverviewReadModel
}

sealed interface FinanceOverviewQueryResult {
    data class Success(val value: FinanceOverviewReadModel) : FinanceOverviewQueryResult

    data class Invalid(val fieldErrors: Map<String, List<String>>) : FinanceOverviewQueryResult
}

class FinanceOverviewQuery(
    private val repository: FinanceOverviewRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    fun execute(
        actorId: UUID,
        month: String?,
        year: String?,
    ): FinanceOverviewQueryResult {
        return when (
            val resolution = FinanceOverviewPeriod.resolve(
                month = month,
                year = year,
                currentMonth = YearMonth.from(LocalDate.now(clock.withZone(zoneId))),
            )
        ) {
            is FinanceOverviewPeriodResolution.Invalid ->
                FinanceOverviewQueryResult.Invalid(resolution.fieldErrors)

            is FinanceOverviewPeriodResolution.Valid ->
                FinanceOverviewQueryResult.Success(repository.find(actorId, resolution.period))
        }
    }
}
