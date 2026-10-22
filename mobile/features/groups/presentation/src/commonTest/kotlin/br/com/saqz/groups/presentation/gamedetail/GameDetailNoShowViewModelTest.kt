package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.PastGamesGateway
import br.com.saqz.groups.domain.game.PastGamesPage
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGame
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** DEB-128 (Ver acerto) e DEB-129 (Marcar falta / Desfazer) no ViewModel do jogo. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailNoShowViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val roster = AttendanceRoster(
        confirmed = listOf(
            AttendanceRosterMember("ana", "Ana"),
            AttendanceRosterMember("bia", "Bia"),
            AttendanceRosterMember("bia", "Leo", guestSeq = 1, hostDisplayName = "Bia"),
        ),
        waitlisted = emptyList(),
    )

    @Test
    fun `jogo encerrado sem diaria pendente vira Ver acerto e com pendencia continua o acerto`() = runTest {
        val settled = viewModel(charges = listOf(charge(ChargeStatus.Paid)))
        assertEquals(false, settled.state.value.settlementPending)

        val pending = viewModel(charges = listOf(charge(ChargeStatus.Pending), charge(ChargeStatus.Paid, gameId = "other")))
        assertEquals(true, pending.state.value.settlementPending)

        val athlete = viewModel(role = GroupRole.ATHLETE)
        assertNull(athlete.state.value.settlementPending)
    }

    @Test
    fun `gestor marca a falta a pessoa vai para Faltaram e o servidor recebe membro e guest_seq`() = runTest {
        val gateway = FakePastGames()
        val vm = viewModel(pastGames = gateway)
        assertNotNull(vm.state.value.noShow.until)

        vm.onIntent(GameDetailIntent.OpenPersonActions("ana"))
        assertEquals("ana", vm.state.value.personActionsId)
        vm.onIntent(GameDetailIntent.SetNoShow("ana", marked = true))

        assertEquals(listOf(Call("ana", 0, true)), gateway.calls)
        assertEquals(listOf("ana"), vm.state.value.noShowRoster.map { it.id })
        assertFalse(vm.state.value.playedRoster.any { it.id == "ana" })
        assertNull(vm.state.value.personActionsId)

        vm.onIntent(GameDetailIntent.SetNoShow("bia#1", marked = true))
        assertEquals(Call("bia", 1, true), gateway.calls.last())
    }

    @Test
    fun `desfazer devolve a pessoa a Jogaram`() = runTest {
        val gateway = FakePastGames()
        val marked = roster.copy(confirmed = roster.confirmed.map { if (it.memberId == "ana" && it.guestSeq == 0) it.copy(noShow = true) else it })
        val vm = viewModel(pastGames = gateway, rosterValue = marked)
        assertEquals(listOf("ana"), vm.state.value.noShowRoster.map { it.id })

        vm.onIntent(GameDetailIntent.SetNoShow("ana", marked = false))

        assertEquals(listOf(Call("ana", 0, false)), gateway.calls)
        assertTrue(vm.state.value.noShowRoster.isEmpty())
    }

    @Test
    fun `toque duplo manda uma marca so`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakePastGames(gate = gate)
        val vm = viewModel(pastGames = gateway)

        vm.onIntent(GameDetailIntent.SetNoShow("ana", marked = true))
        vm.onIntent(GameDetailIntent.SetNoShow("ana", marked = true))
        vm.onIntent(GameDetailIntent.SetNoShow("bia", marked = true))
        assertEquals("ana", vm.state.value.noShow.sendingId)
        gate.complete(Unit)

        assertEquals(1, gateway.calls.size)
        assertNull(vm.state.value.noShow.sendingId)
    }

    @Test
    fun `falha mostra o erro e nao muda a lista`() = runTest {
        val gateway = FakePastGames(result = SaqzResult.Failure(GameError.Data(DataError.Connectivity)))
        val vm = viewModel(pastGames = gateway)

        vm.onIntent(GameDetailIntent.OpenPersonActions("ana"))
        vm.onIntent(GameDetailIntent.SetNoShow("ana", marked = true))

        assertTrue(vm.state.value.noShow.failed)
        assertTrue(vm.state.value.noShowRoster.isEmpty())
        assertEquals("ana", vm.state.value.personActionsId)
    }

    @Test
    fun `depois de 7 dias do fim nao marca nem desfaz`() = runTest {
        val gateway = FakePastGames()
        // Início há 8 dias e 2h de jogo: o fim foi há mais de 7 dias.
        val vm = viewModel(pastGames = gateway, startedAgo = 8.days)

        assertNull(vm.state.value.noShow.until)
        assertFalse(vm.state.value.canMarkNoShow)
        vm.onIntent(GameDetailIntent.OpenPersonActions("ana"))
        vm.onIntent(GameDetailIntent.SetNoShow("ana", marked = true))

        assertNull(vm.state.value.personActionsId)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `atleta nao tem a acao`() = runTest {
        val gateway = FakePastGames()
        val vm = viewModel(pastGames = gateway, role = GroupRole.ATHLETE)

        assertFalse(vm.state.value.canMarkNoShow)
        vm.onIntent(GameDetailIntent.SetNoShow("ana", marked = true))
        assertTrue(gateway.calls.isEmpty())
    }

    private fun viewModel(
        role: GroupRole = GroupRole.OWNER,
        pastGames: FakePastGames = FakePastGames(),
        charges: List<Charge> = emptyList(),
        rosterValue: AttendanceRoster = roster,
        startedAgo: kotlin.time.Duration = 3.hours,
    ): GameDetailViewModel {
        val startsAt = (Clock.System.now() - startedAgo).toString()
        val game = sampleGame().copy(status = GameStatus.Completed, startsAt = startsAt, gameFeeCents = 2500)
        val attendance = FakeAttendanceGateway(rosterResult = SaqzResult.Success(rosterValue))
        // O servidor guarda a marca: a recarga depois do sucesso traz a lista já mudada.
        pastGames.onSuccess = { memberId, guestSeq, marked ->
            val current = (attendance.rosterResult as SaqzResult.Success).value
            attendance.rosterResult = SaqzResult.Success(
                current.copy(
                    confirmed = current.confirmed.map {
                        if (it.memberId == memberId && it.guestSeq == guestSeq) it.copy(noShow = marked) else it
                    },
                ),
            )
        }
        return GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(game))),
            FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = role)))),
            attendance,
            FakeAthleteGateway(),
            organizerFinance = FakeOrganizerFinanceGateway(SaqzResult.Success(ChargeList(charges))),
            pastGames = pastGames,
        )
    }

    private fun charge(status: ChargeStatus, gameId: String = "game-1") = Charge(
        id = "charge-$status-$gameId", groupId = GroupId("group-1"), memberId = "ana", kind = ChargeKind.Game,
        gameId = gameId, amountCents = 2500, dueDate = "2026-08-04", status = status, version = 1, audit = emptyList(),
    )

    private data class Call(val memberId: String, val guestSeq: Int, val marked: Boolean)

    private class FakePastGames(
        val result: SaqzResult<Unit, GameError> = SaqzResult.Success(Unit),
        val gate: CompletableDeferred<Unit>? = null,
    ) : PastGamesGateway {
        val calls = mutableListOf<Call>()
        var onSuccess: (String, Int, Boolean) -> Unit = { _, _, _ -> }

        override suspend fun page(groupId: GroupId, cursor: String?): SaqzResult<PastGamesPage, GameError> =
            SaqzResult.Success(PastGamesPage(emptyList(), null))

        override suspend fun setNoShow(groupId: GroupId, gameId: String, memberId: String, guestSeq: Int, marked: Boolean): SaqzResult<Unit, GameError> {
            calls += Call(memberId, guestSeq, marked)
            gate?.await()
            if (result is SaqzResult.Success) onSuccess(memberId, guestSeq, marked)
            return result
        }
    }
}
