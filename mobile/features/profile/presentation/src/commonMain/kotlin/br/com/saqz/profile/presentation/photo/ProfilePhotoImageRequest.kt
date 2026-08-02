package br.com.saqz.profile.presentation.photo

import coil3.PlatformContext
import coil3.request.ImageRequest

internal fun profilePhotoImageRequest(
    context: PlatformContext,
    photoUrl: String,
): ImageRequest = ImageRequest.Builder(context)
    .data(photoUrl)
    .memoryCacheKey(photoUrl)
    .diskCacheKey(photoUrl)
    .build()
