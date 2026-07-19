package br.com.saqz.groups.application.create

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.groups.domain.group.GroupVenueInput
import br.com.saqz.groups.domain.group.RegularSlotInput
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CreateGroupTest {
    private val actorId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val stored = StoredGroup(
        id = groupId,
        ownerUserId = actorId,
        creationKey = requestId,
        name = AccessName.from("Training Club"),
        timeZone = IanaTimeZone.from("America/Sao_Paulo"),
        version = 1,
        profileStatus = GroupProfileStatus.COMPLETE,
    )

    @Test
    fun `valid input returns the created group with owner role version and profile status`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actorId, requestId, validProfile(), "America/Sao_Paulo")

        assertEquals(
            CreateGroupResult.Success(
                CreatedGroup(
                    groupId,
                    "Training Club",
                    "America/Sao_Paulo",
                    1,
                    GroupRole.OWNER,
                    GroupProfileStatus.COMPLETE,
                ),
            ),
            result,
        )
    }

    @Test
    fun `actor and creation key are persisted with complete profile defaults`() {
        val fixture = fixture()
        val profile = validProfile(
            defaultVenue = GroupVenueInput("Arena Beach", "Rua Central 100", "Quadra 2"),
            regularSlots = listOf(RegularSlotInput(DayOfWeek.MONDAY, LocalTime.of(20, 0), 120)),
            monthlyFeeCents = 5000,
            monthlyDueDay = 10,
        )

        fixture.useCase.execute(actorId, requestId, profile, "America/Sao_Paulo")

        val command = fixture.repository.commands.single()
        assertEquals(actorId, command.ownerUserId)
        assertEquals(requestId, command.creationKey)
        assertEquals("Training Club", command.profile.name)
        assertEquals(GroupModality.COURT_VOLLEYBALL, command.profile.modality)
        assertEquals(GroupComposition.MIXED, command.profile.composition)
        assertEquals(1, command.profile.regularSlots.size)
        assertEquals(5000, command.profile.monthlyFeeCents)
    }

    @Test
    fun `name and optional text are normalized before the transaction begins`() {
        val fixture = fixture(stored = stored.copy(name = AccessName.from("Training Club")))

        fixture.useCase.execute(
            actorId,
            requestId,
            validProfile(name = "  Training Club  ", description = "  Social games  ", city = "  São Paulo  "),
            "America/Sao_Paulo",
        )

        val command = fixture.repository.commands.single()
        assertEquals("Training Club", command.profile.name)
        assertEquals("Social games", command.profile.description)
        assertEquals("São Paulo", command.profile.city)
    }

    @Test
    fun `custom fields are accepted only when their selectors are custom`() {
        val fixture = fixture()

        fixture.useCase.execute(
            actorId,
            requestId,
            validProfile(
                level = GroupLevel.CUSTOM,
                customLevel = "Liga B",
                playStyle = CourtPlayStyle.CUSTOM,
                customPlayStyle = "Sem ponteiro fixo",
            ),
            "America/Sao_Paulo",
        )

        val command = fixture.repository.commands.single()
        assertEquals(GroupLevel.CUSTOM, command.profile.level)
        assertEquals("Liga B", command.profile.customLevel)
        assertEquals(CourtPlayStyle.CUSTOM, command.profile.playStyle)
        assertEquals("Sem ponteiro fixo", command.profile.customPlayStyle)
    }

    @Test
    fun `retry with the same request id returns the repository group unchanged`() {
        val fixture = fixture()

        val first = fixture.useCase.execute(actorId, requestId, validProfile(), "America/Sao_Paulo")
        val second = fixture.useCase.execute(actorId, requestId, validProfile(name = "Changed Name"), "Europe/Lisbon")

        assertEquals(first, second)
        assertEquals(listOf(requestId, requestId), fixture.repository.commands.map { it.creationKey })
    }

    @Test
    fun `a user with memberships elsewhere can create a group`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actorId, requestId, validProfile(), "America/Sao_Paulo")

        assertTrue(result is CreateGroupResult.Success)
        assertEquals(1, fixture.transaction.calls)
    }

    @Test
    fun `invalid profile returns all stable field errors without opening transaction`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(
            actorId,
            requestId,
            GroupProfileDefaultsInput(
                name = " ",
                modality = null,
                composition = null,
                defaultCapacity = 1,
                monthlyFeeCents = 5000,
            ),
            "Mars/Olympus",
        )

        assertTrue(result is CreateGroupResult.Invalid)
        assertEquals(
            setOf("name", "modality", "composition", "defaultCapacity", "monthlyDueDay", "timeZone"),
            result.errors.map { it.field }.toSet(),
        )
        assertEquals(0, fixture.transaction.calls)
        assertTrue(fixture.repository.commands.isEmpty())
    }

    @Test
    fun `contradictory defaults return field errors without opening transaction`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(
            actorId,
            requestId,
            validProfile(
                modality = GroupModality.BEACH_VOLLEYBALL,
                playStyle = CourtPlayStyle.FIVE_ONE,
                customLevel = "Should Not Persist",
            ),
            "America/Sao_Paulo",
        )

        assertTrue(result is CreateGroupResult.Invalid)
        assertEquals(setOf("customLevel", "playStyle"), result.errors.map { it.field }.toSet())
        assertEquals(0, fixture.transaction.calls)
        assertTrue(fixture.repository.commands.isEmpty())
    }

    @Test
    fun `invalid venue and slot return nested field errors without opening transaction`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(
            actorId,
            requestId,
            validProfile(
                defaultVenue = GroupVenueInput("A", "Rua", "\n"),
                regularSlots = listOf(RegularSlotInput(null, null, 14)),
            ),
            "America/Sao_Paulo",
        )

        assertTrue(result is CreateGroupResult.Invalid)
        assertEquals(
            setOf(
                "defaultVenue.name",
                "defaultVenue.address",
                "regularSlots[0].weekday",
                "regularSlots[0].startTime",
                "regularSlots[0].durationMinutes",
            ),
            result.errors.map { it.field }.toSet(),
        )
        assertEquals(0, fixture.transaction.calls)
    }

    @Test
    fun `repository failure escapes the transaction for rollback`() {
        val failure = IllegalStateException("write failed")
        val transaction = RecordingTransactionRunner()
        val repository = RecordingGroupCreationRepository(stored, failure)
        val useCase = CreateGroup(transaction, repository)

        val thrown = assertFailsWith<IllegalStateException> {
            useCase.execute(actorId, requestId, validProfile(), "America/Sao_Paulo")
        }

        assertSame(failure, thrown)
        assertEquals(1, transaction.calls)
        assertEquals(1, transaction.rollbacks)
        assertEquals(1, repository.commands.size)
    }

    private fun fixture(stored: StoredGroup = this.stored): Fixture {
        val transaction = RecordingTransactionRunner()
        val repository = RecordingGroupCreationRepository(stored)
        return Fixture(CreateGroup(transaction, repository), transaction, repository)
    }

    private fun validProfile(
        name: String = "Training Club",
        modality: GroupModality = GroupModality.COURT_VOLLEYBALL,
        composition: GroupComposition = GroupComposition.MIXED,
        description: String? = null,
        city: String? = null,
        level: GroupLevel? = GroupLevel.INTERMEDIATE,
        customLevel: String? = null,
        playStyle: CourtPlayStyle? = CourtPlayStyle.FIVE_ONE,
        customPlayStyle: String? = null,
        defaultVenue: GroupVenueInput? = null,
        regularSlots: List<RegularSlotInput> = emptyList(),
        monthlyFeeCents: Long? = null,
        monthlyDueDay: Int? = null,
    ) = GroupProfileDefaultsInput(
        name = name,
        modality = modality,
        composition = composition,
        description = description,
        city = city,
        level = level,
        customLevel = customLevel,
        playStyle = playStyle,
        customPlayStyle = customPlayStyle,
        defaultVenue = defaultVenue,
        regularSlots = regularSlots,
        defaultCapacity = 18,
        defaultConfirmationLeadMinutes = 180,
        defaultGameFeeCents = 1500,
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
    )

    private data class Fixture(
        val useCase: CreateGroup,
        val transaction: RecordingTransactionRunner,
        val repository: RecordingGroupCreationRepository,
    )

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0
        var rollbacks = 0

        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return try {
                block()
            } catch (failure: Throwable) {
                rollbacks += 1
                throw failure
            }
        }
    }

    private class RecordingGroupCreationRepository(
        private val stored: StoredGroup,
        private val failure: Throwable? = null,
    ) : GroupCreationRepository {
        val commands = mutableListOf<CreateGroupCommand>()

        override fun create(command: CreateGroupCommand): StoredGroup {
            commands += command
            failure?.let { throw it }
            return stored
        }
    }
}
