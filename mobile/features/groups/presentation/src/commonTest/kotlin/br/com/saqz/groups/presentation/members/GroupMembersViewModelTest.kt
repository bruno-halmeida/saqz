package br.com.saqz.groups.presentation.members

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupMembershipGateway
import br.com.saqz.groups.presentation.FakeGroupEntryRequestGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeModerationGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.blockedPerson
import br.com.saqz.groups.presentation.fakeBlocks
import br.com.saqz.groups.presentation.moderation.BlockedPeopleRepository
import br.com.saqz.groups.presentation.sampleRosterEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembersViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads roster including unknown financial status`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry("admin"), sampleRosterEntry("athlete")),
            ),
        )
        val memberships = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(
                listOf(GroupMembership("admin", "Admin", GroupRole.ADMIN)),
            ),
        )
        val viewModel = membersViewModel("group-1", athlete, memberships, FakeGroupGateway())

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(2, viewModel.state.value.totalCount)
        assertEquals(listOf("admin"), viewModel.state.value.admins.map { it.id })
        assertEquals(listOf("athlete"), viewModel.state.value.members.map { it.id })
        assertEquals("Central · Mensalista", viewModel.state.value.members.single().meta)
        assertTrue(GroupMembersFilter.values().toList().none { it.name.contains("Financial", ignoreCase = true) })
    }

    @Test
    fun `exempt monthly member shows com isencao to the organizer`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("athlete").copy(monthlyFeeExempt = true))),
        )
        val memberships = FakeGroupMembershipGateway(listResult = SaqzResult.Success(emptyList()))
        val viewModel = membersViewModel("group-1", athlete, memberships, FakeGroupGateway())

        assertEquals("Central · Mensalista · com isenção", viewModel.state.value.members.single().meta)
    }

    @Test
    fun `type filter splits mensalistas and avulsos and counts both`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(
                sampleRosterEntry("bia"),
                sampleRosterEntry("diego").copy(membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO),
                sampleRosterEntry("renata").copy(monthlyFeeExempt = true),
            )),
        )
        val memberships = FakeGroupMembershipGateway(listResult = SaqzResult.Success(emptyList()))
        val viewModel = membersViewModel("group-1", athlete, memberships, FakeGroupGateway())

        assertEquals(2, viewModel.state.value.monthlyCount)
        assertEquals(1, viewModel.state.value.perGameCount)
        viewModel.onIntent(GroupMembersIntent.SelectTypeFilter(MemberTypeFilter.Monthly))
        assertEquals(setOf("bia", "renata"), viewModel.state.value.members.map { it.id }.toSet())
        assertTrue(viewModel.state.value.members.first { it.id == "renata" }.exempt)
        viewModel.onIntent(GroupMembersIntent.SelectTypeFilter(MemberTypeFilter.PerGame))
        assertEquals(listOf("diego"), viewModel.state.value.members.map { it.id })
    }

    @Test
    fun `signed in member is marked self and its sheet only edits the profile`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                br.com.saqz.groups.domain.athlete.OwnAthleteProfile("me", "Me", null, emptyList()),
            ),
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("me"))),
        )
        val viewModel = membersViewModel(
            "group-1",
            athlete,
            FakeGroupMembershipGateway(
                listResult = SaqzResult.Success(listOf(GroupMembership("me", "Me", GroupRole.ADMIN))),
            ),
            FakeGroupGateway(),
        )

        val self = viewModel.state.value.admins.single()
        assertTrue(self.isSelf)

        assertEquals(listOf(GroupMemberAction.ViewProfile, GroupMemberAction.EditMember), self.sheetActions())
        viewModel.onIntent(GroupMembersIntent.OpenMember(self.id))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        assertEquals(self.id, viewModel.state.value.selected?.id)
        assertEquals(null, athlete.lastRemovedUserId)
    }

    @Test
    fun `dono e admin editam jogador de dono e de admin mas so o dono remove admin`() {
        val base = MemberUi("x", "X", "", isAdmin = true, isSelf = false, stats = "")
        val ownerSelf = base.copy(isOwner = true, isSelf = true)
        val ownerTarget = base.copy(isOwner = true, canManageRoles = false)
        val adminTarget = base.copy(canManageRoles = false)
        val adminTargetByOwner = base.copy()
        assertEquals(
            listOf(GroupMemberAction.ViewProfile, GroupMemberAction.EditMember),
            ownerSelf.sheetActions(),
        )
        assertEquals(
            listOf(
                GroupMemberAction.ViewProfile,
                GroupMemberAction.EditMember,
                GroupMemberAction.Report,
                GroupMemberAction.Block,
            ),
            ownerTarget.sheetActions(),
        )
        assertEquals(
            listOf(
                GroupMemberAction.ViewProfile,
                GroupMemberAction.EditMember,
                GroupMemberAction.Report,
                GroupMemberAction.Block,
            ),
            adminTarget.sheetActions(),
        )
        assertEquals(
            listOf(
                GroupMemberAction.ViewProfile,
                GroupMemberAction.EditMember,
                GroupMemberAction.Demote,
                GroupMemberAction.Remove,
                GroupMemberAction.Report,
                GroupMemberAction.Block,
            ),
            adminTargetByOwner.sheetActions(),
        )
    }

    @Test
    fun `empty roster renders without sections`() = runTest {
        val viewModel = membersViewModel(
            "group-1",
            FakeAthleteGateway(),
            FakeGroupMembershipGateway(),
            FakeGroupGateway(),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(0, viewModel.state.value.totalCount)
        assertTrue(viewModel.state.value.admins.isEmpty())
        assertTrue(viewModel.state.value.members.isEmpty())
    }

    @Test
    fun `roster failure is visible and typed`() = runTest {
        val viewModel = membersViewModel(
            "group-1",
            FakeAthleteGateway(
                rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Forbidden)),
            ),
            FakeGroupMembershipGateway(),
            FakeGroupGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `forbidden membership lookup does not hide a readable roster`() = runTest {
        val viewModel = membersViewModel(
            "group-1",
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            FakeGroupMembershipGateway(
                listResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Forbidden)),
            ),
            FakeGroupGateway(),
        )

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(listOf("member-1"), viewModel.state.value.members.map { it.id })
    }

    @Test
    fun `athlete sem lookup de memberships nao recebe acoes de gestao`() = runTest {
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Forbidden)),
        )
        val viewModel = membersViewModel(
            "group-1",
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            membership,
            groupAs(GroupRole.ATHLETE),
        )

        val member = viewModel.state.value.members.single()
        assertEquals(
            listOf(GroupMemberAction.ViewProfile, GroupMemberAction.Report, GroupMemberAction.Block),
            member.sheetActions(),
        )
        viewModel.onIntent(GroupMembersIntent.OpenMember(member.id))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Promote))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        assertEquals(null, membership.lastRoleCommand)
    }

    @Test
    fun `admin ve o dono na secao de admins mesmo sem lookup de memberships`() = runTest {
        val viewModel = membersViewModel(
            "group-1",
            FakeAthleteGateway(
                ownProfileResult = SaqzResult.Success(
                    br.com.saqz.groups.domain.athlete.OwnAthleteProfile("carla", "Carla", null, emptyList()),
                ),
                rosterResult = SaqzResult.Success(
                    listOf(
                        sampleRosterEntry("owner", role = GroupRole.OWNER),
                        sampleRosterEntry("carla", role = GroupRole.ADMIN),
                        sampleRosterEntry("athlete"),
                    ),
                ),
            ),
            FakeGroupMembershipGateway(
                listResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Forbidden)),
            ),
            groupAs(GroupRole.ADMIN),
        )

        assertEquals(listOf("owner", "carla"), viewModel.state.value.admins.map { it.id })
        assertEquals(listOf("athlete"), viewModel.state.value.members.map { it.id })
        assertTrue(viewModel.state.value.admins.single { it.id == "owner" }.isOwner)
    }

    @Test
    fun `admin remove atleta e nao promove`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val membership = FakeGroupMembershipGateway()
        val viewModel = membersViewModel("group-1", athlete, membership, groupAs(GroupRole.ADMIN))

        val member = viewModel.state.value.members.single()
        assertEquals(
            listOf(
                GroupMemberAction.ViewProfile,
                GroupMemberAction.EditMember,
                GroupMemberAction.Remove,
                GroupMemberAction.Report,
                GroupMemberAction.Block,
            ),
            member.sheetActions(),
        )
        viewModel.onIntent(GroupMembersIntent.OpenMember(member.id))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Promote))
        assertEquals(null, membership.lastRoleCommand)

        viewModel.onIntent(GroupMembersIntent.OpenMember(member.id))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))
        viewModel.onIntent(GroupMembersIntent.ConfirmRemove)
        assertEquals("member-1", athlete.lastRemovedUserId)
    }

    @Test
    fun ownerCanOpenProfileAndEditorOfSelectedAthlete() = runTest {
        assertProfileAndEditorNavigation(
            GroupRole.OWNER,
            listOf(
                GroupMemberAction.ViewProfile,
                GroupMemberAction.EditMember,
                GroupMemberAction.Promote,
                GroupMemberAction.Remove,
                GroupMemberAction.Report,
                GroupMemberAction.Block,
            ),
        )
    }

    @Test
    fun adminCanOpenProfileAndEditorOfSelectedAthlete() = runTest {
        assertProfileAndEditorNavigation(
            GroupRole.ADMIN,
            listOf(
                GroupMemberAction.ViewProfile,
                GroupMemberAction.EditMember,
                GroupMemberAction.Remove,
                GroupMemberAction.Report,
                GroupMemberAction.Block,
            ),
        )
    }

    @Test
    fun `admin nao rebaixa nem remove o dono`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                br.com.saqz.groups.domain.athlete.OwnAthleteProfile("carla", "Carla", null, emptyList()),
            ),
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("owner", role = GroupRole.OWNER))),
        )
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership("owner", "Owner", GroupRole.OWNER))),
        )
        val viewModel = membersViewModel("group-1", athlete, membership, groupAs(GroupRole.ADMIN))

        val owner = viewModel.state.value.admins.single()
        assertEquals(
            listOf(
                GroupMemberAction.ViewProfile,
                GroupMemberAction.EditMember,
                GroupMemberAction.Report,
                GroupMemberAction.Block,
            ),
            owner.sheetActions(),
        )
        viewModel.onIntent(GroupMembersIntent.OpenMember(owner.id))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Demote))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        assertEquals(null, membership.lastRoleCommand)
        assertEquals(null, athlete.lastRemovedUserId)
    }

    @Test
    fun `admin nao remove outro admin e o dono remove`() = runTest {
        val roster = listOf(sampleRosterEntry("admin-2", role = GroupRole.ADMIN))
        val membership = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership("admin-2", "Admin", GroupRole.ADMIN))),
        )

        val asAdmin = FakeAthleteGateway(rosterResult = SaqzResult.Success(roster))
        val adminViewModel = membersViewModel("group-1", asAdmin, membership, groupAs(GroupRole.ADMIN))
        val otherAdmin = adminViewModel.state.value.admins.single()
        assertFalse(GroupMemberAction.Remove in otherAdmin.sheetActions())
        adminViewModel.onIntent(GroupMembersIntent.OpenMember(otherAdmin.id))
        adminViewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))
        assertNull(adminViewModel.state.value.confirmRemove)
        assertEquals(null, asAdmin.lastRemovedUserId)

        val asOwner = FakeAthleteGateway(rosterResult = SaqzResult.Success(roster))
        val ownerViewModel = membersViewModel("group-1", asOwner, membership, groupAs(GroupRole.OWNER))
        val admin = ownerViewModel.state.value.admins.single()
        assertTrue(GroupMemberAction.Remove in admin.sheetActions())
        ownerViewModel.onIntent(GroupMembersIntent.OpenMember(admin.id))
        ownerViewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))
        ownerViewModel.onIntent(GroupMembersIntent.ConfirmRemove)
        assertEquals("admin-2", asOwner.lastRemovedUserId)
    }

    @Test
    fun `servidor que nega a remocao mostra o aviso curto e nao o generico`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())),
            removeResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Forbidden)),
        )
        val viewModel = membersViewModel("group-1", athlete, FakeGroupMembershipGateway(), groupAs(GroupRole.ADMIN))
        val member = viewModel.state.value.members.single()

        viewModel.onIntent(GroupMembersIntent.OpenMember(member.id))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))
        viewModel.onIntent(GroupMembersIntent.ConfirmRemove)

        assertTrue(viewModel.state.value.actionDenied)
        assertFalse(viewModel.state.value.actionFailed)
        viewModel.onIntent(GroupMembersIntent.DismissActionFailed)
        assertFalse(viewModel.state.value.actionDenied)
    }

    @Test
    fun `promote calls membership gateway and closes the sheet`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val membership = FakeGroupMembershipGateway()
        val viewModel = membersViewModel(
            "group-1",
            athlete,
            membership,
            FakeGroupGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup(
                        br.com.saqz.groups.presentation.sampleGroup(role = GroupRole.OWNER),
                    ),
                ),
            ),
        )

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Promote))

        assertEquals(AssignableGroupRole.ADMIN, membership.lastRoleCommand?.role)
        assertEquals("member-1", membership.lastRoleCommand?.userId)
        assertEquals(null, viewModel.state.value.selected)
    }

    @Test
    fun `remove calls athlete gateway`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = membersViewModel(
            "group-1",
            athlete,
            FakeGroupMembershipGateway(),
            FakeGroupGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup(
                        br.com.saqz.groups.presentation.sampleGroup(role = GroupRole.OWNER),
                    ),
                ),
            ),
        )

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        // Remover pede confirmação: nada some no primeiro toque.
        assertEquals(null, athlete.lastRemovedUserId)
        assertEquals("member-1", viewModel.state.value.confirmRemove?.id)

        viewModel.onIntent(GroupMembersIntent.ConfirmRemove)

        assertEquals("member-1", athlete.lastRemovedUserId)
        assertEquals(null, viewModel.state.value.confirmRemove)
    }

    @Test
    fun `cancelar a confirmacao nao remove ninguem`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = membersViewModel(
            "group-1",
            athlete,
            FakeGroupMembershipGateway(),
            FakeGroupGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup(
                        br.com.saqz.groups.presentation.sampleGroup(role = GroupRole.OWNER),
                    ),
                ),
            ),
        )

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))
        viewModel.onIntent(GroupMembersIntent.DismissRemove)

        assertEquals(null, athlete.lastRemovedUserId)
        assertEquals(null, viewModel.state.value.confirmRemove)
    }

    @Test
    fun `trocar papel com plano pausado abre a folha da assinatura e mantem a lista`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val membership = FakeGroupMembershipGateway(
            changeRoleResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.SubscriptionRequired)),
        )
        val viewModel = membersViewModel(
            "group-1",
            athlete,
            membership,
            FakeGroupGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup(
                        br.com.saqz.groups.presentation.sampleGroup(role = GroupRole.OWNER),
                    ),
                ),
            ),
        )

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Promote))

        val state = viewModel.state.value
        assertTrue(state.subscriptionRequired)
        assertEquals(false, state.loadFailed)
        assertEquals(1, state.members.size)

        viewModel.onIntent(GroupMembersIntent.DismissSubscriptionRequired)
        assertEquals(false, viewModel.state.value.subscriptionRequired)
    }

    @Test
    fun `filter and search are applied to loaded roster`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry("one"), sampleRosterEntry("two")),
            ),
        )
        val memberships = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership("one", "One", GroupRole.ADMIN))),
        )
        val viewModel = membersViewModel("group-1", athlete, memberships, FakeGroupGateway())

        viewModel.onIntent(GroupMembersIntent.SelectFilter(GroupMembersFilter.Admins))

        assertEquals(listOf("one"), viewModel.state.value.admins.map { it.id })
        assertTrue(viewModel.state.value.members.isEmpty())
    }

    @Test
    fun `athlete reports and blocks anyone else including the owner`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry("owner", role = GroupRole.OWNER), sampleRosterEntry("me"), sampleRosterEntry("bia")),
            ),
        )
        val viewModel = membersViewModel(
            "group-1",
            athlete,
            FakeGroupMembershipGateway(listResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Forbidden))),
            groupAs(GroupRole.ATHLETE),
        )

        val owner = viewModel.state.value.admins.single()
        assertEquals(
            listOf(GroupMemberAction.ViewProfile, GroupMemberAction.Report, GroupMemberAction.Block),
            owner.sheetActions(),
        )
        assertTrue(viewModel.state.value.members.single { it.id == "me" }.sheetActions().none {
            it == GroupMemberAction.Report || it == GroupMemberAction.Block
        })

        viewModel.onIntent(GroupMembersIntent.OpenMember("owner"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Report))
        assertEquals(GroupMembersEffect.ReportMember("group-1", "owner", "Member"), viewModel.effects.first())
        assertEquals(null, viewModel.state.value.selected)

        viewModel.onIntent(GroupMembersIntent.OpenMember("bia"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Block))
        assertEquals(GroupMembersEffect.BlockMember("group-1", "bia", "Member"), viewModel.effects.first())
        assertEquals(null, viewModel.state.value.selected)
    }

    @Test
    fun `blocked people offer unblock and the row follows a new block at once`() = runTest {
        val gateway = FakeModerationGateway(blockedResult = SaqzResult.Success(listOf(blockedPerson("bia"))))
        val blocks = BlockedPeopleRepository(gateway)
        val viewModel = membersViewModel(
            "group-1",
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("bia"), sampleRosterEntry("leo")))),
            FakeGroupMembershipGateway(),
            groupAs(GroupRole.ATHLETE),
            blocks,
        )

        val bia = viewModel.state.value.members.single { it.id == "bia" }
        assertTrue(bia.isBlocked)
        assertEquals(GroupMemberAction.Unblock, bia.sheetActions().last())
        viewModel.onIntent(GroupMembersIntent.OpenMember("bia"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Unblock))
        assertEquals(GroupMembersEffect.UnblockMember("bia"), viewModel.effects.first())

        assertFalse(viewModel.state.value.members.single { it.id == "leo" }.isBlocked)
        blocks.block("leo", br.com.saqz.domain.GroupId("group-1"))
        assertTrue(viewModel.state.value.members.single { it.id == "leo" }.isBlocked)
    }

    @Test
    fun `loads the join requests and the pending filter shows the real number`() = runTest {
        // O dia conta no fuso do grupo: entre 0h e 3h dele, 3 h atrás já é ontem.
        val zone = TimeZone.of("America/Sao_Paulo")
        val threeHoursAgo = Clock.System.now() - 3.hours
        val sameDay = threeHoursAgo.toLocalDateTime(zone).date == Clock.System.now().toLocalDateTime(zone).date
        val entry = FakeGroupEntryRequestGateway(
            SaqzResult.Success(
                listOf(
                    request("a", "Ana", threeHoursAgo.toString()),
                    request("b", "Beto"),
                ),
            ),
        )
        val viewModel = requestsViewModel(entry)

        assertTrue(viewModel.state.value.canReviewRequests)
        assertEquals(2, viewModel.state.value.pendingCount)
        assertEquals(listOf("Ana", "Beto"), viewModel.state.value.joinRequests.map { it.name })
        assertEquals(if (sameDay) "Pediu há 3 h" else "Pediu ontem", viewModel.state.value.joinRequests.first().meta)
        assertEquals("Pediu agora", viewModel.state.value.joinRequests.last().meta)

        viewModel.onIntent(GroupMembersIntent.SelectFilter(GroupMembersFilter.Pending))
        assertEquals(2, viewModel.state.value.joinRequests.size)
        assertTrue(viewModel.state.value.members.isEmpty())
    }

    @Test
    fun `approving warns then drops the request and puts the person in the members list`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("member-1"))))
        entry.onApproved = {
            athlete.rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("member-1"), sampleRosterEntry("new")))
        }
        val viewModel = requestsViewModel(entry, athlete = athlete)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))

        assertEquals(listOf("new"), entry.approvedUserIds)
        assertEquals(GroupMembersNotice.RequestApproved("Ana"), viewModel.state.value.notice)
        assertTrue(viewModel.state.value.joinRequests.isEmpty())
        assertEquals(0, viewModel.state.value.pendingCount)
        assertEquals(2, viewModel.state.value.totalCount)
        assertTrue(viewModel.state.value.members.any { it.id == "new" })
        assertTrue(viewModel.state.value.busyRequestIds.isEmpty())
    }

    @Test
    fun `declining asks first then warns and drops the request`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val viewModel = requestsViewModel(entry)

        viewModel.onIntent(GroupMembersIntent.DeclineRequest("new"))
        assertEquals("Ana", viewModel.state.value.declineCandidate?.name)
        assertTrue(entry.rejectedUserIds.isEmpty())

        viewModel.onIntent(GroupMembersIntent.DismissDecline)
        assertEquals(null, viewModel.state.value.declineCandidate)
        assertTrue(entry.rejectedUserIds.isEmpty())

        viewModel.onIntent(GroupMembersIntent.DeclineRequest("new"))
        viewModel.onIntent(GroupMembersIntent.ConfirmDecline)

        assertEquals(listOf("new"), entry.rejectedUserIds)
        assertEquals(GroupMembersNotice.RequestRejected, viewModel.state.value.notice)
        assertEquals(null, viewModel.state.value.declineCandidate)
        assertTrue(viewModel.state.value.joinRequests.isEmpty())
        assertEquals(0, viewModel.state.value.pendingCount)
        assertTrue(viewModel.state.value.members.none { it.id == "new" })
    }

    @Test
    fun `a failed approval keeps the request and shows the error and allows another try`() = runTest {
        val entry = FakeGroupEntryRequestGateway(
            SaqzResult.Success(listOf(request("new", "Ana"))),
            approveResult = SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Server)),
        )
        val viewModel = requestsViewModel(entry)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))

        assertTrue(viewModel.state.value.actionFailed)
        assertEquals(null, viewModel.state.value.notice)
        assertEquals(listOf("new"), viewModel.state.value.joinRequests.map { it.id })
        assertEquals(1, viewModel.state.value.pendingCount)
        assertTrue(viewModel.state.value.busyRequestIds.isEmpty())

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        assertEquals(listOf("new", "new"), entry.approvedUserIds)
    }

    @Test
    fun `approval with expired organizer plan opens the subscription sheet and keeps the request`() = runTest {
        val entry = FakeGroupEntryRequestGateway(
            SaqzResult.Success(listOf(request("new", "Ana"))),
            approveResult = SaqzResult.Failure(EntryRequestError.DataFailure(DataError.SubscriptionRequired)),
        )
        val viewModel = requestsViewModel(entry)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))

        assertTrue(viewModel.state.value.subscriptionRequired)
        assertEquals(false, viewModel.state.value.actionFailed)
        assertEquals(null, viewModel.state.value.notice)
        assertEquals(listOf("new"), viewModel.state.value.joinRequests.map { it.id })
        assertTrue(viewModel.state.value.busyRequestIds.isEmpty())

        viewModel.onIntent(GroupMembersIntent.DismissSubscriptionRequired)
        assertEquals(false, viewModel.state.value.subscriptionRequired)
    }

    @Test
    fun `a failed decline keeps the request and shows the error`() = runTest {
        val entry = FakeGroupEntryRequestGateway(
            SaqzResult.Success(listOf(request("new", "Ana"))),
            rejectResult = SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Connectivity)),
        )
        val viewModel = requestsViewModel(entry)

        viewModel.onIntent(GroupMembersIntent.DeclineRequest("new"))
        viewModel.onIntent(GroupMembersIntent.ConfirmDecline)

        assertTrue(viewModel.state.value.actionFailed)
        assertEquals(null, viewModel.state.value.notice)
        assertEquals(listOf("new"), viewModel.state.value.joinRequests.map { it.id })
    }

    @Test
    fun `a double tap on accept calls the api once`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        entry.decisionDeferred = CompletableDeferred()
        val viewModel = requestsViewModel(entry)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        viewModel.onIntent(GroupMembersIntent.DeclineRequest("new"))
        viewModel.onIntent(GroupMembersIntent.ConfirmDecline)

        assertEquals(setOf("new"), viewModel.state.value.busyRequestIds)
        assertEquals(listOf("new"), entry.approvedUserIds)
        assertTrue(entry.rejectedUserIds.isEmpty())
        entry.decisionDeferred?.complete(Unit)
    }

    @Test
    fun `a common athlete gets no pending filter and the api is not asked`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val viewModel = requestsViewModel(entry, role = GroupRole.ATHLETE)

        assertFalse(viewModel.state.value.canReviewRequests)
        assertEquals(0, entry.listCalls)
        assertEquals(0, viewModel.state.value.pendingCount)
        assertTrue(viewModel.state.value.joinRequests.isEmpty())
        assertEquals(listOf("member-1"), viewModel.state.value.members.map { it.id })
    }

    @Test
    fun `when the requests fail the roster stays and the notice offers a retry`() = runTest {
        val entry = FakeGroupEntryRequestGateway(
            SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Server)),
        )
        val viewModel = requestsViewModel(entry)

        assertFalse(viewModel.state.value.loadFailed)
        assertTrue(viewModel.state.value.requestsFailed)
        assertEquals(listOf("member-1"), viewModel.state.value.members.map { it.id })

        entry.listResult = SaqzResult.Success(listOf(request("new")))
        viewModel.onIntent(GroupMembersIntent.Retry)
        assertFalse(viewModel.state.value.requestsFailed)
        assertEquals(1, viewModel.state.value.pendingCount)
    }

    @Test
    fun `when the roster fails the requests stay and the notice offers a retry`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Server)),
        )
        val viewModel = requestsViewModel(entry, athlete = athlete)

        assertFalse(viewModel.state.value.loadFailed)
        assertTrue(viewModel.state.value.rosterFailed)
        assertEquals(listOf("new"), viewModel.state.value.joinRequests.map { it.id })

        athlete.rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("member-1")))
        viewModel.onIntent(GroupMembersIntent.Retry)
        assertFalse(viewModel.state.value.rosterFailed)
        assertEquals(listOf("member-1"), viewModel.state.value.members.map { it.id })
    }

    @Test
    fun `when both lists fail the screen shows the load failure`() = runTest {
        val entry = FakeGroupEntryRequestGateway(
            SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Server)),
        )
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Server)),
        )
        val viewModel = requestsViewModel(entry, athlete = athlete)

        assertTrue(viewModel.state.value.loadFailed)
    }

    private suspend fun assertProfileAndEditorNavigation(role: GroupRole, actions: List<GroupMemberAction>) {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("member-1"))))
        val membership = FakeGroupMembershipGateway()
        val viewModel = membersViewModel("group-1", athlete, membership, groupAs(role))
        assertEquals(actions, viewModel.state.value.members.single().sheetActions())

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        assertEquals("member-1", viewModel.state.value.selected?.id)
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.ViewProfile))
        assertEquals(GroupMembersEffect.OpenMemberProfile("member-1"), viewModel.effects.first())
        assertEquals(null, viewModel.state.value.selected)

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        assertEquals("member-1", viewModel.state.value.selected?.id)
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.EditMember))
        assertEquals(GroupMembersEffect.OpenMemberEditor("member-1"), viewModel.effects.first())
        assertEquals(null, viewModel.state.value.selected)
        assertEquals(null, athlete.lastUpdateCommand)
        assertEquals(null, athlete.lastUpdateOwnProfileCommand)
        assertEquals(null, athlete.lastRemovedUserId)
        assertEquals(null, membership.lastRoleCommand)
    }

    private fun membersViewModel(
        groupId: String,
        athlete: FakeAthleteGateway,
        memberships: FakeGroupMembershipGateway,
        group: FakeGroupGateway,
        blocks: BlockedPeopleRepository = fakeBlocks(),
        entryRequests: FakeGroupEntryRequestGateway = FakeGroupEntryRequestGateway(),
    ) = GroupMembersViewModel(groupId, athlete, memberships, group, entryRequests, blocks)

    private fun request(userId: String, name: String = "Ana", requestedAt: String = Clock.System.now().toString()) =
        GroupEntryRequest(userId, name, requestedAt)

    @Test
    fun `sem mensalidade padrao aceitar e um toque so e como avulso`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val viewModel = requestsViewModel(entry)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))

        assertEquals(null, viewModel.state.value.acceptAs)
        assertEquals(listOf(br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO), entry.approvedTypes)
    }

    @Test
    fun `com mensalidade padrao aceitar abre a folha com avulso marcado e nao chama a api`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val viewModel = requestsViewModel(entry, monthlyFee = true)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))

        val sheet = assertNotNull(viewModel.state.value.acceptAs)
        assertEquals(br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO, sheet.selected)
        assertEquals(8000L, sheet.feeCents)
        assertEquals(10, sheet.dueDay)
        assertTrue(entry.approvedUserIds.isEmpty())
    }

    @Test
    fun `escolher mensalista e confirmar envia o tipo e fecha a folha`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val viewModel = requestsViewModel(entry, monthlyFee = true)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        viewModel.onIntent(GroupMembersIntent.SelectAcceptAs(br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA))
        viewModel.onIntent(GroupMembersIntent.ConfirmAcceptAs)

        assertEquals(listOf(br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA), entry.approvedTypes)
        assertEquals(null, viewModel.state.value.acceptAs)
        assertEquals(GroupMembersNotice.RequestApproved("Ana"), viewModel.state.value.notice)
        assertTrue(viewModel.state.value.joinRequests.isEmpty())
    }

    @Test
    fun `confirmar com avulso marcado aceita como avulso`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val viewModel = requestsViewModel(entry, monthlyFee = true)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        viewModel.onIntent(GroupMembersIntent.ConfirmAcceptAs)

        assertEquals(listOf(br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO), entry.approvedTypes)
    }

    @Test
    fun `fechar a folha sem confirmar nao muda nada`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        val viewModel = requestsViewModel(entry, monthlyFee = true)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        viewModel.onIntent(GroupMembersIntent.SelectAcceptAs(br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA))
        viewModel.onIntent(GroupMembersIntent.DismissAcceptAs)

        assertEquals(null, viewModel.state.value.acceptAs)
        assertTrue(entry.approvedUserIds.isEmpty())
        assertEquals(listOf("new"), viewModel.state.value.joinRequests.map { it.id })
    }

    @Test
    fun `padrao removido no meio-tempo mantem o pedido e mostra o aviso na folha`() = runTest {
        val entry = FakeGroupEntryRequestGateway(
            SaqzResult.Success(listOf(request("new", "Ana"))),
            approveResult = SaqzResult.Failure(EntryRequestError.MonthlyFeeRequired),
        )
        val viewModel = requestsViewModel(entry, monthlyFee = true)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        viewModel.onIntent(GroupMembersIntent.SelectAcceptAs(br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA))
        viewModel.onIntent(GroupMembersIntent.ConfirmAcceptAs)

        assertTrue(assertNotNull(viewModel.state.value.acceptAs).feeRequired)
        assertEquals(false, viewModel.state.value.actionFailed)
        assertEquals(null, viewModel.state.value.notice)
        assertEquals(listOf("new"), viewModel.state.value.joinRequests.map { it.id })
        assertTrue(viewModel.state.value.busyRequestIds.isEmpty())
    }

    @Test
    fun `toque duplo em confirmar a folha chama a api uma vez`() = runTest {
        val entry = FakeGroupEntryRequestGateway(SaqzResult.Success(listOf(request("new", "Ana"))))
        entry.decisionDeferred = CompletableDeferred()
        val viewModel = requestsViewModel(entry, monthlyFee = true)

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("new"))
        viewModel.onIntent(GroupMembersIntent.ConfirmAcceptAs)
        viewModel.onIntent(GroupMembersIntent.ConfirmAcceptAs)
        viewModel.onIntent(GroupMembersIntent.DismissAcceptAs)

        assertEquals(listOf("new"), entry.approvedUserIds)
        assertNotNull(viewModel.state.value.acceptAs)
        entry.decisionDeferred?.complete(Unit)
    }

    private fun requestsViewModel(
        entryRequests: FakeGroupEntryRequestGateway,
        role: GroupRole = GroupRole.ADMIN,
        athlete: FakeAthleteGateway = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("member-1"))),
        ),
        monthlyFee: Boolean = false,
    ) = membersViewModel("group-1", athlete, FakeGroupMembershipGateway(), groupAs(role, monthlyFee), entryRequests = entryRequests)

    private fun groupAs(role: GroupRole, monthlyFee: Boolean = false) = FakeGroupGateway(
        readResult = SaqzResult.Success(
            br.com.saqz.groups.presentation.sampleVersionedGroup(
                br.com.saqz.groups.presentation.sampleGroup(role = role).copy(
                    financeDefaults = if (monthlyFee) br.com.saqz.groups.domain.group.GroupFinanceDefaults(defaultGameFeeCents = null, monthlyFeeCents = 8000, monthlyDueDay = 10) else null,
                ),
            ),
        ),
    )
}
