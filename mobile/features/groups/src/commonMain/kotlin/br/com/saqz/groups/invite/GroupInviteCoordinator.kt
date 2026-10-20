package br.com.saqz.groups.invite

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.groups.domain.membership.InvitePreview
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface GroupInviteEffect {
    data class OpenInviteLanding(val code: String) : GroupInviteEffect

    data class NavigateToGroup(
        val groupId: String,
        val status: InviteRedeemStatus,
        val inviteCode: String,
    ) : GroupInviteEffect

    data class RedeemFailed(
        val code: String,
        val error: InviteError,
        val willRetry: Boolean,
    ) : GroupInviteEffect

    data class PendingInviteStorageFailed(val code: String? = null) : GroupInviteEffect
}

/**
 * Coordena o convite entre o link nativo, o storage seguro e a sessão.
 *
 * A geração não representa uma tentativa de rede: representa o link que é dono do fluxo. Ela
 * sobe antes de cada evento e de cada troca de sessão, então uma resposta velha não pode limpar
 * nem navegar sobre o convite que chegou depois.
 */
class GroupInviteCoordinator(
    private val linkPort: NativeGroupLinkPort,
    private val localState: LocalGroupStatePort,
    private val inviteGateway: InviteGateway,
    private val scope: CoroutineScope,
    private val storageWrites: Mutex = Mutex(),
    private val effectSink: (GroupInviteEffect) -> Unit = {},
) {
    private val effectChannel = Channel<QueuedEffect>(Channel.BUFFERED)
    private var generation = 0L
    private val authenticated = MutableStateFlow(false)
    private var linkCancelable: br.com.saqz.groups.port.GroupCancelable? = null
    private var started = false

    /**
     * Sair da conta não conseguiu apagar o convite guardado. Enquanto a marca estiver de pé o
     * próximo login não resgata nada (o convite é da conta que saiu) e tenta apagar de novo; um
     * link novo gravado com sucesso desliga a marca, porque esse sim é de quem entrar.
     */
    private var discardPending = false

    val effects: Flow<GroupInviteEffect> = effectChannel.receiveAsFlow()
        .filter { it.generation == generation }
        .map { it.effect }
    val isAuthenticated = authenticated.asStateFlow()

    private val redeemingState = MutableStateFlow(false)

    /**
     * Verdadeiro do momento em que o convite pendente começa a ser tratado até a tela dele (cadastro
     * de atleta, pedido enviado ou erro) ser aberta. Enquanto isso a Início não deve aparecer nem
     * receber toque. Quem trata o efeito chama [redeemHandled].
     */
    val redeeming = redeemingState.asStateFlow()

    /** A navegação do efeito já aconteceu: a Início pode voltar a ser a tela de baixo. */
    fun redeemHandled() {
        redeemingState.value = false
    }

    fun start() {
        if (started) return
        started = true
        linkCancelable = linkPort.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                if (event is GroupLinkEvent.Invite) acceptInvite(event.code)
            }
        })
    }

    fun stop() {
        if (!started) return
        started = false
        generation++
        redeemingState.value = false
        linkCancelable?.cancel()
        linkCancelable = null
    }

    fun onSignedOut() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authenticated.value = false
            redeemingState.value = false
            generation++
        }
    }

    /**
     * Sair da conta apaga o convite guardado, com garantia: a geração sobe (um `persistPending` que
     * ainda espera a trava desiste) e a escrita roda dentro de [storageWrites], então uma gravação
     * em voo termina antes e é apagada depois, nunca o contrário. Se apagar falhar, [discardPending]
     * impede o próximo login de resgatar o convite da conta anterior.
     */
    fun onLoggedOut() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authenticated.value = false
            redeemingState.value = false
            nextGeneration()
            discardPending = true
            clearDiscarded()
        }
    }

    private suspend fun clearDiscarded() {
        storageWrites.withLock {
            if (writePending(null)) discardPending = false
        }
    }

    /** Chamado pelo fecho depois que login ou registro entrega uma sessão. */
    fun onAuthenticated() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (authenticated.value) return@launch
            authenticated.value = true
            val token = nextGeneration()
            redeemingState.value = true
            if (discardPending) {
                clearDiscarded()
            } else {
                redeemPending(token)
            }
            settleRedeeming(token)
        }
    }

    /** Recupera o convite persistido antes de uma troca de sessão consumir o pending. */
    suspend fun readPendingInviteCode(): String? = when (val pending = readPending()) {
        PendingRead.Failed -> null
        is PendingRead.Value -> pending.code
    }

    /**
     * Entrada pública para o adapter ou para o fecho quando o port já entregou um evento.
     * Persistir termina antes de qualquer efeito de navegação ou chamada autenticada.
     */
    fun acceptInvite(code: String) {
        if (code.isBlank()) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val token = nextGeneration()
            if (!persistPending(token, code)) return@launch
            if (!isCurrent(token)) return@launch
            if (authenticated.value) {
                redeemingState.value = true
                redeem(token, InviteCode(code))
                settleRedeeming(token)
            } else {
                emitIfCurrent(token, GroupInviteEffect.OpenInviteLanding(code))
            }
        }
    }

    /**
     * A landing pode concluir um redeem que começou fora do coordinator. O coordinator continua
     * dono do pending persistido: só depois da limpeza confirmada a navegação pode sair da landing.
     * Se o código já não estiver persistido, a operação é idempotente e também conclui.
     */
    fun clearPendingInvite(code: String, onCleared: () -> Unit = {}) {
        if (code.isBlank()) {
            onCleared()
            return
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val token = generation
            when (val pending = readPending()) {
                PendingRead.Failed -> emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed(code))
                is PendingRead.Value -> {
                    if (pending.code != null && pending.code != code) {
                        onCleared()
                    } else if (clearPending(token)) {
                        onCleared()
                    } else {
                        emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed(code))
                    }
                }
            }
        }
    }

    /** O modo-convite do registro usa este preview enquanto a sessão ainda é anônima. */
    suspend fun previewPending(): SaqzResult<InvitePreview, InviteError>? {
        val token = generation
        val pending = readPending()
        val code = (pending as? PendingRead.Value)?.code ?: return null
        if (!isCurrent(token)) return null
        val result = inviteGateway.preview(InviteCode(code))
        if (isCurrent(token) && result is SaqzResult.Failure && result.error.isTerminal()) {
            if (!clearPending(token)) {
                emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed(code))
            }
        }
        return result.takeIf { isCurrent(token) }
    }

    private suspend fun redeemPending(token: Long) {
        val pending = readPending()
        val code = when (pending) {
            PendingRead.Failed -> {
                emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed())
                return
            }
            is PendingRead.Value -> pending.code
        } ?: return
        if (!isCurrent(token)) return
        redeem(token, InviteCode(code))
    }

    private suspend fun redeem(token: Long, code: InviteCode) {
        when (val preview = inviteGateway.preview(code)) {
            is SaqzResult.Failure -> finishFailure(token, code, preview.error)
            // Resposta velha: saiu da conta (ou chegou outro link) enquanto o preview andava.
            is SaqzResult.Success -> if (isCurrent(token)) redeemAfterPreview(token, code)
        }
    }

    private suspend fun redeemAfterPreview(token: Long, code: InviteCode) {
        when (val result = inviteGateway.redeem(code)) {
            is SaqzResult.Failure -> finishFailure(token, code, result.error)
            is SaqzResult.Success -> {
                if (!clearPending(token)) {
                    emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed(code.value))
                    return
                }
                emitIfCurrent(
                    token,
                    GroupInviteEffect.NavigateToGroup(
                        groupId = result.value.groupId.value,
                        status = result.value.status,
                        inviteCode = code.value,
                    ),
                )
            }
        }
    }

    private suspend fun finishFailure(token: Long, code: InviteCode, error: InviteError) {
        if (!isCurrent(token)) return
        val terminal = error.isTerminal()
        if (terminal && !clearPending(token)) {
            emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed(code.value))
            return
        }
        emitIfCurrent(
            token,
            GroupInviteEffect.RedeemFailed(code.value, error, willRetry = !terminal),
        )
    }

    private suspend fun persistPending(token: Long, code: String): Boolean = storageWrites.withLock {
        if (!isCurrent(token)) return@withLock false
        val result = writePending(code)
        if (!result || !isCurrent(token)) {
            if (!result) emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed(code))
            return@withLock false
        }
        discardPending = false
        true
    }

    private suspend fun clearPending(token: Long): Boolean = storageWrites.withLock {
        if (!isCurrent(token)) return@withLock false
        val result = writePending(null)
        result && isCurrent(token)
    }

    private suspend fun readPending(): PendingRead = suspendCancellableCoroutine { continuation ->
        localState.readPendingInvite(object : GroupValueCallback {
            override fun complete(result: GroupValueResult) {
                if (!continuation.isActive) return
                continuation.resume(
                    when (result) {
                        is GroupValueResult.Success -> PendingRead.Value(result.value)
                        is GroupValueResult.Failure -> PendingRead.Failed
                    },
                )
            }
        })
    }

    private suspend fun writePending(value: String?): Boolean = suspendCancellableCoroutine { continuation ->
        localState.writePendingInvite(value, object : GroupResultCallback {
            override fun complete(result: GroupOperationResult) {
                if (!continuation.isActive) return
                continuation.resume(result is GroupOperationResult.Success)
            }
        })
    }

    private var lastEmittedToken = -1L

    /** Terminou sem efeito para a tela (sem convite guardado, por exemplo): nada a esperar. */
    private fun settleRedeeming(token: Long) {
        if (isCurrent(token) && lastEmittedToken != token) redeemingState.value = false
    }

    private fun emitIfCurrent(token: Long, effect: GroupInviteEffect) {
        if (isCurrent(token)) {
            lastEmittedToken = token
            effectChannel.trySend(QueuedEffect(token, effect))
            effectSink(effect)
        }
    }

    private fun isCurrent(token: Long): Boolean = generation == token

    private fun nextGeneration(): Long {
        generation++
        return generation
    }

    private sealed interface PendingRead {
        data object Failed : PendingRead
        data class Value(val code: String?) : PendingRead
    }

    private data class QueuedEffect(
        val generation: Long,
        val effect: GroupInviteEffect,
    )
}

private fun InviteError.isTerminal(): Boolean = when (this) {
    is InviteError.InvalidOrExpired,
    is InviteError.Expired,
    InviteError.GroupDeleted,
    InviteError.PlanLimit,
    InviteError.GroupNotReceiving,
    -> true
    is InviteError.RateLimited,
    is InviteError.DataFailure,
    -> false
}
