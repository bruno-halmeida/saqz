package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.presentation.SessionAccessState
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
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

    // -- fixtures --------------------------------------------------------------------

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
) = SaqzTheme {
    SaqzNavHost(state = session.value, onIntent = {}, backStack = backStack)
}

/**
 * Uma carga por **instância**, não por leitura: a faixa recarrega por baixo no `resume`
 * (VUL-202), então contar leituras diria pouco sobre quantas ViewModels existem.
 */
private class HomeProbe(vararg payloads: HomeReadModel) {
    private val payloads = payloads.toList()
    val instances = mutableListOf<HomeViewModel>()

    fun newViewModel(attendance: AttendanceGateway, now: GroupNowPort): HomeViewModel {
        val payload = payloads.getOrElse(instances.size) { payloads.last() }
        return HomeViewModel(FixedHomeGateway(payload), FixedAthleteGateway, attendance, now)
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

    override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> {
        reads += groupId.value
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
