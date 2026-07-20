package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.composeapp.SaqzAppDependencies
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupPhotoReadResult
import br.com.saqz.groups.data.GroupPhotoReceipt
import br.com.saqz.groups.data.GroupPhotoUploadCommand
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.ui.InviteToolState
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupCreateCommand
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupDraftResource
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.model.GroupUpdateCommand
import br.com.saqz.groups.presentation.setup.GroupCommandKeyFactory
import br.com.saqz.groups.presentation.setup.GroupSetupInput
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.port.EncodedGroupPhoto
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupPhotoCachePort
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import br.com.saqz.groups.port.GroupPhotoMediaType
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelection
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupPhotoSelectionResult
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import br.com.saqz.groups.ui.photo.GroupPhotoTags
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.network.SessionDto
import br.com.saqz.network.SessionMembershipDto
import br.com.saqz.network.SessionUserDto
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AuthenticatedAccessRootTest {
    @Test
    fun `starting renders one loading destination`() = runComposeUiTest {
        root(snapshot(authObserved = false))

        onNodeWithContentDescription("Carregando").assertExists()
        assertEquals(1, onAllNodes(hasTestTag(AccessRootTag)).fetchSemanticsNodes().size)
    }

    @Test
    fun `persisted session starts bootstrap without flashing login`() = runComposeUiTest {
        root(snapshot(session = SessionAccessState.Bootstrapping))

        onNodeWithTag("bootstrap-loading").assertExists()
        onNodeWithText("Entrar").assertDoesNotExist()
    }

    @Test
    fun `absence of session renders login without protected shell`() = runComposeUiTest {
        root(snapshot())

        onNodeWithTag("login-submit").assertExists()
        onNodeWithText("Current Group").assertDoesNotExist()
        onNodeWithText("Componentes").assertDoesNotExist()
    }

    @Test
    fun `registration back returns through one callback`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(
            snapshot(authentication = AuthenticationState(screen = AuthScreen.REGISTRATION)),
            intents::add,
        )

        onNodeWithText("Voltar para entrar").performClick()

        assertEquals(
            listOf<AccessIntent>(AccessIntent.Authentication(AuthenticationIntent.ShowLogin)),
            intents,
        )
    }

    @Test
    fun `password reset back returns through one callback`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(
            snapshot(authentication = AuthenticationState(screen = AuthScreen.PASSWORD_RESET)),
            intents::add,
        )

        onNodeWithText("Voltar para entrar").performClick()

        assertEquals(
            listOf<AccessIntent>(AccessIntent.Authentication(AuthenticationIntent.ShowLogin)),
            intents,
        )
    }

    @Test
    fun `password reset system back maps to login`() {
        assertEquals(
            AccessIntent.Authentication(AuthenticationIntent.ShowLogin),
            AccessDestination.PASSWORD_RESET.systemBackIntent(),
        )
    }

    @Test
    fun `account setup system back maps to login`() {
        assertEquals(
            AccessIntent.Authentication(AuthenticationIntent.ShowLogin),
            AccessDestination.REGISTRATION.systemBackIntent(),
        )
    }

    @Test
    fun `login does not consume system back`() {
        assertNull(AccessDestination.LOGIN.systemBackIntent())
    }

    @Test
    fun `only signed out auth destinations ignore safe area`() {
        assertFalse(AccessDestination.LOGIN.respectsSafeArea())
        assertFalse(AccessDestination.REGISTRATION.respectsSafeArea())
        assertFalse(AccessDestination.PASSWORD_RESET.respectsSafeArea())

        assertTrue(AccessDestination.GROUP_CONTEXT.respectsSafeArea())
        assertTrue(AccessDestination.CREATE_GROUP.respectsSafeArea())
        assertTrue(AccessDestination.GROUP_ONBOARDING.respectsSafeArea())
        assertTrue(AccessDestination.BOOTSTRAP.respectsSafeArea())
    }

    @Test
    fun `zero memberships offers only group creation`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(ready(GroupSelectionState.NoGroup), intents::add)

        onNodeWithText("Voce ainda nao participa de um grupo").assertExists()
        onNodeWithText("Criar grupo").performClick()

        assertEquals(listOf<AccessIntent>(AccessIntent.OpenCreateGroup), intents)
    }

    @Test
    fun `multiple memberships show every group and role`() = runComposeUiTest {
        root(ready(selector))

        onNodeWithText("Alpha").assertExists()
        onNodeWithText("OWNER").assertExists()
        onNodeWithText("Beta").assertExists()
        onNodeWithText("ATHLETE").assertExists()
    }

    @Test
    fun `selector keeps create group available to verified user`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(ready(selector), intents::add)

        onNodeWithText("Criar grupo").performClick()

        assertEquals(listOf<AccessIntent>(AccessIntent.OpenCreateGroup), intents)
    }

    @Test
    fun `authenticated create destination renders complete Groups setup instead of legacy form`() = runComposeUiTest {
        rootWithSetup(
            ready(GroupSelectionState.NoGroup).copy(page = AccessPage.CREATE_GROUP),
            GroupSetupState(
                mode = GroupSetupMode.CREATE,
                form = GroupSetupForm(),
                commandKey = "create-command",
                timeZone = GroupTimeZone.parse("America/Sao_Paulo").let {
                    (it as GroupTimeZone.ParseResult.Valid).value
                },
            ),
        )

        onNodeWithText("Perfil do grupo").assertExists()
        onNodeWithText("Modalidade").assertExists()
        onNodeWithText("Composição").assertExists()
        onNodeWithText("Padrões para novos jogos").assertExists()
        onNodeWithText("Padrões financeiros").assertExists()
        onNodeWithText("Timezone").assertDoesNotExist()
    }

    @Test
    fun `production create route reaches both photo sources and consumes upload effect`() = runComposeUiTest {
        val fixture = productionPhotoRouteFixture()
        try {
            setContent {
                SaqzTheme {
                    AuthenticatedAccessRoute(
                        dependencies = SaqzAppDependencies.Unconfigured,
                        accessViewModelOverride = fixture.access,
                        groupSetupViewModelOverride = fixture.setup,
                        groupPhotos = fixture.dependencies,
                    )
                }
            }
            waitForIdle()

            onNodeWithTag(GroupPhotoTags.Library).performClick()
            waitUntil(timeoutMillis = 5_000) { fixture.selections.libraryCalls == 1 }
            onNodeWithTag(GroupPhotoTags.Camera).performClick()
            waitUntil(timeoutMillis = 5_000) { fixture.selections.cameraCalls == 1 }

            fixture.setup.onIntent(GroupSetupIntent.SetPhotoPending(true))
            fixture.setup.onIntent(GroupSetupIntent.Submit)
            waitUntil(timeoutMillis = 5_000) { fixture.photos.uploads.isNotEmpty() }

            val upload = fixture.photos.uploads.single()
            assertEquals("created-group", upload.groupId)
            assertEquals("\"1\"", upload.groupEtag)
            assertEquals(listOf<Byte>(1, 2, 3), upload.photo.source.read().toList())
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `one selected membership renders current group and role`() = runComposeUiTest {
        root(active(ownerAdministration))

        onNodeWithText("Current Group").assertExists()
        onNodeWithText("OWNER").assertExists()
    }

    @Test
    fun `group switch routes through one action`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(active(ownerAdministration), intents::add)

        onNodeWithTag("context-switch").performClick()

        assertEquals(listOf<AccessIntent>(AccessIntent.SwitchGroup), intents)
    }

    @Test
    fun `switch loading removes previous protected content before new load`() = runComposeUiTest {
        root(ready(GroupSelectionState.Loading("beta")))

        onNodeWithText("Current Group").assertDoesNotExist()
        onNodeWithTag("group-loading").assertExists()
    }

    @Test
    fun `discarded stale selection renders current selector only`() = runComposeUiTest {
        root(ready(selector))

        onNodeWithText("Removed Group").assertDoesNotExist()
        onNodeWithText("Alpha").assertExists()
        onNodeWithText("Beta").assertExists()
    }

    @Test
    fun `owner context exposes settings roles and invite`() = runComposeUiTest {
        root(active(ownerAdministration))

        onNodeWithTag("context-settings").assertExists()
        onNodeWithTag("context-roles").assertExists()
        onNodeWithTag("context-invite").assertExists()
    }

    @Test
    fun `role refresh to athlete removes every privileged action`() = runComposeUiTest {
        root(active(athleteAdministration))

        onNodeWithTag("context-settings").assertDoesNotExist()
        onNodeWithTag("context-roles").assertDoesNotExist()
        onNodeWithTag("context-invite").assertDoesNotExist()
    }

    @Test
    fun `logout replaces protected history with login only`() {
        val stack = AccessDestinationStack(AccessDestination.GROUP_CONTEXT)

        stack.replace(AccessDestination.SETTINGS)
        stack.replace(AccessDestination.LOGIN)

        assertEquals(listOf(AccessDestination.LOGIN), stack.entries)
    }

    @Test
    fun `reselection and repeated auth callbacks never duplicate destinations`() {
        val stack = AccessDestinationStack(AccessDestination.LOGIN)

        repeat(3) {
            stack.replace(AccessDestination.REGISTRATION)
            stack.replace(AccessDestination.LOGIN)
            stack.replace(AccessDestination.LOGIN)
        }

        assertEquals(listOf(AccessDestination.LOGIN), stack.entries)
    }

    @Test
    fun `access resources resolve through umbrella packaging`() = runComposeUiTest {
        root(snapshot())

        onNodeWithText("Organize seu grupo.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `maximum text scale keeps login actions reachable`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                Box(Modifier) {
                    SaqzTheme { AuthenticatedAccessRoot(snapshot()) {} }
                }
            }
        }

        onNodeWithText("Entrar com Google").performScrollTo().assertIsDisplayed()
        onNodeWithText("Criar conta").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `share effect invokes native adapter once and returns through access intent`() {
        val share = RecordingSharePort(OperationResult.Success)
        val intents = mutableListOf<AccessIntent>()

        handleAccessEffect(AccessUiEffect.RequestShare("https://example.test/invite"), share, intents::add)

        assertEquals(listOf("https://example.test/invite"), share.values)
        assertEquals(listOf<AccessIntent>(AccessIntent.ShareFinished(successful = true)), intents)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.root(
        state: AccessRootSnapshot,
        onIntent: (AccessIntent) -> Unit = {},
    ) = setContent { SaqzTheme { AuthenticatedAccessRoot(state, onIntent = onIntent) } }

    private fun androidx.compose.ui.test.ComposeUiTest.rootWithSetup(
        state: AccessRootSnapshot,
        groupSetupState: GroupSetupState,
        onGroupPhotoIntent: (GroupPhotoIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            AuthenticatedAccessRoot(
                state,
                groupSetupState = groupSetupState,
                onGroupPhotoIntent = onGroupPhotoIntent,
                onIntent = {},
            )
        }
    }

    private class RecordingSharePort(private val result: OperationResult) : NativeSharePort {
        val values = mutableListOf<String>()

        override fun share(text: String, done: ResultCallback) {
            values += text
            done.complete(result)
        }
    }

    private fun productionPhotoRouteFixture(): ProductionPhotoRouteFixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val profiles = RecordingProfileGateway()
        val photos = RecordingPhotoGateway()
        val runtime = PhotoRouteRuntime(profiles, photos)
        val access = AccessViewModel(runtime, scope).also {
            it.onIntent(AccessIntent.OpenCreateGroup)
        }
        val setup = GroupSetupViewModel(
            input = GroupSetupInput(),
            gateway = profiles,
            timeZones = GroupSystemTimeZonePort { done ->
                done(GroupSystemTimeZoneResult.Available(groupTimeZone()))
            },
            drafts = ImmediateDraftStore(validSetupDraft()),
            commandKeys = GroupCommandKeyFactory { "create-command" },
            testScope = scope,
        )
        val selections = RecordingPhotoSelectionPort()
        val dependencies = GroupPhotoRuntimeDependencies(
            selection = selections,
            encoder = ImmediatePhotoEncoder,
            cache = NoOpPhotoCache,
            previews = GroupPhotoPreviewPort { null },
        )
        return ProductionPhotoRouteFixture(scope, access, setup, selections, photos, dependencies)
    }

    private data class ProductionPhotoRouteFixture(
        val scope: CoroutineScope,
        val access: AccessViewModel,
        val setup: GroupSetupViewModel,
        val selections: RecordingPhotoSelectionPort,
        val photos: RecordingPhotoGateway,
        val dependencies: GroupPhotoRuntimeDependencies,
    )

    private class RecordingPhotoSelectionPort : GroupPhotoSelectionPort {
        var cameraCalls = 0
        var libraryCalls = 0

        override suspend fun chooseCamera(): GroupPhotoSelectionResult {
            cameraCalls += 1
            return GroupPhotoSelectionResult.Selected(
                GroupPhotoSelection(
                    source = GroupPhotoSourceHandle("camera-source"),
                    preview = GroupPhotoPreviewHandle("camera-preview"),
                    width = 640,
                    height = 480,
                ),
            )
        }

        override suspend fun chooseLibrary(): GroupPhotoSelectionResult {
            libraryCalls += 1
            return GroupPhotoSelectionResult.Cancelled
        }

        override fun cleanup(source: GroupPhotoSourceHandle) = Unit
    }

    private object ImmediatePhotoEncoder : GroupPhotoEncoderPort {
        override suspend fun encode(
            source: GroupPhotoSourceHandle,
            crop: GroupPhotoCrop,
        ): GroupPhotoEncodingResult = GroupPhotoEncodingResult.Encoded(
            EncodedGroupPhoto(
                mediaType = GroupPhotoMediaType.JPEG,
                contentLength = 3,
                source = { byteArrayOf(1, 2, 3) },
            ),
        )

        override fun cancel(source: GroupPhotoSourceHandle) = Unit
    }

    private object NoOpPhotoCache : GroupPhotoCachePort {
        override fun evict(groupId: String) = Unit
        override fun clearAll() = Unit
    }

    private class RecordingPhotoGateway : GroupPhotoGateway {
        val uploads = mutableListOf<GroupPhotoUploadCommand>()

        override suspend fun upload(command: GroupPhotoUploadCommand): NetworkResult<GroupPhotoReceipt> {
            uploads += command
            return NetworkResult.Success(GroupPhotoReceipt("\"2\""))
        }

        override suspend fun read(groupId: String, etag: String?): NetworkResult<GroupPhotoReadResult> =
            NetworkResult.Success(GroupPhotoReadResult.NotModified)

        override suspend fun remove(groupId: String, groupEtag: String): NetworkResult<GroupPhotoReceipt> =
            NetworkResult.Success(GroupPhotoReceipt("\"2\""))
    }

    private class RecordingProfileGateway : GroupProfileGateway {
        override suspend fun createProfile(command: GroupCreateCommand): NetworkResult<GroupDto> =
            NetworkResult.Success(
                GroupDto(
                    id = "created-group",
                    name = command.form.name,
                    timeZone = command.timeZone.id,
                    version = 1,
                    role = GroupRoleDto.OWNER,
                ),
            )

        override suspend fun readProfile(groupId: String): NetworkResult<VersionedGroupDto> =
            error("not used")

        override suspend fun updateProfile(command: GroupUpdateCommand): NetworkResult<VersionedGroupDto> =
            error("not used")
    }

    private class ImmediateDraftStore(private val draft: GroupSetupDraft) : GroupDraftStorePort {
        override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) =
            done(GroupDraftReadResult.Success(draft))

        override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) =
            done(GroupDraftWriteResult.Success)

        override fun clear(
            key: GroupDraftKey,
            commandKey: String,
            done: (GroupDraftWriteResult) -> Unit,
        ) = done(GroupDraftWriteResult.Success)
    }

    private class PhotoRouteRuntime(
        override val groupProfileGateway: GroupProfileGateway,
        override val groupPhotoGateway: GroupPhotoGateway,
    ) : AccessRuntimeContract {
        override val authObservedState = MutableStateFlow(true)
        override val authenticationState = MutableStateFlow(AuthenticationState())
        override val sessionState = MutableStateFlow<SessionAccessState>(SessionAccessState.Ready(session))
        override val selectionState = MutableStateFlow<GroupSelectionState>(GroupSelectionState.NoGroup)
        override val administrationState = MutableStateFlow(GroupAdministrationState())
        override val inviteToolState = MutableStateFlow(InviteToolState())

        override fun onIntent(intent: AccessRuntimeIntent) = Unit
        override fun newRequestId(): String = "route-request"
    }

    private fun validSetupDraft() = GroupSetupDraft(
        resource = GroupDraftResource.CREATE_GROUP,
        groupId = null,
        groupVersion = null,
        etag = null,
        commandKey = "create-command",
        form = GroupSetupForm(
            name = "Training Club",
            modality = GroupModality.COURT_VOLLEYBALL,
            composition = GroupComposition.MIXED,
        ),
    )

    private fun groupTimeZone(): GroupTimeZone = GroupTimeZone.parse("America/Sao_Paulo").let {
        (it as GroupTimeZone.ParseResult.Valid).value
    }

    private fun snapshot(
        authObserved: Boolean = true,
        authentication: AuthenticationState = AuthenticationState(),
        session: SessionAccessState = SessionAccessState.SignedOut,
    ) = AccessRootSnapshot(
        authObserved = authObserved,
        authentication = authentication,
        session = session,
        selection = GroupSelectionState.NoGroup,
        administration = GroupAdministrationState(),
    )

    private fun ready(selection: GroupSelectionState) = snapshot(session = SessionAccessState.Ready(session)).copy(
        selection = selection,
    )

    private fun active(administration: GroupAdministrationState) = ready(GroupSelectionState.Selected(group)).copy(
        administration = administration,
    )

    private companion object {
        val session = SessionDto(
            user = SessionUserDto("user", "user@example.test", "User"),
            memberships = listOf(
                SessionMembershipDto("alpha", "Alpha", "OWNER"),
                SessionMembershipDto("beta", "Beta", "ATHLETE"),
            ),
        )
        val selector = GroupSelectionState.Selector(session.memberships)
        val group = VersionedGroupDto(
            GroupDto("current", "Current Group", "America/Sao_Paulo", 1, GroupRoleDto.OWNER),
            "\"1\"",
        )
        val ownerAdministration = GroupAdministrationState(
            group = group,
            actions = GroupActions(true, true, true),
        )
        val athleteAdministration = GroupAdministrationState(
            group = group.copy(group = group.group.copy(role = GroupRoleDto.ATHLETE)),
            actions = GroupActions(false, false, false),
        )
    }
}
