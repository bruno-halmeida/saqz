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
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

/**
 * Tudo que a máquina muda, num valor só.
 *
 * Estava em cinco campos soltos — estado, geração, pendência, usuário e a saída em curso —
 * e cada guarda era um `if` lido de um campo seguido de uma escrita em outro. Duas etapas
 * com uma janela no meio: o `scope` da máquina roda em `Dispatchers.Default` e os intentos
 * chegam da interface, então entre conferir a geração e escrever o estado cabia um logout
 * inteiro, e a resposta da conta anterior aterrissava sobre o contexto novo.
 *
 * Num valor só, a verificação e a escrita viram **uma** operação: o `compareAndSet` do
 * `MutableStateFlow` aplica a mudança apenas se nada tiver mudado desde a leitura, e
 * recomeça se tiver. Não há mais janela para fechar porque não há mais duas etapas.
 */
private data class SessionContext(
    val state: SessionAccessState = SessionAccessState.SignedOut,
    /**
     * A geração do contexto de sessão, para a guarda que o `mobile/AGENTS.md` lista nas
     * disciplinas obrigatórias: **resposta que volta com o contexto trocado é descartada**.
     * Sobe em toda troca — sair da conta e autenticar outra identidade —, porque nada
     * cancela ao sair: o escopo é o singleton do app e o voltar da 1c segue clicável.
     */
    val generation: Int = 0,
    /**
     * O telefone digitado antes do bootstrap, esperando a sessão existir para poder subir.
     * Fica fora de [state] porque o bootstrap passa por `Bootstrapping`, que não tem onde
     * guardá-lo — e porque um `RetryBootstrap` depois de uma queda precisa reencontrá-lo
     * em vez de perder o que a pessoa já digitou.
     */
    val pendingIdentity: PendingIdentity? = null,
    val currentUser: NativeUser? = null,
    val loggingOut: Boolean = false,
)

class SessionAccessStateMachine(
    private val auth: NativeAuthPort,
    private val localState: LocalAccessStatePort,
    private val session: SessionGateway,
    private val scope: CoroutineScope,
) : SessionInvalidator {
    private val context = MutableStateFlow(SessionContext())
    val state: StateFlow<SessionAccessState> = SessionStateView(context)

    /**
     * Verificação e escrita numa operação só: [edit] recebe um instantâneo do contexto e o
     * resultado dele só entra se nada tiver mudado desde a leitura; se tiver, a tentativa
     * recomeça sobre o valor novo. Devolver nulo é desistir — a condição que [edit] exigia
     * não vale mais.
     *
     * [edit] roda mais de uma vez sob disputa, então **não pode ter efeito colateral**:
     * decida no lambda, aja com o contexto que [mutate] devolve.
     */
    private fun mutate(edit: (SessionContext) -> SessionContext?): SessionContext? {
        while (true) {
            val current = context.value
            val next = edit(current) ?: return null
            if (next == current || context.compareAndSet(current, next)) return next
        }
    }

    /** Escreve o estado só se o contexto ainda for o de [token] — na mesma operação. */
    private fun publish(token: Int, state: SessionAccessState): SessionContext? =
        mutate { if (it.generation == token) it.copy(state = state) else null }

    /**
     * A troca de contexto inteira, aplicada de uma vez: a geração sobe **e** o que ficou
     * guardado para o contexto anterior cai junto.
     *
     * Os dois andam juntos porque a guarda sozinha protege metade do problema. Ela descarta
     * a *resposta* em voo, mas o que já foi guardado à espera do próximo passo seria
     * consumido pelo contexto novo, que é corrente: a conta A deixava [SessionContext.pendingIdentity]
     * esperando o bootstrap, e o bootstrap da conta B subia o nome, o telefone e a foto de
     * A com a sessão de B — e a 1c promete que o telefone só fica visível para os grupos de
     * quem o digitou.
     *
     * [SessionContext.currentUser] cai pelo mesmo motivo: é ele que o `RetryBootstrap` usa
     * para decidir se há conta a rebootar, e conta que saiu não tem.
     */
    private fun SessionContext.switched() = copy(
        generation = generation + 1,
        pendingIdentity = null,
        currentUser = null,
    )

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

    /**
     * Toda condição de entrada — inclusive "a saída já foi decidida" — mora dentro do
     * [mutate], e não num `if` antes dele. Trabalho disparado **entre** o intento de sair e
     * o fim da saída nasceria já com a geração nova e passaria pela guarda; recusá-lo é
     * parte da mesma operação atômica que o aplica.
     */
    private fun updateIdentity(edit: SessionAccessState.CompletingIdentity.() -> SessionAccessState.CompletingIdentity) {
        mutate { context ->
            val current = context.state as? SessionAccessState.CompletingIdentity ?: return@mutate null
            if (context.loggingOut || current.isLoading) null
            else context.copy(state = current.edit().copy(error = null))
        }
    }

    /**
     * Um botão, dois caminhos, decididos por [SessionAccessState.CompletingIdentity.session].
     *
     * A foto sobe junto, mas nunca manda no resultado: quem decide se a pessoa entra são o
     * nome e o telefone (VUL-87).
     */
    private fun completeIdentity() {
        // A transição para "enviando" é o que decide quem envia: só um `compareAndSet`
        // vence, então o envio tem um dono só. É também o que sustenta o toque duplo — o
        // segundo encontra `isLoading` e desiste dentro da mesma operação.
        val started = mutate { context ->
            val current = context.state as? SessionAccessState.CompletingIdentity ?: return@mutate null
            if (context.loggingOut || current.isLoading) return@mutate null
            val name = normalizedDisplayName(current.name)
            val phone = normalizedBrMobilePhone(current.phone)
            val next = if (name == null || phone == null) {
                current.copy(invalidName = name == null, invalidPhone = phone == null)
            } else {
                // `photoFailed` zera aqui junto com os outros sinais: tocar de novo é
                // recomeçar o envio, e o aviso da tentativa anterior não pode sobreviver a
                // ela — se sobrevivesse, um segundo toque sem foto para subir manteria a
                // pessoa presa na 1c com o recado de um envio que nem aconteceu.
                current.copy(
                    isLoading = true,
                    error = null,
                    invalidName = false,
                    invalidPhone = false,
                    photoFailed = false,
                )
            }
            context.copy(state = next)
        } ?: return
        val submitting = started.state as? SessionAccessState.CompletingIdentity ?: return
        submitting.send(started.generation)
    }

    /**
     * Os dois caminhos do mesmo botão, decididos por
     * [SessionAccessState.CompletingIdentity.session]: sem sessão o nome sobe ao provedor
     * antes do bootstrap; com sessão o perfil vai direto.
     */
    private fun SessionAccessState.CompletingIdentity.send(token: Int) {
        // Recusa local: os campos foram marcados e não há o que enviar.
        if (!isLoading) return
        val validName = normalizedDisplayName(name) ?: return
        val validPhone = normalizedBrMobilePhone(phone) ?: return
        if (session == null) claimNameThenBootstrap(this, validName, validPhone, token)
        else scope.launch { submitProfile(this@send, validName, validPhone, token) }
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
        token: Int,
    ) {
        auth.updateDisplayName(name, authCallback { result ->
            when (result) {
                AuthResult.Cancelled -> publish(token, current.copy(isLoading = false, name = name))
                is AuthResult.Failure -> publish(
                    token,
                    current.copy(isLoading = false, name = name, error = result.code.toUiError()),
                )
                is AuthResult.Success -> refreshTokenThenBootstrap(result.user, current, name, phone, token)
            }
        })
    }

    private fun refreshTokenThenBootstrap(
        user: NativeUser,
        current: SessionAccessState.CompletingIdentity,
        name: String,
        phone: String,
        token: Int,
    ) {
        auth.idToken(true, object : TokenCallback {
            override fun complete(result: TokenResult) {
                when (result) {
                    is TokenResult.Failure -> publish(
                        token,
                        current.copy(isLoading = false, name = name, error = result.code.toUiError()),
                    )
                    // Guardar a pendência e entrar em `Bootstrapping` é uma operação só: se
                    // fossem duas, um logout no meio deixaria a pendência para a conta
                    // seguinte consumir.
                    is TokenResult.Success -> mutate { context ->
                        if (context.generation != token) return@mutate null
                        context.copy(
                            pendingIdentity = PendingIdentity(name, phone, current.photo),
                            currentUser = user,
                            state = SessionAccessState.Bootstrapping,
                        )
                    }?.let { bootstrap(token) }
                }
            }
        })
    }

    private suspend fun submitProfile(
        base: SessionAccessState.CompletingIdentity,
        name: String,
        phone: String,
        token: Int,
    ) {
        val current = uploadPhoto(base, token) ?: return
        val result = session.completeProfile(phone, name)
        publish(token, when (result) {
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
        })
    }

    /**
     * A foto sobe **antes** do perfil e devolve o estado com que o resto do envio segue.
     *
     * Antes porque é o único momento em que a 1c ainda está na tela para avisar: depois do
     * `completeProfile` bem-sucedido a pessoa já entrou, e um aviso emitido ali não teria
     * onde aparecer. Falhar aqui não interrompe nada — o envio do nome e do telefone
     * acontece igual, com a foto descartada.
     *
     * Nulo é a saída da guarda de geração: o contexto trocou enquanto a imagem subia, e
     * daí em diante não há mais o que escrever nem perfil que gravar.
     */
    private suspend fun uploadPhoto(
        base: SessionAccessState.CompletingIdentity,
        token: Int,
    ): SessionAccessState.CompletingIdentity? {
        val photo = base.photo ?: return base
        val failed = session.uploadPhoto(photo.bytes, photo.mediaType) is SaqzResult.Failure
        if (!failed) return if (context.value.generation == token) base else null
        val warned = base.copy(photo = null, photoFailed = true)
        return if (publish(token, warned) != null) warned else null
    }

    private fun retryBootstrap() {
        val started = mutate { context ->
            if (context.loggingOut || context.currentUser == null) return@mutate null
            if (context.state !is SessionAccessState.BootstrapError) return@mutate null
            context.copy(state = SessionAccessState.Bootstrapping)
        } ?: return
        bootstrap(started.generation)
    }

    private fun logout() {
        // Decidir sair e trocar o contexto é uma operação só: dois logouts em disputa não
        // podem passar os dois, e a troca não pode acontecer sem a trava que recusa
        // trabalho novo — a janela entre uma e outra é por onde o "Concluir cadastro" e o
        // "Tentar novamente" escapavam.
        //
        // O contexto troca **aqui**, no intento, e não quando a saída termina: a decisão de
        // sair é deste instante, e o que estava em voo ou guardado tem de cair desde já.
        mutate { if (it.loggingOut) null else it.switched().copy(loggingOut = true) } ?: return
        localState.writeSelectedGroupId(null, resultCallback {
            localState.writePendingInvite(null, resultCallback {
                auth.signOut(resultCallback {
                    mutate { it.copy(loggingOut = false, state = SessionAccessState.SignedOut) }
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
        // Autenticar é a outra troca de contexto: nem o que estava em voo pela conta
        // anterior aterrissa sobre a nova, nem o que ela deixou guardado viaja junto. A
        // troca e o destino entram na mesma operação, senão a conta nova existiria por um
        // instante sem estado próprio.
        val nameless = normalizedDisplayName(user.displayName.orEmpty()) == null
        val switched = mutate { context ->
            if (context.loggingOut) return@mutate null
            context.switched().copy(
                currentUser = user,
                state = if (nameless) {
                    SessionAccessState.CompletingIdentity(session = null, name = user.displayName.orEmpty())
                } else {
                    SessionAccessState.Bootstrapping
                },
            )
        } ?: return
        if (!nameless) bootstrap(switched.generation)
    }

    /** Só a parte assíncrona: quem já pôs o estado em `Bootstrapping` chama daqui. */
    private fun bootstrap(token: Int) {
        scope.launch {
            when (val result = session.bootstrap()) {
                is SaqzResult.Failure -> publish(token, SessionAccessState.BootstrapError)
                is SaqzResult.Success -> resumePendingOrGate(result.value, token)
            }
        }
    }

    /**
     * A pendência é consumida ao ser aplicada, e não antes: um bootstrap que falha a
     * preserva para o `RetryBootstrap`, e um `completeProfile` que falha devolve a 1c já
     * **com** sessão — dali em diante o caminho normal, pós-bootstrap, dá conta.
     */
    private suspend fun resumePendingOrGate(session: AccessSession, token: Int) {
        // Consumir a pendência e entrar em "enviando" é a mesma operação que a guarda:
        // ler a pendência, conferir a geração e escrever o estado em três etapas era o que
        // deixava a identidade de uma conta ser aplicada pelo bootstrap da seguinte.
        val resumed = mutate { context ->
            if (context.generation != token) return@mutate null
            val pending = context.pendingIdentity
                ?: return@mutate context.copy(state = readyOrIdentityGate(session))
            context.copy(
                pendingIdentity = null,
                state = SessionAccessState.CompletingIdentity(
                    session = session,
                    name = pending.name,
                    phone = pending.phone,
                    photo = pending.photo,
                    isLoading = true,
                ),
            )
        } ?: return
        val base = resumed.state as? SessionAccessState.CompletingIdentity ?: return
        if (!base.isLoading) return
        submitProfile(base, base.name, base.phone, token)
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
 * O [SessionAccessState] visto de fora, sem expor a contabilidade que anda com ele.
 *
 * Uma vista, e não um segundo `MutableStateFlow` espelhado: dois flows voltariam a ser dois
 * valores para manter em acordo, que é exatamente o problema que juntá-los resolveu — e um
 * espelho escrito depois do `compareAndSet` pode inverter a ordem entre duas escritas que
 * venceram em sequência, deixando a tela num estado mais velho que o contexto.
 *
 * `value` continua síncrono: quem lê logo depois de um intento vê o resultado dele, como
 * antes.
 *
 * O opt-in é o preço de herdar de `StateFlow` — a instabilidade anunciada é a de **métodos
 * novos** aparecerem na interface, e esta vista não tem comportamento próprio para divergir:
 * os três membros só encaminham para o flow de baixo.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class SessionStateView(
    private val source: StateFlow<SessionContext>,
) : StateFlow<SessionAccessState> {
    override val value: SessionAccessState get() = source.value.state

    override val replayCache: List<SessionAccessState> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<SessionAccessState>): Nothing {
        // `distinctUntilChanged` porque mudança só de contabilidade — a geração subindo, a
        // pendência caindo — não é mudança de tela.
        source.map { it.state }.distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}

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
