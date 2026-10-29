package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.WaitlistCharge
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.VersionedCharge
import br.com.saqz.groups.domain.group.GroupGameConfig
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.FakeAthleteFinanceGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleRosterEntry
import br.com.saqz.groups.presentation.sampleVersionedGame
import br.com.saqz.groups.presentation.sampleVersionedGroup
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

/** DEB-150 · PROM-010: a tela do jogo com o pagamento antecipado ligado (avulso na espera e espera do gestor). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailAdvancePaymentTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val advanceGame = sampleGame().copy(
        confirmationDeadline = "2030-01-01T12:00:00-03:00",
        startsAt = "2030-01-01T20:00:00-03:00",
        gameFeeCents = 2000,
        noGameFee = false,
        advancePaymentRequired = true,
    )

    private fun pending(id: String, version: Long = 3) = WaitlistCharge(id, ChargeStatus.Pending, 2000, version)

    private fun organizer(
        attendance: FakeAttendanceGateway = FakeAttendanceGateway(),
        finance: FakeOrganizerFinanceGateway = FakeOrganizerFinanceGateway(),
        game: br.com.saqz.groups.domain.game.Game = advanceGame,
    ): GameDetailViewModel {
        attendance.rosterResult = SaqzResult.Success(
            AttendanceRoster(
                confirmed = listOf(AttendanceRosterMember("me", "Bruno")),
                waitlisted = listOf(
                    AttendanceRosterMember("caio", "Caio", 1, charge = WaitlistCharge("c-caio", ChargeStatus.Paid, 2000, 4)),
                    AttendanceRosterMember("rafa", "Rafa", 2, charge = pending("c-rafa")),
                    AttendanceRosterMember("bia", "Júlia", 3, 1, "Bia", charge = pending("c-julia")),
                    AttendanceRosterMember("duda", "Duda", 4),
                ),
            ),
        )
        attendance.readResult = SaqzResult.Success(
            sampleAttendanceDetail().copy(ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1L)),
        )
        return GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(game))),
            FakeGroupGateway(
                SaqzResult.Success(
                    sampleVersionedGroup(
                        sampleGroup(role = GroupRole.ADMIN).copy(gameConfig = GroupGameConfig(promotionMode = PromotionMode.MANUAL)),
                    ),
                ),
            ),
            attendance,
            FakeAthleteGateway(
                rosterResult = SaqzResult.Success(listOf("me", "caio", "rafa", "bia", "duda").map(::sampleRosterEntry)),
            ),
            organizerFinance = finance,
        )
    }

    @Test
    fun `the organizer waitlist shows paid, missing and the host of a guest`() = runTest {
        val waitlist = organizer().state.value.waitlist

        assertEquals(WaitlistPaymentUi("R$ 20", paid = true, chargeId = "c-caio", version = 4), waitlist[0].payment)
        assertEquals(false, waitlist[1].payment?.paid)
        assertEquals("Bia", waitlist[2].payment?.hostName)
        assertNull(waitlist[3].payment, "quem já estava na espera sem diária coloca como hoje")
    }

    @Test
    fun `without advance payment the organizer sees no payment line`() = runTest {
        val waitlist = organizer(game = advanceGame.copy(advancePaymentRequired = false)).state.value.waitlist
        assertTrue(waitlist.all { it.payment == null })
    }

    @Test
    fun `Recebi uses the status route with the version of the charge and marks the row as paid`() = runTest {
        val finance = FakeOrganizerFinanceGateway().apply {
            updateStatusResult = SaqzResult.Success(VersionedCharge(charge("c-rafa", ChargeStatus.Paid), FinanceVersionToken("\"4\"")))
        }
        val vm = organizer(finance = finance)

        vm.onIntent(GameDetailIntent.ReceiveWaitlistPayment("rafa"))
        vm.onIntent(GameDetailIntent.ReceiveWaitlistPayment("rafa"))

        val call = finance.statusCalls.single()
        assertEquals("c-rafa", call.first)
        assertEquals(FinanceVersionToken("\"3\""), call.second)
        assertEquals(ChargeStatus.Paid, call.third.status)
        assertNull(vm.state.value.receivingPaymentId)
    }

    @Test
    fun `a version conflict on Recebi reloads the waitlist without the error`() = runTest {
        val attendance = FakeAttendanceGateway()
        val finance = FakeOrganizerFinanceGateway().apply { updateStatusResult = SaqzResult.Failure(FinanceError.Conflict) }
        val vm = organizer(attendance = attendance, finance = finance)
        val before = attendance.rosterCalls

        vm.onIntent(GameDetailIntent.ReceiveWaitlistPayment("rafa"))

        assertTrue(attendance.rosterCalls > before, "409 recarrega a espera")
        assertFalse(vm.state.value.receivePaymentFailed)
    }

    @Test
    fun `placing someone who did not pay opens the sheet and both exits send the right markPaid`() = runTest {
        val attendance = FakeAttendanceGateway()
        val vm = organizer(attendance = attendance)

        vm.onIntent(GameDetailIntent.OpenAdvancePlace("rafa"))
        assertEquals("rafa", vm.state.value.advancePlaceRowId)
        vm.onIntent(GameDetailIntent.ConfirmAdvancePlace(markPaid = true, reason = "Escolha do organizador"))
        assertNull(vm.state.value.advancePlaceRowId)
        assertEquals(true, attendance.lastPromotionCommand?.markPaid)
        assertEquals(PaidMethod.Pix, attendance.lastPromotionCommand?.paidMethod)
        assertEquals("rafa", attendance.lastPromotionCommand?.memberId)

        val later = FakeAttendanceGateway()
        val other = organizer(attendance = later)
        other.onIntent(GameDetailIntent.OpenAdvancePlace("bia#1"))
        other.onIntent(GameDetailIntent.ConfirmAdvancePlace(markPaid = false, reason = "Escolha do organizador"))
        assertEquals(false, later.lastPromotionCommand?.markPaid)
        assertEquals("bia", later.lastPromotionCommand?.memberId)
        assertEquals(1, later.lastPromotionCommand?.guestSeq)
    }

    @Test
    fun `the sheet does not open for someone who already paid`() = runTest {
        val vm = organizer()
        vm.onIntent(GameDetailIntent.OpenAdvancePlace("caio"))
        assertNull(vm.state.value.advancePlaceRowId)
    }

    // ---- avulso na espera ----

    private fun avulso(
        charges: List<Charge> = emptyList(),
        status: AttendanceStatus = AttendanceStatus.Waitlisted,
        type: AthleteMembershipType = AthleteMembershipType.AVULSO,
        receivables: Boolean = false,
    ): GameDetailViewModel {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(ownAttendance = AttendanceEntry("me", status, 1L, 1L)),
            ),
        )
        val group = sampleGroup(role = GroupRole.ATHLETE).let {
            it.copy(
                profile = it.profile?.copy(pixKey = "pix@grupo.com"),
                gameConfig = GroupGameConfig(promotionMode = PromotionMode.MANUAL),
                receivablesActive = receivables,
            )
        }
        return GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(advanceGame))),
            FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(group))),
            attendance,
            FakeAthleteGateway(
                ownProfileResult = SaqzResult.Success(
                    OwnAthleteProfile(
                        "me", "Rafa", null,
                        listOf(OwnAthleteMembership(GroupId("group-1"), "Vôlei do CERET", GroupRole.ATHLETE, null, type, true)),
                    ),
                ),
            ),
            athleteFinance = FakeAthleteFinanceGateway(SaqzResult.Success(ChargeList(charges))),
        )
    }

    @Test
    fun `the avulso on the waitlist sees the pending box with the group Pix`() = runTest {
        val box = assertNotNull(avulso(listOf(charge("c-1", ChargeStatus.Pending))).state.value.advancePayment)
        assertEquals(AdvancePaymentUi("R$ 20", paid = false, pixKey = "pix@grupo.com"), box)
    }

    @Test
    fun `with receivables active the box offers the existing Pagar`() = runTest {
        assertEquals(true, avulso(receivables = true).state.value.advancePayment?.payInApp)
    }

    @Test
    fun `after Recebi the avulso sees the payment as received`() = runTest {
        assertEquals(true, avulso(listOf(charge("c-1", ChargeStatus.Paid))).state.value.advancePayment?.paid)
    }

    @Test
    fun `mensalistas and confirmed people see no box`() = runTest {
        assertNull(avulso(type = AthleteMembershipType.MENSALISTA).state.value.advancePayment)
        assertNull(avulso(status = AttendanceStatus.Confirmed).state.value.advancePayment)
    }

    private fun charge(id: String, status: ChargeStatus) = Charge(
        id = id, groupId = GroupId("group-1"), memberId = "me", kind = ChargeKind.Game, gameId = "game-1",
        amountCents = 2000, dueDate = "2030-01-01", status = status, version = 1, audit = emptyList(),
    )
}
