package br.com.saqz.groups.presentation.membereditor

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTrialAccessInfo
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupMembershipGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.GroupUiError
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MemberEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load resolves nickname and exposes attributes stats and group defaults`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(
                    sampleRosterEntry().copy(
                        displayName = "Bruno Almeida",
                        nickname = "Raio",
                        level = AthleteLevel.AVANCADO,
                        preferredSide = null,
                        heightCm = 190,
                        joinedAt = "2026-03-15T12:00:00Z",
                        monthlyFeeCents = null,
                        monthlyDueDay = null,
                    ),
                ),
            ),
            statsResult = SaqzResult.Success(AthleteStats(games = 18, attendanceRate = 92, absences = 2)),
        )
        val viewModel = MemberEditorViewModel(
            GROUP_ID,
            USER_ID,
            SavedStateHandle(),
            athlete,
            FakeGroupMembershipGateway(
                listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Bruno Almeida", GroupRole.ADMIN))),
            ),
            FakeGroupGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup().copy(
                        group = br.com.saqz.groups.presentation.sampleGroup().copy(
                            financeDefaults = GroupFinanceDefaults(1500, 8500, 10),
                        ),
                    ),
                ),
            ),
        )

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Raio", state.name)
        assertEquals("Bruno Almeida", state.displayName)
        assertEquals("março", state.joinedAtLabel)
        assertEquals(18, state.games)
        assertEquals(92, state.attendanceRate)
        assertEquals(2, state.absences)
        assertEquals(8500, state.effectiveMonthlyFeeCents)
        assertEquals(10, state.effectiveMonthlyDueDay)
        assertEquals(AthleteLevel.AVANCADO, state.level)
        assertEquals(190, state.heightCm)
        assertEquals(USER_ID, athlete.lastStatsUserId)
    }

    @Test
    fun `monthly billing saves override and switching back to single game preserves it`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.MENSALISTA))
        assertTrue(viewModel.state.value.billingSheetOpen)
        assertEquals("85,00", viewModel.state.value.billingAmountText)
        viewModel.onIntent(MemberEditorIntent.BillingAmountChanged("120,00"))
        viewModel.onIntent(MemberEditorIntent.BillingDueDaySelected(15))
        viewModel.onIntent(MemberEditorIntent.SaveBilling)

        assertEquals(AthleteMembershipType.MENSALISTA, athlete.lastUpdateCommand?.membershipType)
        assertEquals(12000, athlete.lastUpdateCommand?.monthlyFeeCents)
        assertEquals(15, athlete.lastUpdateCommand?.monthlyDueDay)
        assertFalse(viewModel.state.value.billingSheetOpen)

        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.AVULSO))
        viewModel.onIntent(MemberEditorIntent.Save)

        assertEquals(AthleteMembershipType.AVULSO, athlete.lastUpdateCommand?.membershipType)
        assertEquals(12000, athlete.lastUpdateCommand?.monthlyFeeCents)
        assertEquals(15, athlete.lastUpdateCommand?.monthlyDueDay)
    }

    @Test
    fun `monthly billing uses persisted profile and preserves the profile draft`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry().copy(nickname = "Salvo", heightCm = 180)),
            ),
        )
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.NicknameChanged("Rascunho"))
        viewModel.onIntent(MemberEditorIntent.HeightSelected(null))
        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.MENSALISTA))
        viewModel.onIntent(MemberEditorIntent.BillingAmountChanged("120,00"))
        viewModel.onIntent(MemberEditorIntent.BillingDueDaySelected(15))
        viewModel.onIntent(MemberEditorIntent.SaveBilling)

        assertEquals("Salvo", athlete.lastUpdateCommand?.nickname)
        assertEquals(180, athlete.lastUpdateCommand?.heightCm)
        assertEquals(AthleteMembershipType.MENSALISTA, athlete.lastUpdateCommand?.membershipType)
        assertEquals(12000, athlete.lastUpdateCommand?.monthlyFeeCents)
        assertEquals(15, athlete.lastUpdateCommand?.monthlyDueDay)
        assertEquals("Rascunho", viewModel.state.value.nickname)
        assertNull(viewModel.state.value.heightCm)
        assertFalse(viewModel.state.value.billingSheetOpen)
    }

    @Test
    fun `picking a height saves it in cm and a stored one outside the range errors on the field`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry().copy(heightCm = 80))))
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.Save)
        assertTrue(viewModel.state.value.heightError)
        assertNull(viewModel.state.value.error)
        assertNull(athlete.lastUpdateCommand)

        viewModel.onIntent(MemberEditorIntent.HeightSelected(175))
        assertFalse(viewModel.state.value.heightError)
        viewModel.onIntent(MemberEditorIntent.Save)
        assertEquals(175, athlete.lastUpdateCommand?.heightCm)
    }

    @Test
    fun `billing rejects extra decimal places without truncating money`() = runTest {
        val invalidAthlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val invalidViewModel = editor(invalidAthlete)
        invalidViewModel.onIntent(MemberEditorIntent.BillingAmountChanged("12,345"))
        invalidViewModel.onIntent(MemberEditorIntent.SaveBilling)

        assertEquals(GroupUiError.Validation, invalidViewModel.state.value.error)
        assertNull(invalidAthlete.lastUpdateCommand)

        listOf("12,3" to 1230L, "12,34" to 1234L, "12" to 1200L).forEach { (value, expectedCents) ->
            val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
            val viewModel = editor(athlete)
            viewModel.onIntent(MemberEditorIntent.BillingAmountChanged(value))
            viewModel.onIntent(MemberEditorIntent.SaveBilling)

            assertEquals(expectedCents, athlete.lastUpdateCommand?.monthlyFeeCents, value)
        }
    }

    @Test
    fun `draft fields restore from SavedStateHandle`() = runTest {
        val savedState = SavedStateHandle()
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = editor(athlete, savedState = savedState)

        viewModel.onIntent(MemberEditorIntent.NicknameChanged("Raio"))
        viewModel.onIntent(MemberEditorIntent.PositionSelected(AthletePosition.PONTA))
        viewModel.onIntent(MemberEditorIntent.SecondaryPositionSelected(AthletePosition.CENTRAL))
        viewModel.onIntent(MemberEditorIntent.LevelSelected(AthleteLevel.AVANCADO))
        viewModel.onIntent(MemberEditorIntent.PreferredSideSelected(AthletePreferredSide.ESQUERDA))
        viewModel.onIntent(MemberEditorIntent.HeightSelected(188))
        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.MENSALISTA))
        viewModel.onIntent(MemberEditorIntent.BillingAmountChanged("120,00"))
        viewModel.onIntent(MemberEditorIntent.BillingDueDaySelected(15))

        val restored = editor(athlete, savedState = savedState).state.value

        assertEquals("Raio", restored.nickname)
        assertEquals(AthletePosition.PONTA, restored.position)
        assertEquals(AthletePosition.CENTRAL, restored.secondaryPosition)
        assertEquals(AthleteLevel.AVANCADO, restored.level)
        assertEquals(AthletePreferredSide.ESQUERDA, restored.preferredSide)
        assertEquals(188, restored.heightCm)
        assertEquals(AthleteMembershipType.MENSALISTA, restored.membershipType)
        assertEquals("120,00", restored.billingAmountText)
        assertEquals(15, restored.billingDueDay)
    }

    @Test
    fun `selecting secondary position as primary clears duplicate before patch`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry().copy(position = AthletePosition.PONTA, secondaryPosition = AthletePosition.CENTRAL)),
            ),
        )
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.PositionSelected(AthletePosition.CENTRAL))
        assertNull(viewModel.state.value.secondaryPosition)

        viewModel.onIntent(MemberEditorIntent.Save)

        assertNull(athlete.lastUpdateCommand?.secondaryPosition)
    }

    @Test
    fun `owner cannot be changed by admin toggle`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Owner", GroupRole.OWNER))),
        )
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = editor(athlete, membership)

        assertTrue(viewModel.state.value.isOwner)
        viewModel.onIntent(MemberEditorIntent.AdminChanged(false))

        assertNull(membership.lastRoleCommand)
        assertNull(viewModel.state.value.operation)
    }

    @Test
    fun `admin viewer cannot change member role`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
        )
        val viewModel = editor(
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            membership,
            viewerRole = GroupRole.ADMIN,
        )

        assertFalse(viewModel.state.value.canManageRoles)
        viewModel.onIntent(MemberEditorIntent.AdminChanged(true))

        assertNull(membership.lastRoleCommand)
    }

    @Test
    fun `owner cannot be removed from the editor`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = editor(
            athlete,
            FakeGroupMembershipGateway(
                listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Owner", GroupRole.OWNER))),
            ),
        )

        viewModel.onIntent(MemberEditorIntent.OpenRemove)
        viewModel.onIntent(MemberEditorIntent.ConfirmRemove)

        assertFalse(viewModel.state.value.removeSheetOpen)
        assertEquals(null, athlete.lastRemovedUserId)
    }

    @Test
    fun `admin cannot remove another admin from the editor but the owner can`() = runTest {
        val adminMembership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Admin", GroupRole.ADMIN))),
        )
        val asAdmin = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val adminEditor = editor(asAdmin, adminMembership, viewerRole = GroupRole.ADMIN)
        assertFalse(adminEditor.state.value.canRemove)
        adminEditor.onIntent(MemberEditorIntent.OpenRemove)
        adminEditor.onIntent(MemberEditorIntent.ConfirmRemove)
        assertFalse(adminEditor.state.value.removeSheetOpen)
        assertEquals(null, asAdmin.lastRemovedUserId)

        val asOwner = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val ownerEditor = editor(asOwner, adminMembership, viewerRole = GroupRole.OWNER)
        assertTrue(ownerEditor.state.value.canRemove)
        ownerEditor.onIntent(MemberEditorIntent.OpenRemove)
        ownerEditor.onIntent(MemberEditorIntent.ConfirmRemove)
        assertEquals(USER_ID, asOwner.lastRemovedUserId)
    }

    @Test
    fun `admin still removes an athlete from the editor`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = editor(athlete, viewerRole = GroupRole.ADMIN)

        assertTrue(viewModel.state.value.canRemove)
        viewModel.onIntent(MemberEditorIntent.OpenRemove)
        viewModel.onIntent(MemberEditorIntent.ConfirmRemove)

        assertEquals(USER_ID, athlete.lastRemovedUserId)
    }

    @Test
    fun `server refusal to remove exposes access denied`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())),
            removeResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Forbidden)),
        )
        val viewModel = editor(athlete, viewerRole = GroupRole.OWNER)

        viewModel.onIntent(MemberEditorIntent.OpenRemove)
        viewModel.onIntent(MemberEditorIntent.ConfirmRemove)

        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
        assertNull(viewModel.state.value.operation)
    }

    @Test
    fun `role change sends assignable role and updates state`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
            changeRoleResult = SaqzResult.Success(GroupMembership(USER_ID, "Member", GroupRole.ADMIN)),
        )
        val viewModel = editor(FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))), membership)

        viewModel.onIntent(MemberEditorIntent.AdminChanged(true))

        assertEquals(AssignableGroupRole.ADMIN, membership.lastRoleCommand?.role)
        assertEquals(GroupRole.ADMIN, viewModel.state.value.role)
        assertNull(viewModel.state.value.operation)
    }

    @Test
    fun `removes monthly member and emits removed effect`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = editor(athlete)
        viewModel.onIntent(MemberEditorIntent.OpenRemove)
        viewModel.onIntent(MemberEditorIntent.ConfirmRemove)

        assertEquals(USER_ID, athlete.lastRemovedUserId)
        assertFalse(viewModel.state.value.removeSheetOpen)
        assertEquals(MemberEditorEffect.Removed, viewModel.effects.first())
    }

    @Test
    fun `removes single game member and emits removed effect`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry().copy(membershipType = AthleteMembershipType.AVULSO)),
            ),
        )
        val viewModel = editor(athlete)
        viewModel.onIntent(MemberEditorIntent.OpenRemove)
        viewModel.onIntent(MemberEditorIntent.ConfirmRemove)

        assertEquals(USER_ID, athlete.lastRemovedUserId)
        assertEquals(MemberEditorEffect.Removed, viewModel.effects.first())
    }

    @Test
    fun `typed load failures remain visible`() = runTest {
        val viewModel = editor(
            FakeAthleteGateway(
                rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Forbidden)),
            ),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `typed role failure clears operation and exposes error`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
            changeRoleResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Conflict)),
        )
        val viewModel = editor(FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))), membership)

        viewModel.onIntent(MemberEditorIntent.AdminChanged(true))

        assertEquals(GroupUiError.Conflict, viewModel.state.value.error)
        assertNull(viewModel.state.value.operation)
    }

    // ---- Backend v.0.0.17: promover durante o teste responde 403 SUBSCRIPTION_REQUIRED ----

    private val activeTrial = GroupTrialAccessPort {
        GroupTrialAccessInfo(endsAt = "2026-10-28T00:00:00Z", readOnly = false, isOwner = true)
    }

    @Test
    fun `promoting during the trial on the old backend opens the plan sheet and the toggle stays off`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
            changeRoleResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.SubscriptionRequired)),
        )
        val viewModel = editor(
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            membership,
            trialAccess = activeTrial,
        )

        viewModel.onIntent(MemberEditorIntent.AdminChanged(true))

        val state = viewModel.state.value
        assertEquals(AssignableGroupRole.ADMIN, membership.lastRoleCommand?.role)
        assertTrue(state.subscriptionRequired)
        assertFalse(state.trialEnded, "o teste ainda está ativo: o título é o do plano inativo")
        assertNull(state.error, "sem o cartão genérico, que ficava fora da vista com a tela rolada")
        assertNull(state.operation)
        assertEquals(GroupRole.ATHLETE, state.role, "o toggle volta desligado, agora com a folha explicando")

        viewModel.onIntent(MemberEditorIntent.DismissSubscriptionRequired)

        assertFalse(viewModel.state.value.subscriptionRequired)
        assertEquals(GroupRole.ATHLETE, viewModel.state.value.role)
    }

    @Test
    fun `the plan sheet tells that the trial is over when it is`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
            changeRoleResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.SubscriptionRequired)),
        )
        val viewModel = editor(
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            membership,
            trialAccess = GroupTrialAccessPort {
                GroupTrialAccessInfo(endsAt = "2026-10-01T00:00:00Z", readOnly = true, isOwner = true, trialEnded = true)
            },
        )

        viewModel.onIntent(MemberEditorIntent.AdminChanged(true))

        assertTrue(viewModel.state.value.subscriptionRequired)
        assertTrue(viewModel.state.value.trialEnded)
    }

    @Test
    fun `saving and removing without an active plan open the plan sheet too`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())),
            updateResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.SubscriptionRequired)),
            removeResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.SubscriptionRequired)),
        )
        val viewModel = editor(athlete, trialAccess = activeTrial)

        viewModel.onIntent(MemberEditorIntent.Save)
        assertTrue(viewModel.state.value.subscriptionRequired)
        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.operation)

        viewModel.onIntent(MemberEditorIntent.DismissSubscriptionRequired)
        viewModel.onIntent(MemberEditorIntent.OpenRemove)
        viewModel.onIntent(MemberEditorIntent.ConfirmRemove)
        assertTrue(viewModel.state.value.subscriptionRequired)
        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.operation)
    }

    @Test
    fun `a role failure that is not about the plan keeps the generic warning and no sheet`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
            changeRoleResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Forbidden)),
        )
        val viewModel = editor(
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            membership,
            trialAccess = activeTrial,
        )

        viewModel.onIntent(MemberEditorIntent.AdminChanged(true))

        assertFalse(viewModel.state.value.subscriptionRequired)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `typed save failure clears operation and exposes error in normal state`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())),
            updateResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Conflict)),
        )
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.Save)

        assertEquals(GroupUiError.Conflict, viewModel.state.value.error)
        assertNull(viewModel.state.value.operation)
        assertFalse(viewModel.state.value.loadFailed)
    }

    @Test
    fun `typed monthly failure clears operation and exposes error`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())),
            updateResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Conflict)),
        )
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.MENSALISTA))
        viewModel.onIntent(MemberEditorIntent.SaveBilling)

        assertEquals(GroupUiError.Conflict, viewModel.state.value.error)
        assertNull(viewModel.state.value.operation)
    }

    @Test
    fun `typed removal failure clears operation and exposes error`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())),
            removeResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Conflict)),
        )
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.OpenRemove)
        viewModel.onIntent(MemberEditorIntent.ConfirmRemove)

        assertEquals(GroupUiError.Conflict, viewModel.state.value.error)
        assertNull(viewModel.state.value.operation)
        assertTrue(viewModel.state.value.removeSheetOpen)
    }

    @Test
    fun `monthly member with a pending monthly charge switching to single game gets the notice`() = runTest {
        val viewModel = editor(
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            finance = FakeOrganizerFinanceGateway(SaqzResult.Success(ChargeList(listOf(monthlyCharge(ChargeStatus.Pending))))),
        )

        assertTrue(viewModel.state.value.hasPendingMonthly)
        assertFalse(viewModel.state.value.monthlyPendingNotice)

        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.AVULSO))

        assertTrue(viewModel.state.value.monthlyPendingNotice)

        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.MENSALISTA))
        assertFalse(viewModel.state.value.monthlyPendingNotice)
    }

    @Test
    fun `no pending monthly charge for this member means no notice on the switch`() = runTest {
        val viewModel = editor(
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            finance = FakeOrganizerFinanceGateway(
                SaqzResult.Success(
                    ChargeList(
                        listOf(
                            monthlyCharge(ChargeStatus.Paid),
                            monthlyCharge(ChargeStatus.Pending, memberId = "other-member"),
                            monthlyCharge(ChargeStatus.Pending, kind = ChargeKind.Game),
                        ),
                    ),
                ),
            ),
        )

        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.AVULSO))

        assertFalse(viewModel.state.value.hasPendingMonthly)
        assertFalse(viewModel.state.value.monthlyPendingNotice)
    }

    @Test
    fun `finance that does not load opens the editor without the notice`() = runTest {
        val viewModel = editor(
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            finance = FakeOrganizerFinanceGateway(SaqzResult.Failure(FinanceError.Forbidden)),
        )

        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.AVULSO))

        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.monthlyPendingNotice)
    }

    @Test
    fun `single game member is not charged a notice even with a pending monthly charge`() = runTest {
        val viewModel = editor(
            FakeAthleteGateway(
                rosterResult = SaqzResult.Success(
                    listOf(sampleRosterEntry().copy(membershipType = AthleteMembershipType.AVULSO)),
                ),
            ),
            finance = FakeOrganizerFinanceGateway(SaqzResult.Success(ChargeList(listOf(monthlyCharge(ChargeStatus.Pending))))),
        )

        assertFalse(viewModel.state.value.hasPendingMonthly)
        assertFalse(viewModel.state.value.monthlyPendingNotice)
    }

    private fun monthlyCharge(
        status: ChargeStatus,
        memberId: String = USER_ID,
        kind: ChargeKind = ChargeKind.Monthly,
    ) = Charge(
        id = "charge-$memberId-${status.name}-${kind.name}",
        groupId = GroupId(GROUP_ID),
        memberId = memberId,
        kind = kind,
        month = "2026-08",
        amountCents = 8_500L,
        dueDate = "2026-08-10",
        status = status,
        version = 1L,
        audit = emptyList(),
    )

    // DEB-115: chave "Isenção da mensalidade".
    private fun mensalista(exempt: Boolean = false) = FakeAthleteGateway(
        rosterResult = SaqzResult.Success(
            listOf(
                sampleRosterEntry().copy(
                    membershipType = AthleteMembershipType.MENSALISTA,
                    monthlyFeeCents = 9000,
                    monthlyDueDay = 5,
                    monthlyFeeExempt = exempt,
                ),
            ),
        ),
    )

    @Test
    fun `turning the exemption on asks first and confirming sends true`() = runTest {
        val athlete = mensalista()
        val viewModel = editor(athlete)
        assertTrue(viewModel.state.value.showsExemption)

        viewModel.onIntent(MemberEditorIntent.ExemptionChanged(true))
        assertTrue(viewModel.state.value.exemptionConfirmOpen)
        assertEquals(null, athlete.lastUpdateCommand)

        viewModel.onIntent(MemberEditorIntent.ConfirmExemption)
        val sent = athlete.lastUpdateCommand
        assertEquals(true, sent?.monthlyFeeExempt)
        assertEquals(AthleteMembershipType.MENSALISTA, sent?.membershipType)
        assertEquals(9000, sent?.monthlyFeeCents)
        assertEquals(5, sent?.monthlyDueDay)
        assertTrue(viewModel.state.value.monthlyFeeExempt)
        assertFalse(viewModel.state.value.exemptionConfirmOpen)
        assertEquals(null, viewModel.state.value.operation)
    }

    @Test
    fun `backing out of the exemption confirmation sends nothing`() = runTest {
        val athlete = mensalista()
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.ExemptionChanged(true))
        viewModel.onIntent(MemberEditorIntent.DismissExemption)

        assertEquals(null, athlete.lastUpdateCommand)
        assertFalse(viewModel.state.value.monthlyFeeExempt)
        assertFalse(viewModel.state.value.exemptionConfirmOpen)
    }

    @Test
    fun `turning the exemption off saves false right away`() = runTest {
        val athlete = mensalista(exempt = true)
        val viewModel = editor(athlete)
        assertTrue(viewModel.state.value.monthlyFeeExempt)

        viewModel.onIntent(MemberEditorIntent.ExemptionChanged(false))

        assertFalse(viewModel.state.value.exemptionConfirmOpen)
        assertEquals(false, athlete.lastUpdateCommand?.monthlyFeeExempt)
        assertFalse(viewModel.state.value.monthlyFeeExempt)
    }

    @Test
    fun `saving the form keeps the exemption and single game members have no switch`() = runTest {
        val exempt = mensalista(exempt = true)
        editor(exempt).onIntent(MemberEditorIntent.Save)
        assertEquals(true, exempt.lastUpdateCommand?.monthlyFeeExempt)

        val single = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry().copy(membershipType = AthleteMembershipType.AVULSO))),
        )
        val viewModel = editor(single)
        assertFalse(viewModel.state.value.showsExemption)
        viewModel.onIntent(MemberEditorIntent.ExemptionChanged(true))
        assertFalse(viewModel.state.value.exemptionConfirmOpen)
        assertEquals(null, single.lastUpdateCommand)
    }

    @Test
    fun `a failed exemption keeps it off and shows the warning`() = runTest {
        val athlete = mensalista()
        athlete.updateResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Conflict))
        val viewModel = editor(athlete)

        viewModel.onIntent(MemberEditorIntent.ExemptionChanged(true))
        viewModel.onIntent(MemberEditorIntent.ConfirmExemption)

        assertFalse(viewModel.state.value.monthlyFeeExempt)
        assertEquals(null, viewModel.state.value.operation)
        assertTrue(viewModel.state.value.error != null)
    }

    private fun editor(
        athlete: FakeAthleteGateway,
        membership: FakeGroupMembershipGateway = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
        ),
        savedState: SavedStateHandle = SavedStateHandle(),
        viewerRole: GroupRole = GroupRole.OWNER,
        trialAccess: GroupTrialAccessPort? = null,
        finance: FakeOrganizerFinanceGateway? = null,
    ) = MemberEditorViewModel(
        GROUP_ID,
        USER_ID,
        savedState,
        athlete,
        membership,
        FakeGroupGateway(
            readResult = SaqzResult.Success(
                br.com.saqz.groups.presentation.sampleVersionedGroup().copy(
                    group = br.com.saqz.groups.presentation.sampleGroup(role = viewerRole).copy(
                        financeDefaults = GroupFinanceDefaults(1500, 8500, 10),
                    ),
                ),
            ),
        ),
        trialAccess,
        finance,
    )

    companion object {
        private const val GROUP_ID = "group-1"
        private const val USER_ID = "member-1"
    }
}
