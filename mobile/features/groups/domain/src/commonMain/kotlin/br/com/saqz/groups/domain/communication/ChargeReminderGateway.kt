package br.com.saqz.groups.domain.communication

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

data class ChargeReminderReceipt(val notificationCount: Int)
fun interface ChargeReminderGateway {
    suspend fun send(groupId: GroupId, requestId: String, chargeIds: List<String>): SaqzResult<ChargeReminderReceipt, CommunicationError>
}
