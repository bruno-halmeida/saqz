package br.com.saqz.groups.presentation.invite

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.Athlete
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
import br.com.saqz.groups.domain.membership.GroupInviteMetadata
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import br.com.saqz.groups.port.GroupInviteUrlReadCallback
import br.com.saqz.groups.port.GroupInviteUrlReadResult
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.GroupInviteUrlWriteCallback
import br.com.saqz.groups.port.GroupInviteUrlWriteResult
import br.com.saqz.groups.port.InviteNativeFailureCode
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupInviteViewModelsTest {
    @Test
    fun `load empty metadata hides url and lists no people`() = runTest {
        val viewModel = groupViewModel(metadata = SaqzResult.Success(GroupInviteMetadata(active = false)))
        advanceUntilIdle()

        assertEquals(InviteStatus.Empty, viewModel.state.value.inviteStatus)
        assertNull(viewModel.state.value.inviteUrl)
        assertTrue(viewModel.state.value.pendingRequests.isEmpty())
        assertTrue(viewModel.state.value.recentMembers.isEmpty())
    }

    @Test
    fun `load active metadata consumes cached url and sorts roster by joinedAt`() = runTest {
        val store = FakeUrlStore("https://saqz.app/invite/new")
        val athletes = FakeAthleteGateway(
            listOf(
                roster("old", "2025-01-01T10:00:00Z"),
                roster("new", "2026-07-31T10:00:00Z"),
            ),
        )
        val viewModel = groupViewModel(urlStore = store, athleteGateway = athletes)
        advanceUntilIdle()

        assertEquals(InviteStatus.Active, viewModel.state.value.inviteStatus)
        assertEquals("https://saqz.app/invite/new", viewModel.state.value.inviteUrl)
        assertEquals(listOf("new", "old"), viewModel.state.value.recentMembers.map { it.userId })
    }

    @Test
    fun `load failure exposes retryable error`() = runTest {
        val viewModel = groupViewModel(metadata = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Timeout)))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupInviteError.Load, viewModel.state.value.error)
    }

    @Test
    fun `rotate success replaces url and writes local cache`() = runTest {
        val membership = FakeMembershipGateway(rotateResult = SaqzResult.Success(GroupInviteUrl("https://saqz.app/invite/rotated")))
        val store = FakeUrlStore()
        val viewModel = groupViewModel(membership = membership, urlStore = store)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.GenerateInvite)
        advanceUntilIdle()

        assertEquals("https://saqz.app/invite/rotated", viewModel.state.value.inviteUrl)
        assertEquals("https://saqz.app/invite/rotated", store.value)
        assertFalse(viewModel.state.value.isGenerating)
    }

    @Test
    fun `rotate failure leaves operation error`() = runTest {
        val viewModel = groupViewModel(
            membership = FakeMembershipGateway(
                rotateResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Connectivity)),
            ),
        )
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.GenerateInvite)
        advanceUntilIdle()

        assertEquals(GroupInviteError.Operation, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isGenerating)
    }

    @Test
    fun `expire success clears active cache`() = runTest {
        val store = FakeUrlStore("https://saqz.app/invite/active")
        val viewModel = groupViewModel(urlStore = store, membership = FakeMembershipGateway(expireResult = SaqzResult.Success(Unit)))
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.DeactivateInvite)
        advanceUntilIdle()

        assertEquals(InviteStatus.Empty, viewModel.state.value.inviteStatus)
        assertNull(viewModel.state.value.inviteUrl)
        assertNull(store.value)
    }

    @Test
    fun `expire failure keeps active invite`() = runTest {
        val viewModel = groupViewModel(
            membership = FakeMembershipGateway(expireResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Timeout))),
        )
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.DeactivateInvite)
        advanceUntilIdle()

        assertEquals(InviteStatus.Active, viewModel.state.value.inviteStatus)
        assertEquals(GroupInviteError.Operation, viewModel.state.value.error)
    }

    @Test
    fun `toggle approval success sends group update`() = runTest {
        val groupGateway = FakeInviteGroupGateway()
        val viewModel = groupViewModel(groupGateway = groupGateway)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.ToggleApproval(true))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.entryRequiresApproval)
        assertTrue(groupGateway.lastUpdate!!.entryRequiresApproval)
    }

    @Test
    fun `toggle approval failure rolls state back`() = runTest {
        val groupGateway = FakeInviteGroupGateway(
            updateResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Timeout)),
        )
        val viewModel = groupViewModel(groupGateway = groupGateway)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.ToggleApproval(true))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.entryRequiresApproval)
        assertEquals(GroupInviteError.Operation, viewModel.state.value.error)
    }

    @Test
    fun `approve success reloads without pending request`() = runTest {
        val entryGateway = FakeEntryGateway(requests = mutableListOf(request("pending")))
        val viewModel = groupViewModel(entryGateway = entryGateway)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.ApproveRequest("pending"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.pendingRequests.isEmpty())
        assertEquals("pending", entryGateway.approvedUserId)
    }

    @Test
    fun `approve failure keeps request and error`() = runTest {
        val entryGateway = FakeEntryGateway(
            requests = mutableListOf(request("pending")),
            approveResult = SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Forbidden)),
        )
        val viewModel = groupViewModel(entryGateway = entryGateway)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.ApproveRequest("pending"))
        advanceUntilIdle()

        assertEquals(listOf("pending"), viewModel.state.value.pendingRequests.map { it.userId })
        assertEquals(GroupInviteError.Operation, viewModel.state.value.error)
    }

    @Test
    fun `reject success removes request`() = runTest {
        val entryGateway = FakeEntryGateway(requests = mutableListOf(request("pending")))
        val viewModel = groupViewModel(entryGateway = entryGateway)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.RejectRequest("pending"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.pendingRequests.isEmpty())
        assertEquals("pending", entryGateway.rejectedUserId)
    }

    @Test
    fun `reject failure keeps request and error`() = runTest {
        val entryGateway = FakeEntryGateway(
            requests = mutableListOf(request("pending")),
            rejectResult = SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Timeout)),
        )
        val viewModel = groupViewModel(entryGateway = entryGateway)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.RejectRequest("pending"))
        advanceUntilIdle()

        assertEquals(listOf("pending"), viewModel.state.value.pendingRequests.map { it.userId })
        assertEquals(GroupInviteError.Operation, viewModel.state.value.error)
    }

    @Test
    fun `copy link success stores requested toast`() = runTest {
        val clipboard = FakeClipboard()
        val viewModel = groupViewModel(urlStore = FakeUrlStore("https://saqz.app/invite/active"), clipboard = clipboard)
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.CopyLink)

        assertEquals("https://saqz.app/invite/active", clipboard.value)
        assertEquals(GroupInviteToast.LinkCopied, viewModel.state.value.toast)
    }

    @Test
    fun `copy link failure exposes operation error`() = runTest {
        val viewModel = groupViewModel(
            urlStore = FakeUrlStore("https://saqz.app/invite/active"),
            clipboard = FakeClipboard(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)),
        )
        advanceUntilIdle()

        viewModel.onIntent(GroupInviteIntent.CopyLink)

        assertEquals(GroupInviteError.Operation, viewModel.state.value.error)
    }

    @Test
    fun `3a image sharing passes rendered invitation and reports failure`() = runTest {
        val successShare = FakeShare()
        val success = groupViewModel(share = successShare)
        advanceUntilIdle()
        success.onIntent(GroupInviteIntent.ShareImage)
        assertTrue(successShare.imageBytes?.isNotEmpty() == true)

        val failure = groupViewModel(
            share = FakeShare(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)),
        )
        advanceUntilIdle()
        failure.onIntent(GroupInviteIntent.ShareImage)
        assertEquals(GroupInviteError.Operation, failure.state.value.error)
    }

    @Test
    fun `preview clamps message and appends link automatically`() = runTest {
        val viewModel = InvitePreviewMessageViewModel("CERET", "https://saqz.app/invite/1", FakeShare())
        val message = "x".repeat(400)

        viewModel.onIntent(InvitePreviewIntent.MessageChanged(message))

        assertEquals(300, viewModel.state.value.message.length)
        assertTrue(viewModel.state.value.composedText.endsWith("https://saqz.app/invite/1"))
    }

    @Test
    fun `preview share success and failure are distinct`() = runTest {
        val successShare = FakeShare()
        val success = InvitePreviewMessageViewModel("CERET", "url", successShare)
        success.onIntent(InvitePreviewIntent.Share)
        assertEquals(InvitePreviewEffect.Shared, success.effects.first())
        assertEquals("url", successShare.text)

        val failure = InvitePreviewMessageViewModel(
            "CERET",
            "url",
            FakeShare(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)),
        )
        failure.onIntent(InvitePreviewIntent.Share)
        assertEquals(InvitePreviewError.Share, failure.state.value.error)
    }

    @Test
    fun `qr generates png and handles share and save failure`() = runTest {
        val share = FakeShare()
        val viewModel = InviteQrViewModel("CERET", "https://saqz.app/invite/1", share)
        assertNotNull(viewModel.state.value.pngBytes)
        viewModel.onIntent(InviteQrIntent.Share)
        assertEquals(InviteQrEffect.Shared, viewModel.effects.first())
        viewModel.onIntent(InviteQrIntent.Save)
        assertEquals(InviteQrEffect.Saved, viewModel.effects.first())

        val failure = InviteQrViewModel(
            "CERET",
            "url",
            FakeShare(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)),
        )
        failure.onIntent(InviteQrIntent.Share)
        assertEquals(InviteQrError.Share, failure.state.value.error)
        failure.onIntent(InviteQrIntent.Save)
        assertEquals(InviteQrError.Save, failure.state.value.error)
    }

    private fun groupViewModel(
        groupGateway: GroupGateway = FakeInviteGroupGateway(),
        membership: GroupMembershipGateway = FakeMembershipGateway(),
        entryGateway: GroupEntryRequestGateway = FakeEntryGateway(),
        athleteGateway: AthleteGateway = FakeAthleteGateway(),
        urlStore: GroupInviteUrlStorePort = FakeUrlStore("https://saqz.app/invite/active"),
        share: NativeInviteSharePort = FakeShare(),
        clipboard: NativeInviteClipboardPort = FakeClipboard(),
        metadata: SaqzResult<GroupInviteMetadata, GroupMembershipError> = SaqzResult.Success(
            GroupInviteMetadata(active = true, expiresAt = "2099-08-07T23:59:00Z"),
        ),
    ): GroupInviteViewModel {
        val resolvedMembership = (membership as? FakeMembershipGateway)?.also { it.metadataResult = metadata } ?: membership
        return GroupInviteViewModel("group-1", groupGateway, resolvedMembership, entryGateway, athleteGateway, urlStore, share, clipboard)
    }

    private class FakeInviteGroupGateway(
        var updateResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(sampleVersionedGroup()),
    ) : GroupGateway {
        var lastUpdate: UpdateGroupSettingsCommand? = null
        override suspend fun create(command: CreateGroupCommand) = SaqzResult.Success(sampleGroup())
        override suspend fun read(groupId: GroupId) = SaqzResult.Success(sampleVersionedGroup())
        override suspend fun update(command: UpdateGroupSettingsCommand): SaqzResult<VersionedGroup, GroupProfileError> {
            lastUpdate = command
            return when (val result = updateResult) {
                is SaqzResult.Failure -> result
                is SaqzResult.Success -> SaqzResult.Success(
                    result.value.copy(group = result.value.group.copy(entryRequiresApproval = command.entryRequiresApproval)),
                )
            }
        }
        override suspend fun delete(groupId: GroupId) = SaqzResult.Success(Unit)
    }

    private class FakeMembershipGateway(
        var metadataResult: SaqzResult<GroupInviteMetadata, GroupMembershipError> = SaqzResult.Success(GroupInviteMetadata(true)),
        private val rotateResult: SaqzResult<GroupInviteUrl, GroupMembershipError> = SaqzResult.Success(GroupInviteUrl("https://saqz.app/invite/rotated")),
        private val expireResult: EmptyResult<GroupMembershipError> = SaqzResult.Success(Unit),
    ) : GroupMembershipGateway {
        override suspend fun listMemberships(groupId: GroupId) = SaqzResult.Success(emptyList<GroupMembership>())
        override suspend fun changeRole(command: ChangeMembershipRoleCommand) = SaqzResult.Success(GroupMembership("u", "U", GroupRole.ATHLETE))
        override suspend fun rotateInvite(groupId: GroupId) = rotateResult
        override suspend fun readInviteMetadata(groupId: GroupId) = metadataResult
        override suspend fun expireInvite(groupId: GroupId) = expireResult
        override suspend fun redeem(code: InviteCode) = SaqzResult.Success(RedeemedMembership(GroupId("group-1"), GroupRole.ATHLETE))
    }

    private class FakeEntryGateway(
        private val requests: MutableList<GroupEntryRequest> = mutableListOf(),
        private val approveResult: SaqzResult<GroupMembership, EntryRequestError> = SaqzResult.Success(GroupMembership("u", "U", GroupRole.ATHLETE)),
        private val rejectResult: EmptyResult<EntryRequestError> = SaqzResult.Success(Unit),
    ) : GroupEntryRequestGateway {
        var approvedUserId: String? = null
        var rejectedUserId: String? = null
        override suspend fun list(groupId: GroupId) = SaqzResult.Success(requests.toList())
        override suspend fun approve(groupId: GroupId, userId: String): SaqzResult<GroupMembership, EntryRequestError> { approvedUserId = userId; requests.removeAll { it.userId == userId }; return approveResult }
        override suspend fun reject(groupId: GroupId, userId: String): EmptyResult<EntryRequestError> { rejectedUserId = userId; if (rejectResult is SaqzResult.Success) requests.removeAll { it.userId == userId }; return rejectResult }
    }

    private class FakeAthleteGateway(private val roster: List<AthleteRosterEntry> = emptyList()) : AthleteGateway {
        override suspend fun roster(groupId: GroupId, filter: AthleteRosterFilter) = SaqzResult.Success(roster)
        override suspend fun updateOwnPosition(groupId: GroupId, position: AthletePosition?) = error("unused")
        override suspend fun updateOwnProfile(command: UpdateOwnAthleteProfileCommand) = error("unused")
        override suspend fun updateAthlete(command: UpdateAthleteCommand) = error("unused")
        override suspend fun stats(groupId: GroupId, userId: String) = error("unused")
        override suspend fun removeAthlete(groupId: GroupId, userId: String) = error("unused")
        override suspend fun ownProfile() = error("unused")
    }

    private class FakeUrlStore(initial: String? = null) : GroupInviteUrlStorePort {
        var value: String? = initial
        override fun read(groupId: String, done: GroupInviteUrlReadCallback) { done.complete(GroupInviteUrlReadResult.Success(value)) }
        override fun write(groupId: String, inviteUrl: String?, done: GroupInviteUrlWriteCallback) { value = inviteUrl; done.complete(GroupInviteUrlWriteResult.Success) }
    }

    private class FakeShare(
        private val result: InviteNativeOperationResult = InviteNativeOperationResult.Success,
    ) : NativeInviteSharePort {
        var text: String? = null
        var imageBytes: ByteArray? = null
        override fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit) { this.text = text; done(result) }
        override fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) { imageBytes = image.pngBytes; done(result) }
        override fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) { done(result) }
    }

    private class FakeClipboard(
        private val result: InviteNativeOperationResult = InviteNativeOperationResult.Success,
    ) : NativeInviteClipboardPort {
        var value: String? = null
        override fun copyText(text: String, done: (InviteNativeOperationResult) -> Unit) { value = text; done(result) }
    }

    private companion object {
        fun sampleGroup() = Group("group-1", "CERET", "America/Sao_Paulo", 2, GroupRole.OWNER)
        fun sampleVersionedGroup() = VersionedGroup(sampleGroup(), GroupVersionToken("etag-2"))
        fun roster(id: String, joinedAt: String) = AthleteRosterEntry(id, id, null, null, br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO, true, br.com.saqz.groups.domain.athlete.AthleteFinancialStatus.DESCONHECIDO, joinedAt = joinedAt)
        fun request(id: String) = GroupEntryRequest(id, "Pessoa $id", "2026-08-01T10:00:00Z")
    }
}
