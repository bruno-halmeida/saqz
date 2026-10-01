package br.com.saqz.groups.presentation.communication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.moderation.ReportTargetType
import br.com.saqz.groups.presentation.moderation.ModerationIntent
import br.com.saqz.groups.presentation.moderation.ModerationViewModel
import br.com.saqz.groups.presentation.moderation.ReportTargetUi
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.moderation.ModerationActionRow
import br.com.saqz.groups.presentation.ui.moderation.ModerationOverlay
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.connected_load_failure_title
import br.com.saqz.groups.resources.communication_chat
import br.com.saqz.groups.resources.communication_notices
import br.com.saqz.groups.resources.communication_refresh
import br.com.saqz.groups.resources.communication_more
import br.com.saqz.groups.resources.communication_empty
import br.com.saqz.groups.resources.communication_draft
import br.com.saqz.groups.resources.communication_send
import br.com.saqz.groups.resources.communication_send_failure
import br.com.saqz.groups.resources.communication_read_only
import br.com.saqz.groups.resources.communication_failure
import br.com.saqz.groups.resources.communication_objectionable
import br.com.saqz.groups.resources.moderation_block_author
import br.com.saqz.groups.resources.moderation_block_cancel
import br.com.saqz.groups.resources.moderation_notice_options
import br.com.saqz.groups.resources.moderation_report_notice
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.Preview

object GroupThreadTags {
    const val Screen = "group-thread"
    const val Draft = "message-draft"
    const val Send = "message-send"
    const val ReportMessage = "message-actions-report"
    const val BlockAuthor = "message-actions-block"
    fun message(id: String) = "message-$id"
    fun options(id: String) = "message-options-$id"
}

@Composable
fun GroupThreadRoot(
    groupId: String,
    notices: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    moderation: ModerationViewModel = koinViewModel(key = "moderation/group-thread/$groupId/$notices"),
) {
    val vm: GroupThreadViewModel = koinViewModel(
        key = "group-thread/$groupId/$notices", parameters = { parametersOf(groupId, notices) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val moderationState by moderation.state.collectAsStateWithLifecycle()
    ObserveAsEvents(vm.effects) { effect ->
        when (effect) {
            is GroupThreadEffect.ReportMessage -> moderation.onIntent(
                ModerationIntent.StartReport(
                    ReportTargetUi(effect.groupId, ReportTargetType.MESSAGE, effect.messageId, effect.author),
                ),
            )
            is GroupThreadEffect.BlockAuthor -> moderation.onIntent(
                ModerationIntent.StartBlock(effect.groupId, effect.authorId, effect.author),
            )
        }
    }
    Box(modifier.fillMaxSize()) {
        GroupThreadScreen(state, notices, onBack, vm::onIntent)
        ModerationOverlay(state = moderationState, onIntent = moderation::onIntent)
    }
}

@Composable
internal fun GroupThreadScreen(
    state: GroupThreadState,
    notices: Boolean,
    onBack: () -> Unit,
    onIntent: (GroupThreadIntent) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GroupThreadContent(state, notices, onBack, onIntent)
        MessageActionsSheet(state.actionsFor, onIntent)
    }
}

@Composable
private fun GroupThreadContent(
    state: GroupThreadState,
    notices: Boolean,
    onBack: () -> Unit,
    onIntent: (GroupThreadIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(SaqzTheme.colors.background)
            .imePadding().navigationBarsPadding().testTag(GroupThreadTags.Screen),
    ) {
        SaqzTopAppBar(
            title = stringResource(if (notices) Res.string.communication_notices else Res.string.communication_chat),
            onBack = onBack,
        )
        when {
            state.loading -> SaqzSpinner()
            state.error != null -> GroupLoadFailure(
                state.error, { onIntent(GroupThreadIntent.Refresh) },
                failureTitle = stringResource(Res.string.connected_load_failure_title),
            )
            else -> {
                SaqzButton(
                    stringResource(Res.string.communication_refresh),
                    { onIntent(GroupThreadIntent.Refresh) }, enabled = !state.sending,
                    modifier = Modifier.padding(horizontal = SaqzTheme.metrics.horizontalPadding),
                )
                if (state.pageFailed) Text(stringResource(Res.string.communication_failure))
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = SaqzTheme.metrics.horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
                ) {
                    if (state.messages.isEmpty()) item { Text(stringResource(Res.string.communication_empty)) }
                    items(state.messages, key = { it.id }) { message ->
                        ThreadMessageCard(message, onOptions = { onIntent(GroupThreadIntent.OpenMessageActions(message.id)) })
                    }
                    if (state.nextCursor != null) item {
                        SaqzButton(
                            stringResource(Res.string.communication_more),
                            { onIntent(GroupThreadIntent.More) }, loading = state.paging,
                        )
                    }
                }
                if (state.canPost) Column(Modifier.padding(SaqzTheme.metrics.horizontalPadding)) {
                    SaqzInput(
                        state.draft, { onIntent(GroupThreadIntent.Draft(it)) }, stringResource(Res.string.communication_draft),
                        modifier = Modifier.testTag(GroupThreadTags.Draft),
                        enabled = !state.sending, singleLine = false, minLines = 2,
                        errorText = when {
                            state.sendRejected -> stringResource(Res.string.communication_objectionable)
                            state.sendFailed -> stringResource(Res.string.communication_send_failure)
                            else -> null
                        },
                    )
                    SaqzButton(
                        stringResource(Res.string.communication_send), { onIntent(GroupThreadIntent.Send) },
                        loading = state.sending, enabled = state.draft.isNotBlank(),
                        modifier = Modifier.testTag(GroupThreadTags.Send),
                    )
                } else Text(
                    stringResource(Res.string.communication_read_only),
                    modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
                )
            }
        }
    }
}

/** O menu de três pontos só existe na mensagem de outra pessoa: ninguém denuncia a si mesmo. */
@Composable
private fun ThreadMessageCard(message: ThreadMessageUi, onOptions: () -> Unit) {
    SaqzCard(modifier = Modifier.testTag(GroupThreadTags.message(message.id))) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(message.author, color = SaqzTheme.colors.textPrimary, style = SaqzTheme.typography.body)
                Text(message.body, color = SaqzTheme.colors.textPrimary)
                Text(message.time, color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.support)
            }
            if (!message.own) {
                SaqzIconButton(
                    onClick = onOptions,
                    contentDescription = stringResource(Res.string.moderation_notice_options, message.author),
                    size = SaqzTheme.metrics.grid * 4,
                    modifier = Modifier.testTag(GroupThreadTags.options(message.id)),
                ) {
                    SaqzIcon(SaqzIcons.MoreVertical, tint = SaqzTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun MessageActionsSheet(message: ThreadMessageUi?, onIntent: (GroupThreadIntent) -> Unit) {
    SaqzBottomSheet(
        open = message != null,
        onClose = { onIntent(GroupThreadIntent.DismissMessageActions) },
        footer = {
            SaqzButton(
                label = stringResource(Res.string.moderation_block_cancel),
                onClick = { onIntent(GroupThreadIntent.DismissMessageActions) },
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
            )
        },
    ) {
        val current = message ?: return@SaqzBottomSheet
        ModerationActionRow(
            icon = SaqzIcons.Flag,
            title = stringResource(Res.string.moderation_report_notice),
            onClick = { onIntent(GroupThreadIntent.ReportMessage) },
            modifier = Modifier.testTag(GroupThreadTags.ReportMessage),
        )
        if (current.authorId.isNotBlank()) {
            ModerationActionRow(
                icon = SaqzIcons.Ban,
                title = stringResource(Res.string.moderation_block_author, current.author),
                onClick = { onIntent(GroupThreadIntent.BlockAuthor) },
                destructive = true,
                modifier = Modifier.testTag(GroupThreadTags.BlockAuthor),
            )
        }
    }
}

@Preview
@Composable
private fun GroupThreadPreview() = SaqzTheme {
    GroupThreadScreen(GroupThreadState(loading = false, canPost = true, draft = "Confirmado para sábado!"), false, {}, {})
}
