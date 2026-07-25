package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * C1: the app's only own destination — the empty authenticated shell. The access side of
 * the back stack is feature-owned ([br.com.saqz.access.navigation.AccessRoute]); together
 * they form the single acesso→shell stack the session gate reconciles.
 *
 * ponytail: one destination, so no sealed hierarchy. Product routes come back as their
 * screens do, and a sealed interface is cheap to reintroduce then.
 */
@Serializable
data object SaqzShellDestination : NavKey
