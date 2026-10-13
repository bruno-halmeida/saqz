package br.com.saqz.groups.application.report

import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupReportServiceTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val clock = Clock.fixed(Instant.parse("2026-10-13T02:00:00Z"), ZoneOffset.UTC)
    private val repository = FakeRepository()
    private var entitled = true
    private val service = GroupReportService(repository, { entitled }, clock)

    @Test
    fun `period ranges follow the group time zone`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        // 02:00Z de 13/10 ainda é 12/10 em São Paulo.
        val today = LocalDate.of(2026, 10, 12)

        val current = ReportRange.of(ReportPeriod.CURRENT_MONTH, today, zone)
        val previous = ReportRange.of(ReportPeriod.PREVIOUS_MONTH, today, zone)
        val year = ReportRange.of(ReportPeriod.YEAR, today, zone)

        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 11, 1), current.from to current.toExclusive)
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 10, 1), previous.from to previous.toExclusive)
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2027, 1, 1), year.from to year.toExclusive)
        assertEquals(Instant.parse("2026-10-01T03:00:00Z"), current.fromAt)
    }

    @Test
    fun `january previous month reaches back to december`() {
        val range = ReportRange.of(ReportPeriod.PREVIOUS_MONTH, LocalDate.of(2027, 1, 5), ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 12, 1), range.from)
        assertEquals(LocalDate.of(2027, 1, 1), range.toExclusive)
    }

    @Test
    fun `attendance summarises averages rate and top five rankings`() {
        val games = listOf(
            game(capacity = 10, confirmed = 8, confirmedMembers = 7, declined = 2, noResponse = 1),
            game(capacity = 12, confirmed = 9, confirmedMembers = 9, declined = 1, noResponse = 0),
        )
        val members = (1..7).map { member("Atleta $it", games = 2, confirmed = 8 - it, declined = it % 3, responded = 2) } +
            member("Silva", games = 2, confirmed = 0, declined = 0, responded = 0) +
            member("Alice", games = 2, confirmed = 0, declined = 0, responded = 0) +
            member("Recém-chegado", games = 0, confirmed = 0, declined = 0, responded = 0)

        val report = GroupReportService.attendance(games, members)

        assertEquals(2, report.games)
        assertEquals(8.5, report.averageConfirmed)
        assertEquals(11.0, report.averageCapacity)
        assertEquals(80, report.confirmationRatePercent) // 16 de 20
        assertEquals(listOf(7, 6, 5, 4, 3), report.mostPresent.map { it.count })
        assertEquals(5, report.mostDeclined.size)
        assertEquals(listOf("Alice", "Silva"), report.neverResponded.map { it.name })
        assertTrue(report.neverResponded.none { it.name == "Recém-chegado" })
    }

    @Test
    fun `period without games is all zeros`() {
        val report = GroupReportService.attendance(emptyList(), emptyList())

        assertEquals(0, report.games)
        assertEquals(0.0, report.averageConfirmed)
        assertEquals(0, report.confirmationRatePercent)
    }

    @Test
    fun `finance totals separate money in and out and list overdue monthly members`() {
        repository.role = GroupRole.ADMIN
        repository.ledger = listOf(
            ledger("IN", 5_000), ledger("IN", 2_500), ledger("OUT", 3_000),
        )
        repository.receivable = 4_000
        repository.overdue = listOf(ReportOverdueMember(UUID.randomUUID(), "Marina", 6_000, 2))

        val result = assertIs<GroupReportResult.Success<GroupReport>>(
            service.report(actor, group, ReportPeriod.CURRENT_MONTH),
        ).value

        assertEquals(7_500, result.finance.inCents)
        assertEquals(3_000, result.finance.outCents)
        assertEquals(4_500, result.finance.balanceCents)
        assertEquals(4_000, result.finance.receivableCents)
        assertEquals("Marina", result.finance.overdueMonthly.single().name)
        assertEquals(LocalDate.of(2026, 10, 1), result.from)
        assertEquals(LocalDate.of(2026, 10, 12), repository.overdueToday)
    }

    @Test
    fun `owner and admin read, athlete is forbidden, outsider is hidden`() {
        repository.role = GroupRole.OWNER
        assertIs<GroupReportResult.Success<GroupReport>>(service.report(actor, group, ReportPeriod.YEAR))
        repository.role = GroupRole.ADMIN
        assertIs<GroupReportResult.Success<GroupReport>>(service.report(actor, group, ReportPeriod.YEAR))
        repository.role = GroupRole.ATHLETE
        assertIs<GroupReportResult.Forbidden>(service.report(actor, group, ReportPeriod.YEAR))
        repository.role = null
        assertIs<GroupReportResult.Hidden>(service.report(actor, group, ReportPeriod.YEAR))
    }

    @Test
    fun `plan without reports blocks report and export but availability answers false`() {
        repository.role = GroupRole.OWNER
        entitled = false

        assertIs<GroupReportResult.PlanRequired>(service.report(actor, group, ReportPeriod.CURRENT_MONTH))
        assertIs<GroupReportResult.PlanRequired>(
            service.export(actor, group, ReportPeriod.CURRENT_MONTH, ReportExportKind.LEDGER),
        )
        assertEquals(false, assertIs<GroupReportResult.Success<Boolean>>(service.availability(actor, group)).value)
        assertEquals(owner, repository.checkedOwner)
    }

    @Test
    fun `availability is true for an entitled admin and hidden for athletes`() {
        repository.role = GroupRole.ADMIN
        assertEquals(true, assertIs<GroupReportResult.Success<Boolean>>(service.availability(actor, group)).value)
        repository.role = GroupRole.ATHLETE
        assertIs<GroupReportResult.Forbidden>(service.availability(actor, group))
    }

    @Test
    fun `export names the file after the kind and the period`() {
        repository.role = GroupRole.OWNER
        repository.games = listOf(game(capacity = 10, confirmed = 5, confirmedMembers = 5, declined = 0, noResponse = 0))

        val export = assertIs<GroupReportResult.Success<ReportExport>>(
            service.export(actor, group, ReportPeriod.CURRENT_MONTH, ReportExportKind.ATTENDANCE),
        ).value

        assertEquals("saqz-presenca-2026-10-01-2026-10-31.csv", export.fileName)
        assertTrue(export.csv.startsWith("﻿Jogo;Data;"))
    }

    private fun game(capacity: Int, confirmed: Int, confirmedMembers: Int, declined: Int, noResponse: Int) =
        ReportGameRow(UUID.randomUUID(), "Jogo", Instant.parse("2026-10-05T22:00:00Z"), capacity, "COMPLETED", confirmed, confirmedMembers, declined, 0, noResponse)

    private fun member(name: String, games: Int, confirmed: Int, declined: Int, responded: Int) =
        ReportMemberRow(UUID.randomUUID(), name, games, confirmed, declined, responded)

    private fun ledger(direction: String, cents: Long) =
        ReportLedgerRow(Instant.parse("2026-10-02T12:00:00Z"), direction, "EXPENSE", "x", "OTHER", null, cents)

    private inner class FakeRepository : GroupReportRepository {
        var role: GroupRole? = GroupRole.OWNER
        var games: List<ReportGameRow> = emptyList()
        var ledger: List<ReportLedgerRow> = emptyList()
        var receivable = 0L
        var overdue: List<ReportOverdueMember> = emptyList()
        var overdueToday: LocalDate? = null
        var checkedOwner: UUID? = null

        override fun role(actorId: UUID, groupId: UUID) = role
        override fun ownerOf(groupId: UUID): UUID? = owner.also { checkedOwner = it }
        override fun timeZone(groupId: UUID): ZoneId = ZoneId.of("America/Sao_Paulo")
        override fun games(groupId: UUID, range: ReportRange, now: Instant) = games
        override fun members(groupId: UUID, range: ReportRange, now: Instant) = emptyList<ReportMemberRow>()
        override fun ledger(groupId: UUID, range: ReportRange) = ledger
        override fun receivableCents(groupId: UUID, range: ReportRange) = receivable
        override fun overdueMonthly(groupId: UUID, range: ReportRange, today: LocalDate): List<ReportOverdueMember> {
            overdueToday = today
            return overdue
        }
    }
}
