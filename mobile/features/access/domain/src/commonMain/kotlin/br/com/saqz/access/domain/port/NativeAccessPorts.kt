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

    // O provedor barrou por excesso de tentativas. É o **único** bloqueio que existe de
    // verdade: o login roda no cliente contra o Firebase e o nosso backend nunca vê
    // tentativa que falhou, então quem conta e quem tranca é ele. Sem este código a
    // recusa chegaria como UNKNOWN e a 1a não teria como dizer "conta bloqueada".
    TOO_MANY_REQUESTS,
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

sealed interface ProfilePhotoResult {
    // Bytes já recortados e recodificados pela plataforma: o envio é HTTP multipart,
    // então o acesso não precisa do arquivo de origem nem de um handle para ele.
    data class Selected(val bytes: ByteArray, val mediaType: String) : ProfilePhotoResult {
        init {
            require(bytes.isNotEmpty() && mediaType.isNotBlank())
        }

        override fun equals(other: Any?): Boolean =
            other is Selected && bytes.contentEquals(other.bytes) && mediaType == other.mediaType

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
    }

    data object Cancelled : ProfilePhotoResult
    data object Failed : ProfilePhotoResult
}

interface ProfilePhotoCallback {
    fun complete(result: ProfilePhotoResult)
}

/**
 * Escolher e recodificar imagem é capacidade de plataforma, não conceito de grupo: os
 * adapters desta porta delegam para as mesmas implementações que `:features:groups` usa
 * (`AndroidPhotoSelectionAdapter`/`AndroidPhotoEncoder` e `SaqzIOS/GroupsPhoto/`), sem
 * cópia. O envio em si vai por HTTP, não por aqui.
 *
 * O `Cancelable` é quem desiste da escolha ainda aberta — a tela morreu, o escopo caiu —
 * para o adapter apagar o arquivo temporário. Desistência da pessoa é
 * `ProfilePhotoResult.Cancelled`, que ainda chega pelo callback.
 */
interface NativeProfilePhotoPort {
    fun chooseCamera(done: ProfilePhotoCallback): Cancelable
    fun chooseLibrary(done: ProfilePhotoCallback): Cancelable
}
