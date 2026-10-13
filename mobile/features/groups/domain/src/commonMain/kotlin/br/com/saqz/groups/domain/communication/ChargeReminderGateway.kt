package br.com.saqz.groups.domain.communication

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

/** [alreadyRemindedCount]: cobranças que o servidor não reenviou por já terem sido cobradas nas últimas 12 h. */
data class ChargeReminderReceipt(val notificationCount: Int, val alreadyRemindedCount: Int = 0)
fun interface ChargeReminderGateway {
    suspend fun send(groupId: GroupId, requestId: String, chargeIds: List<String>): SaqzResult<ChargeReminderReceipt, CommunicationError>
}
