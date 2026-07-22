package br.com.saqz.groups.data.photo

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.GroupPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkBinaryBody
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkMediaUpload
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.ByteReadChannel

class KtorGroupPhotoGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GroupPhotoGateway {
    override suspend fun upload(
        command: GroupPhotoUploadCommand,
    ): SaqzResult<GroupPhotoReceipt, GroupPhotoError> {
        val photo = command.photo
        return network.uploadMedia(
            HttpMethod.Put,
            route(command.groupId),
            NetworkMediaUpload(
                fileName = "group-photo.${photo.mediaType.extension}",
                contentType = ContentType.parse(photo.mediaType.value),
                contentLength = photo.contentLength,
                etag = command.groupVersion.value,
                openChannel = { ByteReadChannel(photo.source.read()) },
            ),
        ).toReceipt()
    }

    override suspend fun read(
        groupId: GroupId,
        version: GroupPhotoVersionToken?,
    ): SaqzResult<GroupPhotoReadResult, GroupPhotoError> {
        val request = NetworkRequest(headers = version?.let {
            mapOf(HttpHeaders.IfNoneMatch to it.value)
        }.orEmpty())
        return retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.readBinary(route(groupId), request)
        }.toReadResult()
    }

    override suspend fun remove(
        groupId: GroupId,
        groupVersion: GroupPhotoVersionToken,
    ): SaqzResult<GroupPhotoReceipt, GroupPhotoError> = network.executeNoContent(
        HttpMethod.Delete,
        route(groupId),
        NetworkRequest(headers = mapOf(HttpHeaders.IfMatch to groupVersion.value)),
    ).toReceipt()

    private fun route(groupId: GroupId) = "api/groups/${groupId.value}/photo"
}

private fun NetworkResult<Unit>.toReceipt(): SaqzResult<GroupPhotoReceipt, GroupPhotoError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toPhotoError())
    is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)
        ?.takeIf(String::isNotBlank)
        ?.let(::GroupPhotoVersionToken)
        ?.let(::GroupPhotoReceipt)
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<NetworkBinaryBody>.toReadResult() = when (this) {
    is NetworkResult.Failure -> if (error == NetworkError.HttpStatus(304)) {
        SaqzResult.Success(GroupPhotoReadResult.NotModified)
    } else {
        SaqzResult.Failure(error.toPhotoError())
    }

    is NetworkResult.Success -> value.etag
        ?.takeIf(String::isNotBlank)
        ?.let(::GroupPhotoVersionToken)
        ?.takeIf { value.contentType.isNotBlank() }
        ?.let { GroupPhotoReadResult.Available(value.bytes, value.contentType, it) }
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkError.toPhotoError(): GroupPhotoError = when (this) {
    is NetworkError.ApiProblemError -> when {
        problem.status == 409 || problem.code == "VERSION_CONFLICT" -> GroupPhotoError.StaleVersion
        problem.status == 404 -> GroupPhotoError.NotFound
        problem.status == 413 -> GroupPhotoError.MediaLimit
        else -> GroupPhotoError.DataFailure(problem.status.toDataError())
    }

    is NetworkError.HttpStatus -> when (status) {
        409 -> GroupPhotoError.StaleVersion
        404 -> GroupPhotoError.NotFound
        413 -> GroupPhotoError.MediaLimit
        else -> GroupPhotoError.DataFailure(status.toDataError())
    }

    NetworkError.PayloadTooLarge -> GroupPhotoError.MediaLimit
    NetworkError.Timeout -> GroupPhotoError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> GroupPhotoError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> GroupPhotoError.DataFailure(DataError.InvalidResponse)
    NetworkError.Unavailable, NetworkError.Unknown -> GroupPhotoError.DataFailure(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun invalidResponse() = SaqzResult.Failure(
    GroupPhotoError.DataFailure(DataError.InvalidResponse),
)
