package br.com.saqz.profile.fake

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.Profile
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.domain.UpdateField
import br.com.saqz.profile.domain.UpdateSessionProfileRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeProfileGatewayTest {
    @Test
    fun `update applies only fields present in the patch and records it`() = runTest {
        val gateway = FakeProfileGateway()
        val originalCity = gateway.profile.user.city

        val result = gateway.updateProfile(
            UpdateSessionProfileRequest(
                nickname = UpdateField.Set(null),
                phoneVisibility = UpdateField.Set(PhoneVisibility.EVERYONE),
            ),
        )

        assertEquals(null, gateway.profile.user.nickname)
        assertEquals(PhoneVisibility.EVERYONE, gateway.profile.user.phoneVisibility)
        assertEquals(originalCity, gateway.profile.user.city)
        assertEquals(1, gateway.updateRequests.size)
        assertEquals(gateway.profile, assertIs<SaqzResult.Success<Profile>>(result).value)
    }

    @Test
    fun `photo operations retain bytes and make deletion visible in the fake state`() = runTest {
        val gateway = FakeProfileGateway()

        assertIs<SaqzResult.Success<Unit>>(gateway.uploadPhoto(byteArrayOf(1, 2), "image/jpeg"))
        assertEquals(
            FakeProfileGateway.UploadedPhoto(byteArrayOf(1, 2), "image/jpeg"),
            gateway.uploadedPhotos.single(),
        )

        gateway.deletePhoto()

        assertEquals(null, gateway.profile.user.photoUrl)
        assertEquals(1, gateway.deletePhotoCalls)
    }

    @Test
    fun `operation errors are injected without changing successful state`() = runTest {
        val gateway = FakeProfileGateway().apply {
            statsError = ProfileError.DataFailure(DataError.Connectivity)
        }

        val result = gateway.stats()

        assertEquals(
            ProfileError.DataFailure(DataError.Connectivity),
            assertIs<SaqzResult.Failure<ProfileError>>(result).error,
        )
        assertTrue(gateway.profile.user.displayName.isNotBlank())
    }
}
