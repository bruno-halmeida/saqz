package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/** A montagem única dos testes de bloco: a tela inteira, com o tema, sem DI. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.setDetailsScreen(
    state: GroupDetailsState,
    photoFailed: Boolean = false,
    onIntent: (GroupDetailsIntent) -> Unit = {},
) = setContent {
    SaqzTheme {
        GroupDetailsScreen(
            state = state,
            onBack = {},
            onIntent = onIntent,
            photoFailed = photoFailed,
        )
    }
}
