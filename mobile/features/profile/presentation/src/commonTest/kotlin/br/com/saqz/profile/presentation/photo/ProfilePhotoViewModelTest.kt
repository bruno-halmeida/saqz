package br.com.saqz.profile.presentation.photo

import br.com.saqz.profile.domain.ProfilePhotoSelectionCallback
import br.com.saqz.profile.domain.ProfilePhotoSelectionCancelable
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.domain.ProfilePhotoSelectionResult
import br.com.saqz.profile.fake.FakeProfileGateway
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ProfilePhotoViewModelTest {
    @Test
    fun `upload refreshes the digest and exposes the new photo url`() = runTest {
        val gateway = FakeProfileGateway()
        val viewModel = ProfilePhotoViewModel(gateway, FakeSelection(ProfilePhotoSelectionResult.Selected(BYTES, MEDIA_TYPE)))
        val previousUrl = viewModel.state.value.photoUrl

        viewModel.onIntent(ProfilePhotoIntent.ChooseLibrary)

        assertNotEquals(previousUrl, viewModel.state.value.photoUrl)
        assertEquals("/api/session/photo?v=upload-1", viewModel.state.value.photoUrl)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `removal immediately returns to initials state`() = runTest {
        val gateway = FakeProfileGateway()
        val viewModel = ProfilePhotoViewModel(
            gateway,
            FakeSelection(ProfilePhotoSelectionResult.Cancelled),
            initialPhotoUrl = gateway.profile.user.photoUrl,
        )
        viewModel.onIntent(ProfilePhotoIntent.Remove)

        assertNull(viewModel.state.value.photoUrl)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `camera and library permission denials remain distinct`() = runTest {
        val camera = ProfilePhotoViewModel(
            FakeProfileGateway(),
            FakeSelection(ProfilePhotoSelectionResult.CameraPermissionDenied),
        )
        camera.onIntent(ProfilePhotoIntent.ChooseCamera)

        val library = ProfilePhotoViewModel(
            FakeProfileGateway(),
            FakeSelection(ProfilePhotoSelectionResult.LibraryPermissionDenied),
        )
        library.onIntent(ProfilePhotoIntent.ChooseLibrary)

        assertEquals(ProfilePhotoError.CameraPermissionDenied, camera.state.value.error)
        assertEquals(ProfilePhotoError.LibraryPermissionDenied, library.state.value.error)
    }

    private class FakeSelection(
        private val result: ProfilePhotoSelectionResult,
    ) : ProfilePhotoSelectionPort {
        override fun chooseCamera(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable = deliver(done)
        override fun chooseLibrary(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable = deliver(done)

        private fun deliver(done: ProfilePhotoSelectionCallback) = object : ProfilePhotoSelectionCancelable {
            init { done.complete(result) }
            override fun cancel() = Unit
        }
    }

    private companion object {
        val BYTES = byteArrayOf(1, 2, 3)
        const val MEDIA_TYPE = "image/jpeg"
    }
}
