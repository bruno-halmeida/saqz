package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GroupPhotoPreview(
    handle: GroupPhotoPreviewHandle,
    previews: GroupPhotoPreviewPort,
    modifier: Modifier,
): Boolean {
    var bitmap by remember(handle) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(handle, previews) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching { previews.read(handle)?.decodeToImageBitmap() }.getOrNull()
        }
    }
    val rendered = bitmap ?: return false
    Image(
        bitmap = rendered,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
    return true
}
