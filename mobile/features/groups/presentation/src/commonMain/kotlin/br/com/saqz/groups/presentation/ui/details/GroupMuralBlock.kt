package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_chat
import br.com.saqz.groups.resources.group_details_mural_chat_meta
import br.com.saqz.groups.resources.group_details_mural_notice_preview
import br.com.saqz.groups.resources.group_details_mural_notices_empty
import br.com.saqz.groups.resources.group_details_mural_title
import br.com.saqz.groups.resources.group_details_notices
import org.jetbrains.compose.resources.stringResource

private const val NoticePreviewLines = 2

/**
 * Mural: as duas portas de conversa do grupo, para todo mundo. A linha de avisos já mostra o
 * último aviso (autor, texto e hora em até duas linhas) — é o que substitui o card "Aviso
 * recente". As tags das duas linhas são contrato do e2e.
 */
@Composable
internal fun GroupMuralBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = state.latestNotice
    val noticeMeta = if (notice != null) {
        stringResource(Res.string.group_details_mural_notice_preview, notice.author, notice.body, notice.timestamp)
    } else {
        stringResource(Res.string.group_details_mural_notices_empty)
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Mural),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_mural_title))
        SaqzCard(padded = false) {
            GroupShellRow(
                icon = SaqzIcons.Megaphone,
                title = stringResource(Res.string.group_details_notices),
                meta = noticeMeta,
                tag = GroupDetailsTags.ShortcutNotices,
                onClick = { onIntent(GroupDetailsIntent.OpenNotices) },
                metaMaxLines = NoticePreviewLines,
            )
            SaqzDivider()
            GroupShellRow(
                icon = SaqzIcons.MessageSquare,
                title = stringResource(Res.string.group_details_chat),
                meta = stringResource(Res.string.group_details_mural_chat_meta),
                tag = GroupDetailsTags.ShortcutChat,
                onClick = { onIntent(GroupDetailsIntent.OpenChat) },
            )
        }
    }
}
