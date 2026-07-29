package br.com.saqz.access.presentation.identitycompletion

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ProfilePhotoCallback
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IdentityCompletionViewModelTest {

    // A 1c é composta porque a máquina já está em `CompletingIdentity`: o primeiro quadro
    // tem de trazer o nome do provedor, não um campo em branco que pisca.
    @Test fun `the first frame already carries the identity being completed`() = runTest {
        val fixture = fixture()

        assertEquals("Person Name", fixture.viewModel.state.value.name)
    }

    @Test fun `editing a field reaches the shared session machine`() = runTest {
        val fixture = fixture()

        fixture.viewModel.onIntent(IdentityCompletionIntent.UpdatePhone("(11) 99999-0000"))
        runCurrent()

        assertEquals("(11) 99999-0000", fixture.viewModel.state.value.phone)
    }

    @Test fun `the chosen image is decoded for the screen`() = runTest {
        val fixture = fixture()

        fixture.viewModel.onIntent(IdentityCompletionIntent.PickPhoto)
        fixture.photos.complete(ProfilePhotoResult.Selected(PNG_PIXEL, "image/png"))
        runCurrent()

        assertEquals(1, fixture.viewModel.state.value.photo?.width)
    }

    // Bytes que a plataforma entregou mas que o decodificador recusa não podem derrubar a
    // composição: a tela volta ao círculo vazio.
    @Test fun `undecodable bytes leave the circle empty instead of crashing`() = runTest {
        val fixture = fixture()

        fixture.viewModel.onIntent(IdentityCompletionIntent.PickPhoto)
        fixture.photos.complete(ProfilePhotoResult.Selected(byteArrayOf(1, 2, 3), "image/jpeg"))
        runCurrent()

        assertNull(fixture.viewModel.state.value.photo)
    }

    @Test fun `giving up on the picker leaves no trace on the screen`() = runTest {
        val fixture = fixture()

        fixture.viewModel.onIntent(IdentityCompletionIntent.PickPhoto)
        fixture.photos.complete(ProfilePhotoResult.Cancelled)
        runCurrent()

        assertFalse(fixture.viewModel.state.value.photoFailed)
        assertNull(fixture.viewModel.state.value.photo)
    }

    // A escolha que morreu na plataforma é o mesmo aviso do envio recusado: nada saiu do
    // aparelho, e a pessoa precisa saber que a foto não entrou.
    @Test fun `a picker failure warns without blocking the registration`() = runTest {
        val fixture = fixture()

        fixture.viewModel.onIntent(IdentityCompletionIntent.PickPhoto)
        fixture.photos.complete(ProfilePhotoResult.Failed)
        runCurrent()

        assertTrue(fixture.viewModel.state.value.photoFailed)
        assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
    }

    @Test fun `the back control signs the incomplete identity out`() = runTest {
        val fixture = fixture()

        fixture.viewModel.onIntent(IdentityCompletionIntent.Back)
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    private fun TestScope.fixture(): Fixture {
        val machine = SessionAccessStateMachine(FakeAuthPort(), FakeLocalState(), FakeSessionGateway(), this)
        machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(user)))
        runCurrent()
        assertIs<SessionAccessState.CompletingIdentity>(machine.state.value)
        val photos = FakePhotoPort()
        return Fixture(IdentityCompletionViewModel(machine, photos), machine, photos)
    }

    private class Fixture(
        val viewModel: IdentityCompletionViewModel,
        val machine: SessionAccessStateMachine,
        val photos: FakePhotoPort,
    )

    private class FakePhotoPort : NativeProfilePhotoPort {
        private var callback: ProfilePhotoCallback? = null
        var cancelled = false

        override fun chooseCamera(done: ProfilePhotoCallback): Cancelable = register(done)

        override fun chooseLibrary(done: ProfilePhotoCallback): Cancelable = register(done)

        fun complete(result: ProfilePhotoResult) = callback!!.complete(result)

        private fun register(done: ProfilePhotoCallback): Cancelable {
            callback = done
            return object : Cancelable {
                override fun cancel() { cancelled = true }
            }
        }
    }

    private class FakeSessionGateway : SessionGateway {
        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> =
            SaqzResult.Success(phoneRequiredSession)

        override suspend fun completeProfile(
            phone: String,
            displayName: String?,
        ): SaqzResult<AccessSession, AccessError> = SaqzResult.Success(phoneRequiredSession)

        override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, AccessError> =
            SaqzResult.Success(Unit)
    }

    private class FakeAuthPort : NativeAuthPort {
        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable {
            override fun cancel() = Unit
        }

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)
    }

    private class FakeLocalState : LocalAccessStatePort {
        override fun readSelectedGroupId(done: ValueCallback) = Unit
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) =
            done.complete(OperationResult.Success)

        override fun readPendingInvite(done: ValueCallback) = Unit
        override fun writePendingInvite(value: String?, done: ResultCallback) =
            done.complete(OperationResult.Success)
    }

    private companion object {
        val user = NativeUser("subject", "person@example.test", true, "Person Name")
        val session = AccessSession(AccessUser("user-id", "person@example.test", "Person Name"), emptyList())
        val phoneRequiredSession = session.copy(user = session.user.copy(phoneRequired = true))

        // Um PNG 1×1 válido, em bytes: é o menor arquivo que o decodificador de cada
        // plataforma aceita, e serve para provar que a decodificação aconteceu de verdade.
        val PNG_PIXEL = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte(),
        )
    }
}
