package br.com.saqz.groups.presentation.ui.finance.review

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.ReviewDecision
import br.com.saqz.groups.domain.finance.UnchargedDecision
import br.com.saqz.groups.domain.finance.UnchargedGame
import br.com.saqz.groups.domain.finance.UnchargedKind
import br.com.saqz.groups.domain.finance.UnchargedList
import br.com.saqz.groups.domain.finance.UnchargedPerson
import br.com.saqz.groups.domain.finance.UnchargedReviewEntry
import br.com.saqz.groups.domain.finance.UnchargedReviewResult
import br.com.saqz.groups.domain.finance.UnchargedSummary
import br.com.saqz.groups.presentation.FakeCashboxGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UnchargedReviewViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial states follow kind no show left group and the stored skip`() = runTest {
        val state = viewModel().state.value

        assertEquals(
            listOf(
                ReviewDecision.Charge, // avulso
                ReviewDecision.Charge, // convidado
                ReviewDecision.Skip, // saiu do grupo
                ReviewDecision.Skip, // faltou
                ReviewDecision.Skip, // Não cobrar já decidido
            ),
            state.rows.map { it.decision },
        )
        assertEquals("Bia Souza", state.rows[1].hostName)
        assertEquals("20,00", state.amountInput)
        assertTrue(state.suggested)
        assertEquals("Terça, 04/08", state.gameDay)
        assertEquals("04/08", state.gameDate)
    }

    @Test
    fun `without a suggested value the field starts empty`() = runTest {
        val cashbox = FakeCashboxGateway(unchargedResult = SaqzResult.Success(list().copy(suggestedAmountCents = null)))
        val state = viewModel(cashbox).state.value

        assertEquals("", state.amountInput)
        assertFalse(state.suggested)
    }

    @Test
    fun `menu choice and the method tap change the summary`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(UnchargedReviewIntent.OpenMenu("caio/0"))
        assertEquals("caio/0", viewModel.state.value.menuKey)
        viewModel.onIntent(UnchargedReviewIntent.Choose("caio/0", ReviewDecision.Paid))
        viewModel.onIntent(UnchargedReviewIntent.CycleMethod("caio/0"))
        val state = viewModel.state.value

        assertNull(state.menuKey)
        assertEquals(PaidMethod.Cash, state.rows.first().method)
        assertEquals(1, state.chargeCount)
        assertEquals(1, state.paidCount)
        assertEquals(3, state.skipCount)
    }

    @Test
    fun `confirm sends one decision per person the amount and the method only on paid`() = runTest {
        val cashbox = FakeCashboxGateway(unchargedResult = SaqzResult.Success(list()))
        val viewModel = viewModel(cashbox)
        viewModel.onIntent(UnchargedReviewIntent.Choose("caio/0", ReviewDecision.Paid))
        viewModel.onIntent(UnchargedReviewIntent.CycleMethod("caio/0"))
        viewModel.onIntent(UnchargedReviewIntent.AmountChanged("10,00"))
        viewModel.onIntent(UnchargedReviewIntent.Confirm)

        val command = cashbox.reviews.single()
        assertEquals(1_000L, command.amountCents)
        assertEquals(
            listOf(
                UnchargedReviewEntry("caio", 0, ReviewDecision.Paid, PaidMethod.Cash),
                UnchargedReviewEntry("bia", 1, ReviewDecision.Charge),
                UnchargedReviewEntry("rafa", 0, ReviewDecision.Skip),
                UnchargedReviewEntry("marcos", 0, ReviewDecision.Skip),
                UnchargedReviewEntry("duda", 0, ReviewDecision.Skip),
            ),
            command.people,
        )
        assertEquals(UnchargedReviewEffect.Done, viewModel.effects.first())
    }

    @Test
    fun `only skips send no amount`() = runTest {
        val cashbox = FakeCashboxGateway(unchargedResult = SaqzResult.Success(list().copy(suggestedAmountCents = null)))
        val viewModel = viewModel(cashbox)
        viewModel.onIntent(UnchargedReviewIntent.Choose("caio/0", ReviewDecision.Skip))
        viewModel.onIntent(UnchargedReviewIntent.Choose("bia/1", ReviewDecision.Skip))
        viewModel.onIntent(UnchargedReviewIntent.Confirm)

        assertNull(cashbox.reviews.single().amountCents)
    }

    @Test
    fun `charging without a valid amount is refused before the server`() = runTest {
        val cashbox = FakeCashboxGateway(unchargedResult = SaqzResult.Success(list().copy(suggestedAmountCents = null)))
        val viewModel = viewModel(cashbox)
        viewModel.onIntent(UnchargedReviewIntent.Confirm)

        assertTrue(viewModel.state.value.amountInvalid)
        assertTrue(cashbox.reviews.isEmpty())
    }

    @Test
    fun `a retry after a failure repeats the same request id`() = runTest {
        val cashbox = FakeCashboxGateway(
            unchargedResult = SaqzResult.Success(list()),
            reviewResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
        )
        val viewModel = viewModel(cashbox)
        viewModel.onIntent(UnchargedReviewIntent.Confirm)
        assertTrue(viewModel.state.value.saveFailed)

        cashbox.reviewResult = SaqzResult.Success(UnchargedReviewResult(2, 0, 3, 0, emptyList()))
        viewModel.onIntent(UnchargedReviewIntent.Confirm)

        assertEquals(2, cashbox.reviews.size)
        assertEquals(cashbox.reviews[0].requestId, cashbox.reviews[1].requestId)
    }

    @Test
    fun `double tap on confirm sends once`() = runTest {
        val reply = CompletableDeferred<SaqzResult<UnchargedReviewResult, FinanceError>>()
        val cashbox = FakeCashboxGateway(unchargedResult = SaqzResult.Success(list()), reviewDeferred = reply)
        val viewModel = viewModel(cashbox)

        viewModel.onIntent(UnchargedReviewIntent.Confirm)
        viewModel.onIntent(UnchargedReviewIntent.Confirm)
        reply.complete(SaqzResult.Success(UnchargedReviewResult(2, 0, 3, 0, emptyList())))

        assertEquals(1, cashbox.reviews.size)
    }

    @Test
    fun `games list keeps the server order and opens the chosen review`() = runTest {
        val cashbox = FakeCashboxGateway(
            summaryResult = SaqzResult.Success(
                UnchargedSummary(
                    6,
                    listOf(
                        UnchargedGame("g2", "Vôlei de terça", "2026-10-20", "19:30", "CERET", 4),
                        UnchargedGame("g1", "Vôlei de terça", "2026-10-13", "19:30", "CERET", 2),
                    ),
                ),
            ),
        )
        val viewModel = UnchargedGamesViewModel("group-1", cashbox)
        viewModel.onIntent(UnchargedGamesIntent.Refresh)

        assertEquals(listOf("Terça, 20/10 · CERET", "Terça, 13/10 · CERET"), viewModel.state.value.games.map { it.title })
        viewModel.onIntent(UnchargedGamesIntent.Open("g1"))
        assertEquals(UnchargedGamesEffect.OpenReview("group-1", "g1"), viewModel.effects.first())
    }

    private fun viewModel(cashbox: FakeCashboxGateway = FakeCashboxGateway(unchargedResult = SaqzResult.Success(list()))) =
        UnchargedReviewViewModel("group-1", "game-1", cashbox, FakeGameGateway())

    private fun list() = UnchargedList(
        gameId = "game-1",
        suggestedAmountCents = 2_000L,
        people = listOf(
            person("caio", "Caio Lima"),
            person("bia", "Júlia Rocha", guestSeq = 1, host = "Bia Souza"),
            person("rafa", "Rafa Nunes", leftGroup = true),
            person("marcos", "Marcos Lima", noShow = true),
            person("duda", "Duda Alves", skip = true),
        ),
    )

    private fun person(
        id: String,
        name: String,
        guestSeq: Int = 0,
        host: String? = null,
        noShow: Boolean = false,
        leftGroup: Boolean = false,
        skip: Boolean = false,
    ) = UnchargedPerson(
        memberId = id,
        guestSeq = guestSeq,
        displayName = name,
        hostDisplayName = host,
        kind = if (host != null) UnchargedKind.Guest else UnchargedKind.Avulso,
        noShow = noShow,
        leftGroup = leftGroup,
        decision = if (skip) UnchargedDecision.Skip else null,
    )
}
