package br.com.saqz.sharedkernel.job

import org.slf4j.LoggerFactory

/**
 * Log de execução das tarefas agendadas, no padrão `chave=valor` do resto do backend. Só entram nomes de job,
 * contagens, durações e UUIDs: nada de nome, apelido, telefone, e-mail, texto de mensagem, token ou chave.
 *
 * - [cron]: uma linha `job_run` a cada execução, mesmo com zero (prova que o job rodou).
 * - [frequent]: a linha `job_run` só sai quando algo foi processado (15 s = 5.760 linhas/dia seriam ruído).
 * - [item] / [itemFailed]: uma linha `job_item` por item afetado, com os ids.
 */
object JobLog {
    private val log = LoggerFactory.getLogger("br.com.saqz.job")

    /** Job de horário marcado: sempre loga o resumo; as contagens do bloco vão na ordem em que ele devolve. */
    fun <T> cron(job: String, block: () -> T, counts: (T) -> Map<String, Int>): T = measure(job, always = true, block, counts)

    /** Job de intervalo curto: loga o resumo só quando alguma contagem passou de zero. */
    fun <T> frequent(job: String, block: () -> T, counts: (T) -> Map<String, Int>): T = measure(job, always = false, block, counts)

    fun item(job: String, event: String, vararg ids: Pair<String, Any?>) {
        log.info("job_item job={} event={}{}", job, event, ids.format())
    }

    /** Falha de um item: WARN com os mesmos ids e só o código do erro (a mensagem pode trazer dado pessoal). */
    fun itemFailed(job: String, event: String, reason: String, vararg ids: Pair<String, Any?>) {
        log.warn("job_item_failed job={} event={}{} error={}", job, event, ids.format(), reason)
    }

    /** Falha de um item por exceção: só a classe do erro entra no log. */
    fun itemFailed(job: String, event: String, error: Throwable?, vararg ids: Pair<String, Any?>) =
        itemFailed(job, event, error?.javaClass?.simpleName ?: "unknown", *ids)

    private fun <T> measure(job: String, always: Boolean, block: () -> T, counts: (T) -> Map<String, Int>): T {
        val started = System.nanoTime()
        val result = try {
            block()
        } catch (failure: Throwable) {
            log.warn("job_run_failed job={} durationMs={} error={}", job, elapsedMs(started), failure.javaClass.simpleName)
            throw failure
        }
        val values = counts(result)
        if (always || values.values.any { it > 0 }) {
            log.info("job_run job={}{} durationMs={}", job, values.entries.joinToString("") { " ${it.key}=${it.value}" }, elapsedMs(started))
        }
        return result
    }

    private fun elapsedMs(started: Long) = (System.nanoTime() - started) / 1_000_000

    private fun Array<out Pair<String, Any?>>.format() =
        filter { it.second != null }.joinToString("") { " ${it.first}=${it.second}" }
}
