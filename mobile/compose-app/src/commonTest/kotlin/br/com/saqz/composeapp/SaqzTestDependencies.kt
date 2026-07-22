package br.com.saqz.composeapp

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.composeapp.di.startSaqzKoin
import br.com.saqz.composeapp.di.stopSaqzKoin
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraftStorePort
import br.com.saqz.groups.presentation.finance.charges.MonthlyDraftReadResult
import br.com.saqz.groups.presentation.finance.charges.MonthlyDraftWriteResult
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraft
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftReadResult
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftStorePort
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftWriteResult
import br.com.saqz.groups.presentation.games.editor.GameDraftReadResult
import br.com.saqz.groups.presentation.games.editor.GameDraftStorePort
import br.com.saqz.groups.presentation.games.editor.GameDraftWriteResult
import br.com.saqz.groups.presentation.games.editor.GameEditorDraft

internal fun startTestSaqzKoin(
    dependencies: SaqzPlatformDependencies = testSaqzPlatformDependencies(),
): SaqzPlatformDependencies {
    stopSaqzKoin()
    startSaqzKoin(dependencies)
    return dependencies
}

internal fun stopTestSaqzKoin() = stopSaqzKoin()

internal fun testSaqzPlatformDependencies() = SaqzPlatformDependencies(
    environment = "test",
    apiBaseUrl = "https://api.invalid",
    auth = TestAuthPort,
    links = TestLinkPort,
    localState = TestLocalAccessStatePort,
    share = TestSharePort,
    attendanceShare = TestAttendanceSharePort,
    groupPhotos = GroupPhotoRuntimeDependencies(
        selection = TestGroupPhotoSelectionPort,
        encoder = TestGroupPhotoEncoderPort,
        previews = GroupPhotoPreviewPort { null },
    ),
    groupLinks = TestGroupLinkPort,
    groupState = TestLocalGroupStatePort,
    groupDrafts = TestGroupDraftStore,
    gameDrafts = TestGameDraftStore,
    monthlyChargeDrafts = TestMonthlyChargeDraftStore,
    expenseDrafts = TestExpenseDraftStore,
)

private object TestAuthPort : NativeAuthPort {
    override fun observe(listener: AuthStateListener): Cancelable {
        listener.onStateChanged(AuthState.SignedOut)
        return TestCancelable
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

private object TestCancelable : Cancelable {
    override fun cancel() = Unit
}

private object TestLinkPort : NativeLinkPort {
    override fun start(listener: InviteCodeListener): Cancelable = TestCancelable
}

private object TestLocalAccessStatePort : LocalAccessStatePort {
    override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
    override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
}

private object TestSharePort : NativeSharePort {
    override fun share(text: String, done: ResultCallback) = done.complete(OperationResult.Success)
}

private object TestAttendanceSharePort : br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort {
    override fun shareLink(url: br.com.saqz.groups.domain.attendance.share.AttendanceLinkUrl, done: (br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult) -> Unit) = done(br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult.Success)
    override fun shareImage(image: br.com.saqz.groups.domain.attendance.share.AttendanceShareImage, done: (br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult) -> Unit) = done(br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult.Success)
}

private object TestGroupPhotoSelectionPort : GroupPhotoSelectionPort {
    override suspend fun chooseCamera() = GroupPhotoSelectionResult.Failed
    override suspend fun chooseLibrary() = GroupPhotoSelectionResult.Failed
    override fun cleanup(source: GroupPhotoSourceHandle) = Unit
}

private object TestGroupPhotoEncoderPort : GroupPhotoEncoderPort {
    override suspend fun encode(source: GroupPhotoSourceHandle, crop: GroupPhotoCrop) = GroupPhotoEncodingResult.Failed
    override fun cancel(source: GroupPhotoSourceHandle) = Unit
}

private object TestGroupLinkPort : NativeGroupLinkPort {
    override fun start(listener: GroupLinkEventListener): GroupCancelable = object : GroupCancelable {
        override fun cancel() = Unit
    }
}

private object TestLocalGroupStatePort : LocalGroupStatePort {
    override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    override fun readPendingInvite(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writePendingInvite(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    override fun readPendingAttendanceLink(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
}

private object TestGroupDraftStore : GroupDraftStorePort {
    override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) = done(GroupDraftReadResult.Success(null))
    override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) = done(GroupDraftWriteResult.Success)
    override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) = done(GroupDraftWriteResult.Success)
}

private object TestGameDraftStore : GameDraftStorePort {
    override fun read(groupId: String, resourceId: String?, done: (GameDraftReadResult) -> Unit) = done(GameDraftReadResult.Success(null))
    override fun write(draft: GameEditorDraft, done: (GameDraftWriteResult) -> Unit) = done(GameDraftWriteResult.Success)
    override fun clear(groupId: String, resourceId: String?, commandKey: String, done: (GameDraftWriteResult) -> Unit) = done(GameDraftWriteResult.Success)
}

private object TestMonthlyChargeDraftStore : MonthlyChargeDraftStorePort {
    override fun read(groupId: String, done: (MonthlyDraftReadResult) -> Unit) = done(MonthlyDraftReadResult.Success(null))
    override fun write(draft: MonthlyChargeDraft, done: (MonthlyDraftWriteResult) -> Unit) = done(MonthlyDraftWriteResult.Success)
    override fun clear(groupId: String, commandKey: String, done: (MonthlyDraftWriteResult) -> Unit) = done(MonthlyDraftWriteResult.Success)
}

private object TestExpenseDraftStore : ExpenseDraftStorePort {
    override fun read(groupId: String, expenseId: String?, done: (ExpenseDraftReadResult) -> Unit) = done(ExpenseDraftReadResult.Success(null))
    override fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit) = done(ExpenseDraftWriteResult.Success)
    override fun clear(groupId: String, expenseId: String?, commandKey: String, done: (ExpenseDraftWriteResult) -> Unit) = done(ExpenseDraftWriteResult.Success)
}
