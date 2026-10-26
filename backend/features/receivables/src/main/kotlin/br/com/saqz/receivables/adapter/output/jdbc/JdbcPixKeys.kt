package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.PaymentCodeProvider
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * Chave aleatória (EVP) da subconta (seção B/G): garantida ao ativar e tentada de novo pelo job enquanto não estiver ativa.
 * A consulta vem antes da criação no adaptador, então repetir nunca cria uma segunda chave.
 */
class JdbcPixKeys(dataSource: DataSource, private val provider: PaymentCodeProvider) {
    private val jdbc = JdbcClient.create(dataSource)

    fun ensure(accountId: UUID): Boolean {
        val ready = jdbc.sql("SELECT pix_key_ready FROM receivable_accounts WHERE id=:id AND registration='APPROVED'")
            .param("id", accountId).query(Boolean::class.java).optional().orElse(null) ?: return false
        if (ready) return true
        val key = provider.ensurePixKey(accountId) ?: return false
        jdbc.sql("UPDATE receivable_accounts SET pix_key_id=:id,pix_key=:key,pix_key_ready=:ready WHERE id=:account")
            .param("id", key.id).param("key", key.key.ifBlank { null }).param("ready", key.active && key.key.isNotBlank())
            .param("account", accountId).update()
        return key.active
    }

    fun recoverPending() {
        JobLog.frequent("pix_keys", {
            jdbc.sql("""SELECT DISTINCT a.id FROM receivable_accounts a JOIN receivable_group_links l ON l.account_id=a.id AND l.enabled
                WHERE NOT a.pix_key_ready AND a.registration='APPROVED' LIMIT 20""").query(UUID::class.java).list().filterNotNull()
                .count { runCatching { ensure(it) }.getOrDefault(false) }
        }) { mapOf("ready" to it) }
    }
}
