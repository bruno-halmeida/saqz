package br.com.saqz.groups.adapter.input.scheduling

import br.com.saqz.groups.application.communication.GameFeeUndefinedNotices
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.scheduling.annotation.Scheduled

/** Aviso aos grupos sem valor por jogo (DEB-146); o bean só existe com `saqz.groups.game-fee-notice.enabled=true`. */
class GameFeeUndefinedNoticeJob(private val notices: GameFeeUndefinedNotices) {
    @Scheduled(fixedDelayString = "\${saqz.groups.game-fee-notice.delay-ms:3600000}", initialDelayString = "\${saqz.groups.game-fee-notice.initial-delay-ms:120000}")
    fun run() {
        JobLog.frequent("game_fee_undefined_notice", { notices.run() }) { mapOf("sent" to it) }
    }
}
