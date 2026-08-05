package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.presentation.FakeAthleteFinanceGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeFinanceStatementGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.port.GroupNowPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class GroupDetailsRootTest {
    @Test
    fun `refresh version retries the details view model`() = runComposeUiTest {
        val gameGateway = FakeGameGateway()
        val viewModel = detailsViewModel(gameGateway = gameGateway)
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            SaqzTheme {
                GroupDetailsRoot(
                    groupId = GroupId,
                    onBack = {},
                    onEffect = {},
                    viewModel = viewModel,
                    refreshVersion = refreshVersion,
                )
            }
        }
        waitForIdle()
        assertNull(viewModel.state.value.nextGame)

        gameGateway.listResult = SaqzResult.Success(listOf(sampleGame()))
        runOnIdle { refreshVersion = 1 }
        waitForIdle()

        assertEquals("game-1", viewModel.state.value.nextGame?.gameId)
    }

    /**
     * VUL-205: os contadores são `rememberSaveable` **fora** do `NavDisplay`, então sobrevivem
     * ao pop da entrada — depois do primeiro lançamento da sessão nenhum deles volta a zero.
     * Uma entrada nova nasce com o contador já acumulado, e ler isso como "recarregue" somava
     * o `Retry` ao `init { load() }` da ViewModel recém-construída: duas idas concorrentes ao
     * `groupGateway.read` e duas piscadas de esqueleto, em qualquer grupo, pelo resto do
     * processo (`groupDetailsRefreshVersion` é global, não por grupo).
     */
    @Test
    fun `a fresh view model does not reload on an already bumped refresh version`() = runComposeUiTest {
        val groupGateway = FakeGroupGateway()
        val viewModel = detailsViewModel(groupGateway = groupGateway)

        setContent {
            SaqzTheme {
                GroupDetailsRoot(
                    groupId = GroupId,
                    onBack = {},
                    onEffect = {},
                    viewModel = viewModel,
                    // O que a entrada reempilhada recebe depois de um lançamento salvo.
                    refreshVersion = 3,
                )
            }
        }
        waitForIdle()

        assertEquals(1, groupGateway.readCalls)
    }

    /**
     * O outro lado da mesma guarda, e o motivo de a referência ser `rememberSaveable`: quando
     * a entrada **continua** na pilha (o caixa e o lançamento entram por cima dela), a
     * ViewModel sobrevive junto com o estado salvo, e o incremento que aconteceu enquanto ela
     * estava fora de composição tem que recarregar na volta. Com `remember` puro a referência
     * se perderia aí e a recarga que o lançamento pediu nunca aconteceria.
     */
    @Test
    fun `returning to a stacked entry reloads once for the bump it missed`() = runComposeUiTest {
        val groupGateway = FakeGroupGateway()
        val viewModel = detailsViewModel(groupGateway = groupGateway)
        var refreshVersion by mutableIntStateOf(0)
        var onScreen by mutableStateOf(true)

        setContent {
            val stateHolder = rememberSaveableStateHolder()
            SaqzTheme {
                // O que o `NavDisplay` faz com a entrada que fica no fundo da pilha: o
                // conteúdo sai de composição e o `SaveableStateHolder` guarda o
                // `rememberSaveable` dela até a volta. O `ViewModelStore` da entrada não é
                // tocado nesse caminho — por isso a ViewModel aqui é sempre a mesma.
                if (onScreen) {
                    stateHolder.SaveableStateProvider(GroupId) {
                        GroupDetailsRoot(
                            groupId = GroupId,
                            onBack = {},
                            onEffect = {},
                            viewModel = viewModel,
                            refreshVersion = refreshVersion,
                        )
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1, groupGateway.readCalls)

        // Caixa e lançamento empilham por cima; salvar incrementa o contador do detalhe.
        runOnIdle { onScreen = false }
        waitForIdle()
        runOnIdle { refreshVersion = 1 }
        waitForIdle()
        runOnIdle { onScreen = true }
        waitForIdle()

        assertEquals(2, groupGateway.readCalls)
    }

    private fun detailsViewModel(
        groupGateway: FakeGroupGateway = FakeGroupGateway(),
        gameGateway: FakeGameGateway = FakeGameGateway(),
    ) = GroupDetailsViewModel(
        groupId = GroupId,
        groupGateway = groupGateway,
        gameGateway = gameGateway,
        attendanceGateway = FakeAttendanceGateway(),
        athleteGateway = FakeAthleteGateway(),
        statementGateway = FakeFinanceStatementGateway(),
        organizerFinanceGateway = FakeOrganizerFinanceGateway(),
        athleteFinanceGateway = FakeAthleteFinanceGateway(),
        now = GroupNowPort { kotlin.time.Instant.parse("2026-08-01T00:00:00Z") },
    )

    private companion object {
        const val GroupId = "group-1"
    }
}
