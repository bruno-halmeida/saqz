package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.composeapp.startTestSaqzKoin
import br.com.saqz.composeapp.stopTestSaqzKoin
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.Athlete
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.membership.GroupDepartureGateway
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.Profile
import br.com.saqz.profile.domain.ProfileUser
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.ProfileStats
import br.com.saqz.profile.domain.AthleteProfile
import br.com.saqz.profile.domain.AthleteMembership
import org.koin.mp.KoinPlatform
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeOwnChargeGroup
import br.com.saqz.groups.domain.home.HomeOwnChargeOldest
import br.com.saqz.groups.domain.home.HomeOwnCharges
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.presentation.home.HomeViewModel
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.domain.athlete.AthleteFinancialStatus
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * VUL-204 — o escopo de ViewModel por destino do `NavDisplay`.
 *
 * Antes deste ticket o `NavDisplay` era montado **sem** `entryDecorators`: o default do
 * `navigation3-ui` traz só o `rememberSaveableStateHolderNavEntryDecorator()`, então o
 * `LocalViewModelStoreOwner` de dentro de qualquer entrada era a Activity e toda ViewModel
 * de `koinViewModel()` era singleton **de processo**. Os quatro testes travam as quatro
 * consequências disso, e cada um reprova se o `entryDecorators` sair:
 *
 * 1. faixa e aba Início continuam **uma** instância — o compartilhamento de que o VUL-202
 *    depende, e que o escopo por destino não pode quebrar;
 * 2. sair e entrar com outra conta constrói ViewModel nova, e a faixa passa a mostrar o
 *    valor de quem entrou — não o de quem saiu;
 * 3. grupo A → voltar → grupo B vai carregar o B;
 * 4. `rememberSaveable` (a aba ativa do shell) sobrevive à ida e volta — é o segundo
 *    decorator, o default que a lista explícita teria substituído em silêncio.
 *
 * O gate de sessão é dirigido pelo teste em vez de vir da `AccessViewModel`: o que importa
 * é a transição `Ready(ana)` → `SignedOut` → `Ready(bruno)`, e é o [reconcileAccessStack]
 * quem tira o shell do stack no meio dela.
 */
@OptIn(ExperimentalTestApi::class)
class SaqzNavHostViewModelScopeTest {

    @Test
    fun theBannerAndTheHomeTabShareOneViewModel() = withProbes { probe, _ ->
        runComposeUiTest {
            setContent { NavHostUnderTest(mutableStateOf(ready(ana))) }
            awaitText(AnaBanner)

            // A faixa é composta no slot do shell e a aba Início no conteúdo — as duas
            // dentro da MESMA entrada, e por isso no mesmo `ViewModelStore`. Duas
            // instâncias aqui significariam dois `GET /api/me/home` na abertura do app.
            assertEquals(1, probe.instances.size)
        }
    }

    @Test
    fun signingInAsSomeoneElseDoesNotInheritThePreviousHome() = withProbes { probe, _ ->
        runComposeUiTest {
            val session = mutableStateOf(ready(ana))
            setContent { NavHostUnderTest(session) }
            awaitText(AnaBanner)

            // Sair colapsa o stack para o login (o gate), e é aí que a entrada do shell
            // sai da pilha e o store dela é descartado.
            session.value = AccessUiState(authObserved = true, session = SessionAccessState.SignedOut)
            waitForIdle()
            session.value = ready(bruno)
            awaitText(BrunoBanner)

            onNodeWithText(BrunoBanner, substring = true).assertIsDisplayed()
            assertEquals(0, onAllNodesWithText(AnaBanner, substring = true).fetchSemanticsNodes().size)
            assertEquals(2, probe.instances.size)
            assertNotSame(probe.instances[0], probe.instances[1])
        }
    }

    @Test
    fun openingASecondGroupLoadsTheSecondGroup() = withProbes { _, groups ->
        runComposeUiTest {
            val backStack = NavBackStack<NavKey>(SaqzShellDestination.Home)
            setContent { NavHostUnderTest(mutableStateOf(ready(ana)), backStack) }
            awaitText(AnaBanner)

            backStack.add(GroupsRoute.Details("grupo-a"))
            waitForIdle()
            backStack.removeLastOrNull()
            waitForIdle()
            backStack.add(GroupsRoute.Details("grupo-b"))
            waitForIdle()

            // Com a ViewModel de processo, a segunda abertura reusava a instância do A —
            // com o `groupId` do A — e nunca chegava a perguntar pelo B.
            assertEquals(listOf("grupo-a", "grupo-b"), groups.reads)
        }
    }

    @Test
    fun theSelectedTabSurvivesNavigatingAwayAndBack() = withProbes { _, _ ->
        runComposeUiTest {
            val backStack = NavBackStack<NavKey>(SaqzShellDestination.Home)
            setContent { NavHostUnderTest(mutableStateOf(ready(ana)), backStack) }
            awaitText(AnaBanner)

            // A aba ativa é `rememberSaveable` no `SaqzAppShell`, e quem a guarda enquanto
            // a entrada está fora de composição é o
            // `rememberSaveableStateHolderNavEntryDecorator()` — o decorator default que
            // a lista explícita de `entryDecorators` teria substituído.
            tab("Grupos").performClick()
            waitForIdle()
            tab("Grupos").assertIsSelected()

            backStack.add(GroupsRoute.Details("grupo-a"))
            waitForIdle()
            backStack.removeLastOrNull()
            waitForIdle()

            tab("Grupos").assertIsSelected()
        }
    }

    @Test
    fun confirmedDepartureClearsTheJourneyAndReloadsGroupsAndProfile() = withProbes { home, groups ->
        val data = installConnectedJourney(groups)
        home.gatewayOverride = object : HomeGateway {
            override suspend fun read() = SaqzResult.Success(
                if (data.member) homeOf(AnaCents)
                else homeOf(AnaCents).copy(ownCharges = HomeOwnCharges(0, 0, emptyList())),
            )
        }
        runComposeUiTest {
            val backStack = NavBackStack<NavKey>(SaqzShellDestination.Home)
            val intents = mutableListOf<AccessIntent>()
            setContent { NavHostUnderTest(mutableStateOf(ready(ana)), backStack, intents::add) }
            awaitText(AnaBanner)
            tab("Grupos").performClick()
            awaitText("Grupo integrado")
            onNodeWithTag("group-list-group-grupo-a").performClick()
            waitForIdle()
            onNodeWithTag("group-details-leave").performScrollTo().performClick()
            onNodeWithTag("group-leave-cancel").performClick()
            assertTrue(data.member)
            assertTrue(data.departures.isEmpty())
            onNodeWithTag("group-details-leave").performScrollTo().performClick()
            onNodeWithTag("group-leave-confirm").performClick()
            waitForIdle()

            assertEquals(listOf<NavKey>(SaqzShellDestination.Groups), backStack.toList())
            assertEquals(listOf(GroupId("grupo-a")), data.departures)
            assertTrue(intents.contains(AccessIntent.Session(SessionIntent.MembershipRemoved("grupo-a"))))
            onNodeWithTag("group-list-empty").assertIsDisplayed()
            onNodeWithTag("group-list-group-grupo-a").assertDoesNotExist()
            tab("Perfil").performClick()
            waitForIdle()
            onNodeWithTag("own-profile-groups-empty").performScrollTo().assertIsDisplayed()
            onNodeWithTag("own-profile-group-grupo-a").assertDoesNotExist()
            tab("Início").performClick()
            waitForIdle()
            onNodeWithText(AnaBanner, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun editingOwnAthleteReturnsToProfileWithTheSavedPosition() = withProbes { _, groups ->
        val data = installConnectedJourney(groups)
        runComposeUiTest {
            val profileDestination = SaqzShellDestination.Home
            val backStack = NavBackStack<NavKey>(profileDestination)
            setContent { NavHostUnderTest(mutableStateOf(ready(ana)), backStack) }
            awaitText(AnaBanner)
            tab("Perfil").performClick()
            awaitText("Grupo integrado")
            onNodeWithTag("own-profile-group-grupo-a").performScrollTo().performClick()
            waitForIdle()
            assertEquals(GroupsRoute.AthleteRegistration("grupo-a", fromProfile = true), backStack.last())
            onNodeWithTag("athlete-registration-position-CENTRAL").performScrollTo().performClick()
            onNodeWithTag("athlete-registration-save").performScrollTo().performClick()
            waitForIdle()
            assertEquals(AthletePosition.CENTRAL, data.position)
            assertEquals(listOf<NavKey>(profileDestination), backStack.toList())
            onNodeWithTag("own-profile-group-grupo-a").performScrollTo().assertIsDisplayed()
            onNodeWithText("Central", substring = true).assertIsDisplayed()
        }
    }

    // -- fixtures --------------------------------------------------------------------

    @Test
    fun monthlyGenerationUsesRealNavigationAndReloadsCashboxOnlyAfterConfirmedSuccess() = withProbes { _, groups ->
        val finance = installMonthlyJourney(groups)
        runComposeUiTest {
            val backStack = NavBackStack<NavKey>(SaqzShellDestination.Home)
            setContent { NavHostUnderTest(mutableStateOf(ready(ana)), backStack) }
            awaitText(AnaBanner)
            backStack.add(FinanceRoute.GroupCashbox("grupo-mensal"))
            waitForIdle()
            assertEquals(1, finance.reads)
            onNodeWithTag("group-cashbox-generate-monthly").performScrollTo().performClick()
            waitForIdle()
            assertEquals(FinanceRoute.MonthlyGeneration("grupo-mensal"), backStack.last())
            onNodeWithTag("monthly-generation-member-ana").performScrollTo().performClick()
            onNodeWithTag("monthly-generation-review").performScrollTo().performClick()
            onNodeWithTag("monthly-generation-confirm").performScrollTo().performClick()
            waitForIdle()
            assertEquals(FinanceRoute.MonthlyGeneration("grupo-mensal"), backStack.last())
            assertEquals(1, finance.reads)
            assertTrue(finance.charges.isEmpty())
            finance.fail = false
            onNodeWithTag("monthly-generation-confirm").performScrollTo().performClick()
            waitForIdle()
            assertEquals(listOf<NavKey>(SaqzShellDestination.Home, FinanceRoute.GroupCashbox("grupo-mensal")), backStack.toList())
            assertEquals(2, finance.reads)
            assertEquals(finance.commands[0], finance.commands[1])
            assertEquals(GroupId("grupo-mensal"), finance.charges.single().groupId)
            assertEquals(8000L, finance.charges.single().amountCents)
            onNodeWithTag("group-cashbox-debtors").assertExists()
            onNodeWithText("Ana").assertExists()
        }
    }

    /**
     * Sobe o Koin do app e sobrescreve **duas** definições: a da [HomeViewModel], para
     * contar instâncias e servir a carga de cada sessão, e a do [GroupGateway], para
     * registrar por qual grupo o detalhe foi perguntar. O resto do grafo é o de produção.
     */
    private fun withProbes(block: (HomeProbe, RecordingGroupGateway) -> Unit) {
        val home = HomeProbe(homeOf(AnaCents), homeOf(BrunoCents))
        val groups = RecordingGroupGateway()
        startTestSaqzKoin()
        try {
            loadKoinModules(
                module {
                    viewModel { home.newViewModel(attendance = get(), now = get()) }
                    single<GroupGateway> { groups }
                },
            )
            block(home, groups)
        } finally {
            stopTestSaqzKoin()
        }
    }

    private fun ready(user: AccessUser) = AccessUiState(
        authObserved = true,
        session = SessionAccessState.Ready(AccessSession(user = user, memberships = emptyList())),
    )

    private companion object {
        const val AnaCents = 8_000L
        const val BrunoCents = 1_200L
        const val AnaBanner = "80,00 em aberto"
        const val BrunoBanner = "12,00 em aberto"

        // `emailVerified` de propósito: sem a faixa de e-mail disputando o slot, a única
        // faixa possível é a de cobrança e o desempate do VUL-202 sai da equação.
        val ana = AccessUser(id = "ana", email = "ana@exemplo.com", displayName = "Ana", emailVerified = true)
        val bruno = AccessUser(id = "bruno", email = "bruno@exemplo.com", displayName = "Bruno", emailVerified = true)

        fun homeOf(totalCents: Long) = HomeReadModel(
            member = HomeMemberReadModel(nextGame = null, lastCompletedGame = null, groups = emptyList()),
            admin = null,
            ownCharges = HomeOwnCharges(
                groupCount = 1,
                totalCents = totalCents,
                groups = listOf(
                    HomeOwnChargeGroup(
                        groupId = GroupId("grupo-a"),
                        groupName = "Ceret",
                        count = 1,
                        totalCents = totalCents,
                        nextDueDate = "2026-08-10",
                        overdue = true,
                        pixKey = null,
                        pixLabel = null,
                        oldest = HomeOwnChargeOldest.Monthly("2026-08"),
                    ),
                ),
            ),
        )
    }
}

/** A aba da barra do shell, e não um texto igual que a tela da aba possa desenhar. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.tab(label: String) = onNode(hasText(label) and isSelectable())

/** A carga é assíncrona (rede fake + `getString`), então a asserção espera o texto. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 10_000) {
    onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
}

@Composable
private fun NavHostUnderTest(
    session: MutableState<AccessUiState>,
    backStack: NavBackStack<NavKey> = NavBackStack(SaqzShellDestination.Home),
    onIntent: (AccessIntent) -> Unit = {},
) = SaqzTheme {
    val context = LocalPlatformContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context).build().also { imageLoader ->
            loadKoinModules(module { single<ImageLoader> { imageLoader } })
        }
    }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    SaqzNavHost(state = session.value, onIntent = onIntent, backStack = backStack)
}

/**
 * Uma carga por **instância**, não por leitura: a faixa recarrega por baixo no `resume`
 * (VUL-202), então contar leituras diria pouco sobre quantas ViewModels existem.
 */
private class HomeProbe(vararg payloads: HomeReadModel) {
    private val payloads = payloads.toList()
    val instances = mutableListOf<HomeViewModel>()
    var gatewayOverride: HomeGateway? = null

    fun newViewModel(attendance: AttendanceGateway, now: GroupNowPort): HomeViewModel {
        val payload = payloads.getOrElse(instances.size) { payloads.last() }
        return HomeViewModel(gatewayOverride ?: FixedHomeGateway(payload), FixedAthleteGateway, attendance, now)
            .also { instances += it }
    }
}

private class FixedHomeGateway(private val payload: HomeReadModel) : HomeGateway {
    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> = SaqzResult.Success(payload)
}

/** A Home só chama `ownProfile()`; o resto da porta não é alcançável por esta jornada. */
private object FixedAthleteGateway : AthleteGateway {
    override suspend fun ownProfile(): SaqzResult<OwnAthleteProfile, AthleteError> = SaqzResult.Success(
        OwnAthleteProfile(userId = "user", displayName = "Atleta", phone = null, memberships = emptyList()),
    )

    override suspend fun roster(
        groupId: GroupId,
        filter: AthleteRosterFilter,
    ): SaqzResult<List<AthleteRosterEntry>, AthleteError> = unused()

    override suspend fun updateOwnPosition(
        groupId: GroupId,
        position: AthletePosition?,
    ): SaqzResult<Athlete, AthleteError> = unused()

    override suspend fun updateAthlete(command: UpdateAthleteCommand): SaqzResult<Athlete, AthleteError> = unused()

    override suspend fun stats(groupId: GroupId, userId: String): SaqzResult<AthleteStats, AthleteError> = unused()

    override suspend fun removeAthlete(groupId: GroupId, userId: String): SaqzResult<Unit, AthleteError> = unused()

    private fun unused(): Nothing = error("A Home não usa este método")
}

/**
 * Registra o grupo pedido e falha: o teste é sobre **qual** grupo a tela foi carregar, não
 * sobre o que ela desenha depois.
 */
private class RecordingGroupGateway : GroupGateway {
    val reads = mutableListOf<String>()
    var payload: Group? = null

    override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> {
        reads += groupId.value
        payload?.let { return SaqzResult.Success(VersionedGroup(it, GroupVersionToken("v1"))) }
        return offline()
    }

    override suspend fun create(command: CreateGroupCommand): SaqzResult<Group, GroupProfileError> = offline()

    override suspend fun update(
        command: UpdateGroupSettingsCommand,
    ): SaqzResult<VersionedGroup, GroupProfileError> = offline()

    override suspend fun delete(groupId: GroupId): SaqzResult<Unit, GroupProfileError> = offline()

    private fun <T> offline(): SaqzResult<T, GroupProfileError> =
        SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Connectivity))
}

/** Shared persistence fake: each real destination reads the values changed by the previous one. */
private class ConnectedJourneyData {
    var member = true
    var position = AthletePosition.PONTA
    val departures = mutableListOf<GroupId>()
}

private fun installConnectedJourney(groups: RecordingGroupGateway): ConnectedJourneyData {
    val data = ConnectedJourneyData()
    groups.payload = Group("grupo-a", "Grupo integrado", "UTC", 1, GroupRole.ATHLETE).copy(
        profile = GroupProfile(
            GroupModality.COURT_VOLLEYBALL, null, null, null, null, null, null, null, null, emptyList(), null, null,
        ),
    )
    val originalGames = KoinPlatform.getKoin().get<GameGateway>()
    val originalProfile = KoinPlatform.getKoin().get<ProfileGateway>()
    val athletes = object : AthleteGateway by FixedAthleteGateway {
        override suspend fun ownProfile() = SaqzResult.Success(OwnAthleteProfile("ana", "Ana", null,
            if (data.member) listOf(OwnAthleteMembership(
                GroupId("grupo-a"), "Grupo integrado", GroupRole.ATHLETE, data.position, AthleteMembershipType.AVULSO, true,
            )) else emptyList(),
        ))
        override suspend fun updateOwnProfile(command: UpdateOwnAthleteProfileCommand): SaqzResult<Athlete, AthleteError> {
            assertEquals(GroupId("grupo-a"), command.groupId)
            data.position = requireNotNull(command.position)
            return SaqzResult.Success(Athlete("ana", "Ana", GroupRole.ATHLETE, data.position, AthleteMembershipType.AVULSO, true))
        }
    }
    val profile = object : ProfileGateway by originalProfile {
        override suspend fun bootstrap() = SaqzResult.Success(Profile(
            ProfileUser("ana", "ana@exemplo.com", "Ana", null, null, false, PhoneVisibility.NOBODY, null, true, null),
            emptyList(),
        ))
        override suspend fun stats() = SaqzResult.Success(ProfileStats(0, null, if (data.member) 1 else 0))
        override suspend fun athleteProfile() = SaqzResult.Success(AthleteProfile("ana", "Ana", null,
            if (data.member) listOf(AthleteMembership(
                GroupId("grupo-a"), "Grupo integrado", "ATHLETE", data.position.name, "AVULSO", true,
            )) else emptyList(),
        ))
    }
    loadKoinModules(module {
        single<AthleteGateway> { athletes }
        single<ProfileGateway> { profile }
        single<GameGateway> { object : GameGateway by originalGames {
            override suspend fun list(groupId: GroupId) = SaqzResult.Success(emptyList<br.com.saqz.groups.domain.game.Game>())
        } }
        single<GroupDepartureGateway> { GroupDepartureGateway { groupId ->
            data.departures += groupId
            data.member = false
            SaqzResult.Success(Unit)
        } }
    })
    return data
}

private class MonthlyJourneyFinance(delegate: OrganizerFinanceGateway) : OrganizerFinanceGateway by delegate {
    var fail = true
    var reads = 0
    val charges = mutableListOf<Charge>()
    val commands = mutableListOf<MonthlyChargeCommand>()
    override suspend fun charges(groupId: GroupId): SaqzResult<ChargeList, FinanceError> {
        assertEquals(GroupId("grupo-mensal"), groupId)
        reads++
        return SaqzResult.Success(ChargeList(charges.toList()))
    }
    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand): SaqzResult<ChargeList, FinanceError> {
        assertEquals(GroupId("grupo-mensal"), groupId)
        commands += command
        if (fail) return SaqzResult.Failure(FinanceError.Data(DataError.Connectivity))
        charges += Charge(
            "new-monthly", groupId, "ana", ChargeKind.Monthly, month = command.month,
            amountCents = command.amountCents, dueDate = command.dueDate,
            status = ChargeStatus.Pending, version = 1, audit = emptyList(),
        )
        return SaqzResult.Success(ChargeList(charges.toList()))
    }
}

private fun installMonthlyJourney(groups: RecordingGroupGateway): MonthlyJourneyFinance {
    groups.payload = Group("grupo-mensal", "Grupo mensal", "UTC", 1, GroupRole.OWNER)
        .copy(financeDefaults = GroupFinanceDefaults(null, 8000, 12))
    val koin = KoinPlatform.getKoin()
    val finance = MonthlyJourneyFinance(koin.get())
    val originalMemberships = koin.get<GroupMembershipGateway>()
    loadKoinModules(module {
        single<OrganizerFinanceGateway> { finance }
        single<GroupNowPort> { GroupNowPort { Instant.parse("2026-08-12T12:00:00Z") } }
        single<AthleteGateway> { object : AthleteGateway by FixedAthleteGateway {
            override suspend fun roster(groupId: GroupId, filter: AthleteRosterFilter) = SaqzResult.Success(listOf(
                AthleteRosterEntry("ana", "Ana", null, null, AthleteMembershipType.MENSALISTA, true, AthleteFinancialStatus.EM_DIA),
            ))
        } }
        single<GroupMembershipGateway> { object : GroupMembershipGateway by originalMemberships {
            override suspend fun listMemberships(groupId: GroupId) = SaqzResult.Success(listOf(GroupMembership("ana", "Ana", GroupRole.OWNER)))
        } }
        single<FinanceStatementGateway> { object : FinanceStatementGateway {
            override suspend fun statement(groupId: GroupId, query: FinanceStatementQuery) = SaqzResult.Success(
                FinanceStatementPage("2026-08", emptyList(), FinanceStatementSummary(0, 0, 0, 0), 20, 0, false),
            )
        } }
    })
    return finance
}
