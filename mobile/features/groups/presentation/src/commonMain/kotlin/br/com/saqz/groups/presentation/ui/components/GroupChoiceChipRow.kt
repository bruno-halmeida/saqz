package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.durationLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupChoiceChipRow(
    values: List<Int>,
    selectedValue: Int?,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        values.forEach { value ->
            SaqzChoiceChip(
                label = label(value),
                selected = value == selectedValue,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Preview
@Composable
private fun GroupChoiceChipRowPreview() = SaqzTheme {
    GroupChoiceChipRow(
        values = listOf(60, 90, 120, 150),
        selectedValue = 120,
        label = { durationLabel(it) },
        onSelect = {},
        modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
    )
}
