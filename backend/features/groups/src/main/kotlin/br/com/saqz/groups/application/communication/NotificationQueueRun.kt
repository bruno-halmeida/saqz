package br.com.saqz.groups.application.communication

/** Uma entrega tentada da fila: quantos destinos aceitaram e quantos falharam (ou ficaram para nova tentativa). */
data class QueueStep(val delivered: Int = 0, val failed: Int = 0)

/** Resultado de uma rodada de [drain][br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationPush]: itens tirados da fila e o que deu em cada um. */
data class QueueRun(val processed: Int = 0, val delivered: Int = 0, val failed: Int = 0) {
    operator fun plus(step: QueueStep) = QueueRun(processed + 1, delivered + step.delivered, failed + step.failed)
}
