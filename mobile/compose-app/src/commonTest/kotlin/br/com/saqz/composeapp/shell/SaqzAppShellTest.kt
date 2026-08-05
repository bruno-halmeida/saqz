package br.com.saqz.composeapp.shell

import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
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

    /** VUL-200: membro comum tem três abas, e Jogos não existe mais para ninguém. */
    @Test
    fun opensOnTheHomeTabWithThreeTabsForAPlainMember() = runComposeUiTest {
        setContent { SaqzTheme { SaqzAppShell(homeTab = { Text(HomeTab) }, groupsTab = { Text(GroupsTab) }) } }
        onNodeWithTag(SaqzShellTabContentTag).assertIsDisplayed()
        // VUL-193: o login cai na aba Início, não em Grupos.
        onNodeWithText(HomeTab).assertIsDisplayed()
        onNodeWithText(GroupsTab).assertDoesNotExist()
        // A barra é do shell: os itens estão aqui, não nas telas de grupo.
        listOf("Início", "Grupos", "Perfil").forEach { onNodeWithText(it).assertIsDisplayed() }
        onNodeWithText("Jogos").assertDoesNotExist()
        onNodeWithText("Financeiro").assertDoesNotExist()
    }

    /** VUL-200: quem administra algum grupo ganha a quarta aba. */
    @Test
    fun showsTheFinanceTabForWhoeverAdministersAGroup() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(financeTabVisible = true, homeTab = { Text(HomeTab) })
            }
        }
        listOf("Início", "Grupos", "Financeiro", "Perfil").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
        onNodeWithText("Jogos").assertDoesNotExist()
    }

    @Test
    fun financeTabRendersInsideShellAndCanReturnToGroups() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    financeTabVisible = true,
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

    /**
     * VUL-200: a guarda defensiva da aba ativa. A visibilidade cair com a Caixa aberta não
     * acontece hoje (a sessão não recarrega memberships com o shell montado), mas é a regra
     * que impede a aba ativa de apontar para item que a barra não desenha — e é ela também
     * que cobre os casos reais, `initialTab` desconhecido e `"jogos"` restaurado.
     */
    @Test
    fun fallsBackToHomeWhenTheFinanceTabDisappears() = runComposeUiTest {
        val administers = mutableStateOf(true)
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    financeTabVisible = administers.value,
                    homeTab = { Text(HomeTab) },
                    financeTab = { Text(FinanceTab) },
                )
            }
        }
        onNodeWithText("Financeiro").performClick()
        waitForIdle()
        onNodeWithText(FinanceTab).assertIsDisplayed()

        administers.value = false
        waitForIdle()

        onNodeWithText("Financeiro").assertDoesNotExist()
        onNodeWithText(FinanceTab).assertDoesNotExist()
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
