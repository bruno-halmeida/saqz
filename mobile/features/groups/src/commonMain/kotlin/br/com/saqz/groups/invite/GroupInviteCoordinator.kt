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
    ) : GroupInviteEffect

    data class RedeemFailed(
        val error: InviteError,
        val willRetry: Boolean,
    ) : GroupInviteEffect

    data object PendingInviteStorageFailed : GroupInviteEffect
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
    private val effectChannel = Channel<GroupInviteEffect>(Channel.BUFFERED)
    private var generation = 0L
    private val authenticated = MutableStateFlow(false)
    private var linkCancelable: br.com.saqz.groups.port.GroupCancelable? = null
    private var started = false

    val effects: Flow<GroupInviteEffect> = effectChannel.receiveAsFlow()
    val isAuthenticated = authenticated.asStateFlow()

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
        linkCancelable?.cancel()
        linkCancelable = null
    }

    fun onSignedOut() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authenticated.value = false
            generation++
        }
    }

    /** Chamado pelo fecho depois que login ou registro entrega uma sessão. */
    fun onAuthenticated() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authenticated.value = true
            val token = nextGeneration()
            redeemPending(token)
        }
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
                redeem(token, InviteCode(code))
            } else {
                emitIfCurrent(token, GroupInviteEffect.OpenInviteLanding(code))
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
            clearPending(token)
        }
        return result.takeIf { isCurrent(token) }
    }

    private suspend fun redeemPending(token: Long) {
        val pending = readPending()
        val code = when (pending) {
            PendingRead.Failed -> {
                emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed)
                return
            }
            is PendingRead.Value -> pending.code
        } ?: return
        if (!isCurrent(token)) return
        redeem(token, InviteCode(code))
    }

    private suspend fun redeem(token: Long, code: InviteCode) {
        when (val preview = inviteGateway.preview(code)) {
            is SaqzResult.Failure -> finishFailure(token, preview.error)
            is SaqzResult.Success -> when (val result = inviteGateway.redeem(code)) {
                is SaqzResult.Failure -> finishFailure(token, result.error)
                is SaqzResult.Success -> {
                    if (!clearPending(token)) return
                    emitIfCurrent(
                        token,
                        GroupInviteEffect.NavigateToGroup(
                            groupId = result.value.groupId.value,
                            status = result.value.status,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun finishFailure(token: Long, error: InviteError) {
        if (!isCurrent(token)) return
        val terminal = error.isTerminal()
        if (terminal && !clearPending(token)) return
        emitIfCurrent(token, GroupInviteEffect.RedeemFailed(error, willRetry = !terminal))
    }

    private suspend fun persistPending(token: Long, code: String): Boolean = storageWrites.withLock {
        if (!isCurrent(token)) return@withLock false
        val result = writePending(code)
        if (!result || !isCurrent(token)) {
            if (!result) emitIfCurrent(token, GroupInviteEffect.PendingInviteStorageFailed)
            return@withLock false
        }
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

    private fun emitIfCurrent(token: Long, effect: GroupInviteEffect) {
        if (isCurrent(token)) {
            effectChannel.trySend(effect)
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
}

private fun InviteError.isTerminal(): Boolean = when (this) {
    InviteError.InvalidOrExpired,
    InviteError.GroupDeleted,
    InviteError.PlanLimit,
    -> true
    is InviteError.RateLimited,
    is InviteError.DataFailure,
    -> false
}
