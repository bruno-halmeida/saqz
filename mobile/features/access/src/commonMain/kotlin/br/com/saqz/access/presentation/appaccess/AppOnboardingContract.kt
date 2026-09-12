package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.appaccess.OnboardingError

data class AppOnboardingState(
    val isLoading: Boolean = true,
    val completed: Boolean = false,
    val failure: OnboardingError? = null,
)

sealed interface AppOnboardingIntent {
    data object Opened : AppOnboardingIntent
    data object Skip : AppOnboardingIntent
    data object Retry : AppOnboardingIntent
}
