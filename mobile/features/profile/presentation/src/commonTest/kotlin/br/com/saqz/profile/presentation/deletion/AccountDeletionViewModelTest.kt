package br.com.saqz.profile.presentation.deletion

import br.com.saqz.domain.DataError
import br.com.saqz.profile.domain.*
import br.com.saqz.profile.fake.FakeProfileGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    @Test fun `requires explicit confirmation and a password for password authentication`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway()
        val vm = AccountDeletionViewModel(gateway, Authorization())
        vm.onIntent(AccountDeletionIntent.Delete(AccountDeletionMethod.GOOGLE))
        vm.onIntent(AccountDeletionIntent.Confirm(true))
        vm.onIntent(AccountDeletionIntent.Delete(AccountDeletionMethod.PASSWORD))
        assertFalse(gateway.accountDeleted)
        assertFalse(vm.state.value.isBusy)
    }

    @Test fun `success deletes displayed identity and emits logout only after server success`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway()
        val vm = AccountDeletionViewModel(gateway, Authorization())
        vm.onIntent(AccountDeletionIntent.Password("secret"))
        vm.onIntent(AccountDeletionIntent.Confirm(true))
        vm.onIntent(AccountDeletionIntent.Delete(AccountDeletionMethod.PASSWORD))
        assertTrue(gateway.accountDeleted)
        assertEquals(gateway.profile.user.id, gateway.deletedUserId)
        assertEquals("", vm.state.value.password)
        assertTrue(vm.state.value.deleted)
        assertEquals(AccountDeletionEffect.DELETED, vm.effects.first())
    }

    @Test fun `cancelled or rejected authentication never deletes and permits retry`() = runTest(dispatcher) {
        for (result in listOf(AccountDeletionAuthorizationResult.CANCELLED, AccountDeletionAuthorizationResult.REJECTED)) {
            val gateway = FakeProfileGateway()
            val vm = AccountDeletionViewModel(gateway, Authorization(result))
            vm.onIntent(AccountDeletionIntent.Confirm(true))
            vm.onIntent(AccountDeletionIntent.Delete(AccountDeletionMethod.GOOGLE))
            assertFalse(gateway.accountDeleted)
            assertFalse(vm.state.value.isBusy)
            assertTrue(vm.state.value.canDelete)
            assertEquals(if (result == AccountDeletionAuthorizationResult.CANCELLED) null else AccountDeletionError.AUTHENTICATION, vm.state.value.error)
        }
    }

    @Test fun `server failure retains account and offers recoverable retry`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply { deleteSessionError = ProfileError.DataFailure(DataError.Connectivity) }
        val vm = AccountDeletionViewModel(gateway, Authorization())
        vm.onIntent(AccountDeletionIntent.Confirm(true))
        vm.onIntent(AccountDeletionIntent.Delete(AccountDeletionMethod.GOOGLE))
        assertFalse(gateway.accountDeleted)
        assertEquals(AccountDeletionError.REQUEST, vm.state.value.error)
        gateway.deleteSessionError = null
        vm.onIntent(AccountDeletionIntent.Delete(AccountDeletionMethod.GOOGLE))
        assertTrue(gateway.accountDeleted)
        assertEquals(AccountDeletionEffect.DELETED, vm.effects.first())
    }

    @Test fun `double submission cannot start another deletion while authorization is pending`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway()
        var completion: ((AccountDeletionAuthorizationResult) -> Unit)? = null
        val authorization = object : AccountDeletionAuthorization {
            override val supportsApple = true
            override fun authorize(expectedUserId: String, method: AccountDeletionMethod, password: String, done: (AccountDeletionAuthorizationResult) -> Unit): AccountDeletionCancellation {
                check(completion == null)
                completion = done
                return AccountDeletionCancellation { }
            }
        }
        val vm = AccountDeletionViewModel(gateway, authorization)
        vm.onIntent(AccountDeletionIntent.Confirm(true))
        repeat(2) { vm.onIntent(AccountDeletionIntent.Delete(AccountDeletionMethod.APPLE)) }
        assertTrue(vm.state.value.isBusy)
        assertFalse(gateway.accountDeleted)
        completion!!(AccountDeletionAuthorizationResult.AUTHORIZED)
        assertTrue(gateway.accountDeleted)
        assertEquals(1, gateway.deleteSessionCalls)
    }

    private class Authorization(val result: AccountDeletionAuthorizationResult = AccountDeletionAuthorizationResult.AUTHORIZED) : AccountDeletionAuthorization {
        override val supportsApple = true
        override fun authorize(expectedUserId: String, method: AccountDeletionMethod, password: String, done: (AccountDeletionAuthorizationResult) -> Unit): AccountDeletionCancellation {
            done(result)
            return AccountDeletionCancellation { }
        }
    }
}
