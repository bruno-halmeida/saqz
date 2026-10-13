package br.com.saqz.webapp.groups

import br.com.saqz.groups.model.ExpenseDraft
import br.com.saqz.groups.model.GameEditorDraft
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.MonthlyChargeDraft
import br.com.saqz.groups.port.ExpenseDraftReadResult
import br.com.saqz.groups.port.ExpenseDraftStorePort
import br.com.saqz.groups.port.ExpenseDraftWriteResult
import br.com.saqz.groups.port.GameDraftReadResult
import br.com.saqz.groups.port.GameDraftStorePort
import br.com.saqz.groups.port.GameDraftWriteResult
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.MonthlyChargeDraftStorePort
import br.com.saqz.groups.port.MonthlyDraftReadResult
import br.com.saqz.groups.port.MonthlyDraftWriteResult

// STUB: substituir por localStorage + IOSGroupDraftCodec (compose-app, commonMain), espelhando
// SaqzIOS/GroupsDraft/IOSGroupDraftAdapters.swift (chave por tipo/grupo/recurso, schemaVersion,
// clear só quando o commandKey bate).
internal class WebGroupDraftStores {
    val setup: GroupDraftStorePort = object : GroupDraftStorePort {
        override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) = done(GroupDraftReadResult.Success(null))
        override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) = done(GroupDraftWriteResult.Success)
        override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)
    }

    val game: GameDraftStorePort = object : GameDraftStorePort {
        override fun read(groupId: String, resourceId: String?, done: (GameDraftReadResult) -> Unit) =
            done(GameDraftReadResult.Success(null))
        override fun write(draft: GameEditorDraft, done: (GameDraftWriteResult) -> Unit) = done(GameDraftWriteResult.Success)
        override fun clear(groupId: String, resourceId: String?, commandKey: String, done: (GameDraftWriteResult) -> Unit) =
            done(GameDraftWriteResult.Success)
    }

    val monthly: MonthlyChargeDraftStorePort = object : MonthlyChargeDraftStorePort {
        override fun read(groupId: String, done: (MonthlyDraftReadResult) -> Unit) = done(MonthlyDraftReadResult.Success(null))
        override fun write(draft: MonthlyChargeDraft, done: (MonthlyDraftWriteResult) -> Unit) = done(MonthlyDraftWriteResult.Success)
        override fun clear(groupId: String, commandKey: String, done: (MonthlyDraftWriteResult) -> Unit) =
            done(MonthlyDraftWriteResult.Success)
    }

    val expense: ExpenseDraftStorePort = object : ExpenseDraftStorePort {
        override fun read(groupId: String, expenseId: String?, done: (ExpenseDraftReadResult) -> Unit) =
            done(ExpenseDraftReadResult.Success(null))
        override fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit) = done(ExpenseDraftWriteResult.Success)
        override fun clear(groupId: String, expenseId: String?, commandKey: String, done: (ExpenseDraftWriteResult) -> Unit) =
            done(ExpenseDraftWriteResult.Success)
    }
}
