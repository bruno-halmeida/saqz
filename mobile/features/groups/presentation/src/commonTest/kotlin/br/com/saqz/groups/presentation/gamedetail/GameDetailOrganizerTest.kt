package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleRosterEntry
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DefaultReason = "Escolha do organizador"

/** DEB-017: o gestor tira outra pessoa do jogo e coloca alguém nele (`override`). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailOrganizerTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun openGame(): Game =
        sampleGame().copy(confirmationDeadline = "2030-01-01T12:00:00-03:00", startsAt = "2030-01-01T20:00:00-03:00")

    private fun startedGame(): Game = sampleGame().copy(startsAt = "2020-01-01T20:00:00-03:00")

    /** Eu ("me", gestor) e Ana confirmados; Caio e Duda na espera; Edu disse "Não vou"; Fabi nunca respondeu. */
    private fun viewModel(
        attendance: FakeAttendanceGateway = FakeAttendanceGateway(),
        game: Game = openGame(),
        role: GroupRole = GroupRole.ADMIN,
        answered: Boolean = true,
        guestOnWaitlist: Boolean = false,
        full: Boolean = false,
    ): GameDetailViewModel {
        val roster = AttendanceRoster(
            confirmed = buildList {
                if (answered) add(AttendanceRosterMember("me", "Bruno"))
                add(AttendanceRosterMember("ana", "Ana"))
            },
            waitlisted = buildList {
                add(AttendanceRosterMember("caio", "Caio", 1))
                add(AttendanceRosterMember("duda", "Duda", 2))
                if (guestOnWaitlist) add(AttendanceRosterMember("ana", "Convidado da Ana", 3, 1, "Ana"))
            },
        )
        attendance.rosterResult = SaqzResult.Success(roster)
        attendance.readResult = SaqzResult.Success(
            sampleAttendanceDetail().copy(
                ownAttendance = if (answered) AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1L) else null,
                availableSpots = if (full) 0 else 4,
                confirmedCount = if (full) 12 else 8,
            ),
        )
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf("me", "ana", "caio", "duda", "edu", "fabi").map { id -> entry(id) },
            ),
        )
        return GameDetailViewModel(
            "group-1",
            "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(game))),
            FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = role)))),
            attendance,
            athletes,
        )
    }

    private fun entry(id: String): AthleteRosterEntry =
        sampleRosterEntry(id).copy(displayName = id.replaceFirstChar { it.uppercase() }, membershipType = AthleteMembershipType.AVULSO)

    private fun GameDetailViewModel.removeAndConfirm(rowId: String) {
        onIntent(GameDetailIntent.RequestRemoveMember(rowId))
        onIntent(GameDetailIntent.ConfirmRemoveMember(DefaultReason))
    }

    @Test
    fun `tirar um confirmado manda DECLINE com o motivo padrao`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance)

        viewModel.removeAndConfirm("ana")

        val command = assertNotNullCommand(attendance)
        assertEquals("ana", command.memberId)
        assertEquals(AttendanceIntent.Decline, command.intent)
        assertEquals(DefaultReason, command.reason)
        assertTrue(command.requestId.isNotBlank())
    }

    @Test
    fun `tirar alguem da espera manda DECLINE com o motivo padrao`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance)

        viewModel.removeAndConfirm("caio")

        val command = assertNotNullCommand(attendance)
        assertEquals("caio", command.memberId)
        assertEquals(AttendanceIntent.Decline, command.intent)
        assertEquals(DefaultReason, command.reason)
    }

    @Test
    fun `a folha de tirar sabe se a pessoa esta confirmada ou na espera`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GameDetailIntent.RequestRemoveMember("ana"))
        assertEquals(true, viewModel.state.value.organizer.removal?.confirmed)
        assertEquals("Ana", viewModel.state.value.organizer.removal?.name)
        viewModel.onIntent(GameDetailIntent.DismissRemoveMember)
        assertNull(viewModel.state.value.organizer.removal)

        viewModel.onIntent(GameDetailIntent.RequestRemoveMember("caio"))
        assertEquals(false, viewModel.state.value.organizer.removal?.confirmed)
    }

    @Test
    fun `outro motivo preenchido vai no lugar do padrao`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.RequestRemoveMember("ana"))
        viewModel.onIntent(GameDetailIntent.ToggleRemoveReason)
        viewModel.onIntent(GameDetailIntent.UpdateRemoveReason("  Faltou muito  "))

        viewModel.onIntent(GameDetailIntent.ConfirmRemoveMember(DefaultReason))

        assertEquals("Faltou muito", assertNotNullCommand(attendance).reason)
    }

    @Test
    fun `motivo vazio de um caractere ou com o campo fechado cai no padrao`() = runTest {
        suspend fun reasonSentFor(prepare: GameDetailViewModel.() -> Unit): String {
            val attendance = FakeAttendanceGateway()
            val viewModel = viewModel(attendance)
            viewModel.onIntent(GameDetailIntent.RequestRemoveMember("ana"))
            viewModel.prepare()
            viewModel.onIntent(GameDetailIntent.ConfirmRemoveMember(DefaultReason))
            return assertNotNullCommand(attendance).reason
        }

        assertEquals(DefaultReason, reasonSentFor { onIntent(GameDetailIntent.ToggleRemoveReason) })
        assertEquals(
            DefaultReason,
            reasonSentFor {
                onIntent(GameDetailIntent.ToggleRemoveReason)
                onIntent(GameDetailIntent.UpdateRemoveReason("   "))
            },
        )
        assertEquals(
            DefaultReason,
            reasonSentFor {
                onIntent(GameDetailIntent.ToggleRemoveReason)
                onIntent(GameDetailIntent.UpdateRemoveReason(" a "))
            },
        )
        assertEquals(
            DefaultReason,
            reasonSentFor {
                onIntent(GameDetailIntent.ToggleRemoveReason)
                onIntent(GameDetailIntent.UpdateRemoveReason("texto digitado"))
                onIntent(GameDetailIntent.ToggleRemoveReason)
            },
        )
    }

    @Test
    fun `o motivo digitado e cortado em 500 caracteres`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(GameDetailIntent.RequestRemoveMember("ana"))
        viewModel.onIntent(GameDetailIntent.ToggleRemoveReason)

        viewModel.onIntent(GameDetailIntent.UpdateRemoveReason("x".repeat(800)))

        assertEquals(500, viewModel.state.value.organizer.removal?.reason?.length)
    }

    @Test
    fun `segundo toque com a primeira em voo e ignorado e roda de novo depois que termina`() = runTest {
        val attendance = FakeAttendanceGateway()
        val deferred = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        attendance.overrideDeferred = deferred
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.RequestRemoveMember("ana"))

        viewModel.onIntent(GameDetailIntent.ConfirmRemoveMember(DefaultReason))
        viewModel.onIntent(GameDetailIntent.ConfirmRemoveMember(DefaultReason))

        assertEquals(1, attendance.overrideCalls)
        assertTrue(viewModel.state.value.organizer.removing)

        deferred.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))
        assertFalse(viewModel.state.value.organizer.removing)
        assertNull(viewModel.state.value.organizer.removal)

        attendance.overrideDeferred = null
        viewModel.removeAndConfirm("caio")
        assertEquals(2, attendance.overrideCalls)
    }

    @Test
    fun `tirar com sucesso fecha a folha atualiza o placar e recarrega as listas`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance)
        val rostersBefore = attendance.rosterCalls

        viewModel.removeAndConfirm("ana")

        assertNull(viewModel.state.value.organizer.removal)
        assertEquals(9, viewModel.state.value.attendance?.confirmed)
        assertEquals(rostersBefore + 1, attendance.rosterCalls)
    }

    @Test
    fun `falha ao tirar mostra o erro libera a folha e repete o mesmo requestId`() = runTest {
        val attendance = FakeAttendanceGateway()
        attendance.overrideResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))
        val viewModel = viewModel(attendance)

        viewModel.removeAndConfirm("ana")
        val first = assertNotNullCommand(attendance)

        val organizer = viewModel.state.value.organizer
        assertEquals(GameOrganizerFailure.Generic, organizer.removeFailure)
        assertFalse(organizer.removing)
        assertEquals("ana", organizer.removal?.memberId)

        viewModel.onIntent(GameDetailIntent.ConfirmRemoveMember(DefaultReason))
        assertEquals(2, attendance.overrideCalls)
        assertEquals(first.requestId, assertNotNullCommand(attendance).requestId)

        // Outro motivo é outro pedido: ganha requestId novo.
        viewModel.onIntent(GameDetailIntent.ToggleRemoveReason)
        viewModel.onIntent(GameDetailIntent.UpdateRemoveReason("Outro assunto"))
        viewModel.onIntent(GameDetailIntent.ConfirmRemoveMember(DefaultReason))
        assertNotEquals(first.requestId, assertNotNullCommand(attendance).requestId)
    }

    @Test
    fun `conflito de versao fecha a folha e recarrega a tela`() = runTest {
        val attendance = FakeAttendanceGateway()
        attendance.overrideResult = SaqzResult.Failure(AttendanceError.Conflict)
        val viewModel = viewModel(attendance)
        val readsBefore = attendance.readCalls

        viewModel.removeAndConfirm("ana")

        assertNull(viewModel.state.value.organizer.removal)
        assertEquals(readsBefore + 1, attendance.readCalls)
    }

    @Test
    fun `jogo encerrado ou cancelado mostra o motivo na folha`() = runTest {
        val attendance = FakeAttendanceGateway()
        attendance.overrideResult = SaqzResult.Failure(AttendanceError.Frozen)
        val viewModel = viewModel(attendance)

        viewModel.removeAndConfirm("ana")

        assertEquals(GameOrganizerFailure.Closed, viewModel.state.value.organizer.removeFailure)
        assertFalse(viewModel.state.value.organizer.removing)
    }

    @Test
    fun `tirar nao aparece na propria linha no convidado nem para quem nao e gestor`() = runTest {
        val manager = viewModel(guestOnWaitlist = true)
        val state = manager.state.value
        assertEquals(mapOf("me" to false, "ana" to true), state.confirmedRoster.associate { it.id to it.canRemove })
        assertEquals(
            mapOf("caio" to true, "duda" to true, "ana#1" to false),
            state.waitlist.associate { it.id to it.canRemove },
        )
        manager.onIntent(GameDetailIntent.RequestRemoveMember("me"))
        manager.onIntent(GameDetailIntent.RequestRemoveMember("ana#1"))
        assertNull(manager.state.value.organizer.removal)

        val athlete = viewModel(role = GroupRole.ATHLETE)
        assertFalse(athlete.state.value.canManageAttendance)
        assertTrue(athlete.state.value.confirmedRoster.none { it.canRemove })
        assertTrue(athlete.state.value.waitlist.none { it.canRemove })
        athlete.onIntent(GameDetailIntent.RequestRemoveMember("ana"))
        athlete.onIntent(GameDetailIntent.OpenPlaceSheet)
        assertNull(athlete.state.value.organizer.removal)
        assertFalse(athlete.state.value.organizer.placeOpen)
    }

    @Test
    fun `gestor que ainda nao respondeu tambem nao se tira da lista`() = runTest {
        val viewModel = viewModel(answered = false)
        // Sem presença própria, quem é o gestor vem do perfil.
        assertTrue(viewModel.state.value.confirmedRoster.all { it.canRemove })
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        assertFalse("me" in viewModel.state.value.organizer.candidates.map { it.id })
    }

    @Test
    fun `depois do inicio do jogo tirar e colocar continuam valendo`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance, game = startedGame())
        assertTrue(viewModel.state.value.header?.gameStarted == true)
        assertTrue(viewModel.state.value.canManageAttendance)

        viewModel.removeAndConfirm("ana")
        assertEquals(1, attendance.overrideCalls)

        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("edu"))
        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))
        assertEquals(2, attendance.overrideCalls)
    }

    @Test
    fun `a lista de colocar exclui confirmados espera e o proprio gestor`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)

        assertTrue(viewModel.state.value.organizer.placeOpen)
        assertEquals(listOf("edu", "fabi"), viewModel.state.value.organizer.candidates.map { it.id })
    }

    @Test
    fun `convidado na espera nao tira o anfitriao da lista de colocar`() = runTest {
        val viewModel = viewModel(guestOnWaitlist = true)

        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)

        // "ana" já está confirmada: sai por ser a pessoa, não pelo convidado.
        assertEquals(listOf("edu", "fabi"), viewModel.state.value.organizer.candidates.map { it.id })
    }

    @Test
    fun `jogo cheio avisa que a pessoa entra na espera e jogo com vaga nao`() = runTest {
        val full = viewModel(full = true)
        assertTrue(full.state.value.placeGoesToWaitlist)
        assertFalse(full.state.value.placeBlocked)

        val open = viewModel()
        assertFalse(open.state.value.placeGoesToWaitlist)
    }

    @Test
    fun `colocar manda CONFIRM com o motivo padrao so depois de escolher alguem`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)

        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))
        assertEquals(0, attendance.overrideCalls)

        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("edu"))
        assertEquals("Edu", viewModel.state.value.organizer.selected?.name)
        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))

        val command = assertNotNullCommand(attendance)
        assertEquals("edu", command.memberId)
        assertEquals(AttendanceIntent.Confirm, command.intent)
        assertEquals(DefaultReason, command.reason)
        assertFalse(viewModel.state.value.organizer.placeOpen)
    }

    @Test
    fun `escolher quem nao esta na lista de colocar e ignorado`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)

        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("ana"))

        assertNull(viewModel.state.value.organizer.selectedId)
    }

    @Test
    fun `colocar com toque duplo manda uma vez so`() = runTest {
        val attendance = FakeAttendanceGateway()
        val deferred = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        attendance.overrideDeferred = deferred
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("edu"))

        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))
        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("fabi"))
        viewModel.onIntent(GameDetailIntent.DismissPlaceSheet)

        assertEquals(1, attendance.overrideCalls)
        assertEquals("edu", viewModel.state.value.organizer.selectedId)
        assertTrue(viewModel.state.value.organizer.placeOpen)

        deferred.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))
        assertFalse(viewModel.state.value.organizer.placing)
    }

    @Test
    fun `falha ao colocar libera a folha com a mesma escolha e o mesmo requestId`() = runTest {
        val attendance = FakeAttendanceGateway()
        attendance.overrideResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("edu"))

        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))
        val first = assertNotNullCommand(attendance)

        val organizer = viewModel.state.value.organizer
        assertEquals(GameOrganizerFailure.Generic, organizer.placeFailure)
        assertFalse(organizer.placing)
        assertEquals("edu", organizer.selectedId)

        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))
        assertEquals(first.requestId, assertNotNullCommand(attendance).requestId)
    }

    @Test
    fun `colocar com outro motivo preenchido manda o texto`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("edu"))
        viewModel.onIntent(GameDetailIntent.TogglePlaceReason)
        viewModel.onIntent(GameDetailIntent.UpdatePlaceReason("  Veio de última hora  "))

        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))

        assertEquals("Veio de última hora", assertNotNullCommand(attendance).reason)
        assertEquals(AttendanceIntent.Confirm, assertNotNullCommand(attendance).intent)
    }

    @Test
    fun `colocar com motivo vazio curto ou campo fechado cai no padrao`() = runTest {
        suspend fun reasonSentFor(prepare: GameDetailViewModel.() -> Unit): String {
            val attendance = FakeAttendanceGateway()
            val viewModel = viewModel(attendance)
            viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
            viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("edu"))
            viewModel.prepare()
            viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))
            return assertNotNullCommand(attendance).reason
        }

        assertEquals(DefaultReason, reasonSentFor { })
        assertEquals(DefaultReason, reasonSentFor { onIntent(GameDetailIntent.TogglePlaceReason) })
        assertEquals(
            DefaultReason,
            reasonSentFor {
                onIntent(GameDetailIntent.TogglePlaceReason)
                onIntent(GameDetailIntent.UpdatePlaceReason("   "))
            },
        )
        assertEquals(
            DefaultReason,
            reasonSentFor {
                onIntent(GameDetailIntent.TogglePlaceReason)
                onIntent(GameDetailIntent.UpdatePlaceReason(" a "))
            },
        )
        assertEquals(
            DefaultReason,
            reasonSentFor {
                onIntent(GameDetailIntent.TogglePlaceReason)
                onIntent(GameDetailIntent.UpdatePlaceReason("texto digitado"))
                onIntent(GameDetailIntent.TogglePlaceReason)
            },
        )
    }

    @Test
    fun `o motivo de colocar e cortado em 500 e some ao fechar a folha`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.TogglePlaceReason)
        viewModel.onIntent(GameDetailIntent.UpdatePlaceReason("x".repeat(800)))
        assertEquals(500, viewModel.state.value.organizer.placeReason.length)

        viewModel.onIntent(GameDetailIntent.DismissPlaceSheet)
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)

        assertFalse(viewModel.state.value.organizer.placeReasonOpen)
        assertEquals("", viewModel.state.value.organizer.placeReason)
    }

    @Test
    fun `jogo ja comecado e cheio nao deixa colocar ninguem`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(attendance, game = startedGame(), full = true)
        assertTrue(viewModel.state.value.placeBlocked)

        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("edu"))
        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember(DefaultReason))

        assertEquals(0, attendance.overrideCalls)
    }

    @Test
    fun `jogo ja comecado com vaga ainda deixa colocar`() = runTest {
        val viewModel = viewModel(game = startedGame())

        assertFalse(viewModel.state.value.placeBlocked)
    }

    private fun assertNotNullCommand(attendance: FakeAttendanceGateway) =
        requireNotNull(attendance.lastOverride) { "override não foi chamado" }
}
