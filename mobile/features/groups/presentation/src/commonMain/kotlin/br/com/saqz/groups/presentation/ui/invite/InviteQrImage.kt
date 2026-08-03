package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
internal fun InviteQrImage(
    pngBytes: ByteArray,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pngBytes) { bitmap = decodeInviteQr(pngBytes) }
    Box(modifier = modifier.size(260.dp), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(240.dp),
            )
        }
    }
}

internal expect suspend fun decodeInviteQr(bytes: ByteArray): ImageBitmap?
