package br.com.saqz.groups.data.finance

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.AttendanceReport
import br.com.saqz.groups.domain.finance.FinanceReport
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.GroupReport
import br.com.saqz.groups.domain.finance.GroupReportGateway
import br.com.saqz.groups.domain.finance.ReportExport
import br.com.saqz.groups.domain.finance.ReportExportKind
import br.com.saqz.groups.domain.finance.ReportOverdueMember
import br.com.saqz.groups.domain.finance.ReportPeriod
import br.com.saqz.groups.domain.finance.ReportRankedMember
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
internal data class ReportAvailabilityTransport(val enabled: Boolean)

@Serializable
internal data class ReportRankedMemberTransport(val userId: String, val name: String, val count: Int)

@Serializable
internal data class ReportOverdueMemberTransport(
    val userId: String,
    val name: String,
    val amountCents: Long,
    val months: Int,
)

@Serializable
internal data class AttendanceReportTransport(
    val games: Int,
    val averageConfirmed: Double,
    val averageCapacity: Double,
    val confirmationRatePercent: Int,
    val mostPresent: List<ReportRankedMemberTransport>,
    val mostDeclined: List<ReportRankedMemberTransport>,
    val neverResponded: List<ReportRankedMemberTransport>,
)

@Serializable
internal data class FinanceReportTransport(
    val inCents: Long,
    val outCents: Long,
    val balanceCents: Long,
    val receivableCents: Long,
    val overdueMonthly: List<ReportOverdueMemberTransport>,
)

@Serializable
internal data class GroupReportTransport(
    val period: String,
    val from: String,
    val toExclusive: String,
    val attendance: AttendanceReportTransport,
    val finance: FinanceReportTransport,
)

@Serializable
internal data class ReportExportTransport(val fileName: String, val csv: String)

class KtorGroupReportGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GroupReportGateway {
    override suspend fun availability(groupId: GroupId): SaqzResult<Boolean, FinanceError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/reports/availability",
                ReportAvailabilityTransport.serializer(),
            )
        }.mapReport { it.enabled }

    override suspend fun report(groupId: GroupId, period: ReportPeriod): SaqzResult<GroupReport, FinanceError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/reports",
                GroupReportTransport.serializer(),
                NetworkRequest(query = mapOf("period" to period.toTransport())),
            )
        }.mapReport { it.toDomain(period) }

    override suspend fun export(
        groupId: GroupId,
        period: ReportPeriod,
        kind: ReportExportKind,
    ): SaqzResult<ReportExport, FinanceError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/reports/export",
                ReportExportTransport.serializer(),
                NetworkRequest(query = mapOf("period" to period.toTransport(), "kind" to kind.toTransport())),
            )
        }.mapReport { ReportExport(it.fileName, it.csv) }
}

private inline fun <T, R> NetworkResult<T>.mapReport(mapper: (T) -> R): SaqzResult<R, FinanceError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toFinanceError())
        is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    }

private fun ReportPeriod.toTransport() = when (this) {
    ReportPeriod.CurrentMonth -> "CURRENT_MONTH"
    ReportPeriod.PreviousMonth -> "PREVIOUS_MONTH"
    ReportPeriod.Year -> "YEAR"
}

private fun ReportExportKind.toTransport() = when (this) {
    ReportExportKind.Attendance -> "ATTENDANCE"
    ReportExportKind.Ledger -> "LEDGER"
}

private fun ReportRankedMemberTransport.toDomain() = ReportRankedMember(userId, name, count)

private fun GroupReportTransport.toDomain(period: ReportPeriod) = GroupReport(
    period = period,
    from = from,
    toExclusive = toExclusive,
    attendance = AttendanceReport(
        games = attendance.games,
        averageConfirmed = attendance.averageConfirmed,
        averageCapacity = attendance.averageCapacity,
        confirmationRatePercent = attendance.confirmationRatePercent,
        mostPresent = attendance.mostPresent.map { it.toDomain() },
        mostDeclined = attendance.mostDeclined.map { it.toDomain() },
        neverResponded = attendance.neverResponded.map { it.toDomain() },
    ),
    finance = FinanceReport(
        inCents = finance.inCents,
        outCents = finance.outCents,
        balanceCents = finance.balanceCents,
        receivableCents = finance.receivableCents,
        overdueMonthly = finance.overdueMonthly.map { ReportOverdueMember(it.userId, it.name, it.amountCents, it.months) },
    ),
)
