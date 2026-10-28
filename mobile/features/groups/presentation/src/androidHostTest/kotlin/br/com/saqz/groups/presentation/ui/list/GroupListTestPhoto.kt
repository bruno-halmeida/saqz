package br.com.saqz.groups.presentation.ui.list

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlin.coroutines.EmptyCoroutineContext

/**
 * O Robolectric não baixa imagem: este loader devolve uma "foto" de quadra desenhada aqui (piso laranja, linhas brancas,
 * rede e gente em azul-escuro), para a captura mostrar a foto sangrada com as etiquetas por cima. Sem troca de contexto,
 * como o loader dos testes de foto do `commonTest`.
 */
internal fun groupListPhotoLoader(context: PlatformContext): ImageLoader {
    val photo = courtBitmap().asImage()
    return ImageLoader.Builder(context).coroutineContext(EmptyCoroutineContext).components {
        add(Interceptor { chain -> SuccessResult(photo, chain.request) })
    }.build()
}

private fun courtBitmap(): Bitmap {
    val width = 448
    val height = 480
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(196, 110, 58))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Tábuas do piso.
    paint.color = Color.rgb(176, 96, 48)
    for (x in 0 until width step 28) canvas.drawRect(x.toFloat(), 0f, x + 3f, height.toFloat(), paint)
    // Linhas da quadra.
    paint.color = Color.WHITE
    paint.strokeWidth = 6f
    canvas.drawLine(0f, height * 0.78f, width.toFloat(), height * 0.78f, paint)
    canvas.drawLine(width * 0.15f, height * 0.4f, width * 0.15f, height.toFloat(), paint)
    // Rede.
    paint.color = Color.rgb(30, 30, 40)
    canvas.drawRect(0f, height * 0.2f, width.toFloat(), height * 0.24f, paint)
    // Pessoas.
    paint.color = Color.rgb(20, 40, 110)
    listOf(0.25f, 0.55f, 0.82f).forEach { cx ->
        canvas.drawCircle(width * cx, height * 0.36f, 26f, paint)
        canvas.drawRect(width * cx - 30f, height * 0.42f, width * cx + 30f, height * 0.7f, paint)
    }
    return bitmap
}
