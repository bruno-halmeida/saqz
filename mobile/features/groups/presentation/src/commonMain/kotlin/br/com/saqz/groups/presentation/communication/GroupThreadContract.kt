package br.com.saqz.groups.presentation.communication

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class GroupThreadState(
    val loading: Boolean = true,
    val error: GroupUiError? = null,
    val messages: List<ThreadMessageUi> = emptyList(),
    val nextCursor: Long? = null,
    val paging: Boolean = false,
    val canPost: Boolean = false,
    val draft: String = "",
    val sending: Boolean = false,
    val sendFailed: Boolean = false,
    /** O filtro de texto do servidor recusou o rascunho: a frase específica troca a genérica. */
    val sendRejected: Boolean = false,
    val pageFailed: Boolean = false,
    /** != null abre o sheet de opções do aviso (denunciar, bloquear quem escreveu). */
    val actionsFor: ThreadMessageUi? = null,
)

/** [own] tira o menu de denúncia das mensagens de quem olha. */
@Immutable
data class ThreadMessageUi(
    val id: String,
    val author: String,
    val body: String,
    val time: String,
    val authorId: String = "",
    val own: Boolean = false,
)

sealed interface GroupThreadIntent {
    data object Refresh : GroupThreadIntent
    data object More : GroupThreadIntent
    data object Send : GroupThreadIntent
    data class Draft(val text: String) : GroupThreadIntent
    data class OpenMessageActions(val messageId: String) : GroupThreadIntent
    data object DismissMessageActions : GroupThreadIntent

    /** Agem sobre `state.actionsFor`; sem aviso escolhido, não fazem nada. */
    data object ReportMessage : GroupThreadIntent
    data object BlockAuthor : GroupThreadIntent
}

/** Os sheets de denúncia e bloqueio são do `ModerationViewModel`; o Root repassa. */
sealed interface GroupThreadEffect {
    data class ReportMessage(val groupId: String, val messageId: String, val author: String) : GroupThreadEffect
    data class BlockAuthor(val groupId: String, val authorId: String, val author: String) : GroupThreadEffect
}
