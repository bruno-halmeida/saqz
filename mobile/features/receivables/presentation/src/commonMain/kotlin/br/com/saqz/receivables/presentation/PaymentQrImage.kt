package br.com.saqz.receivables.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.io.encoding.Base64

@Composable
internal fun PaymentQrImage(encoded: String) {
    var bitmap by remember(encoded) { mutableStateOf<ImageBitmap?>(null) }
    var loaded by remember(encoded) { mutableStateOf(false) }
    LaunchedEffect(encoded) {
        bitmap = if (encoded.length <= 2_000_000) runCatching { decodePaymentQr(Base64.decode(encoded)) }.getOrNull() else null
        loaded = true
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    bitmap?.let { Image(it, stringResource(Res.string.payment_qr), Modifier.fillMaxWidth(0.72f).aspectRatio(1f)) }
    if (loaded && bitmap == null) Text(stringResource(Res.string.payment_qr_failed))
    }
}
internal expect suspend fun decodePaymentQr(bytes: ByteArray): ImageBitmap?
