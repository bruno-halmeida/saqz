package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BootstrapSessionTest {
    private val userId = UUID.randomUUID()
    private val memberships = listOf(
        SessionMembership(UUID.randomUUID(), AccessName.from("First Group"), GroupRole.OWNER),
    )
    private val view = SessionView(
        UserAccount(userId, "subject-1", "person@example.test", AccessName.from("Person Name")),
        memberships,
    )

    @Test
    fun `verified identity writes exact Firebase UID mirrors and returns session`() {
        val repository = RecordingSessionRepository(view)
        val useCase = BootstrapSession(repository)

        val result = useCase.execute(identity())

        assertEquals(BootstrapSessionResult.Success(view), result)
        assertEquals(
            listOf(SessionUpsert("subject-1", "person@example.test", AccessName.from("Person Name"))),
            repository.commands,
        )
    }

    @Test
    fun `verified identity without email remains valid`() {
        val repository = RecordingSessionRepository(view)

        assertEquals(BootstrapSessionResult.Success(view), BootstrapSession(repository).execute(identity(email = null)))
        assertEquals(null, repository.commands.single().email)
    }

    @Test
    fun `false email verification is rejected before write`() {
        assertBlocked(identity(emailVerified = false), BootstrapSessionResult.EmailNotVerified)
    }

    @Test
    fun `missing email verification is rejected before write`() {
        assertBlocked(identity(emailVerified = null), BootstrapSessionResult.EmailNotVerified)
    }

    @Test
    fun `missing display name is rejected before write`() {
        assertBlocked(identity(displayName = null), BootstrapSessionResult.InvalidDisplayName)
    }

    @Test
    fun `blank display name is rejected before write`() {
        assertBlocked(identity(displayName = "   "), BootstrapSessionResult.InvalidDisplayName)
    }

    @Test
    fun `one character display name is rejected before write`() {
        assertBlocked(identity(displayName = "a"), BootstrapSessionResult.InvalidDisplayName)
    }

    @Test
    fun `control character display name is rejected before write`() {
        assertBlocked(identity(displayName = "line\nbreak"), BootstrapSessionResult.InvalidDisplayName)
    }

    @Test
    fun `display name is trimmed before persistence`() {
        val repository = RecordingSessionRepository(view)

        BootstrapSession(repository).execute(identity(displayName = "  Person Name  "))

        assertEquals("Person Name", repository.commands.single().displayName.value)
    }

    @Test
    fun `retry delegates the same UID command and returns the same session`() {
        val repository = RecordingSessionRepository(view)
        val useCase = BootstrapSession(repository)
        val identity = identity()

        val first = useCase.execute(identity)
        val second = useCase.execute(identity)

        assertEquals(first, second)
        assertEquals(2, repository.commands.size)
        assertEquals(repository.commands[0], repository.commands[1])
    }

    @Test
    fun `changed mirrors preserve repository user ID roles and memberships`() {
        val repository = RecordingSessionRepository(view)
        val useCase = BootstrapSession(repository)

        val result = useCase.execute(identity(email = "changed@example.test", displayName = "Changed Name"))

        assertEquals(BootstrapSessionResult.Success(view), result)
        assertEquals(userId, (result as BootstrapSessionResult.Success).session.user.id)
        assertSame(memberships, result.session.memberships)
        assertEquals("changed@example.test", repository.commands.single().email)
        assertEquals("Changed Name", repository.commands.single().displayName.value)
    }

    @Test
    fun `concurrent calls for the same UID return equivalent sessions`() {
        val repository = RecordingSessionRepository(view)
        val useCase = BootstrapSession(repository)
        val pool = Executors.newFixedThreadPool(2)

        val results = pool.invokeAll(List(2) { Callable { useCase.execute(identity()) } }).map { it.get() }
        pool.shutdown()

        assertEquals(listOf(BootstrapSessionResult.Success(view), BootstrapSessionResult.Success(view)), results)
        assertEquals(2, repository.commands.size)
        assertTrue(repository.commands.all { it.subject == "subject-1" })
    }

    private fun identity(
        email: String? = "person@example.test",
        emailVerified: Boolean? = true,
        displayName: String? = "Person Name",
    ) = RequestIdentity("subject-1", email, emailVerified, displayName)

    private fun assertBlocked(identity: RequestIdentity, expected: BootstrapSessionResult) {
        val repository = RecordingSessionRepository(view)

        assertSame(expected, BootstrapSession(repository).execute(identity))
        assertTrue(repository.commands.isEmpty())
    }

    private class RecordingSessionRepository(
        private val result: SessionView,
    ) : SessionRepository {
        val commands: MutableList<SessionUpsert> = Collections.synchronizedList(mutableListOf())

        override fun upsertAndLoad(command: SessionUpsert): SessionView {
            commands += command
            return result
        }
    }
}
