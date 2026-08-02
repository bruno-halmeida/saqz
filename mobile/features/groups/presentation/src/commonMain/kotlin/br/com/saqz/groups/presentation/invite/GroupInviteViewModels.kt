package br.com.saqz.groups.presentation.invite

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
import br.com.saqz.groups.domain.membership.GroupInviteMetadata
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.port.GroupInviteUrlReadResult
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.GroupInviteUrlWriteResult
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import qrcode.QRCode

@Suppress("LongParameterList")
class GroupInviteViewModel(
    private val groupId: String,
    private val groupGateway: GroupGateway,
    private val membershipGateway: GroupMembershipGateway,
    private val entryRequestGateway: GroupEntryRequestGateway,
    private val athleteGateway: AthleteGateway,
    private val urlStore: GroupInviteUrlStorePort,
    private val sharePort: NativeInviteSharePort,
    private val clipboardPort: NativeInviteClipboardPort,
) : MviViewModel<GroupInviteState, GroupInviteIntent, GroupInviteEffect>(GroupInviteState()) {
    private var generation = 0L
    private var versionToken: GroupVersionToken? = null
    private var groupName = ""
    private var timeZone = GroupTimeZone("")

    init { load() }

    override fun onIntent(intent: GroupInviteIntent) {
        when (intent) {
            GroupInviteIntent.Retry -> load()
            GroupInviteIntent.GenerateInvite -> rotate()
            GroupInviteIntent.DeactivateInvite -> expire()
            is GroupInviteIntent.ToggleApproval -> toggleApproval(intent.enabled)
            is GroupInviteIntent.ApproveRequest -> approve(intent.userId)
            is GroupInviteIntent.RejectRequest -> reject(intent.userId)
            GroupInviteIntent.OpenShareSheet -> update { it.copy(isShareSheetVisible = true, toast = null) }
            GroupInviteIntent.CloseShareSheet -> update { it.copy(isShareSheetVisible = false) }
            GroupInviteIntent.CopyLink -> copyLink()
            GroupInviteIntent.OpenMessagePreview -> openPreview()
            GroupInviteIntent.OpenQr -> openQr()
            GroupInviteIntent.ShareImage -> shareImage()
            GroupInviteIntent.ClearToast -> update { it.copy(toast = null) }
        }
    }

    private fun load() {
        val requestGeneration = nextGeneration()
        update { it.copy(isLoading = true, loadFailed = false, error = null, toast = null) }
        viewModelScope.launch {
            val group = when (val result = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(requestGeneration)
            }
            if (!isCurrent(requestGeneration)) return@launch
            val metadata = when (val result = membershipGateway.readInviteMetadata(GroupId(groupId))) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(requestGeneration)
            }
            val cachedUrl = readCachedUrl()
            val requests = when (val result = entryRequestGateway.list(GroupId(groupId))) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(requestGeneration)
            }
            val roster = when (val result = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(requestGeneration)
            }
            if (!isCurrent(requestGeneration)) return@launch
            groupName = group.group.name
            timeZone = group.group.timeZone
            versionToken = group.versionToken
            val active = metadata.isActiveNow()
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = null,
                    groupName = group.group.name,
                    inviteStatus = if (active) InviteStatus.Active else InviteStatus.Empty,
                    expiresLabel = metadata.expiresAt?.takeIf { active }?.formatExpiry(),
                    inviteUrl = cachedUrl.takeIf { active },
                    entryRequiresApproval = group.group.entryRequiresApproval,
                    pendingRequests = requests.map(GroupEntryRequest::toUi),
                    recentMembers = roster.sortedByDescending { it.joinedAt }
                        .map { member -> member.toUi() },
                )
            }
        }
    }

    private suspend fun readCachedUrl(): String? {
        val result = CompletableDeferred<GroupInviteUrlReadResult>()
        urlStore.read(groupId) { result.complete(it) }
        return when (val value = result.await()) {
            is GroupInviteUrlReadResult.Success -> value.inviteUrl?.takeIf(String::isNotBlank)
            GroupInviteUrlReadResult.Failure -> null
        }
    }

    private fun rotate() {
        if (state.value.isGenerating) return
        val requestGeneration = nextGeneration()
        update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            when (val result = membershipGateway.rotateInvite(GroupId(groupId))) {
                is SaqzResult.Failure -> if (isCurrent(requestGeneration)) {
                    update { it.copy(isGenerating = false, error = GroupInviteError.Operation) }
                }
                is SaqzResult.Success -> if (isCurrent(requestGeneration)) {
                    val url = result.value.value
                    urlStore.write(groupId, url) { writeResult ->
                        if (!isCurrent(requestGeneration)) return@write
                        update { it.copy(isGenerating = false, inviteStatus = InviteStatus.Active, inviteUrl = url) }
                        if (writeResult is GroupInviteUrlWriteResult.Failure) {
                            update { it.copy(error = GroupInviteError.Operation) }
                        }
                    }
                }
            }
        }
    }

    private fun expire() {
        if (state.value.isDeactivating) return
        val requestGeneration = nextGeneration()
        update { it.copy(isDeactivating = true, error = null) }
        viewModelScope.launch {
            when (membershipGateway.expireInvite(GroupId(groupId))) {
                is SaqzResult.Failure -> if (isCurrent(requestGeneration)) {
                    update { it.copy(isDeactivating = false, error = GroupInviteError.Operation) }
                }
                is SaqzResult.Success -> if (isCurrent(requestGeneration)) {
                    urlStore.write(groupId, null) { if (isCurrent(requestGeneration)) {
                        update {
                            it.copy(
                                isDeactivating = false,
                                inviteStatus = InviteStatus.Empty,
                                inviteUrl = null,
                                expiresLabel = null,
                            )
                        }
                    } }
                }
            }
        }
    }

    private fun toggleApproval(enabled: Boolean) {
        if (state.value.isUpdatingApproval || versionToken == null) return
        val requestGeneration = nextGeneration()
        val previous = state.value.entryRequiresApproval
        update { it.copy(entryRequiresApproval = enabled, isUpdatingApproval = true, error = null) }
        viewModelScope.launch {
            val result = groupGateway.update(
                UpdateGroupSettingsCommand(
                    groupId = GroupId(groupId),
                    versionToken = versionToken ?: return@launch,
                    name = groupName,
                    timeZone = timeZone,
                    entryRequiresApproval = enabled,
                ),
            )
            if (!isCurrent(requestGeneration)) return@launch
            when (result) {
                is SaqzResult.Failure -> update {
                    it.copy(
                        entryRequiresApproval = previous,
                        isUpdatingApproval = false,
                        error = GroupInviteError.Operation,
                    )
                }
                is SaqzResult.Success -> {
                    versionToken = result.value.versionToken
                    update {
                        it.copy(
                            isUpdatingApproval = false,
                            entryRequiresApproval = result.value.group.entryRequiresApproval,
                        )
                    }
                }
            }
        }
    }

    private fun approve(userId: String) {
        if (userId in state.value.pendingActionIds) return
        val requestGeneration = nextGeneration()
        update { it.copy(pendingActionIds = it.pendingActionIds + userId, error = null) }
        viewModelScope.launch {
            when (entryRequestGateway.approve(GroupId(groupId), userId)) {
                is SaqzResult.Failure -> if (isCurrent(requestGeneration)) update {
                    it.copy(
                        pendingActionIds = it.pendingActionIds - userId,
                        error = GroupInviteError.Operation,
                    )
                }
                is SaqzResult.Success -> if (isCurrent(requestGeneration)) {
                    update { it.copy(pendingActionIds = it.pendingActionIds - userId) }
                    load()
                }
            }
        }
    }

    private fun reject(userId: String) {
        if (userId in state.value.pendingActionIds) return
        val requestGeneration = nextGeneration()
        update { it.copy(pendingActionIds = it.pendingActionIds + userId, error = null) }
        viewModelScope.launch {
            when (entryRequestGateway.reject(GroupId(groupId), userId)) {
                is SaqzResult.Failure -> if (isCurrent(requestGeneration)) update {
                    it.copy(
                        pendingActionIds = it.pendingActionIds - userId,
                        error = GroupInviteError.Operation,
                    )
                }
                is SaqzResult.Success -> if (isCurrent(requestGeneration)) {
                    update {
                        it.copy(
                            pendingActionIds = it.pendingActionIds - userId,
                            pendingRequests = it.pendingRequests.filterNot { request -> request.userId == userId },
                        )
                    }
                }
            }
        }
    }

    private fun copyLink() {
        val url = state.value.inviteUrl ?: return
        val requestGeneration = nextGeneration()
        clipboardPort.copyText(url) { result ->
            if (!isCurrent(requestGeneration)) return@copyText
            when (result) {
                InviteNativeOperationResult.Success -> {
                    update { it.copy(toast = GroupInviteToast.LinkCopied) }
                    emit(GroupInviteEffect.LinkCopied)
                }
                InviteNativeOperationResult.Cancelled,
                is InviteNativeOperationResult.Failure,
                -> update { it.copy(error = GroupInviteError.Operation) }
            }
        }
    }

    private fun openPreview() {
        val url = state.value.inviteUrl ?: return
        emit(GroupInviteEffect.OpenMessagePreview(state.value.groupName, url))
    }

    private fun openQr() {
        val url = state.value.inviteUrl ?: return
        emit(GroupInviteEffect.OpenQr(state.value.groupName, url))
    }

    private fun shareImage() {
        val url = state.value.inviteUrl ?: return
        sharePort.shareImage(InviteShareImage(renderQr(url))) { result ->
            if (result !is InviteNativeOperationResult.Success) update { it.copy(error = GroupInviteError.Operation) }
        }
    }

    private fun failLoad(requestGeneration: Long) {
        if (isCurrent(requestGeneration)) update {
            it.copy(isLoading = false, loadFailed = true, error = GroupInviteError.Load)
        }
    }

    private fun nextGeneration(): Long { generation += 1; return generation }
    private fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}

class InvitePreviewMessageViewModel(
    groupName: String,
    inviteUrl: String,
    private val sharePort: NativeInviteSharePort,
) : MviViewModel<InvitePreviewState, InvitePreviewIntent, InvitePreviewEffect>(
    InvitePreviewState(groupName, inviteUrl),
) {
    private var generation = 0L

    override fun onIntent(intent: InvitePreviewIntent) {
        when (intent) {
            is InvitePreviewIntent.MessageChanged -> updateMessage(intent.value)
            InvitePreviewIntent.Share -> share()
            InvitePreviewIntent.Back -> emit(InvitePreviewEffect.Back)
        }
    }

    private fun updateMessage(value: String) {
        val message = value.take(MAX_MESSAGE_LENGTH)
        update { it.copy(message = message, composedText = message.withLink(state.value.inviteUrl)) }
    }

    private fun share() {
        if (state.value.isSharing) return
        val requestGeneration = ++generation
        update { it.copy(isSharing = true, error = null) }
        sharePort.shareText(state.value.composedText) { result ->
            if (requestGeneration != generation) return@shareText
            when (result) {
                InviteNativeOperationResult.Success -> {
                    update { it.copy(isSharing = false) }
                    emit(InvitePreviewEffect.Shared)
                }
                InviteNativeOperationResult.Cancelled,
                is InviteNativeOperationResult.Failure,
                -> update { it.copy(isSharing = false, error = InvitePreviewError.Share) }
            }
        }
    }

    private companion object { const val MAX_MESSAGE_LENGTH = 300 }
}

class InviteQrViewModel(
    groupName: String,
    inviteUrl: String,
    private val sharePort: NativeInviteSharePort,
) : MviViewModel<InviteQrState, InviteQrIntent, InviteQrEffect>(
    InviteQrState(groupName = groupName, inviteUrl = inviteUrl, pngBytes = renderQr(inviteUrl)),
) {
    private var generation = 0L

    override fun onIntent(intent: InviteQrIntent) {
        when (intent) {
            InviteQrIntent.Share -> share()
            InviteQrIntent.Save -> save()
            InviteQrIntent.Back -> emit(InviteQrEffect.Back)
        }
    }

    private fun share() {
        val image = state.value.pngBytes?.let(::InviteShareImage) ?: return
        if (state.value.isSharing) return
        val requestGeneration = ++generation
        update { it.copy(isSharing = true, error = null) }
        sharePort.shareImage(image) { result ->
            if (requestGeneration != generation) return@shareImage
            when (result) {
                InviteNativeOperationResult.Success -> {
                    update { it.copy(isSharing = false) }
                    emit(InviteQrEffect.Shared)
                }
                InviteNativeOperationResult.Cancelled,
                is InviteNativeOperationResult.Failure,
                -> update { it.copy(isSharing = false, error = InviteQrError.Share) }
            }
        }
    }

    private fun save() {
        val image = state.value.pngBytes?.let(::InviteShareImage) ?: return
        if (state.value.isSaving) return
        val requestGeneration = ++generation
        update { it.copy(isSaving = true, error = null) }
        sharePort.saveImage(image) { result ->
            if (requestGeneration != generation) return@saveImage
            when (result) {
                InviteNativeOperationResult.Success -> {
                    update { it.copy(isSaving = false) }
                    emit(InviteQrEffect.Saved)
                }
                InviteNativeOperationResult.Cancelled,
                is InviteNativeOperationResult.Failure,
                -> update { it.copy(isSaving = false, error = InviteQrError.Save) }
            }
        }
    }
}

internal fun renderQr(inviteUrl: String): ByteArray = QRCode.ofSquares().withSize(10).build(inviteUrl).renderToBytes()

private fun String.withLink(inviteUrl: String): String = if (isBlank()) inviteUrl else "$this\n\n$inviteUrl"

private fun GroupInviteMetadata.isActiveNow(): Boolean = active &&
    (expiresAt?.let { runCatching { Instant.parse(it) > Clock.System.now() }.getOrDefault(false) } ?: true)

private fun GroupEntryRequest.toUi() = PendingEntryRequestUi(userId, displayName, requestedAt.formatDateTime())

private fun String.formatExpiry(): String {
    val date = take(10)
    return if (date.length == 10) "${date.substring(8, 10)}/${date.substring(5, 7)}" else this
}

private fun String.formatDateTime(): String {
    val date = take(10)
    if (date.length != 10) return this
    return "${date.substring(8, 10)}/${date.substring(5, 7)} · ${substringAfter('T', "").take(5)}"
}

private fun br.com.saqz.groups.domain.athlete.AthleteRosterEntry.toUi(): RecentMemberUi {
    val joined = runCatching { Instant.parse(joinedAt) }.getOrNull()
        ?: return RecentMemberUi(userId, displayName)
    val minutes = ((Clock.System.now() - joined).inWholeMinutes)
        .coerceAtLeast(0)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return when {
        minutes < 60 -> RecentMemberUi(userId, displayName, minutes, JoinedAtUnit.Minutes)
        minutes < 24 * 60 -> RecentMemberUi(userId, displayName, minutes / 60, JoinedAtUnit.Hours)
        else -> RecentMemberUi(userId, displayName, minutes / (24 * 60), JoinedAtUnit.Days)
    }
}
