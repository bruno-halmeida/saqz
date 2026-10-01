package br.com.saqz.groups.presentation.moderation

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.moderation.ReportReason
import br.com.saqz.groups.domain.moderation.ReportTargetType

/**
 * O que está sendo denunciado. [name] é quem escreveu o aviso, a pessoa ou o grupo — é o que
 * o sheet mostra abaixo de "Denunciar".
 */
@Immutable
data class ReportTargetUi(
    val groupId: String,
    val type: ReportTargetType,
    val targetId: String,
    val name: String,
)

@Immutable
data class ReportDraftUi(
    val target: ReportTargetUi,
    val reason: ReportReason? = null,
    val details: String = "",
    val sending: Boolean = false,
    val failed: Boolean = false,
) {
    val canSend: Boolean get() = reason != null && !sending
}

@Immutable
data class BlockPromptUi(
    val groupId: String,
    val userId: String,
    val name: String,
    val blocking: Boolean = false,
    val failed: Boolean = false,
)

/** O retorno curto (toast) de uma ação concluída. */
enum class ModerationFeedback { ReportSent, Blocked, Unblocked, UnblockFailed }

/** Os dois sheets e o toast que toda tela com conteúdo de terceiros sobrepõe. */
@Immutable
data class ModerationState(
    val report: ReportDraftUi? = null,
    val block: BlockPromptUi? = null,
    val unblocking: Boolean = false,
    val feedback: ModerationFeedback? = null,
) {
    val busy: Boolean get() = report?.sending == true || block?.blocking == true || unblocking
}

sealed interface ModerationIntent {
    data class StartReport(val target: ReportTargetUi) : ModerationIntent

    data class SelectReason(val reason: ReportReason) : ModerationIntent

    data class UpdateDetails(val text: String) : ModerationIntent

    data object SubmitReport : ModerationIntent

    data object DismissReport : ModerationIntent

    data class StartBlock(val groupId: String, val userId: String, val name: String) : ModerationIntent

    data object ConfirmBlock : ModerationIntent

    data object DismissBlock : ModerationIntent

    /** Desbloquear não pede confirmação: é desfazer, não punir. */
    data class Unblock(val userId: String) : ModerationIntent

    data object DismissFeedback : ModerationIntent
}
