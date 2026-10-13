package br.com.saqz.webapp.groups

import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import br.com.saqz.webapp.interop.BrowserOutcome
import br.com.saqz.webapp.interop.BrowserShare
import br.com.saqz.webapp.interop.renderAttendanceImageBase64
import kotlinx.coroutines.CoroutineScope

/**
 * Compartilhamento da presença pelo navegador. O link vai por `navigator.share` e, sem ele, para a área de
 * transferência; a imagem é desenhada aqui (canvas 2D, como `AndroidAttendanceShareAdapter`) e vai como
 * `File` por `navigator.share`, ou é baixada quando o navegador não compartilha arquivos.
 *
 * `scope` não é usado: a chamada ao navegador precisa ser síncrona com o toque (ativação do usuário).
 */
internal class WebAttendanceShareAdapter(@Suppress("UnusedPrivateProperty") scope: CoroutineScope) : NativeAttendanceSharePort {
    override fun shareLink(url: String, done: (NativeAttendanceShareResult) -> Unit) {
        val finish = { outcome: BrowserOutcome -> done(outcome.toResult()) }
        if (BrowserShare.canShare) BrowserShare.shareText(url, finish) else BrowserShare.copyText(url, finish)
    }

    override fun shareImage(image: AttendanceShareImage, done: (NativeAttendanceShareResult) -> Unit) {
        val png = renderAttendanceImageBase64(image) ?: return done(NativeAttendanceShareResult.Failure)
        BrowserShare.shareFile(png, "saqz-presenca.png", "image/png") { done(it.toResult()) }
    }

    private fun BrowserOutcome.toResult(): NativeAttendanceShareResult = when (this) {
        BrowserOutcome.DONE -> NativeAttendanceShareResult.Success
        // Como no convite: fechar a folha não é erro (a folha nativa nem informa a desistência).
        BrowserOutcome.CANCELLED -> NativeAttendanceShareResult.Success
        BrowserOutcome.FAILED -> NativeAttendanceShareResult.Failure
    }
}
