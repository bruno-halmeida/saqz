package br.com.saqz.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import br.com.saqz.designsystem.theme.SaqzTheme

// Bottom-anchored modal built on the same Dialog + scaffold as SaqzDialog. No experimental
// ModalBottomSheet, no drag handle and no drag-to-dismiss: the same dismiss flags apply,
// the body scrolls and the footer stays fixed.
@Composable
fun SaqzBottomSheet(
    title: String,
    onCloseRequest: () -> Unit,
    primaryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = false,
    dismissOnClickOutside: Boolean = false,
    showCloseAction: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onCloseRequest,
        properties = saqzDialogProperties(dismissOnBackPress, dismissOnClickOutside),
    ) {
        SaqzModalScaffold(
            title = title,
            onCloseRequest = onCloseRequest,
            primaryAction = primaryAction,
            showCloseAction = showCloseAction,
            dismissOnClickOutside = dismissOnClickOutside,
            alignment = Alignment.BottomCenter,
            // Full width within the scaffold's horizontal safe-area padding.
            cardModifier = modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Preview
@Composable
private fun SaqzBottomSheetPreview() = SaqzTheme {
    SaqzBottomSheet(
        "Opções",
        {},
        primaryAction = {
            SaqzButton(
                "Salvar",
                {})
        }) { Text("Conteúdo") }
}
