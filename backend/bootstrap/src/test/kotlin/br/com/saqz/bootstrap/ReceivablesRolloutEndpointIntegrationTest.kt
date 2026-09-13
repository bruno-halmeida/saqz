package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminReceivablesRolloutController
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.jdbc.JdbcReceivablesRollout
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
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
import java.net.http.*
import java.time.Clock
import java.util.UUID
import kotlin.test.*

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReceivablesRolloutEndpointIntegrationTest.Config::class)
@ActiveProfiles("test")
@TestPropertySource(properties=["saqz.firebase.emulator.enabled=true"])
class ReceivablesRolloutEndpointIntegrationTest {
    @LocalServerPort private var port = 0
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var access: AdminAccess
    @Autowired private lateinit var rollout: JdbcReceivablesRollout
    private val client = HttpClient.newHttpClient()
    @Test fun `real HTTP authenticates every endpoint rejects malformed bodies and reevaluates admin revocation`() {
        val root = "/admin/receivables/rollout"
        access.enabled = true
        for (path in listOf(root,"$root/users/$USER","$root/history")) {
            val unauthenticated = call(path,token=null)
            assertEquals(401,unauthenticated.statusCode())
            assertEquals("UNAUTHORIZED",mapper.readTree(unauthenticated.body())["error"].stringValue())
            UUID.fromString(mapper.readTree(unauthenticated.body())["requestId"].stringValue())
            assertEquals(403,call(path,token="user").statusCode())
            assertEquals(200,call(path).statusCode())
        }
        assertEquals(401,call("/api/receivables/availability",token=null).statusCode())
        val initial = call("/api/receivables/availability?userId=$ADMIN",token="user")
        assertEquals(200,initial.statusCode())
        assertEquals("no-store",initial.headers().firstValue("Cache-Control").orElse(null))
        assertTrue(mapper.readTree(initial.body())["maintenanceAvailable"].booleanValue())
        assertEquals(404,call("$root/users/${UUID.randomUUID()}").statusCode())
        for (body in listOf("{}", "{", """{"requestId":"${UUID.randomUUID()}","reason":"valid reason","systems":[{"system":"BACKEND","mode":"OFF"},{"system":"MOBILE","mode":"OFF"}]}""")) {
            assertEquals(400,call(root,"PUT",body).statusCode())
        }
        val version = rollout.read().version
        val id = UUID.randomUUID()
        val body = """{"requestId":"$id","expectedVersion":$version,"reason":"Enable backend","systems":[{"system":"BACKEND","mode":"ALL_USERS"},{"system":"MOBILE","mode":"SELECTED_USERS"}]}"""
        for (invalid in listOf(body.replace("\"expectedVersion\":$version", "\"expectedVersion\":1.5"),
            body.replace("Enable backend","x"),body.replace("Enable backend","x".repeat(501)),
            body.replace("ALL_USERS","INVALID"),body.replace("\"system\":\"MOBILE\"","\"system\":\"BACKEND\""),
            body.replace("[{\"system\":\"BACKEND\",\"mode\":\"ALL_USERS\"},{\"system\":\"MOBILE\",\"mode\":\"SELECTED_USERS\"}]", "[null,null]"))) {
            val result = call(root,"PUT",invalid)
            assertEquals(400,result.statusCode(),result.body())
            assertEquals("INVALID_INPUT",mapper.readTree(result.body())["error"].stringValue())
        }
        assertEquals(403,call(root,"PUT",body,"user").statusCode())
        val written = call(root,"PUT",body)
        assertEquals(200,written.statusCode())
        assertEquals(written.body(),call(root,"PUT",body).body())
        assertEquals(409,call(root,"PUT",body.replace("Enable backend","Different reason")).statusCode())
        assertEquals(409,call(root,"PUT",body.replace(id.toString(),UUID.randomUUID().toString())).statusCode())
        val state = mapper.readTree(call("/api/receivables/availability",token="user").body())
        assertTrue(state["backendEnabled"].booleanValue())
        assertFalse(state["mobileEnabled"].booleanValue())
        val userVersion = rollout.user(USER).version
        val userBody = """{"requestId":"${UUID.randomUUID()}","expectedVersion":$userVersion,"reason":"Allow mobile","overrides":[{"system":"BACKEND","decision":"INHERIT"},{"system":"MOBILE","decision":"ALLOW"}],"accountOperationsEnabled":null}"""
        assertEquals(403,call("$root/users/$USER","PUT",userBody,"user").statusCode())
        assertEquals(200,call("$root/users/$USER","PUT",userBody).statusCode())
        assertTrue(mapper.readTree(call("/api/receivables/availability",token="user").body())["mobileEnabled"].booleanValue())
        assertEquals(400,call("$root/history?page=0").statusCode())
        assertEquals(400,call("$root/history?size=101").statusCode())
        access.enabled = false
        try {
            assertEquals(403,call(root).statusCode())
            assertEquals(403,call(root,"PUT",body).statusCode())
            assertEquals(403,call("$root/history").statusCode())
        } finally { access.enabled = true }
    }
    private fun call(path: String, method: String="GET", body: String?=null, token: String?="admin"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type","application/json").method(method,body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) request.header("Authorization","Bearer $token")
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString())
    }
    class AdminAccess : PlatformAdminLookup {
        var enabled = true
        override fun findBySubject(subject: String) = if (enabled && subject == "admin") PlatformAdminView(ADMIN,null,null) else null
    }
    @TestConfiguration(proxyBeanMethods=false) class Config {
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            if (it.value in setOf("admin","user")) TokenVerification.Verified(RequestIdentity(it.value)) else TokenVerification.Rejected
        }
        @Bean fun admins() = AdminAccess()
        @Bean fun rollout(): JdbcReceivablesRollout {
            val db = TestPostgres.migrated("classpath:db/migration",owner=this)
            return JdbcReceivablesRollout(db.dataSource,{ it == USER || it == ADMIN },Clock.systemUTC())
        }
        @Bean fun controller(admins: AdminAccess, rollout: JdbcReceivablesRollout) = AdminReceivablesRolloutController(admins,rollout,
            SubscriptionActorResolver { if (it.subject == "admin") ADMIN else USER })
    }
    companion object {
        val USER: UUID = UUID.fromString("419a657a-0b38-452b-944b-95c7485f8f71")
        val ADMIN: UUID = UUID.fromString("9826fe98-43c7-41f0-bba0-b9ace6aa74ce")
    }
}
