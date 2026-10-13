package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

internal actual suspend fun decodeInviteQr(bytes: ByteArray): ImageBitmap? =
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
