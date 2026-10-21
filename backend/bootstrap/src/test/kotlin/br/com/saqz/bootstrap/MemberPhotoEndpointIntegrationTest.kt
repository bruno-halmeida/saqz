package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.AccessMembershipController
import br.com.saqz.groups.adapter.input.http.AthleteController
import br.com.saqz.groups.adapter.input.http.AttendanceController
import br.com.saqz.groups.adapter.input.http.GroupCommunicationController
import br.com.saqz.groups.adapter.input.http.MemberPhotoController
import br.com.saqz.groups.adapter.input.http.MyHomeController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRosterRepository
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteStatsRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.home.JdbcHomeRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.groups.adapter.output.jdbc.photo.JdbcMemberPhotoDirectory
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.GetAthleteStats
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfile
import br.com.saqz.groups.application.athlete.ListAthletes
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.athlete.UpdateAthlete
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfile
import br.com.saqz.groups.application.attendance.AdjustGameCapacity
import br.com.saqz.groups.application.attendance.AttendanceChargePort
import br.com.saqz.groups.application.attendance.GameGuests
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.home.HomeQuery
import br.com.saqz.groups.application.membership.ChangeMemberRole
import br.com.saqz.groups.application.membership.ListAccessMemberships
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
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

/** MEM-006 pela API: rota da foto de um membro e `photoTag` nas respostas com pessoas do grupo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MemberPhotoEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true"])
class MemberPhotoEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var json: ObjectMapper
    private val jdbc by lazy { JdbcClient.create(dataSource) }
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val noPhoto = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val ownerPhoto = "foto-dono-${UUID.randomUUID()}".encodeToByteArray()
    private val memberPhoto = "foto-atleta-${UUID.randomUUID()}".encodeToByteArray()

    @BeforeEach fun setup() {
        for ((id, name) in listOf(owner to "Dono", member to "Atleta", noPhoto to "Sem Foto", stranger to "De Fora")) {
            jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, :name, now(), now())")
                .param("id", id).param("subject", id.toString()).param("name", name).update()
        }
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, 'Vôlei', 'UTC', now(), now())")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (id in listOf(owner, member, noPhoto)) {
            jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES (:group, :user, 'ATHLETE', now(), now())")
                .param("group", group).param("user", id).update()
        }
        // Fotos gravadas antes da mudança: só a linha em access_user_photos.
        photo(owner, ownerPhoto)
        photo(member, memberPhoto)
        photo(stranger, "foto-de-fora".encodeToByteArray())
    }

    @Test fun `a member reads another member's photo with a strong ETag and 304`() {
        val path = "/api/groups/$group/members/$owner/photo?v=${hex(ownerPhoto)}"
        val response = bytes(path, member)
        assertEquals(200, response.statusCode())
        assertContentEquals(ownerPhoto, response.body())
        assertEquals("\"${hex(ownerPhoto)}\"", response.headers().firstValue("ETag").get())
        assertEquals("private, no-cache", response.headers().firstValue("Cache-Control").get())
        assertEquals("image/jpeg", response.headers().firstValue("Content-Type").get())

        val revalidated = bytes(path, member, ifNoneMatch = "\"${hex(ownerPhoto)}\"")
        assertEquals(304, revalidated.statusCode())
        assertEquals(0, revalidated.body().size)
    }

    @Test fun `outsiders are forbidden and missing or foreign targets are not found`() {
        assertEquals(401, bytes("/api/groups/$group/members/$owner/photo", actor = null).statusCode())
        assertEquals(403, bytes("/api/groups/$group/members/$owner/photo", stranger).statusCode())
        assertEquals(404, bytes("/api/groups/$group/members/$noPhoto/photo", member).statusCode())
        assertEquals(404, bytes("/api/groups/$group/members/$stranger/photo", member).statusCode())
        assertEquals(404, bytes("/api/groups/$group/members/not-a-uuid/photo", member).statusCode())
        assertEquals(404, bytes("/api/groups/not-a-uuid/members/$owner/photo", member).statusCode())
    }

    @Test fun `former members lose access both ways`() {
        jdbc.sql("DELETE FROM group_memberships WHERE group_id = :group AND user_id = :user").param("group", group).param("user", member).update()

        assertEquals(403, bytes("/api/groups/$group/members/$owner/photo", member).statusCode())
        assertEquals(404, bytes("/api/groups/$group/members/$member/photo", owner).statusCode())
        assertEquals(null, roster(owner)[member.toString()])
    }

    @Test fun `the blocker gets initials while the blocked still sees the photo`() {
        jdbc.sql("INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (:blocker, :blocked)").param("blocker", owner).param("blocked", member).update()

        assertEquals(404, bytes("/api/groups/$group/members/$member/photo", owner).statusCode())
        assertTrue(roster(owner).getValue(member.toString()).isNull)
        assertEquals(200, bytes("/api/groups/$group/members/$owner/photo", member).statusCode())
        assertEquals(hex(ownerPhoto), roster(member).getValue(owner.toString()).stringValue())
    }

    @Test fun `roster and memberships carry the photo tag`() {
        val tags = roster(member)
        assertEquals(hex(ownerPhoto), tags.getValue(owner.toString()).stringValue())
        assertEquals(hex(memberPhoto), tags.getValue(member.toString()).stringValue())
        assertTrue(tags.getValue(noPhoto.toString()).isNull)

        val memberships = json.readTree(text("/api/groups/$group/memberships", owner).body())
        val byUser = memberships.associate { it["userId"].stringValue() to it["photoTag"] }
        assertEquals(hex(memberPhoto), byUser.getValue(member.toString()).stringValue())
        assertTrue(byUser.getValue(noPhoto.toString()).isNull)
    }

    @Test fun `game roster and home preview carry the photo tag`() {
        jdbc.sql(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,published_at,created_at,updated_at) " +
                "VALUES (:game,:group,'Treino',(CURRENT_DATE + 2),TIME '19:30','UTC',now() + interval '2 days',90,now() + interval '1 day','Arena','Rua Central 100',10,2500,'PUBLISHED',now(),now(),now())",
        ).param("game", game).param("group", group).update()
        for ((id, name) in listOf(owner to "Dono", noPhoto to "Sem Foto")) {
            jdbc.sql("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,responded_at,updated_at,member_display_name) VALUES (:game,:group,:user,'CONFIRMED',now(),now(),:name)")
                .param("game", game).param("group", group).param("user", id).param("name", name).update()
        }

        val confirmed = json.readTree(text("/api/groups/$group/games/$game/attendance/roster", member).body())["confirmed"]
            .associate { it["memberId"].stringValue() to it["photoTag"] }
        assertEquals(hex(ownerPhoto), confirmed.getValue(owner.toString()).stringValue())
        assertTrue(confirmed.getValue(noPhoto.toString()).isNull)

        val preview = json.readTree(text("/api/me/home", member).body())["member"]["nextGame"]["rosterPreview"]["confirmed"]
            .associate { it["userId"].stringValue() to it["photoTag"] }
        assertEquals(hex(ownerPhoto), preview.getValue(owner.toString()).stringValue())
        assertTrue(preview.getValue(noPhoto.toString()).isNull)
    }

    @Test fun `notices and the inbox carry the author's photo tag`() {
        val posted = text("/api/groups/$group/messages?channel=NOTICE", owner, """{"requestId":"${UUID.randomUUID()}","body":"Treino confirmado"}""")
        assertEquals(200, posted.statusCode(), posted.body())

        val notices = json.readTree(text("/api/groups/$group/messages?channel=NOTICE", member).body())["items"]
        assertEquals(hex(ownerPhoto), notices[0]["photoTag"].stringValue())
        val inbox = json.readTree(text("/api/me/notifications", member).body())["items"]
        assertEquals(hex(ownerPhoto), inbox[0]["message"]["photoTag"].stringValue())
    }

    @Test fun `home groups and own profile carry the group photo version only when there is a photo`() {
        fun homeVersion() = json.readTree(text("/api/me/home", member).body())["member"]["groups"][0]["photoVersion"]
        fun profileVersion() = json.readTree(text("/api/athletes/me", member).body())["memberships"][0]["photoVersion"]
        assertTrue(homeVersion().isNull)
        assertTrue(profileVersion().isNull)

        val bytes = "foto-do-grupo".encodeToByteArray()
        jdbc.sql(
            "INSERT INTO group_photos (group_id, photo_bytes, media_type, byte_size, width, height, sha256_digest, version, updated_by, created_at, updated_at) " +
                "VALUES (:group, :bytes, 'image/png', :size, 1, 1, :digest, 3, :owner, now(), now())",
        ).param("group", group).param("bytes", bytes).param("size", bytes.size.toLong())
            .param("digest", MessageDigest.getInstance("SHA-256").digest(bytes)).param("owner", owner).update()

        assertEquals(3L, homeVersion().longValue())
        assertEquals(3L, profileVersion().longValue())
    }

    private fun roster(actor: UUID): Map<String, JsonNode> =
        json.readTree(text("/api/groups/$group/athletes", actor).body())["athletes"]
            .associate { it["userId"].stringValue() to it["photoTag"] }

    private fun photo(user: UUID, bytes: ByteArray) {
        jdbc.sql(
            "INSERT INTO access_user_photos (user_id, photo_bytes, byte_size, width, height, sha256_digest, created_at, updated_at) " +
                "VALUES (:user, :bytes, :size, 1, 1, :digest, now(), now())",
        ).param("user", user).param("bytes", bytes).param("size", bytes.size.toLong())
            .param("digest", MessageDigest.getInstance("SHA-256").digest(bytes)).update()
    }

    private fun hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun builder(path: String, actor: UUID?) = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        .also { if (actor != null) it.header("Authorization", "Bearer $actor") }

    private fun bytes(path: String, actor: UUID?, ifNoneMatch: String? = null): HttpResponse<ByteArray> {
        val request = builder(path, actor).also { if (ifNoneMatch != null) it.header("If-None-Match", ifNoneMatch) }.GET().build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun text(path: String, actor: UUID, body: String? = null): HttpResponse<String> {
        val request = builder(path, actor).apply {
            if (body != null) header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)) else GET()
        }.build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        private val actors = VerifiedGroupActorResolver { UUID.fromString(it.subject) }

        @Bean fun memberPhotoTestDataSource(): DataSource = TestPostgres.migrated("classpath:db/migration").dataSource
        @Bean @Primary fun memberPhotoVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) = TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Test Person"))
        }
        @Bean fun memberPhotoTestDirectory(dataSource: DataSource) = JdbcMemberPhotoDirectory(dataSource)
        @Bean fun memberPhotoTestController(photos: JdbcMemberPhotoDirectory) = MemberPhotoController(actors, photos)
        @Bean fun memberPhotoAthleteController(dataSource: DataSource, photos: JdbcMemberPhotoDirectory): AthleteController {
            val transaction = JdbcTransactionRunner(dataSource)
            val groups = JdbcGroupReadRepository(dataSource)
            val athletes = JdbcAthleteRepository(dataSource)
            val roster = JdbcAthleteRosterRepository(dataSource)
            val access = GroupAccessPolicy()
            return AthleteController(
                actors,
                ListAthletes(groups, roster, access),
                UpdateOwnAthleteProfile(transaction, groups, athletes),
                UpdateAthlete(transaction, groups, athletes, access),
                RemoveAthlete(transaction, groups, athletes, access),
                GetOwnAthleteProfile(roster),
                GetAthleteStats(groups, athletes, JdbcAthleteStatsRepository(dataSource)),
                photos,
            )
        }
        @Bean fun memberPhotoMembershipController(dataSource: DataSource, photos: JdbcMemberPhotoDirectory): AccessMembershipController {
            val groups = JdbcGroupReadRepository(dataSource)
            val memberships = JdbcMembershipRepository(dataSource)
            return AccessMembershipController(
                actors,
                ListAccessMemberships(groups, memberships, GroupAccessPolicy()),
                ChangeMemberRole(JdbcTransactionRunner(dataSource), groups, memberships, GroupAccessPolicy()),
                photos,
            )
        }
        @Bean fun memberPhotoAttendanceController(dataSource: DataSource, photos: JdbcMemberPhotoDirectory): AttendanceController {
            val transaction = JdbcTransactionRunner(dataSource)
            val repository = JdbcAttendanceCommandRepository(dataSource)
            val charges = AttendanceChargePort { _, _ -> }
            val responses = RespondAttendance(transaction, repository, charges, Instant::now)
            return AttendanceController(
                actors, responses, AdjustGameCapacity(transaction, repository, charges, Instant::now),
                repository, repository, GameGuests(transaction, repository, responses, Instant::now), photos,
            )
        }
        @Bean fun memberPhotoHomeController(dataSource: DataSource, photos: JdbcMemberPhotoDirectory) =
            MyHomeController(actors, HomeQuery(JdbcHomeRepository(dataSource), Clock.systemUTC(), ZoneId.of("UTC")), photos)
        @Bean fun memberPhotoCommunicationController(dataSource: DataSource, photos: JdbcMemberPhotoDirectory) = GroupCommunicationController(
            actors,
            GroupCommunicationService(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcGroupCommunicationRepository(dataSource)),
            photos,
        )
    }
}
