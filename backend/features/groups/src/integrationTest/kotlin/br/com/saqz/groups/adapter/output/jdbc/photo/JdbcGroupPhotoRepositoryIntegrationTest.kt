package br.com.saqz.groups.adapter.output.jdbc.photo

import br.com.saqz.groups.application.photo.GroupPhotoMediaType
import br.com.saqz.groups.application.photo.GroupPhotoWriteResult
import br.com.saqz.groups.application.photo.ReplaceGroupPhotoCommand
import br.com.saqz.groups.application.photo.ValidatedGroupPhoto
import br.com.saqz.groups.testing.accessMigrationLocation
import br.com.saqz.groups.testing.startAndAwaitJdbc
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGroupPhotoRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcGroupPhotoRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations(accessMigrationLocation()).load().migrate()
        repository = JdbcGroupPhotoRepository(dataSource)
    }

    @AfterAll fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() = execute(
        "TRUNCATE group_photos, group_invites, group_memberships, access_groups, " +
            "invite_redemption_limits, access_users CASCADE",
    )

    @Test fun `migration stores one complete private photo record per group`() {
        val owner = insertUser()
        val group = insertGroup(owner)

        val result = assertIs<GroupPhotoWriteResult.Replaced>(repository.replace(command(group, owner, photo("first"))))

        assertEquals(2, result.groupVersion)
        assertEquals("image/png|5|2|3|1|$owner", photoRow(group))
        assertContentEquals("first".encodeToByteArray(), repository.read(group)?.bytes)
    }

    @Test fun `replacement increments both group and photo versions`() {
        val owner = insertUser()
        val group = insertGroup(owner)
        repository.replace(command(group, owner, photo("first")))

        val result = assertIs<GroupPhotoWriteResult.Replaced>(repository.replace(command(group, owner, photo("second"), 2)))

        assertEquals(3, result.groupVersion)
        assertEquals(2, result.photo.version)
        assertContentEquals("second".encodeToByteArray(), result.photo.bytes)
    }

    @Test fun `replacement persists exact SHA-256 digest`() {
        val owner = insertUser()
        val group = insertGroup(owner)
        val expected = photo("digest")

        repository.replace(command(group, owner, expected))

        assertContentEquals(expected.sha256Digest, repository.read(group)?.sha256Digest)
    }

    @Test fun `stale replacement changes neither group nor old photo`() {
        val owner = insertUser()
        val group = insertGroup(owner)
        repository.replace(command(group, owner, photo("first")))

        assertEquals(GroupPhotoWriteResult.VersionConflict, repository.replace(command(group, owner, photo("stale"), 1)))

        assertEquals(2, groupVersion(group))
        assertContentEquals("first".encodeToByteArray(), repository.read(group)?.bytes)
        assertEquals(1, repository.read(group)?.version)
    }

    @Test fun `removal deletes photo and increments group version atomically`() {
        val owner = insertUser()
        val group = insertGroup(owner)
        repository.replace(command(group, owner, photo("first")))

        val result = assertIs<GroupPhotoWriteResult.Removed>(repository.remove(group, 2))

        assertEquals(3, result.groupVersion)
        assertNull(repository.read(group))
    }

    @Test fun `repeated removal is idempotent without another version increment`() {
        val owner = insertUser()
        val group = insertGroup(owner)

        val result = assertIs<GroupPhotoWriteResult.AlreadyAbsent>(repository.remove(group, 1))

        assertEquals(1, result.groupVersion)
        assertEquals(1, groupVersion(group))
    }

    @Test fun `injected storage failure rolls back group version and preserves old photo`() {
        val owner = insertUser()
        val group = insertGroup(owner)
        repository.replace(command(group, owner, photo("first")))
        execute(
            """
            CREATE FUNCTION reject_photo_update() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN RAISE EXCEPTION 'injected photo failure'; END ${'$'}${'$'};
            CREATE TRIGGER reject_photo_update BEFORE UPDATE ON group_photos
            FOR EACH ROW EXECUTE FUNCTION reject_photo_update();
            """.trimIndent(),
        )
        try {
            assertFailsWith<RuntimeException> { repository.replace(command(group, owner, photo("second"), 2)) }
        } finally {
            execute("DROP TRIGGER reject_photo_update ON group_photos; DROP FUNCTION reject_photo_update()")
        }

        assertEquals(2, groupVersion(group))
        assertContentEquals("first".encodeToByteArray(), repository.read(group)?.bytes)
        assertEquals(1, repository.read(group)?.version)
    }

    @Test fun `metadata read excludes bytes while preserving cache identity`() {
        val owner = insertUser()
        val group = insertGroup(owner)
        repository.replace(command(group, owner, photo("private-bytes")))

        val metadata = requireNotNull(repository.readMetadata(group))

        assertEquals(GroupPhotoMediaType.PNG, metadata.mediaType)
        assertEquals(13, metadata.byteSize)
        assertEquals(1, metadata.version)
    }

    @Test fun `database rejects bytes whose stored size does not match`() {
        val owner = insertUser()
        val group = insertGroup(owner)

        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO group_photos (group_id, photo_bytes, media_type, byte_size, width, height, " +
                    "sha256_digest, version, updated_by, created_at, updated_at) VALUES " +
                    "('$group', decode('01','hex'), 'image/png', 2, 1, 1, decode('${"00".repeat(32)}','hex'), 1, " +
                    "'$owner', now(), now())",
            )
        }
    }

    @Test fun `database primary key prevents two photos for one group`() {
        val owner = insertUser()
        val group = insertGroup(owner)
        repository.replace(command(group, owner, photo("first")))

        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO group_photos SELECT * FROM group_photos WHERE group_id = '$group'",
            )
        }
    }

    private fun command(group: UUID, actor: UUID, photo: ValidatedGroupPhoto, version: Long = 1) =
        ReplaceGroupPhotoCommand(group, version, actor, photo)

    private fun photo(value: String): ValidatedGroupPhoto {
        val bytes = value.encodeToByteArray()
        return ValidatedGroupPhoto(
            bytes,
            GroupPhotoMediaType.PNG,
            width = 2,
            height = 3,
            sha256Digest = MessageDigest.getInstance("SHA-256").digest(bytes),
        )
    }

    private fun insertUser(): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'subject-$id', true, 'Photo Owner', now(), now())",
        )
    }

    private fun insertGroup(owner: UUID): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, version, created_at, updated_at) " +
                "VALUES ('$id', '$owner', '${UUID.randomUUID()}', 'Photo Group', 'America/Sao_Paulo', 1, now(), now())",
        )
    }

    private fun photoRow(group: UUID): String = query(
        "SELECT media_type || '|' || byte_size || '|' || width || '|' || height || '|' || version || '|' || updated_by " +
            "FROM group_photos WHERE group_id = '$group'",
    )

    private fun groupVersion(group: UUID): Long = query("SELECT version FROM access_groups WHERE id = '$group'").toLong()

    private fun query(sql: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result -> result.next(); result.getString(1) }
        }
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun connection(): Connection = dataSource.connection
}
