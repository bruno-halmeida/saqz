package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzChoiceChipDefaults
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.height_picker_centimeter_spoken
import br.com.saqz.groups.resources.height_picker_centimeter_spoken_one
import br.com.saqz.groups.resources.height_picker_centimeters
import br.com.saqz.groups.resources.height_picker_clear
import br.com.saqz.groups.resources.height_picker_meters
import br.com.saqz.groups.resources.height_picker_meters_spoken
import br.com.saqz.groups.resources.height_picker_save
import br.com.saqz.groups.resources.height_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * Altura por toque (DEB-103): metros e décimos (1,0 a 2,5) numa grade e o centímetro (0 a 9) noutra, de 1,00 m a
 * 2,50 m de 1 em 1 cm. A conta continua em centímetros inteiros; o seletor só devolve `Int?` (nulo = sem altura).
 */
internal object HeightPicker {
    const val MIN_CM = 100
    const val MAX_CM = 250
    const val DEFAULT_CM = 170

    /** Ao abrir sem altura (ou com uma fora da faixa) a seleção começa perto de 1,70 m. */
    fun initial(heightCm: Int?): Int = heightCm?.takeIf { it in MIN_CM..MAX_CM } ?: DEFAULT_CM

    fun isValid(heightCm: Int): Boolean = heightCm in MIN_CM..MAX_CM

    /** 175 vira "1,75 m". */
    fun format(heightCm: Int): String = "${heightCm / 100},${(heightCm % 100).toString().padStart(2, '0')} m"

    /** Décimos de metro em centímetros de dezena: 17 = 1,7 m. */
    fun tenths(): List<Int> = (MIN_CM / 10..MAX_CM / 10).toList()

    /** Em 2,5 m só o 0 cabe (o limite é 2,50 m). */
    fun centimeters(tenth: Int): List<Int> = if (tenth * 10 >= MAX_CM) listOf(0) else (0..9).toList()

    fun withTenth(current: Int, tenth: Int): Int = tenth * 10 + (current % 10).coerceAtMost(centimeters(tenth).last())

    fun withCentimeter(current: Int, centimeter: Int): Int = current / 10 * 10 + centimeter
}

internal object HeightPickerTags {
    const val Sheet = "height-picker"
    const val Save = "height-picker-save"
    const val Clear = "height-picker-clear"
    fun tenth(tenth: Int) = "height-picker-tenth-$tenth"
    fun centimeter(centimeter: Int) = "height-picker-cm-$centimeter"
}

private const val TENTHS_PER_ROW = 6
private const val CENTIMETERS_PER_ROW = 5

@Composable
internal fun HeightPickerSheet(
    open: Boolean,
    heightCm: Int?,
    onClose: () -> Unit,
    onSave: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(open, heightCm) { mutableStateOf(HeightPicker.initial(heightCm)) }
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        title = stringResource(Res.string.height_picker_title),
        modifier = modifier.testTag(HeightPickerTags.Sheet),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                SaqzButton(
                    label = stringResource(Res.string.height_picker_save),
                    onClick = { onSave(selected) },
                    fullWidth = true,
                    modifier = Modifier.testTag(HeightPickerTags.Save),
                )
                SaqzButton(
                    label = stringResource(Res.string.height_picker_clear),
                    onClick = { onSave(null) },
                    variant = SaqzButtonVariant.Secondary,
                    fullWidth = true,
                    modifier = Modifier.testTag(HeightPickerTags.Clear),
                )
            }
        },
    ) {
        Text(HeightPicker.format(selected), style = SaqzTheme.typography.subtitle, color = SaqzTheme.colors.textPrimary)
        HeightCaption(stringResource(Res.string.height_picker_meters))
        HeightGrid(HeightPicker.tenths(), TENTHS_PER_ROW) { tenth ->
            val label = "${tenth / 10},${tenth % 10}"
            val spoken = stringResource(Res.string.height_picker_meters_spoken, label)
            SaqzChoiceChip(
                label = label,
                selected = selected / 10 == tenth,
                onClick = { selected = HeightPicker.withTenth(selected, tenth) },
                compact = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(HeightPickerTags.tenth(tenth))
                    .semantics { contentDescription = spoken },
            )
        }
        HeightCaption(stringResource(Res.string.height_picker_centimeters))
        HeightGrid(HeightPicker.centimeters(selected / 10), CENTIMETERS_PER_ROW) { centimeter ->
            val spoken = if (centimeter == 1) {
                stringResource(Res.string.height_picker_centimeter_spoken_one)
            } else {
                stringResource(Res.string.height_picker_centimeter_spoken, centimeter)
            }
            SaqzChoiceChip(
                label = centimeter.toString(),
                selected = selected % 10 == centimeter,
                onClick = { selected = HeightPicker.withCentimeter(selected, centimeter) },
                compact = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(HeightPickerTags.centimeter(centimeter))
                    .semantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun HeightCaption(text: String) =
    Text(text, style = SaqzTheme.typography.eyebrow, color = SaqzTheme.colors.textSecondary)

/** Grade de [perRow] colunas; as células que faltam na última linha ficam vazias para os chips manterem a largura. */
@Composable
internal fun HeightGrid(
    items: List<Int>,
    perRow: Int,
    cell: @Composable RowScope.(Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        items.chunked(perRow).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SaqzChoiceChipDefaults.CompactSpacing),
            ) {
                row.forEach { item -> cell(item) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
