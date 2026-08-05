package br.com.saqz.groups.presentation.home

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeLastCompletedGame
import br.com.saqz.groups.domain.home.HomeMemberGroup
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeNextGame
import br.com.saqz.groups.domain.home.HomeOwnAttendance
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_confirmed_summary
import br.com.saqz.groups.resources.home_date
import br.com.saqz.groups.resources.home_deadline_date
import br.com.saqz.groups.resources.home_deadline_today
import br.com.saqz.groups.resources.home_deadline_tomorrow
import br.com.saqz.groups.resources.home_game_date_time
import br.com.saqz.groups.resources.home_group_meta
import br.com.saqz.groups.resources.home_last_game_played
import br.com.saqz.groups.resources.home_last_game_summary
import br.com.saqz.groups.resources.home_last_game_title
import br.com.saqz.groups.resources.home_month_april
import br.com.saqz.groups.resources.home_month_august
import br.com.saqz.groups.resources.home_month_december
import br.com.saqz.groups.resources.home_month_february
import br.com.saqz.groups.resources.home_month_january
import br.com.saqz.groups.resources.home_month_july
import br.com.saqz.groups.resources.home_month_june
import br.com.saqz.groups.resources.home_month_march
import br.com.saqz.groups.resources.home_month_may
import br.com.saqz.groups.resources.home_month_november
import br.com.saqz.groups.resources.home_month_october
import br.com.saqz.groups.resources.home_month_september
import br.com.saqz.groups.resources.home_subtitle_confirmed
import br.com.saqz.groups.resources.home_subtitle_declined
import br.com.saqz.groups.resources.home_subtitle_no_game
import br.com.saqz.groups.resources.home_subtitle_no_response
import br.com.saqz.groups.resources.home_subtitle_waitlisted
import br.com.saqz.groups.resources.home_subtitle_waitlist_avulso
import br.com.saqz.groups.resources.home_subtitle_waitlist_reserva
import br.com.saqz.groups.resources.home_time
import br.com.saqz.groups.resources.home_waitlist_reserva_bell
import br.com.saqz.groups.resources.home_weekday_friday
import br.com.saqz.groups.resources.home_weekday_friday_short
import br.com.saqz.groups.resources.home_weekday_monday
import br.com.saqz.groups.resources.home_weekday_monday_short
import br.com.saqz.groups.resources.home_weekday_saturday
import br.com.saqz.groups.resources.home_weekday_saturday_short
import br.com.saqz.groups.resources.home_weekday_sunday
import br.com.saqz.groups.resources.home_weekday_sunday_short
import br.com.saqz.groups.resources.home_weekday_thursday
import br.com.saqz.groups.resources.home_weekday_thursday_short
import br.com.saqz.groups.resources.home_weekday_tuesday
import br.com.saqz.groups.resources.home_weekday_tuesday_short
import br.com.saqz.groups.resources.home_weekday_wednesday
import br.com.saqz.groups.resources.home_weekday_wednesday_short
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HomeViewModel(
    private val homeGateway: HomeGateway,
    private val athleteGateway: AthleteGateway,
    private val attendanceGateway: AttendanceGateway,
    private val now: GroupNowPort,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    private var loadGeneration = 0L
    private var responseGeneration = 0L

    init {
        load()
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> load()
            is HomeIntent.Respond -> respond(intent.intent)
            HomeIntent.DismissToast -> update { it.copy(toast = null) }
            HomeIntent.OpenGroups -> emit(HomeEffect.OpenGroups)
            is HomeIntent.OpenGroup -> emit(HomeEffect.OpenGroup(intent.groupId))
            is HomeIntent.OpenGame -> emit(HomeEffect.OpenGame(intent.groupId, intent.gameId))
        }
    }

    private fun load(softRefresh: Boolean = false) {
        val generation = ++loadGeneration
        responseGeneration++
        update {
            if (softRefresh) {
                it.copy(loadFailed = false, error = null, responseFailed = false)
            } else {
                it.copy(
                    isLoading = true,
                    loadFailed = false,
                    error = null,
                    responding = false,
                    responseFailed = false,
                )
            }
        }
        viewModelScope.launch {
            val homeRequest = async { homeGateway.read() }
            val profileRequest = async { athleteGateway.ownProfile() }
            val homeResult = homeRequest.await()
            val profileResult = profileRequest.await()
            if (generation < loadGeneration) return@launch

            when (homeResult) {
                is SaqzResult.Failure -> if (!softRefresh) {
                    showFailure(generation, homeResult.error.toUiError())
                }
                is SaqzResult.Success -> when (profileResult) {
                    is SaqzResult.Failure -> if (!softRefresh) {
                        showFailure(generation, profileResult.error.toUiError())
                    }
                    is SaqzResult.Success -> {
                        val member = homeResult.value.member.toUi()
                        if (generation < loadGeneration) return@launch
                        update {
                            it.copy(
                                isLoading = false,
                                loadFailed = false,
                                error = null,
                                displayName = profileResult.value.displayName.firstName(),
                                home = homeResult.value,
                                member = member,
                                responding = false,
                                responseFailed = false,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        val game = current.home?.member?.nextGame ?: return
        val member = current.member ?: return
        val gameUi = member.nextGame ?: return
        val deadline = runCatching { Instant.parse(game.confirmationDeadline) }.getOrNull() ?: return
        if (now.now() >= deadline) return
        val isWaitlisted = game.ownAttendance?.status == AttendanceStatus.Waitlisted
        // Confirm não faz sentido a partir da espera; Decline é o caminho de "Sair da
        // reserva"/"Sair da lista" e por isso é liberado mesmo em Waitlisted.
        if (current.responding || !gameUi.confirmationOpen || (isWaitlisted && intent != AttendanceIntent.Decline)) return

        val generation = ++responseGeneration
        val loadAtStart = loadGeneration
        val previousHome = checkNotNull(current.home)
        val previousMember = member
        val optimisticStatus = game.optimisticStatus(intent)
        // O kind é derivado de `membershipType`/`mensalistaPriority` (o ViewModel já tem
        // os dois no `HomeNextGame`), não do `ownAttendance` — quem confirma e cai direto
        // na lista sem attendance prévia precisa ver o kind certo já no update otimista.
        val optimisticKind = if (optimisticStatus == AttendanceStatus.Waitlisted) game.waitlistKind() else null
        update {
            it.copy(
                home = previousHome.copy(
                    member = previousHome.member.copy(
                        nextGame = game.copy(
                            ownAttendance = game.ownAttendance?.copy(status = optimisticStatus)
                                ?: HomeOwnAttendance(optimisticStatus, null),
                        ),
                    ),
                ),
                member = previousMember.copy(
                    nextGame = gameUi.copy(
                        ownAttendance = optimisticStatus,
                        waitlistKind = optimisticKind ?: gameUi.waitlistKind,
                    ),
                ),
                responding = true,
                responseFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.respond(
                GroupId(game.groupId.value),
                game.gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            )
            if (generation < responseGeneration || loadAtStart < loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val attendance = result.value.value.attendance
                    val actualStatus = attendance.status
                    // O kind reconciliado também deriva de `membershipType`/`mensalistaPriority`
                    // do `HomeNextGame`, não do status — consistente com o caminho otimista.
                    val reconciledKind = if (actualStatus == AttendanceStatus.Waitlisted) {
                        previousHome.member.nextGame?.waitlistKind()
                    } else {
                        null
                    }
                    update {
                        it.copy(
                            home = previousHome.copy(
                                member = previousHome.member.copy(
                                    nextGame = previousHome.member.nextGame?.copy(
                                        ownAttendance = HomeOwnAttendance(
                                            status = actualStatus,
                                            waitlistPosition = attendance.waitlistPosition,
                                        ),
                                    ),
                                ),
                            ),
                            member = previousMember.copy(
                                nextGame = previousMember.nextGame.copy(
                                    ownAttendance = actualStatus,
                                    waitlistKind = reconciledKind ?: previousMember.nextGame.waitlistKind,
                                    // O servidor já traz a posição; copiar para a UI evita
                                    // o chip "Reserva · 0º" entre a reconciliação e o refresh.
                                    waitlistPosition = if (actualStatus == AttendanceStatus.Waitlisted) {
                                        attendance.waitlistPosition
                                    } else {
                                        null
                                    },
                                ),
                            ),
                            responding = false,
                            toast = actualStatus.toToast(),
                        )
                    }
                    load(softRefresh = true)
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        home = previousHome,
                        member = previousMember,
                        responding = false,
                        responseFailed = true,
                    )
                }
            }
        }
    }

    private fun showFailure(generation: Long, error: GroupUiError) {
        if (generation < loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private suspend fun HomeMemberReadModel.toUi(): HomeMemberUi {
        val nextGameUi = nextGame?.toUi()
        return HomeMemberUi(
            subtitle = when {
                nextGameUi == null -> getString(Res.string.home_subtitle_no_game)
                nextGameUi.ownAttendance == AttendanceStatus.Waitlisted -> when (nextGameUi.waitlistKind) {
                    HomeWaitlistKind.Reserva -> getString(
                        Res.string.home_subtitle_waitlist_reserva,
                        nextGameUi.groupName,
                    )
                    HomeWaitlistKind.AvulsoList -> getString(
                        Res.string.home_subtitle_waitlist_avulso,
                        nextGameUi.groupName,
                    )
                    null -> getString(Res.string.home_subtitle_waitlisted, nextGameUi.groupName)
                }
                nextGameUi.ownAttendance == AttendanceStatus.Confirmed -> getString(
                    Res.string.home_subtitle_confirmed,
                    nextGameUi.weekday,
                    nextGameUi.time,
                )
                nextGameUi.ownAttendance == AttendanceStatus.Declined -> getString(
                    Res.string.home_subtitle_declined,
                    nextGameUi.weekday,
                )
                else -> getString(Res.string.home_subtitle_no_response, nextGameUi.weekday.capitalized())
            },
            nextGame = nextGameUi,
            lastCompletedGame = lastCompletedGame?.toUi(),
            groups = groups.map { it.toUi() },
        )
    }

    private suspend fun HomeNextGame.toUi(): HomeNextGameUi {
        val zone = gameTimeZone(zoneId)
        val deadlineInstant = runCatching { Instant.parse(confirmationDeadline) }.getOrNull()
        val startsAtLocal = runCatching { Instant.parse(startsAt).toLocalDateTime(zone) }.getOrNull()
        val deadline = deadlineInstant?.toLocalDateTime(zone)
        val dateTime = startsAtLocal?.let {
            getString(
                Res.string.home_game_date_time,
                getString(it.date.dayOfWeek.shortResource()),
                getString(Res.string.home_date, it.day.twoDigits(), (it.month.ordinal + 1).twoDigits()),
                getString(Res.string.home_time, it.hour.twoDigits(), it.minute.twoDigits()),
            )
        } ?: startsAt
        val time = startsAtLocal?.let { getString(Res.string.home_time, it.hour.twoDigits(), it.minute.twoDigits()) } ?: startsAt
        val weekday = startsAtLocal?.let { getString(it.date.dayOfWeek.longResource()) } ?: ""
        val ownPosition = ownAttendance?.waitlistPosition
        val waitlistedRows = rosterPreview.waitlisted.mapNotNull { member ->
            val pos = member.waitlistPosition ?: return@mapNotNull null
            HomeWaitlistRowUi(
                name = member.displayName,
                position = pos,
                isSelf = ownPosition != null && pos == ownPosition,
            )
        }.sortedBy { it.position }
        return HomeNextGameUi(
            groupId = groupId.value,
            gameId = gameId,
            groupName = groupName,
            dateTime = dateTime,
            local = local,
            deadline = deadlineLabel(deadline, zone),
            confirmedSummary = getString(Res.string.home_confirmed_summary, confirmedCount, capacity),
            confirmedCount = confirmedCount,
            capacity = capacity,
            rosterNames = rosterPreview.confirmed.map { it.displayName },
            ownAttendance = ownAttendance?.status,
            confirmationOpen = deadlineInstant?.let { now.now() < it } == true,
            weekday = weekday,
            time = time,
            waitlistKind = ownAttendance?.status?.let { waitlistKind() },
            waitlistPosition = ownPosition,
            confirmedRoster = rosterPreview.confirmed.map { it.displayName },
            waitlistedRoster = waitlistedRows,
            mensalistaConfirmedCount = confirmedCount,
            deadlineBellLabel = deadline?.let { bellDeadlineLabel(it) } ?: "",
        )
    }

    private suspend fun HomeLastCompletedGame.toUi(): HomeLastCompletedGameUi {
        val local = runCatching {
            Instant.parse(startsAt).toLocalDateTime(gameTimeZone(zoneId))
        }.getOrNull()
        val time = local?.let { getString(Res.string.home_time, it.hour.twoDigits(), it.minute.twoDigits()) } ?: startsAt
        val month = local?.let { getString((it.month.ordinal + 1).monthResource()) } ?: ""
        return HomeLastCompletedGameUi(
            day = local?.day?.toString() ?: startsAt,
            month = month,
            title = getString(Res.string.home_last_game_title, groupName, time),
            summary = if (ownPlayed) {
                getString(Res.string.home_last_game_played, confirmedCount)
            } else {
                getString(Res.string.home_last_game_summary, confirmedCount)
            },
        )
    }

    private suspend fun HomeMemberGroup.toUi() = HomeGroupUi(
        id = id.value,
        name = name,
        meta = getString(Res.string.home_group_meta, memberCount, gamesPlayed),
    )

    private suspend fun deadlineLabel(deadline: kotlinx.datetime.LocalDateTime?, zone: TimeZone): String {
        if (deadline == null) return ""
        val today = now.now().toLocalDateTime(zone).date
        val date = deadline.date
        val time = getString(Res.string.home_time, deadline.hour.twoDigits(), deadline.minute.twoDigits())
        return when {
            date == today -> getString(Res.string.home_deadline_today, time)
            date == today.plus(kotlinx.datetime.DatePeriod(days = 1)) -> getString(Res.string.home_deadline_tomorrow, time)
            else -> getString(
                Res.string.home_deadline_date,
                getString(Res.string.home_date, date.day.twoDigits(), (date.month.ordinal + 1).twoDigits()),
                time,
            )
        }
    }

    private fun gameTimeZone(zoneId: String): TimeZone =
        runCatching { TimeZone.of(zoneId) }.getOrDefault(TimeZone.UTC)

    private fun Int.twoDigits() = toString().padStart(2, '0')

    private fun String.firstName() = trim().substringBefore(' ').ifBlank { trim() }

    private fun String.capitalized() = replaceFirstChar { it.titlecase() }

    private fun HomeNextGame.optimisticStatus(intent: AttendanceIntent) = when {
        intent == AttendanceIntent.Decline -> AttendanceStatus.Declined
        ownAttendance?.status == AttendanceStatus.Confirmed -> AttendanceStatus.Confirmed
        membershipType == AthleteMembershipType.AVULSO && mensalistaPriority -> AttendanceStatus.Waitlisted
        confirmedCount >= capacity -> AttendanceStatus.Waitlisted
        else -> AttendanceStatus.Confirmed
    }

    /**
     * Ponto único de derivação do tipo de espera: `Avulso` com `mensalistaPriority`
     * é a lista do avulso (6e); o resto é reserva (6b). O hero, o subtítulo do
     * cabeçalho e o `optimisticStatus` usam a mesma regra.
     */
    private fun HomeNextGame.waitlistKind(): HomeWaitlistKind =
        if (membershipType == AthleteMembershipType.AVULSO && mensalistaPriority) {
            HomeWaitlistKind.AvulsoList
        } else {
            HomeWaitlistKind.Reserva
        }

    private suspend fun bellDeadlineLabel(deadline: kotlinx.datetime.LocalDateTime): String {
        val time = getString(Res.string.home_time, deadline.hour.twoDigits(), deadline.minute.twoDigits())
        val date = getString(
            Res.string.home_date,
            deadline.day.twoDigits(),
            (deadline.month.ordinal + 1).twoDigits(),
        )
        return getString(Res.string.home_waitlist_reserva_bell, time, date)
    }

    private fun AttendanceStatus.toToast() = when (this) {
        AttendanceStatus.Confirmed -> HomeToast.Confirmed
        AttendanceStatus.Declined -> HomeToast.Declined
        AttendanceStatus.Waitlisted -> HomeToast.Waitlisted
    }
}

private fun DayOfWeek.shortResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.home_weekday_monday_short
    DayOfWeek.TUESDAY -> Res.string.home_weekday_tuesday_short
    DayOfWeek.WEDNESDAY -> Res.string.home_weekday_wednesday_short
    DayOfWeek.THURSDAY -> Res.string.home_weekday_thursday_short
    DayOfWeek.FRIDAY -> Res.string.home_weekday_friday_short
    DayOfWeek.SATURDAY -> Res.string.home_weekday_saturday_short
    DayOfWeek.SUNDAY -> Res.string.home_weekday_sunday_short
}

private fun DayOfWeek.longResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.home_weekday_monday
    DayOfWeek.TUESDAY -> Res.string.home_weekday_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.home_weekday_wednesday
    DayOfWeek.THURSDAY -> Res.string.home_weekday_thursday
    DayOfWeek.FRIDAY -> Res.string.home_weekday_friday
    DayOfWeek.SATURDAY -> Res.string.home_weekday_saturday
    DayOfWeek.SUNDAY -> Res.string.home_weekday_sunday
}

private fun Int.monthResource(): StringResource = when (this) {
    1 -> Res.string.home_month_january
    2 -> Res.string.home_month_february
    3 -> Res.string.home_month_march
    4 -> Res.string.home_month_april
    5 -> Res.string.home_month_may
    6 -> Res.string.home_month_june
    7 -> Res.string.home_month_july
    8 -> Res.string.home_month_august
    9 -> Res.string.home_month_september
    10 -> Res.string.home_month_october
    11 -> Res.string.home_month_november
    else -> Res.string.home_month_december
}
