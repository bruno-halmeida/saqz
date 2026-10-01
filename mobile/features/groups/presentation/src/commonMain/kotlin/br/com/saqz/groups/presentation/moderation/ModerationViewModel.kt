package br.com.saqz.groups.presentation.moderation

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.moderation.ContentReport
import br.com.saqz.groups.domain.moderation.ModerationGateway
import kotlinx.coroutines.launch

/**
 * Denunciar e bloquear, com os mesmos sheets em toda tela que mostra gente ou conteúdo do
 * grupo. A tela hospedeira só diz o quê (`StartReport`/`StartBlock`/`Unblock`); recarregar o
 * conteúdo depois do bloqueio é de quem escuta [BlockedPeopleRepository.changes].
 */
class ModerationViewModel(
    private val gateway: ModerationGateway,
    private val blocks: BlockedPeopleRepository,
) : MviViewModel<ModerationState, ModerationIntent, Nothing>(ModerationState()) {

    override fun handleIntent(intent: ModerationIntent) {
        when (intent) {
            is ModerationIntent.StartReport -> if (!state.value.busy) {
                update { it.copy(report = ReportDraftUi(intent.target), block = null) }
            }
            is ModerationIntent.SelectReason -> updateDraft { it.copy(reason = intent.reason, failed = false) }
            is ModerationIntent.UpdateDetails -> updateDraft {
                it.copy(details = intent.text.take(ContentReport.MAX_DETAILS_LENGTH), failed = false)
            }
            ModerationIntent.SubmitReport -> submitReport()
            ModerationIntent.DismissReport -> if (state.value.report?.sending != true) update { it.copy(report = null) }
            is ModerationIntent.StartBlock -> if (!state.value.busy) {
                update { it.copy(block = BlockPromptUi(intent.groupId, intent.userId, intent.name), report = null) }
            }
            ModerationIntent.ConfirmBlock -> confirmBlock()
            ModerationIntent.DismissBlock -> if (state.value.block?.blocking != true) update { it.copy(block = null) }
            is ModerationIntent.Unblock -> unblock(intent.userId)
            ModerationIntent.DismissFeedback -> update { it.copy(feedback = null) }
        }
    }

    private fun updateDraft(transform: (ReportDraftUi) -> ReportDraftUi) {
        val draft = state.value.report ?: return
        if (draft.sending) return
        update { it.copy(report = transform(draft)) }
    }

    private fun submitReport() {
        val draft = state.value.report ?: return
        val reason = draft.reason ?: return
        if (draft.sending) return
        update { it.copy(report = draft.copy(sending = true, failed = false)) }
        viewModelScope.launch {
            val report = ContentReport(
                groupId = GroupId(draft.target.groupId),
                targetType = draft.target.type,
                targetId = draft.target.targetId,
                reason = reason,
                details = draft.details.trim().takeIf(String::isNotEmpty),
            )
            when (gateway.fileContentReport(report)) {
                is SaqzResult.Success -> update { it.copy(report = null, feedback = ModerationFeedback.ReportSent) }
                is SaqzResult.Failure -> update { state ->
                    state.copy(report = state.report?.copy(sending = false, failed = true))
                }
            }
        }
    }

    private fun confirmBlock() {
        val prompt = state.value.block ?: return
        if (prompt.blocking) return
        update { it.copy(block = prompt.copy(blocking = true, failed = false)) }
        viewModelScope.launch {
            when (blocks.block(prompt.userId, GroupId(prompt.groupId))) {
                is SaqzResult.Success -> update { it.copy(block = null, feedback = ModerationFeedback.Blocked) }
                is SaqzResult.Failure -> update { state ->
                    state.copy(block = state.block?.copy(blocking = false, failed = true))
                }
            }
        }
    }

    private fun unblock(userId: String) {
        if (state.value.busy) return
        update { it.copy(unblocking = true, feedback = null) }
        viewModelScope.launch {
            val feedback = when (blocks.unblock(userId)) {
                is SaqzResult.Success -> ModerationFeedback.Unblocked
                is SaqzResult.Failure -> ModerationFeedback.UnblockFailed
            }
            update { it.copy(unblocking = false, feedback = feedback) }
        }
    }
}
