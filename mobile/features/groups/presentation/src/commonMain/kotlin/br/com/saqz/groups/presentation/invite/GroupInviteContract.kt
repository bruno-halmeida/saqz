package br.com.saqz.groups.presentation.invite

import androidx.compose.runtime.Immutable

@Immutable
data class GroupInviteState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupInviteError? = null,
    val groupName: String = "",
    val inviteStatus: InviteStatus = InviteStatus.Empty,
    /** Código curto do grupo para mostrar (`44DF-8SA8`); vem da API, igual em qualquer aparelho. */
    val inviteCode: String? = null,
    val inviteUrl: String? = null,
    val entryRequiresApproval: Boolean = false,
    val isGenerating: Boolean = false,
    /** Diálogo "Trocar o código?" aberto: trocar invalida o código e o link antigos. */
    val isRotateConfirmVisible: Boolean = false,
    val isUpdatingApproval: Boolean = false,
    /** Pedido que o gestor está prestes a recusar; != null abre o diálogo de confirmação. */
    val rejectCandidate: PendingEntryRequestUi? = null,
    val pendingActionIds: Set<String> = emptySet(),
    val pendingRequests: List<PendingEntryRequestUi> = emptyList(),
    val recentMembers: List<RecentMemberUi> = emptyList(),
    val isShareSheetVisible: Boolean = false,
    val toast: GroupInviteToast? = null,
)

enum class InviteStatus { Active, Empty }
sealed interface GroupInviteToast {
    data object LinkCopied : GroupInviteToast
    data object CodeCopied : GroupInviteToast

    /** Retorno de "Aprovar": [name] é o nome que já estava no pedido. */
    data class RequestApproved(val name: String) : GroupInviteToast
    data object RequestRejected : GroupInviteToast
}

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

    /** Volta à tela: recarga por baixo, sem esqueleto nem tela de erro. */
    data object Refresh : GroupInviteIntent

    /** "Trocar código" abre a confirmação; só [ConfirmRotate] chama a API. */
    data object RequestRotate : GroupInviteIntent
    data object DismissRotate : GroupInviteIntent
    data object ConfirmRotate : GroupInviteIntent
    data class ToggleApproval(val enabled: Boolean) : GroupInviteIntent
    data class ApproveRequest(val userId: String) : GroupInviteIntent
    /** Abre a confirmação; recusar só age em [RejectRequest], depois do "Recusar" do diálogo. */
    data class RequestReject(val userId: String) : GroupInviteIntent
    data object DismissReject : GroupInviteIntent
    data class RejectRequest(val userId: String) : GroupInviteIntent
    data object OpenShareSheet : GroupInviteIntent
    data object CloseShareSheet : GroupInviteIntent
    data object CopyLink : GroupInviteIntent
    data object CopyCode : GroupInviteIntent
    data object OpenMessagePreview : GroupInviteIntent
    data object OpenQr : GroupInviteIntent
    data object ShareImage : GroupInviteIntent
    data object ClearToast : GroupInviteIntent
}

sealed interface GroupInviteEffect {
    data class OpenMessagePreview(val groupName: String, val inviteUrl: String) : GroupInviteEffect
    data class OpenQr(val groupName: String, val inviteUrl: String) : GroupInviteEffect
    data object LinkCopied : GroupInviteEffect
    data object CodeCopied : GroupInviteEffect
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
