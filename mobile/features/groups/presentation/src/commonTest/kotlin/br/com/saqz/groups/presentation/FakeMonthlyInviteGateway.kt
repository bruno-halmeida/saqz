package br.com.saqz.groups.presentation

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.MonthlyFeeDefault
import br.com.saqz.groups.domain.membership.MonthlyInviteCopiedLink
import br.com.saqz.groups.domain.membership.MonthlyInviteError
import br.com.saqz.groups.domain.membership.MonthlyInviteGateway
import br.com.saqz.groups.domain.membership.MonthlyInviteLink
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInvitePerson
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheck
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheckResult
import br.com.saqz.groups.domain.membership.MonthlyInviteSent
import br.com.saqz.groups.domain.membership.MonthlyInvites

/** Convite de mensalistas em memória; cada resposta pode ser trocada pelo teste. */
internal class FakeMonthlyInviteGateway(
    var listResult: SaqzResult<MonthlyInvites, MonthlyInviteError> = SaqzResult.Success(sampleMonthlyInvites()),
    var checks: Map<String, MonthlyInvitePhoneCheckResult> = emptyMap(),
    var sendResult: SaqzResult<MonthlyInviteSent, MonthlyInviteError>? = null,
    var linkResult: SaqzResult<MonthlyInviteCopiedLink, MonthlyInviteError> =
        SaqzResult.Success(MonthlyInviteCopiedLink("https://saqz.app/?saqz_invite=44DF8SA8", "2026-10-27T15:00:00Z")),
    var actionResult: SaqzResult<Int, MonthlyInviteError> = SaqzResult.Success(1),
    var unitResult: SaqzResult<Unit, MonthlyInviteError> = SaqzResult.Success(Unit),
) : MonthlyInviteGateway {
    val sends = mutableListOf<Pair<String, List<MonthlyInvitePerson>>>()
    val calls = mutableListOf<String>()
    var listCalls = 0

    override suspend fun list(groupId: GroupId): SaqzResult<MonthlyInvites, MonthlyInviteError> {
        listCalls++
        return listResult
    }

    override suspend fun check(groupId: GroupId, phone: String): SaqzResult<MonthlyInvitePhoneCheckResult, MonthlyInviteError> {
        calls += "check $phone"
        return SaqzResult.Success(checks[phone] ?: MonthlyInvitePhoneCheckResult(MonthlyInvitePhoneCheck.FREE, null, null))
    }

    override suspend fun send(groupId: GroupId, requestId: String, people: List<MonthlyInvitePerson>): SaqzResult<MonthlyInviteSent, MonthlyInviteError> {
        sends += requestId to people
        return sendResult ?: SaqzResult.Success(MonthlyInviteSent(people.size, "https://saqz.app/?saqz_invite=44DF8SA8", "2026-10-27T15:00:00Z"))
    }

    override suspend fun copyLink(groupId: GroupId): SaqzResult<MonthlyInviteCopiedLink, MonthlyInviteError> {
        calls += "copyLink"
        return linkResult
    }

    override suspend fun resend(groupId: GroupId, inviteeId: String): SaqzResult<Int, MonthlyInviteError> {
        calls += "resend $inviteeId"
        return actionResult
    }

    override suspend fun resendAll(groupId: GroupId): SaqzResult<Int, MonthlyInviteError> {
        calls += "resendAll"
        return actionResult
    }

    override suspend fun remove(groupId: GroupId, inviteeId: String): SaqzResult<Unit, MonthlyInviteError> {
        calls += "remove $inviteeId"
        return unitResult
    }

    override suspend fun invalidateLink(groupId: GroupId): SaqzResult<Unit, MonthlyInviteError> {
        calls += "invalidate"
        return unitResult
    }
}

internal fun sampleMonthlyInvites(
    fee: MonthlyFeeDefault? = MonthlyFeeDefault(8_000, 10),
    link: MonthlyInviteLink = MonthlyInviteLink(MonthlyInviteLinkState.NONE, null, null),
    invitees: List<br.com.saqz.groups.domain.membership.MonthlyInvitee> = emptyList(),
    pausedRetryAt: String? = null,
) = MonthlyInvites(link, invitees, pausedRetryAt, fee)
