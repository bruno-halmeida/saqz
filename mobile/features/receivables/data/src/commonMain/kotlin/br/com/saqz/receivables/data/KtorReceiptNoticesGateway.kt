package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock
import kotlin.time.Instant

class KtorReceiptNoticesGateway(private val network: AuthenticatedNetworkClient, private val clock: Clock) : ReceiptNoticesGateway {
    override suspend fun active(): SaqzResult<List<ReceiptNotice>, ReceiptError> = when (val result = network.execute(
        HttpMethod.Get, "api/receivables/notices", ListSerializer(ReceiptNoticeTransport.serializer()))) {
        is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(false))
        is NetworkResult.Success -> try {
            val now = clock.now()
            require(result.value.map { it.id }.distinct().size == result.value.size)
            SaqzResult.Success(result.value.filter { it.audience == "PLAN_OWNERS" && Instant.parse(it.startsAt) <= now &&
                (it.endsAt == null || Instant.parse(it.endsAt) > now) }.map {
                require(it.id.isNotBlank() && it.title.isNotBlank() && it.message.isNotBlank())
                ReceiptNotice(it.id, it.title, it.message)
            })
        } catch (_: IllegalArgumentException) { SaqzResult.Failure(ReceiptError.INVALID) }
    }
}
@Serializable internal data class ReceiptNoticeTransport(val id: String, val audience: String, val title: String,
    val message: String, val startsAt: String, val endsAt: String? = null)
