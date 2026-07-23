package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
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
import br.com.saqz.composeapp.startTestSaqzKoin
import br.com.saqz.composeapp.stopTestSaqzKoin
import br.com.saqz.designsystem.component.SaqzTopBarTitleTag
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationAccess
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.InviteToolState
import br.com.saqz.groups.domain.group.GroupComposition as DomainGroupComposition
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupDraftResource
import br.com.saqz.groups.domain.group.GroupModality as DomainGroupModality
import br.com.saqz.groups.domain.group.GroupSetupForm
import br.com.saqz.groups.model.GroupSetupForm as DraftGroupSetupForm
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.domain.group.GroupTimeZone as DomainGroupTimeZone
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.presentation.setup.GroupCommandKeyFactory
import br.com.saqz.groups.presentation.setup.GroupSetupInput
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import br.com.saqz.groups.ui.photo.GroupPhotoTags
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.designsystem.component.SaqzTopBarTag
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessMembership
import br.com.saqz.access.domain.session.AccessMembershipRole
import br.com.saqz.domain.GroupId
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.domain.SaqzResult
import br.com.saqz.access.domain.session.SessionInvalidator
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
        onNodeWithTag(SaqzTopBarTag).assertDoesNotExist()
        onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()
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
                timeZone = DomainGroupTimeZone("America/Sao_Paulo"),
            ),
        )

        onNodeWithText("Identidade do grupo").assertExists()
        onNodeWithText("Modalidade").assertExists()
        onNodeWithText("Composição").assertExists()
        onNodeWithText("Rotina dos jogos").assertExists()
        onNodeWithText("Cobrança").assertExists()
        onNodeWithText("Timezone").assertDoesNotExist()
    }

    @Test
    fun `production create route reaches both photo sources and consumes upload effect`() = runComposeUiTest {
        val dependencies = startTestSaqzKoin()
        val fixture = productionPhotoRouteFixture()
        try {
            setContent {
                SaqzTheme {
                    AuthenticatedAccessRoute(
                        dependencies = dependencies,
                        accessViewModelOverride = fixture.access,
                        groupSetupViewModelOverride = fixture.setup,
                        groupPhotos = fixture.dependencies,
                    )
                }
            }
            waitForIdle()

            onNodeWithTag(GroupPhotoTags.Library).assertDoesNotExist()
            onNodeWithTag(GroupPhotoTags.Camera).assertDoesNotExist()
            onNodeWithTag(GroupPhotoTags.Add).performClick()
            onNodeWithTag(GroupPhotoTags.Library).performClick()
            waitUntil(timeoutMillis = 5_000) { fixture.selections.libraryCalls == 1 }
            onNodeWithTag(GroupPhotoTags.Add).performClick()
            onNodeWithTag(GroupPhotoTags.Camera).performClick()
            waitUntil(timeoutMillis = 5_000) { fixture.selections.cameraCalls == 1 }

            fixture.setup.onIntent(GroupSetupIntent.SetPhotoPending(true))
            fixture.setup.onIntent(GroupSetupIntent.Submit)
            waitUntil(timeoutMillis = 5_000) { fixture.photos.uploads.isNotEmpty() }

            val upload = fixture.photos.uploads.single()
            assertEquals(GroupId("created-group"), upload.groupId)
            assertEquals(GroupPhotoVersionToken("\"1\""), upload.groupVersion)
            assertEquals(listOf<Byte>(1, 2, 3), upload.photo.source.read().toList())
            assertEquals(emptyList(), fixture.photos.reads)
        } finally {
            fixture.scope.cancel()
            stopTestSaqzKoin()
        }
    }

    @Test
    fun `production route reads photo only after selected group context is reconciled`() = runComposeUiTest {
        val dependencies = startTestSaqzKoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val photos = RecordingPhotoGateway()
        val selectedSession = AccessSession(
            user = session.user,
            memberships = listOf(AccessMembership(GroupId("current"), "Current Group", AccessMembershipRole("OWNER"))),
        )
        val runtime = PhotoRouteRuntime(
            groupProfileGateway = RecordingProfileGateway(),
            groupPhotoGateway = photos,
            initialSession = SessionAccessState.Ready(selectedSession),
            initialSelection = GroupSelectionState.Selected(group),
            initialAdministration = ownerAdministration,
        )
        val access = AccessViewModel { runtime }
        try {
            setContent {
                SaqzTheme {
                    AuthenticatedAccessRoute(
                        dependencies = dependencies,
                        accessViewModelOverride = access,
                    )
                }
            }

            waitUntil(timeoutMillis = 5_000) { photos.reads.isNotEmpty() }

            assertEquals(
                listOf(Pair<GroupId, GroupPhotoVersionToken?>(GroupId("current"), null)),
                photos.reads,
            )
        } finally {
            scope.cancel()
            stopTestSaqzKoin()
        }
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
    fun `authenticated product starts on home and switches to the group selector`() = runComposeUiTest {
        val groupsIntents = mutableListOf<GroupsNavigationIntent>()
        root(
            state = ready(selector),
            groupsNavigation = GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = session.toSafeGroupSelectionMemberships(),
            ),
            onGroupsIntent = groupsIntents::add,
            initiallyShowAppHome = true,
        )

        onNodeWithText("Home screen").assertIsDisplayed()
        onNodeWithTag("saqz-bottom-nav-item-0").assertIsSelected()
        onNodeWithText("Alpha").assertDoesNotExist()

        onNodeWithTag("saqz-bottom-nav-item-1").performClick()

        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenGroups), groupsIntents)
        onNodeWithText("Home screen").assertDoesNotExist()
        onNodeWithText("Alpha").assertExists()
        onNodeWithTag("saqz-bottom-nav-item-1").assertIsSelected()

        onNodeWithTag("saqz-bottom-nav-item-0").performClick()

        onNodeWithText("Home screen").assertIsDisplayed()
        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenGroups), groupsIntents)
    }

    @Test
    fun `groups from authenticated home preserves no membership onboarding`() = runComposeUiTest {
        root(
            state = snapshot(
                session = SessionAccessState.Ready(session.copy(memberships = emptyList())),
            ),
            groupsNavigation = GroupsNavigationState(destination = GroupsDestination.SETUP),
            initiallyShowAppHome = true,
        )

        onNodeWithText("Home screen").assertIsDisplayed()

        onNodeWithTag("saqz-bottom-nav-item-1").performClick()

        onNodeWithText("Home screen").assertDoesNotExist()
        onNodeWithText("Criar grupo").assertExists()
    }

    @Test
    fun `deferred game detail replaces authenticated home`() = runComposeUiTest {
        val navigation = mutableStateOf(
            GroupsNavigationState(
                destination = GroupsDestination.HOME,
                groupId = group.group.id.value,
            ),
        )
        setContent {
            SaqzTheme {
                AuthenticatedAccessRoot(
                    state = active(ownerAdministration),
                    groupsNavigation = navigation.value,
                    initiallyShowAppHome = true,
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Home screen").assertIsDisplayed()

        runOnIdle {
            navigation.value = navigation.value.copy(
                destination = GroupsDestination.GAME_DETAIL,
                gameId = "game-1",
            )
        }

        onNodeWithText("Home screen").assertDoesNotExist()
        onNodeWithTag(SaqzTopBarTitleTag).assertTextEquals("Detalhes do jogo")
    }

    @Test
    fun `groups list selection routes the exact group through navigation and access`() = runComposeUiTest {
        val accessIntents = mutableListOf<AccessIntent>()
        val groupsIntents = mutableListOf<GroupsNavigationIntent>()
        root(
            state = ready(selector),
            onIntent = accessIntents::add,
            groupsNavigation = GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = session.toSafeGroupSelectionMemberships(),
            ),
            onGroupsIntent = groupsIntents::add,
        )

        onNodeWithText("Beta").performClick()

        assertEquals(
            listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenGroup("beta")),
            groupsIntents,
        )
        assertEquals(
            listOf<AccessIntent>(AccessIntent.Selection(GroupSelectionIntent.Select("beta"))),
            accessIntents,
        )
    }

    @Test
    fun `groups list create action opens the existing creation flow`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(
            state = ready(selector),
            onIntent = intents::add,
            groupsNavigation = GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = session.toSafeGroupSelectionMemberships(),
            ),
        )

        onNodeWithText("Criar grupo").performClick()

        assertEquals(listOf<AccessIntent>(AccessIntent.OpenCreateGroup), intents)
    }

    @Test
    fun `group load retry uses the existing selection retry intent`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(
            state = ready(GroupSelectionState.LoadError("beta")),
            onIntent = intents::add,
            groupsNavigation = GroupsNavigationState(
                destination = GroupsDestination.LOAD_ERROR,
                memberships = session.toSafeGroupSelectionMemberships(),
                requestedGroupId = "beta",
            ),
        )

        onNodeWithText("Tentar novamente").performClick()

        assertEquals(
            listOf<AccessIntent>(AccessIntent.Selection(GroupSelectionIntent.Retry)),
            intents,
        )
    }

    @Test
    fun `group detail connects settings invite and logout to existing access intents`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(
            state = active(ownerAdministration),
            onIntent = intents::add,
            groupsNavigation = GroupsNavigationState(
                destination = GroupsDestination.HOME,
                groupId = group.group.id.value,
                access = GroupsNavigationAccess(
                    showPeople = true,
                    showGames = true,
                    showFinance = true,
                    canMutateOperations = true,
                    financeDestination = GroupsDestination.FINANCE,
                ),
                memberships = session.toSafeGroupSelectionMemberships(),
            ),
        )

        onNodeWithTag("groups-settings").performClick()
        onNodeWithTag(GroupsNavigationTags.Invite).performScrollTo().performClick()
        onNodeWithTag("groups-logout").performScrollTo().performClick()

        assertEquals(
            listOf(
                AccessIntent.OpenSettings,
                AccessIntent.OpenInvite,
                AccessIntent.RequestLogout,
            ),
            intents,
        )
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

        handleAccessEffect(AccessUiEffect.RequestShare("https://example.test/invite"), share, {}, intents::add)

        assertEquals(listOf("https://example.test/invite"), share.values)
        assertEquals(listOf<AccessIntent>(AccessIntent.ShareFinished(successful = true)), intents)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.root(
        state: AccessRootSnapshot,
        onIntent: (AccessIntent) -> Unit = {},
        groupsNavigation: GroupsNavigationState? = null,
        onGroupsIntent: (GroupsNavigationIntent) -> Unit = {},
        initiallyShowAppHome: Boolean = false,
    ) = setContent {
        SaqzTheme {
            AuthenticatedAccessRoot(
                state,
                groupsNavigation = groupsNavigation,
                onGroupsIntent = onGroupsIntent,
                initiallyShowAppHome = initiallyShowAppHome,
                onIntent = onIntent,
            )
        }
    }

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
        val access = AccessViewModel { runtime }.also {
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
        )
        val selections = RecordingPhotoSelectionPort()
        val dependencies = GroupPhotoRuntimeDependencies(
            selection = selections,
            encoder = ImmediatePhotoEncoder,
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

        override fun cleanup(source: String) = Unit
    }

    private object ImmediatePhotoEncoder : GroupPhotoEncoderPort {
        override suspend fun encode(
            source: String,
            crop: GroupPhotoCrop,
        ): GroupPhotoEncodingResult = GroupPhotoEncodingResult.Encoded(
            EncodedGroupPhoto(
                mediaType = GroupPhotoMediaType.JPEG,
                contentLength = 3,
                source = { byteArrayOf(1, 2, 3) },
            ),
        )

        override fun cancel(source: String) = Unit
    }

    private class RecordingPhotoGateway : GroupPhotoGateway {
        val uploads = mutableListOf<GroupPhotoUploadCommand>()
        val reads = mutableListOf<Pair<GroupId, GroupPhotoVersionToken?>>()

        override suspend fun upload(command: GroupPhotoUploadCommand): SaqzResult<GroupPhotoReceipt, br.com.saqz.groups.domain.photo.GroupPhotoError> {
            uploads += command
            return SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"2\"")))
        }

        override suspend fun read(
            groupId: GroupId,
            version: GroupPhotoVersionToken?,
        ): SaqzResult<GroupPhotoReadResult, br.com.saqz.groups.domain.photo.GroupPhotoError> {
            reads += groupId to version
            return SaqzResult.Success(GroupPhotoReadResult.NotModified)
        }

        override suspend fun remove(
            groupId: GroupId,
            groupVersion: GroupPhotoVersionToken,
        ): SaqzResult<GroupPhotoReceipt, br.com.saqz.groups.domain.photo.GroupPhotoError> =
            SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"2\"")))
    }

    private class RecordingProfileGateway : GroupProfileGateway {
        override suspend fun createProfile(
            command: CreateGroupProfileCommand,
        ): SaqzResult<Group, GroupProfileError> =
            SaqzResult.Success(
                Group(
                    id = "created-group",
                    name = command.form.name,
                    timeZone = command.timeZone.id,
                    version = 1,
                    role = GroupRole.OWNER,
                ),
            )

        override suspend fun readProfile(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> =
            error("not used")

        override suspend fun updateProfile(
            command: UpdateGroupProfileCommand,
        ): SaqzResult<VersionedGroup, GroupProfileError> =
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
        initialSession: SessionAccessState = SessionAccessState.Ready(session),
        initialSelection: GroupSelectionState = GroupSelectionState.NoGroup,
        initialAdministration: GroupAdministrationState = GroupAdministrationState(),
    ) : AccessRuntimeContract {
        override val authObservedState = MutableStateFlow(true)
        override val authenticationState = MutableStateFlow(AuthenticationState())
        override val sessionState = MutableStateFlow(initialSession)
        override val selectionState = MutableStateFlow(initialSelection)
        override val administrationState = MutableStateFlow(initialAdministration)
        override val inviteToolState = MutableStateFlow(InviteToolState())
        override val attendanceDestinationState = MutableStateFlow<AttendanceLinkDestination?>(null)
        override val sessionInvalidator: SessionInvalidator = object : SessionInvalidator { override fun invalidate() = Unit }

        override fun onIntent(intent: AccessRuntimeIntent) = Unit
        override fun newRequestId(): String = "route-request"
    }

    private fun validSetupDraft() = GroupSetupDraft(
        resource = GroupDraftResource.CREATE_GROUP,
        groupId = null,
        groupVersion = null,
        etag = null,
        commandKey = "create-command",
        form = DraftGroupSetupForm(
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
        val session = AccessSession(
            user = AccessUser("user", "user@example.test", "User"),
            memberships = listOf(
                AccessMembership(GroupId("alpha"), "Alpha", AccessMembershipRole("OWNER")),
                AccessMembership(GroupId("beta"), "Beta", AccessMembershipRole("ATHLETE")),
            ),
        )
        val selector = GroupSelectionState.Selector(session.toSafeGroupSelectionMemberships())
        val group = VersionedGroup(
            Group("current", "Current Group", "America/Sao_Paulo", 1, GroupRole.OWNER),
            br.com.saqz.groups.domain.group.GroupVersionToken("\"1\""),
        )
        val ownerAdministration = GroupAdministrationState(
            group = group,
            actions = GroupActions(true, true, true),
        )
    }
}
