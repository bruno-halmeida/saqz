package br.com.saqz.groups.application.report

import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class ReportPeriod { CURRENT_MONTH, PREVIOUS_MONTH, YEAR }

/** Intervalo `[from, toExclusive)` em datas do fuso do grupo. */
data class ReportRange(val from: LocalDate, val toExclusive: LocalDate, val zone: ZoneId) {
    val fromAt: Instant get() = from.atStartOfDay(zone).toInstant()
    val toAt: Instant get() = toExclusive.atStartOfDay(zone).toInstant()

    companion object {
        fun of(period: ReportPeriod, today: LocalDate, zone: ZoneId): ReportRange {
            val month = today.withDayOfMonth(1)
            return when (period) {
                ReportPeriod.CURRENT_MONTH -> ReportRange(month, month.plusMonths(1), zone)
                ReportPeriod.PREVIOUS_MONTH -> ReportRange(month.minusMonths(1), month, zone)
                ReportPeriod.YEAR -> ReportRange(today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1), zone)
            }
        }
    }
}

data class ReportGameRow(
    val gameId: UUID,
    val title: String,
    val startsAt: Instant,
    val capacity: Int,
    val status: String,
    val confirmed: Int,
    val confirmedMembers: Int,
    val declined: Int,
    val waitlisted: Int,
    val noResponse: Int,
)

data class ReportMemberRow(
    val userId: UUID,
    val name: String,
    val games: Int,
    val confirmed: Int,
    val declined: Int,
    val responded: Int,
)

data class ReportLedgerRow(
    val occurredAt: Instant,
    val direction: String,
    val type: String,
    val title: String,
    val category: String,
    val paidMethod: String?,
    val amountCents: Long,
)

data class ReportOverdueMember(val userId: UUID, val name: String, val amountCents: Long, val months: Int)

data class RankedMember(val userId: UUID, val name: String, val count: Int)

data class AttendanceReport(
    val games: Int,
    val averageConfirmed: Double,
    val averageCapacity: Double,
    val confirmationRatePercent: Int,
    val mostPresent: List<RankedMember>,
    val mostDeclined: List<RankedMember>,
    val neverResponded: List<RankedMember>,
)

data class FinanceReport(
    val inCents: Long,
    val outCents: Long,
    val balanceCents: Long,
    val receivableCents: Long,
    val overdueMonthly: List<ReportOverdueMember>,
)

data class GroupReport(
    val period: ReportPeriod,
    val from: LocalDate,
    val toExclusive: LocalDate,
    val attendance: AttendanceReport,
    val finance: FinanceReport,
)

enum class ReportExportKind { ATTENDANCE, LEDGER }

data class ReportExport(val fileName: String, val csv: String)

interface GroupReportRepository {
    fun role(actorId: UUID, groupId: UUID): GroupRole?
    fun ownerOf(groupId: UUID): UUID?
    fun timeZone(groupId: UUID): ZoneId
    fun games(groupId: UUID, range: ReportRange, now: Instant): List<ReportGameRow>
    fun members(groupId: UUID, range: ReportRange, now: Instant): List<ReportMemberRow>
    fun ledger(groupId: UUID, range: ReportRange): List<ReportLedgerRow>
    fun receivableCents(groupId: UUID, range: ReportRange): Long
    fun overdueMonthly(groupId: UUID, range: ReportRange, today: LocalDate): List<ReportOverdueMember>
}
