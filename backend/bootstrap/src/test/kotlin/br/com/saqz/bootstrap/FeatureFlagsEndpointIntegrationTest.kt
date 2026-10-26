package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.FeaturesController
import br.com.saqz.access.adapter.output.jdbc.flags.JdbcFeatureFlagStore
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.access.application.flags.FeatureCatalog
import br.com.saqz.access.application.flags.FeatureChangeNotifier
import br.com.saqz.access.application.flags.FeatureFlags
import br.com.saqz.access.application.flags.FeatureKeys
import br.com.saqz.adminweb.http.AdminFeaturesController
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flags pela HTTP de verdade (DEB-119): `GET /api/features`, o adm, `FEATURE_DISABLED` e o site do Asaas. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FeatureFlagsEndpointIntegrationTest.Config::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true"])
class FeatureFlagsEndpointIntegrationTest {
    @LocalServerPort private var port = 0
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var notifier: RecordingNotifier
    @Autowired private lateinit var dataSource: DataSource
    private val client = HttpClient.newHttpClient()
    private val jdbc by lazy { JdbcClient.create(dataSource) }

    @Test
    fun `the day of the switch nothing changes and the answer has only the values of the person`() {
        val ios = json(call("/api/features", token = null, platform = "ios", version = "1.0.14"))
        assertEquals(setOf("chat", "purchases_asaas", "purchases_app_store", "purchases_google_play", "whatsapp"),
            ios["flags"].propertyNames().toSet())
        assertFalse(flag(ios, "chat"))
        assertFalse(flag(ios, "purchases_asaas"))
        assertTrue(flag(ios, "purchases_app_store"))
        assertFalse(flag(ios, "purchases_google_play"))
        assertTrue(flag(ios, "whatsapp"), "WhatsApp nasce como a propriedade (ligada neste contexto)")
        assertEquals("IOS", ios["platform"].stringValue())
        assertTrue(ios["appVersion"]["minimum"].isNull)

        val android = call("/api/features", token = "athlete", platform = "android", version = "1.0.14")
        assertEquals(200, android.statusCode())
        assertEquals("no-store", android.headers().firstValue("Cache-Control").orElse(null))
        assertTrue(flag(json(android), "purchases_google_play"))
        assertFalse(flag(json(android), "purchases_app_store"))
        for (secret in listOf("alwaysOn", "alwaysOff", "percentage", "audience", "testadores", "revisores")) {
            assertFalse(android.body().contains(secret), "a resposta não traz $secret")
        }

        val legacy = json(call("/api/features", token = "athlete", platform = null))
        assertTrue(legacy["platform"].isNull)
        assertTrue(legacy["appVersion"].isNull)
    }

    @Test
    fun `admin routes need a session and an admin, a reason and the expected version`() {
        assertEquals(401, call("/admin/features", token = null).statusCode())
        assertEquals(403, call("/admin/features", token = "owner").statusCode())
        assertEquals(403, call("/admin/features/chat", "PUT", ruleBody(1, "x", """{"state":"ALL"}"""), token = "owner").statusCode())
        val list = json(call("/admin/features"))
        assertEquals(5, list["flags"].size())

        val version = json(call("/admin/features/chat"))["version"].longValue()
        val noReason = call("/admin/features/chat", "PUT", ruleBody(version, "  ", """{"state":"ALL"}"""))
        assertEquals(400, noReason.statusCode())
        assertEquals("INVALID_INPUT", json(noReason)["error"].stringValue())

        val signals = notifier.signals.size
        val first = call("/admin/features/chat", "PUT", ruleBody(version, "Liga para os donos",
            """{"state":"AUDIENCE","audience":{"roles":["OWNER"]}}"""))
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(version + 1, json(first)["version"].longValue())
        assertEquals(signals + 1, notifier.signals.size, "cada publicação avisa o Remote Config")
        val second = call("/admin/features/chat", "PUT", ruleBody(version, "Outra pessoa", """{"state":"ALL"}"""))
        assertEquals(409, second.statusCode())
        assertEquals("AUDIENCE", json(call("/admin/features/chat"))["rule"]["state"].stringValue())

        assertTrue(flag(json(call("/api/features", token = "owner", platform = "android")), "chat"))
        assertFalse(flag(json(call("/api/features", token = "athlete", platform = "android")), "chat"))
        assertFalse(flag(json(call("/api/features", token = null, platform = "android")), "chat"), "antes do login, público não vale")

        // Volta para a versão inicial: a regra antiga vale de novo e o histórico ganha uma linha nova.
        val history = json(call("/admin/features/history?type=FLAG&key=chat"))
        val initial = history["items"].toList().single { it["version"].longValue() == 1L }
        assertTrue(initial["actorUserId"].isNull)
        val current = json(call("/admin/features/chat"))["version"].longValue()
        val reverted = call("/admin/features/chat/revert", "POST",
            """{"historyId":"${initial["id"].stringValue()}","expectedVersion":$current,"reason":"Volta ao valor inicial"}""")
        assertEquals(200, reverted.statusCode(), reverted.body())
        assertEquals("OFF", json(reverted)["rule"]["state"].stringValue())
        val after = json(call("/admin/features/history?type=FLAG&key=chat"))
        assertEquals(history["total"].longValue() + 1, after["total"].longValue())
        val last = after["items"][0]
        assertEquals("Volta ao valor inicial", last["reason"].stringValue())
        assertEquals("Admin", last["actorName"].stringValue())
        assertEquals("AUDIENCE", last["before"]["state"].stringValue())
    }

    @Test
    fun `history cannot be updated or deleted`() {
        assertFailsWith<Exception> { jdbc.sql("UPDATE feature_history SET reason = 'apagado'").update() }
        assertFailsWith<Exception> { jdbc.sql("DELETE FROM feature_history").update() }
        assertFailsWith<Exception> { jdbc.sql("TRUNCATE feature_history").update() }
    }

    @Test
    fun `named groups feed the lists and the same person cannot be in both lists`() {
        val created = call("/admin/feature-groups/testadores", "PUT",
            """{"expectedVersion":0,"reason":"Grupo de QA","members":["$ATHLETE"]}""")
        assertEquals(200, created.statusCode(), created.body())
        assertEquals(409, call("/admin/feature-groups/testadores", "PUT",
            """{"expectedVersion":0,"reason":"De novo","members":[]}""").statusCode())
        assertTrue(json(call("/admin/feature-groups"))["groups"].toList().any { it["name"].stringValue() == "revisores" })
        assertEquals(ATHLETE.toString(), json(call("/admin/feature-groups/users?q=atleta@saqz"))["users"][0]["userId"].stringValue())
        assertEquals(ATHLETE.toString(), json(call("/admin/feature-groups/users?q=11 98888-0002"))["users"][0]["userId"].stringValue())

        val version = json(call("/admin/features/purchases_asaas"))["version"].longValue()
        val conflict = call("/admin/features/purchases_asaas", "PUT", ruleBody(version, "Testadores",
            """{"state":"OFF","alwaysOn":{"groups":["testadores"]},"alwaysOff":{"users":["$ATHLETE"]}}"""))
        assertEquals(400, conflict.statusCode())
        val ok = call("/admin/features/purchases_asaas", "PUT", ruleBody(version, "Testadores",
            """{"state":"OFF","alwaysOn":{"groups":["testadores"]},"ios":false}"""))
        assertEquals(200, ok.statusCode(), ok.body())
        assertTrue(flag(json(call("/api/features", token = "athlete", platform = "android")), "purchases_asaas"))
        assertFalse(flag(json(call("/api/features", token = "athlete", platform = "ios")), "purchases_asaas"), "interruptor vence a lista")
        assertFalse(flag(json(call("/api/features", token = "owner", platform = "android")), "purchases_asaas"))

        // Um grupo não pode pôr nas duas listas alguém que já está na outra.
        val groupVersion = json(call("/admin/feature-groups"))["groups"].toList().single { it["name"].stringValue() == "testadores" }["version"].longValue()
        val back = json(call("/admin/features/purchases_asaas"))["version"].longValue()
        assertEquals(200, call("/admin/features/purchases_asaas", "PUT", ruleBody(back, "Dono fora",
            """{"state":"OFF","alwaysOn":{"groups":["testadores"]},"alwaysOff":{"users":["$OWNER"]}}""")).statusCode())
        assertEquals(400, call("/admin/feature-groups/testadores", "PUT",
            """{"expectedVersion":$groupVersion,"reason":"Põe o dono","members":["$ATHLETE","$OWNER"]}""").statusCode())
        val reset = json(call("/admin/features/purchases_asaas"))["version"].longValue()
        assertEquals(200, call("/admin/features/purchases_asaas", "PUT", ruleBody(reset, "Volta", """{"state":"OFF"}""")).statusCode())
    }

    @Test
    fun `minimum and recommended versions per platform reach the app before login`() {
        val current = json(call("/admin/app-versions"))
        assertEquals(400, call("/admin/app-versions", "PUT",
            """{"expectedVersion":${current["version"].longValue()},"reason":"Versão","android":{"minimum":"1.0"},"ios":{}}""").statusCode())
        val saved = call("/admin/app-versions", "PUT",
            """{"expectedVersion":${current["version"].longValue()},"reason":"Sobe a mínima do Android","android":{"minimum":"1.0.13","recommended":"1.0.14"},"ios":{"minimum":null,"recommended":""}}""")
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals(409, call("/admin/app-versions", "PUT",
            """{"expectedVersion":${current["version"].longValue()},"reason":"Atrasado","android":{},"ios":{}}""").statusCode())
        val android = json(call("/api/features", token = null, platform = "android", version = "1.0.12"))
        assertEquals("1.0.13", android["appVersion"]["minimum"].stringValue())
        assertEquals("1.0.14", android["appVersion"]["recommended"].stringValue())
        assertTrue(json(call("/api/features", token = null, platform = "ios"))["appVersion"]["minimum"].isNull)
        val restore = json(saved)["version"].longValue()
        assertEquals(200, call("/admin/app-versions", "PUT", """{"expectedVersion":$restore,"reason":"Zera","android":{},"ios":{}}""").statusCode())
    }

    @Test
    fun `backend refuses new actions with FEATURE_DISABLED but the site keeps selling and the stores keep validating`() {
        // Assinatura pelo Asaas desligada: o app é barrado, o site (sem cabeçalho do app) passa.
        val fromApp = call("/subscriptions", "POST", "{}", token = "owner", platform = "android")
        assertEquals(403, fromApp.statusCode())
        assertTrue(fromApp.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"))
        assertEquals("FEATURE_DISABLED", json(fromApp)["code"].stringValue())
        assertEquals("FEATURE_DISABLED", json(call("/subscriptions", "POST", "{}", token = "owner", platform = "ios"))["code"].stringValue())
        for (site in listOf(call("/subscriptions", "POST", "{}", token = "owner", platform = null),
            call("/subscriptions", "POST", "{}", token = "owner", platform = "web"))) {
            assertFalse(site.body().contains("FEATURE_DISABLED"), "o site continua vendendo: ${site.statusCode()} ${site.body()}")
        }
        // Quem já assina continua cancelando pelo app.
        assertFalse(call("/subscriptions/me/cancel", "POST", "{}", token = "owner", platform = "android").body().contains("FEATURE_DISABLED"))

        // Compra da loja desligada: só esconde o botão; a validação nunca é barrada.
        val appStore = json(call("/admin/features/purchases_app_store"))["version"].longValue()
        assertEquals(200, call("/admin/features/purchases_app_store", "PUT", ruleBody(appStore, "Desliga a App Store", """{"state":"OFF"}""")).statusCode())
        assertFalse(call("/subscriptions/app-store/transactions", "POST", "{}", token = "owner", platform = "ios").body().contains("FEATURE_DISABLED"))
        assertFalse(call("/subscriptions/google-play/purchases", "POST", "{}", token = "owner", platform = "android").body().contains("FEATURE_DISABLED"))
        val back = json(call("/admin/features/purchases_app_store"))["version"].longValue()
        assertEquals(200, call("/admin/features/purchases_app_store", "PUT", ruleBody(back, "Religa", """{"state":"ALL","android":false}""")).statusCode())

        // WhatsApp desligado: vincular um grupo novo é barrado, inclusive para o app antigo sem cabeçalho.
        val whatsApp = json(call("/admin/features/whatsapp"))["version"].longValue()
        assertEquals(200, call("/admin/features/whatsapp", "PUT", ruleBody(whatsApp, "Desliga o WhatsApp", """{"state":"OFF"}""")).statusCode())
        val binding = call("/api/groups/$GROUP/whatsapp-binding", "PUT", """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""", token = "owner", platform = null)
        assertEquals(403, binding.statusCode())
        assertEquals("FEATURE_DISABLED", json(binding)["code"].stringValue())
        val on = json(call("/admin/features/whatsapp"))["version"].longValue()
        assertEquals(200, call("/admin/features/whatsapp", "PUT", ruleBody(on, "Religa o WhatsApp", """{"state":"ALL"}""")).statusCode())
        assertNotEquals("FEATURE_DISABLED", runCatching {
            json(call("/api/groups/$GROUP/whatsapp-binding", "PUT", """{"inviteLink":"x"}""", token = "owner"))["code"]?.stringValue()
        }.getOrNull())
    }

    @Test
    fun `old flag alerts climb at 30, 60 and 90 days, go to the top and can be turned off with a reason`() {
        val key = "purchases_google_play"
        val version = json(call("/admin/features/$key"))["version"].longValue()
        assertEquals(200, call("/admin/features/$key", "PUT", ruleBody(version, "Cem por cento", """{"state":"ALL"}""")).statusCode())
        for ((days, alert) in listOf(10 to null, 31 to "YELLOW", 61 to "ORANGE", 91 to "RED")) {
            jdbc.sql("UPDATE feature_flags SET full_rollout_since = now() - make_interval(days => :days) WHERE flag_key = :key")
                .param("days", days).param("key", key).update()
            val flags = json(call("/admin/features"))["flags"]
            if (alert == null) {
                assertTrue(flags.toList().single { it["key"].stringValue() == key }["alert"].isNull)
            } else {
                assertEquals(key, flags[0]["key"].stringValue(), "com alerta, no topo")
                assertEquals(alert, flags[0]["alert"].stringValue())
            }
        }
        val current = json(call("/admin/features/$key"))["version"].longValue()
        val silenced = call("/admin/features/$key", "PUT", ruleBody(current, "Interruptor permanente", """{"state":"ALL","alertDisabled":true}"""))
        assertTrue(json(silenced)["alert"].isNull)
        assertNotNullSince(key)
        val iosOff = json(silenced)["version"].longValue()
        val partial = call("/admin/features/$key", "PUT", ruleBody(iosOff, "Só Android", """{"state":"ALL","ios":false}"""))
        assertTrue(json(partial)["alert"].isNull)
        assertTrue(json(partial)["fullRolloutSince"].isNull, "deixou de valer 100%: a contagem zera")
    }

    @Test
    fun `whatsapp starts from the environment property with a history line`() {
        val db = TestPostgres.migrated("classpath:db/migration", owner = this)
        val store = JdbcFeatureFlagStore(db.dataSource)
        val flags = FeatureFlags(FeatureCatalog(whatsAppConfigured = false), store, FeatureChangeNotifier { }, Clock.systemUTC())
        assertEquals(listOf("chat", "purchases_asaas", "purchases_app_store", "purchases_google_play", "whatsapp"), flags.ensureCatalog())
        assertEquals(emptyList(), flags.ensureCatalog(), "a segunda subida não mexe na regra gravada")
        assertFalse(flags.enabledOnServer(FeatureKeys.WHATSAPP))
        val line = flags.history(null, "whatsapp", 1, 10).items.single()
        assertContains(line.reason, "variável de ambiente")
        assertNull(line.actorUserId)
        // Já gravada, a regra não volta para a variável: ligar a propriedade não liga a flag.
        val again = FeatureFlags(FeatureCatalog(whatsAppConfigured = true), store, FeatureChangeNotifier { }, Clock.systemUTC())
        assertEquals(emptyList(), again.ensureCatalog())
        assertFalse(again.enabledOnServer(FeatureKeys.WHATSAPP))
    }

    private fun assertNotNullSince(key: String) =
        assertTrue(json(call("/admin/features/$key"))["fullRolloutSince"].isString, "alerta desligado não zera a contagem")

    private fun ruleBody(version: Long, reason: String, rule: String) =
        """{"requestId":"${UUID.randomUUID()}","expectedVersion":$version,"reason":"$reason","rule":$rule}"""

    private fun flag(body: JsonNode, key: String) = body["flags"][key]["enabled"].booleanValue()
    private fun json(response: HttpResponse<String>): JsonNode = mapper.readTree(response.body())

    private fun call(path: String, method: String = "GET", body: String? = null, token: String? = "admin",
        platform: String? = null, version: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port${path.replace(" ", "%20")}"))
            .header("Content-Type", "application/json")
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) request.header("Authorization", "Bearer $token")
        if (platform != null) request.header("X-Saqz-Platform", platform)
        if (version != null) request.header("X-Saqz-App-Version", version)
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    class RecordingNotifier : FeatureChangeNotifier {
        val signals = java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun published(signal: String) { signals += signal }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean fun featureTestDataSource(): DataSource {
            val dataSource = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
            val jdbc = JdbcClient.create(dataSource)
            for ((id, subject, email, phone) in listOf(
                listOf(ADMIN, "admin", "admin@saqz.app", null),
                listOf(OWNER, "owner", "dono@saqz.app", "+5511988880001"),
                listOf(ATHLETE, "athlete", "atleta@saqz.app", "+5511988880002"),
            )) {
                jdbc.sql("""INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, phone, created_at, updated_at)
                    VALUES (:id, :subject, :email, true, :name, :phone, now(), now())""")
                    .param("id", id).param("subject", subject).param("email", email)
                    .param("name", if (subject == "admin") "Admin" else "Pessoa $subject").param("phone", phone).update()
            }
            jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
                VALUES (:id, :owner, :key, 'Vôlei', 'UTC', now(), now())""").param("id", GROUP).param("owner", OWNER).param("key", UUID.randomUUID()).update()
            jdbc.sql("""INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at)
                VALUES (:group, :user, 'ATHLETE', now(), now())""").param("group", GROUP).param("user", ATHLETE).update()
            return dataSource
        }

        @Bean @Primary fun featureTestVerifier() = VerifyRequestIdentity {
            if (it.value in setOf("admin", "owner", "athlete")) TokenVerification.Verified(RequestIdentity(it.value, emailVerified = true))
            else TokenVerification.Rejected
        }
        @Bean fun featureTestAdmins() = PlatformAdminLookup { if (it == "admin") PlatformAdminView(ADMIN, "admin@saqz.app", "Admin") else null }
        @Bean fun featureTestNotifier() = RecordingNotifier()
        @Bean fun featureTestFlags(dataSource: DataSource, notifier: RecordingNotifier) =
            FeatureFlags(FeatureCatalog(whatsAppConfigured = true), JdbcFeatureFlagStore(dataSource), notifier, Clock.systemUTC())
                .also { it.ensureCatalog() }
        @Bean fun featureTestController(flags: FeatureFlags) = FeaturesController(flags)
        @Bean fun featureTestAdminController(flags: FeatureFlags, admins: PlatformAdminLookup) = AdminFeaturesController(flags, admins)
    }

    companion object {
        val ADMIN: UUID = UUID.fromString("0b7a8c62-34a9-4e70-8d0e-6f1e3c9b2a01")
        val OWNER: UUID = UUID.fromString("0b7a8c62-34a9-4e70-8d0e-6f1e3c9b2a02")
        val ATHLETE: UUID = UUID.fromString("0b7a8c62-34a9-4e70-8d0e-6f1e3c9b2a03")
        val GROUP: UUID = UUID.fromString("0b7a8c62-34a9-4e70-8d0e-6f1e3c9b2a10")
    }
}
