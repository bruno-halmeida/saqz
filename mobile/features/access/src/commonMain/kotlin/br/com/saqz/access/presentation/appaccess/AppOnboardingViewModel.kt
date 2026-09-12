package br.com.saqz.access.presentation.appaccess

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.appaccess.OnboardingGateway
import br.com.saqz.access.domain.appaccess.OnboardingStatus
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.launch

class AppOnboardingViewModel(
    private val gateway: OnboardingGateway,
) : MviViewModel<AppOnboardingState, AppOnboardingIntent, Nothing>(AppOnboardingState()) {
    private var generation = 0

    init {
        onIntent(AppOnboardingIntent.Opened)
    }

    override fun onIntent(intent: AppOnboardingIntent) {
        when (intent) {
            AppOnboardingIntent.Opened,
            AppOnboardingIntent.Retry,
            -> load()
            AppOnboardingIntent.Skip -> complete()
        }
    }

    private fun load() {
        val token = ++generation
        update { it.copy(isLoading = true, failure = null) }
        viewModelScope.launch {
            when (val result = gateway.status()) {
                is SaqzResult.Success -> publish(token, result.value)
                is SaqzResult.Failure -> if (token == generation) {
                    update { it.copy(isLoading = false, failure = result.error) }
                }
            }
        }
    }

    private fun complete() {
        val token = ++generation
        update { it.copy(isLoading = true, failure = null) }
        viewModelScope.launch {
            when (val result = gateway.complete()) {
                is SaqzResult.Success -> publish(token, result.value)
                is SaqzResult.Failure -> if (token == generation) {
                    update { it.copy(isLoading = false, failure = result.error) }
                }
            }
        }
    }

    private fun publish(token: Int, status: OnboardingStatus) {
        if (token != generation) return
        update { it.copy(isLoading = false, completed = status.completed, failure = null) }
    }
}
