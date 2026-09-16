package br.com.saqz.designsystem

import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SaqzHeroCardTest {
    @Test
    fun rendersKickerTitleMetaTrailingAndContent() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzHeroCard(
                    kicker = "PRÓXIMO JOGO",
                    title = "Terça, 19h30",
                    meta = "28 de julho",
                    trailing = { Text("Vôlei", modifier = Modifier.testTag("trailing")) },
                ) {
                    Text("conteúdo", modifier = Modifier.testTag("content"))
                }
            }
        }
        onNodeWithText("PRÓXIMO JOGO").assertExists()
        onNodeWithText("Terça, 19h30").assertExists()
        onNodeWithText("28 de julho").assertExists()
        onNodeWithTag("trailing").assertExists()
        onNodeWithTag("content").assertExists()
    }

    @Test
    fun metaTrailingAndContentAreOptional() = runComposeUiTest {
        setContent { SaqzTheme { SaqzHeroCard(kicker = "PRÓXIMO JOGO", title = "Sem jogo marcado") } }
        onNodeWithText("Sem jogo marcado").assertExists()
    }
}
