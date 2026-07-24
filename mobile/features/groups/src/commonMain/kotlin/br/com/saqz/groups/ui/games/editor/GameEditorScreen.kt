package br.com.saqz.groups.ui.games.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzCard
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySlot
import br.com.saqz.groups.presentation.games.editor.GameEditorError
import br.com.saqz.groups.presentation.games.editor.GameEditorForm
import br.com.saqz.groups.presentation.games.editor.GameEditorIntent
import br.com.saqz.groups.presentation.games.editor.GameEditorMode
import br.com.saqz.groups.presentation.games.editor.GameEditorState
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

object GameEditorTags {
    const val Screen = "game-editor-screen"
    const val OneTime = "game-editor-one-time"
    const val Weekly = "game-editor-weekly"
    const val AddSlot = "game-editor-add-slot"
    const val ScopeOnly = "game-editor-scope-only"
    const val ScopeFuture = "game-editor-scope-future"
    const val Submit = "game-editor-submit"
    const val Reload = "game-editor-reload"
    fun field(name: String) = "game-editor-field-$name"
    fun slot(index: Int) = "game-editor-slot-$index"
    fun removeSlot(index: Int) = "game-editor-remove-slot-$index"
    fun weekday(index: Int, weekday: Weekday) = "game-editor-weekday-$index-${weekday.name}"
}

@Composable
fun GameEditorScreen(state: GameEditorState, onIntent: (GameEditorIntent) -> Unit) {
    val draft = state.draft
    val form = draft.form
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(GameEditorTags.Screen),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.sectionVerticalPadding),
    ) {
        Text(
            stringResource(
                if (draft.gameId == null) Res.string.game_editor_new_title
                else Res.string.game_editor_edit_title,
            ),
            style = SaqzTheme.typography.lead,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        ) {
            ModeButton(
                Res.string.game_editor_one_time,
                GameEditorTags.OneTime,
                draft.mode == GameEditorMode.ONE_TIME,
                Modifier.weight(1f),
            ) { onIntent(GameEditorIntent.SetMode(GameEditorMode.ONE_TIME)) }
            ModeButton(
                Res.string.game_editor_weekly,
                GameEditorTags.Weekly,
                draft.mode == GameEditorMode.WEEKLY,
                Modifier.weight(1f),
            ) { onIntent(GameEditorIntent.SetMode(GameEditorMode.WEEKLY)) }
        }
        EditorInput(
            form.title,
            Res.string.game_editor_title,
            state.errorFor("title"),
            GameEditorTags.field("title"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(title = it))) }
        EditorInput(
            form.venue?.name.orEmpty(),
            Res.string.game_editor_venue_name,
            state.errorFor("venue"),
            GameEditorTags.field("venueName"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(venue = form.venue.withName(it)))) }
        EditorInput(
            form.venue?.address.orEmpty(),
            Res.string.game_editor_venue_address,
            state.errorFor("venue"),
            GameEditorTags.field("venueAddress"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(venue = form.venue.withAddress(it)))) }
        EditorInput(
            form.localDate,
            Res.string.game_editor_start_date,
            state.errorFor("localDate"),
            GameEditorTags.field("localDate"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(localDate = it))) }
        if (draft.mode == GameEditorMode.ONE_TIME) {
            EditorInput(
                form.localTime,
                Res.string.game_editor_start_time,
                state.errorFor("localTime"),
                GameEditorTags.field("localTime"),
            ) { onIntent(GameEditorIntent.UpdateForm(form.copy(localTime = it))) }
        } else {
            EditorInput(
                form.localEndDate,
                Res.string.game_editor_end_date,
                state.errorFor("localEndDate"),
                GameEditorTags.field("localEndDate"),
            ) { onIntent(GameEditorIntent.UpdateForm(form.copy(localEndDate = it))) }
        }
        EditorInput(
            form.zoneId,
            Res.string.game_editor_timezone,
            state.errorFor("zoneId"),
            GameEditorTags.field("zoneId"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(zoneId = it))) }
        EditorInput(
            form.durationMinutes,
            Res.string.game_editor_duration,
            state.errorFor("durationMinutes"),
            GameEditorTags.field("duration"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(durationMinutes = it))) }
        EditorInput(
            form.capacity,
            Res.string.game_editor_capacity,
            state.errorFor("capacity"),
            GameEditorTags.field("capacity"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(capacity = it))) }
        if (draft.mode == GameEditorMode.ONE_TIME) {
            EditorInput(
                form.confirmationDeadline,
                Res.string.game_editor_deadline,
                state.errorFor("confirmationDeadline"),
                GameEditorTags.field("deadline"),
            ) { onIntent(GameEditorIntent.UpdateForm(form.copy(confirmationDeadline = it))) }
        }
        EditorInput(
            form.gameFeeBrl,
            Res.string.game_editor_fee,
            state.errorFor("gameFeeBrl"),
            GameEditorTags.field("fee"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(gameFeeBrl = it))) }
        EditorInput(
            form.notes,
            Res.string.game_editor_notes,
            state.errorFor("notes"),
            GameEditorTags.field("notes"),
        ) { onIntent(GameEditorIntent.UpdateForm(form.copy(notes = it))) }
        for (message in state.globalValidationMessages) {
            ErrorText(message)
        }
        if (state.error == GameEditorError.VALIDATION) {
            ErrorText(stringResource(Res.string.game_editor_validation_error))
        }
        if (draft.mode == GameEditorMode.WEEKLY) WeeklySlots(form, state, onIntent)
        if (draft.gameId != null && draft.mode == GameEditorMode.WEEKLY) ScopeChoice(state, onIntent)
        if (state.reloadAvailable) {
            SaqzButton(
                stringResource(Res.string.game_editor_reload),
                { onIntent(GameEditorIntent.Reload) },
                Modifier.fillMaxWidth().testTag(GameEditorTags.Reload),
            )
        }
        SaqzButton(
            stringResource(Res.string.game_editor_submit),
            { onIntent(GameEditorIntent.Submit) },
            Modifier.fillMaxWidth().testTag(GameEditorTags.Submit),
            loading = state.isLoading,
        )
    }
}

@Composable
private fun WeeklySlots(form: GameEditorForm, state: GameEditorState, onIntent: (GameEditorIntent) -> Unit) {
    Text(
        stringResource(Res.string.game_editor_slots),
        style = SaqzTheme.typography.bodyStrong,
        color = SaqzTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
    state.errorFor("slots")?.let { ErrorText(it) }
    form.slots.forEachIndexed { index, slot ->
        SaqzCard(Modifier.fillMaxWidth().testTag(GameEditorTags.slot(index))) {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
                Text(
                    stringResource(Res.string.game_editor_slot, index + 1),
                    style = SaqzTheme.typography.bodyStrong,
                    color = SaqzTheme.colors.textPrimary,
                )
                Weekday.entries.forEach { weekday ->
                    ModeButton(
                        weekday.label(),
                        GameEditorTags.weekday(index, weekday),
                        slot.weekday == weekday,
                        Modifier.fillMaxWidth(),
                    ) { form.updateSlot(index, slot.copy(weekday = weekday), onIntent) }
                }
                SlotInput(index, "title", slot.title, Res.string.game_editor_title, state, form, slot, onIntent) { value ->
                    slot.copy(title = value)
                }
                SlotInput(index, "localTime", slot.localTime, Res.string.game_editor_start_time, state, form, slot, onIntent) { value ->
                    slot.copy(localTime = value)
                }
                SlotInput(index, "durationMinutes", slot.durationMinutes.toString(), Res.string.game_editor_duration, state, form, slot, onIntent) { value ->
                    slot.copy(durationMinutes = value.toIntOrNull() ?: 0)
                }
                SlotInput(index, "venue", slot.venue.name, Res.string.game_editor_venue_name, state, form, slot, onIntent) { value ->
                    slot.copy(venue = slot.venue.copy(name = value))
                }
                SlotInput(index, "venueAddress", slot.venue.address, Res.string.game_editor_venue_address, state, form, slot, onIntent) { value ->
                    slot.copy(venue = slot.venue.copy(address = value))
                }
                SlotInput(index, "capacity", slot.capacity.toString(), Res.string.game_editor_capacity, state, form, slot, onIntent) { value ->
                    slot.copy(capacity = value.toIntOrNull() ?: 0)
                }
                SlotInput(index, "confirmationLeadMinutes", slot.confirmationLeadMinutes.toString(), Res.string.game_editor_confirmation_lead, state, form, slot, onIntent) { value ->
                    slot.copy(confirmationLeadMinutes = value.toIntOrNull() ?: 0)
                }
                SaqzButton(
                    stringResource(Res.string.game_editor_remove_slot),
                    { onIntent(GameEditorIntent.UpdateForm(form.copy(slots = form.slots.filterIndexed { i, _ -> i != index }))) },
                    Modifier.fillMaxWidth().testTag(GameEditorTags.removeSlot(index)),
                    SaqzButtonVariant.Secondary,
                )
            }
        }
    }
    SaqzButton(
        stringResource(Res.string.game_editor_add_slot),
        { onIntent(GameEditorIntent.AddSlot) },
        Modifier.fillMaxWidth().testTag(GameEditorTags.AddSlot),
        SaqzButtonVariant.Secondary,
    )
}

@Composable
private fun ScopeChoice(state: GameEditorState, onIntent: (GameEditorIntent) -> Unit) {
    Text(
        stringResource(Res.string.game_editor_scope_title),
        style = SaqzTheme.typography.bodyStrong,
        color = SaqzTheme.colors.textPrimary,
    )
    state.errorFor("scope")?.let { ErrorText(it) }
    ModeButton(
        Res.string.game_editor_only_this,
        GameEditorTags.ScopeOnly,
        state.draft.scope == SeriesBoundaryScope.OnlyThis,
        Modifier.fillMaxWidth(),
    ) { onIntent(GameEditorIntent.SetScope(SeriesBoundaryScope.OnlyThis)) }
    ModeButton(
        Res.string.game_editor_this_future,
        GameEditorTags.ScopeFuture,
        state.draft.scope == SeriesBoundaryScope.ThisAndFuture,
        Modifier.fillMaxWidth(),
    ) { onIntent(GameEditorIntent.SetScope(SeriesBoundaryScope.ThisAndFuture)) }
}

@Composable
private fun ModeButton(
    label: org.jetbrains.compose.resources.StringResource,
    tag: String,
    selected: Boolean,
    modifier: Modifier,
    click: () -> Unit,
) = ModeButton(stringResource(label), tag, selected, modifier, click)

@Composable
private fun ModeButton(
    label: String,
    tag: String,
    selected: Boolean,
    modifier: Modifier,
    click: () -> Unit,
) = SaqzButton(label, click, modifier.testTag(tag), if (selected) SaqzButtonVariant.Primary else SaqzButtonVariant.Secondary)

@Composable
private fun EditorInput(
    value: String,
    label: org.jetbrains.compose.resources.StringResource,
    error: String?,
    tag: String,
    change: (String) -> Unit,
) = SaqzInput(TextFieldValue(value), { change(it.text) }, stringResource(label), Modifier.testTag(tag), errorText = error)

@Composable
private fun ErrorText(value: String) = Text(value, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground)

@Composable
private fun SlotInput(
    index: Int,
    name: String,
    value: String,
    label: org.jetbrains.compose.resources.StringResource,
    state: GameEditorState,
    form: GameEditorForm,
    slot: WeeklySlot,
    onIntent: (GameEditorIntent) -> Unit,
    update: (String) -> WeeklySlot,
) = EditorInput(
    value,
    label,
    state.errorFor("slots[$index].$name"),
    GameEditorTags.field("slot-$index-$name"),
) { form.updateSlot(index, update(it), onIntent) }

private fun GameEditorState.errorFor(name: String) = fieldErrors[name]?.firstOrNull()

private fun GameVenue?.withName(value: String) = (this ?: GameVenue(null, "", "")).copy(name = value)

private fun GameVenue?.withAddress(value: String) = (this ?: GameVenue(null, "", "")).copy(address = value)

private fun GameEditorForm.updateSlot(index: Int, slot: WeeklySlot, onIntent: (GameEditorIntent) -> Unit) =
    onIntent(GameEditorIntent.UpdateForm(copy(slots = slots.mapIndexed { i, old -> if (i == index) slot else old })))

private fun Weekday.label() = when (this) {
    Weekday.Monday -> "Segunda-feira"
    Weekday.Tuesday -> "Terça-feira"
    Weekday.Wednesday -> "Quarta-feira"
    Weekday.Thursday -> "Quinta-feira"
    Weekday.Friday -> "Sexta-feira"
    Weekday.Saturday -> "Sábado"
    Weekday.Sunday -> "Domingo"
}