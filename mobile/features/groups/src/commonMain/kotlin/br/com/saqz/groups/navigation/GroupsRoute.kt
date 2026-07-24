package br.com.saqz.groups.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Feature-owned, serializable Navigation Compose 3 route keys for the Groups flow.
 *
 * `:features:groups` depends only on the lightweight `navigation3-runtime` artifact
 * for the [NavKey] contract; it never depends on `:navigation` or Navigation
 * Compose 3 UI (`navigation3-ui`).
 */
@Serializable
sealed interface GroupsRoute : NavKey {

    @Serializable
    data object Setup : GroupsRoute

    @Serializable
    data object Selector : GroupsRoute

    @Serializable
    data object Loading : GroupsRoute

    @Serializable
    data object LoadError : GroupsRoute

    @Serializable
    data object GroupHome : GroupsRoute

    @Serializable
    data object ProfileCompletion : GroupsRoute

    @Serializable
    data object People : GroupsRoute

    @Serializable
    data object Games : GroupsRoute

    /**
     * AUTHZ-03: a blank [gameId] is rejected at construction so no navigation
     * command can ever carry an invalid game identity.
     */
    @Serializable
    data class GameDetail(val gameId: String) : GroupsRoute {
        init {
            require(gameId.isNotBlank()) { "GameDetail.gameId must not be blank" }
        }
    }

    /**
     * Game editor route: a null [gameId] creates a new game, a non-blank [gameId]
     * edits the existing one. AUTHZ-03: a blank id is rejected at construction so no
     * navigation command can ever carry an invalid game identity.
     */
    @Serializable
    data class GameEditor(val gameId: String? = null) : GroupsRoute {
        init {
            require(gameId == null || gameId.isNotBlank()) { "GameEditor.gameId must not be blank" }
        }
    }

    @Serializable
    data object Notices : GroupsRoute

    @Serializable
    data object More : GroupsRoute

    @Serializable
    data object Settings : GroupsRoute

    @Serializable
    data object Memberships : GroupsRoute

    @Serializable
    data object Invite : GroupsRoute

    @Serializable
    data object CreateGroup : GroupsRoute
}
