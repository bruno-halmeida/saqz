package br.com.saqz.groups.presentation.photo

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.CachedGroupPhoto
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoByteSource
import br.com.saqz.groups.domain.photo.GroupPhotoCachePort
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoError as DomainPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupPhotoCoordinatorTest {
    @Test fun `cache miss reads private photo and caches the 200 response`() = runTest {
        val fixture = fixture(this, bind = false)
        fixture.gateway.readResult = SaqzResult.Success(
            GroupPhotoReadResult.Available(
                byteArrayOf(4, 5, 6),
                "image/png",
                GroupPhotoVersionToken(PHOTO_ETAG),
            ),
        )

        fixture.machine.onIntent(GroupPhotoIntent.Load(GROUP_ID, GROUP_ETAG))
        runCurrent()

        assertEquals(listOf<Pair<String, String?>>(GROUP_ID to null), fixture.gateway.reads)
        assertEquals(listOf<Byte>(4, 5, 6), fixture.cache.writes.single().bytes.toList())
        assertEquals(LOADED_EXISTING, fixture.machine.state.value.existing)
        assertEquals(GROUP_ETAG, fixture.machine.state.value.groupEtag)
        assertEquals(GroupPhotoStage.IDLE, fixture.machine.state.value.stage)
    }

    @Test fun `cache hit is hidden until private endpoint confirms 304`() = runTest {
        val fixture = fixture(this, bind = false)
        fixture.cache.entries[GroupId(GROUP_ID)] = CACHED
        fixture.gateway.readResult = SaqzResult.Success(GroupPhotoReadResult.NotModified)

        fixture.machine.onIntent(GroupPhotoIntent.Load(GROUP_ID, GROUP_ETAG))
        assertNull(fixture.machine.state.value.existing)
        assertEquals(GroupPhotoStage.LOADING, fixture.machine.state.value.stage)
        runCurrent()

        assertEquals(listOf<Pair<String, String?>>(GROUP_ID to PHOTO_ETAG), fixture.gateway.reads)
        assertEquals(LOADED_EXISTING, fixture.machine.state.value.existing)
    }

    @Test fun `missing private photo shows fallback without a visible read error`() = runTest {
        val fixture = fixture(this, bind = false)
        fixture.gateway.readResult = SaqzResult.Failure(DomainPhotoError.NotFound)

        fixture.machine.onIntent(GroupPhotoIntent.Load(GROUP_ID, GROUP_ETAG))
        runCurrent()

        assertNull(fixture.machine.state.value.existing)
        assertNull(fixture.machine.state.value.error)
        assertEquals(listOf(GROUP_ID), fixture.cache.evicted)
        assertEquals(GroupPhotoStage.IDLE, fixture.machine.state.value.stage)
    }

    @Test fun `transient read failure never exposes cached photo`() = runTest {
        val fixture = fixture(this, bind = false)
        fixture.cache.entries[GroupId(GROUP_ID)] = CACHED
        fixture.gateway.readResult = SaqzResult.Failure(DomainPhotoError.DataFailure(DataError.Connectivity))

        fixture.machine.onIntent(GroupPhotoIntent.Load(GROUP_ID, GROUP_ETAG))
        runCurrent()

        assertNull(fixture.machine.state.value.existing)
        assertEquals(GroupPhotoError.READ_FAILED, fixture.machine.state.value.error)
    }

    @Test fun `invalid cache entry falls back to unconditional authenticated read`() = runTest {
        val fixture = fixture(this, bind = false)
        fixture.cache.invalidGroups += GROUP_ID
        fixture.gateway.readResult = SaqzResult.Success(
            GroupPhotoReadResult.Available(
                byteArrayOf(7),
                "image/jpeg",
                GroupPhotoVersionToken(PHOTO_ETAG),
            ),
        )

        fixture.machine.onIntent(GroupPhotoIntent.Load(GROUP_ID, GROUP_ETAG))
        runCurrent()

        assertEquals(listOf<Pair<String, String?>>(GROUP_ID to null), fixture.gateway.reads)
        assertEquals(LOADED_EXISTING, fixture.machine.state.value.existing)
    }

    @Test fun `late response from previous group cannot replace current group photo`() = runTest {
        val fixture = fixture(this, bind = false)
        val first = CompletableDeferred<SaqzResult<GroupPhotoReadResult, DomainPhotoError>>()
        val second = CompletableDeferred<SaqzResult<GroupPhotoReadResult, DomainPhotoError>>()
        fixture.gateway.readBlock = { groupId, _ ->
            withContext(NonCancellable) {
                if (groupId.value == GROUP_ID) first.await() else second.await()
            }
        }

        fixture.machine.onIntent(GroupPhotoIntent.Load(GROUP_ID, GROUP_ETAG))
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Load(OTHER_GROUP_ID, OTHER_GROUP_ETAG))
        runCurrent()
        first.complete(
            SaqzResult.Success(
                GroupPhotoReadResult.Available(
                    byteArrayOf(1),
                    "image/png",
                    GroupPhotoVersionToken(PHOTO_ETAG),
                ),
            ),
        )
        runCurrent()

        assertEquals(OTHER_GROUP_ID, fixture.machine.state.value.groupId)
        assertNull(fixture.machine.state.value.existing)
        assertEquals(GroupPhotoStage.LOADING, fixture.machine.state.value.stage)

        second.complete(SaqzResult.Failure(DomainPhotoError.NotFound))
        runCurrent()
        assertEquals(OTHER_GROUP_ID, fixture.machine.state.value.groupId)
        assertNull(fixture.machine.state.value.existing)
    }

    @Test fun `logout ignores a late private photo response and clears all cache`() = runTest {
        val fixture = fixture(this, bind = false)
        val response = CompletableDeferred<SaqzResult<GroupPhotoReadResult, DomainPhotoError>>()
        fixture.gateway.readBlock = { _, _ -> withContext(NonCancellable) { response.await() } }
        fixture.machine.onIntent(GroupPhotoIntent.Load(GROUP_ID, GROUP_ETAG))
        runCurrent()

        fixture.machine.onIntent(GroupPhotoIntent.Logout)
        response.complete(
            SaqzResult.Success(
                GroupPhotoReadResult.Available(
                    byteArrayOf(1),
                    "image/png",
                    GroupPhotoVersionToken(PHOTO_ETAG),
                ),
            ),
        )
        runCurrent()

        assertEquals(GroupPhotoState(), fixture.machine.state.value)
        assertTrue(fixture.cache.clearedAll)
        assertTrue(fixture.cache.writes.isEmpty())
    }
    @Test fun `camera selection exposes bounded preview and centered square crop`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupPhotoIntent.ChooseCamera)
        runCurrent()

        assertSame(fixture.selection, fixture.machine.state.value.selection)
        assertEquals(GroupPhotoStage.CROPPING, fixture.machine.state.value.stage)
        assertEquals(GroupPhotoCrop(), fixture.machine.state.value.crop)
    }

    @Test fun `library selection uses provider neutral selection port`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupPhotoIntent.ChooseLibrary)
        runCurrent()

        assertEquals(1, fixture.selections.libraryCalls)
        assertEquals(0, fixture.selections.cameraCalls)
    }

    @Test fun `cancelled picker preserves existing photo`() = runTest {
        val fixture = fixture(this).existing()
        fixture.selections.cameraResult = GroupPhotoSelectionResult.Cancelled
        fixture.machine.onIntent(GroupPhotoIntent.ChooseCamera)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertNull(fixture.machine.state.value.error)
    }

    @Test fun `picker failure preserves existing photo`() = runTest {
        val fixture = fixture(this).existing()
        fixture.selections.cameraResult = GroupPhotoSelectionResult.Failed
        fixture.machine.onIntent(GroupPhotoIntent.ChooseCamera)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertEquals(GroupPhotoError.SELECTION_FAILED, fixture.machine.state.value.error)
    }

    @Test fun `replacement cleans superseded source handle`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupPhotoIntent.ChooseCamera)
        runCurrent()
        val replacement = selection("second")
        fixture.selections.libraryResult = GroupPhotoSelectionResult.Selected(replacement)
        fixture.machine.onIntent(GroupPhotoIntent.ChooseLibrary)
        runCurrent()

        assertEquals(listOf(SOURCE), fixture.selections.cleaned)
        assertSame(replacement, fixture.machine.state.value.selection)
    }

    @Test fun `crop transform is shared immutable state`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupPhotoIntent.ChangeCrop(GroupPhotoCrop(0.1f, 0.2f, 2f)))

        assertEquals(GroupPhotoCrop(0.1f, 0.2f, 2f), fixture.machine.state.value.crop)
    }

    @Test fun `upload uses bound group id and current etag`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        val call = fixture.gateway.uploads.single()
        assertEquals(GroupId(GROUP_ID), call.groupId)
        assertEquals(GroupPhotoVersionToken(GROUP_ETAG), call.groupVersion)
    }

    @Test fun `encoding failure keeps existing and selection retryable`() = runTest {
        val fixture = fixture(this).existing().selected()
        runCurrent()
        fixture.encoder.result = GroupPhotoEncodingResult.Failed
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertSame(fixture.selection, fixture.machine.state.value.selection)
        assertTrue(fixture.machine.state.value.retryUpload)
        assertEquals(GroupPhotoError.ENCODING_FAILED, fixture.machine.state.value.error)
    }

    @Test fun `upload failure keeps existing and source without cleanup`() = runTest {
        val fixture = fixture(this).existing().selected()
        runCurrent()
        fixture.gateway.uploadResult = SaqzResult.Failure(
            DomainPhotoError.DataFailure(DataError.Connectivity),
        )
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertTrue(fixture.selections.cleaned.isEmpty())
        assertTrue(fixture.machine.state.value.retryUpload)
    }

    @Test fun `stale group version keeps selection but requires authoritative reload`() = runTest {
        val fixture = fixture(this).existing().selected()
        runCurrent()
        fixture.gateway.uploadResult = SaqzResult.Failure(DomainPhotoError.StaleVersion)
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertSame(fixture.selection, fixture.machine.state.value.selection)
        assertFalse(fixture.machine.state.value.retryUpload)
        assertEquals(GroupPhotoError.STALE_VERSION, fixture.machine.state.value.error)
    }

    @Test fun `retry uploads same confirmed group without a create dependency`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.gateway.uploadResult = SaqzResult.Failure(DomainPhotoError.DataFailure(DataError.Timeout))
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()
        fixture.gateway.uploadResult = SaqzResult.Success(
            GroupPhotoReceipt(GroupPhotoVersionToken(PHOTO_ETAG)),
        )
        fixture.machine.onIntent(GroupPhotoIntent.RetryUpload)
        runCurrent()

        assertEquals(listOf(GroupId(GROUP_ID), GroupId(GROUP_ID)), fixture.gateway.uploads.map { it.groupId })
    }

    @Test fun `upload success publishes preview then clears source and image cache`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(ExistingGroupPhoto(PREVIEW), fixture.machine.state.value.existing)
        assertNull(fixture.machine.state.value.selection)
        assertEquals(UPLOAD_GROUP_ETAG, fixture.machine.state.value.groupEtag)
        assertEquals(listOf(SOURCE), fixture.selections.cleaned)
        assertEquals(listOf(GROUP_ID), fixture.cache.evicted)
    }

    @Test fun `replace after upload uses returned group etag without refetch`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.ChooseLibrary)
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(
            listOf(GroupPhotoVersionToken(GROUP_ETAG), GroupPhotoVersionToken(UPLOAD_GROUP_ETAG)),
            fixture.gateway.uploads.map { it.groupVersion },
        )
    }

    @Test fun `remove failure preserves existing photo`() = runTest {
        val fixture = fixture(this).existing()
        fixture.gateway.removeResult = SaqzResult.Failure(
            DomainPhotoError.DataFailure(DataError.Connectivity),
        )
        fixture.machine.onIntent(GroupPhotoIntent.Remove)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertEquals(GroupPhotoError.REMOVE_FAILED, fixture.machine.state.value.error)
    }

    @Test fun `successful remove shows fallback and retains returned group version`() = runTest {
        val fixture = fixture(this).existing()
        fixture.machine.onIntent(GroupPhotoIntent.Remove)
        runCurrent()

        assertNull(fixture.machine.state.value.existing)
        assertEquals(REMOVE_ETAG, fixture.machine.state.value.groupEtag)
        assertEquals(listOf(GROUP_ID), fixture.cache.evicted)
    }

    @Test fun `new upload after removal uses returned group etag without refetch`() = runTest {
        val fixture = fixture(this).existing()
        fixture.machine.onIntent(GroupPhotoIntent.Remove)
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.ChooseCamera)
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(
            GroupPhotoVersionToken(REMOVE_ETAG),
            fixture.gateway.uploads.single().groupVersion,
        )
    }

    @Test fun `cancel asks encoder and selector to clean transient source`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Cancel)

        assertEquals(listOf(SOURCE), fixture.encoder.cancelled)
        assertEquals(listOf(SOURCE), fixture.selections.cleaned)
        assertNull(fixture.machine.state.value.selection)
    }

    @Test fun `membership loss cleans source and only affected group cache`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.MembershipLost)

        assertEquals(listOf(SOURCE), fixture.selections.cleaned)
        assertEquals(listOf(GROUP_ID), fixture.cache.evicted)
        assertFalse(fixture.cache.clearedAll)
        assertEquals(GroupPhotoState(), fixture.machine.state.value)
    }

    @Test fun `logout cleans source and all private image cache`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Logout)

        assertEquals(listOf(SOURCE), fixture.selections.cleaned)
        assertTrue(fixture.cache.clearedAll)
        assertEquals(GroupPhotoState(), fixture.machine.state.value)
    }

    @Test fun `upload without confirmed target never encodes or calls network`() = runTest {
        val fixture = fixture(this, bind = false).selected(bind = false)
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(GroupPhotoError.TARGET_UNAVAILABLE, fixture.machine.state.value.error)
        assertEquals(0, fixture.encoder.calls)
        assertTrue(fixture.gateway.uploads.isEmpty())
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope, bind: Boolean = true): Fixture {
        val selections = FakeSelections()
        val encoder = FakeEncoder()
        val gateway = FakeGateway()
        val cache = FakeCache()
        val machine = GroupPhotoCoordinator(gateway, selections, encoder, cache, scope)
        if (bind) machine.onIntent(GroupPhotoIntent.BindTarget(GROUP_ID, GROUP_ETAG))
        return Fixture(machine, selections, encoder, gateway, cache, selections.selection)
    }

    private fun selection(prefix: String) = GroupPhotoSelection(
        GroupPhotoSourceHandle("$prefix-source"), GroupPhotoPreviewHandle("$prefix-preview"), 200, 100,
    )

    private data class Fixture(
        val machine: GroupPhotoCoordinator,
        val selections: FakeSelections,
        val encoder: FakeEncoder,
        val gateway: FakeGateway,
        val cache: FakeCache,
        val selection: GroupPhotoSelection,
    ) {
        fun existing() = apply { machine.onIntent(GroupPhotoIntent.SetExisting(EXISTING)) }
        fun selected(bind: Boolean = true) = apply {
            if (bind && machine.state.value.groupId == null) machine.onIntent(GroupPhotoIntent.BindTarget(GROUP_ID, GROUP_ETAG))
            machine.onIntent(GroupPhotoIntent.ChooseCamera)
        }
    }

    private class FakeSelections : GroupPhotoSelectionPort {
        val selection = GroupPhotoSelection(SOURCE, PREVIEW, 200, 100)
        var cameraResult: GroupPhotoSelectionResult = GroupPhotoSelectionResult.Selected(selection)
        var libraryResult: GroupPhotoSelectionResult = GroupPhotoSelectionResult.Selected(selection)
        var cameraCalls = 0
        var libraryCalls = 0
        val cleaned = mutableListOf<GroupPhotoSourceHandle>()
        override suspend fun chooseCamera() = cameraResult.also { cameraCalls++ }
        override suspend fun chooseLibrary() = libraryResult.also { libraryCalls++ }
        override fun cleanup(source: String) { cleaned += GroupPhotoSourceHandle(source) }
    }

    private class FakeEncoder : GroupPhotoEncoderPort {
        var calls = 0
        var result: GroupPhotoEncodingResult = GroupPhotoEncodingResult.Encoded(
            EncodedGroupPhoto(GroupPhotoMediaType.JPEG, 3, GroupPhotoByteSource { byteArrayOf(1, 2, 3) }),
        )
        val cancelled = mutableListOf<GroupPhotoSourceHandle>()
        override suspend fun encode(source: String, crop: GroupPhotoCrop) = result.also { calls++ }
        override fun cancel(source: String) { cancelled += GroupPhotoSourceHandle(source) }
    }

    private class FakeGateway : GroupPhotoGateway {
        val uploads = mutableListOf<GroupPhotoUploadCommand>()
        val reads = mutableListOf<Pair<String, String?>>()
        var uploadResult: SaqzResult<GroupPhotoReceipt, DomainPhotoError> = SaqzResult.Success(
            GroupPhotoReceipt(GroupPhotoVersionToken(UPLOAD_GROUP_ETAG)),
        )
        var removeResult: SaqzResult<GroupPhotoReceipt, DomainPhotoError> = SaqzResult.Success(
            GroupPhotoReceipt(GroupPhotoVersionToken(REMOVE_ETAG)),
        )
        var readResult: SaqzResult<GroupPhotoReadResult, DomainPhotoError> = SaqzResult.Failure(
            DomainPhotoError.DataFailure(DataError.Connectivity),
        )
        var readBlock: (suspend (GroupId, GroupPhotoVersionToken?) ->
            SaqzResult<GroupPhotoReadResult, DomainPhotoError>)? = null

        override suspend fun upload(command: GroupPhotoUploadCommand) = uploadResult.also { uploads += command }

        override suspend fun read(
            groupId: GroupId,
            version: GroupPhotoVersionToken?,
        ): SaqzResult<GroupPhotoReadResult, DomainPhotoError> {
            reads += groupId.value to version?.value
            return readBlock?.invoke(groupId, version) ?: readResult
        }

        override suspend fun remove(
            groupId: GroupId,
            groupVersion: GroupPhotoVersionToken,
        ) = removeResult
    }

    private class FakeCache : GroupPhotoCachePort {
        data class Write(val groupId: String, val bytes: ByteArray, val photoEtag: String)
        val entries = mutableMapOf<GroupId, CachedGroupPhoto>()
        val invalidGroups = mutableSetOf<String>()
        val writes = mutableListOf<Write>()
        val evicted = mutableListOf<String>()
        var clearedAll = false
        override fun read(groupId: GroupId) = entries[groupId].takeUnless { groupId.value in invalidGroups }

        override fun write(
            groupId: GroupId,
            bytes: ByteArray,
            version: GroupPhotoVersionToken,
        ): CachedGroupPhoto {
            writes += Write(groupId.value, bytes, version.value)
            return CachedGroupPhoto(GroupPhotoPreviewHandle("existing-preview"), version).also {
                entries[groupId] = it
            }
        }

        override fun evict(groupId: GroupId) {
            evicted += groupId.value
        }

        override fun clearAll() { clearedAll = true }
    }

    private companion object {
        const val GROUP_ID = "group-1"
        const val OTHER_GROUP_ID = "group-2"
        const val GROUP_ETAG = "\"7\""
        const val OTHER_GROUP_ETAG = "\"11\""
        const val PHOTO_ETAG = "\"photo-2\""
        const val UPLOAD_GROUP_ETAG = "\"8\""
        const val REMOVE_ETAG = "\"photo-3\""
        val SOURCE = GroupPhotoSourceHandle("source")
        val PREVIEW = GroupPhotoPreviewHandle("preview")
        val EXISTING = ExistingGroupPhoto(GroupPhotoPreviewHandle("existing-preview"), "\"photo-1\"")
        val LOADED_EXISTING = ExistingGroupPhoto(GroupPhotoPreviewHandle("existing-preview"), PHOTO_ETAG)
        val CACHED = CachedGroupPhoto(
            GroupPhotoPreviewHandle("existing-preview"),
            GroupPhotoVersionToken(PHOTO_ETAG),
        )
    }
}
