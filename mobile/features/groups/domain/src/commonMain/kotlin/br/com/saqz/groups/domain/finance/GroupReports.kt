package br.com.saqz.groups.domain.finance

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

/** Períodos do relatório; o servidor resolve as datas no fuso do grupo. */
enum class ReportPeriod { CurrentMonth, PreviousMonth, Year }

enum class ReportExportKind { Attendance, Ledger }

data class ReportRankedMember(val userId: String, val name: String, val count: Int)

data class ReportOverdueMember(val userId: String, val name: String, val amountCents: Long, val months: Int)

data class AttendanceReport(
    val games: Int,
    val averageConfirmed: Double,
    val averageCapacity: Double,
    val confirmationRatePercent: Int,
    val mostPresent: List<ReportRankedMember>,
    val mostDeclined: List<ReportRankedMember>,
    val neverResponded: List<ReportRankedMember>,
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
    val from: String,
    val toExclusive: String,
    val attendance: AttendanceReport,
    val finance: FinanceReport,
)

data class ReportExport(val fileName: String, val csv: String)

/**
 * Relatórios do plano Ilimitado. Quem decide se o grupo tem direito a eles é o servidor:
 * [availability] responde `false` para trial e planos menores, e [report]/[export] falham com
 * `DataError.SubscriptionRequired`.
 */
interface GroupReportGateway {
    suspend fun availability(groupId: GroupId): SaqzResult<Boolean, FinanceError>

    suspend fun report(groupId: GroupId, period: ReportPeriod): SaqzResult<GroupReport, FinanceError>

    suspend fun export(
        groupId: GroupId,
        period: ReportPeriod,
        kind: ReportExportKind,
    ): SaqzResult<ReportExport, FinanceError>
}
