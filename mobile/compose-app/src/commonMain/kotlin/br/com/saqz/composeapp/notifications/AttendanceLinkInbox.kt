package br.com.saqz.composeapp.notifications

import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.port.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** Link de presença pendente, com a intenção que veio no endereço. */
data class PendingAttendanceLink(
    val code: String,
    val intent: AttendanceIntent = AttendanceIntent.Confirm,
) {
    val decline: Boolean get() = intent == AttendanceIntent.Decline
}

/** Persists a pending game link across login; it never performs authenticated operations. */
internal class AttendanceLinkInbox(
    private val links: NativeGroupLinkPort,
    private val storage: LocalGroupStatePort,
    private val scope: CoroutineScope,
) {
    private val mutablePending = MutableStateFlow<PendingAttendanceLink?>(null)
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
                val link = PendingAttendanceLink(event.code, event.intent)
                scope.launch {
                    writes.withLock {
                        if (version != generation) return@withLock
                        write(encode(link))
                        if (version == generation) mutablePending.value = link
                    }
                }
            }
        })
        storage.readPendingAttendanceLink(object : GroupValueCallback {
            override fun complete(result: GroupValueResult) {
                if (initial == generation && result is GroupValueResult.Success) {
                    mutablePending.value = result.value?.let(::decode)
                }
            }
        })
    }

    fun stop() {
        generation++
        subscription?.cancel()
        subscription = null
    }

    suspend fun consume(link: PendingAttendanceLink) = writes.withLock {
        val version = generation
        if (mutablePending.value == link && write(null) && version == generation) mutablePending.value = null
    }

    private suspend fun write(code: String?): Boolean = suspendCancellableCoroutine { continuation ->
        storage.writePendingAttendanceLink(code, object : GroupResultCallback {
            override fun complete(result: GroupOperationResult) {
                if (continuation.isActive) continuation.resume(result is GroupOperationResult.Success)
            }
        })
    }

    private fun encode(link: PendingAttendanceLink): String =
        if (link.intent == AttendanceIntent.Decline) "$DECLINE_PREFIX${link.code}" else link.code

    private fun decode(value: String): PendingAttendanceLink =
        if (value.startsWith(DECLINE_PREFIX)) {
            PendingAttendanceLink(value.removePrefix(DECLINE_PREFIX), AttendanceIntent.Decline)
        } else {
            PendingAttendanceLink(value, AttendanceIntent.Confirm)
        }

    private companion object {
        /** O código é base64url; o prefixo nunca colide com um valor real. */
        const val DECLINE_PREFIX = "decline:"
    }
}
