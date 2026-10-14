package br.com.saqz.groups.presentation.reports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.AttendanceReport
import br.com.saqz.groups.domain.finance.FinanceReport
import br.com.saqz.groups.domain.finance.GroupReport
import br.com.saqz.groups.domain.finance.ReportExportKind
import br.com.saqz.groups.domain.finance.ReportOverdueMember
import br.com.saqz.groups.domain.finance.ReportPeriod
import br.com.saqz.groups.domain.finance.ReportRankedMember
import br.com.saqz.groups.presentation.ui.LocalOpenSubscriptionPlans
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReportsScreenTest {
    private val report = GroupReport(
        period = ReportPeriod.CurrentMonth,
        from = "2026-10-01",
        toExclusive = "2026-11-01",
        attendance = AttendanceReport(
            games = 4,
            averageConfirmed = 8.5,
            averageCapacity = 12.0,
            confirmationRatePercent = 80,
            mostPresent = listOf(ReportRankedMember("u1", "Ana Souza", 4), ReportRankedMember("u2", "Bia Lima", 1)),
            mostDeclined = listOf(ReportRankedMember("u3", "Caio Melo", 2)),
            neverResponded = listOf(ReportRankedMember("u4", "Edu Rocha", 4)),
        ),
        finance = FinanceReport(
            inCents = 150_050,
            outCents = 30_000,
            balanceCents = 120_050,
            receivableCents = 40_000,
            overdueMonthly = listOf(ReportOverdueMember("u5", "Marina Reis", 12_000, 2)),
        ),
    )

    @Test
    fun showsAttendanceFinanceAndExportNumbers() = runComposeUiTest {
        setContent { SaqzTheme { ReportsScreen(ReportsState(isLoading = false, report = report), {}, {}) } }

        onNodeWithText("Jogos no período").assertExists()
        onNodeWithText("8,5 de 12 vagas").assertExists()
        onNodeWithText("80%").assertExists()
        onNodeWithTag(ReportsTags.AttendanceBasis).assertExists()
        onNodeWithText("Ana Souza").assertExists()
        onAllNodesWithText("4 jogos").assertCountEquals(2)
        onNodeWithText("1 jogo").assertExists()
        onNodeWithText("Edu Rocha").assertExists()
        onNodeWithText("R$ 1.500,50").assertExists()
        onNodeWithText("R$ 300,00").assertExists()
        onNodeWithText("R$ 1.200,50").assertExists()
        onNodeWithText("R$ 400,00").assertExists()
        onNodeWithText("Marina Reis").assertExists()
        onNodeWithText("R$ 120,00 · 2 mensalidades").assertExists()
    }

    @Test
    fun emptyRankingsShowAFriendlyLine() = runComposeUiTest {
        val empty = report.copy(
            attendance = report.attendance.copy(
                mostPresent = emptyList(), mostDeclined = emptyList(), neverResponded = emptyList(),
            ),
            finance = report.finance.copy(overdueMonthly = emptyList()),
        )
        setContent { SaqzTheme { ReportsScreen(ReportsState(isLoading = false, report = empty), {}, {}) } }

        onNodeWithText("Nenhuma mensalidade atrasada.").assertExists()
    }

    @Test
    fun periodAndExportButtonsEmitIntents() = runComposeUiTest {
        val intents = mutableListOf<ReportsIntent>()
        setContent {
            SaqzTheme { ReportsScreen(ReportsState(isLoading = false, report = report), {}, { intents += it }) }
        }

        onNodeWithText("Ano").performClick()
        onNodeWithTag(ReportsTags.ExportAttendance).performScrollTo().performClick()
        onNodeWithTag(ReportsTags.ExportLedger).performScrollTo().performClick()

        assertEquals(
            listOf(
                ReportsIntent.SelectPeriod(ReportPeriod.Year),
                ReportsIntent.Export(ReportExportKind.Attendance),
                ReportsIntent.Export(ReportExportKind.Ledger),
            ),
            intents,
        )
    }

    @Test
    fun exportFailureIsShown() = runComposeUiTest {
        setContent {
            SaqzTheme { ReportsScreen(ReportsState(isLoading = false, report = report, exportFailed = true), {}, {}) }
        }

        onNodeWithTag(ReportsTags.ExportFailed).performScrollTo().assertExists()
    }

    @Test
    fun lockedStateOffersPlansAndHidesTheReport() = runComposeUiTest {
        var plansOpened = 0
        setContent {
            CompositionLocalProvider(LocalOpenSubscriptionPlans provides { plansOpened++ }) {
                SaqzTheme { ReportsScreen(ReportsState(isLoading = false, locked = true), {}, {}) }
            }
        }

        onNodeWithText("Relatórios são do plano Ilimitado").assertExists()
        onAllNodesWithTag(ReportsTags.Content).assertCountEquals(0)
        onNodeWithText("Ver planos").performClick()

        assertEquals(1, plansOpened)
    }
}
