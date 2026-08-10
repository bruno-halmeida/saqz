package br.com.saqz.network

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.onOptions
import org.koin.dsl.module
import okio.Buffer

/**
 * The path and query used by the authenticated image endpoint.
 *
 * Coil keeps the original URI as its cache key. The query is extracted only for the transport
 * request, so a photo digest remains part of the cache identity while Ktor can encode it safely.
 */
internal data class AuthenticatedImageRequest(
    val path: String,
    val query: Map<String, String>,
)

internal fun authenticatedImageRequest(url: String): AuthenticatedImageRequest =
    authenticatedImageRequest(url.toUri())

internal fun authenticatedImageRequest(uri: Uri): AuthenticatedImageRequest {
    require(uri.scheme == null && uri.authority == null) { "profile photo URL must be relative" }
    val path = requireNotNull(uri.path).takeIf(String::isNotBlank)
        ?: error("profile photo URL must contain a path")
    return AuthenticatedImageRequest(path = path, query = uri.query.toQueryParameters())
}

internal suspend fun loadAuthenticatedImage(
    client: AuthenticatedNetworkClient,
    uri: Uri,
): NetworkResult<NetworkBinaryBody> {
    val request = authenticatedImageRequest(uri)
    return client.readBinary(request.path, NetworkRequest(query = request.query))
}

/** Builds the one shared Coil loader used for authenticated profile media. */
fun authenticatedImageLoaderModule(context: PlatformContext): Module = module {
    single<ImageLoader> { authenticatedImageLoader(context, get()) }
        .onOptions { onClose { loader -> loader?.shutdown() } }
}

internal fun authenticatedImageLoader(
    context: PlatformContext,
    client: AuthenticatedNetworkClient,
): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(AuthenticatedImageFetcher.Factory(client))
    }
    // O fetcher do Coil roda em `Dispatchers.IO` por padrão, e este aqui pede o token ao
    // `NativeAuthPort` — no iOS um adapter `@MainActor`, e o alvo compila em Swift 6, onde
    // chamar de fora da main é **trap de runtime**: o app congela no debugger, sem crash e
    // sem mensagem. Só a busca vem para a main, e nada nela bloqueia — o I/O sai da thread
    // pelo próprio engine do Ktor e a decodificação continua no `decoderCoroutineContext`.
    // Cobre também o `sessionInvalidator.invalidate()` do 401, que toca outro port.
    .fetcherCoroutineContext(Dispatchers.Main)
    .build()

private class AuthenticatedImageFetcher(
    private val uri: Uri,
    private val options: Options,
    private val client: AuthenticatedNetworkClient,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = when (val result = loadAuthenticatedImage(client, uri)) {
        is NetworkResult.Success -> result.value.toFetchResult(options)
        is NetworkResult.Failure -> error("authenticated image request failed: ${result.error}")
    }

    class Factory(
        private val client: AuthenticatedNetworkClient,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != null || data.authority != null || data.path.isNullOrBlank()) return null
            return AuthenticatedImageFetcher(data, options, client)
        }
    }
}

private fun NetworkBinaryBody.toFetchResult(options: Options): SourceFetchResult = SourceFetchResult(
    source = ImageSource(
        source = Buffer().apply { write(bytes) },
        fileSystem = options.fileSystem,
    ),
    mimeType = contentType.substringBefore(';').trim(),
    dataSource = DataSource.NETWORK,
)

private fun String?.toQueryParameters(): Map<String, String> = orEmpty()
    .split('&')
    .filter(String::isNotEmpty)
    .associate { parameter ->
        val separator = parameter.indexOf('=')
        if (separator < 0) parameter to ""
        else parameter.substring(0, separator) to parameter.substring(separator + 1)
    }
