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
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupMembershipGateway
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
        viewModel.onIntent(MemberEditorIntent.HeightChanged(""))
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
        assertEquals("", viewModel.state.value.heightText)
        assertNull(viewModel.state.value.heightCm)
        assertFalse(viewModel.state.value.billingSheetOpen)
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
        viewModel.onIntent(MemberEditorIntent.HeightChanged("188"))
        viewModel.onIntent(MemberEditorIntent.MembershipSelected(AthleteMembershipType.MENSALISTA))
        viewModel.onIntent(MemberEditorIntent.BillingAmountChanged("120,00"))
        viewModel.onIntent(MemberEditorIntent.BillingDueDaySelected(15))

        val restored = editor(athlete, savedState = savedState).state.value

        assertEquals("Raio", restored.nickname)
        assertEquals(AthletePosition.PONTA, restored.position)
        assertEquals(AthletePosition.CENTRAL, restored.secondaryPosition)
        assertEquals(AthleteLevel.AVANCADO, restored.level)
        assertEquals(AthletePreferredSide.ESQUERDA, restored.preferredSide)
        assertEquals("188", restored.heightText)
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

    private fun editor(
        athlete: FakeAthleteGateway,
        membership: FakeGroupMembershipGateway = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership(USER_ID, "Member", GroupRole.ATHLETE))),
        ),
        savedState: SavedStateHandle = SavedStateHandle(),
        viewerRole: GroupRole = GroupRole.OWNER,
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
    )

    companion object {
        private const val GROUP_ID = "group-1"
        private const val USER_ID = "member-1"
    }
}
