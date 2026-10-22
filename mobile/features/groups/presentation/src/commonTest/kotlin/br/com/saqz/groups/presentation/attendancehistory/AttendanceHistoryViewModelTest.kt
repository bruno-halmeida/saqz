package br.com.saqz.groups.presentation.attendancehistory

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.AttendanceHistoryEntry
import br.com.saqz.groups.domain.game.AttendanceHistoryGateway
import br.com.saqz.groups.domain.game.AttendanceHistoryKind
import br.com.saqz.groups.domain.game.AttendanceHistoryPage
import br.com.saqz.groups.domain.game.AttendanceHistorySource
import br.com.saqz.groups.domain.game.AttendanceHistoryStatus
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** DEB-135 · histórico de respostas: dias, frases, estados, vazio, erro, puxar e paginação. */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceHistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.of("America/Sao_Paulo")

    // Quinta, 22/10/2026 às 20h00 em São Paulo.
    private val now = Instant.parse("2026-10-22T23:00:00Z")

    @Test
    fun `dias frases quem fez e estado de cada linha como no desenho`() = runTest {
        val gateway = FakeHistory(
            mapOf(
                null to AttendanceHistoryPage(
                    listOf(
                        entry("1", "2026-10-22T22:32:00Z", AttendanceHistoryKind.Promoted, "Felipe Nunes", source = AttendanceHistorySource.System, new = AttendanceHistoryStatus.Confirmed, freedBy = "Carla Dias"),
                        entry("2", "2026-10-22T22:31:00Z", AttendanceHistoryKind.GaveUp, "Carla Dias", new = AttendanceHistoryStatus.Declined),
                        entry("3", "2026-10-22T21:05:00Z", AttendanceHistoryKind.OrganizerRemoved, "Pedro Lima", actor = "Bruno Almeida", source = AttendanceHistorySource.Organizer, new = AttendanceHistoryStatus.Declined, reason = "pediu para sair pelo WhatsApp"),
                        entry("4", "2026-10-22T20:40:00Z", AttendanceHistoryKind.Waitlisted, "Felipe Nunes", new = AttendanceHistoryStatus.Waitlisted),
                        entry("5", "2026-10-22T00:10:00Z", AttendanceHistoryKind.GuestJoined, "Ana Souza", guestSeq = 1, new = AttendanceHistoryStatus.Confirmed),
                        entry("6", "2026-10-21T23:15:00Z", AttendanceHistoryKind.Confirmed, "Carla Dias", new = AttendanceHistoryStatus.Confirmed),
                        entry("7", "2026-10-21T23:01:00Z", AttendanceHistoryKind.Declined, "Lucas Melo", new = AttendanceHistoryStatus.Declined),
                        entry("8", "2026-10-20T12:00:00Z", AttendanceHistoryKind.Waitlisted, "Rui Reis", new = AttendanceHistoryStatus.Waitlisted, afterDeadline = true),
                    ),
                    null,
                ),
            ),
        )
        val rows = viewModel(gateway).state.value.rows

        assertEquals(listOf("HOJE", "HOJE", "HOJE", "HOJE", "ONTEM", "ONTEM", "ONTEM", "TER, 20 DE OUTUBRO"), rows.map { it.dayHeader })
        assertEquals(
            listOf(
                "Felipe Nunes subiu da espera", "Carla Dias desistiu", "Bruno tirou Pedro Lima do jogo",
                "Felipe Nunes entrou na espera", "Ana Souza levou um convidado", "Carla Dias confirmou",
                "Lucas Melo disse que não vai", "Rui Reis entrou na espera",
            ),
            rows.map { it.title },
        )
        assertEquals(
            listOf(
                "19h32 · automático, a vaga de Carla abriu", "19h31 · estava confirmado(a)", "18h05 · organizador",
                "17h40", "21h10 · convidado(a) de Ana", "20h15", "20h01", "09h00 · depois do prazo",
            ),
            rows.map { it.meta },
        )
        assertEquals("Motivo: \"pediu para sair pelo WhatsApp\"", rows[2].reason)
        assertTrue(rows.filterIndexed { i, _ -> i != 2 }.all { it.reason == null })
        assertEquals(listOf("Vai", "Não vai", "Não vai", "Espera", "Vai", "Vai", "Não vai", "Espera"), rows.map { it.statusLabel })
        assertEquals(AttendanceHistoryTone.Waiting, rows[3].tone)
        // Felipe está no jogo agora (a linha mais recente dele é a subida); Carla não.
        assertTrue(rows[3].currentlyGoing)
        assertFalse(rows[5].currentlyGoing)
        assertTrue(rows[4].guest)
        assertNull(rows[4].photo)
        assertEquals("tag-1", rows[0].photo?.tag)
    }

    @Test
    fun `convidado que sobe e sai colocado pelo organizador e subida sem saber de quem era a vaga`() = runTest {
        val gateway = FakeHistory(
            mapOf(
                null to AttendanceHistoryPage(
                    listOf(
                        entry("1", "2026-10-22T22:00:00Z", AttendanceHistoryKind.Promoted, "Ana Souza", guestSeq = 1, source = AttendanceHistorySource.System, new = AttendanceHistoryStatus.Confirmed),
                        entry("2", "2026-10-22T21:00:00Z", AttendanceHistoryKind.GuestLeft, "Ana Souza", guestSeq = 2, new = AttendanceHistoryStatus.Declined),
                        entry("3", "2026-10-22T20:00:00Z", AttendanceHistoryKind.OrganizerAdded, "Pedro Lima", actor = "Bruno", source = AttendanceHistorySource.Organizer, new = AttendanceHistoryStatus.Confirmed, reason = "pagou na hora"),
                        entry("4", "2026-10-22T19:00:00Z", AttendanceHistoryKind.LeftWaitlist, "Rui Reis", new = AttendanceHistoryStatus.Declined),
                        entry("5", "2026-10-22T18:00:00Z", AttendanceHistoryKind.Promoted, "Rui Reis", source = AttendanceHistorySource.System, new = AttendanceHistoryStatus.Confirmed, freedBy = "Ana Souza", freedByGuest = true),
                    ),
                    null,
                ),
            ),
        )
        val rows = viewModel(gateway).state.value.rows
        assertEquals(
            listOf(
                "Convidado(a) de Ana subiu da espera", "O convidado de Ana Souza saiu", "Bruno colocou Pedro Lima no jogo",
                "Rui Reis saiu da espera", "Rui Reis subiu da espera",
            ),
            rows.map { it.title },
        )
        assertEquals("19h00 · automático", rows[0].meta)
        assertEquals("15h00 · automático, a vaga do convidado de Ana abriu", rows[4].meta)
    }

    @Test
    fun `vazio erro e tentar de novo`() = runTest {
        val gateway = FakeHistory(mapOf(null to AttendanceHistoryPage(emptyList(), null)))
        val empty = viewModel(gateway)
        assertFalse(empty.state.value.isLoading)
        assertTrue(empty.state.value.rows.isEmpty())

        gateway.fail = true
        val failed = viewModel(gateway)
        assertTrue(failed.state.value.loadFailed)
        assertEquals(GroupUiError.Network, failed.state.value.error)

        gateway.fail = false
        failed.onIntent(AttendanceHistoryIntent.Retry)
        assertFalse(failed.state.value.loadFailed)
    }

    @Test
    fun `carregar mais junta a proxima pagina e falha deixa tentar de novo`() = runTest {
        val gateway = FakeHistory(
            mapOf(
                null to AttendanceHistoryPage(listOf(entry("1", "2026-10-22T22:00:00Z", AttendanceHistoryKind.Confirmed, "Ana")), "c1"),
                "c1" to AttendanceHistoryPage(listOf(entry("2", "2026-10-22T21:00:00Z", AttendanceHistoryKind.Confirmed, "Rui")), null),
            ),
        )
        val viewModel = viewModel(gateway)
        assertTrue(viewModel.state.value.canLoadMore)

        gateway.fail = true
        viewModel.onIntent(AttendanceHistoryIntent.LoadMore)
        assertTrue(viewModel.state.value.loadMoreFailed)

        gateway.fail = false
        viewModel.onIntent(AttendanceHistoryIntent.LoadMore)
        assertEquals(listOf("1", "2"), viewModel.state.value.rows.map { it.id })
        assertFalse(viewModel.state.value.canLoadMore)
    }

    @Test
    fun `puxar traz o que e novo por cima e falha mostra o aviso sem tirar a lista`() = runTest {
        val gateway = FakeHistory(
            mapOf(null to AttendanceHistoryPage(listOf(entry("1", "2026-10-22T22:00:00Z", AttendanceHistoryKind.Confirmed, "Ana")), null)),
        )
        val viewModel = viewModel(gateway)

        gateway.pages = mapOf(
            null to AttendanceHistoryPage(
                listOf(
                    entry("2", "2026-10-22T22:30:00Z", AttendanceHistoryKind.GaveUp, "Ana", new = AttendanceHistoryStatus.Declined),
                    entry("1", "2026-10-22T22:00:00Z", AttendanceHistoryKind.Confirmed, "Ana"),
                ),
                null,
            ),
        )
        viewModel.onIntent(AttendanceHistoryIntent.PullRefresh)
        assertEquals(listOf("2", "1"), viewModel.state.value.rows.map { it.id })
        assertFalse(viewModel.state.value.isRefreshing)

        gateway.fail = true
        viewModel.onIntent(AttendanceHistoryIntent.PullRefresh)
        assertTrue(viewModel.state.value.refreshFailed)
        assertEquals(2, viewModel.state.value.rows.size)
        viewModel.onIntent(AttendanceHistoryIntent.DismissRefreshFailed)
        assertFalse(viewModel.state.value.refreshFailed)

        // Volta à tela com falha: não mostra aviso (só o puxão avisa).
        viewModel.onIntent(AttendanceHistoryIntent.Refresh)
        assertFalse(viewModel.state.value.refreshFailed)
    }

    @Test
    fun `puxao com carregar mais no ar pega carona`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeHistory(
            mapOf(
                null to AttendanceHistoryPage(listOf(entry("1", "2026-10-22T22:00:00Z", AttendanceHistoryKind.Confirmed, "Ana")), "c1"),
                "c1" to AttendanceHistoryPage(listOf(entry("2", "2026-10-22T21:00:00Z", AttendanceHistoryKind.Confirmed, "Rui")), null),
            ),
        )
        val viewModel = viewModel(gateway)
        gateway.gate = gate
        viewModel.onIntent(AttendanceHistoryIntent.LoadMore)
        viewModel.onIntent(AttendanceHistoryIntent.PullRefresh)
        assertTrue(viewModel.state.value.isRefreshing)
        val calls = gateway.calls
        gate.complete(Unit)
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(calls, gateway.calls)
    }

    private fun viewModel(gateway: AttendanceHistoryGateway) =
        AttendanceHistoryViewModel("group-1", "game-1", gateway, zone) { now }

    private fun entry(
        id: String,
        at: String,
        kind: AttendanceHistoryKind,
        member: String,
        actor: String = member,
        source: AttendanceHistorySource = AttendanceHistorySource.Self,
        new: AttendanceHistoryStatus = AttendanceHistoryStatus.Confirmed,
        reason: String? = null,
        guestSeq: Int = 0,
        afterDeadline: Boolean = false,
        freedBy: String? = null,
        freedByGuest: Boolean = false,
    ) = AttendanceHistoryEntry(
        id, at, kind, "member-$member", member, "tag-1", guestSeq, null, actor, source, null, new, reason, afterDeadline,
        freedBy, freedByGuest,
    )

    private class FakeHistory(var pages: Map<String?, AttendanceHistoryPage>) : AttendanceHistoryGateway {
        var fail = false
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun page(groupId: GroupId, gameId: String, cursor: String?): SaqzResult<AttendanceHistoryPage, GameError> {
            calls++
            gate?.await()
            if (fail) return SaqzResult.Failure(GameError.Data(DataError.Connectivity))
            return SaqzResult.Success(pages.getValue(cursor))
        }
    }
}
