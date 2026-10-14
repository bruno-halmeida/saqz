package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.report.AttendanceReport
import br.com.saqz.groups.application.report.FinanceReport
import br.com.saqz.groups.application.report.GroupReport
import br.com.saqz.groups.application.report.GroupReportResult
import br.com.saqz.groups.application.report.GroupReportService
import br.com.saqz.groups.application.report.RankedMember
import br.com.saqz.groups.application.report.ReportExportKind
import br.com.saqz.groups.application.report.ReportPeriod
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ReportAvailabilityResponse(val enabled: Boolean)

data class RankedMemberResponse(val userId: UUID, val name: String, val count: Int)

data class AttendanceReportResponse(
    val games: Int,
    val averageConfirmed: Double,
    val averageCapacity: Double,
    val confirmationRatePercent: Int,
    val mostPresent: List<RankedMemberResponse>,
    val mostDeclined: List<RankedMemberResponse>,
    val neverResponded: List<RankedMemberResponse>,
)

data class OverdueMemberResponse(val userId: UUID, val name: String, val amountCents: Long, val months: Int)

data class FinanceReportResponse(
    val inCents: Long,
    val outCents: Long,
    val balanceCents: Long,
    val receivableCents: Long,
    val overdueMonthly: List<OverdueMemberResponse>,
)

data class GroupReportResponse(
    val period: String,
    val from: String,
    val toExclusive: String,
    val attendance: AttendanceReportResponse,
    val finance: FinanceReportResponse,
)

data class ReportExportResponse(val fileName: String, val csv: String)

/**
 * Relatórios do plano Ilimitado, por grupo e período. Só dono/admin; o plano do dono do grupo é
 * verificado aqui (403 SUBSCRIPTION_REQUIRED sem ele), nunca decidido pelo app.
 */
@RestController
class GroupReportController(
    private val actors: VerifiedGroupActorResolver,
    private val service: GroupReportService,
) {
    @GetMapping("/api/groups/{groupId}/reports/availability")
    fun availability(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
    ): ReportAvailabilityResponse = ReportAvailabilityResponse(
        unwrap(service.availability(actors.resolve(identity), uuid(groupId))),
    )

    @GetMapping("/api/groups/{groupId}/reports")
    fun report(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestParam(required = false) period: String? = null,
    ): GroupReportResponse = unwrap(service.report(actors.resolve(identity), uuid(groupId), period(period))).response()

    @GetMapping("/api/groups/{groupId}/reports/export")
    fun export(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestParam(required = false) period: String? = null,
        @RequestParam(required = false) kind: String? = null,
    ): ReportExportResponse {
        val parsedKind = kind?.let { runCatching { ReportExportKind.valueOf(it) }.getOrNull() } ?: invalid("kind")
        val export = unwrap(service.export(actors.resolve(identity), uuid(groupId), period(period), parsedKind))
        return ReportExportResponse(export.fileName, export.csv)
    }

    private fun period(value: String?): ReportPeriod =
        if (value == null) ReportPeriod.CURRENT_MONTH
        else runCatching { ReportPeriod.valueOf(value) }.getOrElse { invalid("period") }

    private fun <T> unwrap(result: GroupReportResult<T>): T = when (result) {
        is GroupReportResult.Success -> result.value
        GroupReportResult.Hidden -> throw GameNotFoundException()
        GroupReportResult.Forbidden -> throw AccessForbiddenException()
        GroupReportResult.PlanRequired -> throw SubscriptionRequiredException()
    }

    private fun uuid(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }

    private fun invalid(field: String): Nothing =
        throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}

private fun GroupReport.response() = GroupReportResponse(
    period = period.name,
    from = from.toString(),
    toExclusive = toExclusive.toString(),
    attendance = attendance.response(),
    finance = finance.response(),
)

private fun RankedMember.response() = RankedMemberResponse(userId, name, count)

private fun AttendanceReport.response() = AttendanceReportResponse(
    games = games,
    averageConfirmed = averageConfirmed,
    averageCapacity = averageCapacity,
    confirmationRatePercent = confirmationRatePercent,
    mostPresent = mostPresent.map { it.response() },
    mostDeclined = mostDeclined.map { it.response() },
    neverResponded = neverResponded.map { it.response() },
)

private fun FinanceReport.response() = FinanceReportResponse(
    inCents = inCents,
    outCents = outCents,
    balanceCents = balanceCents,
    receivableCents = receivableCents,
    overdueMonthly = overdueMonthly.map { OverdueMemberResponse(it.userId, it.name, it.amountCents, it.months) },
)
