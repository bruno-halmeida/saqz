package br.com.saqz.groups.presentation.memberprofile

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.DataError
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.presentation.ui.athleteregistration.levelLabel
import br.com.saqz.groups.presentation.ui.athleteregistration.positionLabel
import br.com.saqz.groups.presentation.ui.athleteregistration.sideLabel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class MemberProfileViewModel(
    private val groupId: String,
    private val userId: String,
    private val athletes: AthleteGateway,
) : MviViewModel<MemberProfileState, MemberProfileIntent, MemberProfileEffect>(MemberProfileState()) {
    private var generation = 0

    init { load() }

    override fun handleIntent(intent: MemberProfileIntent) = when (intent) {
        MemberProfileIntent.Retry -> load()
    }

    private fun load() {
        val request = ++generation
        update { MemberProfileState() }
        viewModelScope.launch {
            val roster = athletes.roster(GroupId(groupId), AthleteRosterFilter(includeInactive = true))
            if (request != generation) return@launch
            if (roster is SaqzResult.Failure) {
                update { it.copy(loading = false, error = roster.error.toUiError()) }
                return@launch
            }
            val member = (roster as SaqzResult.Success).value.find { it.userId == userId }
            if (member == null) {
                update { it.copy(loading = false, error = GroupUiError.NotFound) }
                return@launch
            }
            val stats = athletes.stats(GroupId(groupId), userId)
            if (request != generation) return@launch
            val attributes = listOfNotNull(
                member.nickname?.takeIf(String::isNotBlank),
                member.position?.let { getString(positionLabel(it, null)) },
                member.secondaryPosition?.let { getString(positionLabel(it, null)) },
                member.level?.let { getString(levelLabel(it)) },
                member.preferredSide?.let { getString(sideLabel(it)) },
                member.heightCm?.let { "$it cm" },
            )
            if (request != generation) return@launch
            val numbers = (stats as? SaqzResult.Success)?.value
            val statsFailed = stats is SaqzResult.Failure && stats.error != AthleteError.DataFailure(DataError.Forbidden)
            update {
                MemberProfileState(
                    loading = false,
                    name = member.displayName,
                    attributes = attributes,
                    // Only the server-filtered phone is eligible for display; no profile bypass.
                    phone = member.phone,
                    games = numbers?.games?.toString(),
                    attendance = numbers?.attendanceRate?.let { "$it%" },
                    absences = numbers?.absences?.toString(),
                    statsFailed = statsFailed,
                )
            }
        }
    }
}
