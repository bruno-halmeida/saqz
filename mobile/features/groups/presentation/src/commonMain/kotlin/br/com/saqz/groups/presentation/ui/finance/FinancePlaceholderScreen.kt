package br.com.saqz.groups.presentation.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.theme.SaqzTheme

const val FinancePlaceholderTag = "finance-placeholder"

/** Fiação visual mínima; os conteúdos do Fluxo 5 entram na onda C. */
@Composable
fun FinancePlaceholderScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(FinancePlaceholderTag),
        contentAlignment = Alignment.Center,
    ) {
        SaqzSpinner()
    }
}
