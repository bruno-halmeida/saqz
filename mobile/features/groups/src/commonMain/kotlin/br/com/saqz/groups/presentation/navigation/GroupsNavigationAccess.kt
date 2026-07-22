package br.com.saqz.groups.presentation.navigation

import androidx.compose.runtime.Immutable

@Immutable
data class GroupsNavigationAccess(
    val showPeople: Boolean = false,
    val showGames: Boolean = false,
    val showFinance: Boolean = false,
    val canCompleteProfile: Boolean = false,
    val canMutateOperations: Boolean = false,
    val financeDestination: GroupsDestination? = null,
)
