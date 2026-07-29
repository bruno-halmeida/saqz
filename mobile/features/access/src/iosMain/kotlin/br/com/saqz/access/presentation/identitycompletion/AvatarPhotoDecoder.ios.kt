package br.com.saqz.access.presentation.identitycompletion

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.NSData
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * `CGImageSource` com `kCGImageSourceThumbnailMaxPixelSize`: o ImageIO lê o cabeçalho e
 * decodifica **já no tamanho pedido**, sem materializar a imagem inteira. É o equivalente
 * do `inSampleSize` do Android, e o motivo de a redução vir antes da decodificação.
 *
 * O retorno passa por PNG porque o que a tela consome é `ImageBitmap`, e o caminho comum
 * para ele é `decodeToImageBitmap` — nesse ponto os bytes já são os do avatar, não os do
 * arquivo escolhido.
 *
 * Os `CFRelease` são obrigação de quem cria: as funções `Create` do CoreFoundation
 * entregam a posse, e o K/N não tem coletor para elas.
 */
@OptIn(ExperimentalForeignApi::class)
// O dispatcher é escolhido aqui, e não injetado: isto é a decodificação da
// plataforma, não um colaborador de teste — e injetá-lo significaria enfiá-lo na
// ViewModel que chama, que é justamente o que a seção 4 do mobile/AGENTS.md proíbe.
@Suppress("InjectDispatcher")
internal actual suspend fun decodeAvatarPhoto(bytes: ByteArray, targetPx: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) return@withContext null
        // `CFDataCreate` copia os bytes, então o `usePinned` só precisa cobrir a cópia.
        val data = bytes.usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.convert())
        } ?: return@withContext null
        try {
            val source = CGImageSourceCreateWithData(data, null) ?: return@withContext null
            try {
                // `CGImageSourceCreateThumbnailAtIndex` é `Create`: a imagem reduzida é
                // **nossa**, e sem este `CFRelease` cada foto escolhida vazava o bitmap até
                // o processo morrer. O `UIImage` que a embrulha não assume a posse.
                val thumbnail = source.thumbnail(targetPx) ?: return@withContext null
                try {
                    val png = UIImagePNGRepresentation(UIImage.imageWithCGImage(thumbnail))
                        ?: return@withContext null
                    runCatching { png.toByteArray().decodeToImageBitmap() }.getOrNull()
                } finally {
                    CGImageRelease(thumbnail)
                }
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(data)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun CGImageSourceRef.thumbnail(targetPx: Int): CGImageRef? {
    val options = CFDictionaryCreateMutable(null, 2, null, null) ?: return null
    val maxPixelSize = CFNumberCreate(null, kCFNumberIntType, cValuesOf(targetPx))
    return try {
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxPixelSize)
        CGImageSourceCreateThumbnailAtIndex(this, 0.convert(), options)
    } finally {
        maxPixelSize?.let { CFRelease(it) }
        CFRelease(options)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { target ->
        target.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
