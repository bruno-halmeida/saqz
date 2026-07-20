package br.com.saqz.groups.application.game

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.CreateGameInput
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameDefaultSnapshotFactory
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.game.GameDraftValidation
import br.com.saqz.groups.domain.game.GameDraftValidator
import br.com.saqz.groups.domain.game.GameLifecyclePolicy
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameValidationError
import br.com.saqz.groups.domain.game.GroupGameDefaults
import java.util.UUID

data class GameCreationContext(val role: GroupRole?, val defaults: GroupGameDefaults)
data class GameCommandContext(val role: GroupRole?, val game: Game)

sealed interface GameWriteResult {
    data class Saved(val game: Game) : GameWriteResult
    data object VersionConflict : GameWriteResult
    data object NotFound : GameWriteResult
}

interface GameCommandRepository {
    fun creationContext(actor: UUID, groupId: UUID): GameCreationContext?
    fun find(actor: UUID, groupId: UUID, gameId: UUID): GameCommandContext?
    fun create(game: Game): GameWriteResult
    fun update(game: Game, expectedVersion: Long): GameWriteResult
}

enum class GameSideEffect {
    SCHEDULE_CHANGED,
    ATTENDANCE_OPENED,
    ATTENDANCE_FROZEN,
    PENDING_CHARGES_CANCELLED,
    FINANCE_REVIEW_MARKED,
}

fun interface GameSideEffectPort {
    fun apply(game: Game, actorId: UUID, effects: Set<GameSideEffect>)
}

sealed interface GameCommandResult {
    data class Success(val game: Game) : GameCommandResult
    data class Invalid(val errors: List<GameValidationError>) : GameCommandResult
    data class InvalidTransition(val current: GameStatus, val mutation: GameMutation) : GameCommandResult
    data object GroupNotFound : GameCommandResult
    data object GameNotFound : GameCommandResult
    data object AccessForbidden : GameCommandResult
    data object VersionConflict : GameCommandResult
}

class CreateGame(
    private val transactionRunner: TransactionRunner,
    private val repository: GameCommandRepository,
) {
    fun execute(actor: UUID, groupId: UUID, gameId: UUID, input: CreateGameInput): GameCommandResult =
        transactionRunner.inTransaction {
            val context = repository.creationContext(actor, groupId) ?: return@inTransaction GameCommandResult.GroupNotFound
            if (!context.role.isOrganizer()) return@inTransaction context.role.denied()
            when (val validation = GameDraftValidator.validate(GameDefaultSnapshotFactory.copy(context.defaults, input))) {
                is GameDraftValidation.Invalid -> GameCommandResult.Invalid(validation.errors)
                is GameDraftValidation.Valid -> repository.create(Game(gameId, groupId, validation.snapshot)).toResult()
            }
        }
}

class EditGame(
    private val transactionRunner: TransactionRunner,
    private val repository: GameCommandRepository,
    private val sideEffects: GameSideEffectPort,
) {
    fun execute(
        actor: UUID,
        groupId: UUID,
        gameId: UUID,
        expectedVersion: Long,
        input: GameDraftInput,
    ): GameCommandResult = transactionRunner.inTransaction {
        val context = repository.find(actor, groupId, gameId) ?: return@inTransaction GameCommandResult.GameNotFound
        if (!context.role.isOrganizer()) return@inTransaction context.role.denied()
        if (context.game.version != expectedVersion) return@inTransaction GameCommandResult.VersionConflict
        if (!GameLifecyclePolicy.canMutate(context.game.status, GameMutation.EDIT)) {
            return@inTransaction GameCommandResult.InvalidTransition(context.game.status, GameMutation.EDIT)
        }
        val snapshot = when (val validation = GameDraftValidator.validate(input)) {
            is GameDraftValidation.Invalid -> return@inTransaction GameCommandResult.Invalid(validation.errors)
            is GameDraftValidation.Valid -> validation.snapshot
        }
        when (val write = repository.update(context.game.copy(snapshot = snapshot), expectedVersion)) {
            is GameWriteResult.Saved -> {
                sideEffects.apply(write.game, actor, setOf(GameSideEffect.SCHEDULE_CHANGED))
                GameCommandResult.Success(write.game)
            }
            GameWriteResult.VersionConflict -> GameCommandResult.VersionConflict
            GameWriteResult.NotFound -> GameCommandResult.GameNotFound
        }
    }
}

class ChangeGameLifecycle(
    private val transactionRunner: TransactionRunner,
    private val repository: GameCommandRepository,
    private val sideEffects: GameSideEffectPort,
) {
    fun execute(
        actor: UUID,
        groupId: UUID,
        gameId: UUID,
        expectedVersion: Long,
        mutation: GameMutation,
    ): GameCommandResult {
        require(mutation != GameMutation.EDIT)
        return transactionRunner.inTransaction {
            val context = repository.find(actor, groupId, gameId) ?: return@inTransaction GameCommandResult.GameNotFound
            if (!context.role.isOrganizer()) return@inTransaction context.role.denied()
            if (context.game.version != expectedVersion) return@inTransaction GameCommandResult.VersionConflict
            if (!GameLifecyclePolicy.canMutate(context.game.status, mutation)) {
                return@inTransaction GameCommandResult.InvalidTransition(context.game.status, mutation)
            }
            val changed = context.game.copy(status = requireNotNull(GameLifecyclePolicy.target(mutation)))
            when (val write = repository.update(changed, expectedVersion)) {
                is GameWriteResult.Saved -> {
                    sideEffects.apply(write.game, actor, mutation.effects())
                    GameCommandResult.Success(write.game)
                }
                GameWriteResult.VersionConflict -> GameCommandResult.VersionConflict
                GameWriteResult.NotFound -> GameCommandResult.GameNotFound
            }
        }
    }
}

private fun GameWriteResult.toResult(): GameCommandResult = when (this) {
    is GameWriteResult.Saved -> GameCommandResult.Success(game)
    GameWriteResult.VersionConflict -> GameCommandResult.VersionConflict
    GameWriteResult.NotFound -> GameCommandResult.GroupNotFound
}

private fun GroupRole?.isOrganizer(): Boolean = this == GroupRole.OWNER || this == GroupRole.ADMIN
private fun GroupRole?.denied(): GameCommandResult =
    if (this == null) GameCommandResult.GroupNotFound else GameCommandResult.AccessForbidden

private fun GameMutation.effects(): Set<GameSideEffect> = when (this) {
    GameMutation.PUBLISH -> setOf(GameSideEffect.SCHEDULE_CHANGED, GameSideEffect.ATTENDANCE_OPENED)
    GameMutation.CANCEL -> setOf(
        GameSideEffect.SCHEDULE_CHANGED,
        GameSideEffect.ATTENDANCE_FROZEN,
        GameSideEffect.PENDING_CHARGES_CANCELLED,
        GameSideEffect.FINANCE_REVIEW_MARKED,
    )
    GameMutation.COMPLETE -> setOf(GameSideEffect.ATTENDANCE_FROZEN)
    GameMutation.EDIT -> error("edit effects are applied by EditGame")
}
