package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.testing.startAndAwaitJdbc
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.ProfileCompletion
import br.com.saqz.access.application.session.PhoneVisibility
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber
import br.com.saqz.sharedkernel.RequestIdentity
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSessionRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcSessionRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource)
            .locations("classpath:db/migration", groupMigrationLocation())
            .load()
            .migrate()
        repository = JdbcSessionRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE group_invites, group_memberships, access_groups, " +
                "invite_redemption_limits, access_users CASCADE",
        )
    }

    @Test
    fun `inserts Firebase UID mirrors and returns an empty session`() {
        val session = repository.upsertAndLoad(command("subject-new"))

        assertEquals("subject-new", session.user.firebaseSubject)
        assertEquals("person@example.test", session.user.email)
        assertEquals("Person Name", session.user.displayName.value)
        assertTrue(session.memberships.isEmpty())
        assertEquals(1, count("SELECT count(*) FROM access_users"))
    }

    @Test
    fun `retry with the same Firebase UID returns one stable user ID`() {
        val first = repository.upsertAndLoad(command("subject-retry"))
        val second = repository.upsertAndLoad(command("subject-retry"))

        assertEquals(first.user.id, second.user.id)
        assertEquals(1, count("SELECT count(*) FROM access_users"))
    }

    @Test
    fun `unverified account is inserted with email_verified false`() {
        val session = repository.upsertAndLoad(command("subject-unverified", emailVerified = false))

        assertEquals(false, verifiedFlag(session.user.id))
    }

    @Test
    fun `verified account is inserted with email_verified true`() {
        val session = repository.upsertAndLoad(command("subject-verified", emailVerified = true))

        assertEquals(true, verifiedFlag(session.user.id))
    }

    @Test
    fun `confirming the email later flips the persisted flag on the same user`() {
        val before = repository.upsertAndLoad(command("subject-confirms", emailVerified = false))
        assertEquals(false, verifiedFlag(before.user.id))

        val after = repository.upsertAndLoad(command("subject-confirms", emailVerified = true))

        assertEquals(before.user.id, after.user.id)
        assertEquals(true, verifiedFlag(after.user.id))
    }

    @Test
    fun `an unconfirmed claim overwrites a previously verified row`() {
        val before = repository.upsertAndLoad(command("subject-regresses", emailVerified = true))
        assertEquals(true, verifiedFlag(before.user.id))

        val after = repository.upsertAndLoad(command("subject-regresses", emailVerified = false))

        assertEquals(before.user.id, after.user.id)
        assertEquals(false, verifiedFlag(after.user.id))
    }

    @Test
    fun `equal emails with different Firebase UIDs create distinct users`() {
        val first = repository.upsertAndLoad(command("subject-one"))
        val second = repository.upsertAndLoad(command("subject-two"))

        assertNotEquals(first.user.id, second.user.id)
        assertEquals(2, count("SELECT count(*) FROM access_users"))
    }

    @Test
    fun `changed email and display name update mirrors without changing user ID`() {
        val original = repository.upsertAndLoad(command("subject-update"))
        val updated = repository.upsertAndLoad(
            SessionUpsert("subject-update", "changed@example.test", true, AccessName.from("Changed Name")),
        )

        assertEquals(original.user.id, updated.user.id)
        assertEquals("changed@example.test", updated.user.email)
        assertEquals("Changed Name", updated.user.displayName.value)
        assertEquals("changed@example.test", text("SELECT email FROM access_users WHERE id = '${updated.user.id}'"))
    }

    @Test
    fun `email mirror can become null without changing user ID`() {
        val original = repository.upsertAndLoad(command("subject-null-email"))
        val updated = repository.upsertAndLoad(
            SessionUpsert("subject-null-email", null, true, AccessName.from("Person Name")),
        )

        assertEquals(original.user.id, updated.user.id)
        assertEquals(null, updated.user.email)
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE id = '${updated.user.id}' AND email IS NULL"))
    }

    @Test
    fun `group ownership is synthesized as owner membership`() {
        val owner = repository.upsertAndLoad(command("owner-subject"))
        val group = insertGroup(owner.user.id, "Owner Group")

        val refreshed = repository.upsertAndLoad(command("owner-subject"))

        assertEquals(listOf(group), refreshed.memberships.map { it.groupId })
        assertEquals("OWNER", refreshed.memberships.single().role)
    }

    @Test
    fun `deleted owned and joined groups are absent from session memberships`() {
        val owner = repository.upsertAndLoad(command("deleted-session-owner"))
        val member = repository.upsertAndLoad(command("deleted-session-member"))
        val deletedOwned = insertGroup(owner.user.id, "Deleted Owned Group")
        val deletedJoined = insertGroup(owner.user.id, "Deleted Joined Group")
        val active = insertGroup(owner.user.id, "Active Group")
        insertMembership(deletedJoined, member.user.id, "ATHLETE")
        insertMembership(active, member.user.id, "ATHLETE")
        execute("UPDATE access_groups SET deleted_at = now() WHERE id IN ('$deletedOwned', '$deletedJoined')")

        val ownerSession = repository.upsertAndLoad(command("deleted-session-owner"))
        val memberSession = repository.upsertAndLoad(command("deleted-session-member"))

        assertEquals(listOf(active), ownerSession.memberships.map { it.groupId })
        assertEquals(listOf(active), memberSession.memberships.map { it.groupId })
    }

    @Test
    fun `persisted admin and athlete memberships keep their exact roles`() {
        val owner = repository.upsertAndLoad(command("roles-owner"))
        val member = repository.upsertAndLoad(command("roles-member"))
        val adminGroup = insertGroup(owner.user.id, "Admin Group")
        val athleteGroup = insertGroup(owner.user.id, "Athlete Group")
        insertMembership(adminGroup, member.user.id, "ADMIN")
        insertMembership(athleteGroup, member.user.id, "ATHLETE")

        val roles = repository.upsertAndLoad(command("roles-member")).memberships.associate { it.groupId to it.role }

        assertEquals(mapOf(adminGroup to "ADMIN", athleteGroup to "ATHLETE"), roles)
    }

    @Test
    fun `memberships are returned in stable group name and ID order`() {
        val owner = repository.upsertAndLoad(command("order-owner"))
        val member = repository.upsertAndLoad(command("order-member"))
        val beta = insertGroup(owner.user.id, "Beta Group")
        val alpha = insertGroup(owner.user.id, "Alpha Group")
        insertMembership(beta, member.user.id, "ATHLETE")
        insertMembership(alpha, member.user.id, "ATHLETE")

        val first = repository.upsertAndLoad(command("order-member")).memberships
        val second = repository.upsertAndLoad(command("order-member")).memberships

        assertEquals(listOf(alpha, beta), first.map { it.groupId })
        assertEquals(first, second)
    }

    @Test
    fun `mirror update preserves existing memberships and roles`() {
        val owner = repository.upsertAndLoad(command("preserve-owner"))
        val member = repository.upsertAndLoad(command("preserve-member"))
        val group = insertGroup(owner.user.id, "Preserved Group")
        insertMembership(group, member.user.id, "ADMIN")

        val refreshed = repository.upsertAndLoad(
            SessionUpsert("preserve-member", "new@example.test", true, AccessName.from("New Name")),
        )

        assertEquals(listOf(group), refreshed.memberships.map { it.groupId })
        assertEquals("ADMIN", refreshed.memberships.single().role)
    }

    @Test
    fun `two concurrent connections upsert one UID and return the same user ID`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val calls = List(2) {
            Callable {
                ready.countDown()
                start.await()
                repository.upsertAndLoad(command("subject-concurrent")).user.id
            }
        }
        val futures = calls.map(pool::submit)
        ready.await()
        start.countDown()
        val ids = futures.map { it.get() }
        pool.shutdown()

        assertEquals(ids[0], ids[1])
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE firebase_subject = 'subject-concurrent'"))
    }

    @Test
    fun `phone is null until the profile is completed`() {
        val session = repository.upsertAndLoad(command("subject-no-phone"))

        assertEquals(null, session.user.phone)
        assertEquals(null, session.user.nickname)
        assertEquals(null, session.user.city)
        assertEquals("ADMINS", session.user.phoneVisibility)
    }

    @Test
    fun `updateProfile persists all editable profile fields`() {
        repository.upsertAndLoad(command("subject-profile-fields"))

        val updated = repository.updateProfile(
            ProfileCompletion(
                subject = "subject-profile-fields",
                phone = PhoneNumber.from("+5511911112222"),
                displayName = AccessName.from("Rafael Costa"),
                nickname = "Rafa",
                city = "São Paulo, SP",
                phoneVisibility = PhoneVisibility.EVERYONE,
                phoneProvided = true,
                displayNameProvided = true,
                nicknameProvided = true,
                cityProvided = true,
                phoneVisibilityProvided = true,
            ),
        )!!

        assertEquals("Rafael Costa", updated.user.displayName.value)
        assertEquals("Rafa", updated.user.nickname)
        assertEquals("São Paulo, SP", updated.user.city)
        assertEquals("EVERYONE", updated.user.phoneVisibility)
    }

    @Test
    fun `updateProfile preserves fields omitted from the patch`() {
        repository.upsertAndLoad(command("subject-profile-omitted"))
        repository.updateProfile(
            ProfileCompletion(
                subject = "subject-profile-omitted",
                phone = PhoneNumber.from("+5511911112222"),
                displayName = AccessName.from("Rafael Costa"),
                nickname = "Rafa",
                city = "São Paulo, SP",
                phoneVisibility = PhoneVisibility.NOBODY,
                phoneProvided = true,
                displayNameProvided = true,
                nicknameProvided = true,
                cityProvided = true,
                phoneVisibilityProvided = true,
            ),
        )

        val unchanged = repository.updateProfile(
            ProfileCompletion(
                subject = "subject-profile-omitted",
                phone = null,
                displayName = null,
                phoneProvided = false,
            ),
        )!!

        assertEquals("+5511911112222", unchanged.user.phone?.value)
        assertEquals("Rafael Costa", unchanged.user.displayName.value)
        assertEquals("Rafa", unchanged.user.nickname)
        assertEquals("São Paulo, SP", unchanged.user.city)
        assertEquals("NOBODY", unchanged.user.phoneVisibility)
    }

    @Test
    fun `updateProfile persists phone and reloads it on the next session`() {
        repository.upsertAndLoad(command("subject-phone"))

        val updated = repository.updateProfile(
            ProfileCompletion("subject-phone", PhoneNumber.from("+5511911112222"), null),
        )!!
        val reloaded = repository.upsertAndLoad(command("subject-phone"))

        assertEquals(PhoneNumber.from("+5511911112222"), updated.user.phone)
        assertEquals(PhoneNumber.from("+5511911112222"), reloaded.user.phone)
        assertEquals(updated.user.id, reloaded.user.id)
    }

    @Test
    fun `updateProfile does not change the display name when none is supplied`() {
        repository.upsertAndLoad(command("subject-keep-name"))

        val updated = repository.updateProfile(
            ProfileCompletion("subject-keep-name", PhoneNumber.from("+5511911112222"), null),
        )!!

        assertEquals("Person Name", updated.user.displayName.value)
    }

    @Test
    fun `updateProfile overwrites the display name when supplied`() {
        repository.upsertAndLoad(command("subject-rename"))

        val updated = repository.updateProfile(
            ProfileCompletion("subject-rename", PhoneNumber.from("+5511911112222"), AccessName.from("Renamed Person")),
        )!!

        assertEquals("Renamed Person", updated.user.displayName.value)
    }

    @Test
    fun `repeat updateProfile with the same phone is an idempotent overwrite`() {
        repository.upsertAndLoad(command("subject-repeat"))

        repository.updateProfile(ProfileCompletion("subject-repeat", PhoneNumber.from("+5511911112222"), null))
        repository.updateProfile(ProfileCompletion("subject-repeat", PhoneNumber.from("+5511911112222"), null))

        assertEquals(1, count("SELECT count(*) FROM access_users WHERE firebase_subject = 'subject-repeat'"))
        assertEquals(
            "+5511911112222",
            text("SELECT phone FROM access_users WHERE firebase_subject = 'subject-repeat'"),
        )
    }

    @Test
    fun `updateProfile preserves existing memberships`() {
        val owner = repository.upsertAndLoad(command("profile-owner"))
        val member = repository.upsertAndLoad(command("profile-member"))
        val group = insertGroup(owner.user.id, "Profile Group")
        insertMembership(group, member.user.id, "ADMIN")

        val updated = repository.updateProfile(
            ProfileCompletion("profile-member", PhoneNumber.from("+5511911112222"), null),
        )!!

        assertEquals(listOf(group), updated.memberships.map { it.groupId })
        assertEquals("ADMIN", updated.memberships.single().role)
    }

    @Test
    fun `updateProfile returns null for a subject with no bootstrapped account`() {
        val result = repository.updateProfile(
            ProfileCompletion("subject-never-bootstrapped", PhoneNumber.from("+5511911112222"), null),
        )

        assertEquals(null, result)
    }

    @Test
    fun `soft delete clears personal fields and photo while preserving the display name`() {
        val session = repository.upsertAndLoad(command("subject-delete"))
        repository.updateProfile(
            ProfileCompletion(
                subject = "subject-delete",
                phone = PhoneNumber.from("+5511911112222"),
                displayName = AccessName.from("Public Snapshot"),
                nickname = "Rafa",
                city = "São Paulo, SP",
                phoneVisibility = PhoneVisibility.NOBODY,
                phoneProvided = true,
                displayNameProvided = true,
                nicknameProvided = true,
                cityProvided = true,
                phoneVisibilityProvided = true,
            ),
        )
        execute(
            "INSERT INTO access_user_photos " +
                "(user_id, photo_bytes, byte_size, width, height, sha256_digest, created_at, updated_at) VALUES " +
                "('${session.user.id}', decode('01', 'hex'), 1, 1, 1, decode(repeat('ab', 32), 'hex'), now(), now())",
        )

        assertEquals(session.user.id, repository.softDelete("subject-delete"))
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE deleted_at IS NOT NULL"))
        assertEquals("Public Snapshot", text("SELECT display_name FROM access_users WHERE id = '${session.user.id}'"))
        assertNull(textOrNull("SELECT email FROM access_users WHERE id = '${session.user.id}'"))
        assertNull(textOrNull("SELECT phone FROM access_users WHERE id = '${session.user.id}'"))
        assertNull(textOrNull("SELECT nickname FROM access_users WHERE id = '${session.user.id}'"))
        assertNull(textOrNull("SELECT city FROM access_users WHERE id = '${session.user.id}'"))
        assertEquals(0, count("SELECT count(*) FROM access_user_photos WHERE user_id = '${session.user.id}'"))
    }

    @Test
    fun `deleted subject bootstraps a fresh user without old memberships`() {
        val deleted = repository.upsertAndLoad(command("subject-old"))
        val oldGroup = insertGroup(deleted.user.id, "Old Group")
        insertMembership(oldGroup, deleted.user.id, "ADMIN")
        repository.softDelete("subject-old")

        val result = BootstrapSession(repository).execute(
            RequestIdentity("subject-old", "person@example.test", true, "New Person"),
        )
        val replacement = assertIs<BootstrapSessionResult.Success>(result).session

        assertNotEquals(deleted.user.id, replacement.user.id)
        assertEquals("New Person", replacement.user.displayName.value)
        assertTrue(replacement.memberships.isEmpty())
        assertEquals(2, count("SELECT count(*) FROM access_users"))
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE deleted_at IS NOT NULL"))
        assertEquals(
            1,
            count("SELECT count(*) FROM group_memberships WHERE group_id = '$oldGroup' AND user_id = '${deleted.user.id}'"),
        )
    }

    private fun command(subject: String, emailVerified: Boolean = true) =
        SessionUpsert(subject, "person@example.test", emailVerified, AccessName.from("Person Name"))

    private fun verifiedFlag(userId: UUID): Boolean =
        count("SELECT count(*) FROM access_users WHERE id = '$userId' AND email_verified") == 1

    private fun insertGroup(ownerId: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups " +
                "(id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES " +
                "('$id', '$ownerId', '${UUID.randomUUID()}', '$name', 'UTC', now(), now())",
        )
        return id
    }

    private fun insertMembership(groupId: UUID, userId: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$groupId', '$userId', '$role', now(), now())",
        )
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun count(sql: String): Int =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun text(sql: String): String =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getString(1)
                }
            }
        }

    private fun textOrNull(sql: String): String? =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getString(1)
                }
            }
        }

    private fun connection(): Connection = dataSource.connection

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
}
