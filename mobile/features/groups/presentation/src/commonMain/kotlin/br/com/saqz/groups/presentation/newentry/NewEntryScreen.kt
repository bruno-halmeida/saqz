package br.com.saqz.groups.presentation.newentry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.new_entry_amount
import br.com.saqz.groups.resources.new_entry_amount_hint
import br.com.saqz.groups.resources.new_entry_category
import br.com.saqz.groups.resources.new_entry_category_court
import br.com.saqz.groups.resources.new_entry_category_material
import br.com.saqz.groups.resources.new_entry_category_other
import br.com.saqz.groups.resources.new_entry_custom_category
import br.com.saqz.groups.resources.new_entry_custom_category_error
import br.com.saqz.groups.resources.new_entry_category_racha
import br.com.saqz.groups.resources.new_entry_date
import br.com.saqz.groups.resources.new_entry_description
import br.com.saqz.groups.resources.new_entry_description_hint
import br.com.saqz.groups.resources.new_entry_direction
import br.com.saqz.groups.resources.new_entry_in
import br.com.saqz.groups.resources.new_entry_out
import br.com.saqz.groups.resources.new_entry_save
import br.com.saqz.groups.resources.new_entry_save_error
import br.com.saqz.groups.resources.new_entry_saving
import br.com.saqz.groups.resources.new_entry_shortcut_120
import br.com.saqz.groups.resources.new_entry_shortcut_160
import br.com.saqz.groups.resources.new_entry_shortcut_80
import br.com.saqz.groups.resources.new_entry_title
import br.com.saqz.groups.resources.new_entry_validation
import org.jetbrains.compose.resources.stringResource

internal object NewEntryTags {
    const val Screen = "finance-new-entry"
    const val Direction = "finance-new-entry-direction"
    const val Amount = "finance-new-entry-amount"
    const val Description = "finance-new-entry-description"
    const val Category = "finance-new-entry-category"
    const val CustomCategory = "finance-new-entry-custom-category"
    const val Date = "finance-new-entry-date"
    const val Save = "finance-new-entry-save"
}

@Composable
internal fun NewEntryScreen(
    state: NewEntryState,
    onBack: () -> Unit,
    onIntent: (NewEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .imePadding()
            .testTag(NewEntryTags.Screen),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.new_entry_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            Text(
                text = stringResource(Res.string.new_entry_direction),
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            SaqzSegmented(
                options = listOf(
                    stringResource(Res.string.new_entry_in),
                    stringResource(Res.string.new_entry_out),
                ),
                selected = state.direction.ordinal,
                onSelect = { selected ->
                    onIntent(NewEntryIntent.SelectDirection(NewEntryDirection.entries[selected]))
                },
                modifier = Modifier.testTag(NewEntryTags.Direction),
            )
            SaqzInput(
                value = state.amountText,
                onValueChange = { onIntent(NewEntryIntent.AmountChanged(it)) },
                label = stringResource(Res.string.new_entry_amount),
                placeholder = stringResource(Res.string.new_entry_amount_hint),
                kind = SaqzInputKind.Text,
                keyboardType = KeyboardType.Decimal,
                leadingContent = { Text("R$", color = SaqzTheme.colors.textSecondary) },
                modifier = Modifier.testTag(NewEntryTags.Amount),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                listOf(
                    8_000L to Res.string.new_entry_shortcut_80,
                    12_000L to Res.string.new_entry_shortcut_120,
                    16_000L to Res.string.new_entry_shortcut_160,
                ).forEach { (cents, label) ->
                    SaqzChoiceChip(
                        label = stringResource(label),
                        selected = state.amountText == formatEntryCents(cents),
                        onClick = { onIntent(NewEntryIntent.SelectAmountShortcut(cents)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SaqzInput(
                value = state.description,
                onValueChange = { onIntent(NewEntryIntent.DescriptionChanged(it)) },
                label = stringResource(Res.string.new_entry_description),
                placeholder = stringResource(Res.string.new_entry_description_hint),
                singleLine = false,
                minLines = 3,
                modifier = Modifier.testTag(NewEntryTags.Description),
            )
            Text(
                text = stringResource(Res.string.new_entry_category),
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            NewEntryCategory.entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
                ) {
                    row.forEach { category ->
                        SaqzChoiceChip(
                            label = category.label(),
                            selected = category == state.category,
                            onClick = { onIntent(NewEntryIntent.SelectCategory(category)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (state.category == NewEntryCategory.Other) {
                val customCategoryInvalid = state.error == br.com.saqz.groups.presentation.GroupUiError.Validation &&
                    !isValidCustomCategory(state.customCategory)
                SaqzInput(
                    value = state.customCategory,
                    onValueChange = { onIntent(NewEntryIntent.CustomCategoryChanged(it)) },
                    label = stringResource(Res.string.new_entry_custom_category),
                    errorText = if (customCategoryInvalid) {
                        stringResource(Res.string.new_entry_custom_category_error)
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(NewEntryTags.CustomCategory),
                )
            }
            SaqzInput(
                value = formatEntryDate(state.date),
                onValueChange = { onIntent(NewEntryIntent.DateChanged(it)) },
                label = stringResource(Res.string.new_entry_date),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.testTag(NewEntryTags.Date),
            )
            state.error?.let { error ->
                Text(
                    text = if (error == br.com.saqz.groups.presentation.GroupUiError.Validation) {
                        stringResource(Res.string.new_entry_validation)
                    } else {
                        stringResource(Res.string.new_entry_save_error)
                    },
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.errorForeground,
                )
            }
        }
        SaqzButton(
            label = if (state.isSaving) {
                stringResource(Res.string.new_entry_saving)
            } else {
                stringResource(Res.string.new_entry_save)
            },
            onClick = { onIntent(NewEntryIntent.Save) },
            enabled = !state.isSaving,
            loading = state.isSaving,
            fullWidth = true,
            modifier = Modifier
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
                .testTag(NewEntryTags.Save),
        )
    }
}

@Composable
private fun NewEntryCategory.label(): String = stringResource(
    when (this) {
        NewEntryCategory.Court -> Res.string.new_entry_category_court
        NewEntryCategory.Material -> Res.string.new_entry_category_material
        NewEntryCategory.Racha -> Res.string.new_entry_category_racha
        NewEntryCategory.Other -> Res.string.new_entry_category_other
    },
)

private fun formatEntryDate(iso: String): String {
    val parts = iso.split('-')
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}

@Preview
@Composable
private fun NewEntryPreview() = SaqzTheme {
    NewEntryScreen(
        state = NewEntryState(
            date = "2026-08-04",
            amountText = "80,00",
            description = "Aluguel da quadra",
            category = NewEntryCategory.Court,
        ),
        onBack = {},
        onIntent = {},
    )
}
