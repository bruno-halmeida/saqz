package br.com.saqz.groups.presentation.home

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class HomeViewModel(
    private val homeGateway: HomeGateway,
    private val athleteGateway: AthleteGateway,
) : MviViewModel<HomeState, HomeIntent, Nothing>(HomeState()) {
    private var loadGeneration = 0

    init {
        load()
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> load()
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            val homeRequest = async { homeGateway.read() }
            val profileRequest = async { athleteGateway.ownProfile() }
            val homeResult = homeRequest.await()
            val profileResult = profileRequest.await()
            if (generation != loadGeneration) return@launch

            when (homeResult) {
                is SaqzResult.Failure -> showFailure(generation, homeResult.error.toUiError())
                is SaqzResult.Success -> when (profileResult) {
                    is SaqzResult.Failure -> showFailure(generation, profileResult.error.toUiError())
                    is SaqzResult.Success -> update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            error = null,
                            displayName = profileResult.value.displayName,
                            home = homeResult.value,
                        )
                    }
                }
            }
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }
}
