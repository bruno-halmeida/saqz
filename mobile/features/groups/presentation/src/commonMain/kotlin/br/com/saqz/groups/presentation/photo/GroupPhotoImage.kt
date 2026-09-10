package br.com.saqz.groups.presentation.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import org.koin.mp.KoinPlatformTools

internal fun groupPhotoUrl(groupId: String, version: Long): String = "/api/groups/$groupId/photo?v=$version"

@Composable
internal fun GroupRemotePhotoRoot(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val loader = remember { KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<ImageLoader>() }
    GroupRemotePhoto(photoUrl, loader, modifier, contentScale, fallback)
}

@Composable
internal fun GroupRemotePhoto(
    photoUrl: String?,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val currentFallback by rememberUpdatedState(fallback)
    val fallbackContent = remember { movableContentOf { currentFallback() } }
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
        fallbackContent()
        return
    }
    SubcomposeAsyncImage(
        model = request,
        imageLoader = loader,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        loading = { fallbackContent() },
        error = { fallbackContent() },
    )
}
