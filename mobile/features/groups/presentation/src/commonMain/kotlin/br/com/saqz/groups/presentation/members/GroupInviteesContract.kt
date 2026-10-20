package br.com.saqz.groups.presentation.members

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInviteeStatus

@Immutable
data class InviteeUi(val id: String, val name: String, val phone: String, val status: MonthlyInviteeStatus)

sealed interface GroupInviteesNotice {
    data class Resent(val count: Int) : GroupInviteesNotice

    data class Removed(val name: String) : GroupInviteesNotice

    data object Invalidated : GroupInviteesNotice

    data object LinkCopied : GroupInviteesNotice

    data object WhatsAppUnavailable : GroupInviteesNotice

    data object Failed : GroupInviteesNotice
}

/** Seção "Convidados" de Membros (CONV-010): só dono e admin a veem; para o atleta o backend responde 403 e ela some. */
@Immutable
data class GroupInviteesState(
    val visible: Boolean = false,
    val invitees: List<InviteeUi> = emptyList(),
    val linkState: MonthlyInviteLinkState = MonthlyInviteLinkState.NONE,
    /** "27 de out", no fuso do grupo. */
    val linkUntil: String? = null,
    /** "14h32": a próxima tentativa da fila pausada. */
    val pausedRetryAt: String? = null,
    val selected: InviteeUi? = null,
    val confirmInvalidate: Boolean = false,
    val busy: Boolean = false,
    val notice: GroupInviteesNotice? = null,
) {
    /** Reenviar para todos: Aguardando, Falhou, Pausado e Venceu; Na fila e Sem WhatsApp ficam de fora. */
    val canResendAll: Boolean get() = invitees.any { it.status in ResendAllStatuses }

    val canInvalidate: Boolean get() = linkState == MonthlyInviteLinkState.ACTIVE || linkState == MonthlyInviteLinkState.EXPIRED
}

internal val ResendAllStatuses = setOf(
    MonthlyInviteeStatus.WAITING,
    MonthlyInviteeStatus.FAILED,
    MonthlyInviteeStatus.PAUSED,
    MonthlyInviteeStatus.EXPIRED,
)

sealed interface GroupInviteesIntent {
    data object Refresh : GroupInviteesIntent

    data class Open(val id: String) : GroupInviteesIntent

    data object DismissSheet : GroupInviteesIntent

    data object ResendSelected : GroupInviteesIntent

    data object CopyLink : GroupInviteesIntent

    data object RemoveSelected : GroupInviteesIntent

    data object ResendAll : GroupInviteesIntent

    data object AskInvalidate : GroupInviteesIntent

    data object ConfirmInvalidate : GroupInviteesIntent

    data object DismissInvalidate : GroupInviteesIntent

    data object ClearNotice : GroupInviteesIntent

    data object InviteMonthly : GroupInviteesIntent
}

sealed interface GroupInviteesEffect {
    data class OpenMonthlyInvite(val groupId: String) : GroupInviteesEffect

    data class Copy(val text: String) : GroupInviteesEffect
}
