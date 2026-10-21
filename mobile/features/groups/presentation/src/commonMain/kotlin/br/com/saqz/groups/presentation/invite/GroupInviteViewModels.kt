package br.com.saqz.groups.presentation.invite

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.presentation.entryrequest.MonthlyFeeDefaultUi
import br.com.saqz.groups.presentation.entryrequest.monthlyFeeDefault
import br.com.saqz.groups.presentation.entryrequest.sheetFor
import br.com.saqz.groups.presentation.home.InviteLinkParser
import br.com.saqz.groups.presentation.setup.takeCodePoints
import br.com.saqz.groups.resources.invite_share_code_hint
import br.com.saqz.groups.resources.onboarding_invite_message
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import qrcode.QRCode

@Suppress("LongParameterList")
class GroupInviteViewModel(
    private val groupId: String,
    private val groupGateway: GroupGateway,
    private val membershipGateway: GroupMembershipGateway,
    private val entryRequestGateway: GroupEntryRequestGateway,
    private val athleteGateway: AthleteGateway,
    private val sharePort: NativeInviteSharePort,
    private val clipboardPort: NativeInviteClipboardPort,
) : MviViewModel<GroupInviteState, GroupInviteIntent, GroupInviteEffect>(GroupInviteState()) {
    private enum class GenerationOperation {
        Load,
        Rotate,
        Deactivate,
        ToggleApproval,
        Approve,
        Reject,
        RefreshRequests,
        CopyLink,
        CopyCode,
        ShareImage,
    }

    private data class GenerationKey(
        val operation: GenerationOperation,
        val target: String = "",
    )

    private val generations = mutableMapOf<GenerationKey, Long>()
    private var versionToken: GroupVersionToken? = null
    private var groupName = ""
    private var timeZone = GroupTimeZone("")
    private var loadInFlight = false
    private var feeDefault: MonthlyFeeDefaultUi? = null

    init { load() }

    override fun handleIntent(intent: GroupInviteIntent) {
        when (intent) {
            GroupInviteIntent.Retry -> load()
            GroupInviteIntent.Refresh -> refresh()
            GroupInviteIntent.ConfirmRotate -> rotate()
            is GroupInviteDeactivateIntent -> handleDeactivate(intent)
            is GroupInviteIntent.ToggleApproval -> toggleApproval(intent.enabled)
            is GroupInviteIntent.ApproveRequest -> accept(intent.userId)
            is GroupInviteAcceptAsIntent -> handleAcceptAs(intent)
            is GroupInviteIntent.RequestReject -> update { current ->
                current.copy(rejectCandidate = current.pendingRequests.firstOrNull { it.userId == intent.userId })
            }
            GroupInviteIntent.DismissReject -> update { it.copy(rejectCandidate = null) }
            is GroupInviteIntent.RejectRequest -> reject(intent.userId)
            is GroupInviteSheetIntent -> handleSheet(intent)
            GroupInviteIntent.CopyLink -> copyLink()
            GroupInviteIntent.CopyCode -> copyCode()
            GroupInviteIntent.OpenMessagePreview -> openPreview()
            GroupInviteIntent.OpenQr -> openQr()
            GroupInviteIntent.ShareImage -> shareImage()
            GroupInviteIntent.ClearToast -> update { it.copy(toast = null) }
        }
    }

    /**
     * Recarga por baixo ([GroupInviteIntent.Refresh]): sem esqueleto, sem tela de erro, sem apagar
     * o toast. Com carga ou escrita em voo (trocar código, aprovação) é ignorado.
     */
    private fun refresh() {
        if (isFirstAppearance()) return
        val current = state.value
        val writing = current.isGenerating || current.isDeactivating || current.isUpdatingApproval ||
            current.pendingActionIds.isNotEmpty()
        if (!loadInFlight && !writing) load(softRefresh = true)
    }

    private fun load(softRefresh: Boolean = false) {
        val operation = GenerationKey(GenerationOperation.Load)
        val requestGeneration = nextGeneration(operation)
        loadInFlight = true
        if (!softRefresh) update { it.copy(isLoading = true, loadFailed = false, error = null, toast = null) }
        val job = viewModelScope.launch {
            val group = when (val result = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(operation, requestGeneration, softRefresh)
            }
            if (!isCurrent(operation, requestGeneration)) return@launch
            val metadata = when (val result = membershipGateway.readInviteMetadata(GroupId(groupId))) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(operation, requestGeneration, softRefresh)
            }
            val requests = when (val result = entryRequestGateway.list(GroupId(groupId))) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(operation, requestGeneration, softRefresh)
            }
            val roster = when (val result = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())) {
                is SaqzResult.Success -> result.value
                is SaqzResult.Failure -> return@launch failLoad(operation, requestGeneration, softRefresh)
            }
            if (!isCurrent(operation, requestGeneration)) return@launch
            groupName = group.group.name
            timeZone = group.group.timeZone
            feeDefault = group.group.financeDefaults.monthlyFeeDefault()
            versionToken = group.versionToken
            // O código e o link vêm da API, iguais em qualquer aparelho e para qualquer admin: sem cache local.
            val active = metadata.active
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = null,
                    groupName = group.group.name,
                    inviteStatus = if (active) InviteStatus.Active else InviteStatus.Empty,
                    inviteCode = metadata.code?.takeIf { active },
                    inviteUrl = metadata.inviteUrl?.takeIf { active },
                    entryRequiresApproval = group.group.entryRequiresApproval,
                    pendingRequests = requests.map { it.toUi(timeZone.id) },
                    recentMembers = roster.sortedByDescending { it.joinedAt }
                        .map { member -> member.toUi() },
                )
            }
        }
        job.invokeOnCompletion { if (isCurrent(operation, requestGeneration)) loadInFlight = false }
    }

    private fun rotate() {
        if (state.value.isGenerating) return
        val operation = GenerationKey(GenerationOperation.Rotate)
        val requestGeneration = nextGeneration(operation)
        update { it.copy(isGenerating = true, isRotateConfirmVisible = false, error = null) }
        viewModelScope.launch {
            when (val result = membershipGateway.rotateInvite(GroupId(groupId))) {
                is SaqzResult.Failure -> if (isCurrent(operation, requestGeneration)) {
                    update { it.copy(isGenerating = false, error = GroupInviteError.Operation) }
                }
                is SaqzResult.Success -> if (isCurrent(operation, requestGeneration)) {
                    update {
                        it.copy(
                            isGenerating = false,
                            inviteStatus = InviteStatus.Active,
                            inviteCode = result.value.code,
                            inviteUrl = result.value.value,
                        )
                    }
                }
            }
        }
    }

    private fun handleSheet(intent: GroupInviteSheetIntent) {
        when (intent) {
            GroupInviteIntent.RequestRotate -> update { it.copy(isRotateConfirmVisible = true) }
            GroupInviteIntent.DismissRotate -> update { it.copy(isRotateConfirmVisible = false) }
            GroupInviteIntent.OpenShareSheet -> update { it.copy(isShareSheetVisible = true, toast = null) }
            GroupInviteIntent.CloseShareSheet -> update { it.copy(isShareSheetVisible = false) }
        }
    }

    private fun handleDeactivate(intent: GroupInviteDeactivateIntent) {
        when (intent) {
            GroupInviteIntent.RequestDeactivate -> update { it.copy(isDeactivateConfirmVisible = true) }
            GroupInviteIntent.DismissDeactivate -> update { it.copy(isDeactivateConfirmVisible = false) }
            GroupInviteIntent.ConfirmDeactivate -> deactivate()
        }
    }

    /** Desativa o convite: o código e o link deixam de valer; os pedidos de entrada pendentes continuam. */
    private fun deactivate() {
        if (state.value.isDeactivating || state.value.isGenerating) return
        val operation = GenerationKey(GenerationOperation.Deactivate)
        val requestGeneration = nextGeneration(operation)
        update { it.copy(isDeactivating = true, isDeactivateConfirmVisible = false, error = null) }
        viewModelScope.launch {
            when (membershipGateway.expireInvite(GroupId(groupId))) {
                is SaqzResult.Failure -> if (isCurrent(operation, requestGeneration)) {
                    update { it.copy(isDeactivating = false, error = GroupInviteError.Operation) }
                }
                is SaqzResult.Success -> if (isCurrent(operation, requestGeneration)) {
                    update {
                        it.copy(
                            isDeactivating = false,
                            inviteStatus = InviteStatus.Empty,
                            inviteCode = null,
                            inviteUrl = null,
                            isShareSheetVisible = false,
                            toast = GroupInviteToast.InviteDeactivated,
                        )
                    }
                }
            }
        }
    }

    private fun toggleApproval(enabled: Boolean) {
        if (state.value.isUpdatingApproval || versionToken == null) return
        val operation = GenerationKey(GenerationOperation.ToggleApproval)
        val requestGeneration = nextGeneration(operation)
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
            if (!isCurrent(operation, requestGeneration)) return@launch
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

    private fun handleAcceptAs(intent: GroupInviteAcceptAsIntent) {
        when (intent) {
            is GroupInviteIntent.SelectAcceptAs -> update { current ->
                current.copy(acceptAs = current.acceptAs?.copy(selected = intent.type, feeRequired = false))
            }
            GroupInviteIntent.ConfirmAcceptAs -> state.value.acceptAs?.let { approve(it.requestId, it.selected) }
            GroupInviteIntent.DismissAcceptAs -> if (state.value.acceptAs?.requestId !in state.value.pendingActionIds) {
                update { it.copy(acceptAs = null) }
            }
        }
    }

    /** Sem mensalidade padrão, aceita em um toque como avulso; com ela, abre "Aceitar como" (DEB-112). */
    private fun accept(userId: String) {
        if (userId in state.value.pendingActionIds) return
        val default = feeDefault
        val request = state.value.pendingRequests.firstOrNull { it.userId == userId }
        if (default != null && request != null) {
            update { it.copy(acceptAs = default.sheetFor(userId, request.displayName)) }
        } else {
            approve(userId, AthleteMembershipType.AVULSO)
        }
    }

    private fun approve(userId: String, type: AthleteMembershipType) {
        if (userId in state.value.pendingActionIds) return
        val operation = GenerationKey(GenerationOperation.Approve, userId)
        val requestGeneration = nextGeneration(operation)
        val name = state.value.pendingRequests.firstOrNull { it.userId == userId }?.displayName.orEmpty()
        update { it.copy(pendingActionIds = it.pendingActionIds + userId, error = null) }
        viewModelScope.launch {
            when (val result = entryRequestGateway.approve(GroupId(groupId), userId, type)) {
                // O padrão sumiu depois de abrir a folha: a folha fica com o aviso e o pedido continua na lista.
                is SaqzResult.Failure -> if (isCurrent(operation, requestGeneration)) update {
                    val feeRequired = result.error == EntryRequestError.MonthlyFeeRequired
                    it.copy(
                        pendingActionIds = it.pendingActionIds - userId,
                        error = if (feeRequired) null else GroupInviteError.Operation,
                        acceptAs = if (feeRequired) it.acceptAs?.copy(feeRequired = true) else null,
                    )
                }
                is SaqzResult.Success -> if (isCurrent(operation, requestGeneration)) {
                    update {
                        it.copy(
                            pendingActionIds = it.pendingActionIds - userId,
                            acceptAs = null,
                            toast = GroupInviteToast.RequestApproved(name),
                        )
                    }
                    // Quem entrou aparece em "Quem entrou" na hora, sem recarregar a tela.
                    refreshPeople()
                }
            }
        }
    }

    private fun reject(userId: String) {
        if (userId in state.value.pendingActionIds) return
        val operation = GenerationKey(GenerationOperation.Reject, userId)
        val requestGeneration = nextGeneration(operation)
        update { it.copy(pendingActionIds = it.pendingActionIds + userId, error = null, rejectCandidate = null) }
        viewModelScope.launch {
            when (entryRequestGateway.reject(GroupId(groupId), userId)) {
                is SaqzResult.Failure -> if (isCurrent(operation, requestGeneration)) update {
                    it.copy(
                        pendingActionIds = it.pendingActionIds - userId,
                        error = GroupInviteError.Operation,
                    )
                }
                is SaqzResult.Success -> if (isCurrent(operation, requestGeneration)) {
                    update {
                        it.copy(pendingActionIds = it.pendingActionIds - userId, toast = GroupInviteToast.RequestRejected)
                    }
                    refreshPeople()
                }
            }
        }
    }

    /** Depois de aprovar ou recusar: recarrega os pedidos e quem entrou. Cada lista falha sozinha. */
    private fun refreshPeople() {
        val operation = GenerationKey(GenerationOperation.RefreshRequests)
        val requestGeneration = nextGeneration(operation)
        viewModelScope.launch {
            val requests = entryRequestGateway.list(GroupId(groupId))
            val roster = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
            if (!isCurrent(operation, requestGeneration)) return@launch
            update { current ->
                current.copy(
                    pendingRequests = (requests as? SaqzResult.Success)
                        ?.value?.map { it.toUi(timeZone.id) } ?: current.pendingRequests,
                    recentMembers = (roster as? SaqzResult.Success)
                        ?.value?.sortedByDescending { it.joinedAt }?.map { it.toUi() } ?: current.recentMembers,
                    error = if (requests is SaqzResult.Failure || roster is SaqzResult.Failure) {
                        GroupInviteError.Operation
                    } else {
                        current.error
                    },
                )
            }
        }
    }

    private fun copyLink() {
        val url = state.value.inviteUrl ?: return
        val operation = GenerationKey(GenerationOperation.CopyLink)
        val requestGeneration = nextGeneration(operation)
        clipboardPort.copyText(url) { result ->
            if (!isCurrent(operation, requestGeneration)) return@copyText
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

    private fun copyCode() {
        val code = state.value.inviteCode ?: return
        val operation = GenerationKey(GenerationOperation.CopyCode)
        val requestGeneration = nextGeneration(operation)
        clipboardPort.copyText(code) { result ->
            if (!isCurrent(operation, requestGeneration)) return@copyText
            when (result) {
                InviteNativeOperationResult.Success -> {
                    update { it.copy(toast = GroupInviteToast.CodeCopied) }
                    emit(GroupInviteEffect.CodeCopied)
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
        val operation = GenerationKey(GenerationOperation.ShareImage)
        val requestGeneration = nextGeneration(operation)
        sharePort.shareImage(InviteShareImage(renderQr(url))) { result ->
            if (!isCurrent(operation, requestGeneration)) return@shareImage
            if (result !is InviteNativeOperationResult.Success) update { it.copy(error = GroupInviteError.Operation) }
        }
    }

    /** Por baixo ([softRefresh]) a falha não troca a tela por erro: o conteúdo fica. */
    private fun failLoad(operation: GenerationKey, requestGeneration: Long, softRefresh: Boolean) {
        if (!softRefresh && isCurrent(operation, requestGeneration)) update {
            it.copy(isLoading = false, loadFailed = true, error = GroupInviteError.Load)
        }
    }

    private fun nextGeneration(operation: GenerationKey): Long {
        val next = (generations[operation] ?: 0L) + 1L
        generations[operation] = next
        return next
    }

    private fun isCurrent(operation: GenerationKey, requestGeneration: Long): Boolean =
        requestGeneration == generations[operation]
}

class InvitePreviewMessageViewModel(
    groupName: String,
    inviteUrl: String,
    private val sharePort: NativeInviteSharePort,
) : MviViewModel<InvitePreviewState, InvitePreviewIntent, InvitePreviewEffect>(
    InvitePreviewState(groupName, inviteUrl),
) {
    private var shareGeneration = 0L
    private var messageEdited = false

    // O link leva o código curto; a mensagem repete o código para quem já tem o app e prefere digitá-lo.
    private val shortCode: String? = inviteUrl.shortInviteCodeOrNull()
    private var codeHint = ""
    private val prepareMessage = viewModelScope.launch {
        shortCode?.let { codeHint = getString(Res.string.invite_share_code_hint, InviteLinkParser.display(it)) }
        val message = getString(Res.string.onboarding_invite_message, groupName)
        updateMessage(if (messageEdited) state.value.message else message)
    }

    override fun handleIntent(intent: InvitePreviewIntent) {
        when (intent) {
            is InvitePreviewIntent.MessageChanged -> {
                messageEdited = true
                updateMessage(intent.value)
            }
            InvitePreviewIntent.Share -> share()
            InvitePreviewIntent.Back -> emit(InvitePreviewEffect.Back)
        }
    }

    private fun updateMessage(value: String) {
        val message = value.takeCodePoints(MAX_MESSAGE_LENGTH)
        update { it.copy(message = message, composedText = message.withLink(state.value.inviteUrl, codeHint)) }
    }

    private fun share() {
        if (state.value.isSharing) return
        val requestGeneration = ++shareGeneration
        update { it.copy(isSharing = true, error = null) }
        viewModelScope.launch {
            prepareMessage.join()
            sharePort.shareText(state.value.composedText) { result ->
                if (requestGeneration != shareGeneration) return@shareText
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
    private var shareGeneration = 0L
    private var saveGeneration = 0L

    override fun handleIntent(intent: InviteQrIntent) {
        when (intent) {
            InviteQrIntent.Share -> share()
            InviteQrIntent.Save -> save()
            InviteQrIntent.Back -> emit(InviteQrEffect.Back)
        }
    }

    private fun share() {
        val image = state.value.pngBytes?.let(::InviteShareImage) ?: return
        if (state.value.isSharing) return
        val requestGeneration = ++shareGeneration
        update { it.copy(isSharing = true, error = null) }
        sharePort.shareImage(image) { result ->
            if (requestGeneration != shareGeneration) return@shareImage
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
        val requestGeneration = ++saveGeneration
        update { it.copy(isSaving = true, error = null) }
        sharePort.saveImage(image) { result ->
            if (requestGeneration != saveGeneration) return@saveImage
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

internal fun renderQr(inviteUrl: String): ByteArray = QRCode.ofSquares().withSize(10).build(inviteUrl).renderToPngBytes()

/** PNG do QR, gerado por plataforma. */
internal expect fun QRCode.renderToPngBytes(): ByteArray

private fun String.withLink(inviteUrl: String, codeHint: String): String {
    val link = if (isBlank()) inviteUrl else "$this\n\n$inviteUrl"
    return if (codeHint.isBlank()) link else "$link\n\n$codeHint"
}

/** O código curto do link do grupo (`…?saqz_invite=44DF8SA8`); o token longo dos links antigos não tem o que mostrar. */
private fun String.shortInviteCodeOrNull(): String? = substringAfter("saqz_invite=", missingDelimiterValue = "")
    .substringBefore('&')
    .let(InviteLinkParser::normalize)
    ?.takeIf { it.length == SHORT_CODE_LENGTH }

private const val SHORT_CODE_LENGTH = 8

private fun GroupEntryRequest.toUi(zoneId: String) =
    PendingEntryRequestUi(userId, displayName, requestedAt.formatDateTime(zoneId))

/** O instante do pedido vem em UTC; a tela mostra a hora do fuso do grupo (ou do aparelho, se faltar). */
private fun String.formatDateTime(zoneId: String): String {
    val instant = runCatching { Instant.parse(this) }.getOrNull() ?: return this
    val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.currentSystemDefault() }
    val local = instant.toLocalDateTime(zone)
    fun Int.pad() = toString().padStart(2, '0')
    return "${local.day.pad()}/${(local.month.ordinal + 1).pad()} · ${local.hour.pad()}:${local.minute.pad()}"
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
