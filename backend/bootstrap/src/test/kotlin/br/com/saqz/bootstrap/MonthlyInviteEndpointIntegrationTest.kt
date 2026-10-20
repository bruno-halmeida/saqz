package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.adapter.input.http.MonthlyInviteController
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.InviteToken
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.application.monthlyinvite.GetOrRenewMonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.InvalidateMonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.ManageMonthlyInvites
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteDispatcher
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLinkRepository
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteMemberMatch
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteOccupancy
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteRepository
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitee
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteeStatus
import br.com.saqz.groups.application.monthlyinvite.NewMonthlyInvitee
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.BeforeEach
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
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CONV-010: contrato HTTP do convite de mensalistas (403 do atleta, 422, 409 e toque duplo). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MonthlyInviteEndpointIntegrationTest.MonthlyInviteTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class MonthlyInviteEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var state: MonthlyInviteHttpState
    @Autowired private lateinit var objectMapper: ObjectMapper
    private val groupId = UUID.randomUUID()

    @BeforeEach
    fun reset() = state.reset()

    @Test
    fun `dono cadastra o lote e o toque duplo devolve o mesmo resultado sem linhas repetidas`() {
        val body = """{"requestId":"${UUID.randomUUID()}","people":[{"name":"Ana","phone":"11988880001"},{"name":"Bia","phone":"11988880002"}]}"""

        val first = post("/monthly-invites", body)
        val second = post("/monthly-invites", body)

        assertEquals(200, first.statusCode())
        assertEquals(2, json(first)["enqueued"].intValue())
        assertEquals(URI("https://join.test/?saqz_invite=44DF8SA8").toString(), json(first)["link"]["url"].stringValue())
        assertEquals(200, second.statusCode())
        assertEquals(2, state.rows.size)
        assertEquals(1, state.enqueued.size)

        val list = json(get("/monthly-invites"))
        assertEquals("ACTIVE", list["link"]["state"].stringValue())
        assertEquals(listOf("11988880001", "11988880002"), items(list["invitees"]).map { it["phone"].stringValue() })
        assertEquals(listOf("QUEUED", "QUEUED"), items(list["invitees"]).map { it["status"].stringValue() })
        assertTrue(list["paused"].isNull)
        assertEquals(8000, list["defaultFee"]["monthlyFeeCents"].intValue())
    }

    @Test
    fun `atleta recebe 403 em todas as rotas`() {
        state.role = GroupRole.ATHLETE
        val id = UUID.randomUUID()

        listOf(
            get("/monthly-invites"),
            get("/monthly-invites/check?phone=11988880001"),
            post("/monthly-invites", """{"requestId":"${UUID.randomUUID()}","people":[{"name":"Ana","phone":"11988880001"}]}"""),
            post("/monthly-invites/link"),
            post("/monthly-invites/$id/resend"),
            post("/monthly-invites/resend-all"),
            delete("/monthly-invites/$id"),
            post("/monthly-invites/link/invalidate"),
        ).forEach { assertProblem(it, 403, "ACCESS_FORBIDDEN") }
    }

    @Test
    fun `pessoas recusadas voltam em 422 com a posicao e o motivo`() {
        state.members["+5511977770001"] = MonthlyInviteMemberMatch(UUID.randomUUID(), "Mel", AthleteMembershipType.MENSALISTA)
        val response = post(
            "/monthly-invites",
            """{"requestId":"${UUID.randomUUID()}","people":[{"name":"Ana","phone":"1198888"},{"name":"Mel","phone":"11977770001"}]}""",
        )

        assertProblem(response, 422, "INVALID_PEOPLE")
        assertEquals(
            listOf(0 to "INVALID_PHONE", 1 to "ALREADY_MENSALISTA"),
            items(json(response)["people"]).map { it["index"].intValue() to it["reason"].stringValue() },
        )
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `grupo sem mensalidade padrao responde 422 MONTHLY_FEE_REQUIRED`() {
        state.fee = null

        assertProblem(post("/monthly-invites/link"), 422, "MONTHLY_FEE_REQUIRED")
    }

    @Test
    fun `lote maior que as vagas responde 409 PLAN_LIMIT com os numeros`() {
        state.athleteLimit = 3
        state.memberCount = 2
        val response = post(
            "/monthly-invites",
            """{"requestId":"${UUID.randomUUID()}","people":[{"name":"Ana","phone":"11988880001"},{"name":"Bia","phone":"11988880002"}]}""",
        )

        assertProblem(response, 409, "PLAN_LIMIT")
        val body = json(response)
        assertEquals(listOf(3, 2, 2, 1), listOf("limit", "current", "requested", "free").map { body[it].intValue() })
    }

    @Test
    fun `checagem, remover e invalidar`() {
        assertEquals("FREE", json(get("/monthly-invites/check?phone=11988880001"))["result"].stringValue())
        assertProblem(get("/monthly-invites/check?phone=119"), 422, "VALIDATION_FAILED")
        assertEquals(204, delete("/monthly-invites/${UUID.randomUUID()}").statusCode())
        assertEquals(204, post("/monthly-invites/link/invalidate").statusCode())
        val link = post("/monthly-invites/link")
        assertEquals(200, link.statusCode())
        assertEquals(setOf("url", "expiresAt"), json(link).propertyNames().asSequence().toSet())
        assertProblem(post("/monthly-invites/${UUID.randomUUID()}/resend"), 404, "MONTHLY_INVITEE_NOT_FOUND")
        assertEquals(0, json(post("/monthly-invites/resend-all"))["enqueued"].intValue())
    }

    @Test
    fun `previa e resgate trazem inviteKind e membershipType so no link de mensalistas`() {
        val card = br.com.saqz.groups.application.invite.preview.PreviewInviteCard(
            "Vôlei", null, null, null, 3, emptyList(), "Dona", false, null, null,
            br.com.saqz.groups.application.invite.InviteKind.MONTHLY,
        )
        val monthly = objectMapper.readTree(
            objectMapper.writeValueAsString(br.com.saqz.groups.adapter.input.http.PreviewInviteResponse.from(card)),
        )
        val public = objectMapper.readTree(
            objectMapper.writeValueAsString(
                br.com.saqz.groups.adapter.input.http.PreviewInviteResponse.from(
                    card.copy(inviteKind = br.com.saqz.groups.application.invite.InviteKind.PUBLIC),
                ),
            ),
        )
        assertEquals("MONTHLY", monthly["inviteKind"].stringValue())
        assertTrue(!public.has("inviteKind"))

        val joined = br.com.saqz.groups.adapter.input.http.RedeemedInviteResponse(
            br.com.saqz.groups.adapter.input.http.RedeemedInviteStatus.JOINED, groupId, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA,
        )
        assertEquals("MENSALISTA", objectMapper.readTree(objectMapper.writeValueAsString(joined))["membershipType"].stringValue())
        assertTrue(!objectMapper.readTree(objectMapper.writeValueAsString(joined.copy(membershipType = null))).has("membershipType"))
    }

    private fun get(path: String) = send(request(path).GET())
    private fun post(path: String, body: String? = null) = send(
        request(path).header("Content-Type", "application/json")
            .POST(body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()),
    )
    private fun delete(path: String) = send(request(path).DELETE())
    private fun request(path: String) = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$groupId$path"))
        .header("Authorization", "Bearer monthly-token")
    private fun send(builder: HttpRequest.Builder) =
        HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun items(node: tools.jackson.databind.JsonNode) = (0 until node.size()).map { node[it] }
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class MonthlyInviteTestConfiguration {
        @Bean @Primary fun monthlyInviteVerifier() = MonthlyInviteVerifier()
        @Bean fun monthlyInviteSessionRepository() = MonthlyInviteSessionRepository()
        @Bean fun monthlyInviteBootstrap(repository: MonthlyInviteSessionRepository) = BootstrapSession(repository)
        @Bean fun monthlyInviteHttpState() = MonthlyInviteHttpState()

        @Bean
        fun monthlyInviteController(bootstrap: BootstrapSession, state: MonthlyInviteHttpState): MonthlyInviteController {
            val transaction = object : TransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            }
            val clock = Clock.fixed(NOW, ZoneOffset.UTC)
            val links = state.links
            val linkFactory = InviteLinkFactory { code -> URI("https://join.test/?saqz_invite=${code.value}") }
            val generator = SecureTokenGenerator { InviteToken(CODE, InviteTokenDigest.sha256(CODE)) }
            val manage = ManageMonthlyInvites(
                transaction, state, GroupAccessPolicy(), state, links,
                GetOrRenewMonthlyInviteLink(links, generator, linkFactory, clock), linkFactory, state,
                GroupWriteAccess { true }, state.dispatcher, clock,
            )
            return MonthlyInviteController(
                verifiedGroupActorResolver(bootstrap),
                manage,
                InvalidateMonthlyInviteLink(transaction, state, links, GroupAccessPolicy(), clock),
            )
        }

        companion object {
            val USER_ID: UUID = UUID.randomUUID()
            val CODE: InviteCode = InviteCode.from("44DF8SA8")
            val NOW: Instant = Instant.parse("2026-10-20T15:00:00Z")
        }
    }

    class MonthlyInviteVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("monthly-subject", "monthly@example.test", true, "Dona"),
        )
    }

    class MonthlyInviteSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(MonthlyInviteTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    /** Grupo, convidados, link e fila em memória, num objeto só para o teste mexer. */
    class MonthlyInviteHttpState : GroupReadRepository, MonthlyInviteRepository, SubscriptionLimits {
        var role: GroupRole? = GroupRole.OWNER
        var fee: Long? = 8_000
        var athleteLimit: Int? = null
        var memberCount = 0
        val rows = mutableListOf<MonthlyInvitee>()
        val members = mutableMapOf<String, MonthlyInviteMemberMatch>()
        val requests = mutableMapOf<UUID, Int>()
        val enqueued = mutableListOf<List<UUID>>()
        val links = object : MonthlyInviteLinkRepository {
            var link: MonthlyInviteLink? = null
            override fun lockAndFind(groupId: UUID) = link
            override fun find(groupId: UUID) = link
            override fun replaceCode(groupId: UUID, shortCode: String, expiresAt: Instant, createdByUserId: UUID, now: Instant) {
                link = MonthlyInviteLink(groupId, shortCode, expiresAt, null)
            }
            override fun renew(groupId: UUID, expiresAt: Instant, now: Instant) {
                link = link?.copy(expiresAt = expiresAt)
            }
            override fun invalidate(groupId: UUID, now: Instant) {
                link = link?.copy(shortCode = null, invalidatedAt = now)
            }
            override fun isShortCodeTaken(shortCode: String) = false
        }
        val dispatcher: MonthlyInviteDispatcher = object : MonthlyInviteDispatcher {
            override fun pausedUntil(): Instant? = null
            override fun enqueue(ids: List<UUID>) {
                enqueued += ids
            }
        }

        fun reset() {
            role = GroupRole.OWNER
            fee = 8_000
            athleteLimit = null
            memberCount = 0
            rows.clear()
            members.clear()
            requests.clear()
            enqueued.clear()
        }

        override fun find(key: GroupReadKey) = role?.let {
            GroupReadSnapshot(
                key.groupId, AccessName.from("Vôlei"), IanaTimeZone.from("UTC"), it, 1,
                financeDefaults = GroupFinanceDefaultsReadModel(null, fee, 10),
            )
        }

        override fun groupLimitFor(ownerId: UUID): Int? = null
        override fun athleteLimitFor(ownerId: UUID): Int? = athleteLimit

        override fun list(groupId: UUID) = rows.toList()
        override fun find(groupId: UUID, id: UUID) = rows.firstOrNull { it.id == id }
        override fun deleteMatchingMembers(groupId: UUID) = Unit
        override fun membersByPhone(groupId: UUID, phones: Collection<String>) = members.filterKeys { it in phones }
        override fun invitedByPhone(groupId: UUID, phones: Collection<String>) =
            rows.filter { it.phone.value in phones }.associateBy { it.phone.value }
        override fun hasRequest(groupId: UUID, requestId: UUID) = requestId in requests
        override fun countRequest(groupId: UUID, requestId: UUID) = requests[requestId] ?: 0
        override fun insert(
            groupId: UUID,
            people: List<NewMonthlyInvitee>,
            status: MonthlyInviteeStatus,
            createdByUserId: UUID,
            requestId: UUID,
            now: Instant,
        ) {
            requests[requestId] = people.size
            people.forEach { rows += MonthlyInvitee(it.id, groupId, it.name, it.phone, status, 0, null, createdByUserId, now) }
        }
        override fun requeue(groupId: UUID, ids: List<UUID>, status: MonthlyInviteeStatus) = Unit
        override fun delete(groupId: UUID, id: UUID) = rows.removeIf { it.id == id }
        override fun occupancy(groupId: UUID) = MonthlyInviteOccupancy(UUID.randomUUID(), memberCount)
    }
}
