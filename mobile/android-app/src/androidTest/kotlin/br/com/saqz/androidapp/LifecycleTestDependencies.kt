package br.com.saqz.androidapp

import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.port.GroupAttendanceSharePort
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupPhotoSelectionResult
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel
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

internal val lifecycleGroupPhotos = GroupPhotoRuntimeDependencies(
    selection = object : GroupPhotoSelectionPort {
        override suspend fun chooseCamera() = GroupPhotoSelectionResult.Failed
        override suspend fun chooseLibrary() = GroupPhotoSelectionResult.Failed
        override fun cleanup(source: GroupPhotoSourceHandle) = Unit
    },
    encoder = object : GroupPhotoEncoderPort {
        override suspend fun encode(source: GroupPhotoSourceHandle, crop: GroupPhotoCrop) = GroupPhotoEncodingResult.Failed
        override fun cancel(source: GroupPhotoSourceHandle) = Unit
    },
    previews = GroupPhotoPreviewPort { null },
)

internal object LifecycleAttendanceSharePort : GroupAttendanceSharePort {
    override fun shareLink(url: String, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    override fun shareImage(image: AttendanceShareImageModel, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
}

internal object LifecycleGroupDraftStore : GroupDraftStorePort {
    override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) = done(GroupDraftReadResult.Success(null))
    override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) = done(GroupDraftWriteResult.Success)
    override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) = done(GroupDraftWriteResult.Success)
}

internal object LifecycleGameDraftStore : GameDraftStorePort {
    override fun read(groupId: String, resourceId: String?, done: (GameDraftReadResult) -> Unit) = done(GameDraftReadResult.Success(null))
    override fun write(draft: GameEditorDraft, done: (GameDraftWriteResult) -> Unit) = done(GameDraftWriteResult.Success)
    override fun clear(groupId: String, resourceId: String?, commandKey: String, done: (GameDraftWriteResult) -> Unit) = done(GameDraftWriteResult.Success)
}

internal object LifecycleMonthlyChargeDraftStore : MonthlyChargeDraftStorePort {
    override fun read(groupId: String, done: (MonthlyDraftReadResult) -> Unit) = done(MonthlyDraftReadResult.Success(null))
    override fun write(draft: MonthlyChargeDraft, done: (MonthlyDraftWriteResult) -> Unit) = done(MonthlyDraftWriteResult.Success)
    override fun clear(groupId: String, commandKey: String, done: (MonthlyDraftWriteResult) -> Unit) = done(MonthlyDraftWriteResult.Success)
}

internal object LifecycleExpenseDraftStore : ExpenseDraftStorePort {
    override fun read(groupId: String, expenseId: String?, done: (ExpenseDraftReadResult) -> Unit) = done(ExpenseDraftReadResult.Success(null))
    override fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit) = done(ExpenseDraftWriteResult.Success)
    override fun clear(groupId: String, expenseId: String?, commandKey: String, done: (ExpenseDraftWriteResult) -> Unit) = done(ExpenseDraftWriteResult.Success)
}
