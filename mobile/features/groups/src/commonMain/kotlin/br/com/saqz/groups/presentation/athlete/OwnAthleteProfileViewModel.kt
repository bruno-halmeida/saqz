package br.com.saqz.groups.presentation.athlete

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import kotlinx.coroutines.launch

@Immutable
data class OwnAthleteProfileState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val profile: OwnAthleteProfile? = null,
)

sealed interface OwnAthleteProfileIntent {
    data object Reload : OwnAthleteProfileIntent
}

sealed interface OwnAthleteProfileEffect

class OwnAthleteProfileViewModel(
    private val athletes: AthleteGateway,
) : MviViewModel<OwnAthleteProfileState, OwnAthleteProfileIntent, OwnAthleteProfileEffect>(
    OwnAthleteProfileState(),
) {
    init {
        load()
    }

    override fun onIntent(intent: OwnAthleteProfileIntent) {
        when (intent) {
            OwnAthleteProfileIntent.Reload -> load()
        }
    }

    private fun load() {
        update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            when (val result = athletes.ownProfile()) {
                is SaqzResult.Success -> update { it.copy(loading = false, profile = result.value) }
                is SaqzResult.Failure -> update { it.copy(loading = false, failed = true) }
            }
        }
    }
}
