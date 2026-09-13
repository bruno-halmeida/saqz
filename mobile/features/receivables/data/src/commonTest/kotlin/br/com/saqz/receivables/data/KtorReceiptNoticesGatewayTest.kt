package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class KtorReceiptNoticesGatewayTest {
    @Test fun activeNoticesExcludeInternalFutureAndExpiredMessages() = runTest {
        val content = listOf(notice("active"), notice("future", starts = "2026-10-01T00:00:00Z"),
            notice("ended", ends = "2026-09-13T12:00:00Z"), notice("internal", audience = "OPERATIONS"))
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/notices", r.url.encodedPath)
            assertEquals("Bearer token", r.headers[HttpHeaders.Authorization]); assertEquals(HttpMethod.Get, r.method)
            respond(content.joinToString(",", "[", "]"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        assertEquals(SaqzResult.Success(listOf(ReceiptNotice("active", "Plano", "Confira seu plano."))), g.active())
    }
    @Test fun duplicateOrInvalidNoticesAreNotSilentlyTreatedAsEmpty() = runTest {
        for (content in listOf("[${notice("same")},${notice("same")}]", "[${notice("bad", starts = "invalid")}]")) {
            val g = gateway(MockEngine { respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.active())
        }
    }
    private fun gateway(engine: MockEngine) = KtorReceiptNoticesGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider { override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("token")) }, object : SessionInvalidator { override fun invalidate() = Unit }),
        object : Clock { override fun now() = Instant.parse("2026-09-13T12:00:00Z") })
    private fun notice(id: String, starts: String = "2026-09-13T12:00:00Z", ends: String? = null, audience: String = "PLAN_OWNERS") =
        """{"id":"$id","title":"Plano","message":"Confira seu plano.","audience":"$audience","startsAt":"$starts","endsAt":${ends?.let { "\"$it\"" } ?: "null"}}"""
}
