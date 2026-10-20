package br.com.saqz.groups.application.settings

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.attendance.GameSystem
import br.com.saqz.groups.domain.attendance.PositionSlotsError
import br.com.saqz.groups.domain.attendance.PositionSlotsValidator
import java.util.UUID

/** Vagas por posição do grupo (DEB-102). Lista vazia = grupo sem posições (total, como antes). */
data class PositionSlotsConfig(
    val gameSystem: GameSystem?,
    val slots: List<Pair<AthletePosition, Int>>,
    val version: Long,
) {
    val total: Int get() = slots.sumOf { it.second }
}

interface GroupPositionSlotsRepository {
    fun read(groupId: UUID): List<Pair<AthletePosition, Int>>
    fun gameSystem(groupId: UUID): GameSystem?

    /** Troca a configuração com a versão do grupo; com posições grava também `default_capacity = soma`. */
    fun replace(groupId: UUID, expectedVersion: Long, system: GameSystem?, slots: List<Pair<AthletePosition, Int>>): Boolean
}

sealed interface PositionSlotsReadResult {
    data class Success(val config: PositionSlotsConfig) : PositionSlotsReadResult
    data object GroupNotFound : PositionSlotsReadResult
}

sealed interface PositionSlotsWriteResult {
    data class Success(val config: PositionSlotsConfig) : PositionSlotsWriteResult
    data object GroupNotFound : PositionSlotsWriteResult
    data object AccessForbidden : PositionSlotsWriteResult
    data object VersionConflict : PositionSlotsWriteResult
    data class Invalid(val error: PositionSlotsError) : PositionSlotsWriteResult
}

class GroupPositionSlotsService(
    private val transaction: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val repository: GroupPositionSlotsRepository,
    private val accessPolicy: GroupAccessPolicy = GroupAccessPolicy(),
) {
    fun read(actor: UUID, groupId: UUID): PositionSlotsReadResult = transaction.inTransaction {
        val group = readRepository.find(GroupReadKey(actor, groupId)) ?: return@inTransaction PositionSlotsReadResult.GroupNotFound
        if (group.role == null) return@inTransaction PositionSlotsReadResult.GroupNotFound
        PositionSlotsReadResult.Success(PositionSlotsConfig(repository.gameSystem(groupId), repository.read(groupId), group.version))
    }

    fun write(
        actor: UUID,
        groupId: UUID,
        expectedVersion: Long,
        system: GameSystem?,
        slots: List<Pair<AthletePosition, Int>>,
        total: Int? = null,
    ): PositionSlotsWriteResult = transaction.inTransaction {
        val group = readRepository.find(GroupReadKey(actor, groupId)) ?: return@inTransaction PositionSlotsWriteResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.UPDATE_SETTINGS)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction PositionSlotsWriteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction PositionSlotsWriteResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        PositionSlotsValidator.validate(system, slots, total)?.let { return@inTransaction PositionSlotsWriteResult.Invalid(it) }
        if (group.version != expectedVersion || !repository.replace(groupId, expectedVersion, system, slots)) {
            return@inTransaction PositionSlotsWriteResult.VersionConflict
        }
        PositionSlotsWriteResult.Success(PositionSlotsConfig(system, slots, expectedVersion + 1))
    }
}
