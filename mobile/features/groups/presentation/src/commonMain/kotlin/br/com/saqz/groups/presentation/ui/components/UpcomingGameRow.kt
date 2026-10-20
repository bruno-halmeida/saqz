package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzLargeFontScale
import br.com.saqz.designsystem.theme.SaqzTheme

/** Tile de data ice 44dp: dia em `dateDay`, mês em `dateMonth` azul. */
@Composable
internal fun DateTile(day: String, month: String, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    Column(
        modifier = modifier
            // Mínimo, não tamanho fixo: com fonte grande o mês ("OUT") cresce e o tile cresce junto.
            .defaultMinSize(minWidth = DateTileSize, minHeight = DateTileSize)
            .background(colors.surfaceSoft, RoundedCornerShape(SaqzTheme.metrics.inputRadius))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = day, style = SaqzTheme.typography.dateDay, color = colors.textPrimary)
        Text(text = month, style = SaqzTheme.typography.dateMonth, color = colors.primary)
    }
}

/**
 * Linha de "Próximos jogos" (VUL-221): tile de data, título, meta e o que a tela quiser à
 * direita em [trailing] — na Início é o chip do estado do próprio usuário. A linha inteira é
 * um botão; [contentDescription] é o que o leitor de tela anuncia.
 */
@Composable
internal fun UpcomingGameRow(
    day: String,
    month: String,
    title: String,
    meta: String,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    /** Terceira linha em âmbar ("Faltam: Central, Oposto(a)", DEB-109); nula, nada. */
    alert: String? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(tag)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        DateTile(day = day, month = month)
        // Fonte grande: o chip à direita tomava a largura e o texto quebrava letra a letra ("confirmad/os").
        // Ele desce para baixo do texto e a coluna ganha a linha inteira.
        val stackTrailing = LocalDensity.current.fontScale >= SaqzLargeFontScale
        // Um único ponto de chamada do slot: ele se move entre a coluna e a linha sem perder estado.
        val currentTrailing by rememberUpdatedState(trailing)
        val trailingSlot = remember { movableContentOf { currentTrailing() } }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
            Column {
                Text(text = title, style = SaqzTheme.typography.compactTitle, color = colors.textPrimary)
                Text(text = meta, style = SaqzTheme.typography.compactMeta, color = colors.textSecondary)
                if (alert != null) {
                    Text(
                        text = alert,
                        style = SaqzTheme.typography.compactMeta.copy(fontWeight = FontWeight(600)),
                        color = colors.warningForeground,
                    )
                }
            }
            if (stackTrailing) trailingSlot()
        }
        if (!stackTrailing) trailingSlot()
    }
}

private val DateTileSize = 44.dp
