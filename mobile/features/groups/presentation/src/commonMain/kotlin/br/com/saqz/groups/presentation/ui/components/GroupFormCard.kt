package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.theme.SaqzTheme

@Composable
internal fun GroupFormCard(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SaqzCard(modifier = modifier) {
        Column {
            Text(
                text = title,
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
            )
            hint?.let {
                Text(
                    text = it,
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
        content()
    }
}

@Preview
@Composable
private fun GroupFormCardPreview() = SaqzTheme {
    GroupFormCard(
        title = "Duração do jogo",
        hint = "Escolha quanto tempo a partida costuma durar.",
        modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
    ) {
        SaqzChoiceChip(label = "2h", selected = true, onClick = {})
    }
}
