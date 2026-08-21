package br.com.saqz.groups.presentation.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

val LocalGroupPhotoImageLoader = compositionLocalOf<ImageLoader?> { null }

internal fun groupPhotoUrl(groupId: String, version: Long): String = "/api/groups/$groupId/photo?v=$version"

@Composable
internal fun GroupRemotePhoto(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val loader = LocalGroupPhotoImageLoader.current
    val remoteUrl = photoUrl?.takeUnless { it.startsWith("pending:") }
    val context = LocalPlatformContext.current
    val request = if (loader != null && remoteUrl != null) {
        remember(context, remoteUrl) {
            ImageRequest.Builder(context)
                .data(remoteUrl)
                .memoryCacheKey(remoteUrl)
                .diskCacheKey(remoteUrl)
                .build()
        }
    } else {
        null
    }
    if (request == null || loader == null) {
        fallback()
        return
    }
    SubcomposeAsyncImage(
        model = request,
        imageLoader = loader,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        loading = { fallback() },
        error = { fallback() },
    )
}
