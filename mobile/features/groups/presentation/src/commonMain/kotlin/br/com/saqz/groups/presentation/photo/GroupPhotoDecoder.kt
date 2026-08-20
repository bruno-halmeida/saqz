package br.com.saqz.groups.presentation.photo

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Miniatura do quadrado da foto do grupo, em pixels: 72dp do export na maior densidade
 * que interessa (~4x). O destino é o thumb, não o arquivo enviado.
 */
internal const val GROUP_PHOTO_THUMB_PX = 288

/**
 * Decodifica os bytes **já reduzidos** ao tamanho do thumb, fora da thread principal.
 * Devolve nulo quando os bytes não são imagem decodificável.
 *
 * Reduzir antes de decodificar evita materializar o JPEG inteiro (os adapters aceitam
 * até 4096×4096) só para pintar um quadrado de 72dp.
 */
internal expect suspend fun decodeGroupPhoto(bytes: ByteArray, targetPx: Int): ImageBitmap?

internal fun groupPhotoSampleSize(sourceWidth: Int, sourceHeight: Int, targetPx: Int): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetPx <= 0) return 1
    var sample = 1
    var largest = maxOf(sourceWidth, sourceHeight)
    while (largest / 2 >= targetPx) {
        sample *= 2
        largest /= 2
    }
    return sample
}
