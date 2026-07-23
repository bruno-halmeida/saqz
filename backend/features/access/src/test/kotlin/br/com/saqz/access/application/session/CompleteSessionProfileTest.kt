package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompleteSessionProfileTest {
    private val userId = UUID.randomUUID()
    private val view = SessionView(
        UserAccount(userId, "subject-1", "person@example.test", AccessName.from("Person Name"), PhoneNumber.from("+5511911112222")),
        emptyList(),
    )

    @Test
    fun `valid phone is normalized persisted and returned`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute("subject-1", "(11) 91111-2222", null)

        assertEquals(CompleteSessionProfileResult.Success(view), result)
        assertEquals(
            ProfileCompletion("subject-1", PhoneNumber.from("+5511911112222"), null),
            repository.commands.single(),
        )
    }

    @Test
    fun `optional display name is validated and forwarded when present`() {
        val repository = RecordingSessionRepository(view)

        CompleteSessionProfile(repository).execute("subject-1", "+5511911112222", "New Name")

        assertEquals("New Name", repository.commands.single().displayName?.value)
    }

    @Test
    fun `landline shaped phone is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute("subject-1", "+551133334444", null)

        assertSame(CompleteSessionProfileResult.InvalidPhone, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `blank phone is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute("subject-1", "   ", null)

        assertSame(CompleteSessionProfileResult.InvalidPhone, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `blank display name is rejected before write leaving phone unset`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute("subject-1", "+5511911112222", "   ")

        assertSame(CompleteSessionProfileResult.InvalidDisplayName, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `repeat submission of the same phone is an idempotent overwrite`() {
        val repository = RecordingSessionRepository(view)
        val useCase = CompleteSessionProfile(repository)

        useCase.execute("subject-1", "+5511911112222", null)
        useCase.execute("subject-1", "+5511911112222", null)

        assertEquals(2, repository.commands.size)
        assertEquals(repository.commands[0], repository.commands[1])
    }

    @Test
    fun `invalid phone problem never reaches the repository with the raw value`() {
        val repository = RecordingSessionRepository(view)

        CompleteSessionProfile(repository).execute("subject-1", "not-a-phone", null)

        assertNull(repository.commands.singleOrNull())
    }

    @Test
    fun `unbootstrapped account is reported as not found instead of a raw failure`() {
        val repository = RecordingSessionRepository(null)

        val result = CompleteSessionProfile(repository).execute("subject-1", "+5511911112222", null)

        assertSame(CompleteSessionProfileResult.AccountNotFound, result)
        assertEquals(1, repository.commands.size)
    }

    private class RecordingSessionRepository(
        private val result: SessionView?,
    ) : SessionRepository {
        val commands: MutableList<ProfileCompletion> = mutableListOf()

        override fun upsertAndLoad(command: SessionUpsert): SessionView = error("not used")

        override fun updateProfile(command: ProfileCompletion): SessionView? {
            commands += command
            return result
        }
    }
}
