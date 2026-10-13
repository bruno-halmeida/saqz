@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.saqz.webapp.interop

import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/** Usuário como o saqz-bridge.js o fotografa; o Firebase JS não tem tipo que cruze o Wasm. */
@Serializable
internal data class AuthBridgeUser(
    val uid: String,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val displayName: String? = null,
)

/** Resposta única de toda operação do bridge. Em falha, [code] é o `error.code` cru do Firebase. */
@Serializable
internal data class AuthBridgeReply(
    val ok: Boolean = false,
    val code: String? = null,
    val user: AuthBridgeUser? = null,
    val token: String? = null,
)

/** Código sintético de quando o bridge nem chega a responder (função ausente, resposta ilegível). */
internal const val BRIDGE_UNAVAILABLE = "saqz/bridge-unavailable"

// O bridge é módulo ES e carrega depois do wasm; por isso é esta promessa, e não o objeto, que se espera.
@JsFun("() => Promise.resolve(window.saqzBridgeReady).then((bridge) => !!bridge && !bridge.failed, () => false)")
private external fun jsBridgeReady(): Promise<JsBoolean>

@JsFun("(done) => window.saqzBridge.observe(done)")
private external fun jsObserve(done: (String) -> Unit): Int

@JsFun("(id) => window.saqzBridge.unobserve(id)")
private external fun jsUnobserve(id: Int)

@JsFun("() => window.saqzBridge.currentSubject()")
private external fun jsCurrentSubject(): String?

@JsFun("(name, email, password, done) => window.saqzBridge.createAccount(name, email, password, done)")
private external fun jsCreateAccount(name: String, email: String, password: String, done: (String) -> Unit)

@JsFun("(email, password, done) => window.saqzBridge.signInWithPassword(email, password, done)")
private external fun jsSignInWithPassword(email: String, password: String, done: (String) -> Unit)

@JsFun("(customToken, done) => window.saqzBridge.signInWithCustomToken(customToken, done)")
private external fun jsSignInWithCustomToken(customToken: String, done: (String) -> Unit)

@JsFun("(done) => window.saqzBridge.signInWithGoogle(done)")
private external fun jsSignInWithGoogle(done: (String) -> Unit)

@JsFun("(subject, password, done) => window.saqzBridge.reauthenticateWithPassword(subject, password, done)")
private external fun jsReauthenticateWithPassword(subject: String, password: String, done: (String) -> Unit)

@JsFun("(subject, done) => window.saqzBridge.reauthenticateWithGoogle(subject, done)")
private external fun jsReauthenticateWithGoogle(subject: String, done: (String) -> Unit)

@JsFun("(done) => window.saqzBridge.sendVerification(done)")
private external fun jsSendVerification(done: (String) -> Unit)

@JsFun("(done) => window.saqzBridge.reloadUser(done)")
private external fun jsReloadUser(done: (String) -> Unit)

@JsFun("(name, done) => window.saqzBridge.updateDisplayName(name, done)")
private external fun jsUpdateDisplayName(name: String, done: (String) -> Unit)

@JsFun("(forceRefresh, done) => window.saqzBridge.idToken(forceRefresh, done)")
private external fun jsIdToken(forceRefresh: Boolean, done: (String) -> Unit)

@JsFun("(done) => window.saqzBridge.signOut(done)")
private external fun jsSignOut(done: (String) -> Unit)

private const val BRIDGE_TIMEOUT_MILLIS = 20_000L

/**
 * Espera o saqz-bridge.js terminar de carregar o Firebase Auth. `false` quando ele falhou
 * (gstatic fora do ar, sem rede) ou nem respondeu dentro do prazo: sem ele não há login, então
 * quem chama deve parar e avisar em vez de montar um app que não entra.
 */
internal suspend fun awaitAuthBridge(timeoutMillis: Long = BRIDGE_TIMEOUT_MILLIS): Boolean =
    withTimeoutOrNull(timeoutMillis) { jsBridgeReady().await<JsBoolean>().toBoolean() } ?: false

/**
 * Chamadas ao Firebase Auth JS (saqz-bridge.js). Cada operação chama `done` uma vez, no mesmo
 * thread do navegador; erro do bridge vira [AuthBridgeReply] com [BRIDGE_UNAVAILABLE], nunca exceção.
 */
@Suppress("TooManyFunctions")
internal object AuthBridge {
    private val json = Json { ignoreUnknownKeys = true }

    /** Emite o estado atual e depois cada mudança; devolve o id para [unobserve]. */
    fun observe(listener: (AuthBridgeUser?) -> Unit): Int =
        runCatching { jsObserve { raw -> listener(decode(raw).user) } }
            .getOrElse {
                // Sem bridge não há sessão a restaurar: o app precisa sair do "carregando" como deslogado.
                listener(null)
                NO_OBSERVER
            }

    fun unobserve(id: Int) {
        if (id != NO_OBSERVER) runCatching { jsUnobserve(id) }
    }

    /** O uid da sessão agora, para quem precisa conferir que a conta não trocou no meio de uma operação. */
    fun currentSubject(): String? = runCatching { jsCurrentSubject() }.getOrNull()

    fun createAccount(name: String, email: String, password: String, done: (AuthBridgeReply) -> Unit) =
        call(done) { jsCreateAccount(name, email, password, it) }

    fun signInWithPassword(email: String, password: String, done: (AuthBridgeReply) -> Unit) =
        call(done) { jsSignInWithPassword(email, password, it) }

    fun signInWithCustomToken(customToken: String, done: (AuthBridgeReply) -> Unit) =
        call(done) { jsSignInWithCustomToken(customToken, it) }

    fun signInWithGoogle(done: (AuthBridgeReply) -> Unit) =
        call(done) { jsSignInWithGoogle(it) }

    fun reauthenticateWithPassword(subject: String, password: String, done: (AuthBridgeReply) -> Unit) =
        call(done) { jsReauthenticateWithPassword(subject, password, it) }

    fun reauthenticateWithGoogle(subject: String, done: (AuthBridgeReply) -> Unit) =
        call(done) { jsReauthenticateWithGoogle(subject, it) }

    fun sendVerification(done: (AuthBridgeReply) -> Unit) =
        call(done) { jsSendVerification(it) }

    fun reloadUser(done: (AuthBridgeReply) -> Unit) =
        call(done) { jsReloadUser(it) }

    fun updateDisplayName(name: String, done: (AuthBridgeReply) -> Unit) =
        call(done) { jsUpdateDisplayName(name, it) }

    fun idToken(forceRefresh: Boolean, done: (AuthBridgeReply) -> Unit) =
        call(done) { jsIdToken(forceRefresh, it) }

    fun signOut(done: (AuthBridgeReply) -> Unit) =
        call(done) { jsSignOut(it) }

    // Falha síncrona (função ausente no objeto do bridge) vira a mesma resposta de uma falha comum.
    private fun call(done: (AuthBridgeReply) -> Unit, invoke: ((String) -> Unit) -> Unit) {
        runCatching { invoke { raw -> done(decode(raw)) } }
            .onFailure { done(AuthBridgeReply(code = BRIDGE_UNAVAILABLE)) }
    }

    private fun decode(raw: String): AuthBridgeReply =
        runCatching { json.decodeFromString(AuthBridgeReply.serializer(), raw) }
            .getOrElse { AuthBridgeReply(code = BRIDGE_UNAVAILABLE) }

    private const val NO_OBSERVER = -1
}
