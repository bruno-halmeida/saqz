package br.com.saqz.access.adapter.output.jdbc.photo

import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.application.photo.UserPhotoImage
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.Path
import java.util.HexFormat
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcUserPhotoRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var photos: JdbcUserPhotoRepository
    private lateinit var sessions: JdbcSessionRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource)
            .locations("classpath:db/migration", groupMigrationLocation())
            .load()
            .migrate()
        photos = JdbcUserPhotoRepository(dataSource)
        sessions = JdbcSessionRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute("TRUNCATE access_user_photos, access_users CASCADE") }
        }
    }

    @Test
    fun `primeiro envio devolve os bytes exatos e o digest do conteudo`() {
        val userId = bootstrapUser("subject-upload")

        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))

        val stored = photos.read(userId)
        assertContentEquals(byteArrayOf(1, 2, 3), stored?.bytes)
        assertEquals(3, stored?.byteSize)
        assertEquals(hex(digest(byteArrayOf(1, 2, 3))), stored?.digest)
    }

    @Test
    fun `substituicao troca os bytes e o digest sem duplicar a linha`() {
        val userId = bootstrapUser("subject-replace")
        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))
        val primeiro = photos.read(userId)?.digest

        photos.replace(userId, image(byteArrayOf(9, 9), 32, 32))

        val stored = photos.read(userId)
        assertContentEquals(byteArrayOf(9, 9), stored?.bytes)
        assertEquals(2, stored?.byteSize)
        assertNotEquals(primeiro, stored?.digest)
        assertEquals(1, count("SELECT count(*) FROM access_user_photos"))
    }

    @Test
    fun `apagar e reenviar outra foto nao reaproveita o validador da anterior`() {
        val userId = bootstrapUser("subject-recycle")
        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))
        val antes = photos.read(userId)?.digest

        photos.remove(userId)
        photos.replace(userId, image(byteArrayOf(7, 7, 7), 64, 48))

        assertNotEquals(antes, photos.read(userId)?.digest)
    }

    @Test
    fun `contas diferentes com fotos diferentes nunca compartilham validador`() {
        val primeira = bootstrapUser("subject-first-account")
        val segunda = bootstrapUser("subject-second-account")

        photos.replace(primeira, image(byteArrayOf(1, 2, 3), 64, 48))
        photos.replace(segunda, image(byteArrayOf(4, 5, 6), 64, 48))

        assertNotEquals(photos.read(primeira)?.digest, photos.read(segunda)?.digest)
    }

    @Test
    fun `remocao apaga a foto e repetir a remocao continua sem erro`() {
        val userId = bootstrapUser("subject-remove")
        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))

        photos.remove(userId)
        photos.remove(userId)

        assertNull(photos.read(userId))
        assertEquals(0, count("SELECT count(*) FROM access_user_photos"))
    }

    @Test
    fun `a sessao carrega o digest atual da foto e volta a null depois da remocao`() {
        val userId = bootstrapUser("subject-session-photo")
        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))
        photos.replace(userId, image(byteArrayOf(4, 5, 6), 64, 48))

        assertEquals(
            hex(digest(byteArrayOf(4, 5, 6))),
            sessions.upsertAndLoad(upsert("subject-session-photo")).user.photoDigest,
        )

        photos.remove(userId)
        assertNull(sessions.upsertAndLoad(upsert("subject-session-photo")).user.photoDigest)
        assertEquals(userId, sessions.upsertAndLoad(upsert("subject-session-photo")).user.id)
    }

    @Test
    fun `foto de usuario inexistente e recusada pela chave estrangeira`() {
        assertFailsWith<Exception> { photos.replace(UUID.randomUUID(), image(byteArrayOf(1), 8, 8)) }
    }

    @Test
    fun `o schema recusa dimensao acima do avatar recomprimido`() {
        val userId = bootstrapUser("subject-oversized")

        assertFailsWith<Exception> { photos.replace(userId, image(byteArrayOf(1, 2, 3), 513, 48)) }
    }

    @Test
    fun `o schema recusa byte_size divergente dos bytes gravados`() {
        val userId = bootstrapUser("subject-mismatch")

        assertFailsWith<Exception> { execute(photoInsert(userId, byteSize = 3, digestHex = VALID_DIGEST_HEX)) }
    }

    @Test
    fun `o schema recusa digest fora de 32 bytes`() {
        val userId = bootstrapUser("subject-short-digest")

        assertFailsWith<Exception> { execute(photoInsert(userId, byteSize = 2, digestHex = "aabb")) }
    }

    @Test
    fun `apagar a conta leva a foto junto`() {
        val userId = bootstrapUser("subject-cascade")
        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))

        execute("DELETE FROM access_users WHERE id = '$userId'")

        assertEquals(0, count("SELECT count(*) FROM access_user_photos"))
    }

    private fun bootstrapUser(subject: String): UUID = sessions.upsertAndLoad(upsert(subject)).user.id

    private fun upsert(subject: String) = SessionUpsert(
        subject = subject,
        email = "$subject@example.test",
        emailVerified = true,
        displayName = AccessName.from("Photo Person"),
    )

    private fun image(bytes: ByteArray, width: Int, height: Int) =
        UserPhotoImage(bytes, width, height, digest(bytes))

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)

    /** Sempre com dois bytes gravados, para o byteSize divergir quando o teste quiser. */
    private fun photoInsert(userId: UUID, byteSize: Int, digestHex: String): String =
        "INSERT INTO access_user_photos " +
            "(user_id, photo_bytes, byte_size, width, height, sha256_digest, created_at, updated_at) VALUES " +
            "('$userId', decode('aabb', 'hex'), $byteSize, 64, 48, decode('$digestHex', 'hex'), now(), now())"

    private fun execute(sql: String) = dataSource.connection.use { connection ->
        connection.createStatement().use { it.execute(sql) }
    }

    private fun count(sql: String): Int = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    private fun groupMigrationLocation(): String {
        var directory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            val candidates = listOf(
                directory.resolve("backend/features/groups/src/main/resources/db/migration"),
                directory.resolve("features/groups/src/main/resources/db/migration"),
                directory.resolve("groups/src/main/resources/db/migration"),
            )
            candidates.firstOrNull(Files::isDirectory)?.let { return "filesystem:$it" }
            directory = directory.parent ?: return@repeat
        }
        error("Cannot find groups migrations")
    }

    private companion object {
        val VALID_DIGEST_HEX = "ab".repeat(32)
    }
}
