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
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
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
    fun `primeiro envio grava a versao um e devolve os bytes exatos`() {
        val userId = bootstrapUser("subject-upload")

        val version = photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))

        assertEquals(1, version)
        val stored = photos.read(userId)
        assertContentEquals(byteArrayOf(1, 2, 3), stored?.bytes)
        assertEquals(3, stored?.byteSize)
        assertEquals(1, stored?.version)
    }

    @Test
    fun `substituicao troca os bytes e avanca a versao sem duplicar a linha`() {
        val userId = bootstrapUser("subject-replace")
        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))

        val version = photos.replace(userId, image(byteArrayOf(9, 9), 32, 32))

        assertEquals(2, version)
        val stored = photos.read(userId)
        assertContentEquals(byteArrayOf(9, 9), stored?.bytes)
        assertEquals(2, stored?.byteSize)
        assertEquals(1, count("SELECT count(*) FROM access_user_photos"))
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
    fun `a sessao carrega a versao atual da foto e volta a null depois da remocao`() {
        val userId = bootstrapUser("subject-session-photo")
        photos.replace(userId, image(byteArrayOf(1, 2, 3), 64, 48))
        photos.replace(userId, image(byteArrayOf(4, 5, 6), 64, 48))

        assertEquals(2, sessions.upsertAndLoad(upsert("subject-session-photo")).user.photoVersion)

        photos.remove(userId)
        assertNull(sessions.upsertAndLoad(upsert("subject-session-photo")).user.photoVersion)
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

        assertFailsWith<Exception> {
            execute(
                "INSERT INTO access_user_photos " +
                    "(user_id, photo_bytes, byte_size, width, height, version, created_at, updated_at) VALUES " +
                    "('$userId', decode('aabb', 'hex'), 3, 64, 48, 1, now(), now())",
            )
        }
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

    private fun image(bytes: ByteArray, width: Int, height: Int) = UserPhotoImage(bytes, width, height)

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
}
