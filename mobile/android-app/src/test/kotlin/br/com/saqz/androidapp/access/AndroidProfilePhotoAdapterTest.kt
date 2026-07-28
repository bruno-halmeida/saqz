package br.com.saqz.androidapp.access

import br.com.saqz.access.domain.port.ProfilePhotoCallback
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoByteSource
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidProfilePhotoAdapterTest {
    @Test
    fun cameraEntregaBytesCodificadosEApagaAOrigem() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(SELECTION))
        val encoder = FakeEncoder(encoded(byteArrayOf(1, 2, 3)))
        val received = Received()

        AndroidProfilePhotoAdapter(selection, encoder, scope).chooseCamera(received)

        assertEquals(ProfilePhotoResult.Selected(byteArrayOf(1, 2, 3), "image/jpeg"), received.result)
        assertEquals(listOf("camera"), selection.calls)
        assertEquals(listOf(SOURCE), selection.cleaned)
        assertEquals(GroupPhotoCrop(), encoder.crop)
    }

    @Test
    fun galeriaUsaAEscolhaDaGaleria() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(SELECTION))
        val received = Received()

        AndroidProfilePhotoAdapter(selection, FakeEncoder(encoded(byteArrayOf(9))), scope)
            .chooseLibrary(received)

        assertEquals(ProfilePhotoResult.Selected(byteArrayOf(9), "image/jpeg"), received.result)
        assertEquals(listOf("library"), selection.calls)
    }

    @Test
    fun desistenciaDaPessoaChegaComoCancelled() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val selection = FakeSelection(GroupPhotoSelectionResult.Cancelled)
        val received = Received()

        AndroidProfilePhotoAdapter(selection, FakeEncoder(null), scope).chooseCamera(received)

        assertEquals(ProfilePhotoResult.Cancelled, received.result)
        assertTrue(selection.cleaned.isEmpty())
    }

    @Test
    fun falhaDaEscolhaChegaComoFailed() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val received = Received()

        AndroidProfilePhotoAdapter(FakeSelection(GroupPhotoSelectionResult.Failed), FakeEncoder(null), scope)
            .chooseCamera(received)

        assertEquals(ProfilePhotoResult.Failed, received.result)
    }

    @Test
    fun falhaDaCodificacaoChegaComoFailedEAindaApagaAOrigem() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(SELECTION))
        val received = Received()

        AndroidProfilePhotoAdapter(selection, FakeEncoder(GroupPhotoEncodingResult.Failed), scope)
            .chooseCamera(received)

        assertEquals(ProfilePhotoResult.Failed, received.result)
        assertEquals(listOf(SOURCE), selection.cleaned)
    }

    @Test
    fun bytesVaziosNaoViramSelected() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val received = Received()

        AndroidProfilePhotoAdapter(
            FakeSelection(GroupPhotoSelectionResult.Selected(SELECTION)),
            FakeEncoder(encoded(ByteArray(0))),
            scope,
        ).chooseCamera(received)

        assertEquals(ProfilePhotoResult.Failed, received.result)
    }

    @Test
    fun cancelarInterrompeAEscolhaEmAndamentoSemEntregarResultado() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(SELECTION), pending = true)
        val received = Received()

        val cancelable = AndroidProfilePhotoAdapter(selection, FakeEncoder(null), scope)
            .chooseCamera(received)
        cancelable.cancel()

        assertNull(received.result)
        assertTrue(selection.cancelled)
    }

    @Test
    fun cancelarDuranteACodificacaoApagaAOrigem() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val selection = FakeSelection(GroupPhotoSelectionResult.Selected(SELECTION))
        val encoder = FakeEncoder(null, pending = true)
        val received = Received()

        val cancelable = AndroidProfilePhotoAdapter(selection, encoder, scope).chooseCamera(received)
        cancelable.cancel()

        assertNull(received.result)
        assertEquals(listOf(SOURCE), selection.cleaned)
    }

    private class Received : ProfilePhotoCallback {
        var result: ProfilePhotoResult? = null

        override fun complete(result: ProfilePhotoResult) {
            this.result = result
        }
    }

    private class FakeSelection(
        private val result: GroupPhotoSelectionResult,
        private val pending: Boolean = false,
    ) : GroupPhotoSelectionPort {
        val calls = mutableListOf<String>()
        val cleaned = mutableListOf<String>()
        var cancelled = false

        override suspend fun chooseCamera(): GroupPhotoSelectionResult {
            calls += "camera"
            return deliver()
        }

        override suspend fun chooseLibrary(): GroupPhotoSelectionResult {
            calls += "library"
            return deliver()
        }

        override fun cleanup(source: String) {
            cleaned += source
        }

        private suspend fun deliver(): GroupPhotoSelectionResult {
            if (pending) {
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    cancelled = true
                }
            }
            return result
        }
    }

    private class FakeEncoder(
        private val result: GroupPhotoEncodingResult?,
        private val pending: Boolean = false,
    ) : GroupPhotoEncoderPort {
        var crop: GroupPhotoCrop? = null

        override suspend fun encode(source: String, crop: GroupPhotoCrop): GroupPhotoEncodingResult {
            this.crop = crop
            if (pending) CompletableDeferred<Unit>().await()
            return checkNotNull(result)
        }

        override fun cancel(source: String) = Unit
    }

    private companion object {
        const val SOURCE = "source-1.img"
        val SELECTION = GroupPhotoSelection(
            GroupPhotoSourceHandle(SOURCE),
            GroupPhotoPreviewHandle(SOURCE),
            800,
            600,
        )

        fun encoded(bytes: ByteArray) = GroupPhotoEncodingResult.Encoded(
            EncodedGroupPhoto(GroupPhotoMediaType.JPEG, bytes.size.toLong().coerceAtLeast(1), GroupPhotoByteSource { bytes }),
        )
    }
}
