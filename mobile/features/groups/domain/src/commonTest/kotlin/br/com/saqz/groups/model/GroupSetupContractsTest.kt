package br.com.saqz.groups.model

import br.com.saqz.groups.port.GroupDraftFailure
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GroupSetupContractsTest {
    @Test
    fun `timezone value object preserves the exact identifier`() {
        assertEquals(GroupTimeZone("America/Sao_Paulo"), GroupTimeZone("America/Sao_Paulo"))
    }

    @Test
    fun `draft store port distinguishes missing data from typed failure`() {
        val store = RecordingDraftStore()
        var missing: GroupDraftReadResult? = null
        var failed: GroupDraftReadResult? = null

        store.read(GroupDraftKey(GroupDraftResource.CREATE_GROUP, null)) { missing = it }
        store.failure = GroupDraftFailure.CORRUPT
        store.read(GroupDraftKey(GroupDraftResource.UPDATE_GROUP, "group-1")) { failed = it }

        assertNull(assertIs<GroupDraftReadResult.Success>(missing).draft)
        assertEquals(GroupDraftFailure.CORRUPT, assertIs<GroupDraftReadResult.Failure>(failed).reason)
    }

    private class RecordingDraftStore : GroupDraftStorePort {
        var failure: GroupDraftFailure? = null
        override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) {
            val reason = failure
            done(if (reason == null) GroupDraftReadResult.Success(null) else GroupDraftReadResult.Failure(reason))
        }
        override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)
        override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)
    }
}
