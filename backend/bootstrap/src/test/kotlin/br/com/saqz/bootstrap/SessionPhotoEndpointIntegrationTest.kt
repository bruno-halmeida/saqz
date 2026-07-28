package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.UserPhotoController
import br.com.saqz.access.adapter.output.media.UserPhotoConverter
import br.com.saqz.access.application.photo.StoredUserPhoto
import br.com.saqz.access.application.photo.UserPhotoImage
import br.com.saqz.access.application.photo.UserPhotoRepository
import br.com.saqz.access.application.photo.UserPhotoService
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertEquals
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
@Import(SessionPhotoEndpointIntegrationTest.SessionPhotoTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class SessionPhotoEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var photos: RecordingUserPhotoRepository
    @Autowired private lateinit var objectMapper: ObjectMapper
    private val largePng by lazy { png(1000, 600) }

    @BeforeEach
    fun reset() {
        photos.reset()
    }

    @Test
    fun `envio recomprime a foto para JPEG pequeno e devolve a ETag da versao`() {
        val response = put(largePng)

        assertEquals(204, response.statusCode())
        assertEquals("\"photo-1\"", header(response, "ETag"))
        val stored = assertNotNull(photos.photo)
        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(stored.bytes)))
        assertEquals(512, decoded.width)
        assertEquals(307, decoded.height)
        assertEquals(stored.bytes.size.toLong(), stored.byteSize)
    }

    @Test
    fun `segundo envio substitui a foto e avanca a versao`() {
        assertEquals(204, put(largePng).statusCode())
        val first = photos.photo?.bytes

        val response = put(png(40, 40))

        assertEquals(204, response.statusCode())
        assertEquals("\"photo-2\"", header(response, "ETag"))
        assertTrue(!first.contentEquals(photos.photo?.bytes))
    }

    @Test
    fun `leitura devolve JPEG privado com ETag e a mesma foto guardada`() {
        assertEquals(204, put(largePng).statusCode())

        val response = get()

        assertEquals(200, response.statusCode())
        assertEquals("image/jpeg", header(response, "Content-Type"))
        assertEquals("private, no-cache", header(response, "Cache-Control"))
        assertEquals("\"photo-1\"", header(response, "ETag"))
        assertEquals(photos.photo?.bytes?.size, response.body().size)
    }

    @Test
    fun `If-None-Match igual devolve 304 sem bytes`() {
        assertEquals(204, put(largePng).statusCode())

        val response = get("\"photo-1\"")

        assertEquals(304, response.statusCode())
        assertEquals(0, response.body().size)
    }

    @Test
    fun `remocao apaga a foto e a leitura seguinte e um problema estavel`() {
        assertEquals(204, put(largePng).statusCode())

        assertEquals(204, delete().statusCode())

        assertNull(photos.photo)
        assertProblem(get(), 404, "PHOTO_NOT_FOUND")
    }

    @Test
    fun `remocao repetida continua idempotente`() {
        assertEquals(204, put(largePng).statusCode())

        assertEquals(204, delete().statusCode())
        assertEquals(204, delete().statusCode())
    }

    @Test
    fun `tipo declarado fora da lista aceita e recusado sem gravar`() {
        assertProblem(put(largePng, contentType = "image/gif"), 400, "PHOTO_INVALID")
        assertNull(photos.photo)
    }

    @Test
    fun `bytes corrompidos sao recusados sem gravar`() {
        val corrupt = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01)

        assertProblem(put(corrupt), 400, "PHOTO_INVALID")
        assertNull(photos.photo)
    }

    @Test
    fun `envio acima do limite do servlet vira problema de tamanho`() {
        assertProblem(put(ByteArray(5 * 1024 * 1024 + 1)), 413, "PHOTO_TOO_LARGE")
        assertNull(photos.photo)
    }

    @Test
    fun `parte multipart ausente vira problema estavel`() {
        val boundary = "saqz-empty-boundary"
        val builder = request()
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .PUT(HttpRequest.BodyPublishers.ofString("--$boundary--\r\n"))

        assertProblem(send(builder), 400, "PHOTO_INVALID")
        assertNull(photos.photo)
    }

    private fun put(bytes: ByteArray, contentType: String = "image/png"): HttpResponse<ByteArray> {
        val boundary = "saqz-user-photo-boundary"
        val prefix = (
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"avatar.png\"\r\n" +
                "Content-Type: $contentType\r\n\r\n"
            ).encodeToByteArray()
        val body = prefix + bytes + "\r\n--$boundary--\r\n".encodeToByteArray()
        return send(
            request()
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)),
        )
    }

    private fun get(ifNoneMatch: String? = null): HttpResponse<ByteArray> {
        val builder = request().GET()
        if (ifNoneMatch != null) builder.header("If-None-Match", ifNoneMatch)
        return send(builder)
    }

    private fun delete(): HttpResponse<ByteArray> = send(request().DELETE())

    private fun request(): HttpRequest.Builder = HttpRequest.newBuilder(
        URI("http://127.0.0.1:$port/api/session/photo"),
    ).header("Authorization", "Bearer photo-token")

    private fun send(builder: HttpRequest.Builder): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun header(response: HttpResponse<*>, name: String): String = response.headers().firstValue(name).orElse("")

    private fun assertProblem(response: HttpResponse<ByteArray>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", header(response, "Content-Type"))
        assertEquals(code, objectMapper.readTree(response.body())["code"].stringValue())
    }

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        assertTrue(ImageIO.write(image, "png", output))
        output.toByteArray()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SessionPhotoTestConfiguration {
        @Bean @Primary fun userPhotoVerifier() = UserPhotoVerifier()
        @Bean fun userPhotoSessionRepository() = UserPhotoSessionRepository()
        @Bean fun userPhotoBootstrap(repository: UserPhotoSessionRepository) = BootstrapSession(repository)
        @Bean fun recordingUserPhotoRepository() = RecordingUserPhotoRepository()
        @Bean fun testUserPhotoService(photos: RecordingUserPhotoRepository) =
            UserPhotoService(UserPhotoConverter(), photos)
        @Bean fun testUserPhotoController(bootstrap: BootstrapSession, service: UserPhotoService) =
            UserPhotoController(bootstrap, service)

        companion object { val USER_ID: UUID = UUID.randomUUID() }
    }

    class UserPhotoVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("user-photo-subject", "avatar@example.test", true, "Photo Person"),
        )
    }

    class UserPhotoSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(
                SessionPhotoTestConfiguration.USER_ID,
                command.subject,
                command.email,
                command.displayName,
            ),
            emptyList(),
        )
    }

    class RecordingUserPhotoRepository : UserPhotoRepository {
        var photo: StoredUserPhoto? = null

        fun reset() {
            photo = null
        }

        override fun replace(userId: UUID, photo: UserPhotoImage): Long {
            val version = (this.photo?.version ?: 0) + 1
            this.photo = StoredUserPhoto(photo.bytes, photo.byteSize, version)
            return version
        }

        override fun remove(userId: UUID) {
            photo = null
        }

        override fun read(userId: UUID): StoredUserPhoto? = photo
    }
}
