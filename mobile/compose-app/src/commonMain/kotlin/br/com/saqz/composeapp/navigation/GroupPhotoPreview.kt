package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GroupPhotoPreview(
    handle: GroupPhotoPreviewHandle,
    previews: GroupPhotoPreviewPort,
    imageLoader: ImageLoader,
    modifier: Modifier,
): GroupPhotoRenderState {
    val context = LocalPlatformContext.current
    var bytes by remember(handle, previews) { mutableStateOf<ByteArray?>(null) }
    var state by remember(handle, previews) { mutableStateOf(GroupPhotoRenderState.LOADING) }

    LaunchedEffect(handle, previews) {
        state = GroupPhotoRenderState.LOADING
        bytes = withContext(Dispatchers.Default) {
            runCatching { previews.read(handle) }.getOrNull()
        }
        if (bytes == null) state = GroupPhotoRenderState.FAILURE
    }

    val payload = bytes
    if (payload != null) {
        val request = remember(handle, payload, context) {
            ImageRequest.Builder(context)
                .data(payload)
                .memoryCacheKey(handle.value)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onLoading = { state = GroupPhotoRenderState.LOADING },
            onSuccess = { state = GroupPhotoRenderState.SUCCESS },
            onError = { state = GroupPhotoRenderState.FAILURE },
            modifier = modifier,
        )
    }
    return state
}
