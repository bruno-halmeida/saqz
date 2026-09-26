package br.com.saqz.composeapp.notifications

import br.com.saqz.groups.port.*
import kotlin.test.*

class NotificationOpenInboxTest {
    @Test fun keepsOnlyNotificationTapsWithTheirGameAndConsumesOnce() {
        val ports = Ports()
        val inbox = NotificationOpenInbox(ports)
        inbox.start()

        ports.listener?.onEvent(GroupLinkEvent.Attendance("code"))
        ports.listener?.onEvent(GroupLinkEvent.Invite("code"))
        assertNull(inbox.pending.value)

        ports.listener?.onEvent(GroupLinkEvent.NotificationOpen(groupId = "group", gameId = "game"))
        assertEquals(GroupLinkEvent.NotificationOpen(groupId = "group", gameId = "game"), inbox.pending.value)

        inbox.consume()
        assertNull(inbox.pending.value)
    }

    @Test fun startIsIdempotentAndStopCancels() {
        val ports = Ports()
        val inbox = NotificationOpenInbox(ports)
        inbox.start()
        val first = ports.listener
        inbox.start()
        assertSame(first, ports.listener)

        inbox.stop()
        assertNull(ports.listener)
    }

    private class Ports : NativeGroupLinkPort {
        var listener: GroupLinkEventListener? = null
        override fun start(listener: GroupLinkEventListener): GroupCancelable {
            this.listener = listener
            return object : GroupCancelable { override fun cancel() { this@Ports.listener = null } }
        }
    }
}
