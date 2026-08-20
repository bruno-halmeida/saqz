package br.com.saqz.groups.presentation.photo

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
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Suppress("InjectDispatcher")
internal actual suspend fun decodeGroupPhoto(bytes: ByteArray, targetPx: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) return@withContext null
        val data = bytes.usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.convert())
        } ?: return@withContext null
        try {
            val source = CGImageSourceCreateWithData(data, null) ?: return@withContext null
            try {
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
    val options = CFDictionaryCreateMutable(null, 3, null, null) ?: return null
    val maxPixelSize = CFNumberCreate(null, kCFNumberIntType, cValuesOf(targetPx))
    return try {
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
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
