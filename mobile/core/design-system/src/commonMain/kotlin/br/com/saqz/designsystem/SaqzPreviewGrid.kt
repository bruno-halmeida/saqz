package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.designsystem.theme.SaqzTheme

// Moldura das @Preview: fundo canvas, margem de página e o gap de bloco entre peças.
// Existe só para as previews não repetirem esse Column em todo arquivo.
@Composable
internal fun SaqzPreviewGrid(content: @Composable ColumnScope.() -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaqzTheme.colors.background)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionGap),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        content = content,
    )
}
