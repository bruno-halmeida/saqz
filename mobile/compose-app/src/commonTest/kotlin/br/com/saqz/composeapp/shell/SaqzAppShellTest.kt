package br.com.saqz.composeapp.shell

import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SaqzAppShellTest {

    @Test
    fun opensOnTheHomeTab() = runComposeUiTest {
        setContent { SaqzTheme { SaqzAppShell(homeTab = { Text(HomeTab) }, groupsTab = { Text(GroupsTab) }) } }
        onNodeWithTag(SaqzShellTabContentTag).assertIsDisplayed()
        // VUL-193: o login cai na aba Início, não em Grupos.
        onNodeWithText(HomeTab).assertIsDisplayed()
        onNodeWithText(GroupsTab).assertDoesNotExist()
        // A barra é do shell: os cinco itens estão aqui, não nas telas de grupo.
        listOf("Início", "Jogos", "Grupos", "Financeiro", "Perfil").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun financeTabRendersInsideShellAndCanReturnToGroups() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    groupsTab = { Text(GroupsTab) },
                    financeTab = { Text(FinanceTab) },
                )
            }
        }

        onNodeWithText("Financeiro").performClick()
        waitForIdle()

        onNodeWithText(FinanceTab).assertIsDisplayed()
        onNodeWithText("Grupos").performClick()
        waitForIdle()
        onNodeWithText(GroupsTab).assertIsDisplayed()
    }

    @Test
    fun homeTabRendersAndGamesTabRemainsInert() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    groupsTab = { Text(GroupsTab) },
                    homeTab = { Text(HomeTab) },
                )
            }
        }
        // VUL-193: Início já é a aba inicial; o conteúdo aparece sem trocar de aba.
        onNodeWithText(HomeTab).assertIsDisplayed()
        onNodeWithText("Jogos").performClick()
        waitForIdle()
        // Jogos é inerte (Fluxo 4): o toque não troca de aba, Início continua visível.
        onNodeWithText(HomeTab).assertIsDisplayed()
    }

    /**
     * Perfil é a exceção deliberada ao "os outros três ficam inertes": é onde o botão de
     * sair mora até o fluxo 7 existir, e sem ele o app perde a única saída de sessão.
     */
    @Test
    fun profileTabRendersTheProvidedContent() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    groupsTab = { Text(GroupsTab) },
                    profileTab = { Text(ProfileTab) },
                )
            }
        }
        onNodeWithText("Perfil").performClick()
        waitForIdle()
        onNodeWithTag(SaqzShellContentTag).assertIsDisplayed()
        onNodeWithText(ProfileTab).assertIsDisplayed()
    }

    // A restauração de `rememberSaveable` não tem teste automatizado aqui: o
    // `StateRestorationTester` do Compose é `TODO()` no Kotlin/Native
    // (`platformEncodeDecode`), e teste de Compose em `commonTest` só roda no iOS
    // (AGENTS.md §10). Conferida por rotação no emulador Android, no VUL-72.

    private companion object {
        const val GroupsTab = "conteúdo-da-aba-grupos"
        const val HomeTab = "conteúdo-da-aba-inicio"
        const val FinanceTab = "conteúdo-da-aba-financeiro"
        const val ProfileTab = "conteúdo-da-aba-perfil"
    }
}
