package br.com.saqz.access.presentation.appaccess

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.appaccess.OnboardingGateway
import br.com.saqz.access.domain.appaccess.OnboardingStatus
import br.com.saqz.domain.DataError
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.launch

class AppOnboardingViewModel(
    private val gateway: OnboardingGateway,
    private val currentOwnerUserId: () -> String?,
) : MviViewModel<AppOnboardingState, AppOnboardingIntent, AppOnboardingEffect>(AppOnboardingState()) {
    private var generation = 0
    private var loadedOwnerUserId: String? = null

    override fun onIntent(intent: AppOnboardingIntent) {
        when (intent) {
            AppOnboardingIntent.Opened -> load()
            AppOnboardingIntent.Retry -> load(force = true)
            AppOnboardingIntent.Continue,
            AppOnboardingIntent.Skip,
            -> complete()
        }
    }

    private fun load(force: Boolean = false) {
        val ownerUserId = currentOwnerUserId() ?: return
        if (!force && loadedOwnerUserId == ownerUserId) return
        val token = ++generation
        loadedOwnerUserId = ownerUserId
        update { it.copy(isLoading = true, failure = null) }
        viewModelScope.launch {
            when (val result = gateway.status()) {
                is SaqzResult.Success -> {
                    publish(token, ownerUserId, result.value)
                    if (result.value.completed && isCurrent(token, ownerUserId)) emit(AppOnboardingEffect.CloseIntro)
                }
                is SaqzResult.Failure -> if (isCurrent(token, ownerUserId)) {
                    update { it.copy(isLoading = false, failure = result.error) }
                }
            }
        }
    }

    private fun complete() {
        val ownerUserId = currentOwnerUserId() ?: return
        if (state.value.isLoading || loadedOwnerUserId != ownerUserId) return
        val token = ++generation
        update { it.copy(isLoading = true, failure = null) }
        viewModelScope.launch {
            when (val result = gateway.complete()) {
                is SaqzResult.Success -> if (isCurrent(token, ownerUserId)) {
                    if (result.value.completed) {
                        update { it.copy(isLoading = false, completed = true, failure = null) }
                        emit(AppOnboardingEffect.OpenFirstGroupForm)
                    } else {
                        update {
                            it.copy(
                                isLoading = false,
                                completed = false,
                                failure = br.com.saqz.access.domain.appaccess.OnboardingError.Data(DataError.InvalidResponse),
                            )
                        }
                    }
                }
                is SaqzResult.Failure -> if (isCurrent(token, ownerUserId)) {
                    update { it.copy(isLoading = false, failure = result.error) }
                }
            }
        }
    }

    private fun publish(token: Int, ownerUserId: String, status: OnboardingStatus) {
        if (!isCurrent(token, ownerUserId)) return
        update { it.copy(isLoading = false, completed = status.completed, failure = null) }
    }

    private fun isCurrent(token: Int, ownerUserId: String): Boolean =
        token == generation && currentOwnerUserId() == ownerUserId
}
