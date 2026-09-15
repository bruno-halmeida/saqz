package br.com.saqz.groups.data.attendance.share

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.network.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceLinkRegistrationGatewayTest {
    @Test fun registrationRequirementSurvivesTheAuthenticatedResponse() = runTest {
        val network = NetworkClient(MockEngine { request ->
            assertEquals("/api/attendance-links/resolve", request.url.encodedPath)
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond("""{"groupId":"group","gameId":"game","registrationRequired":true}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        val gateway = KtorAttendanceSharingGateway(AuthenticatedNetworkClient(network, object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("test-token"))
        }, object : SessionInvalidator { override fun invalidate() = Unit }))
        assertEquals(SaqzResult.Success(AttendanceLinkDestination(GroupId("group"), "game", true)),
            gateway.resolveLink(AttendanceLinkCode("A".repeat(43))))
    }
}
