package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.adapter.input.http.AthleteController
import br.com.saqz.groups.application.athlete.AthleteMembership
import br.com.saqz.groups.application.athlete.AthleteRepository
import br.com.saqz.groups.application.athlete.AthleteRosterEntry
import br.com.saqz.groups.application.athlete.AthleteRosterFilter
import br.com.saqz.groups.application.athlete.AthleteRosterRepository
import br.com.saqz.groups.application.athlete.FinancialStatus
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfile
import br.com.saqz.groups.application.athlete.GetAthleteStats
import br.com.saqz.groups.application.athlete.AthleteStatsAggregate
import br.com.saqz.groups.application.athlete.AthleteStatsRepository
import br.com.saqz.groups.application.athlete.ListAthletes
import br.com.saqz.groups.application.athlete.OwnAthleteMembership
import br.com.saqz.groups.application.athlete.OwnAthleteProfile
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.athlete.UpdateAthlete
import br.com.saqz.groups.application.athlete.UpdateAthleteCommand
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfile
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.application.read.GroupProfileReadModel
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AthleteEndpointIntegrationTest.AthleteTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AthleteEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var read: RecordingAthleteGroupReadRepository
    @Autowired private lateinit var athletes: RecordingAthleteRepository
    @Autowired private lateinit var roster: RecordingAthleteRosterRepository
    @Autowired private lateinit var stats: RecordingAthleteStatsRepository
    @Autowired private lateinit var objectMapper: ObjectMapper
    private val groupId = UUID.randomUUID()
    private val actorId = AthleteTestConfiguration.USER_ID
    private val memberId = UUID.randomUUID()
    private val adminsOnlyId = UUID.randomUUID()

    @BeforeEach
    fun reset() {
        read.role = GroupRole.OWNER
        read.modality = GroupModality.COURT_VOLLEYBALL
        athletes.reset(
            listOf(
                athlete(actorId, "Owner Person", GroupRole.OWNER),
                athlete(memberId, "Member Person", GroupRole.ATHLETE),
            ),
        )
        roster.entries = listOf(
            AthleteRosterEntry(
                memberId, AccessName.from("Member Person"), "+5511987654321",
                AthletePosition.PONTA, AthleteMembershipType.AVULSO, true, FinancialStatus.PENDENTE,
            ),
        )
        roster.calls = 0
        roster.profile = OwnAthleteProfile(
            actorId, AccessName.from("Owner Person"), "+5511987654321",
            listOf(
                OwnAthleteMembership(
                    groupId, AccessName.from("Training Group"), GroupRole.OWNER,
                    AthletePosition.LEVANTADOR, AthleteMembershipType.MENSALISTA, true,
                    nickname = "Raio",
                    secondaryPosition = AthletePosition.CENTRAL,
                    level = br.com.saqz.groups.domain.AthleteLevel.AVANCADO,
                    preferredSide = null,
                    heightCm = 185,
                    monthlyFeeCents = 12500,
                    monthlyDueDay = 10,
                    joinedAt = java.time.Instant.parse("2026-08-01T12:00:00Z"),
                ),
            ),
        )
        stats.values = AthleteStatsAggregate(games = 3, eligibleGames = 3, absences = 1)
    }

    @Test
    fun `organizer lists roster with phone and financial status`() {
        val response = getRoster(groupId)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(1, body["athletes"].size())
        val entry = body["athletes"][0]
        assertEquals("+5511987654321", entry["phone"].stringValue())
        assertEquals("PENDENTE", entry["financialStatus"].stringValue())
        assertEquals("AVULSO", entry["membershipType"].stringValue())
    }

    @Test
    fun `roster returns raw membership attributes and joined at`() {
        roster.entries = listOf(
            AthleteRosterEntry(
                userId = memberId,
                displayName = AccessName.from("Member Person"),
                phone = null,
                position = AthletePosition.PONTA,
                membershipType = AthleteMembershipType.MENSALISTA,
                active = true,
                financialStatus = FinancialStatus.EM_DIA,
                nickname = "Raio",
                secondaryPosition = AthletePosition.CENTRAL,
                level = br.com.saqz.groups.domain.AthleteLevel.AVANCADO,
                preferredSide = null,
                heightCm = 187,
                monthlyFeeCents = 12500,
                monthlyDueDay = 10,
                joinedAt = java.time.Instant.parse("2026-08-01T12:00:00Z"),
            ),
        )

        val entry = json(getRoster(groupId))["athletes"][0]

        assertEquals("Raio", entry["nickname"].stringValue())
        assertEquals("CENTRAL", entry["secondaryPosition"].stringValue())
        assertEquals("AVANCADO", entry["level"].stringValue())
        assertEquals(187, entry["heightCm"].intValue())
        assertEquals(12500, entry["monthlyFeeCents"].intValue())
        assertEquals(10, entry["monthlyDueDay"].intValue())
        assertEquals("2026-08-01T12:00:00Z", entry["joinedAt"].stringValue())
    }

    @Test
    fun `athlete lists roster with unknown financial status`() {
        read.role = GroupRole.ATHLETE
        roster.entries = listOf(roster.entries.single().copy(monthlyFeeCents = 12500, monthlyDueDay = 10))
        val response = getRoster(groupId)
        val entry = json(response)["athletes"][0]

        assertEquals(200, response.statusCode())
        assertEquals("DESCONHECIDO", entry["financialStatus"].stringValue())
        assertTrue(entry["monthlyFeeCents"].isNull)
        assertTrue(entry["monthlyDueDay"].isNull)
    }

    @Test
    fun `nonmember roster is hidden as not found`() {
        read.role = null
        assertProblem(getRoster(groupId), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `athlete cannot filter roster by financial status`() {
        read.role = GroupRole.ATHLETE

        assertProblem(
            send(get("/api/groups/$groupId/athletes?financialStatus=PENDENTE")),
            403,
            "ACCESS_FORBIDDEN",
        )
        assertEquals(0, roster.calls)
    }

    @Test
    fun `athlete reaches phone visibility through the HTTP roster`() {
        read.role = GroupRole.ATHLETE
        roster.entries = listOf(
            AthleteRosterEntry(
                memberId, AccessName.from("Everyone Person"), "+5511987654321",
                AthletePosition.PONTA, AthleteMembershipType.AVULSO, true, FinancialStatus.EM_DIA,
            ),
            AthleteRosterEntry(
                adminsOnlyId, AccessName.from("Admins Only Person"), null,
                AthletePosition.CENTRAL, AthleteMembershipType.AVULSO, true, FinancialStatus.EM_DIA,
            ),
        )

        val response = getRoster(groupId)
        val entries = json(response)["athletes"]

        assertEquals(200, response.statusCode())
        assertEquals("+5511987654321", entries.first { it["displayName"].stringValue() == "Everyone Person" }["phone"].stringValue())
        assertTrue(entries.first { it["displayName"].stringValue() == "Admins Only Person" }["phone"].isNull())
    }

    @Test
    fun `admin lists roster with financial status`() {
        read.role = GroupRole.ADMIN

        val response = getRoster(groupId)
        val entry = json(response)["athletes"][0]

        assertEquals(200, response.statusCode())
        assertEquals("PENDENTE", entry["financialStatus"].stringValue())
    }

    @Test
    fun `malformed group id follows non enumeration contract`() {
        assertProblem(getRoster("not-a-uuid"), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `invalid roster filter enum is field validation`() {
        val response = send(get("/api/groups/$groupId/athletes?type=PLATINUM"))
        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("type"))
    }

    @Test
    fun `athlete updates own position`() {
        read.role = GroupRole.ATHLETE
        val response = patch("/api/groups/$groupId/athletes/me", mapOf("position" to "CENTRAL"))

        assertEquals(200, response.statusCode())
        assertEquals("CENTRAL", json(response)["position"].stringValue())
        assertEquals(actorId to AthletePosition.CENTRAL, athletes.positionUpdates.single())
    }

    @Test
    fun `invalid own position is field validation`() {
        val response = patch("/api/groups/$groupId/athletes/me", mapOf("position" to "GOLEIRO"))
        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(athletes.positionUpdates.isEmpty())
    }

    @Test
    fun `own patch returns new profile attributes`() {
        read.role = GroupRole.ATHLETE
        val response = patch(
            "/api/groups/$groupId/athletes/me",
            mapOf(
                "nickname" to "Raio",
                "position" to "PONTA",
                "secondaryPosition" to "CENTRAL",
                "level" to "INTERMEDIARIO",
                "heightCm" to 185,
            ),
        )
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals("Raio", body["nickname"].stringValue())
        assertEquals("CENTRAL", body["secondaryPosition"].stringValue())
        assertEquals("INTERMEDIARIO", body["level"].stringValue())
        assertEquals(185, body["heightCm"].intValue())
    }

    @Test
    fun `own patch rejects check violations with 422`() {
        val response = patch(
            "/api/groups/$groupId/athletes/me",
            mapOf("nickname" to " a", "heightCm" to 251),
        )

        assertProblem(response, 422, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("nickname"))
        assertTrue(json(response)["fieldErrors"].has("heightCm"))
        assertTrue(athletes.positionUpdates.isEmpty())
    }

    @Test
    fun `organizer updates athlete attributes`() {
        val response = patch(
            "/api/groups/$groupId/athletes/$memberId",
            mapOf("position" to "OPOSTO", "membershipType" to "MENSALISTA", "active" to false),
        )

        assertEquals(200, response.statusCode())
        assertEquals("MENSALISTA", json(response)["membershipType"].stringValue())
        assertFalse(json(response)["active"].booleanValue())
    }

    @Test
    fun `organizer patch accepts all new attributes and overrides`() {
        val response = patch(
            "/api/groups/$groupId/athletes/$memberId",
            mapOf(
                "nickname" to "Raio",
                "position" to "OPOSTO",
                "secondaryPosition" to "PONTA",
                "level" to "AVANCADO",
                "heightCm" to 190,
                "membershipType" to "MENSALISTA",
                "active" to false,
                "monthlyFeeCents" to 15000,
                "monthlyDueDay" to 12,
            ),
        )
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals("Raio", body["nickname"].stringValue())
        assertEquals("PONTA", body["secondaryPosition"].stringValue())
        assertEquals("AVANCADO", body["level"].stringValue())
        assertEquals(190, body["heightCm"].intValue())
        assertEquals(15000, body["monthlyFeeCents"].intValue())
        assertEquals(12, body["monthlyDueDay"].intValue())
    }

    @Test
    fun `patch rejects attributes not supported by group modality with 422`() {
        read.modality = GroupModality.BEACH_VOLLEYBALL
        val response = patch(
            "/api/groups/$groupId/athletes/$memberId",
            mapOf(
                "position" to "PONTA",
                "secondaryPosition" to "CENTRAL",
                "heightCm" to 180,
                "membershipType" to "AVULSO",
                "active" to true,
            ),
        )

        assertProblem(response, 422, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("position"))
        assertTrue(json(response)["fieldErrors"].has("secondaryPosition"))
        assertTrue(json(response)["fieldErrors"].has("heightCm"))
        assertTrue(athletes.updates.isEmpty())
    }

    @Test
    fun `athlete cannot update another athlete`() {
        read.role = GroupRole.ATHLETE
        val response = patch(
            "/api/groups/$groupId/athletes/$memberId",
            mapOf("position" to "OPOSTO", "membershipType" to "AVULSO", "active" to true),
        )
        assertProblem(response, 403, "ACCESS_FORBIDDEN")
        assertTrue(athletes.updates.isEmpty())
    }

    @Test
    fun `invalid membership type is field validation`() {
        val response = patch(
            "/api/groups/$groupId/athletes/$memberId",
            mapOf("membershipType" to "VIP", "active" to true),
        )
        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("membershipType"))
    }

    @Test
    fun `organizer removes athlete`() {
        val response = delete("/api/groups/$groupId/athletes/$memberId")

        assertEquals(200, response.statusCode())
        assertEquals(memberId, athletes.removals.single())
    }

    @Test
    fun `removing the owner is forbidden`() {
        assertProblem(delete("/api/groups/$groupId/athletes/$actorId"), 403, "ACCESS_FORBIDDEN")
        assertTrue(athletes.removals.isEmpty())
    }

    @Test
    fun `removing absent athlete is idempotent success`() {
        val response = delete("/api/groups/$groupId/athletes/${UUID.randomUUID()}")
        assertEquals(200, response.statusCode())
        assertTrue(athletes.removals.isEmpty())
    }

    @Test
    fun `own profile returns per group memberships`() {
        val response = send(get("/api/athletes/me"))
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals("+5511987654321", body["phone"].stringValue())
        assertEquals(1, body["memberships"].size())
        assertEquals("MENSALISTA", body["memberships"][0]["membershipType"].stringValue())
        assertEquals("Raio", body["memberships"][0]["nickname"].stringValue())
        assertEquals("CENTRAL", body["memberships"][0]["secondaryPosition"].stringValue())
        assertEquals("AVANCADO", body["memberships"][0]["level"].stringValue())
        assertEquals(185, body["memberships"][0]["heightCm"].intValue())
        assertEquals("2026-08-01T12:00:00Z", body["memberships"][0]["joinedAt"].stringValue())
    }

    @Test
    fun `admin reads group athlete stats`() {
        val body = json(send(get("/api/groups/$groupId/athletes/$memberId/stats")))

        assertEquals(3, body["games"].intValue())
        assertEquals(66, body["attendanceRate"].intValue())
        assertEquals(1, body["absences"].intValue())
    }

    @Test
    fun `athlete reads own stats but not another athlete stats`() {
        read.role = GroupRole.ATHLETE
        val ownResponse = send(get("/api/groups/$groupId/athletes/$actorId/stats"))
        val own = json(ownResponse)

        assertEquals(200, ownResponse.statusCode())
        assertEquals(3, own["games"].intValue())
        assertProblem(send(get("/api/groups/$groupId/athletes/$memberId/stats")), 403, "ACCESS_FORBIDDEN")
    }

    @Test
    fun `nonmember stats is hidden as not found`() {
        read.role = null

        assertProblem(send(get("/api/groups/$groupId/athletes/$memberId/stats")), 404, "GROUP_NOT_FOUND")
    }

    private fun getRoster(id: Any): HttpResponse<String> = send(get("/api/groups/$id/athletes"))

    private fun get(path: String): HttpRequest = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        .header("Authorization", "Bearer athlete-token")
        .GET()
        .build()

    private fun patch(path: String, body: Map<String, Any?>): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer athlete-token")
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build(),
    )

    private fun delete(path: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer athlete-token")
            .DELETE()
            .build(),
    )

    private fun send(request: HttpRequest) = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    private fun athlete(id: UUID, name: String, role: GroupRole) = AthleteMembership(
        id, AccessName.from(name), role, null, AthleteMembershipType.AVULSO, true,
    )

    @TestConfiguration(proxyBeanMethods = false)
    class AthleteTestConfiguration {
        @Bean @Primary fun athleteVerifier() = AthleteVerifier()
        @Bean fun athleteSessionRepository() = AthleteSessionRepository()
        @Bean fun athleteBootstrap(repository: AthleteSessionRepository) = BootstrapSession(repository)
        @Bean fun athleteGroupReadRepository() = RecordingAthleteGroupReadRepository()
        @Bean fun recordingAthleteRepository() = RecordingAthleteRepository()
        @Bean fun recordingAthleteRosterRepository() = RecordingAthleteRosterRepository()
        @Bean fun recordingAthleteStatsRepository() = RecordingAthleteStatsRepository()
        @Bean fun athleteTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun testListAthletes(read: RecordingAthleteGroupReadRepository, roster: RecordingAthleteRosterRepository) =
            ListAthletes(read, roster, GroupAccessPolicy())
        @Bean fun testUpdateOwnAthleteProfile(transaction: TransactionRunner, read: RecordingAthleteGroupReadRepository, repository: RecordingAthleteRepository) =
            UpdateOwnAthleteProfile(transaction, read, repository)
        @Bean fun testUpdateAthlete(transaction: TransactionRunner, read: RecordingAthleteGroupReadRepository, repository: RecordingAthleteRepository) =
            UpdateAthlete(transaction, read, repository, GroupAccessPolicy())
        @Bean fun testRemoveAthlete(transaction: TransactionRunner, read: RecordingAthleteGroupReadRepository, repository: RecordingAthleteRepository) =
            RemoveAthlete(transaction, read, repository, GroupAccessPolicy())
        @Bean fun testGetOwnAthleteProfile(roster: RecordingAthleteRosterRepository) = GetOwnAthleteProfile(roster)
        @Bean fun testGetAthleteStats(
            read: RecordingAthleteGroupReadRepository,
            athletes: RecordingAthleteRepository,
            stats: RecordingAthleteStatsRepository,
        ) = GetAthleteStats(read, athletes, stats)
        @Bean fun testAthleteController(
            bootstrap: BootstrapSession,
            list: ListAthletes,
            updateOwn: UpdateOwnAthleteProfile,
            update: UpdateAthlete,
            remove: RemoveAthlete,
            ownProfile: GetOwnAthleteProfile,
            stats: GetAthleteStats,
        ) = AthleteController(verifiedGroupActorResolver(bootstrap), list, updateOwn, update, remove, ownProfile, stats)
        companion object { val USER_ID: UUID = UUID.randomUUID() }
    }

    class AthleteVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("athlete-subject", "athlete@example.test", true, "Athlete Person"),
        )
    }

    class AthleteSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(AthleteTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class RecordingAthleteGroupReadRepository : GroupReadRepository {
        var role: GroupRole? = GroupRole.OWNER
        var modality: GroupModality? = GroupModality.COURT_VOLLEYBALL
        override fun find(key: GroupReadKey) = GroupReadSnapshot(
            key.groupId, AccessName.from("Training Group"), IanaTimeZone.from("UTC"), role, 1,
            profile = GroupProfileReadModel(
                modality = modality,
                composition = GroupComposition.MIXED,
                description = null,
                city = null,
                level = null,
                customLevel = null,
                playStyle = null,
                customPlayStyle = null,
                defaultVenue = null,
                regularSlots = emptyList(),
                defaultCapacity = null,
                defaultConfirmationLeadMinutes = null,
            ),
        )
    }

    class RecordingAthleteRepository : AthleteRepository {
        private val members = linkedMapOf<UUID, AthleteMembership>()
        val positionUpdates = mutableListOf<Pair<UUID, AthletePosition?>>()
        val updates = mutableListOf<UpdateAthleteCommand>()
        val removals = mutableListOf<UUID>()
        fun reset(values: List<AthleteMembership>) {
            members.clear()
            values.associateByTo(members, AthleteMembership::userId)
            positionUpdates.clear()
            updates.clear()
            removals.clear()
        }
        override fun find(groupId: UUID, userId: UUID) = members[userId]
        override fun updateOwn(command: UpdateOwnAthleteProfileCommand): AthleteMembership {
            positionUpdates += command.userId to command.position
            val changed = members.getValue(command.userId).copy(
                nickname = command.nickname,
                position = command.position,
                secondaryPosition = command.secondaryPosition,
                level = command.level,
                preferredSide = command.preferredSide,
                heightCm = command.heightCm,
            )
            members[command.userId] = changed
            return changed
        }
        override fun updatePosition(groupId: UUID, userId: UUID, position: AthletePosition?): AthleteMembership {
            positionUpdates += userId to position
            val changed = members.getValue(userId).copy(position = position)
            members[userId] = changed
            return changed
        }
        override fun update(command: UpdateAthleteCommand): AthleteMembership {
            updates += command
            val changed = members.getValue(command.userId)
                .copy(
                    position = command.position,
                    membershipType = command.membershipType,
                    active = command.active,
                    nickname = command.nickname,
                    secondaryPosition = command.secondaryPosition,
                    level = command.level,
                    preferredSide = command.preferredSide,
                    heightCm = command.heightCm,
                    monthlyFeeCents = command.monthlyFeeCents,
                    monthlyDueDay = command.monthlyDueDay,
                )
            members[command.userId] = changed
            return changed
        }
        override fun remove(groupId: UUID, userId: UUID) {
            removals += userId
            members.remove(userId)
        }
    }

    class RecordingAthleteRosterRepository : AthleteRosterRepository {
        var entries: List<AthleteRosterEntry> = emptyList()
        var profile: OwnAthleteProfile? = null
        var calls = 0

        override fun list(actorId: UUID, groupId: UUID, filter: AthleteRosterFilter): List<AthleteRosterEntry> {
            calls++
            return entries
        }

        override fun findOwnProfile(actor: UUID) = profile
    }

    class RecordingAthleteStatsRepository : AthleteStatsRepository {
        var values = AthleteStatsAggregate(0, 0, 0)
        override fun find(groupId: UUID, userId: UUID) = values
    }
}
