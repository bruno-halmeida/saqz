package br.com.saqz.groups.adapter.input.scheduling

import br.com.saqz.groups.application.communication.RemindStalledEntryRequests
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.scheduling.annotation.Scheduled

/** Todo dia às 10h de São Paulo: pedido de entrada sem resposta há 2 dias ganha um segundo aviso, uma vez só (DEB-068). */
class RemindStalledEntryRequestsJob(private val remind: RemindStalledEntryRequests) {
    @Scheduled(cron = "\${saqz.notifications.entry-request-reminder.cron:0 0 10 * * *}", zone = "America/Sao_Paulo")
    fun run() { JobLog.cron("entry_request_reminder", { remind.run() }) { mapOf("reminded" to it) } }
}
