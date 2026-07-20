package br.com.saqz.androidapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.saqz.androidapp.groups.draft.AndroidGroupDraftAdapters
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.presentation.GroupFinanceVisibility
import br.com.saqz.groups.presentation.GroupRouteAction
import br.com.saqz.groups.presentation.GroupRoutePolicy
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft
import br.com.saqz.groups.presentation.finance.charges.MonthlyDraftReadResult
import br.com.saqz.groups.presentation.finance.charges.MonthlyDraftWriteResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidGroupJourneyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearBefore() { AndroidGroupDraftAdapters.create(context).store.clearAll() }
    @After fun clearAfter() { AndroidGroupDraftAdapters.create(context).store.clearAll() }

    @Test fun completeOwnerSeesPeopleGamesOrganizerFinanceAndCanMutate() {
        val access = GroupRoutePolicy.evaluate(GroupRoleDto.OWNER, GroupProfileStatusDto.COMPLETE)
        assertTrue(access.peopleVisible); assertTrue(access.gamesVisible)
        assertEquals(GroupFinanceVisibility.ORGANIZER, access.financeVisibility)
        assertTrue(access.operationsMutable); assertFalse(access.profileCompletionVisible)
    }

    @Test fun completeAdminHasExactOrganizerSemanticOrder() {
        val access = GroupRoutePolicy.evaluate(GroupRoleDto.ADMIN, GroupProfileStatusDto.COMPLETE)
        assertEquals(
            listOf(GroupRouteAction.PEOPLE, GroupRouteAction.GAMES, GroupRouteAction.FINANCE),
            access.semanticActions,
        )
        assertTrue(access.operationsMutable)
    }

    @Test fun athleteSeesGamesAndOwnChargesWithoutPeopleOrMutations() {
        val access = GroupRoutePolicy.evaluate(GroupRoleDto.ATHLETE, GroupProfileStatusDto.COMPLETE)
        assertFalse(access.peopleVisible); assertTrue(access.gamesVisible)
        assertEquals(GroupFinanceVisibility.OWN_CHARGES, access.financeVisibility)
        assertFalse(access.operationsMutable); assertFalse(access.profileCompletionVisible)
    }

    @Test fun incompleteOwnerStartsWithCompletionThenReadableRoutesInOrder() {
        val access = GroupRoutePolicy.evaluate(GroupRoleDto.OWNER, GroupProfileStatusDto.INCOMPLETE)
        assertEquals(GroupRouteAction.COMPLETE_PROFILE, access.semanticActions.first())
        assertTrue(access.profileCompletionVisible); assertFalse(access.operationsMutable)
        assertTrue(access.gamesVisible)
    }

    @Test fun incompleteAdminCannotMutateGameAttendanceOrFinance() {
        val access = GroupRoutePolicy.evaluate(GroupRoleDto.ADMIN, GroupProfileStatusDto.INCOMPLETE)
        assertTrue(access.profileCompletionVisible)
        assertFalse(access.operationsMutable)
        assertEquals(GroupFinanceVisibility.ORGANIZER, access.financeVisibility)
    }

    @Test fun incompleteAthleteHasNoCompletionEditorAndOnlyOwnFinance() {
        val access = GroupRoutePolicy.evaluate(GroupRoleDto.ATHLETE, GroupProfileStatusDto.INCOMPLETE)
        assertFalse(access.profileCompletionVisible); assertFalse(access.operationsMutable)
        assertEquals(listOf(GroupRouteAction.GAMES, GroupRouteAction.FINANCE), access.semanticActions)
        assertEquals(GroupFinanceVisibility.OWN_CHARGES, access.financeVisibility)
    }

    @Test fun switchLogoutAndMembershipLossHidePriorGroupImmediately() {
        assertTrue(GroupRoutePolicy.canRenderPrivateData(GROUP, GROUP))
        assertFalse(GroupRoutePolicy.canRenderPrivateData(GROUP, "next-group"))
        assertFalse(GroupRoutePolicy.canRenderPrivateData(GROUP, null))
        assertFalse(GroupRoutePolicy.canRenderPrivateData(null, GROUP))
    }

    @Test fun processRecreationRestoresOneMonthlyCommandKeyAndMatchingSuccessClearsOnce() {
        val first = AndroidGroupDraftAdapters.create(context)
        var write: MonthlyDraftWriteResult? = null
        first.monthly.write(monthly) { write = it }
        assertSame(MonthlyDraftWriteResult.Success, write)
        val recreated = AndroidGroupDraftAdapters.create(context)
        var restored: MonthlyDraftReadResult? = null
        recreated.monthly.read(GROUP) { restored = it }
        assertEquals(KEY, (restored as MonthlyDraftReadResult.Success).draft?.commandKey)
        recreated.monthly.clear(GROUP, KEY) {}
        recreated.monthly.clear(GROUP, KEY) {}
        recreated.monthly.read(GROUP) { restored = it }
        assertEquals(null, (restored as MonthlyDraftReadResult.Success).draft)
    }

    private companion object {
        const val GROUP = "journey-group"
        const val KEY = "journey-command-key"
        val monthly = MonthlyChargeDraft(
            groupId = GROUP,
            commandKey = KEY,
            month = "2026-08",
            amountBrl = "70,00",
            dueDate = "2026-08-10",
            selectedMemberIds = setOf("athlete-1"),
            reviewed = true,
        )
    }
}
