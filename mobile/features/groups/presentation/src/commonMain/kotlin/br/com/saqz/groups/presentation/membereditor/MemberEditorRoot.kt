package br.com.saqz.groups.presentation.membereditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MemberEditorRoot(
    groupId: String,
    userId: String,
    onBack: () -> Unit,
    onRemove: () -> Unit,
    viewModel: MemberEditorViewModel = koinViewModel(
        key = "member-editor/$groupId/$userId",
        parameters = { parametersOf(groupId, userId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            MemberEditorEffect.Close -> onBack()
            MemberEditorEffect.Removed -> onRemove()
        }
    }
    MemberEditorScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
