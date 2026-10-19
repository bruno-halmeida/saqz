package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminCourtesiesController
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.ActiveCourtesy
import br.com.saqz.subscriptions.application.CourtesyCandidate
import br.com.saqz.subscriptions.application.PlanCourtesies
import br.com.saqz.subscriptions.application.PlanCourtesyRepository
import br.com.saqz.subscriptions.domain.PlanCourtesy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rotas `/admin` da cortesia de plano (DEB-097): guarda, contrato JSON, dry-run e lote. O SQL tem teste no módulo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminCourtesiesEndpointIntegrationTest.CourtesiesTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminCourtesiesEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var repository: FakeCourtesyRepository

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `usuario comum recebe 403 em todas as rotas e nada e gravado`() {
        val before = repository.rows.size
        listOf(
            "GET" to "/admin/users/$OWNER/courtesy",
            "POST" to "/admin/users/$OWNER/courtesy",
            "POST" to "/admin/users/$OWNER/courtesy/revoke",
            "GET" to "/admin/courtesies",
            "POST" to "/admin/courtesies/batch",
        ).forEach { (method, path) ->
            assertEquals(403, exchange(method, path, "user-token", """{"plan":"TITULAR","reason":"x","dryRun":false,"ownerIds":["$OWNER"]}""").statusCode(), path)
        }
        assertEquals(before, repository.rows.size)
    }

    @Test
    fun `dar, consultar com historico, alterar e revogar`() {
        val owner = repository.newUser()
        val granted = exchange("POST", "/admin/users/$owner/courtesy", "admin-token", """{"plan":"ORGANIZADOR","reason":"Piloto"}""")
        assertEquals(200, granted.statusCode())
        val body = objectMapper.readTree(granted.body())
        assertEquals("ORGANIZADOR", body["plan"].stringValue())
        assertTrue(body["endsAt"].isNull)
        assertEquals("Piloto", body["reason"].stringValue())
        assertEquals(ADMIN_ID.toString(), body["grantedBy"].stringValue())

        val end = Instant.now().plusSeconds(86_400).toString()
        assertEquals(200, exchange("POST", "/admin/users/$owner/courtesy", "admin-token", """{"plan":"ILIMITADO","endsAt":"$end","reason":"Troca"}""").statusCode())
        val view = objectMapper.readTree(exchange("GET", "/admin/users/$owner/courtesy", "admin-token").body())
        assertEquals("ILIMITADO", view["active"]["plan"].stringValue())
        assertEquals(2, view["history"].size())
        assertFalse(view["history"][1]["revokedAt"].isNull)

        assertEquals(204, exchange("POST", "/admin/users/$owner/courtesy/revoke", "admin-token", """{"reason":"fim"}""").statusCode())
        assertEquals(409, exchange("POST", "/admin/users/$owner/courtesy/revoke", "admin-token").statusCode())
        val after = objectMapper.readTree(exchange("GET", "/admin/users/$owner/courtesy", "admin-token").body())
        assertTrue(after["active"].isNull)
        assertEquals(ADMIN_ID.toString(), after["history"][0]["revokedBy"].stringValue())
    }

    @Test
    fun `entrada invalida e usuario inexistente`() {
        val owner = repository.newUser()
        assertEquals(400, exchange("POST", "/admin/users/$owner/courtesy", "admin-token", """{"plan":"FREE","reason":"x"}""").statusCode())
        assertEquals(400, exchange("POST", "/admin/users/$owner/courtesy", "admin-token", """{"plan":"TITULAR"}""").statusCode())
        assertEquals(400, exchange("POST", "/admin/users/$owner/courtesy", "admin-token", """{"plan":"TITULAR","reason":" "}""").statusCode())
        assertEquals(400, exchange("POST", "/admin/users/$owner/courtesy", "admin-token", """{"plan":"TITULAR","endsAt":"2020-01-01T00:00:00Z","reason":"x"}""").statusCode())
        assertEquals(404, exchange("POST", "/admin/users/${UUID.randomUUID()}/courtesy", "admin-token", """{"plan":"TITULAR","reason":"x"}""").statusCode())
        assertEquals(404, exchange("GET", "/admin/users/${UUID.randomUUID()}/courtesy", "admin-token").statusCode())
        assertEquals(404, exchange("POST", "/admin/users/${UUID.randomUUID()}/courtesy/revoke", "admin-token").statusCode())
    }

    @Test
    fun `lote em dry-run so devolve a lista de conferencia e a confirmacao grava so os ids enviados`() {
        val a = repository.newUser(eligible = true)
        val b = repository.newUser(eligible = true)
        val before = repository.rows.size

        val preview = objectMapper.readTree(
            exchange("POST", "/admin/courtesies/batch", "admin-token", """{"plan":"ORGANIZADOR","reason":"Fase de testes","dryRun":true}""").body(),
        )
        assertTrue(preview["dryRun"].booleanValue())
        assertTrue(preview["results"].isNull)
        assertEquals(setOf(a.toString(), b.toString()), preview["candidates"].items().map { it["ownerUserId"].stringValue() }.toSet())
        assertEquals(setOf("ownerUserId", "displayName", "email", "groups", "effectivePlan", "hasCourtesy"), preview["candidates"][0].propertyNames().toSet())
        assertEquals(before, repository.rows.size)

        // Sem `dryRun` o padrão também é não gravar.
        assertEquals(200, exchange("POST", "/admin/courtesies/batch", "admin-token", """{"plan":"ORGANIZADOR","reason":"Fase de testes"}""").statusCode())
        assertEquals(before, repository.rows.size)

        val stranger = UUID.randomUUID()
        val confirm = objectMapper.readTree(
            exchange("POST", "/admin/courtesies/batch", "admin-token", """{"plan":"ORGANIZADOR","reason":"Fase de testes","dryRun":false,"ownerIds":["$a","$stranger"]}""").body(),
        )
        assertFalse(confirm["dryRun"].booleanValue())
        assertEquals(listOf(true, false), confirm["results"].items().map { it["granted"].booleanValue() })
        assertEquals("NOT_ELIGIBLE", confirm["results"][1]["error"].stringValue())
        assertEquals(before + 1, repository.rows.size)
        assertTrue(repository.rows.none { it.ownerUserId == b })
        assertEquals(400, exchange("POST", "/admin/courtesies/batch", "admin-token", """{"plan":"ORGANIZADOR","reason":"x","dryRun":false}""").statusCode())
        assertEquals(400, exchange("POST", "/admin/courtesies/batch", "admin-token", """{"plan":"ORGANIZADOR","reason":"","dryRun":true}""").statusCode())
    }

    @Test
    fun `lista de ativas traz dono, e-mail, plano, fim e motivo`() {
        val owner = repository.newUser()
        exchange("POST", "/admin/users/$owner/courtesy", "admin-token", """{"plan":"TITULAR","reason":"Parceiro"}""")
        val list = objectMapper.readTree(exchange("GET", "/admin/courtesies", "admin-token").body())
        val entry = list.items().first { it["ownerUserId"].stringValue() == owner.toString() }
        assertEquals("$owner@saqz.test", entry["email"].stringValue())
        assertEquals("TITULAR", entry["plan"].stringValue())
        assertEquals("Parceiro", entry["reason"].stringValue())
        assertTrue(entry["endsAt"].isNull)
    }

    private fun tools.jackson.databind.JsonNode.items() = (0 until size()).map { get(it) }

    private fun exchange(method: String, path: String, token: String, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .header("Authorization", "Bearer $token")
        if (body != null) builder.header("Content-Type", "application/json")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class FakeCourtesyRepository : PlanCourtesyRepository {
        val rows = java.util.concurrent.CopyOnWriteArrayList<PlanCourtesy>()
        private val users = java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()
        private val eligible = java.util.concurrent.CopyOnWriteArrayList<UUID>()

        fun newUser(eligible: Boolean = false): UUID = UUID.randomUUID().also {
            users += it
            if (eligible) this.eligible += it
        }

        override fun findActive(ownerUserId: UUID, now: Instant) = rows.lastOrNull { it.ownerUserId == ownerUserId && it.isActiveAt(now) }
        override fun history(ownerUserId: UUID) = rows.filter { it.ownerUserId == ownerUserId }.reversed()
        override fun userExists(userId: UUID) = userId in users

        @Synchronized
        override fun grant(courtesy: PlanCourtesy): PlanCourtesy {
            rows.indices.forEach { i ->
                val row = rows[i]
                if (row.ownerUserId == courtesy.ownerUserId && row.revokedAt == null) rows[i] = row.copy(revokedAt = courtesy.createdAt, revokedBy = courtesy.grantedBy)
            }
            rows += courtesy
            return courtesy
        }

        @Synchronized
        override fun revoke(ownerUserId: UUID, by: UUID, reason: String?, now: Instant): Boolean {
            val i = rows.indexOfFirst { it.ownerUserId == ownerUserId && it.isActiveAt(now) }
            if (i < 0) return false
            rows[i] = rows[i].copy(revokedAt = now, revokedBy = by, revokedReason = reason)
            return true
        }

        override fun listActive(now: Instant) = rows.filter { it.isActiveAt(now) }.map { ActiveCourtesy(it, "Dono", "${it.ownerUserId}@saqz.test") }
        override fun eligibleOwners(now: Instant) = eligible.map { CourtesyCandidate(it, "Dono", "$it@saqz.test", listOf("Grupo"), null, false) }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class CourtesiesTestConfiguration {
        @Bean
        @Primary
        fun courtesiesVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(RequestIdentity("admin-subject", "admin@saqz.test", true, "Ana Admin"))
                "user-token" -> TokenVerification.Verified(RequestIdentity("user-subject", "user@saqz.test", true, "Uso Comum"))
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun courtesiesAdminLookup(): PlatformAdminLookup = PlatformAdminLookup { subject ->
            if (subject == "admin-subject") PlatformAdminView(ADMIN_ID, "admin@saqz.test", "Ana Admin") else null
        }

        @Bean
        fun fakeCourtesyRepository() = FakeCourtesyRepository()

        @Bean
        fun adminCourtesiesController(repository: FakeCourtesyRepository, lookup: PlatformAdminLookup) =
            AdminCourtesiesController(PlanCourtesies(repository, { null }, Clock.systemUTC()), lookup)
    }

    private companion object {
        val OWNER: UUID = UUID.fromString("3d2a9c4e-1f2b-4a5c-8d6e-0f1a2b3c4d5e")
        val ADMIN_ID: UUID = UUID.fromString("aa11bb22-cc33-4d44-9e55-ff6677889900")
    }
}
