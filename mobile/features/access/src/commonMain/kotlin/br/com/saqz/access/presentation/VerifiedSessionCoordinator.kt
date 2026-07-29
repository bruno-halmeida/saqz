package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.domain.session.SessionInvalidator
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionAccessState {
    data object SignedOut : SessionAccessState

    /**
     * A tela 1c: nome, telefone e foto no mesmo estado, porque o export os junta numa tela
     * só. Substitui o par `CompletingName`/`CompletingPhone`.
     *
     * A tela é uma, mas o momento não: [session] nulo é o passo **antes** do bootstrap, de
     * quem entrou por um provedor que não deu nome utilizável; preenchido é o passo depois,
     * de quem tem sessão e ainda deve o telefone. A assimetria não é nossa — é do backend,
     * onde o nome é pré-condição do bootstrap e o telefone é pós-condição.
     */
    data class CompletingIdentity(
        val session: AccessSession?,
        val name: String = "",
        val phone: String = "",
        val photo: ProfilePhotoResult.Selected? = null,
        val isLoading: Boolean = false,
        val error: AuthUiError? = null,
        val invalidName: Boolean = false,
        val invalidPhone: Boolean = false,
        /**
         * A foto foi escolhida e o envio dela falhou — só ela. Nome e telefone gravaram,
         * e é por isso que o sinal é um aviso e não um erro: o cadastro está de pé, o que
         * falta é o JPEG. A foto sai do estado junto, senão o próximo toque tentaria
         * exatamente o envio que acabou de falhar.
         */
        val photoFailed: Boolean = false,
    ) : SessionAccessState

    data object Bootstrapping : SessionAccessState

    data object BootstrapError : SessionAccessState

    data class Ready(val session: AccessSession) : SessionAccessState
}

/**
 * O sinal que o VUL-76 trouxe do backend até `AccessUser`, exposto onde a faixa de e-mail
 * não confirmado (VUL-91) o lê. Derivado em vez de copiado: um campo repetido no [Ready]
 * poderia divergir da sessão que o próprio [Ready] carrega.
 */
val SessionAccessState.Ready.emailVerified: Boolean get() = session.user.emailVerified

sealed interface SessionIntent {
    data class Accept(val transition: AuthTransition) : SessionIntent

    data class UpdateName(val value: String) : SessionIntent

    data class UpdatePhone(val value: String) : SessionIntent

    data class UpdatePhoto(val value: ProfilePhotoResult.Selected?) : SessionIntent

    data object CompleteIdentity : SessionIntent

    data object RetryBootstrap : SessionIntent

    data object Logout : SessionIntent
}

class SessionAccessStateMachine(
    private val auth: NativeAuthPort,
    private val localState: LocalAccessStatePort,
    private val session: SessionGateway,
    private val scope: CoroutineScope,
) : SessionInvalidator {
    private val mutableState = MutableStateFlow<SessionAccessState>(SessionAccessState.SignedOut)
    val state: StateFlow<SessionAccessState> = mutableState.asStateFlow()
    private var currentUser: NativeUser? = null
    private var loggingOut = false

    /**
     * O telefone digitado antes do bootstrap, esperando a sessão existir para poder subir.
     * Vive fora do estado porque o bootstrap passa por [SessionAccessState.Bootstrapping],
     * que não tem onde guardá-lo — e porque um `RetryBootstrap` depois de uma queda precisa
     * reencontrá-lo em vez de perder o que a pessoa já digitou.
     */
    private var pendingIdentity: PendingIdentity? = null

    fun onIntent(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.Accept -> when (val transition = intent.transition) {
                is AuthTransition.Authenticated -> routeIdentity(transition.user)
            }
            is SessionIntent.UpdateName -> updateIdentity { copy(name = intent.value, invalidName = false) }
            is SessionIntent.UpdatePhone -> updateIdentity { copy(phone = intent.value, invalidPhone = false) }
            is SessionIntent.UpdatePhoto -> updateIdentity { copy(photo = intent.value, photoFailed = false) }
            SessionIntent.CompleteIdentity -> completeIdentity()
            SessionIntent.RetryBootstrap -> retryBootstrap()
            SessionIntent.Logout -> logout()
        }
    }

    private fun updateIdentity(edit: SessionAccessState.CompletingIdentity.() -> SessionAccessState.CompletingIdentity) {
        val current = mutableState.value as? SessionAccessState.CompletingIdentity ?: return
        if (!current.isLoading) mutableState.value = current.edit().copy(error = null)
    }

    /**
     * Um botão, dois caminhos, decididos por [SessionAccessState.CompletingIdentity.session].
     *
     * A foto sobe junto, mas nunca manda no resultado: quem decide se a pessoa entra são o
     * nome e o telefone (VUL-87).
     */
    private fun completeIdentity() {
        val current = mutableState.value as? SessionAccessState.CompletingIdentity ?: return
        if (current.isLoading) return
        val name = normalizedDisplayName(current.name)
        val phone = normalizedBrMobilePhone(current.phone)
        if (name == null || phone == null) {
            mutableState.value = current.copy(invalidName = name == null, invalidPhone = phone == null)
            return
        }
        // `photoFailed` zera aqui junto com os outros sinais: tocar de novo é recomeçar o
        // envio, e o aviso da tentativa anterior não pode sobreviver a ela — se sobrevivesse,
        // um segundo toque sem foto para subir manteria a pessoa presa na 1c com o recado
        // de um envio que nem aconteceu.
        val submitting = current.copy(
            isLoading = true,
            error = null,
            invalidName = false,
            invalidPhone = false,
            photoFailed = false,
        )
        mutableState.value = submitting
        if (current.session == null) claimNameThenBootstrap(submitting, name, phone)
        else scope.launch { submitProfile(submitting, name, phone) }
    }

    /**
     * O caminho de quem ainda não tem sessão. `BootstrapSession` recusa a identidade sem
     * nome utilizável **antes** de tocar o repositório, então pedir o nome depois do
     * bootstrap seria pedi-lo numa tela que nunca abre: a pessoa ficaria presa no
     * `BootstrapError`. O nome sobe ao provedor primeiro, o token é renovado para carregá-lo
     * e só então o bootstrap acontece; o telefone viaja como pendência até haver sessão.
     */
    private fun claimNameThenBootstrap(
        current: SessionAccessState.CompletingIdentity,
        name: String,
        phone: String,
    ) {
        auth.updateDisplayName(name, authCallback { result ->
            when (result) {
                AuthResult.Cancelled -> mutableState.value = current.copy(isLoading = false, name = name)
                is AuthResult.Failure -> mutableState.value =
                    current.copy(isLoading = false, name = name, error = result.code.toUiError())
                is AuthResult.Success -> refreshTokenThenBootstrap(result.user, current, name, phone)
            }
        })
    }

    private fun refreshTokenThenBootstrap(
        user: NativeUser,
        current: SessionAccessState.CompletingIdentity,
        name: String,
        phone: String,
    ) {
        auth.idToken(true, object : TokenCallback {
            override fun complete(result: TokenResult) {
                when (result) {
                    is TokenResult.Failure -> mutableState.value =
                        current.copy(isLoading = false, name = name, error = result.code.toUiError())
                    is TokenResult.Success -> {
                        pendingIdentity = PendingIdentity(name, phone, current.photo)
                        bootstrap(user)
                    }
                }
            }
        })
    }

    private suspend fun submitProfile(
        base: SessionAccessState.CompletingIdentity,
        name: String,
        phone: String,
    ) {
        val current = uploadPhoto(base)
        mutableState.value = when (val result = session.completeProfile(phone, name)) {
            is SaqzResult.Success -> if (current.photoFailed) {
                // Gravou nome e telefone, e só a foto ficou pelo caminho: a 1c continua de
                // pé para dizer isso, agora **com** sessão. O próximo toque não tem mais
                // foto para subir e entra direto — o aviso custa um toque, nunca o cadastro.
                current.copy(session = result.value, name = name, phone = phone, isLoading = false)
            } else {
                readyOrIdentityGate(result.value)
            }
            is SaqzResult.Failure -> when (val error = result.error) {
                is AccessError.Validation -> current.refused(error.details)
                is AccessError.DataFailure -> current.copy(isLoading = false, error = error.error.toUiError())
                AccessError.EmailNotVerified,
                AccessError.Unauthenticated,
                AccessError.Forbidden,
                -> current.copy(isLoading = false, error = AuthUiError.UNKNOWN)
            }
        }
    }

    /**
     * A foto sobe **antes** do perfil e devolve o estado com que o resto do envio segue.
     *
     * Antes porque é o único momento em que a 1c ainda está na tela para avisar: depois do
     * `completeProfile` bem-sucedido a pessoa já entrou, e um aviso emitido ali não teria
     * onde aparecer. Falhar aqui não interrompe nada — o envio do nome e do telefone
     * acontece igual, com a foto descartada.
     */
    private suspend fun uploadPhoto(
        base: SessionAccessState.CompletingIdentity,
    ): SessionAccessState.CompletingIdentity {
        val photo = base.photo ?: return base
        if (session.uploadPhoto(photo.bytes, photo.mediaType) is SaqzResult.Success) return base
        return base.copy(photo = null, photoFailed = true).also { mutableState.value = it }
    }

    private fun retryBootstrap() {
        val user = currentUser ?: return
        if (mutableState.value !is SessionAccessState.BootstrapError) return
        bootstrap(user)
    }

    private fun logout() {
        if (loggingOut) return
        loggingOut = true
        localState.writeSelectedGroupId(null, resultCallback {
            localState.writePendingInvite(null, resultCallback {
                auth.signOut(resultCallback {
                    currentUser = null
                    pendingIdentity = null
                    loggingOut = false
                    mutableState.value = SessionAccessState.SignedOut
                })
            })
        })
    }

    override fun invalidate() = onIntent(SessionIntent.Logout)

    /**
     * Autenticou. A trava de e-mail que ficava aqui saiu do backend no VUL-76 e sai do app
     * com ela: quem não confirmou entra e vê a faixa, em vez de parar numa tela. O que
     * continua sendo portão é o nome, porque sem ele o bootstrap não passa.
     */
    private fun routeIdentity(user: NativeUser) {
        currentUser = user
        if (normalizedDisplayName(user.displayName.orEmpty()) == null) {
            mutableState.value = SessionAccessState.CompletingIdentity(
                session = null,
                name = user.displayName.orEmpty(),
            )
        } else {
            bootstrap(user)
        }
    }

    private fun bootstrap(user: NativeUser) {
        currentUser = user
        mutableState.value = SessionAccessState.Bootstrapping
        scope.launch {
            when (val result = session.bootstrap()) {
                is SaqzResult.Failure -> mutableState.value = SessionAccessState.BootstrapError
                is SaqzResult.Success -> resumePendingOrGate(result.value)
            }
        }
    }

    /**
     * A pendência é consumida ao ser aplicada, e não antes: um bootstrap que falha a
     * preserva para o `RetryBootstrap`, e um `completeProfile` que falha devolve a 1c já
     * **com** sessão — dali em diante o caminho normal, pós-bootstrap, dá conta.
     */
    private suspend fun resumePendingOrGate(session: AccessSession) {
        val pending = pendingIdentity
        if (pending == null) {
            mutableState.value = readyOrIdentityGate(session)
            return
        }
        pendingIdentity = null
        val base = SessionAccessState.CompletingIdentity(
            session = session,
            name = pending.name,
            phone = pending.phone,
            photo = pending.photo,
            isLoading = true,
        )
        mutableState.value = base
        submitProfile(base, pending.name, pending.phone)
    }

    /**
     * O portão pós-bootstrap é só o telefone. O nome não é checado aqui porque não pode
     * faltar: a sessão só existe se o backend aceitou o nome, e quem não tinha um passou
     * pelo portão pré-bootstrap do [routeIdentity].
     */
    private fun readyOrIdentityGate(session: AccessSession): SessionAccessState =
        if (session.user.phoneRequired) {
            SessionAccessState.CompletingIdentity(
                session = session,
                name = session.user.displayName,
                phone = session.user.phone.orEmpty(),
            )
        } else {
            SessionAccessState.Ready(session)
        }

    private fun authCallback(block: (AuthResult) -> Unit) = object : AuthCallback {
        override fun complete(result: AuthResult) = block(result)
    }

    private fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
        override fun complete(result: OperationResult) = block(result)
    }
}

private data class PendingIdentity(
    val name: String,
    val phone: String,
    val photo: ProfilePhotoResult.Selected?,
)

/**
 * O backend recusa por campo (`SafeExceptionHandler` devolve `fieldErrors`), e a 1c tem os
 * dois campos na tela ao mesmo tempo — marcar sempre o telefone poria o erro de nome na
 * linha errada. Recusa que não nomeia campo conhecido vira erro genérico, senão a tela
 * ficaria sem nada para mostrar.
 */
private fun SessionAccessState.CompletingIdentity.refused(
    details: ValidationDetails,
): SessionAccessState.CompletingIdentity {
    val nameRefused = FIELD_DISPLAY_NAME in details.fieldMessages
    val phoneRefused = FIELD_PHONE in details.fieldMessages
    return if (nameRefused || phoneRefused) {
        copy(isLoading = false, invalidName = nameRefused, invalidPhone = phoneRefused)
    } else {
        copy(isLoading = false, error = AuthUiError.UNKNOWN)
    }
}

private const val FIELD_DISPLAY_NAME = "displayName"
private const val FIELD_PHONE = "phone"

private fun DataError.toUiError(): AuthUiError = when (this) {
    DataError.Connectivity, DataError.Timeout -> AuthUiError.NETWORK_UNAVAILABLE
    else -> AuthUiError.UNKNOWN
}
