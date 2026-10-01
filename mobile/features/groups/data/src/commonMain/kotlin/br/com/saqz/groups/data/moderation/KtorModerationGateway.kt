package br.com.saqz.groups.data.moderation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.moderation.BlockedPerson
import br.com.saqz.groups.domain.moderation.ContentReport
import br.com.saqz.groups.domain.moderation.ModerationError
import br.com.saqz.groups.domain.moderation.ModerationGateway
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class ContentReportTransport(
    val groupId: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val details: String? = null,
)

@Serializable
internal data class BlockedPersonTransport(
    val userId: String = "",
    val displayName: String = "",
    val blockedAt: String = "",
)

@Serializable
internal data class BlockPersonTransport(val groupId: String)

class KtorModerationGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : ModerationGateway {
    // `details` nulo sai do corpo: o campo é opcional no contrato.
    private val json = Json

    // POST sem `requestId` no contrato: uma retentativa poderia abrir duas denúncias.
    override suspend fun fileContentReport(report: ContentReport): EmptyResult<ModerationError> =
        network.executeNoContent(
            HttpMethod.Post,
            "api/reports",
            NetworkRequest(json.encodeToString(ContentReportTransport.serializer(), report.toTransport())),
        ).toEmptyResult()

    override suspend fun listBlockedPeople(): SaqzResult<List<BlockedPerson>, ModerationError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, "api/me/blocks", ListSerializer(BlockedPersonTransport.serializer()))
        }.toBlockedPeopleResult()

    // PUT e DELETE no próprio recurso são idempotentes no contrato: repetir não duplica nada.
    override suspend fun blockPerson(userId: String, groupId: GroupId): EmptyResult<ModerationError> =
        retryTransport(RetrySafety.IdempotentWrite, delayMillis = retryDelay) {
            network.executeNoContent(
                HttpMethod.Put,
                "api/me/blocks/$userId",
                NetworkRequest(json.encodeToString(BlockPersonTransport.serializer(), BlockPersonTransport(groupId.value))),
            )
        }.toEmptyResult()

    override suspend fun unblockPerson(userId: String): EmptyResult<ModerationError> =
        retryTransport(RetrySafety.IdempotentWrite, delayMillis = retryDelay) {
            network.executeNoContent(HttpMethod.Delete, "api/me/blocks/$userId")
        }.toEmptyResult()
}

private fun ContentReport.toTransport() = ContentReportTransport(
    groupId = groupId.value,
    targetType = targetType.name,
    targetId = targetId,
    reason = reason.name,
    details = details?.trim()?.takeIf(String::isNotEmpty)?.take(ContentReport.MAX_DETAILS_LENGTH),
)

private fun BlockedPersonTransport.toDomain(): BlockedPerson? =
    if (userId.isBlank()) null else BlockedPerson(userId, displayName, blockedAt)

private fun NetworkResult<List<BlockedPersonTransport>>.toBlockedPeopleResult():
    SaqzResult<List<BlockedPerson>, ModerationError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toModerationError())
    is NetworkResult.Success -> value.map { it.toDomain() }
        .takeIf { people -> people.none { it == null } }
        ?.filterNotNull()
        ?.let { SaqzResult.Success(it) }
        ?: SaqzResult.Failure(ModerationError.DataFailure(DataError.InvalidResponse))
}

private fun NetworkResult<Unit>.toEmptyResult(): EmptyResult<ModerationError> = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(Unit)
    is NetworkResult.Failure -> SaqzResult.Failure(error.toModerationError())
}

private fun NetworkError.toModerationError(): ModerationError = when (this) {
    is NetworkError.ApiProblemError -> if (problem.code == "VALIDATION_FAILED" || problem.status == 422) {
        ModerationError.Validation(ValidationDetails(emptyList(), problem.fieldErrors.orEmpty()))
    } else {
        ModerationError.DataFailure(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> ModerationError.DataFailure(status.toDataError())
    NetworkError.Timeout -> ModerationError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> ModerationError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> ModerationError.DataFailure(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> ModerationError.DataFailure(DataError.PayloadTooLarge)
    NetworkError.Unavailable, NetworkError.Unknown -> ModerationError.DataFailure(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
