package br.com.saqz.access.application.clientapp

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.ClientApp
import br.com.saqz.sharedkernel.client.ClientPlatform
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Registra a versão do app de quem acabou de se autenticar, sem custar uma escrita por requisição:
 *  - cliente que não se identificou ([ClientApp.Legacy]) não grava;
 *  - a mesma versão e o mesmo build já guardados nas últimas [refreshAfter] não tocam o banco (a memória
 *    responde sozinha); versão ou build novos gravam na hora; passado [refreshAfter], grava de novo para
 *    renovar o "visto por último", que por isso tem resolução de ~6 h;
 *  - conta que ainda não existe e falha do banco só são tentadas de novo depois de [retryAfter].
 *
 * Quem chama decide o que fazer com a exceção do banco ([execute] a repassa, depois de adiar a próxima
 * tentativa): o filtro HTTP a engole, porque registrar a versão nunca pode derrubar a requisição.
 */
class RecordClientApp(
    private val store: ClientAppVersionStore,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshAfter: Duration = DEFAULT_REFRESH_AFTER,
    private val retryAfter: Duration = DEFAULT_RETRY_AFTER,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private data class Key(val subject: String, val platform: ClientPlatform)

    private sealed interface Seen {
        /** O que já foi guardado nesta janela: dois aparelhos com versões diferentes não se revezam no banco. */
        data class Recorded(val apps: Set<ClientApp.Identified>, val refreshAt: Instant) : Seen

        data class Waiting(val until: Instant) : Seen
    }

    private val seen = ConcurrentHashMap<Key, Seen>()

    fun execute(identity: RequestIdentity, app: ClientApp) {
        if (app !is ClientApp.Identified) return
        val key = Key(identity.subject, app.platform)
        val now = clock.instant()
        val previous = seen[key]
        if (!due(previous, app, now)) return

        val result = try {
            store.record(identity.subject, app, now, refreshAfter)
        } catch (failure: RuntimeException) {
            remember(key, Seen.Waiting(now.plus(retryAfter)))
            throw failure
        }
        remember(
            key,
            when (result) {
                ClientAppRecordResult.UNKNOWN_USER -> Seen.Waiting(now.plus(retryAfter))
                ClientAppRecordResult.WRITTEN, ClientAppRecordResult.UNCHANGED -> Seen.Recorded(
                    apps = if (previous is Seen.Recorded && now < previous.refreshAt) previous.apps + app else setOf(app),
                    refreshAt = now.plus(refreshAfter),
                )
            },
        )
    }

    private fun due(previous: Seen?, app: ClientApp.Identified, now: Instant): Boolean = when (previous) {
        null -> true
        is Seen.Waiting -> now >= previous.until
        is Seen.Recorded -> now > previous.refreshAt || app !in previous.apps
    }

    // ponytail: limpeza só quando enche, e zera tudo se ainda estiver cheio (as próximas requisições
    // regravam no máximo uma vez cada). Serve até ~20 mil usuários ativos por janela; acima disso,
    // trocar por Caffeine com expiração.
    private fun remember(key: Key, entry: Seen) {
        if (seen.size >= maxEntries && !seen.containsKey(key)) evict(clock.instant())
        seen[key] = entry
    }

    private fun evict(now: Instant) {
        seen.values.removeIf { entry ->
            when (entry) {
                is Seen.Recorded -> entry.refreshAt <= now
                is Seen.Waiting -> entry.until <= now
            }
        }
        if (seen.size >= maxEntries) seen.clear()
    }

    companion object {
        val DEFAULT_REFRESH_AFTER: Duration = Duration.ofHours(6)
        val DEFAULT_RETRY_AFTER: Duration = Duration.ofMinutes(1)
        const val DEFAULT_MAX_ENTRIES = 20_000
    }
}
