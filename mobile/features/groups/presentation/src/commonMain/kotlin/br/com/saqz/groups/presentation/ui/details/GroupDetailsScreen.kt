package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.GroupLoadFailure

/**
 * A tela só empilha, e cada bloco mora no próprio arquivo, com um único dono:
 * topo · jogo · cobrança em aberto · esperando você · agenda · quadra · mural · pessoas ·
 * cobranças em dia · sair. Bloco sem conteúdo não emite nada — é o que mantém o `spacedBy`
 * sem buracos. O contêiner é `Column` + `verticalScroll` de propósito: o e2e rola até os
 * nós com `performScrollTo`, que exige todos compostos (com `LazyColumn` ele quebra).
 */
@Composable
internal fun GroupDetailsScreen(
    state: GroupDetailsState,
    onBack: () -> Unit,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
    photoFailed: Boolean = false,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(GroupDetailsTags.Screen),
    ) {
        GroupTopBar(state = state, onBack = onBack, onIntent = onIntent)
        when {
            state.isLoading -> GroupDetailsLoading()
            state.loadFailed -> GroupLoadFailure(error = state.error, onRetry = { onIntent(GroupDetailsIntent.Retry) })
            else -> Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
                        .testTag(GroupDetailsTags.Content),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {
                    GroupTopContent(state = state, onIntent = onIntent, photoFailed = photoFailed)
                    GroupHeroBlock(state = state, onIntent = onIntent)
                    GroupOwnDebtBlock(state = state, onIntent = onIntent)
                    GroupWaitingBlock(state = state, onIntent = onIntent)
                    GroupAgendaBlock(state = state, onIntent = onIntent)
                    GroupHomeCourtBlock(state = state, onIntent = onIntent)
                    GroupMuralBlock(state = state, onIntent = onIntent)
                    GroupPeopleBlock(state = state, onIntent = onIntent)
                    GroupOwnChargesSettledBlock(state = state, onIntent = onIntent)
                    GroupLeaveBlock(state = state, onIntent = onIntent)
                }
                GroupToastBlock(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
    GroupLeaveSheet(state = state, onIntent = onIntent)
}

@Preview
@Composable
private fun GroupDetailsAdminPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsPreviewData.admin, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupDetailsMemberPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsPreviewData.member, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupDetailsLoadingPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsState(), onBack = {}, onIntent = {})
}
