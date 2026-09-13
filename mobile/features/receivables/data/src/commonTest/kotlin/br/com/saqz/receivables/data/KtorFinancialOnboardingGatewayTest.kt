package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorFinancialOnboardingGatewayTest {
    @Test fun ownAccountAbsentAndExistingAreDistinctFromReadFailures() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/accounts/me", r.url.encodedPath); assertEquals("", r.url.encodedQuery)
            assertEquals("Bearer token", r.headers[HttpHeaders.Authorization]); assertEquals(HttpMethod.Get, r.method)
            respond(envelope(accountJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(account), g.mine("owner"))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.mine("foreign"))
        assertEquals(SaqzResult.Success(null), gateway(MockEngine { respond("", HttpStatusCode.NotFound) }).mine("owner"))
        assertEquals(SaqzResult.Failure(ReceiptError.NETWORK), gateway(MockEngine { throw javaFreeIoFailure() }).mine("owner"))
    }
    @Test fun currentTermsRequireActualVersionAndContentAndUseDiscoveryRoute() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/terms", r.url.encodedPath)
            respond(envelope("""{"version":"v2","content":"Termos vigentes"}"""), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(ReceiptTerms("v2", "Termos vigentes")), g.currentTerms())
        for (json in listOf("""{"version":"","content":"Terms"}""", """{"version":"v1","content":" "}"""))
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway(MockEngine { respond(envelope(json), HttpStatusCode.OK, headers) }).currentTerms())
    }
    @Test fun registrationSendsExactLegalDataAndIdenticalIdempotentRetry() = runTest {
        val bodies = mutableListOf<String>()
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/accounts", r.url.encodedPath); assertEquals(HttpMethod.Post, r.method)
            bodies += (r.body as TextContent).text
            if (bodies.size == 1) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(envelope(accountJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(account), g.create("owner", command))
        assertEquals(2, bodies.size); assertEquals(bodies[0], bodies[1])
        val expected = """{"requestId":"request","acceptedTerms":true,"termsVersion":"v1","name":"Titular","email":"owner@example.test",
            "cpfCnpj":"12345678901","mobilePhone":"11999999999","incomeCents":250001,"address":"Rua Teste","addressNumber":"10",
            "province":"Centro","postalCode":"01001000","birthDate":"1990-01-01","companyType":null}"""
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(bodies[0]))
    }
    @Test fun recoverySendsOnlySameRequestAndValidatesOwnerAndCorrelation() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/accounts/me/recover", r.url.encodedPath)
            assertEquals("""{"requestId":"request"}""", (r.body as TextContent).text)
            respond(envelope(accountJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(account), g.recover("owner", "request"))
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.recover("foreign", "request"))
        val bad = gateway(MockEngine { respond(envelope(accountJson).replace("\"request\"", "\"other\""), HttpStatusCode.OK, headers) })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), bad.create("owner", command))
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), bad.recover("owner", "request"))
    }
    @Test fun documentDiscoveryPreservesInstructionsAndRejectsUnsafeLinksAndDuplicateIds() = runTest {
        val json = """[{"id":"doc","type":"CUSTOM","status":"PENDING","description":"Ata","onboardingUrl":null}]"""
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/accounts/me/documents", r.url.encodedPath)
            respond(envelope(json), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(listOf(document)), g.documents())
        for (url in listOf("http://asaas.com/a", "https://evil.test/a", "https://asaas.com.evil.test/a", "https://user@asaas.com/a", "https://asaas.com:444/a")) {
            val body = json.replace("null", "\"$url\"")
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway(MockEngine { respond(envelope(body), HttpStatusCode.OK, headers) }).documents())
        }
        val linked = json.replace("null", "\"https://sandbox.asaas.com/onboarding/test\"")
        assertEquals(SaqzResult.Success(listOf(document.copy(onboardingUrl = "https://sandbox.asaas.com/onboarding/test"))),
            gateway(MockEngine { respond(envelope(linked), HttpStatusCode.OK, headers) }).documents())
        val duplicates = "[" + json.removeSurrounding("[", "]") + "," + json.removeSurrounding("[", "]") + "]"
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway(MockEngine { respond(envelope(duplicates), HttpStatusCode.OK, headers) }).documents())
    }
    @Test fun multipartUploadsActualBytesWithExactFieldTypeAndRequest() = runTest {
        var calls = 0
        val g = gateway(MockEngine { r ->
            calls++; assertEquals(HttpMethod.Post, r.method); assertEquals("/api/receivables/accounts/me/documents/doc", r.url.encodedPath)
            assertEquals("request", r.url.parameters["requestId"]); assertEquals("CUSTOM", r.url.parameters["type"])
            assertEquals("Bearer token", r.headers[HttpHeaders.Authorization])
            val body = r.body.toByteArray().decodeToString()
            assertTrue(body.contains("name=\"documentFile\"; filename=\"document\""))
            assertTrue(body.contains("Content-Type: application/pdf\r\n")); assertTrue(body.contains("%PDF-test"))
            respond(envelope("{}"), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(Unit), g.upload(document, "request", file)); assertEquals(1, calls)
    }
    @Test fun pendingMalformedLostAndUnmatchedUploadRemainUncertainWithoutRetry() = runTest {
        for ((status, body) in listOf(HttpStatusCode.Accepted to """{"error":"RESULT_PENDING","requestId":"request"}""",
            HttpStatusCode.OK to "{}", HttpStatusCode.OK to envelope("{}").replace("request", "other"),
            HttpStatusCode.ServiceUnavailable to "")) {
            var calls = 0
            val g = gateway(MockEngine { calls++; respond(body, status, headers) })
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.upload(document, "request", file)); assertEquals(1, calls)
        }
        var calls = 0
        val g = gateway(MockEngine { calls++; throw javaFreeIoFailure() })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.upload(document, "request", file)); assertEquals(1, calls)
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway(MockEngine {
            respond("""{"error":"RESULT_PENDING","requestId":"request"}""", HttpStatusCode.Accepted, headers)
        }).documents())
    }
    @Test fun invalidCommandsAndFilesNeverReachNetwork() = runTest {
        val g = gateway(MockEngine { error("Must not send") })
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.create("owner", ReceiptRegistrationCommand("request", "v1", false, legal)))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.create("owner", ReceiptRegistrationCommand("", "v1", true, legal)))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.recover("owner", ""))
        for (bad in listOf(ReceiptDocumentFile(byteArrayOf(), "image/png"), ReceiptDocumentFile(byteArrayOf(1), "text/plain"),
            ReceiptDocumentFile(ByteArray(ReceiptDocumentFile.MAX_DOCUMENT_BYTES + 1), "application/pdf")))
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.upload(document, "request", bad))
        for (bad in listOf(document.copy(onboardingUrl = "https://asaas.com/a"), document.copy(status = "APPROVED"),
            document.copy(id = "../other"))) assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.upload(bad, "request", file))
    }
    @Test fun typedHttpFailuresNeverBecomeAccountAbsenceOrSuccessfulUpload() = runTest {
        for ((status, expected) in listOf(400 to ReceiptError.INVALID, 401 to ReceiptError.SIGNED_OUT, 403 to ReceiptError.DENIED,
            404 to ReceiptError.DENIED, 409 to ReceiptError.STALE)) {
            val g = gateway(MockEngine { respond("", HttpStatusCode.fromValue(status)) })
            assertEquals(SaqzResult.Failure(expected), g.create("owner", command))
            assertEquals(SaqzResult.Failure(expected), g.recover("owner", "request"))
            assertEquals(SaqzResult.Failure(expected), g.documents())
            assertEquals(SaqzResult.Failure(expected), g.upload(document, "request", file))
        }
    }
    private fun gateway(engine: MockEngine) = KtorFinancialOnboardingGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider { override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token")) },
        object : SessionInvalidator { override fun invalidate() = Unit }))
    private fun javaFreeIoFailure() = io.ktor.utils.io.errors.IOException("Unavailable")
    private fun envelope(value: String) = """{"value":$value,"requestId":"request"}"""
    private val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val account = OwnedReceiptAccount("account", "owner", AccountRegistration.UNDER_REVIEW, false)
    private val accountJson = """{"id":"account","ownerUserId":"owner","registration":"UNDER_REVIEW","newOperationsEnabled":false}"""
    private val legal = ReceiptLegalRegistration("Titular", "owner@example.test", "12345678901", "11999999999", 250001,
        ReceiptLegalAddress("Rua Teste", "10", "Centro", "01001000"), "1990-01-01", null)
    private val command = ReceiptRegistrationCommand("request", "v1", true, legal)
    private val document = ReceiptDocument("doc", "CUSTOM", "PENDING", null, "Ata")
    private val file = ReceiptDocumentFile("%PDF-test".encodeToByteArray(), "application/pdf")
}
