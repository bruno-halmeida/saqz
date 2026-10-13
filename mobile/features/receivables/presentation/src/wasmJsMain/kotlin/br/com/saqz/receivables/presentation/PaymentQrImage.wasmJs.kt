package br.com.saqz.receivables.presentation

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

internal actual suspend fun decodePaymentQr(bytes: ByteArray): ImageBitmap? =
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
