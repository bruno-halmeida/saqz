package br.com.saqz.groups.presentation.route

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import br.com.saqz.groups.domain.photo.CachedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoCachePort
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import br.com.saqz.groups.port.*
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.photo.GroupPhotoCoordinator
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Combined-state/intent-delegation matrix for [GroupHomeRouteViewModel] (T13).
 * Derived from GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupHomeRouteViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state combines the administration and photo sources`() = runTest(mainDispatcher) {
        val fixture = fixture(this)

        assertEquals(fixture.administration.state.value, fixture.viewModel.state.value.administration)
        assertEquals(fixture.photo.state.value, fixture.viewModel.state.value.photo)
    }

    @Test
    fun `Administration intent forwards to the shared administration machine`() = runTest(mainDispatcher) {
        val fixture = fixture(this)
        val group = VersionedGroup(Group("g1", "Team", "UTC", 1, GroupRole.OWNER), GroupVersionToken("\"1\""))
        fixture.administration.onIntent(GroupAdministrationIntent.SetGroup(group))

        fixture.viewModel.onIntent(
            GroupHomeRouteIntent.Administration(GroupAdministrationIntent.UpdateSettings("New name", "America/Sao_Paulo")),
        )
        runCurrent()

        assertEquals("New name", fixture.viewModel.state.value.administration.group?.group?.name)
    }

    @Test
    fun `Photo intent forwards to the shared photo coordinator`() = runTest(mainDispatcher) {
        val fixture = fixture(this)

        fixture.viewModel.onIntent(GroupHomeRouteIntent.Photo(GroupPhotoIntent.BindTarget("g1", "etag")))
        runCurrent()

        assertEquals("g1", fixture.viewModel.state.value.photo.groupId)
    }

    @Test
    fun `OpenSettings emits a typed effect`() = runTest(mainDispatcher) {
        val fixture = fixture(this)

        fixture.viewModel.onIntent(GroupHomeRouteIntent.OpenSettings)
        val delivered = fixture.viewModel.effects.take(1).toList()

        assertEquals(listOf(GroupHomeRouteEffect.OpenSettings), delivered)
    }

    @Test
    fun `logout confirmation is local UI state until confirmed`() = runTest(mainDispatcher) {
        val fixture = fixture(this)

        fixture.viewModel.onIntent(GroupHomeRouteIntent.RequestLogout)
        assertTrue(fixture.viewModel.state.value.showLogoutConfirmation)

        fixture.viewModel.onIntent(GroupHomeRouteIntent.CancelLogout)
        assertTrue(!fixture.viewModel.state.value.showLogoutConfirmation)
    }

    @Test
    fun `ConfirmLogout clears the dialog and emits a typed effect`() = runTest(mainDispatcher) {
        val fixture = fixture(this)
        fixture.viewModel.onIntent(GroupHomeRouteIntent.RequestLogout)

        fixture.viewModel.onIntent(GroupHomeRouteIntent.ConfirmLogout)
        val delivered = fixture.viewModel.effects.take(1).toList()

        assertTrue(!fixture.viewModel.state.value.showLogoutConfirmation)
        assertEquals(listOf(GroupHomeRouteEffect.ConfirmLogout), delivered)
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope): Fixture {
        val administration = GroupAdministrationStateMachine(FakeGroupGateway(), FakeMembershipGateway(), scope) {}
        val photo = GroupPhotoCoordinator(FakePhotoGateway(), FakeSelectionPort(), FakeEncoderPort(), FakeCachePort(), scope)
        return Fixture(GroupHomeRouteViewModel(administration, photo), administration, photo)
    }

    private data class Fixture(
        val viewModel: GroupHomeRouteViewModel,
        val administration: GroupAdministrationStateMachine,
        val photo: GroupPhotoCoordinator,
    )

    private class FakeGroupGateway : GroupGateway {
        override suspend fun read(groupId: GroupId) = error("unused")
        override suspend fun create(command: CreateGroupCommand) = error("unused")
        override suspend fun update(command: UpdateGroupSettingsCommand): SaqzResult<VersionedGroup, GroupProfileError> =
            SaqzResult.Success(
                VersionedGroup(
                    Group(command.groupId.value, command.name, command.timeZone.id, 2, GroupRole.OWNER),
                    GroupVersionToken("\"2\""),
                ),
            )
    }

    private class FakeMembershipGateway : GroupMembershipGateway {
        override suspend fun listMemberships(groupId: GroupId): SaqzResult<List<GroupMembership>, GroupMembershipError> =
            SaqzResult.Success(emptyList())
        override suspend fun changeRole(command: ChangeMembershipRoleCommand) = error("unused")
        override suspend fun rotateInvite(groupId: GroupId) = error("unused")
        override suspend fun expireInvite(groupId: GroupId): EmptyResult<GroupMembershipError> = SaqzResult.Success(Unit)
        override suspend fun redeem(code: InviteCode): SaqzResult<RedeemedMembership, GroupMembershipError> = error("unused")
    }

    private class FakePhotoGateway : GroupPhotoGateway {
        override suspend fun upload(command: GroupPhotoUploadCommand): SaqzResult<GroupPhotoReceipt, br.com.saqz.groups.domain.photo.GroupPhotoError> =
            error("unused")
        override suspend fun read(
            groupId: GroupId,
            version: GroupPhotoVersionToken?,
        ): SaqzResult<GroupPhotoReadResult, br.com.saqz.groups.domain.photo.GroupPhotoError> = error("unused")
        override suspend fun remove(
            groupId: GroupId,
            groupVersion: GroupPhotoVersionToken,
        ): SaqzResult<GroupPhotoReceipt, br.com.saqz.groups.domain.photo.GroupPhotoError> = error("unused")
    }

    private class FakeSelectionPort : GroupPhotoSelectionPort {
        override suspend fun chooseCamera(): GroupPhotoSelectionResult = error("unused")
        override suspend fun chooseLibrary(): GroupPhotoSelectionResult = error("unused")
        override fun cleanup(source: String) = Unit
    }

    private class FakeEncoderPort : GroupPhotoEncoderPort {
        override suspend fun encode(source: String, crop: GroupPhotoCrop): GroupPhotoEncodingResult = error("unused")
        override fun cancel(source: String) = Unit
    }

    private class FakeCachePort : GroupPhotoCachePort {
        override fun read(groupId: GroupId): CachedGroupPhoto? = null
        override fun write(groupId: GroupId, bytes: ByteArray, version: GroupPhotoVersionToken): CachedGroupPhoto? = null
        override fun evict(groupId: GroupId) = Unit
        override fun clearAll() = Unit
    }
}
