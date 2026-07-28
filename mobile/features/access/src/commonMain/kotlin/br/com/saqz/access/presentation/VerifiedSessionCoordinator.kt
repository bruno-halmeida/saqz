package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.domain.session.SessionInvalidator
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionAccessState {
    data object SignedOut : SessionAccessState

    /**
     * A tela 1c: nome, telefone e foto no mesmo estado, porque o export os junta numa tela
     * só. Substitui o par `CompletingName`/`CompletingPhone` — o primeiro corria antes do
     * bootstrap contra o provedor nativo, o segundo depois contra o backend; agora há um
     * portão só, depois do bootstrap, e um `completeProfile` só, que já aceita os dois.
     */
    data class CompletingIdentity(
        val session: AccessSession,
        val name: String = "",
        val phone: String = "",
        val photo: ProfilePhotoResult.Selected? = null,
        val isLoading: Boolean = false,
        val error: AuthUiError? = null,
        val invalidName: Boolean = false,
        val invalidPhone: Boolean = false,
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

    fun onIntent(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.Accept -> when (val transition = intent.transition) {
                is AuthTransition.Authenticated -> routeIdentity(transition.user)
            }
            is SessionIntent.UpdateName -> updateIdentity { copy(name = intent.value, invalidName = false) }
            is SessionIntent.UpdatePhone -> updateIdentity { copy(phone = intent.value, invalidPhone = false) }
            is SessionIntent.UpdatePhoto -> updateIdentity { copy(photo = intent.value) }
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
     * A foto escolhida fica no estado e não sobe aqui: o envio é HTTP multipart e ainda não
     * há gateway para ele. Quem entrega a 1c completa liga o upload nesta transição.
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
        mutableState.value = current.copy(isLoading = true, error = null, invalidName = false, invalidPhone = false)
        scope.launch {
            mutableState.value = when (val result = session.completeProfile(phone, name)) {
                is SaqzResult.Success -> readyOrIdentityGate(result.value)
                is SaqzResult.Failure -> when (val error = result.error) {
                    is AccessError.Validation -> current.copy(isLoading = false, invalidPhone = true)
                    is AccessError.DataFailure -> current.copy(isLoading = false, error = error.error.toUiError())
                    AccessError.EmailNotVerified,
                    AccessError.Unauthenticated,
                    AccessError.Forbidden,
                    -> current.copy(isLoading = false, error = AuthUiError.UNKNOWN)
                }
            }
        }
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
                    loggingOut = false
                    mutableState.value = SessionAccessState.SignedOut
                })
            })
        })
    }

    override fun invalidate() = onIntent(SessionIntent.Logout)

    /**
     * Autenticou, carrega a sessão. A trava de e-mail que ficava aqui saiu do backend no
     * VUL-76 e sai do app com ela: quem não confirmou entra e vê a faixa, em vez de parar
     * numa tela. Sem essa trava também não há claim nova para buscar, então o
     * `idToken(forceRefresh = true)` que precedia o bootstrap deixou de ter motivo.
     */
    private fun routeIdentity(user: NativeUser) = bootstrap(user)

    private fun bootstrap(user: NativeUser) {
        currentUser = user
        mutableState.value = SessionAccessState.Bootstrapping
        scope.launch {
            mutableState.value = when (val result = session.bootstrap()) {
                is SaqzResult.Success -> readyOrIdentityGate(result.value)
                is SaqzResult.Failure -> SessionAccessState.BootstrapError
            }
        }
    }

    /**
     * O portão único da 1c, agora sobre a sessão do backend e não sobre o usuário do
     * provedor: falta telefone ou falta nome utilizável, a pessoa completa o perfil; senão
     * está pronta. Os campos já entram preenchidos com o que o backend sabe — a 1c pede
     * para *confirmar* o nome, não para digitá-lo do zero.
     */
    private fun readyOrIdentityGate(session: AccessSession): SessionAccessState =
        if (session.user.phoneRequired || normalizedDisplayName(session.user.displayName) == null) {
            SessionAccessState.CompletingIdentity(
                session = session,
                name = session.user.displayName,
                phone = session.user.phone.orEmpty(),
            )
        } else {
            SessionAccessState.Ready(session)
        }

    private fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
        override fun complete(result: OperationResult) = block(result)
    }
}

private fun DataError.toUiError(): AuthUiError = when (this) {
    DataError.Connectivity, DataError.Timeout -> AuthUiError.NETWORK_UNAVAILABLE
    else -> AuthUiError.UNKNOWN
}
