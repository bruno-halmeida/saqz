package br.com.saqz.groups.presentation

import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.moderation.BlockedPerson
import br.com.saqz.groups.domain.moderation.ContentReport
import br.com.saqz.groups.domain.moderation.ModerationError
import br.com.saqz.groups.domain.moderation.ModerationGateway
import br.com.saqz.groups.presentation.moderation.BlockedPeopleRepository
import br.com.saqz.groups.presentation.moderation.ModerationViewModel

class FakeModerationGateway(
    var reportResult: EmptyResult<ModerationError> = SaqzResult.Success(Unit),
    var blockedResult: SaqzResult<List<BlockedPerson>, ModerationError> = SaqzResult.Success(emptyList()),
    var blockResult: EmptyResult<ModerationError> = SaqzResult.Success(Unit),
    var unblockResult: EmptyResult<ModerationError> = SaqzResult.Success(Unit),
) : ModerationGateway {
    val reports = mutableListOf<ContentReport>()
    val blocks = mutableListOf<Pair<String, GroupId>>()
    val unblocks = mutableListOf<String>()
    var listCalls = 0

    /** Suspende a chamada até o teste liberar: deixa ver o estado "enviando". */
    var reportBlock: (suspend () -> EmptyResult<ModerationError>)? = null
    var blockBlock: (suspend () -> EmptyResult<ModerationError>)? = null

    override suspend fun fileContentReport(report: ContentReport): EmptyResult<ModerationError> {
        reports += report
        return reportBlock?.invoke() ?: reportResult
    }

    override suspend fun listBlockedPeople(): SaqzResult<List<BlockedPerson>, ModerationError> {
        listCalls++
        return blockedResult
    }

    override suspend fun blockPerson(userId: String, groupId: GroupId): EmptyResult<ModerationError> {
        blocks += userId to groupId
        return blockBlock?.invoke() ?: blockResult
    }

    override suspend fun unblockPerson(userId: String): EmptyResult<ModerationError> {
        unblocks += userId
        return unblockResult
    }
}

fun blockedPerson(userId: String) = BlockedPerson(userId, "Pessoa $userId", "2026-10-01T12:00:00Z")

fun fakeBlocks(gateway: FakeModerationGateway = FakeModerationGateway()) = BlockedPeopleRepository(gateway)

/** Para Roots montados sem Koin: os sheets existem, mas nada sai para a rede. */
fun fakeModeration() = ModerationViewModel(FakeModerationGateway(), fakeBlocks())
