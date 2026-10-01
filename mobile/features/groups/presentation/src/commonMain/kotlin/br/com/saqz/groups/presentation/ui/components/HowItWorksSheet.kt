package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.how_it_works_got_it
import br.com.saqz.groups.resources.how_it_works_title
import org.jetbrains.compose.resources.stringResource

@Immutable
data class HowItWorksStatus(val label: String, val tone: SaqzChipTone)

/**
 * O conteúdo de uma folha "Como funciona", sempre nas mesmas partes: o que é, como fazer em
 * poucos passos, o que não acontece e, quando existe, os estados. Quem aprende uma folha
 * aprende todas.
 */
@Immutable
data class HowItWorksContent(
    val intro: String,
    val stepsLabel: String,
    val steps: List<String>,
    val notLabel: String,
    val notHappening: String,
    val statusLabel: String? = null,
    val statuses: List<HowItWorksStatus> = emptyList(),
)

internal object HowItWorksTags {
    const val Sheet = "how-it-works-sheet"
    const val GotIt = "how-it-works-got-it"
}

/**
 * A folha "Como funciona" de uma tela densa. Abre sozinha na primeira vez em que a tela é
 * aberta (a ViewModel da tela guarda isso na memória local) e fica no link da tela depois.
 */
@Composable
internal fun HowItWorksSheet(
    open: Boolean,
    content: HowItWorksContent,
    onClose: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    SaqzBottomSheet(
        open = open,
        title = stringResource(Res.string.how_it_works_title),
        description = content.intro,
        onClose = onClose,
        footer = {
            SaqzButton(
                label = stringResource(Res.string.how_it_works_got_it),
                onClick = onClose,
                fullWidth = true,
                modifier = Modifier.testTag(HowItWorksTags.GotIt),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(HowItWorksTags.Sheet),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            HowItWorksLabel(content.stepsLabel)
            content.steps.forEachIndexed { index, step -> HowItWorksStep(number = index + 1, text = step) }
            HowItWorksLabel(content.notLabel)
            Text(
                text = content.notHappening,
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textPrimary,
            )
            if (content.statusLabel != null && content.statuses.isNotEmpty()) {
                HowItWorksLabel(content.statusLabel)
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
                    content.statuses.forEach { status ->
                        SaqzStatusChip(text = status.label, tone = status.tone, dot = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorksLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = SaqzTheme.typography.label,
        color = SaqzTheme.colors.textSecondary,
    )
}

@Composable
private fun HowItWorksStep(number: Int, text: String) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        horizontalArrangement = Arrangement.spacedBy(metrics.grid),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(metrics.iconButtonSize - metrics.grid * 2)
                .clip(CircleShape)
                .background(colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = SaqzTheme.typography.label, color = colors.primary)
        }
        Text(
            text = text,
            style = SaqzTheme.typography.support,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
