package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeReauthentication
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.webapp.interop.AuthBridge
import br.com.saqz.webapp.interop.AuthBridgeReply
import br.com.saqz.webapp.interop.AuthBridgeUser
import br.com.saqz.webapp.interop.BRIDGE_UNAVAILABLE
import kotlinx.coroutines.CoroutineScope

/**
 * `NativeAuthPort` sobre o Firebase Auth JS (saqz-bridge.js). Espelha o `AndroidAuthAdapter`: o
 * que cada método devolve em cada caso é o mesmo, e só a origem muda (popup do Google no lugar do
 * Credential Manager).
 *
 * Sem Apple: `supportsAppleSignIn()` é falso e `signInWithApple`/reautenticação Apple ficam no
 * padrão da porta (`PROVIDER_UNAVAILABLE`). `prepareAccountDeletion` também fica no padrão
 * (sucesso), como no Android: só o Apple tem consentimento de provedor a revogar antes de excluir.
 *
 * O bridge responde por callback no único thread do navegador; [scope] fica só pela assinatura
 * que a composição já usa, porque aqui não há nada a despachar.
 */
@Suppress("TooManyFunctions") // espelha o contrato inteiro de NativeAuthPort, como o SerializedNativeAuthPort
internal class WebAuthAdapter(@Suppress("UnusedPrivateProperty") scope: CoroutineScope) : NativeAuthPort {
    override fun observe(listener: AuthStateListener): Cancelable {
        val id = AuthBridge.observe { user ->
            listener.onStateChanged(user?.let { AuthState.SignedIn(it.toNative()) } ?: AuthState.SignedOut)
        }
        return object : Cancelable {
            override fun cancel() = AuthBridge.unobserve(id)
        }
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) =
        AuthBridge.createAccount(name, email, password) { reply ->
            if (reply.ok && reply.user != null) {
                // O token emitido no cadastro é anterior ao nome: sem o claim `name` o
                // `PUT api/session` volta 400 e o bootstrap não sai da tela de erro. Um token
                // novo aqui faz o primeiro pedido ao backend já levar o nome. Falha ao
                // renovar não desfaz o cadastro; o "Tentar novamente" renova de novo.
                AuthBridge.idToken(forceRefresh = true) { done.complete(reply.toAuthResult()) }
            } else {
                done.complete(reply.toAuthResult())
            }
        }

    override fun signInWithPassword(email: String, password: String, done: AuthCallback) =
        AuthBridge.signInWithPassword(email, password) { done.complete(it.toAuthResult()) }

    override fun signInWithCustomToken(customToken: String, done: AuthCallback) =
        AuthBridge.signInWithCustomToken(customToken) { done.complete(it.toAuthResult()) }

    override fun signInWithGoogle(done: AuthCallback) =
        AuthBridge.signInWithGoogle { done.complete(it.toAuthResult()) }

    // Não há login com Apple no navegador (o app web não oferece o botão).
    override fun supportsAppleSignIn(): Boolean = false

    override fun reauthenticate(request: NativeReauthentication, done: AuthCallback) {
        val subject = AuthBridge.currentSubject()
            ?: return done.complete(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))
        when (request) {
            NativeReauthentication.Apple -> done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
            is NativeReauthentication.Password ->
                AuthBridge.reauthenticateWithPassword(subject, request.password) { done.complete(it.toAuthResult()) }
            NativeReauthentication.Google ->
                AuthBridge.reauthenticateWithGoogle(subject) { done.complete(it.toAuthResult()) }
        }
    }

    override fun sendVerification(done: ResultCallback) =
        AuthBridge.sendVerification { done.complete(it.toOperationResult()) }

    override fun reloadUser(done: AuthCallback) =
        AuthBridge.reloadUser { done.complete(it.toAuthResult()) }

    override fun updateDisplayName(name: String, done: AuthCallback) =
        AuthBridge.updateDisplayName(name) { done.complete(it.toAuthResult()) }

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
        AuthBridge.idToken(forceRefresh) { reply ->
            val token = reply.token
            done.complete(
                if (reply.ok && !token.isNullOrBlank()) TokenResult.Success(token)
                else TokenResult.Failure(reply.failureCode()),
            )
        }

    // No Android há ainda a limpeza do estado do Credential Manager; no navegador o provedor não
    // guarda conta que o seletor do próximo popup não volte a oferecer, então só o Firebase sai.
    override fun signOut(done: ResultCallback) =
        AuthBridge.signOut { done.complete(it.toOperationResult()) }
}

private fun AuthBridgeUser.toNative() = NativeUser(uid, email, emailVerified, displayName)

private fun AuthBridgeReply.toAuthResult(): AuthResult = when {
    ok && user != null -> AuthResult.Success(user.toNative())
    !ok && isCancellation(code) -> AuthResult.Cancelled
    else -> AuthResult.Failure(failureCode())
}

private fun AuthBridgeReply.toOperationResult(): OperationResult =
    if (ok) OperationResult.Success else OperationResult.Failure(failureCode())

// `ok` sem o campo esperado é resposta torta do bridge, não sucesso: UNKNOWN em vez de fingir que deu certo.
private fun AuthBridgeReply.failureCode(): NativeFailureCode =
    if (ok) NativeFailureCode.UNKNOWN else mapFirebaseWebFailure(code)

/** O popup foi fechado ou substituído: a pessoa desistiu, o que não é falha (como `GetCredentialCancellationException`). */
internal fun isCancellation(code: String?): Boolean = when (code) {
    "auth/popup-closed-by-user",
    "auth/cancelled-popup-request",
    "auth/user-cancelled",
    -> true
    else -> false
}

/**
 * `error.code` do Firebase JS para o código da porta, alinhado ao `mapFirebaseFailure` do Android
 * (famílias de exceção + `errorCode`). Divergências do Android estão marcadas.
 */
internal fun mapFirebaseWebFailure(code: String?): NativeFailureCode = when (code) {
    // Android: FirebaseAuthInvalidCredentialsException e FirebaseAuthInvalidUserException. Inclui
    // user-disabled (conta desativada entra como credencial inválida, no Android e no iOS) e o
    // token customizado inválido ou vencido, que é o que o link temporário do cadastro web vira.
    "auth/invalid-credential",
    "auth/wrong-password",
    "auth/user-not-found",
    "auth/invalid-email",
    "auth/user-disabled",
    "auth/user-token-expired",
    "auth/invalid-user-token",
    "auth/user-mismatch",
    "auth/invalid-custom-token",
    "auth/custom-token-mismatch",
    "saqz/missing-user",
    -> NativeFailureCode.INVALID_CREDENTIALS
    // Android: FirebaseAuthUserCollisionException, que só vira conflito de método no
    // ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL. credential-already-in-use fica aqui, como lá.
    "auth/email-already-in-use",
    "auth/credential-already-in-use",
    -> NativeFailureCode.EMAIL_IN_USE
    "auth/account-exists-with-different-credential" -> NativeFailureCode.AUTH_METHOD_CONFLICT
    "auth/weak-password" -> NativeFailureCode.WEAK_PASSWORD
    "auth/network-request-failed" -> NativeFailureCode.NETWORK_UNAVAILABLE
    "auth/too-many-requests" -> NativeFailureCode.TOO_MANY_REQUESTS
    // Divergência do pedido ("resto UNKNOWN"), no espírito do Android (Credential Manager
    // indisponível): o Google não pôde ser aberto neste navegador/domínio, e a tela tem mensagem
    // própria para isso (auth_error_provider) em vez do erro genérico.
    "auth/popup-blocked",
    "auth/operation-not-allowed",
    "auth/operation-not-supported-in-this-environment",
    "auth/unauthorized-domain",
    BRIDGE_UNAVAILABLE,
    -> NativeFailureCode.PROVIDER_UNAVAILABLE
    else -> NativeFailureCode.UNKNOWN
}
