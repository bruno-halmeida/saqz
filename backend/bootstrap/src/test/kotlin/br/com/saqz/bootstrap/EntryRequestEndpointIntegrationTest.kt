package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.AccessName as AccessUserName
import br.com.saqz.groups.adapter.input.http.AccessEntryRequestController
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.entryrequest.ApproveEntryRequest
import br.com.saqz.groups.application.entryrequest.EntryRequestRepository
import br.com.saqz.groups.application.entryrequest.GroupEntryRequest
import br.com.saqz.groups.application.entryrequest.ListEntryRequests
import br.com.saqz.groups.application.entryrequest.RejectEntryRequest
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.redeem.CreateEntryRequestCommand
import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.invite.redeem.InviteAttemptWindow
import br.com.saqz.groups.application.invite.redeem.InviteRedemptionRepository
import br.com.saqz.groups.application.invite.redeem.RecordInvalidInviteAttempt
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemMembershipCommand
import br.com.saqz.groups.application.invite.redeem.RedeemableInvite
import br.com.saqz.groups.application.membership.AccessMembership
import br.com.saqz.groups.application.membership.ChangeMemberRoleCommand
import br.com.saqz.groups.application.membership.MembershipRepository
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
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
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EntryRequestEndpointIntegrationTest.EntryRequestTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class EntryRequestEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var repository: RecordingEntryRequestRepository
    @Autowired private lateinit var read: RecordingEntryRequestGroupReadRepository
    @Autowired private lateinit var memberships: RecordingEntryMembershipRepository
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        repository.reset()
        read.roles = mapOf(
            EntryRequestTestConfiguration.OWNER_ID to GroupRole.OWNER,
            EntryRequestTestConfiguration.ADMIN_ID to GroupRole.ADMIN,
            EntryRequestTestConfiguration.ATHLETE_ID to GroupRole.ATHLETE,
        )
        memberships.reset()
    }

    @Test
    fun `approval flow redeems pending lists approves and creates member`() {
        val pending = redeem("applicant-token")
        assertEquals(200, pending.statusCode())
        assertEquals("PENDING", json(pending)["status"].stringValue())

        val listed = list("owner-token")
        assertEquals(200, listed.statusCode())
        assertEquals(EntryRequestTestConfiguration.APPLICANT_ID.toString(), json(listed)[0]["userId"].stringValue())
        assertEquals("Applicant Person", json(listed)[0]["displayName"].stringValue())

        val approved = approve("owner-token", EntryRequestTestConfiguration.APPLICANT_ID)
        assertEquals(200, approved.statusCode())
        assertEquals("ATHLETE", json(approved)["role"].stringValue())
        assertEquals(EntryRequestTestConfiguration.APPLICANT_ID, memberships.members.values.single().userId)
        assertTrue(repository.entryRequests.isEmpty())
    }

    @Test
    fun `reject removes request and same invite can be requested again`() {
        assertEquals("PENDING", json(redeem("applicant-token"))["status"].stringValue())

        val rejected = reject("owner-token", EntryRequestTestConfiguration.APPLICANT_ID)
        assertEquals(204, rejected.statusCode())
        assertTrue(repository.entryRequests.isEmpty())

        val requestedAgain = redeem("applicant-token")
        assertEquals("PENDING", json(requestedAgain)["status"].stringValue())
        assertEquals(1, repository.entryRequests.size)
    }

    @Test
    fun `non-admin roles are forbidden and nonmembers are hidden`() {
        assertProblem(list("athlete-token"), 403, "ACCESS_FORBIDDEN")
        assertProblem(list("unknown-token"), 404, "GROUP_NOT_FOUND")

        redeem("applicant-token")
        assertProblem(approve("owner-token", UUID.randomUUID()), 404, "ENTRY_REQUEST_NOT_FOUND")
        assertFalse(repository.entryRequests.isEmpty())
    }

    private fun redeem(token: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(uri("/api/invites/redeem"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"${EntryRequestTestConfiguration.RAW_CODE}\"}"))
            .build(),
    )

    private fun list(token: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(uri("/api/groups/${EntryRequestTestConfiguration.GROUP_ID}/entry-requests"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build(),
    )

    private fun approve(token: String, userId: UUID): HttpResponse<String> = send(
        HttpRequest.newBuilder(uri("/api/groups/${EntryRequestTestConfiguration.GROUP_ID}/entry-requests/$userId/approve"))
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
    )

    private fun reject(token: String, userId: UUID): HttpResponse<String> = send(
        HttpRequest.newBuilder(uri("/api/groups/${EntryRequestTestConfiguration.GROUP_ID}/entry-requests/$userId"))
            .header("Authorization", "Bearer $token")
            .DELETE()
            .build(),
    )

    private fun uri(path: String) = URI("http://127.0.0.1:$port$path")
    private fun send(request: HttpRequest) = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class EntryRequestTestConfiguration {
        @Bean @Primary fun entryRequestVerifier() = EntryRequestVerifier()
        @Bean fun entryRequestSessionRepository() = EntryRequestSessionRepository()
        @Bean fun entryRequestBootstrap(repository: EntryRequestSessionRepository) = BootstrapSession(repository)
        @Bean fun entryRequestRepository() = RecordingEntryRequestRepository(GROUP_ID, OWNER_ID, APPLICANT_ID)
        @Bean fun entryRequestGroupReadRepository() = RecordingEntryRequestGroupReadRepository()
        @Bean fun entryRequestMembershipRepository() = RecordingEntryMembershipRepository(APPLICANT_ID)
        @Bean fun entryRequestTransaction() = RecordingEntryRequestTransaction()
        @Bean @Primary fun entryRequestSubscriptionLimits() = UnlimitedEntryRequestSubscriptionLimits

        @Bean
        fun redeemInvite(
            transaction: RecordingEntryRequestTransaction,
            repository: RecordingEntryRequestRepository,
        ) = RedeemInvite(transaction, repository, UnlimitedEntryRequestSubscriptionLimits, Clock.fixed(NOW, ZoneOffset.UTC))

        @Bean
        fun accessInviteRedemptionController(
            bootstrap: BootstrapSession,
            redeemInvite: RedeemInvite,
        ) = br.com.saqz.groups.adapter.input.http.AccessInviteRedemptionController(
            verifiedGroupActorResolver(bootstrap),
            redeemInvite,
        )

        @Bean
        fun listEntryRequests(
            read: RecordingEntryRequestGroupReadRepository,
            repository: RecordingEntryRequestRepository,
        ) = ListEntryRequests(read, repository, GroupAccessPolicy())

        @Bean
        fun approveEntryRequest(
            transaction: RecordingEntryRequestTransaction,
            read: RecordingEntryRequestGroupReadRepository,
            repository: RecordingEntryRequestRepository,
            memberships: RecordingEntryMembershipRepository,
        ) = ApproveEntryRequest(
            transaction,
            read,
            repository,
            memberships,
            UnlimitedEntryRequestSubscriptionLimits,
            GroupAccessPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

        @Bean
        fun rejectEntryRequest(
            transaction: RecordingEntryRequestTransaction,
            read: RecordingEntryRequestGroupReadRepository,
            repository: RecordingEntryRequestRepository,
        ) = RejectEntryRequest(transaction, read, repository, GroupAccessPolicy())

        @Bean
        fun accessEntryRequestController(
            bootstrap: BootstrapSession,
            list: ListEntryRequests,
            approve: ApproveEntryRequest,
            reject: RejectEntryRequest,
        ) = AccessEntryRequestController(verifiedGroupActorResolver(bootstrap), list, approve, reject)

        companion object {
            val NOW: Instant = Instant.parse("2026-08-02T10:00:00Z")
            val OWNER_ID: UUID = UUID.randomUUID()
            val ADMIN_ID: UUID = UUID.randomUUID()
            val ATHLETE_ID: UUID = UUID.randomUUID()
            val APPLICANT_ID: UUID = UUID.randomUUID()
            val GROUP_ID: UUID = UUID.randomUUID()
            val RAW_CODE: String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 4 })
        }
    }

    class EntryRequestVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken): TokenVerification = TokenVerification.Verified(
            when (token.value) {
                "owner-token" -> RequestIdentity("owner-subject", "owner@example.test", true, "Owner Person")
                "applicant-token" -> RequestIdentity("applicant-subject", "applicant@example.test", true, "Applicant Person")
                "athlete-token" -> RequestIdentity("athlete-subject", "athlete@example.test", true, "Athlete Person")
                else -> RequestIdentity("unknown-subject", "unknown@example.test", true, "Unknown Person")
            },
        )
    }

    class EntryRequestSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(
                when (command.subject) {
                    "owner-subject" -> EntryRequestTestConfiguration.OWNER_ID
                    "applicant-subject" -> EntryRequestTestConfiguration.APPLICANT_ID
                    "athlete-subject" -> EntryRequestTestConfiguration.ATHLETE_ID
                    else -> UUID.randomUUID()
                },
                command.subject,
                command.email,
                AccessUserName.from(command.displayName.value),
            ),
            emptyList(),
        )
    }

    object UnlimitedEntryRequestSubscriptionLimits : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = null
        override fun athleteLimitFor(ownerId: UUID): Int? = null
    }

    class RecordingEntryRequestTransaction : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    class RecordingEntryRequestGroupReadRepository : GroupReadRepository {
        var roles: Map<UUID, GroupRole> = emptyMap()

        override fun find(key: GroupReadKey): GroupReadSnapshot? {
            if (key.groupId != EntryRequestTestConfiguration.GROUP_ID) return null
            return GroupReadSnapshot(
                key.groupId,
                AccessName.from("Training Group"),
                IanaTimeZone.from("UTC"),
                roles[key.actorUserId],
                1,
            )
        }
    }

    class RecordingEntryRequestRepository(
        private val groupId: UUID,
        private val ownerId: UUID,
        private val applicantId: UUID,
    ) : InviteRedemptionRepository, EntryRequestRepository {
        var target: RedeemableInvite = RedeemableInvite(groupId, entryRequiresApproval = true)
        val entryRequests = linkedMapOf<UUID, CreateEntryRequestCommand>()
        val roles = mutableMapOf(ownerId to GroupRole.OWNER)
        val windows = mutableMapOf<UUID, InviteAttemptWindow>()

        fun reset() {
            target = RedeemableInvite(groupId, entryRequiresApproval = true)
            entryRequests.clear()
            roles.clear()
            roles[ownerId] = GroupRole.OWNER
            windows.clear()
        }

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant) =
            windows.getOrPut(userId) { InviteAttemptWindow(initializedAt, 0) }

        override fun findInvite(digest: InviteTokenDigest) = target

        override fun recordInvalidAttempt(command: RecordInvalidInviteAttempt) {
            windows[command.userId] = InviteAttemptWindow(command.windowStartedAt, command.invalidCount)
        }

        override fun loadAthleteOccupancy(groupId: UUID) = GroupAthleteOccupancy(
            ownerUserId = ownerId,
            openMemberIds = roles.filterValues { it != GroupRole.OWNER }.keys,
            openWaitlistIds = emptySet(),
            closedOccupancies = emptyList(),
        )

        override fun findMembershipRole(groupId: UUID, userId: UUID): GroupRole? = roles[userId]

        override fun createEntryRequest(command: CreateEntryRequestCommand) {
            entryRequests.putIfAbsent(command.userId, command)
        }

        override fun redeemMembership(command: RedeemMembershipCommand): GroupRole {
            roles[command.userId] = GroupRole.ATHLETE
            return GroupRole.ATHLETE
        }

        override fun list(groupId: UUID) = entryRequests.map { (userId, command) ->
            GroupEntryRequest(
                userId,
                AccessName.from(if (userId == applicantId) "Applicant Person" else "Other Person"),
                command.requestedAt,
            )
        }

        override fun find(groupId: UUID, userId: UUID) = entryRequests[userId]?.let { command ->
            GroupEntryRequest(
                userId,
                AccessName.from(if (userId == applicantId) "Applicant Person" else "Other Person"),
                command.requestedAt,
            )
        }

        override fun delete(groupId: UUID, userId: UUID) {
            entryRequests.remove(userId)
        }
    }

    class RecordingEntryMembershipRepository(private val applicantId: UUID) : MembershipRepository {
        val members = linkedMapOf<UUID, AccessMembership>()

        fun reset() {
            members.clear()
        }

        override fun list(groupId: UUID): List<AccessMembership> = members.values.toList()

        override fun find(groupId: UUID, userId: UUID): AccessMembership? = members[userId]

        override fun change(command: ChangeMemberRoleCommand): AccessMembership {
            val membership = AccessMembership(
                applicantId,
                AccessName.from("Applicant Person"),
                GroupRole.valueOf(command.role.name),
            )
            members[applicantId] = membership
            return membership
        }
    }
}
