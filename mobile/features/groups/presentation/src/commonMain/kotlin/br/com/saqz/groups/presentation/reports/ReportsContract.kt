package br.com.saqz.groups.presentation.reports

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.GroupReport
import br.com.saqz.groups.domain.finance.ReportExportKind
import br.com.saqz.groups.domain.finance.ReportPeriod
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class ReportsState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    /** O plano do dono do grupo não inclui relatórios (decisão do servidor). */
    val locked: Boolean = false,
    val period: ReportPeriod = ReportPeriod.CurrentMonth,
    val report: GroupReport? = null,
    val exporting: ReportExportKind? = null,
    val exportFailed: Boolean = false,
)

sealed interface ReportsIntent {
    data object Retry : ReportsIntent
    data class SelectPeriod(val period: ReportPeriod) : ReportsIntent
    data class Export(val kind: ReportExportKind) : ReportsIntent
}
