package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.composeapp.testSaqzPlatformDependencies
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.groups.domain.membership.InvitePreview
import br.com.saqz.groups.domain.membership.InviteRedeem
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.invite.GroupInviteCoordinator
import br.com.saqz.groups.invite.GroupInviteEffect
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupNativeFailureCode
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Convite guardado de ponta a ponta sem Compose: a máquina de sessão real (acesso) e o
 * `GroupInviteCoordinator` real (grupos) conversam por **um** armazenamento em memória com a mesma
 * chave, como nas três plataformas. O [Driver] repete o que o `SaqzNavHost` faz com a sessão e com
 * os efeitos (a cópia `pendingInviteCode`/`inviteContext` e a transição de sair da conta).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingInviteLifecycleTest {
    @Test
    fun nextAccountNeverRedeemsTheInviteOfTheOneWhoLeft() = runTest {
        val f = fixture(redeemResult = pending())
        f.coordinator.acceptInvite("convite")
        runCurrent()
        assertEquals("convite", f.store.value)

        f.login("conta-a")
        runCurrent()
        // Pedir entrada conta como aceitar: guardado e cópia da navegação vazios.
        assertEquals(listOf("preview:convite", "redeem:convite"), f.gateway.calls)
        assertNull(f.store.value)
        assertNull(f.driver.navCopy)

        f.gateway.calls.clear()
        f.logout()
        f.login("conta-b")
        runCurrent()
        assertEquals(emptyList(), f.gateway.calls)
        assertNull(f.store.value)
        assertNull(f.driver.navCopy)
    }

    @Test
    fun leavingTheAccountWithAnInviteKeptByNetworkFailureClearsBothCopies() = runTest {
        val f = fixture(previewResult = SaqzResult.Failure(InviteError.DataFailure(DataError.Connectivity)))
        f.coordinator.acceptInvite("convite")
        runCurrent()
        f.login("conta-a")
        runCurrent()
        // Erro de rede não é terminal: o convite continua guardado para a próxima tentativa.
        assertEquals("convite", f.store.value)
        assertEquals("convite", f.driver.navCopy)

        f.gateway.calls.clear()
        f.logout()
        // O login não pode seguir mostrando "Entrando no <grupo>" do convite anterior.
        assertNull(f.driver.navCopy)
        assertNull(f.store.value)
        f.login("conta-b")
        runCurrent()
        assertEquals(emptyList(), f.gateway.calls)
    }

    @Test
    fun requestingEntryFromTheInviteScreenClearsStoredAndNavigationCopies() = runTest {
        val f = fixture(
            previewResult = SaqzResult.Failure(InviteError.DataFailure(DataError.Connectivity)),
            redeemResult = pending(),
        )
        f.coordinator.acceptInvite("convite")
        runCurrent()
        f.login("conta-a")
        runCurrent()
        assertEquals("convite", f.store.value)

        // Tela do convite: "Pedir para entrar" = onRequest do SaqzNavHost (mesmo InviteCopy).
        f.driver.copy.clearAfterAction(f.coordinator, "convite")
        runCurrent()
        assertNull(f.store.value)
        assertNull(f.driver.navCopy)

        // Reabrir a sessão (novo coordinator sobre o mesmo armazenamento) não reabre o convite.
        f.gateway.calls.clear()
        val reopened = GroupInviteCoordinator(NoLinks, f.store, f.gateway, backgroundScope)
        assertNull(reopened.readPendingInviteCode())
        reopened.onAuthenticated()
        runCurrent()
        assertEquals(emptyList(), f.gateway.calls)
    }

    @Test
    fun failingToEraseOnLogoutStillKeepsTheNextAccountOutOfTheGroup() = runTest {
        val f = fixture(previewResult = SaqzResult.Failure(InviteError.DataFailure(DataError.Connectivity)))
        f.coordinator.acceptInvite("convite")
        runCurrent()
        f.login("conta-a")
        runCurrent()
        assertEquals("convite", f.store.value)

        f.store.failWrites = true
        f.logout()
        assertEquals("convite", f.store.value)
        assertNull(f.driver.navCopy)

        f.store.failWrites = false
        f.gateway.calls.clear()
        f.login("conta-b")
        runCurrent()
        assertEquals(emptyList(), f.gateway.calls)
        assertNull(f.store.value)
    }

    private fun pending() = SaqzResult.Success(InviteRedeem(InviteRedeemStatus.PENDING, GroupId("g"), "ATHLETE"))

    private fun TestScope.fixture(
        previewResult: SaqzResult<InvitePreview, InviteError> = SaqzResult.Success(
            InvitePreview("Vôlei", "Ana", entryRequiresApproval = true),
        ),
        redeemResult: SaqzResult<InviteRedeem, InviteError> = pending(),
    ): Fixture {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val store = SharedStore()
        val gateway = RecordingInviteGateway(previewResult, redeemResult)
        val sessions = object : SessionGateway {
            var current = AccessSession(AccessUser("conta", null, "Pessoa"), emptyList())
            override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String) = SaqzResult.Success(Unit)
            override suspend fun bootstrap() = SaqzResult.Success(current)
            override suspend fun completeProfile(phone: String, displayName: String?) = SaqzResult.Success(current)
        }
        val auth = testSaqzPlatformDependencies().access.auth
        val machine = SessionAccessStateMachine(auth, store, sessions, scope)
        val coordinator = GroupInviteCoordinator(NoLinks, store, gateway, scope)
        val driver = Driver(machine, coordinator)
        scope.launch { driver.observe() }
        scope.launch {
            coordinator.effects.collect(driver::onEffect)
        }
        return Fixture(machine, coordinator, store, gateway, driver) { id ->
            sessions.current = AccessSession(AccessUser(id, null, "Pessoa"), emptyList())
        }
    }

    private class Fixture(
        val machine: SessionAccessStateMachine,
        val coordinator: GroupInviteCoordinator,
        val store: SharedStore,
        val gateway: RecordingInviteGateway,
        val driver: Driver,
        private val setAccount: (String) -> Unit,
    ) {
        fun login(id: String) {
            setAccount(id)
            machine.onIntent(
                SessionIntent.Accept(AuthTransition.Authenticated(NativeUser(id, null, true, "Pessoa"))),
            )
        }

        fun logout() = machine.onIntent(SessionIntent.Logout)
    }

    /** O que o `SaqzNavHost` faz com a sessão, usando o mesmo [InviteCopy] dele. */
    private class Driver(
        private val machine: SessionAccessStateMachine,
        private val coordinator: GroupInviteCoordinator,
    ) {
        val copy = InviteCopy()
        var navCopy: String?
            get() = copy.code
            set(value) { copy.code = value }
        private var authenticated = false

        suspend fun observe() {
            machine.state.collect { session ->
                copy.onSessionChanged(session, coordinator)
                if (session is SessionAccessState.Ready && !authenticated) {
                    authenticated = true
                    coordinator.onAuthenticated()
                } else if (session !is SessionAccessState.Ready && authenticated) {
                    authenticated = false
                    coordinator.onSignedOut()
                }
                if (copy.code == null && session is SessionAccessState.Ready) {
                    copy.code = coordinator.readPendingInviteCode()
                }
            }
        }

        fun onEffect(effect: GroupInviteEffect) {
            when (effect) {
                is GroupInviteEffect.OpenInviteLanding -> copy.code = effect.code
                is GroupInviteEffect.NavigateToGroup -> copy.onRedeemed()
                else -> Unit
            }
            coordinator.redeemHandled()
        }
    }

    private object NoLinks : NativeGroupLinkPort {
        override fun start(listener: GroupLinkEventListener) = object : GroupCancelable {
            override fun cancel() = Unit
        }
    }

    /** Uma chave só para acesso e grupos, como `pending_invite_ciphertext` / `saqz.access.pendingInvite`. */
    private class SharedStore : LocalAccessStatePort, LocalGroupStatePort {
        var value: String? = null
        var failWrites = false

        override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(null))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(value))
        override fun writePendingInvite(value: String?, done: ResultCallback) {
            if (failWrites) {
                done.complete(OperationResult.Failure(br.com.saqz.access.domain.port.NativeFailureCode.UNKNOWN))
            } else {
                this.value = value
                done.complete(OperationResult.Success)
            }
        }

        override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) =
            done.complete(GroupOperationResult.Success)
        override fun readPendingInvite(done: GroupValueCallback) = done.complete(GroupValueResult.Success(value))
        override fun writePendingInvite(value: String?, done: GroupResultCallback) {
            if (failWrites) {
                done.complete(GroupOperationResult.Failure(GroupNativeFailureCode.UNKNOWN))
            } else {
                this.value = value
                done.complete(GroupOperationResult.Success)
            }
        }

        override fun readPendingAttendanceLink(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) =
            done.complete(GroupOperationResult.Success)
    }

    private class RecordingInviteGateway(
        private val previewResult: SaqzResult<InvitePreview, InviteError>,
        private val redeemResult: SaqzResult<InviteRedeem, InviteError>,
    ) : InviteGateway {
        val calls = mutableListOf<String>()

        override suspend fun preview(code: InviteCode): SaqzResult<InvitePreview, InviteError> {
            calls += "preview:${code.value}"
            return previewResult
        }

        override suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError> {
            calls += "redeem:${code.value}"
            return redeemResult
        }
    }
}
