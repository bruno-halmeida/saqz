package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.game.GameStatus
import java.net.URI
import java.time.Clock
import java.util.UUID

sealed interface RotateAttendanceLinkResult {
    data class Success(val url: URI) : RotateAttendanceLinkResult

    data object GameNotFound : RotateAttendanceLinkResult

    data object AccessForbidden : RotateAttendanceLinkResult

    data object AttendanceFrozen : RotateAttendanceLinkResult

    data object DeadlinePassed : RotateAttendanceLinkResult
}

class RotateAttendanceLink(
    private val transactionRunner: TransactionRunner,
    private val repository: AttendanceLinkRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val tokenGenerator: AttendanceLinkTokenGenerator,
    private val linkFactory: AttendanceLinkFactory,
    private val clock: Clock,
) {
    fun execute(actorId: UUID, groupId: UUID, gameId: UUID): RotateAttendanceLinkResult = transactionRunner.inTransaction {
        val target = repository.lockRotatableTarget(actorId, groupId, gameId)
            ?: return@inTransaction RotateAttendanceLinkResult.GameNotFound
        when (accessPolicy.authorize(target.actorRole, GroupAction.MANAGE_ATTENDANCE_SHARE)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction RotateAttendanceLinkResult.GameNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction RotateAttendanceLinkResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        if (target.status != GameStatus.PUBLISHED) return@inTransaction RotateAttendanceLinkResult.AttendanceFrozen
        if (clock.instant() > target.confirmationDeadline) return@inTransaction RotateAttendanceLinkResult.DeadlinePassed

        val token = tokenGenerator.generate()
        val url = linkFactory.create(token.code)
        repository.rotate(RotateAttendanceLinkCommand(groupId, gameId, token.digest, actorId))
        RotateAttendanceLinkResult.Success(url)
    }
}
