package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.PaymentError
import kotlinx.serialization.Serializable

/**
 * Pagamento do atleta (Atleta-Pagar-Pix, Atleta-Pagar-Cartao, Atleta-Confirmado). Sem [chargeIds], [gameId] nem
 * [billingMonth], cobre tudo o que está em aberto no grupo ("Pagar tudo"). `chargeIds` vai separado por vírgula:
 * rota só leva escalar.
 */
@Serializable
data class ReceivablePaymentRoute(
    val groupId: String,
    val groupName: String,
    val chargeIds: String = "",
    val gameId: String? = null,
    val billingMonth: String? = null,
    /** Cartão recusado (`paymentKind=REFUSED`): além do Pix, "Atualizar cartão" leva ao automático. */
    val offerCardUpdate: Boolean = false,
) : NavKey

data class PaymentArgs(
    val groupId: String,
    val groupName: String,
    val chargeIds: List<String> = emptyList(),
    val gameId: String? = null,
    val billingMonth: String? = null,
)

fun ReceivablePaymentRoute.args() = PaymentArgs(groupId, groupName,
    chargeIds.split(',').map(String::trim).filter(String::isNotEmpty), gameId, billingMonth)

enum class PayTab { PIX, CARD }

/** O título da tela: "Pagar meu jogo", "Pagar mensalidade" ou "Pagar". */
enum class PaymentSubject { GAME, MONTHLY, MANY }

data class PaymentLineUi(val label: String, val amountLabel: String)

data class PaymentConfirmedUi(
    /** "R$ 100,00 · mensalidade de agosto". */
    val summary: String,
    /** "Quer deixar no automático?" — só mensalista, no máximo uma vez por mês. */
    val offer: Boolean,
    val dueDay: String?,
)

data class PaymentState(
    val groupName: String = "",
    val loading: Boolean = true,
    val error: PaymentError? = null,
    /** Quem não deve vê "Você não tem nada em aberto aqui". */
    val nothingOpen: Boolean = false,
    val subject: PaymentSubject = PaymentSubject.MANY,
    val kicker: String = "",
    val amountLabel: String = "",
    val lines: List<PaymentLineUi> = emptyList(),
    val pixAvailable: Boolean = true,
    val cardAvailable: Boolean = true,
    val tab: PayTab = PayTab.PIX,
    val creatingCode: Boolean = false,
    val pixPayload: String? = null,
    val pixImage: String? = null,
    val payeeName: String? = null,
    val copied: Boolean = false,
    /** A cobrança mudou (o gestor deu baixa por fora, por exemplo): o código antigo não vale mais. */
    val codeExpired: Boolean = false,
    val needsDocument: Boolean = false,
    val cpf: String = "",
    val cpfInvalid: Boolean = false,
    /** Voltou da página do Asaas: esperando a confirmação do cartão. */
    val waitingCard: Boolean = false,
    val confirmed: PaymentConfirmedUi? = null,
) {
    val canContinueCard get() = !loading && !creatingCode && (!needsDocument || cpf.length == 11)
    val canPayByCard get() = tab == PayTab.CARD && canContinueCard && confirmed == null
    /** Ainda esperando a baixa: a volta à tela consulta o código. */
    val waiting get() = confirmed == null && !loading
}

sealed interface PaymentIntent {
    data object Retry : PaymentIntent
    data class SelectTab(val tab: PayTab) : PaymentIntent
    data object CopyPix : PaymentIntent
    data object NewCode : PaymentIntent
    data class CpfChanged(val value: String) : PaymentIntent
    data object ContinueCard : PaymentIntent
    /** A tela voltou a aparecer (volta do navegador): consulta o código na hora. */
    data object Resumed : PaymentIntent
    data object AcceptOffer : PaymentIntent
    data object DeclineOffer : PaymentIntent
}

sealed interface PaymentEffect {
    data class Copy(val text: String) : PaymentEffect
    data class OpenUrl(val url: String) : PaymentEffect
    data object OpenAutopay : PaymentEffect
}
