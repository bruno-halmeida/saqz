package br.com.saqz.groups.presentation.photo

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoByteSource
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupPhotoViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a bound group keeps the chosen photo locally until commit`() = runTest(dispatcher) {
        val gateway = FakePhotoGateway()
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(selection()))
        val viewModel = viewModel(gateway, selection)

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        assertEquals("/api/groups/group-1/photo?v=photo-1", viewModel.awaitBound().photoUrl)
        assertEquals(1, viewModel.state.value.preview?.width)

        viewModel.onIntent(GroupPhotoIntent.ChooseCamera)
        val held = viewModel.state.first { it.hasPending && !it.isLoading }

        assertEquals("pending:preview", held.photoUrl)
        assertEquals(0, gateway.uploadCalls)
        assertTrue(selection.cleaned.isEmpty())

        viewModel.onIntent(GroupPhotoIntent.Commit)
        val uploaded = viewModel.state.first { it.photoUrl == "/api/groups/group-1/photo?v=photo-2" && !it.isLoading }

        assertEquals("/api/groups/group-1/photo?v=photo-2", uploaded.photoUrl)
        assertEquals(1, uploaded.preview?.width)
        assertEquals(listOf("source"), selection.cleaned)
        assertEquals(1, gateway.uploadCalls)
    }

    @Test
    fun `binding the same group again does not upload a held photo`() = runTest(dispatcher) {
        val gateway = FakePhotoGateway()
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(selection()))
        val viewModel = viewModel(gateway, selection)

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        viewModel.awaitBound()
        viewModel.onIntent(GroupPhotoIntent.ChooseCamera)
        viewModel.state.first { it.hasPending && !it.isLoading }

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))

        assertEquals(0, gateway.uploadCalls)
        assertTrue(viewModel.state.value.hasPending)
        assertEquals("pending:preview", viewModel.state.value.photoUrl)
        assertTrue(selection.cleaned.isEmpty())

        viewModel.onIntent(GroupPhotoIntent.Commit)
        viewModel.state.first { it.photoUrl == "/api/groups/group-1/photo?v=photo-2" && !it.isLoading }

        assertEquals(1, gateway.uploadCalls)
    }

    @Test
    fun `permission denial is visible in the sheet state`() = runTest(dispatcher) {
        val selection = FakeSelection(GroupPhotoSelectionResult.CameraPermissionDenied)
        val viewModel = viewModel(FakePhotoGateway(), selection)

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        viewModel.awaitBound()
        viewModel.onIntent(GroupPhotoIntent.ChooseCamera)
        viewModel.state.first { it.error == GroupPhotoUiError.CameraPermissionDenied }

        assertEquals(GroupPhotoUiError.CameraPermissionDenied, viewModel.state.value.error)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `removing the photo returns to the placeholder state`() = runTest(dispatcher) {
        val gateway = FakePhotoGateway()
        val viewModel = viewModel(gateway, FakeSelection(GroupPhotoSelectionResult.Cancelled))

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        viewModel.awaitBound()
        viewModel.onIntent(GroupPhotoIntent.Remove)
        viewModel.state.first { !it.isLoading && it.photoUrl == null }

        assertEquals(null, viewModel.state.value.photoUrl)
        assertNull(viewModel.state.value.preview)
        assertEquals(1, gateway.removeCalls)
    }

    @Test
    fun `create flow keeps the photo locally until the group exists`() = runTest(dispatcher) {
        val gateway = FakePhotoGateway()
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(selection()))
        val viewModel = viewModel(gateway, selection)

        viewModel.onIntent(GroupPhotoIntent.ChooseCamera)
        val held = viewModel.state.first { it.hasPending && !it.isLoading }

        assertEquals(1, selection.chooseCalls)
        assertEquals("pending:preview", held.photoUrl)
        assertEquals(1, held.preview?.width)
        assertNull(held.error)
        assertEquals(0, gateway.uploadCalls)
        assertTrue(selection.cleaned.isEmpty())

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        val uploaded = viewModel.state.first { !it.hasPending && !it.isLoading && it.photoUrl?.startsWith("/api/") == true }

        assertEquals("/api/groups/group-1/photo?v=photo-1", uploaded.photoUrl)
        assertEquals(1, uploaded.preview?.width)
        assertEquals(1, gateway.uploadCalls)
        assertEquals(listOf("source"), selection.cleaned)
    }

    @Test
    fun `create flow can remove a locally held photo without a group id`() = runTest(dispatcher) {
        val gateway = FakePhotoGateway()
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(selection()))
        val viewModel = viewModel(gateway, selection)

        viewModel.onIntent(GroupPhotoIntent.ChooseLibrary)
        viewModel.state.first { it.hasPending && !it.isLoading }
        viewModel.onIntent(GroupPhotoIntent.Remove)

        assertNull(viewModel.state.value.photoUrl)
        assertNull(viewModel.state.value.preview)
        assertTrue(!viewModel.state.value.hasPending)
        assertNull(viewModel.state.value.error)
        assertEquals(0, gateway.removeCalls)
        assertEquals(listOf("source"), selection.cleaned)
    }

    @Test
    fun `permission denial during create still opens the picker path`() = runTest(dispatcher) {
        val selection = FakeSelection(GroupPhotoSelectionResult.CameraPermissionDenied)
        val viewModel = viewModel(FakePhotoGateway(), selection)

        viewModel.onIntent(GroupPhotoIntent.ChooseCamera)

        assertEquals(1, selection.chooseCalls)
        assertEquals(GroupPhotoUiError.CameraPermissionDenied, viewModel.state.value.error)
        assertTrue(!viewModel.state.value.hasPending)
        assertTrue(!viewModel.state.value.isLoading)
    }

    private fun viewModel(
        gateway: FakePhotoGateway,
        selection: FakeSelection,
    ) = GroupPhotoViewModel(
        profileGateway = FakeGroupProfileGateway(),
        photoGateway = gateway,
        selection = selection,
        encoder = FakeEncoder,
        previews = GroupPhotoPreviewPort { GROUP_PHOTO_PNG_PIXEL },
    )

    private suspend fun GroupPhotoViewModel.awaitBound(): GroupPhotoState =
        state.first { !it.isLoading && it.photoUrl != null }

    private class FakeSelection(
        private val result: GroupPhotoSelectionResult,
    ) : GroupPhotoSelectionPort {
        val cleaned = mutableListOf<String>()
        var chooseCalls = 0

        override suspend fun chooseCamera(): GroupPhotoSelectionResult {
            chooseCalls++
            return result
        }

        override suspend fun chooseLibrary(): GroupPhotoSelectionResult {
            chooseCalls++
            return result
        }

        override fun cleanup(source: String) {
            cleaned += source
        }
    }

    private class FakePhotoGateway : GroupPhotoGateway {
        var uploadCalls = 0
        var removeCalls = 0
        private var readCalls = 0

        override suspend fun upload(command: GroupPhotoUploadCommand): SaqzResult<GroupPhotoReceipt, GroupPhotoError> {
            uploadCalls++
            return SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"group-3\"")))
        }

        override suspend fun read(
            groupId: GroupId,
            version: GroupPhotoVersionToken?,
        ): SaqzResult<GroupPhotoReadResult, GroupPhotoError> {
            val versionValue = if (readCalls++ == 0) "\"photo-1\"" else "\"photo-2\""
            return SaqzResult.Success(
                GroupPhotoReadResult.Available(GROUP_PHOTO_PNG_PIXEL, GroupPhotoMediaType.JPEG.value, GroupPhotoVersionToken(versionValue)),
            )
        }

        override suspend fun remove(
            groupId: GroupId,
            groupVersion: GroupPhotoVersionToken,
        ): SaqzResult<GroupPhotoReceipt, GroupPhotoError> {
            removeCalls++
            return SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"group-4\"")))
        }
    }

    private object FakeEncoder : br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort {
        override suspend fun encode(source: String, crop: GroupPhotoCrop): GroupPhotoEncodingResult =
            GroupPhotoEncodingResult.Encoded(
                EncodedGroupPhoto(
                    mediaType = GroupPhotoMediaType.JPEG,
                    contentLength = 1,
                    source = GroupPhotoByteSource { byteArrayOf(1) },
                ),
            )

        override fun cancel(source: String) = Unit
    }

    private fun selection() = GroupPhotoSelection(
        source = GroupPhotoSourceHandle("source"),
        preview = br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle("preview"),
        width = 100,
        height = 100,
    )
}
