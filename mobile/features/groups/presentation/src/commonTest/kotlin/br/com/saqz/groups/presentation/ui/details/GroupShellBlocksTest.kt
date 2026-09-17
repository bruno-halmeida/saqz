package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Topo, mural, galera/gestão, quadra e sair: os blocos do ticket C2. */
@OptIn(ExperimentalTestApi::class)
class GroupShellBlocksTest {
    private val managerOnly = listOf(
        GroupDetailsTags.EditGroup,
        GroupDetailsTags.Manage,
        GroupDetailsTags.ManageMembers,
        GroupDetailsTags.ManageSchedule,
        GroupDetailsTags.ManageInviteLink,
        GroupDetailsTags.Cashbox,
    )

    private val memberOnly = listOf(GroupDetailsTags.ViewAllMembers)

    private val everyone = listOf(
        GroupDetailsTags.Mural,
        GroupDetailsTags.ShortcutNotices,
        GroupDetailsTags.ShortcutChat,
        GroupDetailsTags.People,
    )

    private val gone = listOf(
        GroupDetailsTags.ShortcutSchedule,
        GroupDetailsTags.ShortcutCashbox,
        GroupDetailsTags.Notice,
        GroupDetailsTags.Invite,
    )

    private val goneTexts = listOf("Marcar próximo jogo", "Editar grupo", "Aviso recente", "Convidar mais gente", "Gerenciar")

    @Test
    fun managerViewHasManagementAndNoCrowdRow() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        (managerOnly + everyone).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        memberOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun memberViewHasTheCrowdRowAndNoManagement() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        (memberOnly + everyone).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        managerOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun oldShellPiecesAreGoneForTheManager() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        gone.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
        goneTexts.forEach { onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun oldShellPiecesAreGoneForTheMember() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        gone.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
        goneTexts.forEach { onAllNodesWithText(it).assertCountEquals(0) }
        onAllNodesWithText("Membros").assertCountEquals(0)
    }

    // ---- topo ----

    @Test
    fun editLivesInTheTopBarAndAsksToEditTheGroup() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.manager) { intents += it }

        onNodeWithTag(GroupDetailsTags.EditGroup).performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.EditGroup), intents)
    }

    @Test
    fun loadingShowsTheSkeletonAndNoEditAction() = runComposeUiTest {
        setDetailsScreen(GroupDetailsState(isAdmin = true))

        onNodeWithTag(GroupDetailsTags.Skeleton).assertExists()
        onAllNodesWithTag(GroupDetailsTags.EditGroup).assertCountEquals(0)
    }

    @Test
    fun loadFailureHasNoSkeletonAndNoEditAction() = runComposeUiTest {
        setDetailsScreen(GroupDetailsState(isLoading = false, loadFailed = true, isAdmin = true))

        onAllNodesWithTag(GroupDetailsTags.Skeleton).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.EditGroup).assertCountEquals(0)
    }

    @Test
    fun loadedScreenHasNoSkeleton() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onAllNodesWithTag(GroupDetailsTags.Skeleton).assertCountEquals(0)
        onNodeWithText("Vôlei do CERET").assertExists()
    }

    @Test
    fun createdPhotoFailedBannerExplainsTheGroupExists() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.managerNewGroup, photoFailed = true)

        onNodeWithTag(GroupDetailsTags.PhotoFailed).assertExists()
        onNodeWithText("Grupo criado").assertExists()
        onNodeWithText("A foto não carregou. Você pode tentar de novo em Editar grupo.").assertExists()
    }

    @Test
    fun withoutPhotoFailureThereIsNoBanner() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.managerNewGroup)

        onAllNodesWithTag(GroupDetailsTags.PhotoFailed).assertCountEquals(0)
    }

    // ---- mural ----

    @Test
    fun muralRowsOpenNoticesAndChat() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.member) { intents += it }

        onNodeWithTag(GroupDetailsTags.ShortcutNotices).performScrollTo().performClick()
        onNodeWithTag(GroupDetailsTags.ShortcutChat).performScrollTo().performClick()

        assertEquals(listOf(GroupDetailsIntent.OpenNotices, GroupDetailsIntent.OpenChat), intents)
    }

    @Test
    fun muralShowsTheLatestNoticePreview() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onNodeWithText("Mural").assertExists()
        onNodeWithText("Lucas: Cheguem 15 min antes para montar a rede. · Hoje, 10h30").assertExists()
        onNodeWithText("Fale com a galera do grupo").assertExists()
        onAllNodesWithText("Nenhum aviso por enquanto").assertCountEquals(0)
    }

    @Test
    fun muralWithoutNoticeSaysThereIsNone() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.memberBare)

        onNodeWithTag(GroupDetailsTags.ShortcutNotices).assertExists()
        onNodeWithText("Nenhum aviso por enquanto").assertExists()
    }

    // ---- galera ----

    @Test
    fun crowdRowShowsCountAndTheFirstTwoNamesPlusTheRest() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onNodeWithText("Galera").assertExists()
        onNodeWithText("26 pessoas").assertExists()
        onNodeWithText("Lucas, Bia e mais 24").assertExists()
    }

    @Test
    fun crowdRowWithTwoPeopleJoinsTheNames() = runComposeUiTest {
        val member = GroupShellPreviewData.member
        setDetailsScreen(member.copy(memberPreview = member.memberPreview.take(2), memberCount = 2))

        onNodeWithText("2 pessoas").assertExists()
        onNodeWithText("Lucas e Bia").assertExists()
    }

    @Test
    fun crowdRowWithOnePersonShowsTheWholeName() = runComposeUiTest {
        val member = GroupShellPreviewData.member
        setDetailsScreen(member.copy(memberPreview = member.memberPreview.take(1), memberCount = 1))

        onNodeWithText("1 pessoa").assertExists()
        onNodeWithText("Lucas Prado").assertExists()
    }

    @Test
    fun crowdRowWithoutDataFallsBackAndStillOpensMembers() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.memberBare) { intents += it }

        onNodeWithText("Membros").assertExists()
        // O mesmo seletor do e2e: o clicável é DESCENDENTE do nó com a tag.
        onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(GroupDetailsTags.ViewAllMembers)),
        ).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.ViewAllMembers), intents)
    }

    // ---- gestão ----

    @Test
    fun manageRowsEmitTheirIntents() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.manager) { intents += it }

        listOf(
            GroupDetailsTags.ManageMembers,
            GroupDetailsTags.ManageSchedule,
            GroupDetailsTags.ManageInviteLink,
            GroupDetailsTags.Cashbox,
        ).forEach { onNodeWithTag(it).performScrollTo().performClick() }

        assertEquals(
            listOf(
                GroupDetailsIntent.ManageMembers,
                GroupDetailsIntent.ManageSchedule,
                GroupDetailsIntent.InviteByLink,
                GroupDetailsIntent.OpenCashbox,
            ),
            intents,
        )
    }

    @Test
    fun manageRowsShowCountScheduleInviteAndBalance() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        onNodeWithText("Gestão").assertExists()
        onNodeWithText("26 pessoas").assertExists()
        onNodeWithText("Terça e Quinta · 19h30").assertExists()
        onNodeWithText("Link, QR e pedidos de entrada").assertExists()
        onNodeWithText("Saldo R$ 380,00").assertExists()
    }

    @Test
    fun manageRowsSurviveEmptyMetas() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager.copy(memberCount = 0, scheduleSummary = null))

        onNodeWithText("Membros e permissões").assertExists()
        onNodeWithText("Jogos e horários").assertExists()
        onAllNodesWithText("26 pessoas").assertCountEquals(0)
    }

    @Test
    fun cashboxRowWithoutSummaryStaysClickable() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.managerNoCashSummary) { intents += it }

        onNodeWithText("Caixa do grupo").assertExists()
        onAllNodesWithText("Saldo R$ 380,00").assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.Cashbox).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OpenCashbox), intents)
    }

    @Test
    fun managerWithoutCashboxHasNoCashRow() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager.copy(cashbox = null))

        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.ManageInviteLink).assertExists()
    }

    @Test
    fun memberNeverSeesTheCashboxEvenFromStaleState() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member.copy(cashbox = CashboxUi(summary = "Saldo R$ 380,00")))

        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
        onAllNodesWithText("Caixa do grupo").assertCountEquals(0)
        onAllNodesWithText("Saldo R$ 380,00").assertCountEquals(0)
    }

    // ---- quadra ----

    @Test
    fun homeCourtIsHiddenWhileThereIsAGame() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onAllNodesWithTag(GroupDetailsTags.HomeCourt).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.HomeCourtMap).assertCountEquals(0)
    }

    @Test
    fun homeCourtWithoutGameOpensTheMap() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.memberNoGame) { intents += it }

        onNodeWithTag(GroupDetailsTags.HomeCourt).assertExists()
        onNodeWithTag(GroupDetailsTags.HomeCourtMap).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OpenVenueMap), intents)
    }

    @Test
    fun homeCourtWithoutVenueIsHidden() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.memberNoGame.copy(venue = null))

        onAllNodesWithTag(GroupDetailsTags.HomeCourt).assertCountEquals(0)
    }

    @Test
    fun homeCourtShowsTheMapFailureBelowTheCard() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.memberNoGameMapFailed)

        onNode(
            hasText("Não foi possível abrir o mapa. Consulte o endereço acima ou tente novamente.") and
                hasAnyAncestor(hasTestTag(GroupDetailsTags.HomeCourt)),
        ).assertExists()
    }

    // ---- sair ----

    @Test
    fun ownerCannotLeave() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        onAllNodesWithTag(GroupDetailsTags.Leave).assertCountEquals(0)
    }

    @Test
    fun adminWhoIsNotTheOwnerCanRequestDeparture() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.managerNotOwner) { intents += it }

        onNodeWithTag(GroupDetailsTags.Leave).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.Leave), intents)
    }

    @Test
    fun memberCanRequestDeparture() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.member) { intents += it }

        onNodeWithText("Sair do grupo").assertExists()
        onNodeWithTag(GroupDetailsTags.Leave).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.Leave), intents)
    }
}
