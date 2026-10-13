package br.com.saqz.groups.presentation.reports

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.finance.AttendanceReport
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceReport
import br.com.saqz.groups.domain.finance.GroupReport
import br.com.saqz.groups.domain.finance.GroupReportGateway
import br.com.saqz.groups.domain.finance.ReportExport
import br.com.saqz.groups.domain.finance.ReportExportKind
import br.com.saqz.groups.domain.finance.ReportPeriod
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opens on the current month and loads the report`() = runTest(dispatcher) {
        val gateway = FakeReportGateway()
        val viewModel = ReportsViewModel("group-1", gateway, FakeFileShare())

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(ReportPeriod.CurrentMonth, viewModel.state.value.period)
        assertEquals(listOf(ReportPeriod.CurrentMonth), gateway.periods)
        assertEquals(4, viewModel.state.value.report?.attendance?.games)
    }

    @Test
    fun `selecting another period reloads and ignores a repeated selection`() = runTest(dispatcher) {
        val gateway = FakeReportGateway()
        val viewModel = ReportsViewModel("group-1", gateway, FakeFileShare())

        viewModel.onIntent(ReportsIntent.SelectPeriod(ReportPeriod.Year))
        viewModel.onIntent(ReportsIntent.SelectPeriod(ReportPeriod.Year))

        assertEquals(ReportPeriod.Year, viewModel.state.value.period)
        assertEquals(listOf(ReportPeriod.CurrentMonth, ReportPeriod.Year), gateway.periods)
    }

    @Test
    fun `plan without reports locks the screen and offers no content`() = runTest(dispatcher) {
        val gateway = FakeReportGateway(failure = FinanceError.Data(DataError.SubscriptionRequired))
        val viewModel = ReportsViewModel("group-1", gateway, FakeFileShare())

        assertTrue(viewModel.state.value.locked)
        assertFalse(viewModel.state.value.loadFailed)
        assertNull(viewModel.state.value.report)

        viewModel.onIntent(ReportsIntent.Export(ReportExportKind.Ledger))
        assertTrue(gateway.exports.isEmpty())
    }

    @Test
    fun `other failures show the retryable error and retry recovers`() = runTest(dispatcher) {
        val gateway = FakeReportGateway(failure = FinanceError.Data(DataError.Connectivity))
        val viewModel = ReportsViewModel("group-1", gateway, FakeFileShare())

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.Network, viewModel.state.value.error)

        gateway.failure = null
        viewModel.onIntent(ReportsIntent.Retry)

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(4, viewModel.state.value.report?.attendance?.games)
    }

    @Test
    fun `export shares the csv file through the system sheet`() = runTest(dispatcher) {
        val gateway = FakeReportGateway()
        val share = FakeFileShare()
        val viewModel = ReportsViewModel("group-1", gateway, share)
        viewModel.onIntent(ReportsIntent.SelectPeriod(ReportPeriod.PreviousMonth))

        viewModel.onIntent(ReportsIntent.Export(ReportExportKind.Attendance))

        assertEquals(listOf(ReportPeriod.PreviousMonth to ReportExportKind.Attendance), gateway.exports)
        assertEquals(Triple("saqz-presenca.csv", "text/csv", "Jogo;Data\r\n"), share.shared.single())
        assertNull(viewModel.state.value.exporting)
        assertFalse(viewModel.state.value.exportFailed)
    }

    @Test
    fun `failed export and failed share both report the failure`() = runTest(dispatcher) {
        val gateway = FakeReportGateway()
        val share = FakeFileShare()
        val viewModel = ReportsViewModel("group-1", gateway, share)

        gateway.exportFailure = FinanceError.Data(DataError.Server)
        viewModel.onIntent(ReportsIntent.Export(ReportExportKind.Ledger))
        assertTrue(viewModel.state.value.exportFailed)
        assertNull(viewModel.state.value.exporting)

        gateway.exportFailure = null
        share.result = NativeAttendanceShareResult.Failure
        viewModel.onIntent(ReportsIntent.Export(ReportExportKind.Ledger))
        assertTrue(viewModel.state.value.exportFailed)

        share.result = NativeAttendanceShareResult.Success
        viewModel.onIntent(ReportsIntent.Export(ReportExportKind.Ledger))
        assertFalse(viewModel.state.value.exportFailed)
    }

    private class FakeReportGateway(var failure: FinanceError? = null) : GroupReportGateway {
        val periods = mutableListOf<ReportPeriod>()
        val exports = mutableListOf<Pair<ReportPeriod, ReportExportKind>>()
        var exportFailure: FinanceError? = null

        override suspend fun availability(groupId: GroupId): SaqzResult<Boolean, FinanceError> =
            SaqzResult.Success(true)

        override suspend fun report(groupId: GroupId, period: ReportPeriod): SaqzResult<GroupReport, FinanceError> {
            periods += period
            failure?.let { return SaqzResult.Failure(it) }
            return SaqzResult.Success(
                GroupReport(
                    period = period,
                    from = "2026-10-01",
                    toExclusive = "2026-11-01",
                    attendance = AttendanceReport(4, 8.5, 11.0, 80, emptyList(), emptyList(), emptyList()),
                    finance = FinanceReport(7_500, 3_000, 4_500, 4_000, emptyList()),
                ),
            )
        }

        override suspend fun export(
            groupId: GroupId,
            period: ReportPeriod,
            kind: ReportExportKind,
        ): SaqzResult<ReportExport, FinanceError> {
            exports += period to kind
            exportFailure?.let { return SaqzResult.Failure(it) }
            return SaqzResult.Success(
                ReportExport(
                    if (kind == ReportExportKind.Attendance) "saqz-presenca.csv" else "saqz-lancamentos.csv",
                    "Jogo;Data\r\n",
                ),
            )
        }
    }

    private class FakeFileShare : NativeAttendanceSharePort {
        val shared = mutableListOf<Triple<String, String, String>>()
        var result: NativeAttendanceShareResult = NativeAttendanceShareResult.Success

        override fun shareLink(url: String, done: (NativeAttendanceShareResult) -> Unit) = done(result)

        override fun shareImage(image: AttendanceShareImage, done: (NativeAttendanceShareResult) -> Unit) = done(result)

        override fun shareFile(
            fileName: String,
            mimeType: String,
            content: String,
            done: (NativeAttendanceShareResult) -> Unit,
        ) {
            shared += Triple(fileName, mimeType, content)
            done(result)
        }
    }
}
