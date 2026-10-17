package br.com.saqz.groups.data.attendance.share

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.network.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceLinkClosedGameGatewayTest {
    private suspend fun resolveWith(body: String): SaqzResult<AttendanceLinkDestination, AttendanceSharingError> {
        val network = NetworkClient(MockEngine {
            respond(body, HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/problem+json"))
        }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        val gateway = KtorAttendanceSharingGateway(AuthenticatedNetworkClient(network, object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("test-token"))
        }, object : SessionInvalidator { override fun invalidate() = Unit }))
        return gateway.resolveLink(AttendanceLinkCode("A".repeat(43)))
    }

    private fun problem(reason: String?) = """{"status":404,"code":"ATTENDANCE_LINK_INVALID_OR_EXPIRED","correlationId":"corr-1"""" +
        (reason?.let { ""","reason":"$it"""" } ?: "") + "}"

    @Test fun eachReasonBecomesItsOwnClosedGameError() = runTest {
        mapOf(
            "GAME_STARTED" to AttendanceLinkClosedReason.GameStarted,
            "GAME_CANCELLED" to AttendanceLinkClosedReason.GameCancelled,
            "GAME_UNPUBLISHED" to AttendanceLinkClosedReason.GameUnpublished,
        ).forEach { (reason, expected) ->
            assertEquals(SaqzResult.Failure(AttendanceSharingError.GameClosed(expected)), resolveWith(problem(reason)), reason)
        }
    }

    @Test fun withoutReasonOrWithAnUnknownOneTheErrorStaysTheGenericInvalidOrExpired() = runTest {
        assertEquals(SaqzResult.Failure(AttendanceSharingError.InvalidOrExpired), resolveWith(problem(null)))
        assertEquals(SaqzResult.Failure(AttendanceSharingError.InvalidOrExpired), resolveWith(problem("GAME_SOMETHING_NEW")))
    }
}
