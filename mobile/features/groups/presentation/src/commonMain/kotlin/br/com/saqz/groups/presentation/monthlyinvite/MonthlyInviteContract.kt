package br.com.saqz.groups.presentation.monthlyinvite

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.membership.MonthlyFeeDefault
import br.com.saqz.groups.domain.membership.MonthlyInvitePlanLimit
import br.com.saqz.groups.domain.membership.MonthlyInviteRejection

@Immutable
data class MonthlyInvitePersonUi(val name: String, val phone: String)

/** O número digitado já tem dono no grupo: a folha avisa e não deixa ir para a fila. */
sealed interface MonthlyInviteMatch {
    data class Mensalista(val name: String) : MonthlyInviteMatch

    data class Avulso(val name: String, val userId: String) : MonthlyInviteMatch
}

@Immutable
data class MonthlyInviteSheet(
    val name: String = "",
    val phone: String = "",
    val nameError: Boolean = false,
    val phoneError: MonthlyInvitePhoneError? = null,
    /** Nome de quem já está na lista com o mesmo número. */
    val duplicateOf: String? = null,
    /** Nome de quem já está em Convidados com o mesmo número. */
    val invitedName: String? = null,
    val match: MonthlyInviteMatch? = null,
    val promoting: Boolean = false,
)

sealed interface MonthlyInviteToast {
    data class Sent(val count: Int) : MonthlyInviteToast

    data class Removed(val name: String) : MonthlyInviteToast

    data class Promoted(val name: String) : MonthlyInviteToast

    data object Copied : MonthlyInviteToast
}

@Immutable
data class MonthlyInviteState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val groupName: String = "",
    val senderName: String = "",
    /** Sem valor e dia padrão a tela fica bloqueada (MEM-003). */
    val defaultFee: MonthlyFeeDefault? = null,
    val whatsAppAvailable: Boolean = true,
    /** Link vivo, quando já existe: entra na mensagem de exemplo. */
    val linkUrl: String? = null,
    val people: List<MonthlyInvitePersonUi> = emptyList(),
    /** Recusas do servidor no último envio, pela posição na lista. */
    val rowErrors: Map<Int, MonthlyInviteRejection> = emptyMap(),
    /** Vagas do plano vistas no último envio recusado por `PLAN_LIMIT`. */
    val planLimit: MonthlyInvitePlanLimit? = null,
    val sheet: MonthlyInviteSheet? = null,
    val messageOpen: Boolean = false,
    val sending: Boolean = false,
    val copying: Boolean = false,
    val toast: MonthlyInviteToast? = null,
    val actionFailed: Boolean = false,
    val subscriptionRequired: Boolean = false,
) {
    /** Pessoas a mais que as vagas livres; > 0 desliga o envio. */
    val overLimit: Int get() = planLimit?.let { people.size - it.free } ?: 0

    val canSend: Boolean get() = people.isNotEmpty() && overLimit <= 0 && !sending && rowErrors.isEmpty()
}

sealed interface MonthlyInviteIntent {
    data object Retry : MonthlyInviteIntent

    data object Refresh : MonthlyInviteIntent

    data object OpenSheet : MonthlyInviteIntent

    data object CloseSheet : MonthlyInviteIntent

    data class UpdateName(val value: String) : MonthlyInviteIntent

    data class UpdatePhone(val value: String) : MonthlyInviteIntent

    /** [keepOpen]: "Adicionar e cadastrar outra". */
    data class Add(val keepOpen: Boolean) : MonthlyInviteIntent

    data object Promote : MonthlyInviteIntent

    data class Remove(val index: Int) : MonthlyInviteIntent

    data object Undo : MonthlyInviteIntent

    data object ToggleMessage : MonthlyInviteIntent

    data object Send : MonthlyInviteIntent

    data object CopyMessage : MonthlyInviteIntent

    data object CopyLink : MonthlyInviteIntent

    data object ConfigureFee : MonthlyInviteIntent

    data object OpenPlans : MonthlyInviteIntent

    data object DismissToast : MonthlyInviteIntent

    data object DismissActionFailed : MonthlyInviteIntent

    data object DismissSubscriptionRequired : MonthlyInviteIntent
}

sealed interface MonthlyInviteEffect {
    data class OpenFeeSettings(val groupId: String) : MonthlyInviteEffect

    data object OpenPlans : MonthlyInviteEffect

    data class Copy(val text: String) : MonthlyInviteEffect
}

/** Texto fixo do convite (CONV-012); sem nome no modo de copiar, que vai para várias pessoas. */
fun monthlyInviteMessage(firstName: String?, sender: String, group: String, link: String): String =
    "E aí${firstName?.let { ", $it" }.orEmpty()}! 🏐 O $sender te colocou na lista de mensalistas do $group aqui no Saqz. " +
        "Entra por aqui e confirma presença nos jogos direto pelo app: $link\nSe não era pra você, é só ignorar."
