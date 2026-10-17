package br.com.saqz.webapp.groups

import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.model.ExpenseDraft
import br.com.saqz.groups.model.ExpenseForm
import br.com.saqz.groups.model.GameEditorDraft
import br.com.saqz.groups.model.GameEditorForm
import br.com.saqz.groups.model.GameEditorMode
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupDraftResource
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.MonthlyChargeDraft
import br.com.saqz.groups.port.ExpenseDraftReadResult
import br.com.saqz.groups.port.ExpenseDraftWriteResult
import br.com.saqz.groups.port.GameDraftReadResult
import br.com.saqz.groups.port.GameDraftWriteResult
import br.com.saqz.groups.port.GroupDraftFailure
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.MonthlyDraftReadResult
import br.com.saqz.groups.port.MonthlyDraftWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("TooManyFunctions") // um caso por comportamento do store, como no IOSGroupDraftAdaptersTests
class WebGroupDraftStoresTest {
    @Test
    fun setupRoundTripsThroughThePort() {
        val fixture = Fixture()
        val written = capture<GroupDraftWriteResult> { fixture.stores.setup.write(setup(), it) }
        assertEquals(GroupDraftWriteResult.Success, written)

        val read = capture<GroupDraftReadResult> { fixture.stores.setup.read(GroupDraftKey(GroupDraftResource.UPDATE_GROUP, GROUP), it) }

        val restored = assertNotNull(assertIs<GroupDraftReadResult.Success>(read).draft)
        assertEquals(setup(), restored)
    }

    @Test
    fun gameRoundTripsAndIsKeyedByGroupAndGame() {
        val fixture = Fixture()
        assertEquals(GameDraftWriteResult.Success, capture<GameDraftWriteResult> { fixture.stores.game.write(game(), it) })

        val read = capture<GameDraftReadResult> { fixture.stores.game.read(GROUP, GAME, it) }
        val other = capture<GameDraftReadResult> { fixture.stores.game.read(GROUP, "other-game", it) }
        val noResource = capture<GameDraftReadResult> { fixture.stores.game.read(GROUP, null, it) }

        assertEquals(game(), assertIs<GameDraftReadResult.Success>(read).draft)
        assertEquals(null, assertIs<GameDraftReadResult.Success>(other).draft)
        assertEquals(null, assertIs<GameDraftReadResult.Success>(noResource).draft)
    }

    @Test
    fun monthlyAndExpenseRoundTrip() {
        val fixture = Fixture()
        assertEquals(MonthlyDraftWriteResult.Success, capture<MonthlyDraftWriteResult> { fixture.stores.monthly.write(monthly(), it) })
        assertEquals(ExpenseDraftWriteResult.Success, capture<ExpenseDraftWriteResult> { fixture.stores.expense.write(expense(), it) })

        val monthly = capture<MonthlyDraftReadResult> { fixture.stores.monthly.read(GROUP, it) }
        val expense = capture<ExpenseDraftReadResult> { fixture.stores.expense.read(GROUP, EXPENSE, it) }

        assertEquals(monthly(), assertIs<MonthlyDraftReadResult.Success>(monthly).draft)
        assertEquals(expense(), assertIs<ExpenseDraftReadResult.Success>(expense).draft)
    }

    @Test
    fun draftsOfDifferentTypesAndGroupsDoNotCollide() {
        val fixture = Fixture()
        fixture.stores.monthly.write(monthly(), {})
        fixture.stores.expense.write(expense(), {})
        fixture.stores.expense.write(expense().copy(groupId = "group-2"), {})

        assertEquals(3, fixture.items.size)
        assertTrue(fixture.items.keys.all { it.startsWith("saqz.groups.draft.") })
        val monthly = capture<MonthlyDraftReadResult> { fixture.stores.monthly.read("group-2", it) }
        assertEquals(null, assertIs<MonthlyDraftReadResult.Success>(monthly).draft)
    }

    @Test
    fun corruptEnvelopeIsATypedFailureOnSetupAndAPlainFailureElsewhere() {
        val fixture = Fixture()
        fixture.stores.setup.write(setup(), {})
        fixture.stores.expense.write(expense(), {})
        fixture.items.keys.toList().forEach { fixture.items[it] = "not-json" }

        val setup = capture<GroupDraftReadResult> {
            fixture.stores.setup.read(GroupDraftKey(GroupDraftResource.UPDATE_GROUP, GROUP), it)
        }
        val expense = capture<ExpenseDraftReadResult> { fixture.stores.expense.read(GROUP, EXPENSE, it) }

        assertEquals(GroupDraftReadResult.Failure(GroupDraftFailure.CORRUPT), setup)
        assertEquals(ExpenseDraftReadResult.Failure, expense)
    }

    @Test
    fun envelopeStoredUnderAnotherDraftsKeyIsCorrupt() {
        val fixture = Fixture()
        fixture.stores.monthly.write(monthly(), {})
        // O envelope diz que é do group-1; sob a chave do grupo "other" ele não pode valer.
        fixture.items[monthlyKeyFor("other")] = fixture.items.values.single()

        val read = capture<MonthlyDraftReadResult> { fixture.stores.monthly.read("other", it) }

        assertEquals(MonthlyDraftReadResult.Failure, read)
    }

    @Test
    fun oldPayloadSchemaIsUnsupportedWithoutBeingDispatched() {
        val fixture = Fixture()
        fixture.stores.monthly.write(monthly().copy(schemaVersion = 99), {})
        fixture.stores.setup.write(setup().copy(schemaVersion = 99), {})

        val monthly = capture<MonthlyDraftReadResult> { fixture.stores.monthly.read(GROUP, it) }
        val setup = capture<GroupDraftReadResult> {
            fixture.stores.setup.read(GroupDraftKey(GroupDraftResource.UPDATE_GROUP, GROUP), it)
        }

        assertEquals(MonthlyDraftReadResult.Failure, monthly)
        assertEquals(GroupDraftReadResult.Failure(GroupDraftFailure.UNSUPPORTED_SCHEMA), setup)
    }

    @Test
    fun clearOnlyRemovesWhenTheCommandKeyMatches() {
        val fixture = Fixture()
        fixture.stores.monthly.write(monthly(), {})
        fixture.stores.expense.write(expense(), {})

        assertEquals(
            MonthlyDraftWriteResult.Success,
            capture<MonthlyDraftWriteResult> { fixture.stores.monthly.clear(GROUP, "other-attempt", it) },
        )
        assertIs<MonthlyDraftReadResult.Success>(capture<MonthlyDraftReadResult> { fixture.stores.monthly.read(GROUP, it) }).also {
            assertEquals(monthly(), it.draft)
        }

        assertEquals(
            MonthlyDraftWriteResult.Success,
            capture<MonthlyDraftWriteResult> { fixture.stores.monthly.clear(GROUP, COMMAND_KEY, it) },
        )
        val cleared = capture<MonthlyDraftReadResult> { fixture.stores.monthly.read(GROUP, it) }
        assertEquals(null, assertIs<MonthlyDraftReadResult.Success>(cleared).draft)
        val remaining = capture<ExpenseDraftReadResult> { fixture.stores.expense.read(GROUP, EXPENSE, it) }
        assertEquals(expense(), assertIs<ExpenseDraftReadResult.Success>(remaining).draft)
    }

    @Test
    fun clearingWhatDoesNotExistIsASuccess() {
        val fixture = Fixture()

        assertEquals(
            GameDraftWriteResult.Success,
            capture<GameDraftWriteResult> { fixture.stores.game.clear(GROUP, GAME, COMMAND_KEY, it) },
        )
        assertEquals(
            ExpenseDraftWriteResult.Success,
            capture<ExpenseDraftWriteResult> { fixture.stores.expense.clear(GROUP, null, COMMAND_KEY, it) },
        )
        assertEquals(
            GroupDraftWriteResult.Success,
            capture<GroupDraftWriteResult> {
                fixture.stores.setup.clear(GroupDraftKey(GroupDraftResource.CREATE_GROUP, null), COMMAND_KEY, it)
            },
        )
    }

    @Test
    fun setupClearUsesTheResourceAndGroupOfTheKey() {
        val fixture = Fixture()
        fixture.stores.setup.write(setup(), {})
        val otherKey = GroupDraftKey(GroupDraftResource.CREATE_GROUP, null)

        fixture.stores.setup.clear(otherKey, COMMAND_KEY, {})
        assertEquals(1, fixture.items.size)

        fixture.stores.setup.clear(GroupDraftKey(GroupDraftResource.UPDATE_GROUP, GROUP), COMMAND_KEY, {})
        assertTrue(fixture.items.isEmpty())
    }

    @Test
    fun unavailableStorageFailsWritesWithoutThrowing() {
        val fixture = Fixture(writable = false)

        assertEquals(
            GroupDraftWriteResult.Failure(GroupDraftFailure.UNAVAILABLE),
            capture<GroupDraftWriteResult> { fixture.stores.setup.write(setup(), it) },
        )
        assertEquals(GameDraftWriteResult.Failure, capture<GameDraftWriteResult> { fixture.stores.game.write(game(), it) })
        assertEquals(MonthlyDraftWriteResult.Failure, capture<MonthlyDraftWriteResult> { fixture.stores.monthly.write(monthly(), it) })
        assertEquals(ExpenseDraftWriteResult.Failure, capture<ExpenseDraftWriteResult> { fixture.stores.expense.write(expense(), it) })
    }

    @Test
    fun serializedDraftsNeverCarryForbiddenFields() {
        val fixture = Fixture()
        fixture.stores.setup.write(setup(), {})
        fixture.stores.game.write(game(), {})
        fixture.stores.monthly.write(monthly(), {})
        fixture.stores.expense.write(expense(), {})

        val raw = fixture.items.values.joinToString()
        listOf("bearerToken", "inviteCode", "photoBytes", "photoHandle", "paymentCredential", "rawServerError")
            .forEach { assertTrue("\"$it\"" !in raw, it) }
    }

    @Test
    fun envelopeCarriesTypeGroupResourceAndStorageVersion() {
        val fixture = Fixture()
        fixture.stores.expense.write(expense(), {})

        val raw = fixture.items.values.single()

        assertTrue("\"storageVersion\":1" in raw)
        assertTrue("\"type\":\"expense\"" in raw)
        assertTrue("\"groupId\":\"$GROUP\"" in raw)
        assertTrue("\"resourceId\":\"$EXPENSE\"" in raw)
    }

    private class Fixture(private val writable: Boolean = true) {
        val items = mutableMapOf<String, String>()
        val stores = WebGroupDraftStores(
            WebGroupDraftStore(
                readItem = { items[it] },
                writeItem = { key, value ->
                    if (!writable) {
                        false
                    } else {
                        if (value == null) items.remove(key) else items[key] = value
                        true
                    }
                },
            ),
        )
    }

    /** Os ports respondem de forma síncrona; isto devolve a única resposta. */
    private fun <T> capture(call: ((T) -> Unit) -> Unit): T {
        val results = mutableListOf<T>()
        call { results += it }
        return results.single()
    }

    private fun monthlyKeyFor(groupId: String) =
        "saqz.groups.draft.monthly." + kotlin.io.encoding.Base64.UrlSafe
            .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
            .encode(groupId.encodeToByteArray()) + "._"

    private fun setup() = GroupSetupDraft(
        resource = GroupDraftResource.UPDATE_GROUP,
        groupId = GROUP,
        groupVersion = 7,
        etag = "\"7\"",
        commandKey = COMMAND_KEY,
        form = GroupSetupForm(
            name = "Vôlei",
            modality = GroupModality.COURT_VOLLEYBALL,
            composition = GroupComposition.MIXED,
            city = "Recife",
            defaultCapacity = 24,
            monthlyFeeCents = 7000,
            monthlyDueDay = 10,
        ),
    )

    private fun game() = GameEditorDraft(
        groupId = GROUP,
        gameId = GAME,
        seriesId = null,
        commandKey = COMMAND_KEY,
        version = GameVersionToken("\"4\""),
        mode = GameEditorMode.ONE_TIME,
        form = GameEditorForm(
            title = "Treino",
            venue = GameVenue(null, "Arena", "Rua 1", null),
            localDate = "2026-08-12",
            localTime = "19:30:00",
            zoneId = "America/Sao_Paulo",
            durationMinutes = "90",
            capacity = "24",
            gameFeeBrl = "25,00",
        ),
    )

    private fun monthly() = MonthlyChargeDraft(
        groupId = GROUP,
        commandKey = COMMAND_KEY,
        month = "2026-08",
        amountBrl = "70,00",
        dueDate = "2026-08-10",
        selectedMemberIds = setOf("member-1", "member-2"),
        reviewed = true,
    )

    private fun expense() = ExpenseDraft(
        groupId = GROUP,
        expenseId = EXPENSE,
        etag = "\"3\"",
        commandKey = COMMAND_KEY,
        form = ExpenseForm(description = "Água do jogo", amountBrl = "123,45", expenseDate = "2026-08-12", notes = "Compra manual"),
    )

    private companion object {
        const val GROUP = "group-1"
        const val GAME = "game-1"
        const val EXPENSE = "expense-1"
        const val COMMAND_KEY = "draft-key"
    }
}
