package br.com.saqz.composeapp

import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupAttendanceSharePort
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupPhotoSelectionResult
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraftStorePort
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftStorePort
import br.com.saqz.groups.presentation.games.editor.GameDraftStorePort

class GroupPhotoRuntimeDependencies(
    val selection: GroupPhotoSelectionPort,
    val encoder: GroupPhotoEncoderPort,
    val previews: GroupPhotoPreviewPort,
) {
    companion object {
        val Unconfigured = GroupPhotoRuntimeDependencies(
            selection = UnconfiguredGroupPhotoSelection,
            encoder = UnconfiguredGroupPhotoEncoder,
            previews = GroupPhotoPreviewPort { null },
        )
    }
}

class SaqzAppDependencies(
    val environment: String,
    val apiBaseUrl: String,
    val auth: NativeAuthPort,
    val links: NativeLinkPort = UnconfiguredLinkPort,
    val localState: LocalAccessStatePort,
    val share: NativeSharePort,
    val attendanceShare: GroupAttendanceSharePort = UnconfiguredAttendanceSharePort,
    val groupPhotos: GroupPhotoRuntimeDependencies,
    val groupLinks: NativeGroupLinkPort = UnconfiguredGroupLinkPort,
    val groupState: LocalGroupStatePort = UnconfiguredGroupStatePort,
    val groupDrafts: GroupDraftStorePort = UnconfiguredGroupDraftStore,
    val gameDrafts: GameDraftStorePort = UnconfiguredGameDraftStore,
    val monthlyChargeDrafts: MonthlyChargeDraftStorePort = UnconfiguredMonthlyDraftStore,
    val expenseDrafts: ExpenseDraftStorePort = UnconfiguredExpenseDraftStore,
) {
    init {
        require(environment.isNotBlank()) { "environment must not be blank" }
        require(apiBaseUrl.isNotBlank()) { "API base URL must not be blank" }
    }

    internal companion object {
        val Unconfigured = SaqzAppDependencies(
            environment = "unconfigured",
            apiBaseUrl = "https://api.invalid",
            auth = UnconfiguredAuthPort,
            links = UnconfiguredLinkPort,
            localState = UnconfiguredLocalStatePort,
            share = UnconfiguredSharePort,
            attendanceShare = UnconfiguredAttendanceSharePort,
            groupPhotos = GroupPhotoRuntimeDependencies.Unconfigured,
            groupLinks = UnconfiguredGroupLinkPort,
            groupState = UnconfiguredGroupStatePort,
        )
    }
}

private object UnconfiguredGroupPhotoSelection : GroupPhotoSelectionPort {
    override suspend fun chooseCamera() = GroupPhotoSelectionResult.Failed
    override suspend fun chooseLibrary() = GroupPhotoSelectionResult.Failed
    override fun cleanup(source: br.com.saqz.groups.port.GroupPhotoSourceHandle) = Unit
}

private object UnconfiguredGroupPhotoEncoder : GroupPhotoEncoderPort {
    override suspend fun encode(
        source: br.com.saqz.groups.port.GroupPhotoSourceHandle,
        crop: br.com.saqz.groups.port.GroupPhotoCrop,
    ) = GroupPhotoEncodingResult.Failed

    override fun cancel(source: br.com.saqz.groups.port.GroupPhotoSourceHandle) = Unit
}

private object NoOpCancelable : Cancelable {
    override fun cancel() = Unit
}

private object UnconfiguredAuthPort : NativeAuthPort {
    override fun observe(listener: AuthStateListener): Cancelable {
        listener.onStateChanged(AuthState.SignedOut)
        return NoOpCancelable
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = unavailable(done)
    override fun signInWithPassword(email: String, password: String, done: AuthCallback) = unavailable(done)
    override fun signInWithGoogle(done: AuthCallback) = unavailable(done)
    override fun sendVerification(done: ResultCallback) = unavailable(done)
    override fun reloadUser(done: AuthCallback) = unavailable(done)
    override fun sendPasswordReset(email: String, done: ResultCallback) = unavailable(done)
    override fun updateDisplayName(name: String, done: AuthCallback) = unavailable(done)
    override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
        done.complete(TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
    override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)

    private fun unavailable(done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    private fun unavailable(done: ResultCallback) =
        done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
}

private object UnconfiguredLinkPort : NativeLinkPort {
    override fun start(listener: InviteCodeListener): Cancelable = NoOpCancelable
}

private object UnconfiguredLocalStatePort : LocalAccessStatePort {
    override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
    override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
}

private object UnconfiguredSharePort : NativeSharePort {
    override fun share(text: String, done: ResultCallback) = done.complete(OperationResult.Success)
}

private object UnconfiguredAttendanceSharePort : GroupAttendanceSharePort {
    override fun shareLink(url: String, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    override fun shareImage(image: br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
}

private object UnconfiguredGroupLinkPort : NativeGroupLinkPort {
    override fun start(listener: GroupLinkEventListener): GroupCancelable = object : GroupCancelable { override fun cancel() = Unit }
}

private object UnconfiguredGroupStatePort : LocalGroupStatePort {
    override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    override fun readPendingInvite(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writePendingInvite(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    override fun readPendingAttendanceLink(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
}

private object UnconfiguredGroupDraftStore : GroupDraftStorePort {
    override fun read(key: br.com.saqz.groups.model.GroupDraftKey, done: (br.com.saqz.groups.port.GroupDraftReadResult) -> Unit) = done(br.com.saqz.groups.port.GroupDraftReadResult.Success(null))
    override fun write(draft: br.com.saqz.groups.model.GroupSetupDraft, done: (br.com.saqz.groups.port.GroupDraftWriteResult) -> Unit) = done(br.com.saqz.groups.port.GroupDraftWriteResult.Success)
    override fun clear(key: br.com.saqz.groups.model.GroupDraftKey, commandKey: String, done: (br.com.saqz.groups.port.GroupDraftWriteResult) -> Unit) = done(br.com.saqz.groups.port.GroupDraftWriteResult.Success)
}
private object UnconfiguredGameDraftStore : GameDraftStorePort {
    override fun read(groupId:String,resourceId:String?,done:(br.com.saqz.groups.presentation.games.editor.GameDraftReadResult)->Unit)=done(br.com.saqz.groups.presentation.games.editor.GameDraftReadResult.Success(null))
    override fun write(draft:br.com.saqz.groups.presentation.games.editor.GameEditorDraft,done:(br.com.saqz.groups.presentation.games.editor.GameDraftWriteResult)->Unit)=done(br.com.saqz.groups.presentation.games.editor.GameDraftWriteResult.Success)
    override fun clear(groupId:String,resourceId:String?,commandKey:String,done:(br.com.saqz.groups.presentation.games.editor.GameDraftWriteResult)->Unit)=done(br.com.saqz.groups.presentation.games.editor.GameDraftWriteResult.Success)
}
private object UnconfiguredMonthlyDraftStore : MonthlyChargeDraftStorePort {
    override fun read(groupId:String,done:(br.com.saqz.groups.presentation.finance.charges.MonthlyDraftReadResult)->Unit)=done(br.com.saqz.groups.presentation.finance.charges.MonthlyDraftReadResult.Success(null))
    override fun write(draft:br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft,done:(br.com.saqz.groups.presentation.finance.charges.MonthlyDraftWriteResult)->Unit)=done(br.com.saqz.groups.presentation.finance.charges.MonthlyDraftWriteResult.Success)
    override fun clear(groupId:String,commandKey:String,done:(br.com.saqz.groups.presentation.finance.charges.MonthlyDraftWriteResult)->Unit)=done(br.com.saqz.groups.presentation.finance.charges.MonthlyDraftWriteResult.Success)
}
private object UnconfiguredExpenseDraftStore : ExpenseDraftStorePort {
    override fun read(groupId:String,done:(br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftReadResult)->Unit)=done(br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftReadResult.Success(null))
    override fun write(draft:br.com.saqz.groups.presentation.finance.expenses.ExpenseDraft,done:(br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftWriteResult)->Unit)=done(br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftWriteResult.Success)
    override fun clear(groupId:String,expenseId:String?,commandKey:String,done:(br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftWriteResult)->Unit)=done(br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftWriteResult.Success)
}
