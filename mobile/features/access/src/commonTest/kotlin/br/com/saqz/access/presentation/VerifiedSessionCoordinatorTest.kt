package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionAccessStateMachineTest {

    // ---- a trava de e-mail saiu (VUL-76 no backend, VUL-84 aqui) ----

    @Test
    fun `unverified authentication bootstraps instead of blocking`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertEquals(1, fixture.session.calls)
    }

    @Test
    fun `a named identity bootstraps without touching the provider`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertTrue(fixture.auth.tokenCalls.isEmpty())
        assertTrue(fixture.auth.nameUpdates.isEmpty())
        assertEquals(0, fixture.auth.reloadCalls)
    }

    // ---- emailVerified chega ao estado Ready ----

    @Test
    fun `ready carries the unverified email signal from the session`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(session))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertFalse(assertIs<SessionAccessState.Ready>(fixture.machine.state.value).emailVerified)
    }

    @Test
    fun `ready carries the verified email signal from the session`() = runTest {
        val confirmed = session.copy(user = session.user.copy(emailVerified = true))
        val fixture = fixture(this, SaqzResult.Success(confirmed))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        assertTrue(assertIs<SessionAccessState.Ready>(fixture.machine.state.value).emailVerified)
    }

    // ---- a volta do plano de fundo faz a faixa sumir sozinha (VUL-91) ----

    @Test
    fun `returning from the background clears the unverified signal once the provider confirms`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(session))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        fixture.machine.onIntent(SessionIntent.RefreshEmailVerification)
        assertEquals(1, fixture.auth.reloadCalls)
        fixture.auth.completeAuth(AuthResult.Success(verified))

        assertTrue(assertIs<SessionAccessState.Ready>(fixture.machine.state.value).emailVerified)
    }

    @Test
    fun `a reload that still comes back unverified keeps the banner up`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(session))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        fixture.machine.onIntent(SessionIntent.RefreshEmailVerification)
        fixture.auth.completeAuth(AuthResult.Success(unverified))

        assertFalse(assertIs<SessionAccessState.Ready>(fixture.machine.state.value).emailVerified)
    }

    /**
     * O reload da conta A voltando depois de a conta B ter entrado: sem conferir de quem é
     * a resposta, o "confirmado" de A apagava a faixa de B — e o e-mail por confirmar é o
     * de B. Sair e entrar com outra conta leva segundos; o reload leva uma volta de rede.
     */
    @Test
    fun `a reload from a session that already ended does not touch the one that replaced it`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(session))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()
        fixture.machine.onIntent(SessionIntent.RefreshEmailVerification)
        assertEquals(1, fixture.auth.reloadCalls)

        // A conta B entra enquanto o reload de A ainda está no ar.
        val other = session.copy(user = session.user.copy(id = "outra-pessoa"))
        fixture.session.result = SaqzResult.Success(other)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        fixture.auth.completeAuth(AuthResult.Success(verified))

        val ready = assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertEquals("outra-pessoa", ready.session.user.id)
        assertFalse(ready.emailVerified)
    }

    // Sem faixa não há o que recarregar: a volta do plano de fundo é frequente e o provedor
    // cobra rede por reload.
    @Test
    fun `refreshing does not touch the provider when the email is already confirmed`() = runTest {
        val confirmed = session.copy(user = session.user.copy(emailVerified = true))
        val fixture = fixture(this, SaqzResult.Success(confirmed))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        fixture.machine.onIntent(SessionIntent.RefreshEmailVerification)

        assertEquals(0, fixture.auth.reloadCalls)
    }

    @Test
    fun `refreshing outside a ready session does nothing`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.DataFailure(DataError.Unknown)))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        fixture.machine.onIntent(SessionIntent.RefreshEmailVerification)

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
        assertEquals(0, fixture.auth.reloadCalls)
    }

    // ---- o portão pré-bootstrap: o nome é pré-condição do backend ----

    // A regressão que o `BootstrapSession` produzia: ele recusa a identidade sem nome
    // utilizável antes de tocar o repositório, então quem entra por um provedor sem nome
    // nunca recebe sessão. Pedir o nome só depois do bootstrap prendia a pessoa no erro.
    @Test
    fun `an identity without a display name reaches identity completion instead of the error`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified.copy(displayName = null))))
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertNull(state.session)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `a blank display name is treated as missing`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified.copy(displayName = " "))))
        runCurrent()

        assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `claiming the name raises it to the provider before bootstrapping`() = runTest {
        val fixture = namelessFixture(SaqzResult.Success(phoneRequiredSession))

        fixture.claimIdentity(name = "Pessoa Nova")
        runCurrent()

        assertEquals(listOf("Pessoa Nova"), fixture.auth.nameUpdates)
        // Token renovado à força: o bootstrap lê o nome da identidade do token, não do corpo.
        assertEquals(listOf(true), fixture.auth.tokenCalls)
        assertEquals(1, fixture.session.calls)
    }

    // O telefone digitado antes do bootstrap não pode se perder no caminho: ele espera a
    // sessão existir e sobe assim que ela existe, sem a pessoa passar pela 1c duas vezes.
    @Test
    fun `the phone typed before bootstrap is submitted once the session exists`() = runTest {
        val fixture = namelessFixture(SaqzResult.Success(phoneRequiredSession))

        fixture.claimIdentity(name = "Pessoa Nova", phone = "(11) 99999-0000")
        fixture.session.profileResult = SaqzResult.Success(session)
        runCurrent()

        assertEquals(listOf<Pair<String, String?>>("+5511999990000" to "Pessoa Nova"), fixture.session.profileCalls)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    @Test
    fun `a bootstrap failure after the claim keeps the typed identity for the retry`() = runTest {
        val fixture = namelessFixture(SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity)))
        fixture.claimIdentity(name = "Pessoa Nova", phone = "(11) 99999-0000")
        runCurrent()
        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)

        fixture.session.result = SaqzResult.Success(phoneRequiredSession)
        fixture.session.profileResult = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.RetryBootstrap)
        runCurrent()

        assertEquals(listOf<Pair<String, String?>>("+5511999990000" to "Pessoa Nova"), fixture.session.profileCalls)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    // Falhou o `completeProfile` logo depois do bootstrap? A 1c reaparece já **com** sessão:
    // dali em diante o caminho é o normal, e um segundo envio não repete o nome no provedor.
    @Test
    fun `a refused profile after the claim returns to identity completion with a session`() = runTest {
        val fixture = namelessFixture(SaqzResult.Success(phoneRequiredSession))
        fixture.claimIdentity(name = "Pessoa Nova", phone = "(11) 99999-0000")
        fixture.session.profileResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Server))
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals(phoneRequiredSession, state.session)
        assertEquals("Pessoa Nova", state.name)
        // Os campos voltam normalizados, que é o que subiu. As duas normalizações são
        // idempotentes, então reenviar dali manda exatamente o mesmo.
        assertEquals("+5511999990000", state.phone)
        assertEquals("+5511999990000", normalizedBrMobilePhone(state.phone))
        assertFalse(state.isLoading)
    }

    @Test
    fun `a provider refusal of the name stays on the screen`() = runTest {
        val fixture = namelessFixture()
        fixture.machine.onIntent(SessionIntent.UpdateName("Pessoa Nova"))
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, state.error)
        assertFalse(state.isLoading)
        assertEquals(0, fixture.session.calls)
    }

    // ---- o portão pós-bootstrap: só o telefone ----

    @Test
    fun `a session missing the phone opens identity completion`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(phoneRequiredSession))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals(phoneRequiredSession, state.session)
        assertEquals("Person Name", state.name)
        assertEquals("", state.phone)
    }

    @Test
    fun `identity completion seeds the fields the backend already knows`() = runTest {
        val known = phoneRequiredSession.copy(user = phoneRequiredSession.user.copy(phone = "+5511999990000"))
        val fixture = fixture(this, SaqzResult.Success(known))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals("Person Name", state.name)
        assertEquals("+5511999990000", state.phone)
    }

    @Test
    fun `identity completion sends name and phone in a single call`() = runTest {
        val fixture = identityFixture()

        fixture.machine.onIntent(SessionIntent.UpdateName("Outra Pessoa"))
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        fixture.session.profileResult = SaqzResult.Success(session)
        runCurrent()

        assertEquals(listOf<Pair<String, String?>>("+5511999990000" to "Outra Pessoa"), fixture.session.profileCalls)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        // Com sessão na mão o nome vai no corpo do PATCH: nada a atualizar no provedor.
        assertTrue(fixture.auth.nameUpdates.isEmpty())
    }

    @Test
    fun `an invalid phone stays local and never reaches the backend`() = runTest {
        val fixture = identityFixture()

        fixture.machine.onIntent(SessionIntent.UpdatePhone("1234"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertTrue(state.invalidPhone)
        assertFalse(state.invalidName)
        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    // Uma tela só recusa os dois campos de uma vez — a 1c não tem passo intermediário
    // onde só um erro possa aparecer.
    @Test
    fun `both invalid fields are reported together`() = runTest {
        val fixture = identityFixture()

        fixture.machine.onIntent(SessionIntent.UpdateName("\n"))
        fixture.machine.onIntent(SessionIntent.UpdatePhone("1234"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertTrue(state.invalidName)
        assertTrue(state.invalidPhone)
        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    @Test
    fun `the chosen photo is kept in the identity state`() = runTest {
        val fixture = identityFixture()
        val photo = ProfilePhotoResult.Selected(byteArrayOf(1, 2, 3), "image/jpeg")

        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))

        assertEquals(photo, assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value).photo)
    }

    // ---- a foto: opcional em toda parte (VUL-87) ----

    @Test
    fun `the chosen photo goes up with the profile`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.session.profileResult = SaqzResult.Success(session)

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        assertEquals(1, fixture.session.photoUploads.size)
        assertEquals("image/jpeg", fixture.session.photoUploads.single().second)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    @Test
    fun `completing without a photo never touches the photo endpoint`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.session.profileResult = SaqzResult.Success(session)

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        assertTrue(fixture.session.photoUploads.isEmpty())
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    // Perder o cadastro inteiro por causa de um JPEG é o que não pode acontecer: nome e
    // telefone gravam, a 1c avisa, e o toque seguinte entra sem foto para subir.
    @Test
    fun `a photo that fails to upload does not undo the profile`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.session.photoResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity))
        fixture.session.profileResult = SaqzResult.Success(session)

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertTrue(state.photoFailed)
        assertNull(state.photo)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, fixture.session.profileCalls.size)
    }

    @Test
    fun `the touch after a failed photo enters without retrying the upload`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.session.photoResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Server))
        fixture.session.profileResult = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertEquals(1, fixture.session.photoUploads.size)
        assertEquals(2, fixture.session.profileCalls.size)
    }

    // Escolher outra foto é dizer "tenta de novo": o aviso da anterior sai da tela junto.
    @Test
    fun `choosing another photo clears the warning`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.session.photoResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Server))
        fixture.session.profileResult = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertFalse(state.photoFailed)
        assertEquals(photo, state.photo)
    }

    // O caminho pré-bootstrap carrega a foto na pendência: ela sobe quando a sessão passa
    // a existir, sem a pessoa escolher a imagem duas vezes.
    @Test
    fun `the photo chosen before bootstrap goes up once the session exists`() = runTest {
        val fixture = namelessFixture(SaqzResult.Success(phoneRequiredSession))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))

        fixture.claimIdentity(name = "Pessoa Nova", phone = "(11) 99999-0000")
        fixture.session.profileResult = SaqzResult.Success(session)
        runCurrent()

        assertEquals(1, fixture.session.photoUploads.size)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    // ---- a guarda de geração: resposta com o contexto trocado é descartada ----
    //
    // O voltar da 1c continua clicável enquanto o envio corre, e o escopo da máquina é o
    // singleton do app: nada cancela sozinho. Sem a guarda, a corrotina retomada escreve
    // `Ready` por cima do `SignedOut` e reabre o shell de quem acabou de sair.

    @Test
    fun `a logout during the profile submit never undoes the logout`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.session.profileGate = CompletableDeferred()
        fixture.session.profileResult = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.machine.onIntent(SessionIntent.Logout)
        fixture.session.profileGate!!.complete(Unit)
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    @Test
    fun `a logout during the photo upload never revives the screen`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.session.photoGate = CompletableDeferred()
        fixture.session.photoResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Server))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.machine.onIntent(SessionIntent.Logout)
        fixture.session.photoGate!!.complete(Unit)
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
        // A foto caiu com o contexto: o perfil nem chega a ser enviado.
        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    @Test
    fun `a logout during bootstrap never opens the shell`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(session))
        fixture.session.bootstrapGate = CompletableDeferred()
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        fixture.machine.onIntent(SessionIntent.Logout)
        fixture.session.bootstrapGate!!.complete(Unit)
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    // A janela entre o `scope.launch` e o corpo da corrotina: a cadeia já nasceu, mas o
    // `PUT api/session` ainda não saiu. Sair nesse intervalo não pode emitir o pedido —
    // descartar a resposta depois não o traz de volta.
    @Test
    fun `a logout before bootstrap starts never issues the session put`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(session))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        // Sem `runCurrent`: o bootstrap está agendado e a corrotina ainda não rodou.
        fixture.machine.onIntent(SessionIntent.Logout)
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
        assertEquals(0, fixture.session.calls)
    }

    // O caminho pré-bootstrap responde por callback do provedor, não por corrotina, e tem
    // a mesma janela: sair enquanto o nome sobe não pode trazer a 1c de volta.
    @Test
    fun `a logout while the provider claims the name is not undone by the answer`() = runTest {
        val fixture = namelessFixture()
        fixture.machine.onIntent(SessionIntent.UpdateName("Pessoa Nova"))
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)

        fixture.machine.onIntent(SessionIntent.Logout)
        fixture.auth.completeAuth(AuthResult.Success(verified.copy(displayName = "Pessoa Nova")))
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
        assertEquals(0, fixture.session.calls)
    }

    // Autenticar outra identidade é a outra troca de contexto: o que estava em voo pela
    // conta anterior não pode aterrissar sobre a nova.
    @Test
    fun `a response from the previous account never lands on the next one`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.session.profileGate = CompletableDeferred()
        fixture.session.profileResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Server))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.session.result = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()
        fixture.session.profileGate!!.complete(Unit)
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    // ---- todo passo da cadeia responde à geração, inclusive os que não fazem nada ----

    // O caminho "sem foto" também é um passo da cadeia: não envia nada, mas continua sendo
    // revalidado. A janela é entre o `launch` e a corrotina começar a correr — sair da conta
    // aí deixava o `completeProfile` sair mesmo assim, e ele vai com a sessão que o provedor
    // tiver **agora**, que já é a de outra pessoa.
    @Test
    fun `a logout before the completion coroutine runs stops the profile call`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        fixture.machine.onIntent(SessionIntent.Logout)
        runCurrent()

        assertTrue(fixture.session.profileCalls.isEmpty())
        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    // A mesma janela com foto: nem a imagem sobe.
    @Test
    fun `a logout before the completion coroutine runs stops the photo upload`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        fixture.machine.onIntent(SessionIntent.Logout)
        runCurrent()

        assertTrue(fixture.session.photoUploads.isEmpty())
        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    // A foto que já subiu não sobe de novo: o perfil recusado devolve a 1c com a imagem na
    // tela, e o toque seguinte reenviaria o arquivo inteiro por causa de um erro que não
    // foi dele.
    @Test
    fun `a photo that already went up is not sent again on retry`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.session.profileResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Server))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()
        assertEquals(1, fixture.session.photoUploads.size)

        fixture.session.profileResult = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        assertEquals(1, fixture.session.photoUploads.size)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    // Escolher outra foto é ter de novo o que enviar.
    @Test
    fun `choosing another photo makes it sendable again`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.session.profileResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Server))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.machine.onIntent(SessionIntent.UpdatePhoto(otherPhoto))
        fixture.session.profileResult = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        assertEquals(2, fixture.session.photoUploads.size)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    // ---- o contexto trocado leva junto o que ficou guardado ----

    // O vazamento entre contas: a conta A termina os callbacks do provedor e deixa a
    // identidade guardada esperando o bootstrap; a conta B entra, e o bootstrap **dela** é
    // corrente. Sem limpar a pendência na troca, o nome, o telefone e a foto de A subiam
    // com a sessão de B — a foto de uma pessoa virando a foto de perfil de outra, e o
    // telefone de uma entrando no cadastro da outra.
    @Test
    fun `an identity left pending by one account never travels into the next`() = runTest {
        val fixture = namelessFixture(SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity)))
        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))
        fixture.claimIdentity(name = "Ana Costa", phone = "(11) 98888-0000")
        runCurrent()
        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)

        fixture.session.result = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(otherAccount)))
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertTrue(fixture.session.profileCalls.isEmpty())
        assertTrue(fixture.session.photoUploads.isEmpty())
    }

    // A outra metade da mesma janela: trabalho **disparado** entre o intento de sair e o
    // fim da saída nasceria já com a geração nova e passaria pela guarda.
    @Test
    fun `a completion fired during the logout never reaches the backend`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.auth.deferSignOut = true
        fixture.machine.onIntent(SessionIntent.Logout)

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()
        fixture.auth.completeSignOut()
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    @Test
    fun `a retry fired during the logout never reopens the shell`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity)))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()
        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
        fixture.auth.deferSignOut = true
        fixture.machine.onIntent(SessionIntent.Logout)

        fixture.session.result = SaqzResult.Success(session)
        fixture.machine.onIntent(SessionIntent.RetryBootstrap)
        runCurrent()
        fixture.auth.completeSignOut()
        runCurrent()

        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
        assertEquals(1, fixture.session.calls)
    }

    @Test
    fun `identity completion is single flight`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        assertEquals(1, fixture.session.profileCalls.size)
    }

    @Test
    fun `a connectivity failure on completion is retryable in place`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.session.profileResult = SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity))

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `editing a field clears the error it produced`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("1234"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertFalse(state.invalidPhone)
        assertNull(state.error)
    }

    // ---- a recusa do backend cai no campo que ela nomeia ----

    @Test
    fun `a name refused by the backend lands on the name field`() = runTest {
        val state = refusedBy(mapOf("displayName" to listOf("must be between 2 and 80 characters")))

        assertTrue(state.invalidName)
        assertFalse(state.invalidPhone)
        assertNull(state.error)
    }

    @Test
    fun `a phone refused by the backend lands on the phone field`() = runTest {
        val state = refusedBy(mapOf("phone" to listOf("must be a valid Brazilian mobile number")))

        assertTrue(state.invalidPhone)
        assertFalse(state.invalidName)
    }

    @Test
    fun `both fields refused at once are both marked`() = runTest {
        val state = refusedBy(
            mapOf("displayName" to listOf("invalid"), "phone" to listOf("invalid")),
        )

        assertTrue(state.invalidName)
        assertTrue(state.invalidPhone)
    }

    // Recusa que não nomeia campo conhecido não pode sumir: sem isso a tela ficaria sem
    // erro nenhum, e o botão pareceria não ter feito nada.
    @Test
    fun `a refusal naming no known field falls back to a visible error`() = runTest {
        val state = refusedBy(mapOf("timezone" to listOf("unexpected")))

        assertFalse(state.invalidName)
        assertFalse(state.invalidPhone)
        assertEquals(AuthUiError.UNKNOWN, state.error)
    }

    // ---- bootstrap ----

    @Test
    fun `backend failure exposes retry without protected session`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.DataFailure(DataError.Server)))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `bootstrap retry preserves the native session and can recover`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity)))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()
        fixture.session.result = SaqzResult.Success(session)

        fixture.machine.onIntent(SessionIntent.RetryBootstrap)
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertEquals(2, fixture.session.calls)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    // A recusa some do backend no VUL-76, mas o tipo sobrevive no domínio: se voltar a
    // chegar, é erro de bootstrap como qualquer outro — não há mais tela para onde mandar.
    @Test
    fun `a stale email-not-verified refusal is a plain bootstrap error`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.EmailNotVerified))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
    }

    @Test
    fun `unauthenticated bootstrap remains a retryable bootstrap error`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.Unauthenticated))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `validation without global message uses generic bootstrap error state`() = runTest {
        val error = AccessError.Validation(
            ValidationDetails(globalMessages = emptyList(), fieldMessages = mapOf("email" to listOf("invalid"))),
        )
        val fixture = fixture(this, SaqzResult.Failure(error))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
    }

    // ---- saída ----

    @Test
    fun `logout clears selected group pending invite and native session`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        fixture.machine.onIntent(SessionIntent.Logout)

        assertEquals(listOf<String?>(null), fixture.local.selectedWrites)
        assertEquals(listOf<String?>(null), fixture.local.pendingWrites)
        assertEquals(1, fixture.auth.signOutCalls)
        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    // Sair no meio da 1c descarta o que foi digitado: a próxima pessoa a entrar neste
    // aparelho não pode herdar o telefone da anterior.
    @Test
    fun `logout drops the identity pending before bootstrap`() = runTest {
        val fixture = namelessFixture(SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity)))
        fixture.claimIdentity(name = "Pessoa Nova", phone = "(11) 99999-0000")
        runCurrent()

        fixture.machine.onIntent(SessionIntent.Logout)
        fixture.session.result = SaqzResult.Success(phoneRequiredSession)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    @Test
    fun `session invalidation uses the same local logout path`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        fixture.machine.invalidate()

        assertEquals(listOf<String?>(null), fixture.local.selectedWrites)
        assertEquals(listOf<String?>(null), fixture.local.pendingWrites)
        assertEquals(1, fixture.auth.signOutCalls)
        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        result: SaqzResult<AccessSession, AccessError> = SaqzResult.Success(session),
    ): Fixture {
        val auth = FakeAuthPort()
        val local = FakeLocalState()
        val gateway = FakeSessionGateway(result)
        return Fixture(SessionAccessStateMachine(auth, local, gateway, scope), auth, local, gateway)
    }

    /** Parado na 1c **com** sessão: o backend pediu o telefone e o nome já veio preenchido. */
    private fun TestScope.identityFixture(): Fixture {
        val fixture = fixture(this, SaqzResult.Success(phoneRequiredSession))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()
        assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        return fixture
    }

    /** Parado na 1c **sem** sessão: o provedor não deu nome, então o bootstrap nem correu. */
    private fun TestScope.namelessFixture(
        result: SaqzResult<AccessSession, AccessError> = SaqzResult.Success(session),
    ): Fixture {
        val fixture = fixture(this, result)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified.copy(displayName = null))))
        assertNull(assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value).session)
        return fixture
    }

    /** Preenche a 1c pré-bootstrap e leva o provedor até o ponto em que o bootstrap dispara. */
    private fun Fixture.claimIdentity(name: String = "Person Name", phone: String = "(11) 99999-0000") {
        machine.onIntent(SessionIntent.UpdateName(name))
        machine.onIntent(SessionIntent.UpdatePhone(phone))
        machine.onIntent(SessionIntent.CompleteIdentity)
        auth.completeAuth(AuthResult.Success(verified.copy(displayName = name)))
        auth.completeToken(TokenResult.Success("fresh-token"))
    }

    /** A 1c com sessão levando uma recusa por campo do backend. */
    private fun TestScope.refusedBy(fields: Map<String, List<String>>): SessionAccessState.CompletingIdentity {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.session.profileResult = SaqzResult.Failure(
            AccessError.Validation(ValidationDetails(globalMessages = emptyList(), fieldMessages = fields)),
        )

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        return assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
    }

    private class FakeSessionGateway(var result: SaqzResult<AccessSession, AccessError>) : SessionGateway {
        var calls = 0
        val profileCalls = mutableListOf<Pair<String, String?>>()

        /**
         * O caminho pré-bootstrap encadeia `bootstrap` e `completeProfile` na mesma volta,
         * então os dois precisam responder separado — senão uma recusa do perfil chega ao
         * bootstrap e o teste mede outra coisa. Nulo herda o [result].
         */
        var profileResult: SaqzResult<AccessSession, AccessError>? = null

        val photoUploads = mutableListOf<Pair<ByteArray, String>>()
        var photoResult: SaqzResult<Unit, AccessError> = SaqzResult.Success(Unit)

        /**
         * Portões para segurar a resposta no ar: é a única forma de o teste sair da conta
         * **durante** a suspensão, que é exatamente a janela que a guarda de geração fecha.
         */
        var photoGate: CompletableDeferred<Unit>? = null
        var profileGate: CompletableDeferred<Unit>? = null
        var bootstrapGate: CompletableDeferred<Unit>? = null

        override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, AccessError> {
            photoUploads += bytes to mediaType
            photoGate?.await()
            return photoResult
        }

        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> {
            calls += 1
            bootstrapGate?.await()
            return result
        }

        override suspend fun completeProfile(
            phone: String,
            displayName: String?,
        ): SaqzResult<AccessSession, AccessError> {
            profileCalls += phone to displayName
            profileGate?.await()
            return profileResult ?: result
        }
    }

    private class FakeAuthPort : NativeAuthPort {
        val tokenCalls = mutableListOf<Boolean>()
        val nameUpdates = mutableListOf<String>()
        var reloadCalls = 0
        var signOutCalls = 0
        private var authCallback: AuthCallback? = null
        private var tokenCallback: TokenCallback? = null

        override fun reloadUser(done: AuthCallback) { reloadCalls += 1; authCallback = done }
        override fun updateDisplayName(name: String, done: AuthCallback) { nameUpdates += name; authCallback = done }
        override fun sendVerification(done: ResultCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) { tokenCalls += forceRefresh; tokenCallback = done }
        /**
         * Sair do provedor é assíncrono de verdade, e é durante essa espera que os botões
         * da tela continuam alcançáveis — segurar a resposta é a única forma de o teste
         * disparar trabalho novo dentro da janela.
         */
        var deferSignOut = false
        private var signOutCallback: ResultCallback? = null

        override fun signOut(done: ResultCallback) {
            signOutCalls += 1
            if (deferSignOut) signOutCallback = done else done.complete(OperationResult.Success)
        }

        fun completeSignOut() {
            val pending = signOutCallback!!
            signOutCallback = null
            pending.complete(OperationResult.Success)
        }
        fun completeAuth(result: AuthResult) = authCallback!!.complete(result)
        fun completeToken(result: TokenResult) = tokenCallback!!.complete(result)
        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
    }

    private class FakeLocalState : LocalAccessStatePort {
        val selectedWrites = mutableListOf<String?>()
        val pendingWrites = mutableListOf<String?>()
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) { selectedWrites += value; done.complete(OperationResult.Success) }
        override fun writePendingInvite(value: String?, done: ResultCallback) { pendingWrites += value; done.complete(OperationResult.Success) }
        override fun readSelectedGroupId(done: ValueCallback) = Unit
        override fun readPendingInvite(done: ValueCallback) = Unit
    }

    private data class Fixture(
        val machine: SessionAccessStateMachine,
        val auth: FakeAuthPort,
        val local: FakeLocalState,
        val session: FakeSessionGateway,
    )

    private companion object {
        val unverified = NativeUser("subject", "person@example.test", false, "Person Name")
        val verified = unverified.copy(emailVerified = true)
        val session = AccessSession(AccessUser("user-id", "person@example.test", "Person Name"), emptyList())
        val phoneRequiredSession = session.copy(user = session.user.copy(phoneRequired = true))
        val photo = ProfilePhotoResult.Selected(byteArrayOf(1, 2, 3), "image/jpeg")

        val otherPhoto = ProfilePhotoResult.Selected(byteArrayOf(9, 9, 9), "image/jpeg")

        /** Outra pessoa, no mesmo aparelho: nome próprio, para o bootstrap dela passar. */
        val otherAccount = NativeUser("other-subject", "outra@example.test", true, "Outra Pessoa")
    }
}
