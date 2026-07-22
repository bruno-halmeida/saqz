package br.com.saqz.androidapp.groups.attendance.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkUrl
import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal data class AndroidAttendanceShareLayout(val width: Int, val height: Int)

internal object AndroidAttendanceShareLayoutCalculator {
    const val WIDTH = 1080
    const val ROW_HEIGHT = 56

    fun calculate(image: AttendanceShareImage): AndroidAttendanceShareLayout =
        AndroidAttendanceShareLayout(WIDTH, image.heightUnits * ROW_HEIGHT)
}

internal class AndroidAttendanceShareAdapter(
    private val context: Context,
) : NativeAttendanceSharePort {
    private val directory = File(context.cacheDir, "attendance-share")

    override fun shareLink(url: AttendanceLinkUrl, done: (NativeAttendanceShareResult) -> Unit) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url.value)
            val chooser = Intent.createChooser(send, null)
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.onSuccess {
            done(NativeAttendanceShareResult.Success)
        }.onFailure {
            done(NativeAttendanceShareResult.Failure)
        }
    }

    override fun shareImage(image: AttendanceShareImage, done: (NativeAttendanceShareResult) -> Unit) {
        runCatching {
            val file = render(image)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.group-photo-files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, null)
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.onSuccess {
            done(NativeAttendanceShareResult.Success)
        }.onFailure {
            done(NativeAttendanceShareResult.Failure)
        }
    }

    private fun render(image: AttendanceShareImage): File {
        directory.mkdirs()
        val layout = AndroidAttendanceShareLayoutCalculator.calculate(image)
        val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 32f }
        var y = 96f
        canvas.drawText(image.title, 48f, y, titlePaint)
        y += 56f
        canvas.drawText(image.scheduleLine, 48f, y, bodyPaint)
        y += 40f
        canvas.drawText(image.venueLine, 48f, y, bodyPaint)
        y += 40f
        canvas.drawText(image.capacityLine, 48f, y, bodyPaint)
        y += 72f
        image.sections.forEach { section ->
            canvas.drawText("${section.title} (${section.countLabel})", 48f, y, titlePaint)
            y += 48f
            if (section.entries.isEmpty()) {
                canvas.drawText(section.emptyLabel, 48f, y, bodyPaint)
                y += 56f
            } else {
                section.entries.forEach { entry ->
                    canvas.drawText(entry, 48f, y, bodyPaint)
                    y += 56f
                }
            }
            y += 24f
        }
        val file = File(directory, "attendance-share-${UUID.randomUUID()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}
