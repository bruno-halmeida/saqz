package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.adapter.input.http.GroupPhotoController
import br.com.saqz.groups.adapter.output.media.GroupPhotoValidator
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.photo.GroupPhotoMediaType
import br.com.saqz.groups.application.photo.GroupPhotoMetadata
import br.com.saqz.groups.application.photo.GroupPhotoRepository
import br.com.saqz.groups.application.photo.GroupPhotoService
import br.com.saqz.groups.application.photo.GroupPhotoWriteResult
import br.com.saqz.groups.application.photo.ReplaceGroupPhotoCommand
import br.com.saqz.groups.application.photo.StoredGroupPhoto
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupPhotoEndpointIntegrationTest.PhotoTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class GroupPhotoEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var read: PhotoReadRepository
    @Autowired private lateinit var photos: RecordingPhotoRepository
    @Autowired private lateinit var objectMapper: ObjectMapper
    private val groupId = UUID.randomUUID()
    private val validPng by lazy(::png)

    @BeforeEach
    fun reset() {
        read.snapshot = snapshot(GroupRole.OWNER, 7)
        photos.reset(7)
    }

    @Test fun `owner uploads validated photo and receives new group ETag`() {
        val response = put(validPng)

        assertEquals(204, response.statusCode())
        assertEquals("\"8\"", header(response, "ETag"))
        assertContentEquals(validPng, photos.photo?.bytes)
        assertEquals(GroupPhotoMediaType.PNG, photos.photo?.mediaType)
    }

    @Test fun `admin uploads photo`() {
        read.snapshot = snapshot(GroupRole.ADMIN, 7)
        val response = put(validPng)
        assertEquals(204, response.statusCode())
        assertNotNull(photos.photo)
    }

    @Test fun `athlete upload is forbidden without replacement`() {
        read.snapshot = snapshot(GroupRole.ATHLETE, 7)
        assertProblem(put(validPng), 403, "ACCESS_FORBIDDEN")
        assertNull(photos.photo)
    }

    @Test fun `nonmember upload is privacy preserving not found`() {
        read.snapshot = snapshot(null, 7)
        assertProblem(put(validPng), 404, "GROUP_NOT_FOUND")
        assertNull(photos.photo)
    }

    @Test fun `upload requires If-Match`() {
        assertProblem(put(validPng, etag = null), 428, "PRECONDITION_REQUIRED")
        assertNull(photos.photo)
    }

    @Test fun `upload rejects malformed If-Match safely`() {
        assertProblem(put(validPng, etag = "7"), 400, "VALIDATION_FAILED")
        assertNull(photos.photo)
    }

    @Test fun `stale upload returns conflict and preserves old photo`() {
        assertEquals(204, put(validPng).statusCode())
        val old = photos.photo?.bytes

        assertProblem(put(png(color = 0x00FF00), etag = "\"7\""), 409, "VERSION_CONFLICT")
        assertContentEquals(old, photos.photo?.bytes)
    }

    @Test fun `corrupt media maps stable invalid problem without replacement`() {
        assertProblem(put(byteArrayOf(0x89.toByte(), 0x50, 0x4E), contentType = "image/png"), 400, "PHOTO_INVALID")
        assertNull(photos.photo)
    }

    @Test fun `declared actual mismatch maps stable invalid problem`() {
        assertProblem(put(validPng, contentType = "image/jpeg"), 400, "PHOTO_INVALID")
        assertNull(photos.photo)
    }

    @Test fun `multipart over servlet limit maps safe too large problem`() {
        val response = put(ByteArray(5 * 1024 * 1024 + 1), contentType = "image/png")
        assertProblem(response, 413, "PHOTO_TOO_LARGE")
        assertNull(photos.photo)
    }

    @Test fun `missing multipart file maps stable invalid problem`() {
        val boundary = "saqz-empty-boundary"
        val builder = request("PUT")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .header("If-Match", "\"7\"")
            .PUT(HttpRequest.BodyPublishers.ofString("--$boundary--\r\n"))
        assertProblem(send(builder), 400, "PHOTO_INVALID")
        assertNull(photos.photo)
    }

    @Test fun `member GET returns actual type private cache ETag and exact bytes`() {
        assertEquals(204, put(validPng).statusCode())

        val response = get()

        assertEquals(200, response.statusCode())
        assertEquals("image/png", header(response, "Content-Type"))
        assertEquals("private, no-cache", header(response, "Cache-Control"))
        assertEquals("\"photo-1\"", header(response, "ETag"))
        assertContentEquals(validPng, response.body())
    }

    @Test fun `athlete can read private photo as current member`() {
        assertEquals(204, put(validPng).statusCode())
        read.snapshot = snapshot(GroupRole.ATHLETE, 8)
        assertEquals(200, get().statusCode())
    }

    @Test fun `nonmember GET is indistinguishable from missing photo`() {
        assertEquals(204, put(validPng).statusCode())
        read.snapshot = snapshot(null, 8)
        assertProblem(get(), 404, "GROUP_NOT_FOUND")
    }

    @Test fun `missing photo GET is not found without storage detail`() {
        val response = get()
        assertProblem(response, 404, "GROUP_NOT_FOUND")
        assertFalse(response.body().decodeToString().contains("photo_bytes"))
    }

    @Test fun `malformed group photo path is privacy preserving not found`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/not-a-group/photo"))
                .header("Authorization", "Bearer photo-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        assertProblem(response, 404, "GROUP_NOT_FOUND")
    }

    @Test fun `matching If-None-Match returns 304 without bytes`() {
        assertEquals(204, put(validPng).statusCode())
        val response = get("\"photo-1\"")
        assertEquals(304, response.statusCode())
        assertEquals("\"photo-1\"", header(response, "ETag"))
        assertEquals(0, response.body().size)
    }

    @Test fun `owner DELETE removes photo and returns group ETag`() {
        assertEquals(204, put(validPng).statusCode())
        val response = delete("\"8\"")
        assertEquals(204, response.statusCode())
        assertEquals("\"9\"", header(response, "ETag"))
        assertNull(photos.photo)
    }

    @Test fun `athlete DELETE is forbidden without removal`() {
        assertEquals(204, put(validPng).statusCode())
        read.snapshot = snapshot(GroupRole.ATHLETE, 8)
        assertProblem(delete("\"8\""), 403, "ACCESS_FORBIDDEN")
        assertNotNull(photos.photo)
    }

    @Test fun `stale DELETE returns conflict and preserves photo`() {
        assertEquals(204, put(validPng).statusCode())
        assertProblem(delete("\"7\""), 409, "VERSION_CONFLICT")
        assertNotNull(photos.photo)
    }

    @Test fun `DELETE requires If-Match`() {
        assertProblem(delete(null), 428, "PRECONDITION_REQUIRED")
    }

    @Test fun `repeated DELETE is idempotent at current version`() {
        assertEquals(204, put(validPng).statusCode())
        assertEquals(204, delete("\"8\"").statusCode())
        val response = delete("\"9\"")
        assertEquals(204, response.statusCode())
        assertEquals("\"9\"", header(response, "ETag"))
    }

    private fun put(bytes: ByteArray, etag: String? = "\"7\"", contentType: String = "image/png"): HttpResponse<ByteArray> {
        val boundary = "saqz-photo-boundary"
        val prefix = (
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\n" +
                "Content-Type: $contentType\r\n\r\n"
            ).encodeToByteArray()
        val body = prefix + bytes + "\r\n--$boundary--\r\n".encodeToByteArray()
        val builder = request("PUT")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
        if (etag != null) builder.header("If-Match", etag)
        return send(builder)
    }

    private fun get(ifNoneMatch: String? = null): HttpResponse<ByteArray> {
        val builder = request("GET").GET()
        if (ifNoneMatch != null) builder.header("If-None-Match", ifNoneMatch)
        return send(builder)
    }

    private fun delete(etag: String?): HttpResponse<ByteArray> {
        val builder = request("DELETE").DELETE()
        if (etag != null) builder.header("If-Match", etag)
        return send(builder)
    }

    private fun request(method: String): HttpRequest.Builder = HttpRequest.newBuilder(
        URI("http://127.0.0.1:$port/api/groups/$groupId/photo"),
    ).header("Authorization", "Bearer photo-token").method(method, HttpRequest.BodyPublishers.noBody())

    private fun send(builder: HttpRequest.Builder): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun header(response: HttpResponse<*>, name: String): String = response.headers().firstValue(name).orElse("")

    private fun assertProblem(response: HttpResponse<ByteArray>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", header(response, "Content-Type"))
        assertEquals(code, objectMapper.readTree(response.body())["code"].stringValue())
    }

    private fun snapshot(role: GroupRole?, version: Long) = GroupReadSnapshot(
        id = groupId,
        name = AccessName.from("Photo Group"),
        timeZone = IanaTimeZone.from("UTC"),
        role = role,
        version = version,
        profileStatus = GroupProfileStatus.COMPLETE,
    )

    private fun png(color: Int = 0xFF0000): ByteArray = ByteArrayOutputStream().use { output ->
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, color)
        assertTrue(ImageIO.write(image, "png", output))
        output.toByteArray()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PhotoTestConfiguration {
        @Bean @Primary fun photoVerifier() = PhotoVerifier()
        @Bean fun photoSessionRepository() = PhotoSessionRepository()
        @Bean fun photoBootstrap(repository: PhotoSessionRepository) = BootstrapSession(repository)
        @Bean fun photoReadRepository() = PhotoReadRepository()
        @Bean fun photoGetGroup(read: PhotoReadRepository) = GetGroup(read, GroupAccessPolicy())
        @Bean fun recordingPhotoRepository() = RecordingPhotoRepository()
        @Bean fun testPhotoValidator() = GroupPhotoValidator()
        @Bean fun testPhotoService(
            getGroup: GetGroup,
            validator: GroupPhotoValidator,
            photos: RecordingPhotoRepository,
        ) = GroupPhotoService(getGroup, validator, photos)
        @Bean fun testPhotoController(bootstrap: BootstrapSession, service: GroupPhotoService) =
            GroupPhotoController(verifiedGroupActorResolver(bootstrap), service)

        companion object { val USER_ID: UUID = UUID.randomUUID() }
    }

    class PhotoVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("photo-subject", "photo@example.test", true, "Photo Person"),
        )
    }

    class PhotoSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(PhotoTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class PhotoReadRepository : GroupReadRepository {
        var snapshot: GroupReadSnapshot? = null
        override fun find(key: GroupReadKey): GroupReadSnapshot? = snapshot
    }

    class RecordingPhotoRepository : GroupPhotoRepository {
        var photo: StoredGroupPhoto? = null
        private var groupVersion: Long = 7

        fun reset(version: Long) { photo = null; groupVersion = version }

        override fun replace(command: ReplaceGroupPhotoCommand): GroupPhotoWriteResult {
            if (command.expectedGroupVersion != groupVersion) return GroupPhotoWriteResult.VersionConflict
            groupVersion += 1
            val stored = StoredGroupPhoto(
                command.groupId,
                command.photo.bytes,
                command.photo.mediaType,
                command.photo.byteSize,
                command.photo.width,
                command.photo.height,
                command.photo.sha256Digest,
                (photo?.version ?: 0) + 1,
                command.actorId,
            )
            photo = stored
            return GroupPhotoWriteResult.Replaced(stored, groupVersion)
        }

        override fun remove(groupId: UUID, expectedGroupVersion: Long): GroupPhotoWriteResult {
            if (expectedGroupVersion != groupVersion) return GroupPhotoWriteResult.VersionConflict
            if (photo == null) return GroupPhotoWriteResult.AlreadyAbsent(groupVersion)
            photo = null
            groupVersion += 1
            return GroupPhotoWriteResult.Removed(groupVersion)
        }

        override fun read(groupId: UUID): StoredGroupPhoto? = photo
        override fun readMetadata(groupId: UUID): GroupPhotoMetadata? = photo?.let {
            GroupPhotoMetadata(it.groupId, it.mediaType, it.byteSize, it.width, it.height, it.version)
        }
    }
}
