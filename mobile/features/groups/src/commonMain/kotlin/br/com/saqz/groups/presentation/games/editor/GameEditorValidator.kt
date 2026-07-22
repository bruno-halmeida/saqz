package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.core.common.formatting.parseBrlToCents

internal fun validateGameEditor(draft: GameEditorDraft): Map<String, List<String>> = buildMap {
    fun required(name: String, value: String) {
        if (value.isBlank()) put(name, listOf("is required"))
    }

    val form = draft.form
    required("title", form.title)
    if (form.venue == null) put("venue", listOf("is required"))
    required("localDate", form.localDate)
    required("zoneId", form.zoneId)
    if (form.localEndDate.isNotBlank() && form.localEndDate < form.localDate) {
        put("localEndDate", listOf("must not be before start date"))
    }
    if (form.durationMinutes.toIntOrNull() !in 15..480) {
        put("durationMinutes", listOf("must be between 15 and 480"))
    }
    if (form.capacity.toIntOrNull() !in 2..100) {
        put("capacity", listOf("must be between 2 and 100"))
    }
    if (form.gameFeeBrl.isNotBlank() && parseBrlToCents(form.gameFeeBrl) == null) {
        put("gameFeeBrl", listOf("must be a valid BRL amount"))
    }
    if (form.notes.trim().let { it.isNotEmpty() && it.length !in 2..500 }) {
        put("notes", listOf("must be between 2 and 500 characters"))
    }
    if (draft.mode == GameEditorMode.ONE_TIME) {
        required("localTime", form.localTime)
        required("startsAt", form.startsAt)
        required("confirmationDeadline", form.confirmationDeadline)
        if (form.startsAt.isNotBlank() && form.confirmationDeadline > form.startsAt) {
            put("confirmationDeadline", listOf("must not be after start"))
        }
    } else {
        if (form.slots.isEmpty()) put("slots", listOf("must not be empty"))
        form.slots.forEachIndexed { index, slot ->
            if (slot.localTime.isBlank()) put("slots[$index].localTime", listOf("is required"))
            if (slot.durationMinutes !in 15..480) {
                put("slots[$index].durationMinutes", listOf("must be between 15 and 480"))
            }
            if (slot.capacity !in 2..100) put("slots[$index].capacity", listOf("must be between 2 and 100"))
            if (slot.confirmationLeadMinutes !in 0..10080) {
                put("slots[$index].confirmationLeadMinutes", listOf("must be between 0 and 10080"))
            }
            if (slot.title.isBlank()) put("slots[$index].title", listOf("is required"))
            if (slot.venue.name.isBlank() || slot.venue.address.isBlank()) {
                put("slots[$index].venue", listOf("is required"))
            }
        }
        if (draft.gameId != null && draft.scope == null) put("scope", listOf("is required"))
    }
}
