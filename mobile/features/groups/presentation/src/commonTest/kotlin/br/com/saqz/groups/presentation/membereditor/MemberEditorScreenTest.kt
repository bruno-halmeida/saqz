package br.com.saqz.groups.presentation.membereditor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MemberEditorScreenTest {
    @Test
    fun `operation error is visible in the normal form`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                MemberEditorScreen(
                    state = memberEditorPreviewState.copy(error = GroupUiError.Conflict),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag(MemberEditorTags.OperationError).assertExists()
        onNodeWithText("Não foi possível concluir a alteração. Tente novamente.").assertExists()
    }
}
