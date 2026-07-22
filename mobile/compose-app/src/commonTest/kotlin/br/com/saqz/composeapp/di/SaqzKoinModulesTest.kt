package br.com.saqz.composeapp.di

import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.groups.data.GroupApi
import br.com.saqz.groups.data.GroupGateway
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.RolesInvitesApi
import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.groups.data.attendance.AttendanceGateway
import br.com.saqz.groups.data.attendance.share.AttendanceShareGateway
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.port.GroupAttendanceSharePort
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupPhotoSelectionResult
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.presentation.DeferredInviteStateMachine
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel
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
import br.com.saqz.network.SessionApi
import br.com.saqz.network.SessionGateway
import br.com.saqz.network.SessionInvalidator
import kotlin.test.Test
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
        single<GroupAttendanceSharePort> { get<SaqzNativePorts>().attendanceShare }
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
        assertSame(koin.get<DelegatingSessionInvalidator>(), koin.get<SessionInvalidator>())
        assertIs<SessionApi>(koin.get<SessionGateway>())

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

        val groupApi = koin.get<GroupApi>()
        assertSame(groupApi, koin.get<GroupGateway>())
        assertSame(groupApi, koin.get<GroupProfileGateway>())
        assertSame(koin.get<RolesInvitesApi>(), koin.get<RolesInvitesGateway>())
        koin.get<GroupPhotoGateway>()
        koin.get<AttendanceShareGateway>()
        koin.get<GameGateway>()
        koin.get<AttendanceGateway>()

        koin.get<GroupSelectionStateMachine>()
        koin.get<GroupAdministrationStateMachine>()
        koin.get<DeferredInviteStateMachine>()
        koin.get<DeferredAttendanceLinkStateMachine>()
        koin.get<AttendanceDestinationStore>()

        assertSame(FakeAttendanceSharePort, koin.get<GroupAttendanceSharePort>())
        assertSame(FakeGroupPhotoSelectionPort, koin.get<GroupPhotoSelectionPort>())
        assertSame(FakeGroupPhotoEncoderPort, koin.get<GroupPhotoEncoderPort>())
        assertSame(FakeGroupLinkPort, koin.get<NativeGroupLinkPort>())
        assertSame(FakeLocalGroupStatePort, koin.get<LocalGroupStatePort>())

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

private object FakeAttendanceSharePort : GroupAttendanceSharePort {
    override fun shareLink(url: String, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    override fun shareImage(image: AttendanceShareImageModel, done: GroupResultCallback) =
        done.complete(GroupOperationResult.Success)
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
