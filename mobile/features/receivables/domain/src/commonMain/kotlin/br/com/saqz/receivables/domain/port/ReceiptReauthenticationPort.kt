package br.com.saqz.receivables.domain.port

enum class ReceiptReauthenticationResult { AUTHENTICATED, CANCELLED, INVALID_CREDENTIALS, UNAVAILABLE }
fun interface ReceiptReauthenticationCallback { fun onReauthenticated(result: ReceiptReauthenticationResult) }

/** Password is transient; implementations reauthenticate the current identity and refresh its token. */
interface ReceiptReauthenticationPort {
    fun password(password: String, done: ReceiptReauthenticationCallback)
    fun google(done: ReceiptReauthenticationCallback)
}
