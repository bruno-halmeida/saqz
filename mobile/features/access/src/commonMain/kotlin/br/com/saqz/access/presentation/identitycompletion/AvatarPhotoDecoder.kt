package br.com.saqz.access.presentation.identitycompletion

import androidx.compose.ui.graphics.ImageBitmap

/**
 * O alvo do seletor de foto da 1c, em pixels: os 92dp do círculo na maior densidade que
 * interessa (~4x). O destino é o avatar, não o arquivo.
 */
internal const val AVATAR_TARGET_PX = 384

/**
 * Decodifica os bytes escolhidos **já reduzidos** ao tamanho do avatar, fora da thread
 * principal. Devolve nulo quando os bytes não são imagem decodificável.
 *
 * As duas coisas são o ponto, e nenhuma é detalhe:
 *
 * - **reduzir antes de decodificar**, não depois. Os adapters (VUL-83) aceitam até
 *   4096×4096 e limitam só o JPEG comprimido a 5 MiB; decodificar esse limite inteiro
 *   alocaria uns 64 MiB de bitmap para desenhar um círculo de 92dp, e em aparelho fraco
 *   isso é o app morrendo por falta de memória. Reduzir depois não ajuda: o pico já
 *   aconteceu. Cada plataforma tem a sua forma de amostrar durante a decodificação, e é
 *   por isso que isto é `expect` em vez de uma chamada só;
 * - **fora da thread principal**, porque decodificar é síncrono e travaria a tela.
 */
internal expect suspend fun decodeAvatarPhoto(bytes: ByteArray, targetPx: Int): ImageBitmap?

/**
 * O fator de subamostragem para uma imagem de [sourceWidth]×[sourceHeight] caber em
 * [targetPx]: potência de dois, que é o que os decodificadores aceitam sem reamostrar.
 *
 * Nunca amplia — imagem já menor que o alvo volta com fator 1, e esticá-la só gastaria
 * memória para mostrar os mesmos pixels.
 */
internal fun avatarSampleSize(sourceWidth: Int, sourceHeight: Int, targetPx: Int): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetPx <= 0) return 1
    var sample = 1
    var largest = maxOf(sourceWidth, sourceHeight)
    while (largest / 2 >= targetPx) {
        sample *= 2
        largest /= 2
    }
    return sample
}
