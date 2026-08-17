package br.com.saqz.composeapp.catalog

import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.composeapp.shell.SaqzShellProfileTab
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test

/**
 * O catálogo é interativo — é a diferença entre ele e o screenshot test. O que este teste
 * cobre é justamente o que o golden não podia: o toque muda estado de verdade, e a entrada
 * visível na Perfil não existe mais. O gate de ambiente continua; a abertura vira easter egg.
 */
@OptIn(ExperimentalTestApi::class)
class SaqzCatalogScreenTest {

    @Test
    fun everySectionOfFlow10IsPresent() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen(onBack = {}) } }
        listOf(
            SaqzCatalogTags.Foundations,
            SaqzCatalogTags.Actions,
            SaqzCatalogTags.Forms,
            SaqzCatalogTags.Data,
            SaqzCatalogTags.Feedback,
            SaqzCatalogTags.Navigation,
        ).forEach { tag -> onNodeWithTag(tag).assertExists() }
        onNodeWithTag(SaqzCatalogTags.Root).assertIsDisplayed()
    }

    @Test
    fun theSwitchFlipsOnTouch() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen(onBack = {}) } }
        onNodeWithTag(SaqzCatalogTags.Switch).performScrollTo().assertIsOn().performClick()
        waitForIdle()
        onNodeWithTag(SaqzCatalogTags.Switch).assertIsOff()
    }

    @Test
    fun theCompactChoiceChipChangesDayOnTouch() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen(onBack = {}) } }
        onNodeWithTag(SaqzCatalogTags.CompactChips).performScrollTo()
        onNodeWithText("Sáb").performClick()
        waitForIdle()
        onNodeWithText("Sáb").assertIsSelected()
    }

    @Test
    fun theSheetOpensAndClosesFromTheCatalog() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen(onBack = {}) } }
        onNodeWithTag(SaqzCatalogTags.SheetTrigger).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Sair da conta?").assertIsDisplayed()
        onNodeWithTag(SaqzCatalogTags.SheetCancel).performClick()
        waitForIdle()
        onNodeWithText("Sair da conta?").assertDoesNotExist()
    }

    @Test
    fun theCatalogButtonIsGoneFromProfileEvenInDev() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    catalogEnabled = true,
                    profileTab = { Text("Você está conectado.") },
                )
            }
        }
        onNodeWithText("Perfil").performClick()
        waitForIdle()
        onNodeWithText("Catálogo do design system").assertDoesNotExist()
    }

    @Test
    fun theCatalogStaysClosedOutsideDev() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    initialCatalogOpen = true,
                    profileTab = { Text("Você está conectado.") },
                )
            }
        }
        onNodeWithTag(SaqzCatalogTags.Root).assertDoesNotExist()
    }

    @Test
    fun theDevShellReachesTheCatalogAndComesBack() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    catalogEnabled = true,
                    initialCatalogOpen = true,
                    initialTab = SaqzShellProfileTab,
                    profileTab = { Text("Você está conectado.") },
                )
            }
        }
        onNodeWithTag(SaqzCatalogTags.Root).assertIsDisplayed()
        // Dois "Voltar" na árvore: o da barra do catálogo e o espécime de ícone da seção
        // Ações. O primeiro em ordem de layout é a barra.
        onAllNodesWithContentDescription("Voltar").onFirst().performClick()
        waitForIdle()
        onNodeWithText("Você está conectado.").assertIsDisplayed()
    }

    // O toast não tem botão de fechar: ele conta os 2600ms do token e se dispensa sozinho.
    // ponytail: `waitUntil` em vez de dirigir `mainClock` quadro a quadro — com
    // `autoAdvance = false` o próprio clique já não completa. O valor exato do token está
    // coberto por [theCurrentTimingsAreReadableOnScreen]; aqui o que importa é que some
    // sem ninguém tocar.
    @Test
    fun theToastDismissesItself() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen(onBack = {}) } }
        onNodeWithTag(SaqzCatalogTags.ToastTrigger).performScrollTo().performClick()
        onNodeWithTag(SaqzCatalogTags.Toast).assertExists()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(SaqzCatalogTags.Toast).fetchSemanticsNodes().isEmpty()
        }
    }

    // A divergência do switch (`.18s` no export, `.28s` no código) só apareceu lendo CSS.
    // Aqui os quatro tempos vigentes ficam legíveis na própria tela, ao lado da animação.
    @Test
    fun theCurrentTimingsAreReadableOnScreen() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCatalogScreen(onBack = {}) } }
        onNodeWithTag(SaqzCatalogTags.MotionTokens).performScrollTo().assertExists()
        onNodeWithText(
            "sheet 320ms · thumb do segmented 280ms · switch 180ms · toast 2600ms",
        ).assertExists()
    }
}
