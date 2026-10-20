package br.com.saqz.groups.presentation.members

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatInstantClockSaoPaulo
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.MonthlyInviteError
import br.com.saqz.groups.domain.membership.MonthlyInviteGateway
import br.com.saqz.groups.domain.membership.MonthlyInvites
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Convidados de mensalista em Membros (CONV-010 a 012): lista, reenviar, copiar link, remover e invalidar. */
class GroupInviteesViewModel(
    private val groupId: String,
    private val gateway: MonthlyInviteGateway,
) : MviViewModel<GroupInviteesState, GroupInviteesIntent, GroupInviteesEffect>(GroupInviteesState()) {
    private var generation = 0

    init {
        load()
    }

    override fun handleIntent(intent: GroupInviteesIntent) {
        when (intent) {
            GroupInviteesIntent.Refresh -> if (!isFirstAppearance()) load()
            is GroupInviteesIntent.Open -> update { s -> s.copy(selected = s.invitees.firstOrNull { it.id == intent.id }) }
            GroupInviteesIntent.DismissSheet -> update { it.copy(selected = null) }
            GroupInviteesIntent.ResendSelected -> state.value.selected?.let { invitee ->
                act { gateway.resend(GroupId(groupId), invitee.id).asNotice { n -> GroupInviteesNotice.Resent(n) } }
            }
            GroupInviteesIntent.CopyLink -> copyLink()
            GroupInviteesIntent.RemoveSelected -> state.value.selected?.let { invitee ->
                act { gateway.remove(GroupId(groupId), invitee.id).asNotice { GroupInviteesNotice.Removed(invitee.name) } }
            }
            GroupInviteesIntent.ResendAll -> act { gateway.resendAll(GroupId(groupId)).asNotice { n -> GroupInviteesNotice.Resent(n) } }
            GroupInviteesIntent.AskInvalidate -> update { it.copy(confirmInvalidate = true) }
            GroupInviteesIntent.ConfirmInvalidate -> act {
                gateway.invalidateLink(GroupId(groupId)).asNotice { GroupInviteesNotice.Invalidated }
            }
            GroupInviteesIntent.DismissInvalidate -> update { it.copy(confirmInvalidate = false) }
            GroupInviteesIntent.ClearNotice -> update { it.copy(notice = null) }
            GroupInviteesIntent.InviteMonthly -> emit(GroupInviteesEffect.OpenMonthlyInvite(groupId))
        }
    }

    private fun load() {
        val current = ++generation
        viewModelScope.launch {
            val result = gateway.list(GroupId(groupId))
            if (current != generation) return@launch
            when (result) {
                is SaqzResult.Success -> update { it.withList(result.value) }
                // Atleta (403) e grupo escondido: a seção não existe para quem não gerencia.
                is SaqzResult.Failure -> if (result.error.hidesSection()) update { GroupInviteesState() }
            }
        }
    }

    /** Ação de linha ou de seção: um toque por vez; fecha as folhas e recarrega a lista. */
    private fun act(call: suspend () -> GroupInviteesNotice) {
        if (state.value.busy) return
        update { it.copy(busy = true, selected = null, confirmInvalidate = false) }
        viewModelScope.launch {
            val notice = call()
            update { it.copy(busy = false, notice = notice) }
            load()
        }
    }

    private fun copyLink() {
        if (state.value.busy) return
        update { it.copy(busy = true, selected = null) }
        viewModelScope.launch {
            val result = gateway.copyLink(GroupId(groupId))
            if (result is SaqzResult.Success) emit(GroupInviteesEffect.Copy(result.value.url))
            update {
                it.copy(
                    busy = false,
                    notice = if (result is SaqzResult.Failure) result.error.toNotice() else GroupInviteesNotice.LinkCopied,
                )
            }
            load()
        }
    }

    private inline fun <T> SaqzResult<T, MonthlyInviteError>.asNotice(success: (T) -> GroupInviteesNotice) = when (this) {
        is SaqzResult.Success -> success(value)
        is SaqzResult.Failure -> error.toNotice()
    }

    private fun MonthlyInviteError.toNotice() =
        if (this == MonthlyInviteError.WhatsAppUnavailable) GroupInviteesNotice.WhatsAppUnavailable else GroupInviteesNotice.Failed

    private fun MonthlyInviteError.hidesSection() =
        this == MonthlyInviteError.Forbidden || this == MonthlyInviteError.HiddenResource

    private fun GroupInviteesState.withList(list: MonthlyInvites) = copy(
        visible = true,
        invitees = list.invitees.map { InviteeUi(it.id, it.name, it.phone, it.status) },
        linkState = list.link.state,
        linkUntil = list.link.expiresAt?.let(::shortDate),
        pausedRetryAt = formatInstantClockSaoPaulo(list.pausedRetryAt),
        selected = selected?.let { old -> list.invitees.firstOrNull { it.id == old.id } }
            ?.let { InviteeUi(it.id, it.name, it.phone, it.status) },
    )
}

private val Months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

/** "27 de out" em São Paulo, o fuso do aviso do servidor. */
internal fun shortDate(iso: String): String? = runCatching {
    val date = Instant.parse(iso).toLocalDateTime(TimeZone.of("America/Sao_Paulo")).date
    "${date.day} de ${Months[date.month.ordinal]}"
}.getOrNull()
