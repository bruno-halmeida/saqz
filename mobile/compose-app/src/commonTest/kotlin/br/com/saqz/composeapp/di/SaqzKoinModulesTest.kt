package br.com.saqz.composeapp.di

import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraftStorePort
import br.com.saqz.groups.presentation.finance.charges.MonthlyDraftReadResult
import br.com.saqz.groups.presentation.finance.charges.MonthlyDraftWriteResult
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraft
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftReadResult
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftStorePort
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftWriteResult
import br.com.saqz.groups.presentation.games.editor.GameDraftReadResult
import br.com.saqz.groups.presentation.games.editor.GameDraftStorePort
import br.com.saqz.groups.presentation.games.editor.GameDraftWriteResult
import br.com.saqz.groups.presentation.games.editor.GameEditorDraft
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionApi
import br.com.saqz.network.SessionGateway
import br.com.saqz.network.SessionInvalidator
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class SaqzKoinModulesTest {
    private val fixturesModule = module {
        single { NetworkConfig(environment = NetworkEnvironment.Test, baseUrl = "https://api.invalid") }
        single<NativeAuthPort> { FakeAuthPort }
        single {
            SaqzDraftStores(
                groupDrafts = FakeGroupDraftStore,
                gameDrafts = FakeGameDraftStore,
                monthlyChargeDrafts = FakeMonthlyChargeDraftStore,
                expenseDrafts = FakeExpenseDraftStore,
            )
        }
    }

    @Test
    fun networkGraphResolvesWithSingletonClient() {
        val app = koinApplication {
            modules(fixturesModule, networkModule, draftsModule)
        }
        val koin = app.koin

        assertSame(koin.get<NetworkClient>(), koin.get<NetworkClient>())
        assertSame(koin.get<AuthenticatedNetworkClient>(), koin.get<AuthenticatedNetworkClient>())
        assertSame(koin.get<DelegatingSessionInvalidator>(), koin.get<SessionInvalidator>())
        assertIs<SessionApi>(koin.get<SessionGateway>())

        app.close()
    }

    @Test
    fun draftsModuleResolvesPlatformStores() {
        val app = koinApplication {
            modules(fixturesModule, networkModule, draftsModule)
        }
        val koin = app.koin

        assertSame(FakeGroupDraftStore, koin.get<GroupDraftStorePort>())
        assertSame(FakeGameDraftStore, koin.get<GameDraftStorePort>())
        assertSame(FakeMonthlyChargeDraftStore, koin.get<MonthlyChargeDraftStorePort>())
        assertSame(FakeExpenseDraftStore, koin.get<ExpenseDraftStorePort>())

        app.close()
    }
}

private object FakeAuthPort : NativeAuthPort {
    override fun observe(listener: AuthStateListener): Cancelable {
        listener.onStateChanged(AuthState.SignedOut)
        return object : Cancelable {
            override fun cancel() = Unit
        }
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun signInWithPassword(email: String, password: String, done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun signInWithGoogle(done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun sendVerification(done: ResultCallback) =
        done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun reloadUser(done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun sendPasswordReset(email: String, done: ResultCallback) =
        done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun updateDisplayName(name: String, done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
        done.complete(TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)
}

private object FakeGroupDraftStore : GroupDraftStorePort {
    override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) =
        done(GroupDraftReadResult.Success(null))

    override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) =
        done(GroupDraftWriteResult.Success)

    override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) =
        done(GroupDraftWriteResult.Success)
}

private object FakeGameDraftStore : GameDraftStorePort {
    override fun read(groupId: String, resourceId: String?, done: (GameDraftReadResult) -> Unit) =
        done(GameDraftReadResult.Success(null))

    override fun write(draft: GameEditorDraft, done: (GameDraftWriteResult) -> Unit) =
        done(GameDraftWriteResult.Success)

    override fun clear(groupId: String, resourceId: String?, commandKey: String, done: (GameDraftWriteResult) -> Unit) =
        done(GameDraftWriteResult.Success)
}

private object FakeMonthlyChargeDraftStore : MonthlyChargeDraftStorePort {
    override fun read(groupId: String, done: (MonthlyDraftReadResult) -> Unit) =
        done(MonthlyDraftReadResult.Success(null))

    override fun write(draft: MonthlyChargeDraft, done: (MonthlyDraftWriteResult) -> Unit) =
        done(MonthlyDraftWriteResult.Success)

    override fun clear(groupId: String, commandKey: String, done: (MonthlyDraftWriteResult) -> Unit) =
        done(MonthlyDraftWriteResult.Success)
}

private object FakeExpenseDraftStore : ExpenseDraftStorePort {
    override fun read(groupId: String, expenseId: String?, done: (ExpenseDraftReadResult) -> Unit) =
        done(ExpenseDraftReadResult.Success(null))

    override fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit) =
        done(ExpenseDraftWriteResult.Success)

    override fun clear(groupId: String, expenseId: String?, commandKey: String, done: (ExpenseDraftWriteResult) -> Unit) =
        done(ExpenseDraftWriteResult.Success)
}
