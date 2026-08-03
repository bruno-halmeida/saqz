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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupPhotoViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `upload refreshes the photo URL with the returned digest and cleans the source`() = runTest(dispatcher) {
        val gateway = FakePhotoGateway()
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(selection()))
        val viewModel = viewModel(gateway, selection)

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        assertEquals("/api/groups/group-1/photo?v=photo-1", viewModel.state.value.photoUrl)

        viewModel.onIntent(GroupPhotoIntent.ChooseCamera)

        assertEquals("/api/groups/group-1/photo?v=photo-2", viewModel.state.value.photoUrl)
        assertEquals(listOf("source"), selection.cleaned)
        assertEquals(1, gateway.uploadCalls)
    }

    @Test
    fun `permission denial is visible in the sheet state`() = runTest(dispatcher) {
        val selection = FakeSelection(GroupPhotoSelectionResult.CameraPermissionDenied)
        val viewModel = viewModel(FakePhotoGateway(), selection)

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        viewModel.onIntent(GroupPhotoIntent.ChooseCamera)

        assertEquals(GroupPhotoUiError.CameraPermissionDenied, viewModel.state.value.error)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `removing the photo returns to the placeholder state`() = runTest(dispatcher) {
        val gateway = FakePhotoGateway()
        val viewModel = viewModel(gateway, FakeSelection(GroupPhotoSelectionResult.Cancelled))

        viewModel.onIntent(GroupPhotoIntent.BindGroup("group-1"))
        viewModel.onIntent(GroupPhotoIntent.Remove)

        assertEquals(null, viewModel.state.value.photoUrl)
        assertEquals(1, gateway.removeCalls)
    }

    private fun viewModel(
        gateway: FakePhotoGateway,
        selection: FakeSelection,
    ) = GroupPhotoViewModel(
        profileGateway = FakeGroupProfileGateway(),
        photoGateway = gateway,
        selection = selection,
        encoder = FakeEncoder,
        previews = GroupPhotoPreviewPort { byteArrayOf(1) },
    )

    private class FakeSelection(
        private val result: GroupPhotoSelectionResult,
    ) : GroupPhotoSelectionPort {
        val cleaned = mutableListOf<String>()

        override suspend fun chooseCamera() = result

        override suspend fun chooseLibrary() = result

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
                GroupPhotoReadResult.Available(byteArrayOf(1), GroupPhotoMediaType.JPEG.value, GroupPhotoVersionToken(versionValue)),
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
