package br.com.saqz.groups.ui.athlete

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzBottomSheet
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzEmptyState
import br.com.saqz.designsystem.component.SaqzErrorState
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzListItem
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteFinancialStatus
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.presentation.athlete.AthleteEditDraft
import br.com.saqz.groups.presentation.athlete.AthleteRemovalDraft
import br.com.saqz.groups.presentation.athlete.AthleteRosterIntent
import br.com.saqz.groups.presentation.athlete.AthleteRosterState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.roster_edit_active
import br.com.saqz.groups.resources.roster_edit_error
import br.com.saqz.groups.resources.roster_edit_position_none
import br.com.saqz.groups.resources.roster_edit_save
import br.com.saqz.groups.resources.roster_edit_title
import br.com.saqz.groups.resources.roster_empty
import br.com.saqz.groups.resources.roster_filter_all
import br.com.saqz.groups.resources.roster_filter_avulso
import br.com.saqz.groups.resources.roster_filter_em_dia
import br.com.saqz.groups.resources.roster_filter_mensalista
import br.com.saqz.groups.resources.roster_filter_pendente
import br.com.saqz.groups.resources.roster_inactive
import br.com.saqz.groups.resources.roster_remove
import br.com.saqz.groups.resources.roster_remove_body
import br.com.saqz.groups.resources.roster_remove_confirm
import br.com.saqz.groups.resources.roster_remove_error
import br.com.saqz.groups.resources.roster_remove_title
import br.com.saqz.groups.resources.roster_search
import br.com.saqz.groups.resources.roster_show_inactive
import br.com.saqz.groups.resources.roster_status_desconhecido
import org.jetbrains.compose.resources.stringResource

object AthleteRosterTags {
    const val List = "athlete-roster-list"
    const val Search = "athlete-roster-search"
    const val EditSheet = "athlete-roster-edit"
    const val EditSave = "athlete-roster-edit-save"
    const val RemoveDialog = "athlete-roster-remove"
    const val RemoveConfirm = "athlete-roster-remove-confirm"
    fun entry(userId: String) = "athlete-roster-entry-$userId"
}

@Composable
fun AthleteRosterScreen(
    state: AthleteRosterState,
    canManage: Boolean,
    onIntent: (AthleteRosterIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SaqzInput(
            value = TextFieldValue(state.search),
            onValueChange = { onIntent(AthleteRosterIntent.Search(it.text)) },
            label = stringResource(Res.string.roster_search),
            modifier = Modifier.testTag(AthleteRosterTags.Search),
        )
        FilterRow(state, onIntent)
        when {
            state.loading && state.athletes.isEmpty() -> SaqzLoadingState()
            state.failed -> SaqzErrorState(onRetry = { onIntent(AthleteRosterIntent.Reload) })
            state.athletes.isEmpty() -> SaqzEmptyState(Modifier.padding(top = 24.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().testTag(AthleteRosterTags.List),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.athletes, key = AthleteRosterEntry::userId) { entry ->
                    RosterEntryRow(entry, canManage, onIntent)
                }
            }
        }
    }
    state.edit?.let { edit -> if (canManage) AthleteEditSheet(edit, onIntent) }
    state.removal?.let { removal -> if (canManage) AthleteRemovalDialog(removal, onIntent) }
}

@Composable
private fun FilterRow(state: AthleteRosterState, onIntent: (AthleteRosterIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(stringResource(Res.string.roster_filter_all), state.typeFilter == null && state.financialFilter == null) {
            onIntent(AthleteRosterIntent.FilterType(null))
            onIntent(AthleteRosterIntent.FilterFinancial(null))
        }
        FilterChip(stringResource(Res.string.roster_filter_mensalista), state.typeFilter == AthleteMembershipType.MENSALISTA) {
            onIntent(AthleteRosterIntent.FilterType(AthleteMembershipType.MENSALISTA.takeIf { state.typeFilter != it }))
        }
        FilterChip(stringResource(Res.string.roster_filter_avulso), state.typeFilter == AthleteMembershipType.AVULSO) {
            onIntent(AthleteRosterIntent.FilterType(AthleteMembershipType.AVULSO.takeIf { state.typeFilter != it }))
        }
        FilterChip(stringResource(Res.string.roster_filter_pendente), state.financialFilter == AthleteFinancialStatus.PENDENTE) {
            onIntent(AthleteRosterIntent.FilterFinancial(AthleteFinancialStatus.PENDENTE.takeIf { state.financialFilter != it }))
        }
        AthletePosition.entries.forEach { position ->
            FilterChip(stringResource(position.labelRes()), state.positionFilter == position) {
                onIntent(AthleteRosterIntent.FilterPosition(position.takeIf { state.positionFilter != it }))
            }
        }
        FilterChip(stringResource(Res.string.roster_show_inactive), state.includeInactive) {
            onIntent(AthleteRosterIntent.ShowInactive(!state.includeInactive))
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    SaqzButton(
        label = label,
        onClick = onClick,
        variant = if (selected) SaqzButtonVariant.Primary else SaqzButtonVariant.Secondary,
    )
}

@Composable
private fun RosterEntryRow(
    entry: AthleteRosterEntry,
    canManage: Boolean,
    onIntent: (AthleteRosterIntent) -> Unit,
) {
    SaqzListItem(
        headline = entry.displayName,
        modifier = Modifier.testTag(AthleteRosterTags.entry(entry.userId)),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SaqzBadge(
                        label = stringResource(
                            when (entry.membershipType) {
                                AthleteMembershipType.MENSALISTA -> Res.string.roster_filter_mensalista
                                AthleteMembershipType.AVULSO -> Res.string.roster_filter_avulso
                            },
                        ),
                        variant = SaqzBadgeVariant.Info,
                    )
                    SaqzBadge(
                        label = stringResource(
                            when (entry.financialStatus) {
                                AthleteFinancialStatus.EM_DIA -> Res.string.roster_filter_em_dia
                                AthleteFinancialStatus.PENDENTE -> Res.string.roster_filter_pendente
                                AthleteFinancialStatus.DESCONHECIDO -> Res.string.roster_status_desconhecido
                            },
                        ),
                        variant = when (entry.financialStatus) {
                            AthleteFinancialStatus.EM_DIA -> SaqzBadgeVariant.Success
                            AthleteFinancialStatus.PENDENTE -> SaqzBadgeVariant.Warning
                            AthleteFinancialStatus.DESCONHECIDO -> SaqzBadgeVariant.Neutral
                        },
                    )
                    if (!entry.active) {
                        SaqzBadge(stringResource(Res.string.roster_inactive), SaqzBadgeVariant.Error)
                    }
                }
                val details = listOfNotNull(
                    entry.position?.let { stringResource(it.labelRes()) },
                    entry.phone,
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = SaqzTheme.typography.caption,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
            }
        },
        onClick = if (canManage) {
            { onIntent(AthleteRosterIntent.OpenEdit(entry.userId)) }
        } else {
            null
        },
    )
}

@Composable
private fun AthleteEditSheet(edit: AthleteEditDraft, onIntent: (AthleteRosterIntent) -> Unit) {
    SaqzBottomSheet(
        title = stringResource(Res.string.roster_edit_title),
        onCloseRequest = { onIntent(AthleteRosterIntent.CloseEdit) },
        primaryAction = {
            SaqzButton(
                label = stringResource(Res.string.roster_edit_save),
                onClick = { onIntent(AthleteRosterIntent.SaveEdit) },
                loading = edit.saving,
                modifier = Modifier.fillMaxWidth().testTag(AthleteRosterTags.EditSave),
            )
        },
        modifier = Modifier.testTag(AthleteRosterTags.EditSheet),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(edit.displayName, style = SaqzTheme.typography.bodyStrong, color = SaqzTheme.colors.textPrimary)
            if (edit.failed) {
                Text(
                    stringResource(Res.string.roster_edit_error),
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.errorForeground,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(Res.string.roster_filter_mensalista), edit.membershipType == AthleteMembershipType.MENSALISTA) {
                    onIntent(AthleteRosterIntent.EditType(AthleteMembershipType.MENSALISTA))
                }
                FilterChip(stringResource(Res.string.roster_filter_avulso), edit.membershipType == AthleteMembershipType.AVULSO) {
                    onIntent(AthleteRosterIntent.EditType(AthleteMembershipType.AVULSO))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(Res.string.roster_edit_position_none), edit.position == null) {
                    onIntent(AthleteRosterIntent.EditPosition(null))
                }
                AthletePosition.entries.forEach { position ->
                    FilterChip(stringResource(position.labelRes()), edit.position == position) {
                        onIntent(AthleteRosterIntent.EditPosition(position))
                    }
                }
            }
            FilterChip(stringResource(Res.string.roster_edit_active), edit.active) {
                onIntent(AthleteRosterIntent.EditActive(!edit.active))
            }
            SaqzButton(
                label = stringResource(Res.string.roster_remove),
                onClick = { onIntent(AthleteRosterIntent.RequestRemoval(edit.userId)) },
                variant = SaqzButtonVariant.Secondary,
                enabled = !edit.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AthleteRemovalDialog(removal: AthleteRemovalDraft, onIntent: (AthleteRosterIntent) -> Unit) {
    SaqzDialog(
        title = stringResource(Res.string.roster_remove_title),
        onCloseRequest = { onIntent(AthleteRosterIntent.CancelRemoval) },
        primaryAction = {
            SaqzButton(
                label = stringResource(Res.string.roster_remove_confirm),
                onClick = { onIntent(AthleteRosterIntent.ConfirmRemoval) },
                loading = removal.removing,
                modifier = Modifier.fillMaxWidth().testTag(AthleteRosterTags.RemoveConfirm),
            )
        },
        modifier = Modifier.testTag(AthleteRosterTags.RemoveDialog),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.roster_remove_body, removal.displayName),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textSecondary,
            )
            if (removal.failed) {
                Text(
                    stringResource(Res.string.roster_remove_error),
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.errorForeground,
                )
            }
        }
    }
}
