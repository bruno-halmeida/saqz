package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class KtorFinancialManagementGatewayTest {
    @Test fun `discovery and management decode role and exact literal cents without commercial gate`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val gateway = gateway(MockEngine { request -> requests += request
            when (request.url.encodedPath) {
                "/api/receivables/accounts" -> respond(envelope("""[{"id":"account","ownerUserId":"owner","registration":"CORRECTION_REQUIRED","newOperationsEnabled":false}]"""), headers = headers)
                "/api/receivables/accounts/account/management" -> respond(envelope("""{"accountId":"account","role":"DELEGATE","correction":${fields()}}"""), headers = headers)
                else -> error("unexpected ${request.url}")
            }
        })
        val accounts = assertIs<SaqzResult.Success<List<ManagedReceiptAccount>>>(gateway.accounts()).value
        val view = assertIs<SaqzResult.Success<ReceiptManagementView>>(gateway.management(accounts.single().id)).value
        assertEquals("owner", accounts.single().ownerId); assertFalse(accounts.single().operationsEnabled)
        assertEquals(ReceiptManagementRole.DELEGATE, view.role); assertEquals(250001, view.correction.incomeCents)
        assertEquals(listOf(HttpMethod.Get, HttpMethod.Get), requests.map { it.method })
    }

    @Test fun `correction sends allowlist request id and exact cents while recover uses distinct endpoint`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val gateway = gateway(MockEngine { request -> requests += request
            respond(envelope("""{"accountId":"account","status":"SUCCEEDED"}"""), headers = headers)
        })
        val correction = correction()
        assertEquals(SaqzResult.Success(Unit), gateway.correct("account", ReceiptCorrectionCommand("request", correction)))
        assertEquals(SaqzResult.Success(Unit), gateway.recover("account", "request"))
        val payload = Json.parseToJsonElement(requests[0].body.toByteArray().decodeToString()).jsonObject
        assertEquals(250001, payload.getValue("incomeCents").toString().toLong())
        assertEquals("request", payload.getValue("requestId").toString().trim('"'))
        assertFalse(payload.containsKey("cpfCnpj")); assertFalse(payload.containsKey("name")); assertFalse(payload.containsKey("ownerId"))
        assertEquals("/api/receivables/accounts/account/corrections/request/recover", requests[1].url.encodedPath)
    }

    @Test fun `grant and revoke bind exact account user terms and stable request id`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val gateway = gateway(MockEngine { request -> requests += request
            if (request.method == HttpMethod.Delete) respond(envelope("{}", "request-2"), headers = headers)
            else respond(envelope("""{"accountId":"account","userId":"admin","grantedAt":"now","revokedAt":null}"""), headers = headers)
        })
        assertEquals(SaqzResult.Success(Unit), gateway.grant("account", "admin", "v1", "request"))
        assertEquals(SaqzResult.Success(Unit), gateway.revoke("account", "admin", "request-2"))
        val grant = requests[0].body.toByteArray().decodeToString()
        assertTrue(grant.contains("\"userId\":\"admin\"")); assertTrue(grant.contains("\"termsVersion\":\"v1\""))
        assertTrue(grant.contains("\"acknowledgedWholeAccount\":true"))
        assertEquals("request-2", requests[1].url.parameters["requestId"]); assertEquals(HttpMethod.Delete, requests[1].method)
    }

    private fun gateway(engine: MockEngine) = KtorFinancialManagementGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider { override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token")) },
        object : SessionInvalidator { override fun invalidate() = Unit }))
    private fun envelope(value: String, requestId: String = "request") = """{"value":$value,"requestId":"$requestId"}"""
    private fun fields() = """{"email":"owner@example.test","phone":null,"mobilePhone":"11999999999","site":null,
        "incomeCents":250001,"postalCode":"01001000","address":"Rua","addressNumber":"10","complement":null,"province":"Centro"}"""
    private fun correction() = ReceiptRegistrationCorrection("owner@example.test", null, "11999999999", null, 250001,
        "01001000", "Rua", "10", null, "Centro")
    private val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
