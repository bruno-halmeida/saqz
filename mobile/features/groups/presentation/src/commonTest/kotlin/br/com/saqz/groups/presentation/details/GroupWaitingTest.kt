package br.com.saqz.groups.presentation.details

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.presentation.sampleGame
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GroupWaitingTest {
    @Test
    fun `nothing pending means no block`() = runTest {
        assertNull(groupWaiting(emptyList(), emptyList(), emptyList(), "2026-08", reviewingGameId = null))
    }

    @Test
    fun `monthly row sums only the pending charges of the current month`() = runTest {
        val charges = listOf(
            monthly("a", "2026-08", 7_000L),
            monthly("b", "2026-08", 6_500L),
            monthly("julho", "2026-07", 7_000L),
            monthly("paga", "2026-08", 7_000L, ChargeStatus.Paid),
        )

        val waiting = assertNotNull(groupWaiting(charges, emptyList(), emptyList(), "2026-08", reviewingGameId = null))

        assertEquals(
            GroupWaitingRowUi(
                title = "2 mensalidades a receber",
                meta = "R$ 135,00 · AGO",
                contentDescription = "2 mensalidades a receber. R$ 135,00 · AGO",
                count = 2,
            ),
            waiting.monthly,
        )
        assertNull(waiting.settle)
        assertNull(waiting.entryRequests)
    }

    @Test
    fun `settle row points at the most recent completed game that still has pending day charges`() = runTest {
        val games = listOf(
            completed("old", "2026-07-21T19:30:00-03:00"),
            completed("done", "2026-07-28T19:30:00-03:00"),
            completed("clean", "2026-07-30T19:30:00-03:00"),
            sampleGame(),
        )
        val charges = listOf(
            dayCharge("c1", "old"),
            dayCharge("c2", "done"),
            dayCharge("c3", "done", 3_000L),
            dayCharge("c4", "clean", status = ChargeStatus.Paid),
            dayCharge("c5", sampleGame().id),
        )

        val waiting = assertNotNull(groupWaiting(charges, games, emptyList(), "2026-08", reviewingGameId = null))

        assertEquals(
            GroupSettleRowUi(
                gameId = "done",
                title = "Acertar o jogo de 28/07",
                meta = "2 avulsos · R$ 55,00 a receber",
                contentDescription = "Acertar o jogo de 28/07. 2 avulsos · R$ 55,00 a receber",
            ),
            waiting.settle,
        )
    }

    @Test
    fun `settle row steps aside while the onboarding guide reviews that same game`() = runTest {
        val games = listOf(completed("done", "2026-07-28T19:30:00-03:00"))
        val charges = listOf(dayCharge("c1", "done"))

        assertNull(groupWaiting(charges, games, emptyList(), "2026-08", reviewingGameId = "done"))
        assertNotNull(groupWaiting(charges, games, emptyList(), "2026-08", reviewingGameId = "other")?.settle)
    }

    @Test
    fun `entry requests name one two or many people`() = runTest {
        val one = assertNotNull(waitingFor("Ana").entryRequests)
        val two = assertNotNull(waitingFor("Ana", "Bia").entryRequests)
        val many = assertNotNull(waitingFor("Ana", "Bia", "Caio", "Duda").entryRequests)

        assertEquals("Ana" to 1, one.meta to one.count)
        assertEquals("Ana e Bia" to 2, two.meta to two.count)
        assertEquals(
            GroupWaitingRowUi(
                title = "4 pedidos para entrar",
                meta = "Ana, Bia e mais 2",
                contentDescription = "4 pedidos para entrar. Ana, Bia e mais 2",
                count = 4,
            ),
            many,
        )
    }

    @Test
    fun `the three rows come together`() = runTest {
        val waiting = assertNotNull(
            groupWaiting(
                charges = listOf(monthly("a", "2026-08", 7_000L), dayCharge("c1", "done")),
                games = listOf(completed("done", "2026-07-28T19:30:00-03:00")),
                entryRequests = listOf(request("Ana")),
                monthKey = "2026-08",
                reviewingGameId = null,
            ),
        )

        assertNotNull(waiting.entryRequests)
        assertNotNull(waiting.monthly)
        assertEquals("done", waiting.settle?.gameId)
    }

    private suspend fun waitingFor(vararg names: String) = assertNotNull(
        groupWaiting(emptyList(), emptyList(), names.map(::request), "2026-08", reviewingGameId = null),
    )

    private fun request(name: String) =
        GroupEntryRequest(userId = name.lowercase(), displayName = name, requestedAt = "2026-08-01T12:00:00Z")

    private fun completed(id: String, startsAt: String) =
        sampleGame().copy(id = id, status = GameStatus.Completed, startsAt = startsAt)

    private fun monthly(id: String, month: String, cents: Long, status: ChargeStatus = ChargeStatus.Pending) = Charge(
        id = id,
        groupId = GroupId("group-1"),
        memberId = "member-$id",
        kind = ChargeKind.Monthly,
        month = month,
        amountCents = cents,
        dueDate = "$month-10",
        status = status,
        version = 1,
        audit = emptyList(),
    )

    private fun dayCharge(id: String, gameId: String, cents: Long = 2_500L, status: ChargeStatus = ChargeStatus.Pending) = Charge(
        id = id,
        groupId = GroupId("group-1"),
        memberId = "member-$id",
        kind = ChargeKind.Game,
        gameId = gameId,
        amountCents = cents,
        dueDate = "2026-07-28",
        status = status,
        version = 1,
        audit = emptyList(),
    )
}
