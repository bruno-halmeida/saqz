package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.groups.presentation.gamedetail.GameDetailConfirmedUi
import br.com.saqz.groups.resources.position_group_full
import br.com.saqz.groups.resources.position_group_header
import br.com.saqz.groups.resources.position_without_group
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GamePositionPickerUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionPurpose
import br.com.saqz.groups.presentation.gamedetail.GamePositionUi
import br.com.saqz.groups.presentation.ui.pluralLabel
import br.com.saqz.groups.presentation.ui.singularLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.position_force_body
import br.com.saqz.groups.resources.position_force_confirm
import br.com.saqz.groups.resources.position_force_failed
import br.com.saqz.groups.resources.position_force_keep
import br.com.saqz.groups.resources.position_force_reason
import br.com.saqz.groups.resources.position_force_title
import br.com.saqz.groups.resources.position_meta_full
import br.com.saqz.groups.resources.position_meta_open
import br.com.saqz.groups.resources.position_meta_waiting
import br.com.saqz.groups.resources.position_missing
import br.com.saqz.groups.resources.position_picker_suggested
import br.com.saqz.groups.resources.position_picker_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GamePositionTags {
    const val Sheet = "game-position-sheet"
    const val Submit = "game-position-submit"
    const val Failed = "game-position-failed"
    const val ForceConfirm = "game-position-force-confirm"
    const val ForceSheet = "game-position-force-sheet"

    fun choice(prefix: String, position: AthletePosition) = "$prefix-${position.name.lowercase()}"

    fun header(position: AthletePosition) = "game-position-header-${position.name.lowercase()}"

    fun force(rowId: String) = "game-position-force-$rowId"
}

/** "2/2 · cheia", "1/2 · falta 1", "2/2 · cheia · 1 na espera". */
@Composable
internal fun GamePositionUi.metaLabel(): String {
    val base = if (full) {
        stringResource(Res.string.position_meta_full, confirmed, slots)
    } else {
        val missingLabel = pluralStringResource(Res.plurals.position_missing, missing, missing)
        stringResource(Res.string.position_meta_open, confirmed, slots, missingLabel)
    }
    return if (waitlisted > 0) stringResource(Res.string.position_meta_waiting, base, waitlisted) else base
}

/**
 * Posições do jogo como linhas escolhíveis (seletor do "Vou", convidado e "Colocar alguém"). A sugerida (do perfil)
 * ganha uma etiqueta; a selecionada, o visto. Só entram posições com vaga configurada.
 */
@Composable
internal fun GamePositionChoices(
    positions: List<GamePositionUi>,
    selected: AthletePosition?,
    suggested: AthletePosition?,
    enabled: Boolean,
    tagPrefix: String,
    onSelect: (AthletePosition) -> Unit,
) {
    Column {
        positions.filter { it.slots > 0 }.forEach { row ->
            val isSelected = row.position == selected
            SaqzMemberRow(
                name = row.position.singularLabel(),
                meta = row.metaLabel(),
                modifier = Modifier
                    .testTag(GamePositionTags.choice(tagPrefix, row.position))
                    .semantics { this.selected = isSelected }
                    .then(if (isSelected) Modifier.background(SaqzTheme.colors.surfaceSoft) else Modifier),
                onClick = if (enabled) { { onSelect(row.position) } } else null,
                trailing = when {
                    isSelected -> { { SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.primary) } }
                    row.position == suggested -> { { SuggestedChip() } }
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun SuggestedChip() =
    SaqzStatusChip(stringResource(Res.string.position_picker_suggested), tone = SaqzChipTone.Brand)

/** Folha do "Forçar entrada" do gestor (confirma); o "Vou" de quem olha abre a tela de posição (DEB-107). */
@Composable
internal fun GamePositionPickerSheet(picker: GamePositionPickerUi, onIntent: (GameDetailIntent) -> Unit) {
    when (val purpose = picker.purpose) {
        is GamePositionPurpose.Force -> {
            val reason = stringResource(Res.string.position_force_reason)
            SaqzBottomSheet(
                open = true,
                onClose = { onIntent(GameDetailIntent.DismissPositionPicker) },
                modifier = Modifier.testTag(GamePositionTags.ForceSheet),
                title = stringResource(Res.string.position_force_title, purpose.name),
                description = stringResource(Res.string.position_force_body, purpose.position.pluralLabel()),
                splitFooter = {
                    SaqzButton(
                        stringResource(Res.string.position_force_keep),
                        { onIntent(GameDetailIntent.DismissPositionPicker) },
                        Modifier.weight(1f),
                        SaqzButtonVariant.Secondary,
                        enabled = !picker.sending,
                    )
                    SaqzButton(
                        stringResource(Res.string.position_force_confirm),
                        { onIntent(GameDetailIntent.ConfirmForceEntry(reason)) },
                        Modifier.weight(1f).testTag(GamePositionTags.ForceConfirm),
                        loading = picker.sending,
                        enabled = !picker.sending,
                    )
                },
            ) {
                if (picker.failed) {
                    Text(
                        text = stringResource(Res.string.position_force_failed),
                        color = SaqzTheme.colors.errorForeground,
                        style = SaqzTheme.typography.support,
                        modifier = Modifier.padding(top = SaqzTheme.metrics.blockGap).testTag(GamePositionTags.Failed),
                    )
                }
            }
        }
    }
}
