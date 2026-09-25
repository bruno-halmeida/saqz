package br.com.saqz.access.application.emailverification

import br.com.saqz.access.application.session.AppOnboardingCode
import br.com.saqz.access.application.session.AppOnboardingDigest
import java.time.Clock
import java.time.Instant

/**
 * A conta também se confirma pelo WhatsApp: o link vai para o telefone do cadastro e,
 * aberto, grava aquele número como confirmado. Não mexe no `email_verified` do Firebase —
 * quem prova o telefone não provou o e-mail.
 */
interface PhoneConfirmationStore {
    /** Troca o token aberto por este. Devolve o telefone, ou null sem telefone ou com ele já confirmado. */
    fun issue(subject: String, digest: AppOnboardingDigest, expiresAt: Instant): String?

    /** Consome o token; false se venceu, não existe ou o telefone mudou depois do envio. */
    fun confirm(digest: AppOnboardingDigest, now: Instant): Boolean
}

fun interface PhoneConfirmationSender {
    fun send(phone: String, code: AppOnboardingCode)
}

class ConfirmAccountPhone(
    private val store: PhoneConfirmationStore,
    private val clock: Clock,
) {
    fun confirm(rawCode: String): Boolean {
        val code = AppOnboardingCode.from(rawCode) ?: return false
        return store.confirm(AppOnboardingDigest.sha256(code), clock.instant())
    }
}
