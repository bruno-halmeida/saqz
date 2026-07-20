package br.com.saqz.groups.domain

import br.com.saqz.groups.port.DefaultGroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupDraftFailure
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupSetupContractsTest {
    @Test
    fun `system timezone provider returns a validated timezone value`() {
        var result: GroupSystemTimeZoneResult? = null

        DefaultGroupSystemTimeZonePort().detect { result = it }

        val available = assertIs<GroupSystemTimeZoneResult.Available>(result)
        assertIs<GroupTimeZone.ParseResult.Valid>(GroupTimeZone.parse(available.value.id))
    }

    @Test
    fun `invalid timezone becomes a typed failure instead of raw text`() {
        val result = GroupTimeZone.parse("Mars/Olympus")

        assertEquals(GroupTimeZone.ParseResult.Invalid, result)
    }

    @Test
    fun `versioned draft round trips form resource version etag and command key`() {
        val draft = draft()

        val decoded = Json.decodeFromString<GroupSetupDraft>(Json.encodeToString(draft))

        assertEquals(GroupSetupDraft.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(GroupDraftResource.UPDATE_GROUP, decoded.resource)
        assertEquals("group-1", decoded.groupId)
        assertEquals(7, decoded.groupVersion)
        assertEquals("\"7\"", decoded.etag)
        assertEquals("command-1", decoded.commandKey)
        assertEquals("Training Club", decoded.form.name)
    }

    @Test
    fun `draft serialization structurally excludes credentials invites photos and raw errors`() {
        val encoded = Json.encodeToString(draft())

        listOf("bearer", "token", "invite", "photo", "credential", "password", "rawError").forEach { forbidden ->
            assertFalse(encoded.contains(forbidden, ignoreCase = true), forbidden)
        }
        assertFalse(encoded.contains("timeZone", ignoreCase = true))
        assertTrue(encoded.contains("commandKey"))
        assertTrue(encoded.contains("groupVersion"))
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

    private fun draft() = GroupSetupDraft(
        resource = GroupDraftResource.UPDATE_GROUP,
        groupId = "group-1",
        groupVersion = 7,
        etag = "\"7\"",
        commandKey = "command-1",
        form = GroupSetupForm(
            name = "Training Club",
            modality = GroupModality.COURT_VOLLEYBALL,
            composition = GroupComposition.MIXED,
        ),
    )

    private class RecordingDraftStore : GroupDraftStorePort {
        var failure: GroupDraftFailure? = null
        override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) {
            val reason = failure
            done(if (reason == null) GroupDraftReadResult.Success(null) else GroupDraftReadResult.Failure(reason))
        }
        override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)
        override fun clear(key: GroupDraftKey, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)
    }
}
