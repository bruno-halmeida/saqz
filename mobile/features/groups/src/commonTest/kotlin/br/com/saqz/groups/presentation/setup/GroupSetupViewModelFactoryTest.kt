package br.com.saqz.groups.presentation.setup

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.model.GroupTimeZone as SystemGroupTimeZone
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Factory-identity matrix for [GroupSetupViewModelFactory] (T14). Derived from
 * LIFE-01..05, GROUPNAV-01.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSetupViewModelFactoryTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `create threads the route identity for the Setup route with no existing group`() = runTest(mainDispatcher) {
        val factory = factory()

        val viewModel = factory.create(GroupSetupInput(existing = null), GroupCommandKeyFactory { "command-1" })

        assertEquals(GroupSetupMode.CREATE, viewModel.state.value.mode)
    }

    @Test
    fun `create threads the route identity for the CreateGroup route with an existing group`() = runTest(mainDispatcher) {
        val factory = factory()
        val existing = VersionedGroup(
            Group("g1", "Team", "America/Sao_Paulo", 1, br.com.saqz.groups.domain.group.GroupRole.OWNER),
            GroupVersionToken("\"1\""),
        )

        val viewModel = factory.create(GroupSetupInput(existing = existing), GroupCommandKeyFactory { "command-2" })

        assertEquals(GroupSetupMode.EDIT, viewModel.state.value.mode)
    }

    @Test
    fun `each entry receives its own view model instance for the same route identity`() = runTest(mainDispatcher) {
        val factory = factory()
        val input = GroupSetupInput(existing = null)

        val first = factory.create(input, GroupCommandKeyFactory { "command-1" })
        val second = factory.create(input, GroupCommandKeyFactory { "command-1" })

        assertNotSame(first, second)
    }

    private fun factory() = GroupSetupViewModelFactory(
        gateway = FakeGateway(),
        timeZones = GroupSystemTimeZonePort {
            it(GroupSystemTimeZoneResult.Available((SystemGroupTimeZone.parse("America/Sao_Paulo") as SystemGroupTimeZone.ParseResult.Valid).value))
        },
        drafts = FakeDrafts(),
    )

    private class FakeGateway : GroupProfileGateway {
        override suspend fun createProfile(command: CreateGroupProfileCommand): SaqzResult<Group, GroupProfileError> =
            error("unused")
        override suspend fun readProfile(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> = error("unused")
        override suspend fun updateProfile(command: UpdateGroupProfileCommand): SaqzResult<VersionedGroup, GroupProfileError> =
            error("unused")
    }

    private class FakeDrafts : GroupDraftStorePort {
        override fun read(key: br.com.saqz.groups.model.GroupDraftKey, done: (GroupDraftReadResult) -> Unit) =
            done(GroupDraftReadResult.Success(null))
        override fun write(draft: br.com.saqz.groups.model.GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)
        override fun clear(key: br.com.saqz.groups.model.GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)
    }
}
