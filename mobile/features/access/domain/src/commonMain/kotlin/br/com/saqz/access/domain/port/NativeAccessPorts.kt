package br.com.saqz.access.domain.port

interface Cancelable {
    fun cancel()
}

data class NativeUser(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
)

enum class NativeFailureCode {
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    AUTH_METHOD_CONFLICT,
    NETWORK_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    UNKNOWN,
}

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val user: NativeUser) : AuthState
}

sealed interface AuthResult {
    data class Success(val user: NativeUser) : AuthResult
    data object Cancelled : AuthResult
    data class Failure(val code: NativeFailureCode) : AuthResult
}

sealed interface OperationResult {
    data object Success : OperationResult
    data class Failure(val code: NativeFailureCode) : OperationResult
}

sealed interface TokenResult {
    data class Success(val token: String) : TokenResult
    data class Failure(val code: NativeFailureCode) : TokenResult
}

sealed interface ValueResult {
    data class Success(val value: String?) : ValueResult
    data class Failure(val code: NativeFailureCode) : ValueResult
}

interface AuthStateListener {
    fun onStateChanged(state: AuthState)
}

interface AuthCallback {
    fun complete(result: AuthResult)
}

interface ResultCallback {
    fun complete(result: OperationResult)
}

interface TokenCallback {
    fun complete(result: TokenResult)
}

interface ValueCallback {
    fun complete(result: ValueResult)
}

interface InviteCodeListener {
    fun onInviteCode(code: String)
}

interface NativeAuthPort {
    fun observe(listener: AuthStateListener): Cancelable
    fun createAccount(name: String, email: String, password: String, done: AuthCallback)
    fun signInWithPassword(email: String, password: String, done: AuthCallback)
    fun signInWithGoogle(done: AuthCallback)
    fun sendVerification(done: ResultCallback)
    fun reloadUser(done: AuthCallback)
    fun sendPasswordReset(email: String, done: ResultCallback)
    fun updateDisplayName(name: String, done: AuthCallback)
    fun idToken(forceRefresh: Boolean, done: TokenCallback)
    fun signOut(done: ResultCallback)
}

interface NativeLinkPort {
    fun start(listener: InviteCodeListener): Cancelable
}

interface LocalAccessStatePort {
    fun readSelectedGroupId(done: ValueCallback)
    fun writeSelectedGroupId(value: String?, done: ResultCallback)
    fun readPendingInvite(done: ValueCallback)
    fun writePendingInvite(value: String?, done: ResultCallback)
}

interface NativeSharePort {
    fun share(text: String, done: ResultCallback)
}
