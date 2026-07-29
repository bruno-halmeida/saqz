package br.com.saqz.access.presentation.identitycompletion

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Duas passagens do `BitmapFactory`: a primeira só lê o cabeçalho (`inJustDecodeBounds`,
 * que não aloca pixel nenhum) e a segunda decodifica já amostrada. É a forma da plataforma
 * de nunca materializar o bitmap inteiro.
 */
// O dispatcher é escolhido aqui, e não injetado: isto é a decodificação da
// plataforma, não um colaborador de teste — e injetá-lo significaria enfiá-lo na
// ViewModel que chama, que é justamente o que a seção 4 do mobile/AGENTS.md proíbe.
@Suppress("InjectDispatcher")
internal actual suspend fun decodeAvatarPhoto(bytes: ByteArray, targetPx: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = avatarSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
