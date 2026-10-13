package br.com.saqz.groups.presentation.reports

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.GroupReportGateway
import br.com.saqz.groups.domain.finance.ReportExportKind
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.coroutines.launch

/**
 * Relatórios do grupo (dono/admin). O plano é decidido pelo servidor: a falha
 * `SubscriptionRequired` vira [ReportsState.locked] e a tela oferece "Ver planos".
 */
class ReportsViewModel(
    private val groupId: String,
    private val gateway: GroupReportGateway,
    private val share: NativeAttendanceSharePort,
) : MviViewModel<ReportsState, ReportsIntent, Nothing>(ReportsState()) {
    private var generation = 0L

    init {
        load()
    }

    override fun handleIntent(intent: ReportsIntent) {
        when (intent) {
            ReportsIntent.Retry -> load()
            is ReportsIntent.SelectPeriod -> if (intent.period != state.value.period && !state.value.locked) {
                update { it.copy(period = intent.period) }
                load()
            }
            is ReportsIntent.Export -> export(intent.kind)
        }
    }

    private fun load() {
        val current = ++generation
        val period = state.value.period
        update { it.copy(isLoading = true, loadFailed = false, error = null, exportFailed = false) }
        viewModelScope.launch {
            val result = gateway.report(GroupId(groupId), period)
            if (current != generation) return@launch
            when (result) {
                is SaqzResult.Success -> update {
                    it.copy(isLoading = false, locked = false, report = result.value)
                }
                is SaqzResult.Failure -> update {
                    if (result.error.isSubscriptionRequired()) {
                        it.copy(isLoading = false, locked = true, report = null)
                    } else {
                        it.copy(isLoading = false, loadFailed = true, error = result.error.toUiError())
                    }
                }
            }
        }
    }

    private fun export(kind: ReportExportKind) {
        val snapshot = state.value
        if (snapshot.locked || snapshot.isLoading || snapshot.exporting != null) return
        update { it.copy(exporting = kind, exportFailed = false) }
        viewModelScope.launch {
            when (val result = gateway.export(GroupId(groupId), snapshot.period, kind)) {
                is SaqzResult.Failure -> update { it.copy(exporting = null, exportFailed = true) }
                is SaqzResult.Success -> share.shareFile(
                    fileName = result.value.fileName,
                    mimeType = CSV_MIME_TYPE,
                    content = result.value.csv,
                ) { outcome ->
                    update {
                        it.copy(exporting = null, exportFailed = outcome == NativeAttendanceShareResult.Failure)
                    }
                }
            }
        }
    }

    private fun FinanceError.isSubscriptionRequired() = this == FinanceError.Data(DataError.SubscriptionRequired)

    private fun FinanceError.toUiError(): GroupUiError = when (this) {
        is FinanceError.Validation -> GroupUiError.Validation
        FinanceError.HiddenResource -> GroupUiError.NotFound
        FinanceError.Forbidden, FinanceError.Authentication -> GroupUiError.AccessDenied
        FinanceError.Conflict -> GroupUiError.Conflict
        FinanceError.PreconditionRequired, FinanceError.InvalidLifecycle, is FinanceError.Data -> GroupUiError.Network
    }

    private companion object {
        const val CSV_MIME_TYPE = "text/csv"
    }
}
