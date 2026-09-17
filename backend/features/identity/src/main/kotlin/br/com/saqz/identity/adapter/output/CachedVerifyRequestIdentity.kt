package br.com.saqz.identity.adapter.output

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Guarda por pouco tempo o resultado de um token já verificado. A verificação no provedor inclui
 * a checagem de revogação, que é uma ida à rede por requisição; com o cache ela acontece uma vez
 * por janela. Custo aceito: uma revogação leva até [ttl] para valer aqui. Requisição sensível
 * (pagamento, exclusão) não passa por este caminho: o filtro chama [executeFresh].
 *
 * Só resultado Verified entra: rejeição e provedor fora do ar são sempre reavaliados. A entrada
 * nunca sobrevive ao `exp` do próprio token, e token sem `exp` legível não é guardado.
 */
class CachedVerifyRequestIdentity(
    private val delegate: VerifyRequestIdentity,
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofMinutes(3),
    private val maxEntries: Int = 10_000,
) : VerifyRequestIdentity {
    private class Entry(val verification: TokenVerification.Verified, val expiresAtEpochSeconds: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    override fun execute(token: RawIdentityToken): TokenVerification {
        val now = clock.instant().epochSecond
        val key = digest(token.value)
        entries[key]?.let { if (it.expiresAtEpochSeconds > now) return it.verification }

        val verification = delegate.execute(token)
        val tokenExpiry = expiryOf(token.value)
        if (verification is TokenVerification.Verified && tokenExpiry != null && tokenExpiry > now) {
            if (entries.size >= maxEntries) evict(now)
            entries[key] = Entry(verification, minOf(now + ttl.seconds, tokenExpiry))
        } else {
            entries.remove(key)
        }
        return verification
    }

    override fun executeFresh(token: RawIdentityToken): TokenVerification {
        val verification = delegate.execute(token)
        if (verification !is TokenVerification.Verified) entries.remove(digest(token.value))
        return verification
    }

    // ponytail: limpeza só quando enche, e zera tudo se ainda estiver cheio. Serve até ~10 mil
    // tokens ativos por janela; acima disso, trocar por Caffeine.
    private fun evict(now: Long) {
        entries.values.removeIf { it.expiresAtEpochSeconds <= now }
        if (entries.size >= maxEntries) entries.clear()
    }

    // A chave é o hash: o token cru não fica parado na memória.
    private fun digest(token: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private fun expiryOf(token: String): Long? = runCatching {
        val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1]))
        EXP.find(payload)?.groupValues?.get(1)?.toLong()
    }.getOrNull()

    private companion object {
        val EXP = Regex("\"exp\"\\s*:\\s*(\\d+)")
    }
}
