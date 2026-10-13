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
import br.com.saqz.groups.port.GroupDraftFailure
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.MonthlyChargeDraftStorePort
import br.com.saqz.groups.port.MonthlyDraftReadResult
import br.com.saqz.groups.port.MonthlyDraftWriteResult

/**
 * Os quatro ports de rascunho sobre o [WebGroupDraftStore] (`localStorage`), com a mesma tradução de falhas
 * dos adapters do iOS: no setup, rascunho corrompido ou de schema antigo vira falha tipada; nos demais ports,
 * qualquer leitura que não seja "achou" ou "não existe" é `Failure`.
 */
internal class WebGroupDraftStores(private val store: WebGroupDraftStore = WebGroupDraftStore()) {
    val setup: GroupDraftStorePort = object : GroupDraftStorePort {
        override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) = done(
            when (val result = store.readSetup(key)) {
                is DraftRead.Found -> GroupDraftReadResult.Success(result.value)
                DraftRead.Missing -> GroupDraftReadResult.Success(null)
                DraftRead.Corrupt -> GroupDraftReadResult.Failure(GroupDraftFailure.CORRUPT)
                DraftRead.UnsupportedSchema -> GroupDraftReadResult.Failure(GroupDraftFailure.UNSUPPORTED_SCHEMA)
            },
        )

        override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) =
            done(if (store.writeSetup(draft)) GroupDraftWriteResult.Success else unavailable)

        override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) =
            done(if (store.clearSetup(key, commandKey)) GroupDraftWriteResult.Success else unavailable)

        private val unavailable get() = GroupDraftWriteResult.Failure(GroupDraftFailure.UNAVAILABLE)
    }

    val game: GameDraftStorePort = object : GameDraftStorePort {
        override fun read(groupId: String, resourceId: String?, done: (GameDraftReadResult) -> Unit) = done(
            when (val result = store.readGame(groupId, resourceId)) {
                is DraftRead.Found -> GameDraftReadResult.Success(result.value)
                DraftRead.Missing -> GameDraftReadResult.Success(null)
                DraftRead.Corrupt, DraftRead.UnsupportedSchema -> GameDraftReadResult.Failure
            },
        )

        override fun write(draft: GameEditorDraft, done: (GameDraftWriteResult) -> Unit) =
            done(if (store.writeGame(draft)) GameDraftWriteResult.Success else GameDraftWriteResult.Failure)

        override fun clear(groupId: String, resourceId: String?, commandKey: String, done: (GameDraftWriteResult) -> Unit) =
            done(
                if (store.clearGame(groupId, resourceId, commandKey)) GameDraftWriteResult.Success else GameDraftWriteResult.Failure,
            )
    }

    val monthly: MonthlyChargeDraftStorePort = object : MonthlyChargeDraftStorePort {
        override fun read(groupId: String, done: (MonthlyDraftReadResult) -> Unit) = done(
            when (val result = store.readMonthly(groupId)) {
                is DraftRead.Found -> MonthlyDraftReadResult.Success(result.value)
                DraftRead.Missing -> MonthlyDraftReadResult.Success(null)
                DraftRead.Corrupt, DraftRead.UnsupportedSchema -> MonthlyDraftReadResult.Failure
            },
        )

        override fun write(draft: MonthlyChargeDraft, done: (MonthlyDraftWriteResult) -> Unit) =
            done(if (store.writeMonthly(draft)) MonthlyDraftWriteResult.Success else MonthlyDraftWriteResult.Failure)

        override fun clear(groupId: String, commandKey: String, done: (MonthlyDraftWriteResult) -> Unit) =
            done(if (store.clearMonthly(groupId, commandKey)) MonthlyDraftWriteResult.Success else MonthlyDraftWriteResult.Failure)
    }

    val expense: ExpenseDraftStorePort = object : ExpenseDraftStorePort {
        override fun read(groupId: String, expenseId: String?, done: (ExpenseDraftReadResult) -> Unit) = done(
            when (val result = store.readExpense(groupId, expenseId)) {
                is DraftRead.Found -> ExpenseDraftReadResult.Success(result.value)
                DraftRead.Missing -> ExpenseDraftReadResult.Success(null)
                DraftRead.Corrupt, DraftRead.UnsupportedSchema -> ExpenseDraftReadResult.Failure
            },
        )

        override fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit) =
            done(if (store.writeExpense(draft)) ExpenseDraftWriteResult.Success else ExpenseDraftWriteResult.Failure)

        override fun clear(groupId: String, expenseId: String?, commandKey: String, done: (ExpenseDraftWriteResult) -> Unit) =
            done(
                if (store.clearExpense(groupId, expenseId, commandKey)) {
                    ExpenseDraftWriteResult.Success
                } else {
                    ExpenseDraftWriteResult.Failure
                },
            )
    }
}
