package br.com.saqz.groups.presentation.communication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.Preview

object GroupThreadTags {
    const val Screen = "group-thread"
    const val Draft = "message-draft"
    const val Send = "message-send"
    fun message(id: String) = "message-$id"
}

@Composable
fun GroupThreadRoot(groupId: String, notices: Boolean, onBack: () -> Unit) {
    val vm: GroupThreadViewModel = koinViewModel(
        key = "group-thread/$groupId/$notices", parameters = { parametersOf(groupId, notices) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    GroupThreadScreen(state, notices, onBack, vm::onIntent)
}

@Composable
internal fun GroupThreadScreen(
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
                        SaqzCard(modifier = Modifier.testTag(GroupThreadTags.message(message.id))) {
                            Text(message.author, color = SaqzTheme.colors.textPrimary, style = SaqzTheme.typography.body)
                            Text(message.body, color = SaqzTheme.colors.textPrimary)
                            Text(message.time, color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.support)
                        }
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
                        errorText = if (state.sendFailed) stringResource(Res.string.communication_send_failure) else null,
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

@Preview
@Composable
private fun GroupThreadPreview() = SaqzTheme {
    GroupThreadScreen(GroupThreadState(loading = false, canPost = true, draft = "Confirmado para sábado!"), false, {}, {})
}
