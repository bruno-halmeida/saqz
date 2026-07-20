package br.com.saqz.groups.presentation.photo

import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupPhotoReadResult
import br.com.saqz.groups.data.GroupPhotoReceipt
import br.com.saqz.groups.data.GroupPhotoUploadCommand
import br.com.saqz.groups.port.EncodedGroupPhoto
import br.com.saqz.groups.port.GroupPhotoByteSource
import br.com.saqz.groups.port.GroupPhotoCachePort
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import br.com.saqz.groups.port.GroupPhotoMediaType
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoSelection
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupPhotoSelectionResult
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        assertEquals(GROUP_ID, call.groupId)
        assertEquals(GROUP_ETAG, call.groupEtag)
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
        fixture.gateway.uploadResult = NetworkResult.Failure(NetworkError.Unavailable)
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertTrue(fixture.selections.cleaned.isEmpty())
        assertTrue(fixture.machine.state.value.retryUpload)
    }

    @Test fun `retry uploads same confirmed group without a create dependency`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.gateway.uploadResult = NetworkResult.Failure(NetworkError.Timeout)
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()
        fixture.gateway.uploadResult = NetworkResult.Success(GroupPhotoReceipt(PHOTO_ETAG))
        fixture.machine.onIntent(GroupPhotoIntent.RetryUpload)
        runCurrent()

        assertEquals(listOf(GROUP_ID, GROUP_ID), fixture.gateway.uploads.map { it.groupId })
    }

    @Test fun `upload success publishes preview then clears source and image cache`() = runTest {
        val fixture = fixture(this).selected()
        runCurrent()
        fixture.machine.onIntent(GroupPhotoIntent.Upload)
        runCurrent()

        assertEquals(ExistingGroupPhoto(PREVIEW, PHOTO_ETAG), fixture.machine.state.value.existing)
        assertNull(fixture.machine.state.value.selection)
        assertNull(fixture.machine.state.value.groupEtag)
        assertEquals(listOf(SOURCE), fixture.selections.cleaned)
        assertEquals(listOf(GROUP_ID), fixture.cache.evicted)
    }

    @Test fun `remove failure preserves existing photo`() = runTest {
        val fixture = fixture(this).existing()
        fixture.gateway.removeResult = NetworkResult.Failure(NetworkError.Unavailable)
        fixture.machine.onIntent(GroupPhotoIntent.Remove)
        runCurrent()

        assertEquals(EXISTING, fixture.machine.state.value.existing)
        assertEquals(GroupPhotoError.REMOVE_FAILED, fixture.machine.state.value.error)
    }

    @Test fun `successful remove shows fallback and invalidates current version`() = runTest {
        val fixture = fixture(this).existing()
        fixture.machine.onIntent(GroupPhotoIntent.Remove)
        runCurrent()

        assertNull(fixture.machine.state.value.existing)
        assertNull(fixture.machine.state.value.groupEtag)
        assertEquals(listOf(GROUP_ID), fixture.cache.evicted)
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
        override fun cleanup(source: GroupPhotoSourceHandle) { cleaned += source }
    }

    private class FakeEncoder : GroupPhotoEncoderPort {
        var calls = 0
        var result: GroupPhotoEncodingResult = GroupPhotoEncodingResult.Encoded(
            EncodedGroupPhoto(GroupPhotoMediaType.JPEG, 3, GroupPhotoByteSource { ByteReadChannel(byteArrayOf(1, 2, 3)) }),
        )
        val cancelled = mutableListOf<GroupPhotoSourceHandle>()
        override suspend fun encode(source: GroupPhotoSourceHandle, crop: GroupPhotoCrop) = result.also { calls++ }
        override fun cancel(source: GroupPhotoSourceHandle) { cancelled += source }
    }

    private class FakeGateway : GroupPhotoGateway {
        val uploads = mutableListOf<GroupPhotoUploadCommand>()
        var uploadResult: NetworkResult<GroupPhotoReceipt> = NetworkResult.Success(GroupPhotoReceipt(PHOTO_ETAG))
        var removeResult: NetworkResult<Unit> = NetworkResult.Success(Unit)
        override suspend fun upload(command: GroupPhotoUploadCommand) = uploadResult.also { uploads += command }
        override suspend fun read(groupId: String, etag: String?): NetworkResult<GroupPhotoReadResult> =
            NetworkResult.Failure(NetworkError.Unavailable)
        override suspend fun remove(groupId: String, groupEtag: String) = removeResult
    }

    private class FakeCache : GroupPhotoCachePort {
        val evicted = mutableListOf<String>()
        var clearedAll = false
        override fun evict(groupId: String) { evicted += groupId }
        override fun clearAll() { clearedAll = true }
    }

    private companion object {
        const val GROUP_ID = "group-1"
        const val GROUP_ETAG = "\"7\""
        const val PHOTO_ETAG = "\"photo-2\""
        val SOURCE = GroupPhotoSourceHandle("source")
        val PREVIEW = GroupPhotoPreviewHandle("preview")
        val EXISTING = ExistingGroupPhoto(GroupPhotoPreviewHandle("existing-preview"), "\"photo-1\"")
    }
}
