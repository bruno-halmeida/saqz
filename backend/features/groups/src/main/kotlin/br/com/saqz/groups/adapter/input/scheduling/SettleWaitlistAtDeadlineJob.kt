package br.com.saqz.groups.adapter.input.scheduling

import br.com.saqz.groups.application.attendance.SettleWaitlistAtDeadline
import org.springframework.scheduling.annotation.Scheduled

/** A cada minuto: vencido o prazo, a fila entra nas vagas (ordem de chegada) ou o dono é avisado (escolha manual). */
class SettleWaitlistAtDeadlineJob(private val settle: SettleWaitlistAtDeadline) {
    @Scheduled(fixedDelayString = "\${saqz.games.waitlist-deadline-delay-ms:60000}", initialDelayString = "\${saqz.games.waitlist-deadline-initial-delay-ms:45000}")
    fun run() { settle.run() }
}
