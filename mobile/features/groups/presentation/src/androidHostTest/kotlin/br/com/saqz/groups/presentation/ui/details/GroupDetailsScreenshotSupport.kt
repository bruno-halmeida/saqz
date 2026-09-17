package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.captureRoboImage

/** A captura única da tela inteira: cada suíte de bloco só escolhe estado, nome e pasta. */
internal fun ComposeContentTestRule.captureDetails(
    name: String,
    state: GroupDetailsState,
    directory: String,
    photoFailed: Boolean = false,
) {
    setContent {
        SaqzTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SaqzTheme.colors.background),
            ) {
                GroupDetailsScreen(state = state, onBack = {}, onIntent = {}, photoFailed = photoFailed)
            }
        }
    }
    onRoot().captureRoboImage("screenshots/$directory/$name.png")
}
