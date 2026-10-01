package br.com.saqz.groups.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.GroupWhatsAppBinding
import br.com.saqz.groups.domain.communication.GroupWhatsAppGateway
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus

/** Vínculo com o WhatsApp em memória, com um `shouldFail` para o caminho triste. */
internal class FakeGroupWhatsAppGateway(
    var binding: GroupWhatsAppBinding = GroupWhatsAppBinding(bound = false),
    var linked: GroupWhatsAppBinding = GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.ACTIVE),
    var shouldFail: Boolean = false,
) : GroupWhatsAppGateway {
    val bindingCalls = mutableListOf<GroupId>()
    val linkCalls = mutableListOf<Pair<GroupId, String>>()
    val enabledCalls = mutableListOf<Pair<GroupId, Boolean>>()

    override suspend fun binding(groupId: GroupId): SaqzResult<GroupWhatsAppBinding, CommunicationError> {
        bindingCalls += groupId
        return failure() ?: SaqzResult.Success(binding)
    }

    override suspend fun link(groupId: GroupId, inviteLink: String): SaqzResult<GroupWhatsAppBinding, CommunicationError> {
        linkCalls += groupId to inviteLink
        return failure() ?: SaqzResult.Success(linked)
    }

    override suspend fun setEnabled(groupId: GroupId, enabled: Boolean): SaqzResult<GroupWhatsAppBinding, CommunicationError> {
        enabledCalls += groupId to enabled
        val next = binding.copy(
            bound = true,
            status = if (enabled) GroupWhatsAppStatus.ACTIVE else GroupWhatsAppStatus.DISABLED,
        )
        return failure() ?: SaqzResult.Success(next)
    }

    private fun failure(): SaqzResult.Failure<CommunicationError>? =
        if (shouldFail) SaqzResult.Failure(CommunicationError(DataError.Connectivity)) else null
}
