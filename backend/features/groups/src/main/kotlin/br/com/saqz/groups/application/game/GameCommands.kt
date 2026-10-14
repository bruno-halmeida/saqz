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
import br.com.saqz.groups.domain.game.GameTimingValidator
import br.com.saqz.groups.domain.game.GameValidationError
import br.com.saqz.groups.domain.game.GroupGameDefaults
import java.util.UUID

data class GameCreationContext(val role: GroupRole?, val defaults: GroupGameDefaults)
data class GameCommandContext(val role: GroupRole?, val game: Game)

sealed interface GameWriteResult {
    data class Saved(val game: Game) : GameWriteResult
    data class ScheduleConflict(val gameId: UUID) : GameWriteResult
    data object VersionConflict : GameWriteResult
    data object NotFound : GameWriteResult
}

class GameScheduleConflictWriteException : RuntimeException()

interface GameCommandRepository {
    fun creationContext(actor: UUID, groupId: UUID): GameCreationContext?
    fun recurringConflict(groupId: UUID, startsAt: java.time.Instant, excludingGameId: UUID?): UUID?
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

class GameSideEffects(private val delegates: List<GameSideEffectPort>) : GameSideEffectPort {
    override fun apply(game: Game, actorId: UUID, effects: Set<GameSideEffect>) {
        delegates.forEach { it.apply(game, actorId, effects) }
    }
}

sealed interface GameCommandResult {
    data class Success(val game: Game) : GameCommandResult
    data class Invalid(val errors: List<GameValidationError>) : GameCommandResult
    data class InvalidTransition(val current: GameStatus, val mutation: GameMutation) : GameCommandResult
    data object GroupNotFound : GameCommandResult
    data object GameNotFound : GameCommandResult
    data object AccessForbidden : GameCommandResult
    data object VersionConflict : GameCommandResult
    data class ScheduleConflict(val gameId: UUID) : GameCommandResult
}

class CreateGame(
    private val transactionRunner: TransactionRunner,
    private val repository: GameCommandRepository,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) {
    fun execute(actor: UUID, groupId: UUID, gameId: UUID, input: CreateGameInput): GameCommandResult =
        transactionRunner.inTransaction {
            val context = repository.creationContext(actor, groupId) ?: return@inTransaction GameCommandResult.GroupNotFound
            if (!context.role.isOrganizer()) return@inTransaction context.role.denied()
            val now = clock.instant()
            val draft = GameDefaultSnapshotFactory.copy(context.defaults, input).let { copied ->
                // Prazo derivado do padrão do grupo que já passou (jogo marcado em cima da hora): fecha agora.
                val deadline = copied.confirmationDeadline
                if (input.confirmationDeadline == null && deadline != null && deadline.isBefore(now) &&
                    copied.startsAt != null && copied.startsAt.isAfter(now)
                ) copied.copy(confirmationDeadline = now) else copied
            }
            when (val validation = GameDraftValidator.validate(draft)) {
                is GameDraftValidation.Invalid -> GameCommandResult.Invalid(validation.errors)
                is GameDraftValidation.Valid -> GameTimingValidator.check(validation.snapshot, now).takeIf { it.isNotEmpty() }
                    ?.let(GameCommandResult::Invalid)
                    ?: repository.recurringConflict(groupId, validation.snapshot.startsAt, excludingGameId = null)
                    ?.let(GameCommandResult::ScheduleConflict)
                    ?: repository.create(Game(gameId, groupId, validation.snapshot)).toResult()
            }
        }
}

class EditGame(
    private val transactionRunner: TransactionRunner,
    private val repository: GameCommandRepository,
    private val sideEffects: GameSideEffectPort,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
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
        // Jogo que já passou do horário de início não é mais alterado.
        if (!context.game.snapshot.startsAt.isAfter(clock.instant()) ||
            !GameLifecyclePolicy.canMutate(context.game.status, GameMutation.EDIT)
        ) {
            return@inTransaction GameCommandResult.InvalidTransition(context.game.status, GameMutation.EDIT)
        }
        val snapshot = when (val validation = GameDraftValidator.validate(input)) {
            is GameDraftValidation.Invalid -> return@inTransaction GameCommandResult.Invalid(validation.errors)
            is GameDraftValidation.Valid -> validation.snapshot
        }
        // Só o que mudou é checado contra o relógio: reenviar o prazo antigo ao editar outro campo segue valendo.
        val current = context.game.snapshot
        val now = clock.instant()
        val timing = (if (snapshot.startsAt != current.startsAt) GameTimingValidator.startsAtInFuture(snapshot.startsAt, now) else emptyList()) +
            (if (snapshot.confirmationDeadline != current.confirmationDeadline) GameTimingValidator.deadlineNotInPast(snapshot.confirmationDeadline, now) else emptyList())
        if (timing.isNotEmpty()) return@inTransaction GameCommandResult.Invalid(timing)
        repository.recurringConflict(groupId, snapshot.startsAt, excludingGameId = gameId)
            ?.let { return@inTransaction GameCommandResult.ScheduleConflict(it) }
        when (val write = repository.update(context.game.copy(snapshot = snapshot), expectedVersion)) {
            is GameWriteResult.Saved -> {
                sideEffects.apply(write.game, actor, setOf(GameSideEffect.SCHEDULE_CHANGED))
                GameCommandResult.Success(write.game)
            }
            is GameWriteResult.ScheduleConflict -> GameCommandResult.ScheduleConflict(write.gameId)
            GameWriteResult.VersionConflict -> GameCommandResult.VersionConflict
            GameWriteResult.NotFound -> GameCommandResult.GameNotFound
        }
    }
}

class ChangeGameLifecycle(
    private val transactionRunner: TransactionRunner,
    private val repository: GameCommandRepository,
    private val sideEffects: GameSideEffectPort,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) {
    fun execute(
        actor: UUID,
        groupId: UUID,
        gameId: UUID,
        expectedVersion: Long,
        mutation: GameMutation,
        /** A rotina de liberação da série publica dentro da janela do prazo; só o "Publicar" manual confere o relógio. */
        enforceTiming: Boolean = true,
    ): GameCommandResult {
        require(mutation != GameMutation.EDIT)
        return transactionRunner.inTransaction {
            val context = repository.find(actor, groupId, gameId) ?: return@inTransaction GameCommandResult.GameNotFound
            if (!context.role.isOrganizer()) return@inTransaction context.role.denied()
            if (context.game.version != expectedVersion) return@inTransaction GameCommandResult.VersionConflict
            if (!GameLifecyclePolicy.canMutate(context.game.status, mutation)) {
                return@inTransaction GameCommandResult.InvalidTransition(context.game.status, mutation)
            }
            if (mutation == GameMutation.PUBLISH && enforceTiming) {
                val timing = GameTimingValidator.check(context.game.snapshot, clock.instant())
                if (timing.isNotEmpty()) return@inTransaction GameCommandResult.Invalid(timing)
            }
            val changed = context.game.copy(status = requireNotNull(GameLifecyclePolicy.target(mutation)))
            when (val write = repository.update(changed, expectedVersion)) {
                is GameWriteResult.Saved -> {
                    // Rascunho cancelado nunca foi visto por ninguém: não há o que avisar, congelar nem cobrar.
                    val unpublishedDraftCancelled = mutation == GameMutation.CANCEL && context.game.status == GameStatus.DRAFT
                    if (!unpublishedDraftCancelled) sideEffects.apply(write.game, actor, mutation.effects())
                    val responseGame = if (mutation == GameMutation.CANCEL && !unpublishedDraftCancelled) {
                        repository.find(actor, groupId, gameId)?.game ?: write.game
                    } else {
                        write.game
                    }
                    GameCommandResult.Success(responseGame)
                }
                is GameWriteResult.ScheduleConflict -> GameCommandResult.ScheduleConflict(write.gameId)
                GameWriteResult.VersionConflict -> GameCommandResult.VersionConflict
                GameWriteResult.NotFound -> GameCommandResult.GameNotFound
            }
        }
    }
}

private fun GameWriteResult.toResult(): GameCommandResult = when (this) {
    is GameWriteResult.Saved -> GameCommandResult.Success(game)
    is GameWriteResult.ScheduleConflict -> GameCommandResult.ScheduleConflict(gameId)
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
