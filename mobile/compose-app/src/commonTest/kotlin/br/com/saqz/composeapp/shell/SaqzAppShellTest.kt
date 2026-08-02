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
    fun opensOnTheGroupsTab() = runComposeUiTest {
        setContent { SaqzTheme { SaqzAppShell(groupsTab = { Text(GroupsTab) }) } }
        onNodeWithTag(SaqzShellTabContentTag).assertIsDisplayed()
        onNodeWithText(GroupsTab).assertIsDisplayed()
        // A barra é do shell: os quatro itens do 10q estão aqui, não nas telas de grupo.
        listOf("Início", "Jogos", "Grupos", "Perfil").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
    }

    // VUL-72: Início e Jogos ficam inertes até os fluxos 6 e 4 existirem — tocar neles não
    // pode trocar o conteúdo nem esconder a lista.
    @Test
    fun homeAndGamesTabsAreInert() = runComposeUiTest {
        setContent { SaqzTheme { SaqzAppShell(groupsTab = { Text(GroupsTab) }) } }
        onNodeWithText("Início").performClick()
        waitForIdle()
        onNodeWithText(GroupsTab).assertIsDisplayed()
        onNodeWithText("Jogos").performClick()
        waitForIdle()
        onNodeWithText(GroupsTab).assertIsDisplayed()
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
        const val ProfileTab = "conteúdo-da-aba-perfil"
    }
}
