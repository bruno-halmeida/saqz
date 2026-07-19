package br.com.saqz.groups.application.read

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GetGroupTest {
    private val actorId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

    @Test
    fun `owner receives exact group settings role and version`() {
        val fixture = fixture(snapshot(GroupRole.OWNER))

        assertEquals(
            GetGroupResult.Success(
                GroupView(
                    groupId,
                    AccessName.from("Training Club"),
                    IanaTimeZone.from("America/Sao_Paulo"),
                    GroupRole.OWNER,
                    7,
                ),
            ),
            fixture.useCase.execute(actorId, groupId),
        )
    }

    @Test
    fun `admin can read the group`() {
        val result = fixture(snapshot(GroupRole.ADMIN)).useCase.execute(actorId, groupId)

        assertEquals(GroupRole.ADMIN, (result as GetGroupResult.Success).group.role)
    }

    @Test
    fun `athlete can read the group`() {
        val result = fixture(snapshot(GroupRole.ATHLETE)).useCase.execute(actorId, groupId)

        assertEquals(GroupRole.ATHLETE, (result as GetGroupResult.Success).group.role)
    }

    @Test
    fun `nonmember receives group not found even when a group snapshot exists`() {
        val result = fixture(snapshot(role = null)).useCase.execute(actorId, groupId)

        assertSame(GetGroupResult.GroupNotFound, result)
    }

    @Test
    fun `missing group and nonmember have the identical public result`() {
        val missing = fixture(null).useCase.execute(actorId, groupId)
        val nonmember = fixture(snapshot(role = null)).useCase.execute(actorId, groupId)

        assertSame(GetGroupResult.GroupNotFound, missing)
        assertSame(missing, nonmember)
    }

    @Test
    fun `repository receives only the requested actor and group context`() {
        val fixture = fixture(snapshot(GroupRole.OWNER))

        fixture.useCase.execute(actorId, groupId)

        assertEquals(listOf(GroupReadKey(actorId, groupId)), fixture.repository.keys)
    }

    @Test
    fun `role is copied from the repository snapshot rather than inferred`() {
        val result = fixture(snapshot(GroupRole.ADMIN)).useCase.execute(actorId, groupId)

        assertEquals(GroupRole.ADMIN, (result as GetGroupResult.Success).group.role)
    }

    @Test
    fun `next read observes a changed repository role`() {
        val repository = RecordingGroupReadRepository(snapshot(GroupRole.ADMIN))
        val useCase = GetGroup(repository, GroupAccessPolicy())
        val first = useCase.execute(actorId, groupId)
        repository.result = snapshot(GroupRole.ATHLETE)

        val second = useCase.execute(actorId, groupId)

        assertEquals(GroupRole.ADMIN, (first as GetGroupResult.Success).group.role)
        assertEquals(GroupRole.ATHLETE, (second as GetGroupResult.Success).group.role)
        assertEquals(2, repository.keys.size)
    }

    @Test
    fun `requested group data is not replaced by another context`() {
        val requested = UUID.randomUUID()
        val fixture = fixture(snapshot(GroupRole.OWNER, id = requested))

        val result = fixture.useCase.execute(actorId, requested)

        assertEquals(requested, (result as GetGroupResult.Success).group.id)
        assertEquals(listOf(GroupReadKey(actorId, requested)), fixture.repository.keys)
    }

    private fun fixture(result: GroupReadSnapshot?): Fixture {
        val repository = RecordingGroupReadRepository(result)
        return Fixture(GetGroup(repository, GroupAccessPolicy()), repository)
    }

    private fun snapshot(role: GroupRole?, id: UUID = groupId) = GroupReadSnapshot(
        id = id,
        name = AccessName.from("Training Club"),
        timeZone = IanaTimeZone.from("America/Sao_Paulo"),
        role = role,
        version = 7,
    )

    private data class Fixture(
        val useCase: GetGroup,
        val repository: RecordingGroupReadRepository,
    )

    private class RecordingGroupReadRepository(
        var result: GroupReadSnapshot?,
    ) : GroupReadRepository {
        val keys = mutableListOf<GroupReadKey>()

        override fun find(key: GroupReadKey): GroupReadSnapshot? {
            keys += key
            return result
        }
    }
}
