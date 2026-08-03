package br.com.saqz.composeapp.di

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.access.data.passwordreset.KtorPasswordResetGateway
import br.com.saqz.access.data.session.KtorSessionGateway
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.domain.session.SessionInvalidator as AccessSessionInvalidator
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.login.LoginViewModel
import br.com.saqz.access.presentation.newpassword.NewPasswordViewModel
import br.com.saqz.access.presentation.register.RegisterViewModel
import br.com.saqz.access.presentation.resetcode.ResetCodeViewModel
import br.com.saqz.composeapp.AccessRuntimeDependencies
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.composeapp.GroupsRuntimeDependencies
import br.com.saqz.composeapp.navigation.AccessRuntimeContract
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.model.ExpenseDraft
import br.com.saqz.groups.model.GameEditorDraft
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.MonthlyChargeDraft
import br.com.saqz.groups.port.ExpenseDraftReadResult
import br.com.saqz.groups.port.ExpenseDraftStorePort
import br.com.saqz.groups.port.ExpenseDraftWriteResult
import br.com.saqz.groups.port.GameDraftReadResult
import br.com.saqz.groups.port.GameDraftStorePort
import br.com.saqz.groups.port.GameDraftWriteResult
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupInviteUrlReadCallback
import br.com.saqz.groups.port.GroupInviteUrlReadResult
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.GroupInviteUrlWriteCallback
import br.com.saqz.groups.port.GroupInviteUrlWriteResult
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.MonthlyChargeDraftStorePort
import br.com.saqz.groups.port.MonthlyDraftReadResult
import br.com.saqz.groups.port.MonthlyDraftWriteResult
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.groups.port.DefaultGroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.data.di.groupsDataModule
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.di.groupsPresentationModule
import br.com.saqz.groups.presentation.list.GroupListViewModel
import br.com.saqz.groups.presentation.members.GroupMembersViewModel
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator as NetworkSessionInvalidator
import br.com.saqz.subscriptions.data.subscription.KtorSubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class SaqzKoinModulesTest {
    private val configFixturesModule = module {
        single { NetworkConfig(environment = NetworkEnvironment.Test, baseUrl = "https://api.invalid") }
        single {
            SaqzDraftStores(
                groupDrafts = FakeGroupDraftStore,
                gameDrafts = FakeGameDraftStore,
                monthlyChargeDrafts = FakeMonthlyChargeDraftStore,
                expenseDrafts = FakeExpenseDraftStore,
            )
        }
    }
    private val authFixtureModule = module {
        single<NativeAuthPort> { FakeAuthPort }
    }
    private val nativePortsFixtureModule = module {
        single {
            SaqzNativePorts(
                access = AccessRuntimeDependencies(
                    auth = FakeAuthPort,
                    links = FakeLinkPort,
                    localState = FakeLocalAccessStatePort,
                    share = FakeSharePort,
                    profilePhoto = FakeProfilePhotoPort,
                    profilePhotoSelection = FakeProfilePhotoPort,
                ),
                groups = GroupsRuntimeDependencies(
                    attendanceShare = FakeAttendanceSharePort,
                    photos = GroupPhotoRuntimeDependencies(
                        selection = FakeGroupPhotoSelectionPort,
                        encoder = FakeGroupPhotoEncoderPort,
                        previews = GroupPhotoPreviewPort { null },
                    ),
                    links = FakeGroupLinkPort,
                    state = FakeLocalGroupStatePort,
                    inviteUrlStore = FakeInviteUrlStorePort,
                    inviteShare = FakeInviteSharePort,
                    inviteClipboard = FakeInviteClipboardPort,
                ),
            )
        }
        single<NativeAuthPort> { get<SaqzNativePorts>().access.auth }
        single<NativeLinkPort> { get<SaqzNativePorts>().access.links }
        single<LocalAccessStatePort> { get<SaqzNativePorts>().access.localState }
        single<NativeSharePort> { get<SaqzNativePorts>().access.share }
        single<br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort> { get<SaqzNativePorts>().groups.attendanceShare }
        single<GroupPhotoSelectionPort> { get<SaqzNativePorts>().groups.photos.selection }
        single<GroupPhotoEncoderPort> { get<SaqzNativePorts>().groups.photos.encoder }
        single<NativeGroupLinkPort> { get<SaqzNativePorts>().groups.links }
        single<LocalGroupStatePort> { get<SaqzNativePorts>().groups.state }
        single<GroupSystemTimeZonePort> { DefaultGroupSystemTimeZonePort() }
    }

    @Test
    fun networkGraphResolvesWithSingletonClient() {
        val app = koinApplication {
            modules(
                configFixturesModule,
                authFixtureModule,
                coreNetworkModule,
                platformDraftsModule,
                accessDataModule,
                accessInvalidationModule,
                subscriptionsDataModule,
                groupsDataModule(),
            )
        }
        val koin = app.koin

        assertSame(koin.get<NetworkClient>(), koin.get<NetworkClient>())
        assertSame(koin.get<AuthenticatedNetworkClient>(), koin.get<AuthenticatedNetworkClient>())
        assertSame(koin.get<DelegatingSessionInvalidator>(), koin.get<AccessSessionInvalidator>())
        assertSame(koin.get<DelegatingSessionInvalidator>(), koin.get<NetworkSessionInvalidator>())
        assertIs<KtorSessionGateway>(koin.get<SessionGateway>())
        assertIs<KtorPasswordResetGateway>(koin.get<PasswordResetGateway>())
        assertIs<KtorSubscriptionGateway>(koin.get<SubscriptionGateway>())

        app.close()
    }

    @Test
    fun draftsModuleResolvesPlatformStores() {
        val app = koinApplication {
            modules(
                configFixturesModule,
                authFixtureModule,
                coreNetworkModule,
                platformDraftsModule,
                accessDataModule,
                accessInvalidationModule,
            )
        }
        val koin = app.koin

        assertSame(FakeGroupDraftStore, koin.get<GroupDraftStorePort>())
        assertSame(FakeGameDraftStore, koin.get<GameDraftStorePort>())
        assertSame(FakeMonthlyChargeDraftStore, koin.get<MonthlyChargeDraftStorePort>())
        assertSame(FakeExpenseDraftStore, koin.get<ExpenseDraftStorePort>())

        app.close()
    }

    @Test
    fun accessModuleResolvesMachinesAndWiresSessionInvalidator() {
        val app = koinApplication {
            modules(
                configFixturesModule,
                nativePortsFixtureModule,
                coreNetworkModule,
                platformDraftsModule,
                accessDataModule,
                accessInvalidationModule,
                accessPresentationModule,
                composePresentationModule,
            )
        }
        val koin = app.koin

        val sessionMachine = koin.get<SessionAccessStateMachine>()
        assertSame(sessionMachine, koin.get<SessionAccessStateMachine>())
        assertSame(sessionMachine, koin.get<DelegatingSessionInvalidator>().delegate)
        assertSame(koin.get<AuthenticationStateMachine>(), koin.get<AuthenticationStateMachine>())

        assertSame(FakeAuthPort, koin.get<NativeAuthPort>())
        assertSame(FakeLinkPort, koin.get<NativeLinkPort>())
        assertSame(FakeLocalAccessStatePort, koin.get<LocalAccessStatePort>())
        assertSame(FakeSharePort, koin.get<NativeSharePort>())

        // C1: the whole app graph is the session gate plus the access screens — the
        // orchestrator resolves as the runtime contract and the gate resolves on top of it.
        koin.get<AccessRuntimeContract>()
        koin.get<AccessViewModel>()
        koin.get<LoginViewModel>()
        // O handle entra pelo `parametersOf` como no `GroupSetupViewModel`: sem
        // `ViewModelStoreOwner` aqui, o `CreationExtras` do `NavEntry` não existe.
        koin.get<RegisterViewModel> { parametersOf(SavedStateHandle()) }
        // O 1g resolve **com o token que a rota carrega** (VUL-90), como as telas de
        // grupo resolvem com o `groupId`.
        koin.get<NewPasswordViewModel> { parametersOf("ticket-do-reset") }

        app.close()
    }

    /**
     * VUL-72: junto do grafo do app, as telas de grupo resolvem **com os argumentos que a
     * rota passa** — o `groupId` de `GroupsRoute.Details`/`Members` e o `GroupSetupMode` de
     * `Create`/`Edit` —, não com um mock qualquer.
     *
     * O `SavedStateHandle` do formulário é a única coisa que o teste fornece à mão: em app
     * ele vem do `CreationExtras` do `NavEntry`, pelo `AndroidParametersHolder` do Koin, e
     * nunca esteve no grafo. Sem `ViewModelStoreOwner` aqui, ele entra pelo `parametersOf`
     * — o que se verifica é a definição, não a origem do handle.
     *
     * A quinta, `GroupScheduleViewModel`, é `internal` ao módulo dela (o `State` carrega o
     * `SlotDraft`, também `internal`), então nenhum teste deste módulo consegue nomeá-la.
     * A definição permanece no mesmo módulo Koin e a construção direta fica coberta pelos
     * testes de apresentação; tornar a ViewModel pública só para este teste desfaria a
     * decisão de encapsulamento do VUL-71.
     */
    @Test
    fun groupsPresentationModuleResolvesWithTheRouteArguments() {
        val app = koinApplication {
            modules(
                configFixturesModule,
                nativePortsFixtureModule,
                coreNetworkModule,
                platformDraftsModule,
                accessDataModule,
                accessInvalidationModule,
                accessPresentationModule,
                composePresentationModule,
                groupsDataModule(),
                groupsPresentationModule(),
            )
        }
        val koin = app.koin

        koin.get<GroupListViewModel>()
        koin.get<GroupSetupViewModel> { parametersOf(GroupSetupMode.Create, SavedStateHandle()) }
        koin.get<GroupSetupViewModel> {
            parametersOf(GroupSetupMode.Edit("ceret"), SavedStateHandle())
        }
        koin.get<ResetCodeViewModel> { parametersOf("ana@exemplo.com") }
        koin.get<GroupDetailsViewModel> { parametersOf("ceret") }
        koin.get<GroupMembersViewModel> { parametersOf("ceret") }

        app.close()
    }

    @Test
    fun unauthorizedResponseInvalidatesResolvedSession() = runTest {
        val auth = RefreshingAuthPort()
        val unauthorizedNetwork = module {
            single<NativeAuthPort> { auth }
            single {
                NetworkClient(
                    MockEngine {
                        respond(
                            content = """{"status":401,"code":"AUTHENTICATION_REQUIRED"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    get(),
                )
            }
        }
        val app = koinApplication {
            allowOverride(true)
            modules(
                configFixturesModule,
                nativePortsFixtureModule,
                coreNetworkModule,
                platformDraftsModule,
                accessDataModule,
                accessInvalidationModule,
                accessPresentationModule,
                unauthorizedNetwork,
            )
        }
        val koin = app.koin
        // Resolver a maquina basta para registrar o invalidador; nao ha intent aqui de
        // proposito. Desde o VUL-84 o `Accept` ja dispara um bootstrap proprio, no escopo
        // de Dispatchers.Default do modulo, e ele correria em paralelo com a chamada
        // abaixo — dois 401 e duas saidas para contar.
        val session = koin.get<SessionAccessStateMachine>()

        // Any authenticated call is enough: the invalidator lives in the network client,
        // not in the gateway. Bootstrap is the one the reset keeps.
        koin.get<SessionGateway>().bootstrap()

        // The invalidator deliberately owns a callback chain on the app scope, not the
        // caller's test dispatcher; wait for that callback before asserting its effects.
        withTimeout(1_000) {
            while (auth.signOutCalls == 0) delay(1)
        }

        assertEquals(SessionAccessState.SignedOut, session.state.value)
        assertEquals(1, auth.signOutCalls)
        assertEquals(listOf(false, true), auth.forceRefreshCalls)
        app.close()
    }

    @Test
    fun nativePortsStayBoundForTheNativeIntegrationSurface() {
        // C1 orphans the group screens but keeps the native integration: every port the
        // launchers supply still resolves from the single SaqzNativePorts instance.
        val app = koinApplication {
            modules(
                configFixturesModule,
                nativePortsFixtureModule,
                coreNetworkModule,
                platformDraftsModule,
                accessDataModule,
                accessInvalidationModule,
            )
        }
        val koin = app.koin

        assertSame(FakeAttendanceSharePort, koin.get<br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort>())
        assertSame(FakeGroupPhotoSelectionPort, koin.get<GroupPhotoSelectionPort>())
        assertSame(FakeGroupPhotoEncoderPort, koin.get<GroupPhotoEncoderPort>())
        assertSame(FakeGroupLinkPort, koin.get<NativeGroupLinkPort>())
        assertSame(FakeLocalGroupStatePort, koin.get<LocalGroupStatePort>())

        app.close()
    }
}

private class RefreshingAuthPort : NativeAuthPort by FakeAuthPort {
    var signOutCalls = 0
    val forceRefreshCalls = mutableListOf<Boolean>()

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) {
        forceRefreshCalls += forceRefresh
        done.complete(TokenResult.Success(if (forceRefresh) "new-token" else "old-token"))
    }

    override fun signOut(done: ResultCallback) {
        signOutCalls += 1
        done.complete(OperationResult.Success)
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

    override fun updateDisplayName(name: String, done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
        done.complete(TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)
}

private object FakeLinkPort : NativeLinkPort {
    override fun start(listener: InviteCodeListener): Cancelable = object : Cancelable {
        override fun cancel() = Unit
    }
}

private object FakeLocalAccessStatePort : LocalAccessStatePort {
    override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
    override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
}

private object FakeSharePort : NativeSharePort {
    override fun share(text: String, done: ResultCallback) = done.complete(OperationResult.Success)
}

private object FakeProfilePhotoPort : br.com.saqz.access.domain.port.NativeProfilePhotoPort,
    br.com.saqz.profile.domain.ProfilePhotoSelectionPort {
    override fun chooseCamera(done: br.com.saqz.access.domain.port.ProfilePhotoCallback) = failed(done)
    override fun chooseLibrary(done: br.com.saqz.access.domain.port.ProfilePhotoCallback) = failed(done)

    override fun chooseCamera(done: br.com.saqz.profile.domain.ProfilePhotoSelectionCallback) =
        profileFailed(done)

    override fun chooseLibrary(done: br.com.saqz.profile.domain.ProfilePhotoSelectionCallback) =
        profileFailed(done)

    private fun failed(done: br.com.saqz.access.domain.port.ProfilePhotoCallback): Cancelable {
        done.complete(br.com.saqz.access.domain.port.ProfilePhotoResult.Failed)
        return object : Cancelable {
            override fun cancel() = Unit
        }
    }

    private fun profileFailed(done: br.com.saqz.profile.domain.ProfilePhotoSelectionCallback): br.com.saqz.profile.domain.ProfilePhotoSelectionCancelable {
        done.complete(br.com.saqz.profile.domain.ProfilePhotoSelectionResult.Failed)
        return object : br.com.saqz.profile.domain.ProfilePhotoSelectionCancelable {
            override fun cancel() = Unit
        }
    }
}

private object FakeAttendanceSharePort : br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort {
    override fun shareLink(url: String, done: (br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult) -> Unit) = done(br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult.Success)
    override fun shareImage(image: br.com.saqz.groups.domain.attendance.share.AttendanceShareImage, done: (br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult) -> Unit) = done(br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult.Success)
}

private object FakeGroupPhotoSelectionPort : GroupPhotoSelectionPort {
    override suspend fun chooseCamera() = GroupPhotoSelectionResult.Failed
    override suspend fun chooseLibrary() = GroupPhotoSelectionResult.Failed
    override fun cleanup(source: String) = Unit
}

private object FakeGroupPhotoEncoderPort : GroupPhotoEncoderPort {
    override suspend fun encode(source: String, crop: GroupPhotoCrop) = GroupPhotoEncodingResult.Failed
    override fun cancel(source: String) = Unit
}

private object FakeGroupLinkPort : NativeGroupLinkPort {
    override fun start(listener: GroupLinkEventListener): GroupCancelable = object : GroupCancelable {
        override fun cancel() = Unit
    }
}

private object FakeLocalGroupStatePort : LocalGroupStatePort {
    override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) =
        done.complete(GroupOperationResult.Success)
    override fun readPendingInvite(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writePendingInvite(value: String?, done: GroupResultCallback) =
        done.complete(GroupOperationResult.Success)
    override fun readPendingAttendanceLink(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) =
        done.complete(GroupOperationResult.Success)
}

private object FakeInviteUrlStorePort : GroupInviteUrlStorePort {
    override fun read(groupId: String, done: GroupInviteUrlReadCallback) =
        done.complete(GroupInviteUrlReadResult.Success(null))

    override fun write(groupId: String, cache: br.com.saqz.groups.port.GroupInviteUrlCache?, done: GroupInviteUrlWriteCallback) =
        done.complete(GroupInviteUrlWriteResult.Success)
}

private object FakeInviteSharePort : NativeInviteSharePort {
    override fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)

    override fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)

    override fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)
}

private object FakeInviteClipboardPort : NativeInviteClipboardPort {
    override fun copyText(text: String, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)
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
