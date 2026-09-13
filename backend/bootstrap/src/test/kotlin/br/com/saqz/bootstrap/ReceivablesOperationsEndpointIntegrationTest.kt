package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminReceivablesOperationsController
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReceivablesOperationsEndpointIntegrationTest.Config::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class ReceivablesOperationsEndpointIntegrationTest {
    @LocalServerPort private var port = 0
    private val client = HttpClient.newHttpClient()

    @Test
    fun `real HTTP protects queue and does not expose provider payload or PII`() {
        assertEquals(401, call("GET", "/admin/receivables/operations", null).statusCode())
        assertEquals(403, call("GET", "/admin/receivables/operations", "user").statusCode())

        val response = call("GET", "/admin/receivables/operations", "admin")

        assertEquals(200, response.statusCode())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null))
        assertContains(response.body(), UNKNOWN.toString())
        listOf("cpfCnpj", "email", "access_token", "providerPayload").forEach { assertFalse(response.body().contains(it)) }
        assertEquals(400, call("GET", "/admin/receivables/operations?page=0", "admin").statusCode())
        assertEquals(400, call("GET", "/admin/receivables/operations?size=101", "admin").statusCode())
    }

    @Test
    fun `real HTTP requires request id and returns uncertain without blind mutation`() {
        val before = Config.store.probes
        val denied = call("POST", "/admin/receivables/operations/$UNKNOWN/recovery", "user",
            """{"requestId":"${UUID.randomUUID()}","reason":"Consulta auditada"}""")
        assertEquals(403, denied.statusCode())
        assertEquals(before, Config.store.probes)
        assertEquals(400, call("POST", "/admin/receivables/operations/$UNKNOWN/recovery", "admin", "{}").statusCode())

        val response = call("POST", "/admin/receivables/operations/$UNKNOWN/recovery", "admin",
            """{"requestId":"${UUID.randomUUID()}","reason":"Consulta auditada"}""")

        assertEquals(503, response.statusCode())
        assertContains(response.body(), "STILL_UNKNOWN")
        assertEquals(before + 1, Config.store.probes)
    }

    @Test
    fun `withdraw recovery is forbidden before probe`() {
        val before = Config.store.probes
        val response = call("POST", "/admin/receivables/operations/$WITHDRAW/recovery", "admin",
            """{"requestId":"${UUID.randomUUID()}","reason":"Não executar saque"}""")
        assertEquals(409, response.statusCode())
        assertContains(response.body(), "NOT_RECOVERABLE")
        assertEquals(before, Config.store.probes)
    }

    private fun call(method: String, path: String, token: String?, body: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) request.header("Authorization", "Bearer $token")
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            if (it.value in setOf("admin", "user")) TokenVerification.Verified(RequestIdentity(it.value)) else TokenVerification.Rejected
        }
        @Bean fun admins(): PlatformAdminLookup = PlatformAdminLookup {
            if (it == "admin") PlatformAdminView(ADMIN, null, null) else null
        }
        @Bean fun operationStore(): FakeStore = store
        @Bean fun notices(): OperationalNotices = FakeNotices()
        @Bean fun clock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        @Bean fun recovery(store: FakeStore, clock: Clock) = RecoverOperationalFailure(store, OperationalRecoveryProbe {
            store.probes++
            OperationalRecoveryObservation(OperationStatus.UNKNOWN)
        }, clock)
        @Bean fun controller(admins: PlatformAdminLookup, store: FakeStore, recovery: RecoverOperationalFailure,
                            notices: OperationalNotices, clock: Clock) =
            AdminReceivablesOperationsController(admins, store, recovery, notices, clock)

        companion object { val store = FakeStore() }
    }

    class FakeStore : OperationalReceivablesStore {
        var probes = 0
        private val unknown = operation(UNKNOWN, OperationKind.CREATE_INSTRUMENT)
        private val withdrawal = operation(WITHDRAW, OperationKind.WITHDRAW)
        override fun list(status: OperationStatus?, kind: OperationKind?, page: Int, size: Int) =
            OperationalPage(listOf(unknown), page, size, 1)
        override fun detail(operationId: UUID) = when (operationId) {
            UNKNOWN -> OperationalOperationDetail(unknown, emptyList())
            WITHDRAW -> OperationalOperationDetail(withdrawal, emptyList())
            else -> null
        }
        override fun reserve(command: OperationalRecoveryCommand, now: Instant, leaseUntil: Instant): OperationalRecoveryReservation {
            val operation = detail(command.operationId)?.operation ?: return OperationalRecoveryReservation.NotFound
            return if (!operation.recoverable) OperationalRecoveryReservation.NotRecoverable
            else OperationalRecoveryReservation.Claimed(UUID.randomUUID(), operation)
        }
        override fun complete(command: OperationalRecoveryCommand, token: UUID, observation: OperationalRecoveryObservation, now: Instant) =
            OperationalRecoveryOutcome(command.requestId, command.operationId, OperationalRecoveryResult.STILL_UNKNOWN, OperationStatus.UNKNOWN)
        companion object {
            private fun operation(id: UUID, kind: OperationKind) = OperationalOperation(id, UUID.randomUUID(), UUID.randomUUID(), kind,
                UUID.randomUUID(), OperationStatus.UNKNOWN, 1, "PROVIDER_TIMEOUT", NOW.minusSeconds(60), NOW, NOW)
        }
    }

    class FakeNotices : OperationalNotices {
        override fun publish(command: PublishOperationalNotice, now: Instant) = error("unused")
        override fun list(audience: OperationalNoticeAudience?, page: Int, size: Int) = OperationalPage<OperationalNotice>(emptyList(), page, size, 0)
        override fun active(audience: OperationalNoticeAudience, at: Instant) = emptyList<OperationalNotice>()
    }

    companion object {
        val NOW: Instant = Instant.parse("2026-09-13T12:00:00Z")
        val ADMIN: UUID = UUID.fromString("9826fe98-43c7-41f0-bba0-b9ace6aa74ce")
        val UNKNOWN: UUID = UUID.fromString("419a657a-0b38-452b-944b-95c7485f8f71")
        val WITHDRAW: UUID = UUID.fromString("d8c63b2a-e633-4b40-ac97-0fdd734ff508")
    }
}
