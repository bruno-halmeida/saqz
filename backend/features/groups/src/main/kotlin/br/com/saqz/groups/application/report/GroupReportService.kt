package br.com.saqz.groups.application.report

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.subscription.ReportsEntitlement
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

sealed interface GroupReportResult<out T> {
    data class Success<T>(val value: T) : GroupReportResult<T>
    data object Hidden : GroupReportResult<Nothing>
    data object Forbidden : GroupReportResult<Nothing>

    /** O plano do dono do grupo não inclui relatórios. */
    data object PlanRequired : GroupReportResult<Nothing>
}

class GroupReportService(
    private val repository: GroupReportRepository,
    private val entitlement: ReportsEntitlement,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Só administra quem é dono/admin; o plano é conferido aqui, nunca pelo app. */
    fun availability(actorId: UUID, groupId: UUID): GroupReportResult<Boolean> =
        when (val result = gate(actorId, groupId) { GroupReportResult.Success(true) }) {
            GroupReportResult.PlanRequired -> GroupReportResult.Success(false)
            else -> result
        }

    fun report(actorId: UUID, groupId: UUID, period: ReportPeriod): GroupReportResult<GroupReport> =
        gate(actorId, groupId) {
            val range = range(groupId, period)
            val now = clock.instant()
            val today = LocalDate.now(clock.withZone(range.zone))
            val games = repository.games(groupId, range, now)
            val members = repository.members(groupId, range, now)
            val ledger = repository.ledger(groupId, range)
            GroupReportResult.Success(
                GroupReport(
                    period = period,
                    from = range.from,
                    toExclusive = range.toExclusive,
                    attendance = attendance(games, members),
                    finance = FinanceReport(
                        inCents = ledger.filter { it.direction == "IN" }.sumOf { it.amountCents },
                        outCents = ledger.filter { it.direction == "OUT" }.sumOf { it.amountCents },
                        balanceCents = ledger.sumOf { if (it.direction == "IN") it.amountCents else -it.amountCents },
                        receivableCents = repository.receivableCents(groupId, range),
                        overdueMonthly = repository.overdueMonthly(groupId, range, today),
                    ),
                ),
            )
        }

    fun export(
        actorId: UUID,
        groupId: UUID,
        period: ReportPeriod,
        kind: ReportExportKind,
    ): GroupReportResult<ReportExport> = gate(actorId, groupId) {
        val range = range(groupId, period)
        val csv = when (kind) {
            ReportExportKind.ATTENDANCE ->
                ReportCsv.attendance(repository.games(groupId, range, clock.instant()), range.zone)
            ReportExportKind.LEDGER -> ReportCsv.ledger(repository.ledger(groupId, range), range.zone)
        }
        val name = when (kind) {
            ReportExportKind.ATTENDANCE -> "presenca"
            ReportExportKind.LEDGER -> "lancamentos"
        }
        GroupReportResult.Success(
            ReportExport("saqz-$name-${range.from}-${range.toExclusive.minusDays(1)}.csv", csv),
        )
    }

    private fun range(groupId: UUID, period: ReportPeriod): ReportRange {
        val zone = repository.timeZone(groupId)
        return ReportRange.of(period, LocalDate.now(clock.withZone(zone)), zone)
    }

    private fun <T> gate(
        actorId: UUID,
        groupId: UUID,
        block: () -> GroupReportResult<T>,
    ): GroupReportResult<T> = when (repository.role(actorId, groupId)) {
        null -> GroupReportResult.Hidden
        GroupRole.ATHLETE -> GroupReportResult.Forbidden
        GroupRole.OWNER, GroupRole.ADMIN -> {
            val owner = repository.ownerOf(groupId)
            when {
                owner == null -> GroupReportResult.Hidden
                !entitlement.isEnabledFor(owner) -> GroupReportResult.PlanRequired
                else -> block()
            }
        }
    }

    companion object {
        const val RANKING_SIZE = 5
        const val NEVER_RESPONDED_LIMIT = 50

        fun attendance(games: List<ReportGameRow>, members: List<ReportMemberRow>): AttendanceReport {
            val count = games.size
            val answerable = games.sumOf { it.confirmedMembers + it.declined + it.noResponse }
            return AttendanceReport(
                games = count,
                averageConfirmed = if (count == 0) 0.0 else round1(games.sumOf { it.confirmed }.toDouble() / count),
                averageCapacity = if (count == 0) 0.0 else round1(games.sumOf { it.capacity }.toDouble() / count),
                confirmationRatePercent = if (answerable == 0) 0 else
                    (games.sumOf { it.confirmedMembers } * 100.0 / answerable).roundToInt(),
                mostPresent = rank(members) { it.confirmed },
                mostDeclined = rank(members) { it.declined },
                neverResponded = members
                    .filter { it.games > 0 && it.responded == 0 }
                    .sortedBy { it.name.lowercase() }
                    .take(NEVER_RESPONDED_LIMIT)
                    .map { RankedMember(it.userId, it.name, it.games) },
            )
        }

        private fun rank(members: List<ReportMemberRow>, metric: (ReportMemberRow) -> Int) = members
            .filter { metric(it) > 0 }
            .sortedWith(compareByDescending<ReportMemberRow> { metric(it) }.thenBy { it.name.lowercase() })
            .take(RANKING_SIZE)
            .map { RankedMember(it.userId, it.name, metric(it)) }

        private fun round1(value: Double) = (value * 10).roundToInt() / 10.0
    }
}
