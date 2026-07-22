package br.com.saqz.groups.presentation.games.detail

enum class GameLifecycleAction {
    PUBLISH,
    CANCEL,
    COMPLETE,
}

internal fun GameLifecycleAction.toDomain() = when (this) {
    GameLifecycleAction.PUBLISH -> br.com.saqz.groups.domain.game.GameLifecycleAction.Publish
    GameLifecycleAction.CANCEL -> br.com.saqz.groups.domain.game.GameLifecycleAction.Cancel
    GameLifecycleAction.COMPLETE -> br.com.saqz.groups.domain.game.GameLifecycleAction.Complete
}
