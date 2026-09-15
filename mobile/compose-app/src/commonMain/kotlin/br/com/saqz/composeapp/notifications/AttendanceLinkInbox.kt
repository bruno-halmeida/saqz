package br.com.saqz.composeapp.notifications

import br.com.saqz.groups.port.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** Persists a pending game link across login; it never performs authenticated operations. */
internal class AttendanceLinkInbox(
    private val links: NativeGroupLinkPort,
    private val storage: LocalGroupStatePort,
    private val scope: CoroutineScope,
) {
    private val mutablePending = MutableStateFlow<String?>(null)
    val pending = mutablePending.asStateFlow()
    private val writes = Mutex()
    private var subscription: GroupCancelable? = null
    private var generation = 0

    fun start() {
        if (subscription != null) return
        val initial = generation
        subscription = links.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                if (event !is GroupLinkEvent.Attendance) return
                generation++
                val version = generation
                scope.launch {
                    writes.withLock {
                        if (version != generation) return@withLock
                        write(event.code)
                        if (version == generation) mutablePending.value = event.code
                    }
                }
            }
        })
        storage.readPendingAttendanceLink(object : GroupValueCallback {
            override fun complete(result: GroupValueResult) {
                if (initial == generation && result is GroupValueResult.Success) mutablePending.value = result.value
            }
        })
    }

    fun stop() {
        generation++
        subscription?.cancel()
        subscription = null
    }

    suspend fun consume(code: String) = writes.withLock {
        val version = generation
        if (mutablePending.value == code && write(null) && version == generation) mutablePending.value = null
    }

    private suspend fun write(code: String?): Boolean = suspendCancellableCoroutine { continuation ->
        storage.writePendingAttendanceLink(code, object : GroupResultCallback {
            override fun complete(result: GroupOperationResult) {
                if (continuation.isActive) continuation.resume(result is GroupOperationResult.Success)
            }
        })
    }
}
