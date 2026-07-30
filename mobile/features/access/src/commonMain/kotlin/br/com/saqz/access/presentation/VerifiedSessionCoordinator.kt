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
        /**
         * A foto já subiu nesta identidade. Existe porque o envio da foto e o do perfil são
         * dois pedidos: quando a foto sobe e o perfil é recusado, a 1c volta com a imagem na
         * tela — e sem esta marca o toque seguinte reenviaria o arquivo inteiro, até 5 MiB,
         * por causa de um erro que não foi dele. Cai quando a pessoa escolhe outra foto,
         * que é quando há de novo algo a enviar.
         */
        val photoUploaded: Boolean = false,
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

    /**
     * Volta do plano de fundo (VUL-91): é quando a pessoa acabou de tocar no link do
     * e-mail. O provedor só atualiza `emailVerified` ao recarregar o usuário, então é aqui
     * que a faixa do shell descobre que já pode sumir.
     */
    data object RefreshEmailVerification : SessionIntent

    /**
     * A 1b deposita o telefone (e o nome) **antes** do `createAccount`: o observe global
     * autentica sozinho e o [Accept] seguinte precisa achar a pendência já no contexto,
     * senão a 1c abre com o campo vazio que o backend devolveu.
     *
     * Não é o mesmo uso da pendência do caminho pré-bootstrap da 1c — aquela sobe o perfil
     * sozinha quando a sessão existe; esta só **preenche** a 1c. Ver [PendingIdentity.submitWhenReady].
     */
    data class StageRegistrationIdentity(val name: String, val phone: String) : SessionIntent

    /** A 1b desistiu (recusa, cancelamento): a pendência de cadastro não pode ficar para a próxima conta. */
    data object ClearRegistrationIdentity : SessionIntent

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
     * decida no lambda, aja com o contexto que [begin] devolve.
     *
     * **Este é o único escritor sem token, e por isso o seu uso é fechado**: os cinco
     * intentos que *definem* contexto (digitar na 1c, começar a conclusão, repetir o
     * bootstrap, sair, autenticar) e a cauda da saída. Nenhum deles é retomada de coisa
     * assíncrona — são o instante em que a pessoa mandou. Tudo que volta depois de suspender
     * escreve por [Turn], que revalida a geração no mesmo ponto atômico.
     */
    private fun begin(edit: (SessionContext) -> SessionContext?): SessionContext? {
        while (true) {
            val current = context.value
            val next = edit(current) ?: return null
            if (next == current || context.compareAndSet(current, next)) return next
        }
    }

    /**
     * O passe da cadeia assíncrona, e a resposta à pergunta "quem pode escrever depois de
     * suspender".
     *
     * A conclusão de perfil é uma cadeia de vários passos — nome ao provedor, token
     * renovado, bootstrap, foto, perfil, publicação — e cada passo era responsável por
     * conferir a geração por conta própria. O que passasse despercebido virava o defeito da
     * rodada seguinte: primeiro faltou a guarda, depois faltou zerar a pendência, depois a
     * guarda tinha corrida, depois faltou guarda no retorno sem foto.
     *
     * O [Turn] fecha isso por construção: ele nasce no intento que começou a cadeia,
     * atravessa **todos** os passos, e é por onde passam as duas coisas que uma cadeia morta
     * não pode fazer — **escrever estado** e **emitir efeito**.
     *
     * As duas, e não só a primeira: pedido de rede disparado por uma cadeia morta já saiu, e
     * descartar a resposta não o traz de volta. Por isso [emit] pergunta antes de emitir, e
     * não depois de voltar.
     */
    private inner class Turn(private val token: Int) {
        /** Escreve o estado se a cadeia ainda for a corrente; nulo é contexto perdido. */
        fun publish(state: SessionAccessState): SessionContext? =
            begin { if (it.generation == token) it.copy(state = state) else null }

        /** Muda estado e contabilidade juntos, sob a mesma revalidação. */
        fun advance(edit: (SessionContext) -> SessionContext?): SessionContext? =
            begin { if (it.generation == token) edit(it) else null }

        /**
         * Emite um efeito externo — pedido HTTP, chamada ao provedor — só se a cadeia ainda
         * for a corrente; nulo é cadeia morta, e quem chama para por ali.
         *
         * **Antes** de emitir, e é essa a diferença: o `PUT api/session` de uma cadeia morta
         * sai com o token que o provedor tiver agora, e nenhum tratamento da resposta desfaz
         * o pedido. O que continua fora do alcance é o intervalo entre esta pergunta e o
         * pedido sair — para fechá-lo, o pedido teria de carregar a identidade esperada, o
         * que é mudança no cliente de rede.
         */
        inline fun <T> emit(effect: () -> T): T? = if (current()) effect() else null

        fun current(): Boolean = context.value.generation == token
    }

    /** O passe de uma cadeia que acabou de nascer neste contexto. */
    private fun SessionContext.turn() = Turn(generation)

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
            is SessionIntent.UpdatePhoto -> updateIdentity {
                copy(photo = intent.value, photoFailed = false, photoUploaded = false)
            }
            SessionIntent.CompleteIdentity -> completeIdentity()
            SessionIntent.RetryBootstrap -> retryBootstrap()
            SessionIntent.RefreshEmailVerification -> refreshEmailVerification()
            is SessionIntent.StageRegistrationIdentity -> stageRegistrationIdentity(intent.name, intent.phone)
            SessionIntent.ClearRegistrationIdentity -> clearRegistrationIdentity()
            SessionIntent.Logout -> logout()
        }
    }

    /**
     * Guarda o que a 1b já validou. Só no [SessionAccessState.SignedOut]: fora disso a
     * pessoa não está criando conta, e uma pendência nova aqui seria de outra jornada.
     */
    private fun stageRegistrationIdentity(name: String, phone: String) {
        val validName = normalizedDisplayName(name) ?: return
        val validPhone = normalizedBrMobilePhone(phone) ?: return
        begin { context ->
            if (context.loggingOut || context.state !is SessionAccessState.SignedOut) return@begin null
            context.copy(
                pendingIdentity = PendingIdentity(
                    name = validName,
                    phone = validPhone,
                    photo = null,
                    submitWhenReady = false,
                ),
            )
        }
    }

    /**
     * Só a pendência de **preenchimento** da 1b — a do envio pós-bootstrap não é desta tela.
     *
     * Só em [SessionAccessState.SignedOut]: se a conta já autenticou (bootstrap/1c), o
     * depósito passou a ser da sessão. Limpar aí — p.ex. no `onCleared` da 1b depois do
     * observe — droparia o telefone no meio do caminho.
     */
    private fun clearRegistrationIdentity() {
        begin { context ->
            if (context.state !is SessionAccessState.SignedOut) return@begin null
            val pending = context.pendingIdentity ?: return@begin null
            if (pending.submitWhenReady) return@begin null
            context.copy(pendingIdentity = null)
        }
    }

    /**
     * Toda condição de entrada — inclusive "a saída já foi decidida" — mora dentro do
     * [begin], e não num `if` antes dele. Trabalho disparado **entre** o intento de sair e
     * o fim da saída nasceria já com a geração nova e passaria pela guarda; recusá-lo é
     * parte da mesma operação atômica que o aplica.
     */
    private fun updateIdentity(edit: SessionAccessState.CompletingIdentity.() -> SessionAccessState.CompletingIdentity) {
        begin { context ->
            val current = context.state as? SessionAccessState.CompletingIdentity ?: return@begin null
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
        val started = begin { context ->
            val current = context.state as? SessionAccessState.CompletingIdentity ?: return@begin null
            if (context.loggingOut || current.isLoading) return@begin null
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
        submitting.send(started.turn())
    }

    /**
     * Os dois caminhos do mesmo botão, decididos por
     * [SessionAccessState.CompletingIdentity.session]: sem sessão o nome sobe ao provedor
     * antes do bootstrap; com sessão o perfil vai direto.
     */
    private fun SessionAccessState.CompletingIdentity.send(turn: Turn) {
        // Recusa local: os campos foram marcados e não há o que enviar.
        if (!isLoading) return
        val validName = normalizedDisplayName(name) ?: return
        val validPhone = normalizedBrMobilePhone(phone) ?: return
        if (session == null) claimNameThenBootstrap(this, validName, validPhone, turn)
        else scope.launch { submitProfile(this@send, validName, validPhone, turn) }
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
        turn: Turn,
    ) {
        auth.updateDisplayName(name, authCallback { result ->
            when (result) {
                AuthResult.Cancelled -> turn.publish(current.copy(isLoading = false, name = name))
                is AuthResult.Failure -> turn.publish(
                    current.copy(isLoading = false, name = name, error = result.code.toUiError()),
                )
                is AuthResult.Success -> refreshTokenThenBootstrap(result.user, current, name, phone, turn)
            }
        })
    }

    private fun refreshTokenThenBootstrap(
        user: NativeUser,
        current: SessionAccessState.CompletingIdentity,
        name: String,
        phone: String,
        turn: Turn,
    ) {
        auth.idToken(true, object : TokenCallback {
            override fun complete(result: TokenResult) {
                when (result) {
                    is TokenResult.Failure -> turn.publish(
                        current.copy(isLoading = false, name = name, error = result.code.toUiError()),
                    )
                    // Guardar a pendência e entrar em `Bootstrapping` é uma operação só: se
                    // fossem duas, um logout no meio deixaria a pendência para a conta
                    // seguinte consumir.
                    is TokenResult.Success -> turn.advance { context ->
                        context.copy(
                            pendingIdentity = PendingIdentity(name, phone, current.photo),
                            currentUser = user,
                            state = SessionAccessState.Bootstrapping,
                        )
                    }?.let { bootstrap(turn) }
                }
            }
        })
    }

    private suspend fun submitProfile(
        base: SessionAccessState.CompletingIdentity,
        name: String,
        phone: String,
        turn: Turn,
    ) {
        val current = when (val photo = uploadPhoto(base, turn)) {
            is PhotoOutcome.Sent -> photo.identity
            is PhotoOutcome.Refused -> photo.identity
            // A cadeia acabou: a conta mudou enquanto a imagem subia.
            PhotoOutcome.ContextLost -> return
        }
        // O `completeProfile` sai com a sessão que o provedor tiver **agora**: revalidar
        // antes de disparar é o que impede a cadeia de chegar ao pedido depois da troca.
        if (!turn.current()) return
        val result = session.completeProfile(phone, name)
        turn.publish(when (result) {
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
     * A foto sobe **antes** do perfil, e o resultado é um dos três — nunca um estado
     * devolvido em silêncio.
     *
     * Antes porque é o único momento em que a 1c ainda está na tela para avisar: depois do
     * `completeProfile` bem-sucedido a pessoa já entrou, e um aviso emitido ali não teria
     * onde aparecer. Falhar aqui não interrompe nada — o envio do nome e do telefone
     * acontece igual, com a foto descartada.
     *
     * O tipo de retorno é o que obriga quem chama a decidir: **todo** caminho de saída,
     * inclusive "não havia foto para enviar", passa pela revalidação da cadeia, e o
     * compilador não deixa esquecer o caso em que ela acabou.
     */
    private suspend fun uploadPhoto(
        base: SessionAccessState.CompletingIdentity,
        turn: Turn,
    ): PhotoOutcome {
        val photo = base.photo
        // Sem foto, ou com a foto já enviada numa tentativa anterior: nada a fazer aqui,
        // mas a cadeia continua sendo revalidada como em qualquer outro passo.
        if (photo == null || base.photoUploaded) {
            return if (turn.current()) PhotoOutcome.Sent(base) else PhotoOutcome.ContextLost
        }
        // Revalidar **antes** de disparar, e não só ao voltar: o envio sai com a sessão que
        // o provedor tiver agora, então a imagem de uma pessoa iria com a conta de outra.
        // A mesma regra do `completeProfile` — todo efeito externo da cadeia é precedido
        // por esta pergunta.
        if (!turn.current()) return PhotoOutcome.ContextLost
        val sent = session.uploadPhoto(photo.bytes, photo.mediaType) is SaqzResult.Success
        val next = if (sent) {
            base.copy(photoUploaded = true)
        } else {
            base.copy(photo = null, photoFailed = true)
        }
        // A marca entra pelo mesmo ponto atômico de qualquer outra escrita: se a cadeia
        // acabou, ela não entra e o envio do perfil também não acontece.
        turn.publish(next) ?: return PhotoOutcome.ContextLost
        return if (sent) PhotoOutcome.Sent(next) else PhotoOutcome.Refused(next)
    }

    private fun retryBootstrap() {
        val started = begin { context ->
            if (context.loggingOut || context.currentUser == null) return@begin null
            if (context.state !is SessionAccessState.BootstrapError) return@begin null
            context.copy(state = SessionAccessState.Bootstrapping)
        } ?: return
        bootstrap(started.turn())
    }

    /**
     * Recarrega o usuário no provedor e espelha o sinal na sessão que o [Ready] carrega —
     * é o que faz a faixa do VUL-91 sumir sozinha, sem a pessoa tocar em nada.
     *
     * Só age com faixa na tela: fora de [SessionAccessState.Ready], ou com o e-mail já
     * confirmado, não há o que recarregar e a volta do plano de fundo não custa nada.
     *
     * ponytail: não refaz o bootstrap. O backend lê `email_verified` do mesmo token e se
     * acerta no próximo `PUT /api/session`; nada além da faixa depende do valor persistido,
     * e a faixa não bloqueia nada (é o ponto do ticket). Refaça o bootstrap aqui no dia em
     * que alguma decisão do app depender do campo do backend.
     */
    private fun refreshEmailVerification() {
        val snapshot = context.value
        val current = snapshot.state as? SessionAccessState.Ready ?: return
        if (current.emailVerified) return
        // A identidade de quem pediu, para o retorno saber a qual sessão pertence.
        val asked = current.session.user.id
        val token = snapshot.generation
        auth.reloadUser(authCallback { result ->
            if (result !is AuthResult.Success || !result.user.emailVerified) return@authCallback
            // O estado é relido no callback: o reload é assíncrono e, enquanto ele voltava,
            // a sessão pode ter caído (logout, invalidação) **ou trocado de dono** — sair e
            // entrar com outra conta é um percurso de segundos. Conferir só que ainda é
            // `Ready` deixaria o "confirmado" de quem pediu apagar a faixa de quem chegou
            // depois, e o e-mail que continua por confirmar é o da conta nova.
            begin { ctx ->
                if (ctx.generation != token) return@begin null
                val ready = ctx.state as? SessionAccessState.Ready ?: return@begin null
                if (ready.session.user.id != asked) return@begin null
                ctx.copy(
                    state = SessionAccessState.Ready(
                        ready.session.copy(user = ready.session.user.copy(emailVerified = true)),
                    ),
                )
            }
        })
    }

    private fun logout() {
        // Decidir sair e trocar o contexto é uma operação só: dois logouts em disputa não
        // podem passar os dois, e a troca não pode acontecer sem a trava que recusa
        // trabalho novo — a janela entre uma e outra é por onde o "Concluir cadastro" e o
        // "Tentar novamente" escapavam.
        //
        // O contexto troca **aqui**, no intento, e não quando a saída termina: a decisão de
        // sair é deste instante, e o que estava em voo ou guardado tem de cair desde já.
        begin { if (it.loggingOut) null else it.switched().copy(loggingOut = true) } ?: return
        localState.writeSelectedGroupId(null, resultCallback {
            localState.writePendingInvite(null, resultCallback {
                auth.signOut(resultCallback {
                    // A cauda da própria saída, e o único ponto assíncrono que escreve sem
                    // passe: enquanto `loggingOut` está de pé, nenhum outro caminho muda o
                    // contexto — conclusão, retry, digitação e autenticação recusam todos
                    // dentro do próprio CAS. Ela fecha a transição que já aconteceu.
                    begin { it.copy(loggingOut = false, state = SessionAccessState.SignedOut) }
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
        //
        // Exceção deliberada (VUL-101): a pendência de **preenchimento** da 1b — telefone
        // validado antes do `createAccount` — precisa atravessar **este** Accept. O observe
        // global autentica sozinho e o `switched()` zeraria o depósito; a pendência de envio
        // (`submitWhenReady`) continua caindo, que é o que impede o vazamento entre contas.
        //
        // O mesmo `subject` duas vezes não troca contexto: o observe do `AccessOrchestrator`
        // e o callback da 1b emitem os dois um `Accept` para a conta que acabou de nascer.
        // Um segundo `switched()` mataria o turno do bootstrap e droparia o telefone já
        // consumido na 1c. Conta **outra** continua trocando — a limpeza entre contas não
        // enfraquece.
        val nameless = normalizedDisplayName(user.displayName.orEmpty()) == null
        val switched = begin { context ->
            if (context.loggingOut) return@begin null
            if (context.currentUser?.subject == user.subject &&
                context.state !is SessionAccessState.SignedOut
            ) {
                return@begin null
            }
            // Prefill da 1b só no Accept que **começa** o cadastro (SignedOut). Com o
            // Accept idempotente acima, o re-Accept da 1b não chega aqui; carregar em
            // BootstrapError/Ready/1c seria levar o telefone da conta A para a B.
            val registrationPrefill = context.pendingIdentity
                ?.takeUnless { it.submitWhenReady }
                ?.takeIf { context.state is SessionAccessState.SignedOut }
            context.switched().copy(
                currentUser = user,
                pendingIdentity = registrationPrefill,
                state = if (nameless) {
                    // Sem nome utilizável o bootstrap nem corre: a 1c pré-bootstrap herda o
                    // telefone da 1b agora, e não depois de uma sessão que não vai existir.
                    SessionAccessState.CompletingIdentity(
                        session = null,
                        name = registrationPrefill?.name ?: user.displayName.orEmpty(),
                        phone = registrationPrefill?.phone.orEmpty(),
                    )
                } else {
                    SessionAccessState.Bootstrapping
                },
            ).let { next ->
                // Prefill já foi para a tela: não precisa sobreviver a um segundo Accept.
                if (nameless) next.copy(pendingIdentity = null) else next
            }
        } ?: return
        if (!nameless) bootstrap(switched.turn())
    }

    /** Só a parte assíncrona: quem já pôs o estado em `Bootstrapping` chama daqui. */
    private fun bootstrap(turn: Turn) {
        scope.launch {
            // O `PUT api/session` sai com o token que o provedor tiver agora: a cadeia morta
            // não pode chegar até ele. A pergunta é **antes** do pedido — o mesmo critério
            // de [Turn.emit] nos outros efeitos externos da conclusão.
            when (val result = turn.emit { session.bootstrap() } ?: return@launch) {
                is SaqzResult.Failure -> turn.publish(SessionAccessState.BootstrapError)
                is SaqzResult.Success -> resumePendingOrGate(result.value, turn)
            }
        }
    }

    /**
     * A pendência é consumida ao ser aplicada, e não antes: um bootstrap que falha a
     * preserva para o `RetryBootstrap`, e um `completeProfile` que falha devolve a 1c já
     * **com** sessão — dali em diante o caminho normal, pós-bootstrap, dá conta.
     *
     * Há dois tipos de pendência (VUL-101): a do envio (`submitWhenReady`) sobe o perfil
     * sozinha; a do preenchimento da 1b só prefere o telefone no portão da 1c.
     */
    private suspend fun resumePendingOrGate(session: AccessSession, turn: Turn) {
        // Consumir a pendência e entrar em "enviando" é a mesma operação que a guarda:
        // ler a pendência, conferir a geração e escrever o estado em três etapas era o que
        // deixava a identidade de uma conta ser aplicada pelo bootstrap da seguinte.
        val resumed = turn.advance { context ->
            val pending = context.pendingIdentity
            when {
                pending == null -> context.copy(state = readyOrIdentityGate(session))
                !pending.submitWhenReady -> context.copy(
                    pendingIdentity = null,
                    state = readyOrIdentityGate(session, pending),
                )
                else -> context.copy(
                    pendingIdentity = null,
                    state = SessionAccessState.CompletingIdentity(
                        session = session,
                        name = pending.name,
                        phone = pending.phone,
                        photo = pending.photo,
                        isLoading = true,
                    ),
                )
            }
        } ?: return
        val base = resumed.state as? SessionAccessState.CompletingIdentity ?: return
        if (!base.isLoading) return
        submitProfile(base, base.name, base.phone, turn)
    }

    /**
     * O portão pós-bootstrap é só o telefone. O nome não é checado aqui porque não pode
     * faltar: a sessão só existe se o backend aceitou o nome, e quem não tinha um passou
     * pelo portão pré-bootstrap do [routeIdentity].
     *
     * [prefill] é o que a 1b depositou: o backend devolve o telefone vazio para conta nova,
     * e sem preferir o depósito a pessoa redigitaria o mesmo número duas telas depois.
     */
    private fun readyOrIdentityGate(
        session: AccessSession,
        prefill: PendingIdentity? = null,
    ): SessionAccessState =
        if (session.user.phoneRequired) {
            SessionAccessState.CompletingIdentity(
                session = session,
                name = prefill?.name?.takeIf { it.isNotEmpty() } ?: session.user.displayName,
                phone = prefill?.phone?.takeIf { it.isNotEmpty() } ?: session.user.phone.orEmpty(),
                photo = prefill?.photo,
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

/**
 * Identidade guardada entre um passo e o próximo do cadastro.
 *
 * @param submitWhenReady `true` (caminho pré-bootstrap da 1c): quando a sessão existir, sobe
 * o perfil sozinho. `false` (depósito da 1b, VUL-101): só preenche a 1c — a pessoa ainda
 * escolhe foto e toca em concluir. Os dois compartilham o carregador; o bit é o que impede
 * o cadastro da 1b de autoenviar sem a pessoa ver a tela.
 */
private data class PendingIdentity(
    val name: String,
    val phone: String,
    val photo: ProfilePhotoResult.Selected?,
    val submitWhenReady: Boolean = true,
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

/**
 * O que o passo da foto devolve. Três casos, e nenhum deles é um estado entregue em
 * silêncio: quem chama tem de decidir o que fazer com cada um, e o compilador cobra.
 */
private sealed interface PhotoOutcome {
    /** Subiu agora, já tinha subido, ou não havia foto: a cadeia segue com esta identidade. */
    data class Sent(val identity: SessionAccessState.CompletingIdentity) : PhotoOutcome

    /** Só a foto falhou; o aviso já está na tela e o perfil sobe do mesmo jeito. */
    data class Refused(val identity: SessionAccessState.CompletingIdentity) : PhotoOutcome

    /** A conta mudou no meio: não há mais cadeia, e nada mais deve sair daqui. */
    data object ContextLost : PhotoOutcome
}
