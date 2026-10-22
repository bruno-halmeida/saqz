package br.com.saqz.groups.presentation.attendancehistory

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.AttendanceHistoryEntry
import br.com.saqz.groups.domain.game.AttendanceHistoryGateway
import br.com.saqz.groups.domain.game.AttendanceHistoryKind
import br.com.saqz.groups.domain.game.AttendanceHistorySource
import br.com.saqz.groups.domain.game.AttendanceHistoryStatus
import br.com.saqz.groups.presentation.game.gameLongDateLabel
import br.com.saqz.groups.presentation.game.gameMenuTitle
import br.com.saqz.groups.presentation.game.gameTimeLabel
import br.com.saqz.groups.presentation.photo.memberPhoto
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_weekday_short_friday
import br.com.saqz.groups.resources.group_weekday_short_monday
import br.com.saqz.groups.resources.group_weekday_short_saturday
import br.com.saqz.groups.resources.group_weekday_short_sunday
import br.com.saqz.groups.resources.group_weekday_short_thursday
import br.com.saqz.groups.resources.group_weekday_short_tuesday
import br.com.saqz.groups.resources.group_weekday_short_wednesday
import br.com.saqz.groups.resources.history_after_deadline
import br.com.saqz.groups.resources.history_by_organizer
import br.com.saqz.groups.resources.history_by_system
import br.com.saqz.groups.resources.history_confirmed
import br.com.saqz.groups.resources.history_day
import br.com.saqz.groups.resources.history_declined
import br.com.saqz.groups.resources.history_freed_by
import br.com.saqz.groups.resources.history_freed_by_guest
import br.com.saqz.groups.resources.history_game_full
import br.com.saqz.groups.resources.history_gave_up
import br.com.saqz.groups.resources.history_guest_joined
import br.com.saqz.groups.resources.history_guest_left
import br.com.saqz.groups.resources.history_guest_of
import br.com.saqz.groups.resources.history_left_waitlist
import br.com.saqz.groups.resources.history_meta
import br.com.saqz.groups.resources.history_organizer_added
import br.com.saqz.groups.resources.history_organizer_removed
import br.com.saqz.groups.resources.history_organizer_waitlisted
import br.com.saqz.groups.resources.history_promoted
import br.com.saqz.groups.resources.history_reason
import br.com.saqz.groups.resources.history_status_confirmed
import br.com.saqz.groups.resources.history_status_declined
import br.com.saqz.groups.resources.history_status_waitlisted
import br.com.saqz.groups.resources.history_subtitle
import br.com.saqz.groups.resources.history_system_removed
import br.com.saqz.groups.resources.history_system_waitlisted
import br.com.saqz.groups.resources.history_today
import br.com.saqz.groups.resources.history_waitlisted
import br.com.saqz.groups.resources.history_was_confirmed
import br.com.saqz.groups.resources.history_yesterday
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Instant

internal class AttendanceHistoryViewModel(
    val groupId: String,
    val gameId: String,
    private val gateway: AttendanceHistoryGateway,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val now: () -> Instant = { Clock.System.now() },
) : MviViewModel<AttendanceHistoryState, AttendanceHistoryIntent, Nothing>(AttendanceHistoryState()) {

    /** Cada carga da primeira página invalida as respostas que ainda estão a caminho. */
    private var generation = 0
    private var cursor: String? = null
    private var entries: List<AttendanceHistoryEntry> = emptyList()

    /** O gestor puxou: a falha da carga que está no ar (a dele ou a de carona) vira aviso. */
    private var pulled = false

    init {
        load()
    }

    override fun handleIntent(intent: AttendanceHistoryIntent) {
        when (intent) {
            AttendanceHistoryIntent.Retry -> load()
            AttendanceHistoryIntent.Refresh -> if (!state.value.isLoading && !state.value.loadFailed) refresh()
            AttendanceHistoryIntent.LoadMore -> loadMore()
            AttendanceHistoryIntent.PullRefresh -> pullRefresh()
            AttendanceHistoryIntent.DismissRefreshFailed -> update { it.copy(refreshFailed = false) }
        }
    }

    private fun load() {
        val current = ++generation
        update { AttendanceHistoryState(isLoading = true) }
        viewModelScope.launch {
            val page = gateway.page(GroupId(groupId), gameId, cursor = null)
            if (current != generation) return@launch
            when (page) {
                is SaqzResult.Success -> {
                    cursor = page.value.nextCursor
                    entries = page.value.entries
                    val rows = rows()
                    update { AttendanceHistoryState(isLoading = false, rows = rows, canLoadMore = cursor != null) }
                }
                is SaqzResult.Failure -> update {
                    AttendanceHistoryState(isLoading = false, loadFailed = true, error = page.error.toUiError())
                }
            }
        }
    }

    /** GER-002: puxão com carga no ar pega carona nela; sem carga no ar, é a atualização por baixo. */
    private fun pullRefresh() {
        val current = state.value
        if (current.isLoading || current.loadFailed) return
        pulled = true
        when {
            current.isRefreshing -> update { it.copy(refreshFailed = false) }
            current.isLoadingMore -> update { it.copy(isRefreshing = true, refreshFailed = false) }
            else -> {
                update { it.copy(refreshFailed = false) }
                refresh()
            }
        }
    }

    /**
     * Busca de novo até a quantidade já carregada: o que é novo entra em cima, a lista não encolhe nem perde a
     * posição. Falha não mexe na lista.
     */
    private fun refresh() {
        if (state.value.isLoadingMore || state.value.isRefreshing) return
        val loaded = entries
        val current = ++generation
        update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val fetched = mutableListOf<AttendanceHistoryEntry>()
            var next: String? = null
            var first = true
            while (first || (next != null && fetched.size < loaded.size)) {
                first = false
                when (val page = gateway.page(GroupId(groupId), gameId, cursor = next)) {
                    is SaqzResult.Success -> {
                        fetched += page.value.entries
                        next = page.value.nextCursor
                    }
                    is SaqzResult.Failure -> {
                        if (current == generation) update { it.copy(isRefreshing = false, refreshFailed = pulled) }
                        pulled = false
                        return@launch
                    }
                }
            }
            if (current != generation) return@launch
            val freshIds = fetched.mapTo(HashSet()) { it.id }
            entries = fetched.distinctBy { it.id } + loaded.filterNot { it.id in freshIds }
            cursor = next
            pulled = false
            val rows = rows()
            update { it.copy(rows = rows, isRefreshing = false, canLoadMore = cursor != null) }
        }
    }

    private fun loadMore() {
        val current = state.value
        val after = cursor
        if (current.isLoading || current.isLoadingMore || current.isRefreshing || after == null) return
        val loadAtStart = generation
        update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            val page = gateway.page(GroupId(groupId), gameId, cursor = after)
            if (loadAtStart != generation) return@launch
            when (page) {
                is SaqzResult.Success -> {
                    cursor = page.value.nextCursor
                    val known = entries.mapTo(HashSet()) { it.id }
                    entries = entries + page.value.entries.filterNot { it.id in known }
                    val rows = rows()
                    update { it.copy(rows = rows, isLoadingMore = false, canLoadMore = cursor != null, isRefreshing = false) }
                }
                is SaqzResult.Failure -> update {
                    it.copy(isLoadingMore = false, loadMoreFailed = true, isRefreshing = false, refreshFailed = pulled)
                }
            }
            pulled = false
        }
    }

    private suspend fun rows(): List<AttendanceHistoryRowUi> {
        val today = now().toLocalDateTime(zone).date
        // A linha mais recente de cada pessoa diz onde ela está agora (iniciais em azul quando vai).
        val latest = HashMap<Pair<String, Int>, AttendanceHistoryStatus>()
        entries.forEach { latest.getOrPut(it.memberId to it.guestSeq) { it.newStatus } }
        return entries.mapNotNull { entry ->
            val at = runCatching { Instant.parse(entry.occurredAt) }.getOrNull() ?: return@mapNotNull null
            val local = at.toLocalDateTime(zone)
            entry.toRow(
                dayHeader = dayHeader(local.date, today),
                time = local.gameTimeLabel(),
                currentlyGoing = latest[entry.memberId to entry.guestSeq] == AttendanceHistoryStatus.Confirmed,
            )
        }
    }

    private suspend fun AttendanceHistoryEntry.toRow(dayHeader: String, time: String, currentlyGoing: Boolean): AttendanceHistoryRowUi {
        val guest = guestSeq > 0
        val detail = detail()
        return AttendanceHistoryRowUi(
            id = id,
            dayHeader = dayHeader,
            avatarName = memberName,
            photo = if (guest) null else memberPhoto(groupId, memberId, photoTag),
            guest = guest,
            currentlyGoing = currentlyGoing,
            title = title(),
            meta = detail?.let { getString(Res.string.history_meta, time, it) } ?: time,
            reason = reason?.let { getString(Res.string.history_reason, it) },
            statusLabel = getString(newStatus.labelResource()),
            tone = when (newStatus) {
                AttendanceHistoryStatus.Confirmed -> AttendanceHistoryTone.Going
                AttendanceHistoryStatus.Waitlisted -> AttendanceHistoryTone.Waiting
                AttendanceHistoryStatus.Declined -> AttendanceHistoryTone.NotGoing
            },
        )
    }

    /** "Carla Dias desistiu", "Bruno tirou Pedro Lima do jogo". */
    private suspend fun AttendanceHistoryEntry.title(): String {
        // Convidado é "Convidado(a) de Ana"; o nome dele não vai na frase.
        val subject = if (guestSeq > 0) {
            getString(Res.string.history_guest_of, firstName(memberName)).replaceFirstChar { it.titlecase() }
        } else {
            memberName
        }
        val organizer = firstName(actorName)
        return when (kind) {
            AttendanceHistoryKind.OrganizerAdded -> getString(Res.string.history_organizer_added, organizer, subject)
            AttendanceHistoryKind.OrganizerWaitlisted -> getString(Res.string.history_organizer_waitlisted, organizer, subject)
            AttendanceHistoryKind.OrganizerRemoved -> getString(Res.string.history_organizer_removed, organizer, subject)
            AttendanceHistoryKind.GuestJoined -> getString(Res.string.history_guest_joined, memberName)
            AttendanceHistoryKind.GuestLeft -> getString(Res.string.history_guest_left, memberName)
            else -> getString(kind.subjectResource(), subject)
        }
    }

    /** O que vem depois da hora: quem fez (nada quando foi a própria pessoa) ou o porquê. */
    private suspend fun AttendanceHistoryEntry.detail(): String? = when {
        kind == AttendanceHistoryKind.Promoted && freedByName != null -> getString(
            if (freedByGuest) Res.string.history_freed_by_guest else Res.string.history_freed_by,
            firstName(freedByName.orEmpty()),
        )
        source == AttendanceHistorySource.System -> getString(Res.string.history_by_system)
        source == AttendanceHistorySource.Organizer -> getString(Res.string.history_by_organizer)
        guestSeq > 0 -> getString(Res.string.history_guest_of, firstName(memberName))
        kind == AttendanceHistoryKind.GaveUp -> getString(Res.string.history_was_confirmed)
        kind == AttendanceHistoryKind.Waitlisted ->
            getString(if (afterDeadline) Res.string.history_after_deadline else Res.string.history_game_full)
        else -> null
    }

    /** "HOJE", "ONTEM", "TER, 20 DE OUTUBRO" (o cabeçalho é caixa-alta, como os da agenda). */
    private suspend fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
        today -> getString(Res.string.history_today)
        today.minus(DatePeriod(days = 1)) -> getString(Res.string.history_yesterday)
        else -> getString(Res.string.history_day, getString(date.dayOfWeek.shortResource()), date.gameLongDateLabel())
    }.uppercase()
}

/** Frase de quem mudou sozinho (ou pelo sistema): "%1$s confirmou". */
private fun AttendanceHistoryKind.subjectResource(): StringResource = when (this) {
    AttendanceHistoryKind.Waitlisted -> Res.string.history_waitlisted
    AttendanceHistoryKind.Declined -> Res.string.history_declined
    AttendanceHistoryKind.GaveUp -> Res.string.history_gave_up
    AttendanceHistoryKind.LeftWaitlist -> Res.string.history_left_waitlist
    AttendanceHistoryKind.Promoted -> Res.string.history_promoted
    AttendanceHistoryKind.SystemRemoved -> Res.string.history_system_removed
    AttendanceHistoryKind.SystemWaitlisted -> Res.string.history_system_waitlisted
    else -> Res.string.history_confirmed
}

private fun AttendanceHistoryStatus.labelResource(): StringResource = when (this) {
    AttendanceHistoryStatus.Confirmed -> Res.string.history_status_confirmed
    AttendanceHistoryStatus.Waitlisted -> Res.string.history_status_waitlisted
    AttendanceHistoryStatus.Declined -> Res.string.history_status_declined
}

/** "Jogo de terça, 28/07 · 19h30" (barra do histórico de respostas, JOGO-012). */
internal suspend fun LocalDateTime.gameHistorySubtitle(): String =
    getString(Res.string.history_subtitle, date.gameMenuTitle(), gameTimeLabel())

private fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { name }

private fun DayOfWeek.shortResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.group_weekday_short_monday
    DayOfWeek.TUESDAY -> Res.string.group_weekday_short_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.group_weekday_short_wednesday
    DayOfWeek.THURSDAY -> Res.string.group_weekday_short_thursday
    DayOfWeek.FRIDAY -> Res.string.group_weekday_short_friday
    DayOfWeek.SATURDAY -> Res.string.group_weekday_short_saturday
    DayOfWeek.SUNDAY -> Res.string.group_weekday_short_sunday
}
