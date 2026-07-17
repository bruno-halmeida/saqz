package br.com.saqz.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_hide_password
import br.com.saqz.designsystem.resources.action_show_password
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

enum class SaqzInputKind { Text, Email, Password }

internal fun keyboardTypeFor(kind: SaqzInputKind): KeyboardType =
    when (kind) {
        SaqzInputKind.Text -> KeyboardType.Text
        SaqzInputKind.Email -> KeyboardType.Email
        SaqzInputKind.Password -> KeyboardType.Password
    }

// Password masks until revealed; the toggle only flips `revealed`, so the visual
// transformation is the ONLY thing it changes — never the value/selection/focus.
internal fun visualTransformationFor(kind: SaqzInputKind, revealed: Boolean): VisualTransformation =
    if (kind == SaqzInputKind.Password && !revealed) PasswordVisualTransformation() else VisualTransformation.None

@Composable
fun SaqzInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    kind: SaqzInputKind = SaqzInputKind.Text,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.compactControlRadius)
    // `revealed` governs only the visual transformation; the field value is never
    // copied here, it stays owned by the caller-provided TextFieldValue.
    var revealed by remember { mutableStateOf(false) }
    val message = errorText ?: helperText

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        Text(text = label, style = SaqzTheme.typography.caption, color = colors.textSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = metrics.minimumTouchTarget)
                .border(1.dp, if (errorText != null) colors.errorForeground else colors.controlBorder, shape)
                .padding(horizontal = metrics.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                visualTransformation = visualTransformationFor(kind, revealed),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(kind)),
                textStyle = SaqzTheme.typography.body.copy(color = colors.textPrimary),
                modifier = Modifier
                    .weight(1f)
                    .semantics { if (errorText != null) error(errorText) },
            )
            if (kind == SaqzInputKind.Password) {
                val toggleLabel = stringResource(
                    if (revealed) Res.string.action_hide_password else Res.string.action_show_password,
                )
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = metrics.minimumTouchTarget, minHeight = metrics.minimumTouchTarget)
                        // The toggle never steals focus, so the field keeps it across the flip.
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClickLabel = toggleLabel,
                            role = Role.Button,
                        ) { revealed = !revealed }
                        .semantics { contentDescription = toggleLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    // Decorative glyph; the accessible name lives on the toggle itself.
                    Text(text = if (revealed) "●" else "○", color = colors.textSecondary)
                }
            }
        }
        if (message != null) {
            Text(
                text = message,
                style = SaqzTheme.typography.caption,
                color = if (errorText != null) colors.errorForeground else colors.textMuted,
            )
        }
    }
}

@Preview
@Composable
private fun SaqzInputPreview() = SaqzTheme {
    SaqzInput(TextFieldValue("nome@exemplo.com"), {}, label = "E-mail", kind = SaqzInputKind.Email)
}
