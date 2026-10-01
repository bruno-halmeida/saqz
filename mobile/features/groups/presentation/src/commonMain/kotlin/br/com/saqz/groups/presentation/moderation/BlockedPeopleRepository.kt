package br.com.saqz.groups.presentation.moderation

import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.moderation.ModerationError
import br.com.saqz.groups.domain.moderation.ModerationGateway
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Quem o usuário bloqueou, compartilhado pelas telas que mostram gente do grupo (membros,
 * perfil de membro, avisos, notificações). [blocked] decide entre "Bloquear" e "Desbloquear";
 * [changes] avisa quem mostra conteúdo de terceiros para recarregar — o servidor já some com o
 * que o bloqueado escreveu, então recarregar basta para a tela ficar limpa na hora.
 *
 * ponytail: cache só em memória e sem dono de sessão. Toda tela que lê [blocked] chama
 * [refresh] ao carregar, o que troca o conjunto inteiro — inclusive depois de trocar de conta.
 * Se aparecer leitura sem refresh, o caminho é limpar o cache no logout.
 */
class BlockedPeopleRepository(private val gateway: ModerationGateway) {
    private val mutableBlocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = mutableBlocked.asStateFlow()

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Dispara depois de bloquear ou desbloquear por este app; não dispara no [refresh]. */
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    /** Falha cala: o conjunto anterior continua valendo e o servidor segue filtrando o conteúdo. */
    suspend fun refresh() {
        val result = gateway.listBlockedPeople()
        if (result is SaqzResult.Success) mutableBlocked.value = result.value.mapTo(mutableSetOf()) { it.userId }
    }

    suspend fun block(userId: String, groupId: GroupId): EmptyResult<ModerationError> =
        gateway.blockPerson(userId, groupId).onSuccess {
            mutableBlocked.update { it + userId }
            mutableChanges.tryEmit(Unit)
        }

    suspend fun unblock(userId: String): EmptyResult<ModerationError> =
        gateway.unblockPerson(userId).onSuccess {
            mutableBlocked.update { it - userId }
            mutableChanges.tryEmit(Unit)
        }
}
