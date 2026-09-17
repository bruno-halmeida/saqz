package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Linha de "Esperando você" (VUL-192/219): círculo ice 40dp com ícone cinza, título em
 * `compactTitle`, meta em `compactMeta` e o que a tela quiser à direita em [trailing]
 * (chip, chevron, botão). Vive dentro de um `SaqzCard(padded = false)`, separada por `SaqzDivider`.
 *
 * [onClick] nulo = linha sem clique: o único alvo é o que estiver em [trailing]. Para o leitor
 * de tela ela continua sendo um item só, descrito por [contentDescription]; um botão no
 * [trailing] segue como alvo próprio. [leading] substitui o círculo com ícone ([icon] é
 * ignorado). [meta] nulo esconde a segunda linha; [metaMaxLines] corta a meta com reticências.
 */
@Composable
internal fun WaitingRow(
    icon: ImageVector,
    title: String,
    meta: String?,
    contentDescription: String,
    onClick: (() -> Unit)?,
    tag: String,
    modifier: Modifier = Modifier,
    metaMaxLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .semantics { this.contentDescription = contentDescription }
            .testTag(tag)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        if (leading != null) {
            leading()
        } else {
            Box(
                modifier = Modifier
                    .size(metrics.grid * 5)
                    .clip(CircleShape)
                    .background(colors.surfaceSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SaqzIcon(icon = icon, tint = colors.textSecondary)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2)) {
            Text(
                text = title,
                style = SaqzTheme.typography.compactTitle,
                color = colors.textPrimary,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = SaqzTheme.typography.compactMeta,
                    color = colors.textSecondary,
                    maxLines = metaMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}
