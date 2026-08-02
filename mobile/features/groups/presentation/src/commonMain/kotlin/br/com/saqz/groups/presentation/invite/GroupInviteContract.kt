package br.com.saqz.groups.presentation.invite

import androidx.compose.runtime.Immutable

@Immutable
data class GroupInviteState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupInviteError? = null,
    val groupName: String = "",
    val inviteStatus: InviteStatus = InviteStatus.Empty,
    val expiresLabel: String? = null,
    val inviteUrl: String? = null,
    val entryRequiresApproval: Boolean = false,
    val isGenerating: Boolean = false,
    val isDeactivating: Boolean = false,
    val isUpdatingApproval: Boolean = false,
    val pendingActionIds: Set<String> = emptySet(),
    val pendingRequests: List<PendingEntryRequestUi> = emptyList(),
    val recentMembers: List<RecentMemberUi> = emptyList(),
    val isShareSheetVisible: Boolean = false,
    val toast: GroupInviteToast? = null,
)

enum class InviteStatus { Active, Empty }
enum class GroupInviteToast { LinkCopied }

@Immutable
data class PendingEntryRequestUi(
    val userId: String,
    val displayName: String,
    val requestedAtLabel: String,
)

@Immutable
data class RecentMemberUi(
    val userId: String,
    val displayName: String,
    val joinedAtCount: Int? = null,
    val joinedAtUnit: JoinedAtUnit = JoinedAtUnit.Recently,
)

enum class JoinedAtUnit { Minutes, Hours, Days, Recently }

sealed interface GroupInviteError {
    data object Load : GroupInviteError
    data object Operation : GroupInviteError
}

sealed interface GroupInviteIntent {
    data object Retry : GroupInviteIntent
    data object GenerateInvite : GroupInviteIntent
    data object DeactivateInvite : GroupInviteIntent
    data class ToggleApproval(val enabled: Boolean) : GroupInviteIntent
    data class ApproveRequest(val userId: String) : GroupInviteIntent
    data class RejectRequest(val userId: String) : GroupInviteIntent
    data object OpenShareSheet : GroupInviteIntent
    data object CloseShareSheet : GroupInviteIntent
    data object CopyLink : GroupInviteIntent
    data object OpenMessagePreview : GroupInviteIntent
    data object OpenQr : GroupInviteIntent
    data object ShareImage : GroupInviteIntent
    data object ClearToast : GroupInviteIntent
}

sealed interface GroupInviteEffect {
    data class OpenMessagePreview(val groupName: String, val inviteUrl: String) : GroupInviteEffect
    data class OpenQr(val groupName: String, val inviteUrl: String) : GroupInviteEffect
    data object LinkCopied : GroupInviteEffect
}

@Immutable
data class InvitePreviewState(
    val groupName: String,
    val inviteUrl: String,
    val message: String = "",
    val composedText: String = inviteUrl,
    val isSharing: Boolean = false,
    val error: InvitePreviewError? = null,
)

sealed interface InvitePreviewError { data object Share : InvitePreviewError }

sealed interface InvitePreviewIntent {
    data class MessageChanged(val value: String) : InvitePreviewIntent
    data object Share : InvitePreviewIntent
    data object Back : InvitePreviewIntent
}

sealed interface InvitePreviewEffect {
    data object Back : InvitePreviewEffect
    data object Shared : InvitePreviewEffect
}

@Immutable
data class InviteQrState(
    val groupName: String,
    val inviteUrl: String,
    val pngBytes: ByteArray? = null,
    val isSharing: Boolean = false,
    val isSaving: Boolean = false,
    val error: InviteQrError? = null,
) {
    override fun equals(other: Any?): Boolean = other is InviteQrState &&
        groupName == other.groupName && inviteUrl == other.inviteUrl &&
        pngBytes.contentEqualsNullable(other.pngBytes) && isSharing == other.isSharing &&
        isSaving == other.isSaving && error == other.error

    override fun hashCode(): Int = (((groupName.hashCode() * 31 + inviteUrl.hashCode()) * 31 +
        pngBytes.contentHashCodeNullable()) * 31 + isSharing.hashCode()) * 31 +
        isSaving.hashCode() * 31 + (error?.hashCode() ?: 0)
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null || other == null -> this == null && other == null
    else -> contentEquals(other)
}

private fun ByteArray?.contentHashCodeNullable(): Int = contentHashCode()

sealed interface InviteQrError { data object Share : InviteQrError; data object Save : InviteQrError }

sealed interface InviteQrIntent {
    data object Share : InviteQrIntent
    data object Save : InviteQrIntent
    data object Back : InviteQrIntent
}

sealed interface InviteQrEffect {
    data object Back : InviteQrEffect
    data object Shared : InviteQrEffect
    data object Saved : InviteQrEffect
}
