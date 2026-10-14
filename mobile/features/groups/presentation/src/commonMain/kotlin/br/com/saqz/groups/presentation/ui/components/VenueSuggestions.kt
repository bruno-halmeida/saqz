package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.venue_search_powered_by_google
import org.jetbrains.compose.resources.stringResource

internal object VenueSuggestionTags {
    const val List = "venue-suggestions"
    const val Attribution = "venue-suggestions-attribution"
    fun item(placeId: String) = "venue-suggestion-$placeId"
}

/**
 * Campo de nome da quadra com as sugestões do Google logo abaixo. Sem sugestões, é só o campo:
 * o texto livre segue valendo (muitas quadras não estão no Google). Perder o foco fecha a lista.
 *
 * As sugestões chegam depois do teclado aberto e crescem para baixo do campo: sem rolar, ficam atrás
 * do teclado (ou do botão fixo do rodapé), e só aparecem ao fechá-lo. Cada lista nova pede que o campo
 * e a lista inteira entrem na vista, e a tela que rola (o formulário) sobe até caberem.
 */
@Composable
internal fun VenueSearchField(
    suggestions: List<PlaceSuggestion>,
    onSelect: (PlaceSuggestion) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
) {
    // Só a perda de foco fecha a lista; o evento inicial (sem foco) não é intenção de ninguém.
    var hadFocus by remember { mutableStateOf(false) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(suggestions) {
        if (suggestions.isEmpty()) return@LaunchedEffect
        // A lista nova só tem tamanho depois do layout do quadro em que chegou: o pedido espera o próximo.
        withFrameNanos { }
        requester.bringIntoView()
    }
    Column(
        modifier = modifier.bringIntoViewRequester(requester).onFocusChanged {
            if (hadFocus && !it.hasFocus) onDismiss()
            hadFocus = it.hasFocus
        },
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        field()
        if (suggestions.isNotEmpty()) VenueSuggestionList(suggestions, onSelect)
    }
}

@Composable
internal fun VenueSuggestionList(
    suggestions: List<PlaceSuggestion>,
    onSelect: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzCard(modifier = modifier.testTag(VenueSuggestionTags.List), padded = false) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueSuggestionTags.item(suggestion.placeId))
                    .clickable(role = Role.Button) { onSelect(suggestion) }
                    .heightIn(min = metrics.minimumTouchTarget)
                    .padding(horizontal = metrics.blockGap, vertical = metrics.subGrid),
                horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaqzIcon(SaqzIcons.Pin, tint = colors.textSecondary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(suggestion.name, style = SaqzTheme.typography.label, color = colors.textPrimary)
                    if (suggestion.address.isNotBlank()) {
                        Text(suggestion.address, style = SaqzTheme.typography.support, color = colors.textSecondary)
                    }
                }
            }
        }
        // Termos do Google Places: a lista de sugestões leva a atribuição à Google.
        Text(
            text = stringResource(Res.string.venue_search_powered_by_google),
            style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueSuggestionTags.Attribution)
                .padding(horizontal = metrics.blockGap, vertical = metrics.subGrid),
        )
    }
}

@Preview
@Composable
private fun VenueSuggestionListPreview() = SaqzTheme {
    VenueSuggestionList(
        suggestions = listOf(
            PlaceSuggestion("p1", "CERET", "R. Canuto Abreu, Tatuapé", "CERET, R. Canuto Abreu, Tatuapé"),
            PlaceSuggestion("p2", "Quadra do Parque", "Av. Paulista, 1000", "Quadra do Parque, Av. Paulista, 1000"),
        ),
        onSelect = {},
        modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
    )
}
