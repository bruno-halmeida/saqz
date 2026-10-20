package br.com.saqz.composeapp.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardLinkTriggerTest {
    @Test fun readsOnTheEntryScreenWithoutAnyPendingLink() {
        assertEquals(true, shouldReadClipboardLink(signedOut = true, hasPendingInvite = false, hasPendingAttendance = false))
    }

    @Test fun signedInSessionNeverReads() {
        assertEquals(false, shouldReadClipboardLink(signedOut = false, hasPendingInvite = false, hasPendingAttendance = false))
    }

    @Test fun aLinkAlreadyPendingSkipsTheClipboard() {
        assertEquals(false, shouldReadClipboardLink(signedOut = true, hasPendingInvite = true, hasPendingAttendance = false))
        assertEquals(false, shouldReadClipboardLink(signedOut = true, hasPendingInvite = false, hasPendingAttendance = true))
    }
}
