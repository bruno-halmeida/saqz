package br.com.saqz.groups.data

import br.com.saqz.groups.port.EncodedGroupPhoto
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkMediaUpload
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.ByteReadChannel

data class GroupPhotoUploadCommand(
    val groupId: String,
    val groupEtag: String,
    val photo: EncodedGroupPhoto,
)

data class GroupPhotoReceipt(val etag: String)

sealed interface GroupPhotoReadResult {
    data class Available(
        val bytes: ByteArray,
        val contentType: String,
        val etag: String,
    ) : GroupPhotoReadResult {
        override fun equals(other: Any?): Boolean = other is Available &&
            bytes.contentEquals(other.bytes) && contentType == other.contentType && etag == other.etag

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data object NotModified : GroupPhotoReadResult
}

interface GroupPhotoGateway {
    suspend fun upload(command: GroupPhotoUploadCommand): NetworkResult<GroupPhotoReceipt>
    suspend fun read(groupId: String, etag: String? = null): NetworkResult<GroupPhotoReadResult>
    suspend fun remove(groupId: String, groupEtag: String): NetworkResult<GroupPhotoReceipt>
}

class GroupPhotoApi(private val network: AuthenticatedNetworkClient) : GroupPhotoGateway {
    override suspend fun upload(command: GroupPhotoUploadCommand): NetworkResult<GroupPhotoReceipt> {
        val photo = command.photo
        val result = network.uploadMedia(
            HttpMethod.Put,
            route(command.groupId),
            NetworkMediaUpload(
                fileName = "group-photo.${photo.mediaType.extension}",
                contentType = ContentType.parse(photo.mediaType.value),
                contentLength = photo.contentLength,
                etag = command.groupEtag,
                openChannel = { ByteReadChannel(photo.source.read()) },
            ),
        )
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> result.metadata.header(HttpHeaders.ETag)?.let {
                NetworkResult.Success(GroupPhotoReceipt(it), result.metadata)
            } ?: NetworkResult.Failure(NetworkError.InvalidResponse)
        }
    }

    override suspend fun read(groupId: String, etag: String?): NetworkResult<GroupPhotoReadResult> {
        val request = NetworkRequest(headers = etag?.let { mapOf(HttpHeaders.IfNoneMatch to it) }.orEmpty())
        return when (val result = network.readBinary(route(groupId), request)) {
            is NetworkResult.Failure -> if (result.error == NetworkError.HttpStatus(304)) {
                NetworkResult.Success(GroupPhotoReadResult.NotModified)
            } else result
            is NetworkResult.Success -> {
                val responseEtag = result.value.etag ?: return NetworkResult.Failure(NetworkError.InvalidResponse)
                NetworkResult.Success(
                    GroupPhotoReadResult.Available(result.value.bytes, result.value.contentType, responseEtag),
                    result.metadata,
                )
            }
        }
    }

    override suspend fun remove(groupId: String, groupEtag: String): NetworkResult<GroupPhotoReceipt> =
        when (val result = network.executeNoContent(
            HttpMethod.Delete,
            route(groupId),
            NetworkRequest(headers = mapOf(HttpHeaders.IfMatch to groupEtag)),
        )) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> result.metadata.header(HttpHeaders.ETag)?.let {
                NetworkResult.Success(GroupPhotoReceipt(it), result.metadata)
            } ?: NetworkResult.Failure(NetworkError.InvalidResponse)
        }

    private fun route(groupId: String) = "api/groups/$groupId/photo"
}
