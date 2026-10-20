package br.com.saqz.groups.presentation.monthlyinvite

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.membership.MonthlyInviteError
import br.com.saqz.groups.domain.membership.MonthlyInvitePersonError
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheck
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheckResult
import br.com.saqz.groups.domain.membership.MonthlyInvitePlanLimit
import br.com.saqz.groups.domain.membership.MonthlyInviteRejection
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupWhatsAppGateway
import br.com.saqz.groups.presentation.FakeMonthlyInviteGateway
import br.com.saqz.groups.presentation.sampleMonthlyInvites
import br.com.saqz.groups.presentation.sampleRosterEntry
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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyInviteViewModelTest {
    private val gateway = FakeMonthlyInviteGateway()
    private val athletes = FakeAthleteGateway()
    private val whatsApp = FakeGroupWhatsAppGateway()

    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun close() { Dispatchers.resetMain() }

    private fun viewModel() = MonthlyInviteViewModel("group-1", gateway, FakeGroupGateway(), athletes, whatsApp)

    private fun MonthlyInviteViewModel.addPerson(name: String, phone: String, keepOpen: Boolean = false) {
        onIntent(MonthlyInviteIntent.OpenSheet)
        onIntent(MonthlyInviteIntent.UpdateName(name))
        onIntent(MonthlyInviteIntent.UpdatePhone(phone))
        onIntent(MonthlyInviteIntent.Add(keepOpen))
    }

    @Test fun `loads the fee group name and sender and starts empty with send disabled`() {
        val vm = viewModel()
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Vôlei do CERET", state.groupName)
        assertEquals("Bruno", state.senderName)
        assertEquals(8_000, state.defaultFee?.monthlyFeeCents)
        assertTrue(state.people.isEmpty())
        assertFalse(state.canSend)
    }

    @Test fun `group without default fee blocks the screen and configure opens group settings`() = runTest {
        gateway.listResult = SaqzResult.Success(sampleMonthlyInvites(fee = null))
        val vm = viewModel()
        assertNull(vm.state.value.defaultFee)
        vm.onIntent(MonthlyInviteIntent.ConfigureFee)
        assertEquals(MonthlyInviteEffect.OpenFeeSettings("group-1"), vm.effects.first())
    }

    @Test fun `sheet keeps only 11 digits and validates name and phone`() {
        val vm = viewModel()
        vm.onIntent(MonthlyInviteIntent.OpenSheet)
        vm.onIntent(MonthlyInviteIntent.UpdatePhone("+55 (11) 98765-43210000"))
        assertEquals("55119876543", vm.state.value.sheet?.phone)
        vm.onIntent(MonthlyInviteIntent.UpdatePhone(""))
        vm.onIntent(MonthlyInviteIntent.UpdateName("A"))
        vm.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        assertTrue(vm.state.value.sheet!!.nameError)
        assertEquals(MonthlyInvitePhoneError.Empty, vm.state.value.sheet!!.phoneError)

        vm.onIntent(MonthlyInviteIntent.UpdatePhone("1198765"))
        vm.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        assertEquals(MonthlyInvitePhoneError.Incomplete, vm.state.value.sheet!!.phoneError)

        vm.onIntent(MonthlyInviteIntent.UpdatePhone("11887654321"))
        assertEquals(MonthlyInvitePhoneError.NotMobile, vm.state.value.sheet!!.phoneError)
        vm.onIntent(MonthlyInviteIntent.UpdatePhone("05987654321"))
        assertEquals(MonthlyInvitePhoneError.NotMobile, vm.state.value.sheet!!.phoneError)
        assertTrue(vm.state.value.people.isEmpty())
    }

    @Test fun `add closes the sheet and add more keeps it open and empty`() {
        val vm = viewModel()
        vm.addPerson("Ana Souza", "11988880001", keepOpen = true)
        assertEquals(MonthlyInviteSheet(), vm.state.value.sheet)
        vm.onIntent(MonthlyInviteIntent.UpdateName("Bia"))
        vm.onIntent(MonthlyInviteIntent.UpdatePhone("11988880002"))
        vm.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        assertNull(vm.state.value.sheet)
        assertEquals(listOf("Ana Souza", "Bia"), vm.state.value.people.map { it.name })
        assertTrue(vm.state.value.canSend)
    }

    @Test fun `repeated number in the list is refused with the name`() {
        val vm = viewModel()
        vm.addPerson("Renata Souza", "11991234567")
        vm.onIntent(MonthlyInviteIntent.OpenSheet)
        vm.onIntent(MonthlyInviteIntent.UpdatePhone("11991234567"))
        assertEquals(MonthlyInvitePhoneError.Duplicate, vm.state.value.sheet!!.phoneError)
        assertEquals("Renata Souza", vm.state.value.sheet!!.duplicateOf)
        vm.onIntent(MonthlyInviteIntent.UpdateName("Renata S."))
        vm.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        assertEquals(1, vm.state.value.people.size)
    }

    @Test fun `check on the 11th digit flags mensalista invited and avulso`() {
        gateway.checks = mapOf(
            "11988880001" to MonthlyInvitePhoneCheckResult(MonthlyInvitePhoneCheck.ALREADY_MENSALISTA, "Thiago Melo", "u1"),
            "11988880002" to MonthlyInvitePhoneCheckResult(MonthlyInvitePhoneCheck.ALREADY_AVULSO, "Camila Alves", "u2"),
            "11988880003" to MonthlyInvitePhoneCheckResult(MonthlyInvitePhoneCheck.ALREADY_INVITED, "Otto", null),
        )
        val vm = viewModel()
        vm.onIntent(MonthlyInviteIntent.OpenSheet)
        vm.onIntent(MonthlyInviteIntent.UpdateName("Thiago"))
        vm.onIntent(MonthlyInviteIntent.UpdatePhone("1198888000"))
        assertTrue(gateway.calls.isEmpty())
        vm.onIntent(MonthlyInviteIntent.UpdatePhone("11988880001"))
        assertEquals(MonthlyInviteMatch.Mensalista("Thiago Melo"), vm.state.value.sheet!!.match)
        vm.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        assertTrue(vm.state.value.people.isEmpty())

        vm.onIntent(MonthlyInviteIntent.UpdatePhone("11988880003"))
        assertEquals(MonthlyInvitePhoneError.AlreadyInvited, vm.state.value.sheet!!.phoneError)

        vm.onIntent(MonthlyInviteIntent.UpdatePhone("11988880002"))
        assertEquals(MonthlyInviteMatch.Avulso("Camila Alves", "u2"), vm.state.value.sheet!!.match)
    }

    @Test fun `promote uses the member patch with the group fee and sends no message`() {
        gateway.checks = mapOf("11988880002" to MonthlyInvitePhoneCheckResult(MonthlyInvitePhoneCheck.ALREADY_AVULSO, "Camila Alves", "u2"))
        athletes.rosterResult = SaqzResult.Success(listOf(sampleRosterEntry(userId = "u2")))
        val vm = viewModel()
        vm.onIntent(MonthlyInviteIntent.OpenSheet)
        vm.onIntent(MonthlyInviteIntent.UpdatePhone("11988880002"))
        vm.onIntent(MonthlyInviteIntent.Promote)

        val command = athletes.lastUpdateCommand!!
        assertEquals("u2", command.userId)
        assertEquals(AthleteMembershipType.MENSALISTA, command.membershipType)
        assertNull(command.monthlyFeeCents)
        assertNull(vm.state.value.sheet)
        assertEquals(MonthlyInviteToast.Promoted("Camila"), vm.state.value.toast)
        assertTrue(gateway.sends.isEmpty())
    }

    @Test fun `send carries one requestId and double tap sends once`() {
        val vm = viewModel()
        vm.addPerson("Ana", "11988880001")
        vm.addPerson("Bia", "11988880002")
        vm.addPerson("Caio", "11988880003")
        vm.onIntent(MonthlyInviteIntent.Send)
        vm.onIntent(MonthlyInviteIntent.Send)

        assertEquals(1, gateway.sends.size)
        assertEquals(listOf("11988880001", "11988880002", "11988880003"), gateway.sends.single().second.map { it.phone })
        assertTrue(vm.state.value.people.isEmpty())
        assertEquals(MonthlyInviteToast.Sent(3), vm.state.value.toast)
    }

    @Test fun `retry after a network failure reuses the requestId and a changed list gets a new one`() {
        gateway.sendResult = SaqzResult.Failure(MonthlyInviteError.DataFailure(br.com.saqz.domain.DataError.Connectivity))
        val vm = viewModel()
        vm.addPerson("Ana", "11988880001")
        vm.onIntent(MonthlyInviteIntent.Send)
        vm.onIntent(MonthlyInviteIntent.DismissActionFailed)
        vm.onIntent(MonthlyInviteIntent.Send)
        assertEquals(gateway.sends[0].first, gateway.sends[1].first)
        vm.addPerson("Bia", "11988880002")
        vm.onIntent(MonthlyInviteIntent.Send)
        assertNotEquals(gateway.sends[1].first, gateway.sends[2].first)
    }

    @Test fun `plan limit shows the notice and disables send until the list fits`() = runTest {
        gateway.sendResult = SaqzResult.Failure(MonthlyInviteError.PlanLimit(MonthlyInvitePlanLimit(5, 3, 3, 2)))
        val vm = viewModel()
        vm.addPerson("Ana", "11988880001")
        vm.addPerson("Bia", "11988880002")
        vm.addPerson("Caio", "11988880003")
        vm.onIntent(MonthlyInviteIntent.Send)
        assertEquals(1, vm.state.value.overLimit)
        assertFalse(vm.state.value.canSend)
        vm.onIntent(MonthlyInviteIntent.Remove(2))
        assertEquals(0, vm.state.value.overLimit)
        assertTrue(vm.state.value.canSend)
        vm.onIntent(MonthlyInviteIntent.OpenPlans)
        assertEquals(MonthlyInviteEffect.OpenPlans, vm.effects.first())
    }

    @Test fun `server rejections mark the rows and editing the list clears them`() {
        gateway.sendResult = SaqzResult.Failure(
            MonthlyInviteError.InvalidPeople(listOf(MonthlyInvitePersonError(1, MonthlyInviteRejection.ALREADY_AVULSO))),
        )
        val vm = viewModel()
        vm.addPerson("Ana", "11988880001")
        vm.addPerson("Bia", "11988880002")
        vm.onIntent(MonthlyInviteIntent.Send)
        assertEquals(mapOf(1 to MonthlyInviteRejection.ALREADY_AVULSO), vm.state.value.rowErrors)
        assertFalse(vm.state.value.canSend)
        vm.onIntent(MonthlyInviteIntent.Remove(1))
        assertTrue(vm.state.value.rowErrors.isEmpty())
    }

    @Test fun `fee removed meanwhile and whatsapp turned off switch the screen state`() {
        gateway.sendResult = SaqzResult.Failure(MonthlyInviteError.WhatsAppUnavailable)
        val vm = viewModel()
        vm.addPerson("Ana", "11988880001")
        vm.onIntent(MonthlyInviteIntent.Send)
        assertFalse(vm.state.value.whatsAppAvailable)
        gateway.sendResult = SaqzResult.Failure(MonthlyInviteError.MonthlyFeeRequired)
        val second = viewModel()
        second.addPerson("Ana", "11988880001")
        second.onIntent(MonthlyInviteIntent.Send)
        assertNull(second.state.value.defaultFee)
    }

    @Test fun `remove offers undo that restores the person in place`() {
        val vm = viewModel()
        vm.addPerson("Ana Souza", "11988880001")
        vm.addPerson("Bia", "11988880002")
        vm.onIntent(MonthlyInviteIntent.Remove(0))
        assertEquals(MonthlyInviteToast.Removed("Ana"), vm.state.value.toast)
        vm.onIntent(MonthlyInviteIntent.Undo)
        assertEquals(listOf("Ana Souza", "Bia"), vm.state.value.people.map { it.name })
    }

    @Test fun `whatsapp unavailable copies the message with a fresh link`() = runTest {
        whatsApp.available = false
        val vm = viewModel()
        assertFalse(vm.state.value.whatsAppAvailable)
        vm.onIntent(MonthlyInviteIntent.CopyMessage)
        val copy = assertIs<MonthlyInviteEffect.Copy>(vm.effects.first())
        assertTrue(copy.text.startsWith("E aí! 🏐 O Bruno te colocou na lista de mensalistas do Vôlei do CERET"))
        assertTrue(copy.text.contains("https://saqz.app/?saqz_invite=44DF8SA8\nSe não era pra você, é só ignorar."))
        vm.onIntent(MonthlyInviteIntent.CopyLink)
        assertEquals(MonthlyInviteEffect.Copy("https://saqz.app/?saqz_invite=44DF8SA8"), vm.effects.first())
        assertEquals(listOf("copyLink", "copyLink"), gateway.calls)
    }

    @Test fun `load failure shows retry`() {
        gateway.listResult = SaqzResult.Failure(MonthlyInviteError.DataFailure(br.com.saqz.domain.DataError.Server))
        val vm = viewModel()
        assertTrue(vm.state.value.loadFailed)
        gateway.listResult = SaqzResult.Success(sampleMonthlyInvites())
        vm.onIntent(MonthlyInviteIntent.Retry)
        assertFalse(vm.state.value.loadFailed)
    }

    @Test fun `message text follows the fixed template`() {
        assertEquals(
            "E aí, Ana! 🏐 O Lucas te colocou na lista de mensalistas do Vôlei da Quinta aqui no Saqz. Entra por aqui e " +
                "confirma presença nos jogos direto pelo app: https://l\nSe não era pra você, é só ignorar.",
            monthlyInviteMessage("Ana", "Lucas", "Vôlei da Quinta", "https://l"),
        )
    }
}
