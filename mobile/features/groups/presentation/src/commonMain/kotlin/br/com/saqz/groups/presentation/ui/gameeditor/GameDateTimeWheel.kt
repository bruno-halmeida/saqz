package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 4b — picker de rolagem com três colunas (dia / hora / minuto em passo de 15). Cada coluna
 * é um [verticalScroll] cujo deslocamento define o item selecionado. Vive no módulo da
 * jornada porque não está no fluxo 10 do export (AGENTS.md §5).
 */
internal data class GameDateTimeWheelState(
    val days: List<WheelItem>,
    val hours: List<Int>,
    val minutes: List<Int>,
    val selectedDayIndex: Int,
    val selectedHour: Int,
    val selectedMinute: Int,
)

internal data class WheelItem(val isoDate: String, val label: String)

internal object GameDateTimeWheel {
    private const val MINUTE_STEP = 15

    fun state(
        days: List<WheelItem>,
        selectedDate: String,
        selectedHour: Int,
        selectedMinute: Int,
    ): GameDateTimeWheelState {
        val dayIndex = days.indexOfFirst { it.isoDate == selectedDate }.let { if (it < 0) 0 else it }
        val preservedMinute = selectedMinute.coerceIn(0, 59)
        return GameDateTimeWheelState(
            days = days,
            hours = (0..23).toList(),
            minutes = ((0..59 step MINUTE_STEP) + preservedMinute).distinct().sorted(),
            selectedDayIndex = dayIndex,
            selectedHour = selectedHour.coerceIn(0, 23),
            selectedMinute = preservedMinute,
        )
    }

    fun selectedDate(state: GameDateTimeWheelState): String =
        state.days.getOrNull(state.selectedDayIndex)?.isoDate ?: ""
}

@Composable
internal fun GameDateTimeWheel(
    state: GameDateTimeWheelState,
    onDayChange: (Int) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val itemHeight = WheelItemHeight
    val visibleCount = WheelVisibleCount
    val wheelHeight = itemHeight * visibleCount
    Row(
        modifier = modifier.fillMaxWidth().height(wheelHeight),
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        WheelColumn(
            items = state.days.map { it.label },
            selectedIndex = state.selectedDayIndex,
            onSelect = onDayChange,
            itemHeight = itemHeight,
            wheelHeight = wheelHeight,
            modifier = Modifier.weight(1f),
        )
        WheelColumn(
            items = state.hours.map { it.toString().padStart(2, '0') },
            selectedIndex = state.selectedHour,
            onSelect = onHourChange,
            itemHeight = itemHeight,
            wheelHeight = wheelHeight,
            modifier = Modifier.width(56.dp),
        )
        WheelColumn(
            items = state.minutes.map { it.toString().padStart(2, '0') },
            selectedIndex = state.minutes.indexOf(state.selectedMinute).let { if (it < 0) 0 else it },
            onSelect = { index -> onMinuteChange(state.minutes.getOrNull(index) ?: 0) },
            itemHeight = itemHeight,
            wheelHeight = wheelHeight,
            modifier = Modifier.width(56.dp),
        )
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    itemHeight: androidx.compose.ui.unit.Dp,
    wheelHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val itemPx = with(density) { itemHeight.toPx() }
    val centerPaddingPx = with(density) { ((wheelHeight - itemHeight) / 2).toPx() }
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val currentOnSelect = androidx.compose.runtime.rememberUpdatedState(onSelect)
    val currentSelectedIndex = androidx.compose.runtime.rememberUpdatedState(selectedIndex)
    LaunchedEffect(safeIndex, items.size) {
        if (items.isNotEmpty()) {
            val target = (safeIndex * itemPx).roundToInt()
            scrollState.animateScrollTo(target)
        }
    }
    LaunchedEffect(scrollState.value, scrollState.isScrollInProgress, items.size) {
        if (items.isNotEmpty() && !scrollState.isScrollInProgress) {
            val index = (scrollState.value / itemPx).roundToInt().coerceIn(0, items.lastIndex)
            if (index != currentSelectedIndex.value) currentOnSelect.value(index)
        }
    }
    Box(
        modifier = modifier
            .height(wheelHeight)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .background(colors.surface)
            .pointerInput(items) {
                detectTapGestures { offset ->
                    val index = wheelIndexForTap(
                        scrollOffsetPx = scrollState.value,
                        tapOffsetPx = offset.y,
                        centerPaddingPx = centerPaddingPx,
                        itemHeightPx = itemPx,
                        itemCount = items.size,
                    )
                    onSelect(index)
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = (wheelHeight - itemHeight) / 2),
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == safeIndex
                Text(
                    text = label,
                    style = if (selected) SaqzTheme.typography.subtitle.copy(fontWeight = FontWeight.Bold)
                    else SaqzTheme.typography.body,
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

internal fun wheelIndexForTap(
    scrollOffsetPx: Int,
    tapOffsetPx: Float,
    centerPaddingPx: Float,
    itemHeightPx: Float,
    itemCount: Int,
): Int {
    if (itemCount == 0) return 0
    return floor((scrollOffsetPx + tapOffsetPx - centerPaddingPx) / itemHeightPx)
        .toInt()
        .coerceIn(0, itemCount - 1)
}

internal fun buildWheelDays(
    start: LocalDate,
    count: Int,
    weekdayLabels: List<String>,
    monthLabels: List<String>,
): List<WheelItem> {
    return (0 until count).map { offset ->
        val date = start.plus(DatePeriod(days = offset))
        val weekday = weekdayLabels[date.dayOfWeek.ordinal]
        WheelItem(
            isoDate = date.toString(),
            label = "$weekday, ${date.day} de ${monthLabels[date.monthNumber - 1]}",
        )
    }
}

private val WheelItemHeight = 40.dp
private const val WheelVisibleCount = 5
