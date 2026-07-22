package br.com.saqz.composeapp.di

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
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.data.session.KtorSessionGateway
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.domain.session.SessionInvalidator as AccessSessionInvalidator
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.groups.data.group.KtorGroupGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.data.RolesInvitesApi
import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.groups.data.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
import br.com.saqz.groups.data.attendance.AttendanceApi
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.data.game.GameApi
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.presentation.DeferredInviteStateMachine
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkStateMachine
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
import br.com.saqz.network.SessionInvalidator as NetworkSessionInvalidator
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
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
                auth = FakeAuthPort,
                links = FakeLinkPort,
                localAccessState = FakeLocalAccessStatePort,
                share = FakeSharePort,
                attendanceShare = FakeAttendanceSharePort,
                groupPhotoSelection = FakeGroupPhotoSelectionPort,
                groupPhotoEncoder = FakeGroupPhotoEncoderPort,
                groupPhotoPreviews = GroupPhotoPreviewPort { null },
                groupLinks = FakeGroupLinkPort,
                localGroupState = FakeLocalGroupStatePort,
            )
        }
        single<NativeAuthPort> { get<SaqzNativePorts>().auth }
        single<NativeLinkPort> { get<SaqzNativePorts>().links }
        single<LocalAccessStatePort> { get<SaqzNativePorts>().localAccessState }
        single<NativeSharePort> { get<SaqzNativePorts>().share }
        single<br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort> { get<SaqzNativePorts>().attendanceShare }
        single<GroupPhotoSelectionPort> { get<SaqzNativePorts>().groupPhotoSelection }
        single<GroupPhotoEncoderPort> { get<SaqzNativePorts>().groupPhotoEncoder }
        single<NativeGroupLinkPort> { get<SaqzNativePorts>().groupLinks }
        single<LocalGroupStatePort> { get<SaqzNativePorts>().localGroupState }
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
            )
        }
        val koin = app.koin

        assertSame(koin.get<NetworkClient>(), koin.get<NetworkClient>())
        assertSame(koin.get<AuthenticatedNetworkClient>(), koin.get<AuthenticatedNetworkClient>())
        assertSame(koin.get<DelegatingSessionInvalidator>(), koin.get<AccessSessionInvalidator>())
        assertSame(koin.get<DelegatingSessionInvalidator>(), koin.get<NetworkSessionInvalidator>())
        assertIs<KtorSessionGateway>(koin.get<SessionGateway>())

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
                groupsDataModule,
                groupsPresentationModule,
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

        app.close()
    }

    @Test
    fun gameDetailUnauthorizedResponseInvalidatesResolvedSession() = runTest {
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
                groupsDataModule,
                unauthorizedNetwork,
            )
        }
        val koin = app.koin
        val session = koin.get<SessionAccessStateMachine>()
        session.onIntent(
            SessionIntent.Accept(
                AuthTransition.VerificationRequired(
                    NativeUser(subject = "user-1", email = "person@example.com", displayName = "Person", emailVerified = false),
                ),
            ),
        )
        assertIs<SessionAccessState.AwaitingVerification>(session.state.value)

        koin.get<GameGateway>().read("group-1", "game-1")

        assertEquals(SessionAccessState.SignedOut, session.state.value)
        assertEquals(1, auth.signOutCalls)
        assertEquals(listOf(false, true), auth.forceRefreshCalls)
        app.close()
    }

    @Test
    fun groupsModuleResolvesGatewaysAndMachines() {
        val app = koinApplication {
            modules(
                configFixturesModule,
                nativePortsFixtureModule,
                coreNetworkModule,
                platformDraftsModule,
                accessDataModule,
                accessInvalidationModule,
                accessPresentationModule,
                groupsDataModule,
                groupsPresentationModule,
                composePresentationModule,
            )
        }
        val koin = app.koin

        val groupApi = koin.get<KtorGroupGateway>()
        assertSame(groupApi, koin.get<GroupGateway>())
        assertSame(groupApi, koin.get<GroupProfileGateway>())
        assertSame(koin.get<RolesInvitesApi>(), koin.get<RolesInvitesGateway>())
        koin.get<GroupPhotoGateway>()
        koin.get<AttendanceSharingGateway>()
        val gameApi = koin.get<GameApi>()
        assertSame(gameApi, koin.get<GameGateway>())
        val attendanceApi = koin.get<AttendanceApi>()
        assertSame(attendanceApi, koin.get<AttendanceGateway>())

        koin.get<GroupSelectionStateMachine>()
        koin.get<GroupAdministrationStateMachine>()
        koin.get<DeferredInviteStateMachine>()
        koin.get<DeferredAttendanceLinkStateMachine>()
        koin.get<AttendanceDestinationStore>()

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

    override fun sendPasswordReset(email: String, done: ResultCallback) =
        done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

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

private object FakeAttendanceSharePort : br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort {
    override fun shareLink(url: br.com.saqz.groups.domain.attendance.share.AttendanceLinkUrl, done: (br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult) -> Unit) = done(br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult.Success)
    override fun shareImage(image: br.com.saqz.groups.domain.attendance.share.AttendanceShareImage, done: (br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult) -> Unit) = done(br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult.Success)
}

private object FakeGroupPhotoSelectionPort : GroupPhotoSelectionPort {
    override suspend fun chooseCamera() = GroupPhotoSelectionResult.Failed
    override suspend fun chooseLibrary() = GroupPhotoSelectionResult.Failed
    override fun cleanup(source: GroupPhotoSourceHandle) = Unit
}

private object FakeGroupPhotoEncoderPort : GroupPhotoEncoderPort {
    override suspend fun encode(source: GroupPhotoSourceHandle, crop: GroupPhotoCrop) = GroupPhotoEncodingResult.Failed
    override fun cancel(source: GroupPhotoSourceHandle) = Unit
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
