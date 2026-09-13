package br.com.saqz.composeapp.receivables

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeReauthentication
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.receivables.domain.port.ReceiptReauthenticationCallback
import br.com.saqz.receivables.domain.port.ReceiptReauthenticationPort
import br.com.saqz.receivables.domain.port.ReceiptReauthenticationResult

internal class ReceiptReauthenticationBinding(private val auth: NativeAuthPort) : ReceiptReauthenticationPort {
    override fun password(password: String, done: ReceiptReauthenticationCallback) =
        auth.reauthenticate(NativeReauthentication.Password(password), callback(done))

    override fun google(done: ReceiptReauthenticationCallback) = auth.reauthenticate(NativeReauthentication.Google, callback(done))

    private fun callback(done: ReceiptReauthenticationCallback) = object : AuthCallback {
        override fun complete(result: AuthResult) {
            done.onReauthenticated(when (result) {
                is AuthResult.Success -> ReceiptReauthenticationResult.AUTHENTICATED
                AuthResult.Cancelled -> ReceiptReauthenticationResult.CANCELLED
                is AuthResult.Failure -> if (result.code == NativeFailureCode.INVALID_CREDENTIALS) {
                    ReceiptReauthenticationResult.INVALID_CREDENTIALS
                } else ReceiptReauthenticationResult.UNAVAILABLE
            })
        }
    }
}
