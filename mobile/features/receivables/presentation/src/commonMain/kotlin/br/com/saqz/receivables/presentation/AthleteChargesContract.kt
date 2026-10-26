package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.PaymentError
import kotlinx.serialization.Serializable

/** Minhas cobranças num grupo com recebimentos ativos (telas Main, Atleta-Mensalista e Netflix-Recusado). */
@Serializable
data class AthleteChargesRoute(val groupId: String, val groupName: String) : NavKey

/** Argumento único do Koin: dois `String` soltos colidiriam no `parametersOf`. */
data class GroupArgs(val groupId: String, val groupName: String)

data class OpenChargeUi(
    val chargeId: String,
    val title: String,
    val support: String,
    val amountLabel: String,
    val overdue: Boolean,
)

data class PaidChargeUi(val chargeId: String, val title: String, val support: String)

sealed interface AutopayCardUi {
    /** Mensalista fora do automático: a oferta "Deixe no automático". */
    data class Offer(val dueDay: String?) : AutopayCardUi
    data class Active(val nextChargeLabel: String?) : AutopayCardUi
    /** Cartão recusado: a mensalidade do mês segue em aberto (Netflix-Recusado). */
    data class Refused(val chargeId: String, val monthLabel: String, val amountLabel: String, val nextChargeLabel: String?) :
        AutopayCardUi
}

data class AthleteChargesState(
    val groupName: String = "",
    val loading: Boolean = true,
    val error: PaymentError? = null,
    /** `false`: o grupo não recebe pelo app (desligou no meio); a tela só mostra o aviso. */
    val active: Boolean = true,
    val totalLabel: String = "",
    /** "3 cobranças" (avulso) ou "Vence em 10/08" (só a mensalidade). */
    val summaryLabel: String = "",
    /** "1 vencida": o detalhe em verde-limão sobre o azul. */
    val overdueLabel: String? = null,
    /** Só a mensalidade em aberto: o hero vira "mensalidade de agosto" com "Pagar mensalidade". */
    val monthlyKicker: String? = null,
    val open: List<OpenChargeUi> = emptyList(),
    val history: List<PaidChargeUi> = emptyList(),
    val historyFailed: Boolean = false,
    val autopay: AutopayCardUi? = null,
) {
    val hasOpen get() = open.isNotEmpty()
}

sealed interface AthleteChargesIntent {
    data object Refresh : AthleteChargesIntent
    /** A tela voltou a aparecer; a primeira vez é a carga do `init`. */
    data object Appeared : AthleteChargesIntent
    data object PayAll : AthleteChargesIntent
    data class Pay(val chargeId: String) : AthleteChargesIntent
    data object OpenAutopay : AthleteChargesIntent
}

sealed interface AthleteChargesEffect {
    data class OpenPayment(val chargeIds: List<String>) : AthleteChargesEffect
    data object OpenAutopay : AthleteChargesEffect
}
