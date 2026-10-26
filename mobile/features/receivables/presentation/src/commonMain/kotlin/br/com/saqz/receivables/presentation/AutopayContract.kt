package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.AutopayCancelReason
import br.com.saqz.receivables.domain.PaymentError
import br.com.saqz.receivables.domain.RecurrenceStatus
import kotlinx.serialization.Serializable

/** Mensalidade no cartão, o "modo Netflix" (Netflix-Adesao, Netflix-Gerenciar, Netflix-Cancelar). */
@Serializable
data class AutopayRoute(val groupId: String, val groupName: String) : NavKey

data class AutopayHistoryUi(val title: String, val support: String)

data class AutopayState(
    val groupName: String = "",
    val loading: Boolean = true,
    val error: PaymentError? = null,
    /** `null` depois da carga: o servidor não oferece o automático para esta pessoa (avulso, ou antes da F3). */
    val status: RecurrenceStatus? = null,
    val amountLabel: String = "",
    /** "Cobrado todo dia 10". */
    val dueDay: String? = null,
    /** "Agosto está em aberto: cobramos agora". */
    val openMonth: String? = null,
    /** "R$ 100,00 em 10/09". */
    val nextCharge: String? = null,
    val history: List<AutopayHistoryUi> = emptyList(),
    val cancelling: Boolean = false,
    val reason: AutopayCancelReason? = null,
    val busy: Boolean = false,
    val cancelled: Boolean = false,
) {
    val canConfirmCancel get() = cancelling && reason != null && !busy
}

sealed interface AutopayIntent {
    data object Retry : AutopayIntent
    data object Resumed : AutopayIntent
    data object Start : AutopayIntent
    data object ChangeCard : AutopayIntent
    data object RequestCancel : AutopayIntent
    data class SelectReason(val reason: AutopayCancelReason) : AutopayIntent
    data object ConfirmCancel : AutopayIntent
    data object KeepAutopay : AutopayIntent
}

sealed interface AutopayEffect {
    data class OpenUrl(val url: String) : AutopayEffect
}
