package br.com.saqz.webapp.interop

import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage
import br.com.saqz.groups.domain.attendance.share.AttendanceShareImageSection
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import br.com.saqz.groups.port.InviteNativeFailureCode
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.receivables.domain.port.ReceiptFileCallback
import br.com.saqz.receivables.domain.port.ReceiptFileSelection
import br.com.saqz.webapp.access.WebShareAdapter
import br.com.saqz.webapp.groups.WebAttendanceShareAdapter
import br.com.saqz.webapp.groups.WebInviteAdapters
import br.com.saqz.webapp.receivables.WebReceiptDocumentPicker
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest

/** As portas sobre o navegador, contra o navegador de mentira de [installFakeBrowser]. */
@Suppress("TooManyFunctions") // um caso por porta/comportamento
class BrowserPortsTest {
    private val scope = CoroutineScope(Job())
    private val png = byteArrayOf(1, 2, 3, 4)

    @Test
    fun shareTextReportsDoneCancelledAndFailed() = runTest {
        installFakeBrowser("ok", canShareFiles = true, hasClipboard = true)
        assertTrue(BrowserShare.canShare)
        assertEquals(BrowserOutcome.DONE, await { BrowserShare.shareText("olá", it) })
        assertContains(fakeBrowserLog(), "text:olá")

        installFakeBrowser("abort", canShareFiles = true, hasClipboard = true)
        assertEquals(BrowserOutcome.CANCELLED, await { BrowserShare.shareText("olá", it) })

        installFakeBrowser("denied", canShareFiles = true, hasClipboard = true)
        assertEquals(BrowserOutcome.FAILED, await { BrowserShare.shareText("olá", it) })
    }

    @Test
    fun noShareApiMeansCanShareIsFalseAndCopyStillWorks() = runTest {
        installFakeBrowser("none", canShareFiles = false, hasClipboard = true)

        assertFalse(BrowserShare.canShare)
        assertEquals(BrowserOutcome.DONE, await { BrowserShare.copyText("link", it) })
        assertContains(fakeBrowserLog(), "\"copied\":[\"link\"]")
    }

    @Test
    fun copyWithoutAClipboardFails() = runTest {
        installFakeBrowser("none", canShareFiles = false, hasClipboard = false)

        assertEquals(BrowserOutcome.FAILED, await { BrowserShare.copyText("link", it) })
    }

    @Test
    fun fileIsSharedWhenTheBrowserAcceptsFilesAndDownloadedOtherwise() = runTest {
        installFakeBrowser("ok", canShareFiles = true, hasClipboard = true)
        assertEquals(BrowserOutcome.DONE, await { BrowserShare.shareFile(png, "qr.png", "image/png", it) })
        assertContains(fakeBrowserLog(), "files:qr.png|image/png|4")

        installFakeBrowser("ok", canShareFiles = false, hasClipboard = true)
        assertEquals(BrowserOutcome.DONE, await { BrowserShare.shareFile(png, "qr.png", "image/png", it) })
        assertContains(fakeBrowserLog(), "\"downloads\":[\"qr.png\"]")
        assertFalse("files:" in fakeBrowserLog())
    }

    @Test
    fun downloadNeverGoesThroughTheShareSheet() = runTest {
        installFakeBrowser("ok", canShareFiles = true, hasClipboard = true)

        assertEquals(BrowserOutcome.DONE, await { BrowserShare.downloadFile(png, "qr.png", "image/png", it) })

        assertContains(fakeBrowserLog(), "\"downloads\":[\"qr.png\"]")
        assertContains(fakeBrowserLog(), "\"shared\":[]")
    }

    @Test
    fun filePickerReturnsTheFileCancelAndInvalid() = runTest {
        installFakeBrowser("none", canShareFiles = false, hasClipboard = false)
        val types = listOf("application/pdf", "image/png")

        fakePickFile("image/png", 4, Base64.encode(png))
        val picked = assertIs<BrowserFilePick.Picked>(awaitPick(types, 100))
        assertEquals("image/png", picked.mimeType)
        assertEquals(png.toList(), picked.bytes.toList())
        assertContains(fakeBrowserLog(), "\"picker\":[\"application/pdf,image/png\"]")

        fakePickScript("cancel")
        assertEquals(BrowserFilePick.Cancelled, awaitPick(types, 100))
        fakePickScript("empty")
        assertEquals(BrowserFilePick.Cancelled, awaitPick(types, 100))

        fakePickFile("image/gif", 4, Base64.encode(png))
        assertEquals(BrowserFilePick.Invalid, awaitPick(types, 100))
        fakePickFile("image/png", 101, Base64.encode(png))
        assertEquals(BrowserFilePick.Invalid, awaitPick(types, 100))
        fakePickFile("image/png", 0, "")
        assertEquals(BrowserFilePick.Invalid, awaitPick(types, 100))
    }

    @Test
    fun accessSharePortTreatsCancelAsSuccessAndFallsBackToCopy() = runTest {
        installFakeBrowser("abort", canShareFiles = true, hasClipboard = true)
        assertEquals(OperationResult.Success, awaitShare("texto"))

        installFakeBrowser("denied", canShareFiles = true, hasClipboard = true)
        assertEquals(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE), awaitShare("texto"))

        installFakeBrowser("none", canShareFiles = false, hasClipboard = true)
        assertEquals(OperationResult.Success, awaitShare("copiado"))
        assertContains(fakeBrowserLog(), "\"copied\":[\"copiado\"]")

        installFakeBrowser("none", canShareFiles = false, hasClipboard = false)
        assertEquals(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE), awaitShare("nada"))
    }

    @Test
    fun invitePortsTreatCancelAsSuccessAndOnlyShareTextNeedsTheShareApi() = runTest {
        val invites = WebInviteAdapters(scope)

        installFakeBrowser("abort", canShareFiles = true, hasClipboard = true)
        // Fechar a folha não é erro: a folha nativa nem informa a desistência.
        assertEquals(InviteNativeOperationResult.Success, await<InviteNativeOperationResult> { invites.share.shareText("t", it) })
        assertEquals(
            InviteNativeOperationResult.Success,
            await<InviteNativeOperationResult> { invites.share.shareImage(InviteShareImage(png), it) },
        )

        installFakeBrowser("none", canShareFiles = false, hasClipboard = true)
        assertEquals(
            InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE),
            await<InviteNativeOperationResult> { invites.share.shareText("t", it) },
        )
        assertEquals(
            InviteNativeOperationResult.Success,
            await<InviteNativeOperationResult> { invites.share.shareImage(InviteShareImage(png), it) },
        )
        assertEquals(
            InviteNativeOperationResult.Success,
            await<InviteNativeOperationResult> { invites.share.saveImage(InviteShareImage(png), it) },
        )
        assertEquals(
            InviteNativeOperationResult.Success,
            await<InviteNativeOperationResult> { invites.clipboard.copyText("url", it) },
        )
        assertContains(fakeBrowserLog(), "\"downloads\":[\"saqz-convite.png\",\"saqz-convite.png\"]")
        assertContains(fakeBrowserLog(), "\"copied\":[\"url\"]")
        assertEquals(
            InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE),
            await<InviteNativeOperationResult> { invites.share.saveImage(InviteShareImage(byteArrayOf()), it) },
        )
    }

    @Test
    fun attendanceLinkFallsBackToCopyAndImageIsDrawnThenShared() = runTest {
        val adapter = WebAttendanceShareAdapter(scope)

        installFakeBrowser("none", canShareFiles = false, hasClipboard = true)
        assertEquals(NativeAttendanceShareResult.Success, await<NativeAttendanceShareResult> { adapter.shareLink("https://x/y", it) })
        assertContains(fakeBrowserLog(), "\"copied\":[\"https://x/y\"]")

        installFakeBrowser("abort", canShareFiles = true, hasClipboard = true)
        assertEquals(NativeAttendanceShareResult.Success, await<NativeAttendanceShareResult> { adapter.shareLink("https://x/y", it) })

        installFakeBrowser("ok", canShareFiles = true, hasClipboard = true)
        val image = AttendanceShareImage(
            title = "Treino",
            scheduleLine = "Qua 19:30",
            venueLine = "Arena",
            capacityLine = "2/12",
            sections = listOf(
                AttendanceShareImageSection("Confirmados", "2", "Ninguém", listOf("Ana", "Beto")),
                AttendanceShareImageSection("Fila", "0", "Ninguém na fila", emptyList()),
            ),
            heightUnits = 4,
        )
        assertEquals(NativeAttendanceShareResult.Success, await<NativeAttendanceShareResult> { adapter.shareImage(image, it) })

        val log = fakeBrowserLog()
        val lines = listOf("Treino", "Qua 19:30", "Arena", "2/12", "Confirmados (2)", "Ana", "Beto", "Fila (0)", "Ninguém na fila")
        assertContains(log, lines.joinToString(",", "\"drawn\":[", "]") { "\"$it\"" })
        assertContains(log, "\"canvas\":\"1080x")
        assertContains(log, "files:saqz-presenca.png|image/png|3")
    }

    @Test
    fun longAttendanceLinesGetAnEllipsisInsteadOfOverflowing() = runTest {
        installFakeBrowser("ok", canShareFiles = true, hasClipboard = true)
        val longName = "N".repeat(200)
        val image = AttendanceShareImage("T", "s", "v", "c", listOf(AttendanceShareImageSection("A", "1", "-", listOf(longName))), 1)

        assertEquals(
            NativeAttendanceShareResult.Success,
            await<NativeAttendanceShareResult> { WebAttendanceShareAdapter(scope).shareImage(image, it) },
        )

        val drawn = fakeBrowserLog().substringAfter("\"drawn\":[").substringBefore("]")
        // 1080 - 2 * 48 = 984 px a 20 px por caractere: no máximo 49 caracteres, o último é a reticência.
        assertContains(drawn, "N".repeat(48) + "…")
        assertFalse(longName in drawn)
    }

    @Test
    fun receiptPickerReturnsValidatedFilesAndTheOtherOutcomes() = runTest {
        installFakeBrowser("none", canShareFiles = false, hasClipboard = false)
        val picker = WebReceiptDocumentPicker(scope)

        fakePickFile("application/pdf", 4, Base64.encode(png))
        val selected = assertIs<ReceiptFileSelection.Selected>(awaitReceipt(picker))
        assertEquals("application/pdf", selected.file.contentType)
        assertEquals(png.toList(), selected.file.bytes.toList())
        assertContains(fakeBrowserLog(), "\"picker\":[\"application/pdf,image/jpeg,image/png\"]")

        fakePickScript("cancel")
        assertEquals(ReceiptFileSelection.Cancelled, awaitReceipt(picker))
        fakePickFile("text/plain", 4, Base64.encode(png))
        assertEquals(ReceiptFileSelection.Invalid, awaitReceipt(picker))
        fakePickFile("image/png", 5 * 1024 * 1024 + 1, Base64.encode(png))
        assertEquals(ReceiptFileSelection.Invalid, awaitReceipt(picker))
    }

    @Test
    fun receiptPickerSupersedesAPendingChoiceAndIgnoresCancelledOnes() = runTest {
        installFakeBrowser("none", canShareFiles = false, hasClipboard = false)
        val picker = WebReceiptDocumentPicker(scope)
        val first = CompletableDeferred<ReceiptFileSelection>()
        val second = CompletableDeferred<ReceiptFileSelection>()
        fakePickFile("image/png", 4, Base64.encode(png))

        picker.choose { first.complete(it) }
        picker.choose { second.complete(it) }

        assertEquals(ReceiptFileSelection.Cancelled, first.await())
        assertIs<ReceiptFileSelection.Selected>(second.await())

        val dropped = CompletableDeferred<ReceiptFileSelection>()
        val next = CompletableDeferred<ReceiptFileSelection>()
        picker.choose { dropped.complete(it) }.cancel()
        picker.choose { next.complete(it) }
        next.await()
        assertFalse(dropped.isCompleted)
    }

    private suspend fun <T> await(start: ((T) -> Unit) -> Unit): T {
        val result = CompletableDeferred<T>()
        start { result.complete(it) }
        return result.await()
    }

    private suspend fun awaitPick(types: List<String>, maxBytes: Int): BrowserFilePick =
        await { BrowserFilePicker.pick(types, maxBytes, it) }

    private suspend fun awaitShare(text: String): OperationResult {
        val done = CompletableDeferred<OperationResult>()
        WebShareAdapter(scope).share(
            text,
            object : ResultCallback {
                override fun complete(result: OperationResult) {
                    done.complete(result)
                }
            },
        )
        return done.await()
    }

    private suspend fun awaitReceipt(picker: WebReceiptDocumentPicker): ReceiptFileSelection {
        val result = CompletableDeferred<ReceiptFileSelection>()
        picker.choose(ReceiptFileCallback { result.complete(it) })
        return result.await()
    }
}
