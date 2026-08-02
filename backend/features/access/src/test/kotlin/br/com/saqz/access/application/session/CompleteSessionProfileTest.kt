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
    fun `missing phone is not changed`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            phoneProvided = false,
        )

        assertEquals(CompleteSessionProfileResult.Success(view), result)
        assertEquals(null, repository.commands.single().phone)
        assertEquals(false, repository.commands.single().phoneProvided)
    }

    @Test
    fun `explicit null phone is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            phoneProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidPhone, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `nickname and city can be cleared while visibility is forwarded`() {
        val repository = RecordingSessionRepository(view)

        CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawNickname = null,
            rawCity = null,
            rawPhoneVisibility = "NOBODY",
            phoneProvided = false,
            nicknameProvided = true,
            cityProvided = true,
            phoneVisibilityProvided = true,
        )

        val command = repository.commands.single()
        assertEquals(true, command.nicknameProvided)
        assertEquals(true, command.cityProvided)
        assertEquals(PhoneVisibility.NOBODY, command.phoneVisibility)
        assertEquals(true, command.phoneVisibilityProvided)
    }

    @Test
    fun `invalid phone visibility is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawPhoneVisibility = "FRIENDS",
            phoneProvided = false,
            phoneVisibilityProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidPhoneVisibility, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `null phone visibility is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawPhoneVisibility = null,
            phoneProvided = false,
            phoneVisibilityProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidPhoneVisibility, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `invalid nickname is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawNickname = "R",
            phoneProvided = false,
            nicknameProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidNickname, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `nickname with one supplementary character is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawNickname = "😀",
            phoneProvided = false,
            nicknameProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidNickname, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `nickname with forty supplementary characters is accepted`() {
        val repository = RecordingSessionRepository(view)
        val nickname = "😀".repeat(40)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawNickname = nickname,
            phoneProvided = false,
            nicknameProvided = true,
        )

        assertEquals(CompleteSessionProfileResult.Success(view), result)
        assertEquals(nickname, repository.commands.single().nickname)
    }

    @Test
    fun `untrimmed nickname is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawNickname = " Rafa",
            phoneProvided = false,
            nicknameProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidNickname, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `nickname with a control character is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawNickname = "Ra\nfa",
            phoneProvided = false,
            nicknameProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidNickname, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `city over eighty characters is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawCity = "a".repeat(81),
            phoneProvided = false,
            cityProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidCity, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `city with a control character is rejected before write`() {
        val repository = RecordingSessionRepository(view)

        val result = CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawCity = "A\u0000B",
            phoneProvided = false,
            cityProvided = true,
        )

        assertSame(CompleteSessionProfileResult.InvalidCity, result)
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `blank nickname is normalized to clear while omitted nickname is preserved`() {
        val repository = RecordingSessionRepository(view)

        CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            rawNickname = "   ",
            phoneProvided = false,
            nicknameProvided = true,
        )

        assertTrue(repository.commands.single().nicknameProvided)
        assertNull(repository.commands.single().nickname)

        repository.commands.clear()
        CompleteSessionProfile(repository).execute(
            subject = "subject-1",
            rawPhone = null,
            rawDisplayName = null,
            phoneProvided = false,
        )

        assertEquals(false, repository.commands.single().nicknameProvided)
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
