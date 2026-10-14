package br.com.saqz.groups.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.AttendanceReport
import br.com.saqz.groups.domain.finance.FinanceReport
import br.com.saqz.groups.domain.finance.ReportExportKind
import br.com.saqz.groups.domain.finance.ReportPeriod
import br.com.saqz.groups.domain.finance.ReportRankedMember
import br.com.saqz.groups.presentation.statement.formatStatementCurrency
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.LocalOpenSubscriptionPlans
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.reports_attendance_basis
import br.com.saqz.groups.resources.reports_attendance_title
import br.com.saqz.groups.resources.reports_average
import br.com.saqz.groups.resources.reports_average_value
import br.com.saqz.groups.resources.reports_balance
import br.com.saqz.groups.resources.reports_export_attendance
import br.com.saqz.groups.resources.reports_export_failed
import br.com.saqz.groups.resources.reports_export_ledger
import br.com.saqz.groups.resources.reports_export_title
import br.com.saqz.groups.resources.reports_finance_title
import br.com.saqz.groups.resources.reports_games
import br.com.saqz.groups.resources.reports_games_many
import br.com.saqz.groups.resources.reports_games_one
import br.com.saqz.groups.resources.reports_in
import br.com.saqz.groups.resources.reports_locked_action
import br.com.saqz.groups.resources.reports_locked_body
import br.com.saqz.groups.resources.reports_locked_title
import br.com.saqz.groups.resources.reports_months_many
import br.com.saqz.groups.resources.reports_months_one
import br.com.saqz.groups.resources.reports_most_declined
import br.com.saqz.groups.resources.reports_most_present
import br.com.saqz.groups.resources.reports_never_responded
import br.com.saqz.groups.resources.reports_none
import br.com.saqz.groups.resources.reports_out
import br.com.saqz.groups.resources.reports_overdue_none
import br.com.saqz.groups.resources.reports_overdue_title
import br.com.saqz.groups.resources.reports_period_current
import br.com.saqz.groups.resources.reports_period_previous
import br.com.saqz.groups.resources.reports_period_year
import br.com.saqz.groups.resources.reports_rate
import br.com.saqz.groups.resources.reports_rate_value
import br.com.saqz.groups.resources.reports_receivable
import br.com.saqz.groups.resources.reports_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

object ReportsTags {
    const val Screen = "reports-screen"
    const val Period = "reports-period"
    const val Locked = "reports-locked"
    const val AttendanceBasis = "reports-attendance-basis"
    const val Content = "reports-content"
    const val ExportAttendance = "reports-export-attendance"
    const val ExportLedger = "reports-export-ledger"
    const val ExportFailed = "reports-export-failed"
}

@Composable
internal fun ReportsScreen(
    state: ReportsState,
    onBack: () -> Unit,
    onIntent: (ReportsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openPlans = LocalOpenSubscriptionPlans.current
    Column(
        modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(ReportsTags.Screen),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.reports_title), onBack = onBack)
        when {
            state.locked -> SaqzEmptyState(
                title = stringResource(Res.string.reports_locked_title),
                description = stringResource(Res.string.reports_locked_body),
                icon = SaqzIcons.Lock,
                action = stringResource(Res.string.reports_locked_action),
                onAction = openPlans,
                actionFullWidth = true,
                modifier = Modifier.weight(1f).testTag(ReportsTags.Locked),
            )
            state.loadFailed -> GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(ReportsIntent.Retry) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            else -> ReportsContent(state, onIntent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReportsContent(state: ReportsState, onIntent: (ReportsIntent) -> Unit, modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    val periods = ReportPeriod.entries
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(ReportsTags.Content),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSegmented(
            options = listOf(
                stringResource(Res.string.reports_period_current),
                stringResource(Res.string.reports_period_previous),
                stringResource(Res.string.reports_period_year),
            ),
            selected = periods.indexOf(state.period),
            onSelect = { onIntent(ReportsIntent.SelectPeriod(periods[it])) },
            modifier = Modifier.testTag(ReportsTags.Period),
        )
        val report = state.report
        if (state.isLoading && report == null) {
            Box(Modifier.fillMaxWidth().padding(vertical = metrics.blockGap), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
        }
        if (report != null) {
            AttendanceSection(report.attendance)
            FinanceSection(report.finance)
        }
        ExportSection(state, onIntent)
    }
}

@Composable
private fun AttendanceSection(attendance: AttendanceReport) {
    SaqzSectionHeader(title = stringResource(Res.string.reports_attendance_title))
    SaqzCard {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            ValueRow(stringResource(Res.string.reports_games), attendance.games.toString())
            ValueRow(
                stringResource(Res.string.reports_average),
                stringResource(
                    Res.string.reports_average_value,
                    oneDecimal(attendance.averageConfirmed),
                    oneDecimal(attendance.averageCapacity),
                ),
            )
            ValueRow(
                stringResource(Res.string.reports_rate),
                stringResource(Res.string.reports_rate_value, attendance.confirmationRatePercent),
            )
        }
    }
    Text(
        stringResource(Res.string.reports_attendance_basis),
        style = SaqzTheme.typography.caption,
        color = SaqzTheme.colors.textSecondary,
        modifier = Modifier.testTag(ReportsTags.AttendanceBasis),
    )
    RankingCard(stringResource(Res.string.reports_most_present), attendance.mostPresent)
    RankingCard(stringResource(Res.string.reports_most_declined), attendance.mostDeclined)
    RankingCard(stringResource(Res.string.reports_never_responded), attendance.neverResponded)
}

@Composable
private fun RankingCard(title: String, members: List<ReportRankedMember>) {
    SaqzCard {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            Text(title, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
            if (members.isEmpty()) {
                Text(
                    stringResource(Res.string.reports_none),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            members.forEachIndexed { index, member ->
                if (index > 0) SaqzDivider()
                ValueRow(member.name, gamesLabel(member.count))
            }
        }
    }
}

@Composable
private fun FinanceSection(finance: FinanceReport) {
    SaqzSectionHeader(title = stringResource(Res.string.reports_finance_title))
    SaqzCard {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            ValueRow(stringResource(Res.string.reports_in), money(finance.inCents))
            ValueRow(stringResource(Res.string.reports_out), money(finance.outCents))
            ValueRow(stringResource(Res.string.reports_balance), money(finance.balanceCents), emphasis = true)
            SaqzDivider()
            ValueRow(stringResource(Res.string.reports_receivable), money(finance.receivableCents))
        }
    }
    SaqzCard {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            Text(
                stringResource(Res.string.reports_overdue_title),
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
            )
            if (finance.overdueMonthly.isEmpty()) {
                Text(
                    stringResource(Res.string.reports_overdue_none),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            finance.overdueMonthly.forEachIndexed { index, member ->
                if (index > 0) SaqzDivider()
                ValueRow(
                    label = member.name,
                    value = "${money(member.amountCents)} · ${monthsLabel(member.months)}",
                )
            }
        }
    }
}

@Composable
private fun ExportSection(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    SaqzSectionHeader(title = stringResource(Res.string.reports_export_title))
    Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzButton(
            label = stringResource(Res.string.reports_export_attendance),
            onClick = { onIntent(ReportsIntent.Export(ReportExportKind.Attendance)) },
            variant = SaqzButtonVariant.Secondary,
            loading = state.exporting == ReportExportKind.Attendance,
            enabled = state.exporting == null && !state.isLoading,
            fullWidth = true,
            modifier = Modifier.testTag(ReportsTags.ExportAttendance),
        )
        SaqzButton(
            label = stringResource(Res.string.reports_export_ledger),
            onClick = { onIntent(ReportsIntent.Export(ReportExportKind.Ledger)) },
            variant = SaqzButtonVariant.Secondary,
            loading = state.exporting == ReportExportKind.Ledger,
            enabled = state.exporting == null && !state.isLoading,
            fullWidth = true,
            modifier = Modifier.testTag(ReportsTags.ExportLedger),
        )
        if (state.exportFailed) {
            Text(
                stringResource(Res.string.reports_export_failed),
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(ReportsTags.ExportFailed),
            )
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = SaqzTheme.typography.support.copy(
                fontWeight = if (emphasis) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = SaqzTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun gamesLabel(count: Int) = if (count == 1) {
    stringResource(Res.string.reports_games_one)
} else {
    stringResource(Res.string.reports_games_many, count)
}

@Composable
private fun monthsLabel(count: Int) = if (count == 1) {
    stringResource(Res.string.reports_months_one)
} else {
    stringResource(Res.string.reports_months_many, count)
}

private fun money(cents: Long): String = (if (cents < 0) "−" else "") + formatStatementCurrency(abs(cents))

/** 8.5 -> "8,5"; 10.0 -> "10". */
internal fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole" else "$whole,$fraction"
}
