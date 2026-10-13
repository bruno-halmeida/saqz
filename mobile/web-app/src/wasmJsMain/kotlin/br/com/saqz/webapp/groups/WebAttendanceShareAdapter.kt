package br.com.saqz.webapp.groups

import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import kotlinx.coroutines.CoroutineScope

// STUB: substituir por navigator.share (link e imagem como File) com fallback de download/cópia.
internal class WebAttendanceShareAdapter(private val scope: CoroutineScope) : NativeAttendanceSharePort {
    override fun shareLink(url: String, done: (NativeAttendanceShareResult) -> Unit) = done(NativeAttendanceShareResult.Failure)
    override fun shareImage(image: AttendanceShareImage, done: (NativeAttendanceShareResult) -> Unit) =
        done(NativeAttendanceShareResult.Failure)
}
