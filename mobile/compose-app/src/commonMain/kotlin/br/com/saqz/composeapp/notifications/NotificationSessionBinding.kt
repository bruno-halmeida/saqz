package br.com.saqz.composeapp.notifications

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.communication.NotificationDevice
import br.com.saqz.groups.domain.communication.NotificationDeviceGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

internal class NotificationSessionBinding(
    private val session: StateFlow<String?>,
    private val native: NativeNotificationPort,
    private val gateway: NotificationDeviceGateway,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var needsClear = true
    private var previous: String? = null
    private var registered: Pair<String, NotificationDevice>? = null
    init {
        scope.launch {
            val subscription = native.observe { refresh() }
            try {
                session.collect { refresh() }
            } finally { subscription.cancel() }
        }
    }
    fun refresh() {
        scope.launch {
            mutex.withLock {
                val current = session.value
                if (previous != null && previous != current) { needsClear = true; native.dismissAll() }
                previous = current
                println("[SaqzPush] refresh session=${current != null} needsClear=$needsClear")
                if (needsClear) {
                    // Revoga inclusive após reinício; falhas mantêm o bloqueio até o próximo refresh.
                    if (!clearDevice()) {
                        println("[SaqzPush] clear FALHOU — registro bloqueado")
                        return@withLock
                    }
                    registered = null
                    needsClear = false
                }
                if (current == null) return@withLock
                val device = currentDevice()
                println("[SaqzPush] device=${device != null}")
                if (device == null) return@withLock
                if (session.value != current || registered == current to device) return@withLock
                val result = gateway.register(device)
                println("[SaqzPush] register=${result is SaqzResult.Success}")
                if (result is SaqzResult.Success && session.value == current) registered = current to device
            }
        }
    }
    private suspend fun currentDevice(): NotificationDevice? = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { continuation ->
            native.device { if (continuation.isActive) continuation.resume(it) }
        }
    }
    private suspend fun clearDevice(): Boolean = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { continuation ->
            native.clear { if (continuation.isActive) continuation.resume(it) }
        }
    } ?: false
}
