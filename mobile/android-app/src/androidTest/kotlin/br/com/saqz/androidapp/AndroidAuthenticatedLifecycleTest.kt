package br.com.saqz.androidapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ProfilePhotoCallback
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.androidapp.access.AndroidIntentLinkPort
import br.com.saqz.composeapp.AccessRuntimeDependencies
import br.com.saqz.composeapp.GroupsRuntimeDependencies
import br.com.saqz.composeapp.SaqzPlatformDependencies
import br.com.saqz.composeapp.di.SaqzDraftStores
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupInviteUrlReadCallback
import br.com.saqz.groups.port.GroupInviteUrlReadResult
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.GroupInviteUrlWriteCallback
import br.com.saqz.groups.port.GroupInviteUrlWriteResult
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.toNetworkEnvironment
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class AndroidAuthenticatedLifecycleTest {
    private val state = LifecycleFixture()
    private val injection = CompositionInjectionRule(state)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(injection).around(compose)

    @After
    fun resetSystemConfiguration() {
        shell("settings put system font_scale 1.0")
        if (state.links.warmUrls.isNotEmpty()) {
            compose.activityRule.scenario.onActivity { it.finishAndRemoveTask() }
        }
    }

    @Test
    fun configuredCompositionStartsAtLoginWithoutProtectedContent() {
        compose.onNodeWithTag("login-submit").assertIsDisplayed()
        compose.onNodeWithText("Explorar componentes").assertDoesNotExist()
        assertEquals(1, state.compositions)
        assertEquals(NetworkEnvironment.Dev, state.lastEnvironment)
    }

    @Test
    fun recreationRetainsOneCompositionAndOneAuthObserver() {
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(1, state.compositions)
        assertEquals(1, state.auth.observeCalls)
        assertEquals(1, state.auth.activeObservers)
    }

    @Test
    fun backgroundForegroundDoesNotDuplicateSubscriptionsOrColdStart() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertEquals(1, state.auth.observeCalls)
        assertEquals(listOf<String?>(null), state.links.coldUrls)
    }

    @Test
    fun warmIntentIsForwardedAndBecomesCurrentActivityIntent() {
        val url = "https://links.saqz.app/invite?saqz_invite=${LifecycleFixture.NEW_INVITE}"
        lateinit var scenarioIntent: Intent
        try {
            compose.activityRule.scenario.onActivity { activity ->
                scenarioIntent = Intent(activity.intent)
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url), activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            compose.waitUntil(timeoutMillis = 5_000) { state.links.warmUrls == listOf(url) }

            assertEquals(listOf(url), state.links.warmUrls)
            compose.activityRule.scenario.onActivity { assertEquals(url, it.intent.dataString) }
        } finally {
            compose.activityRule.scenario.onActivity { it.intent = scenarioIntent }
        }
    }

    @Test
    fun passwordSubmitRemainsSingleFlightAcrossRecreation() {
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]
            .performTextInput("athlete@example.test")
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1]
            .performTextInput("password")
        compose.onNodeWithTag("login-submit").performClick()
        assertEquals(1, state.auth.passwordCalls)

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(1, state.auth.passwordCalls)
        compose.onNodeWithTag("login-submit").assertIsDisplayed()
    }

    @Test
    fun googleSubmitRemainsSingleFlightAcrossRecreation() {
        compose.onNodeWithTag("login-google").performClick()
        assertEquals(1, state.auth.googleCalls)

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(1, state.auth.googleCalls)
        compose.onNodeWithTag("login-google").assertIsDisplayed()
    }

    @Test
    fun registrationSubmitRemainsSingleFlightAcrossRecreation() {
        compose.onNodeWithTag("login-create-account").performScrollTo().performClick()
        compose.onNodeWithTag("register-name").performScrollTo()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("register-name")), useUnmergedTree = true)
            .performTextInput("Athlete")
        compose.onNodeWithTag("register-email").performScrollTo()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("register-email")), useUnmergedTree = true)
            .performTextInput("athlete@example.test")
        compose.onNodeWithTag("register-phone").performScrollTo()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("register-phone")), useUnmergedTree = true)
            .performTextInput("11999999999")
        compose.onNodeWithTag("register-password").performScrollTo()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("register-password")), useUnmergedTree = true)
            .performTextInput("password")
        compose.onNodeWithTag("register-submit").performScrollTo().performClick()
        assertEquals(1, state.auth.registrationCalls)

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(1, state.auth.registrationCalls)
        compose.onNodeWithTag("register-submit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun restoredUnverifiedSessionSkipsLoginAndSurvivesRecreation() {
        state.auth.emit(
            AuthState.SignedIn(
                NativeUser("subject-1", "athlete@example.test", false, "Athlete"),
            ),
        )
        // VUL-84 retirou o bloqueio por e-mail: esta identidade deve alcançar o bootstrap.
        // A porta desta fixture recusa o token, então o resultado esperado é BootstrapError,
        // sem voltar ao login e sem reinstalar a antiga tela identity-verify.
        val session = org.koin.mp.KoinPlatformTools.defaultContext().get()
            .get<br.com.saqz.access.presentation.SessionAccessStateMachine>()
        compose.waitUntil(5_000) {
            session.state.value == br.com.saqz.access.presentation.SessionAccessState.BootstrapError
        }
        compose.onNodeWithText("Não foi possível carregar sua conta.").assertIsDisplayed()
        compose.onNodeWithTag("login-submit").assertDoesNotExist()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(br.com.saqz.access.presentation.SessionAccessState.BootstrapError, session.state.value)
        compose.onNodeWithText("Não foi possível carregar sua conta.").assertIsDisplayed()
        assertEquals(1, state.compositions)
        assertEquals(1, state.auth.observeCalls)
        compose.onNodeWithTag("login-submit").assertDoesNotExist()
    }

    @Test
    fun providerFailureKeepsLoginAndProtectedContentAbsent() {
        state.auth.failPassword = true
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]
            .performTextInput("athlete@example.test")
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1]
            .performTextInput("password")
        compose.onNodeWithTag("login-submit").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Não foi possível entrar agora. Tente novamente.").assertIsDisplayed()
        compose.onNodeWithTag("login-submit").assertIsDisplayed()
        compose.onNodeWithText("Explorar componentes").assertDoesNotExist()
    }

    @Test
    fun maximumFontScaleKeepsAuthenticationActionsReachable() {
        shell("settings put system font_scale 2.0")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithTag("login-submit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("login-google").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun imeAndRecreationKeepOneReachableDestination() {
        compose.onNodeWithTag("login-password").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithTag("login-submit").performScrollTo().assertIsDisplayed()
        assertEquals(
            1,
            compose.onAllNodesWithTag("authenticated-access-destination").fetchSemanticsNodes().size,
        )
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
    }
}

private class CompositionInjectionRule(
    private val state: LifecycleFixture,
) : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            MainActivityComposition.factoryOverride = LifecycleCompositionFactory(state)
            try {
                base.evaluate()
            } finally {
                MainActivityComposition.factoryOverride = null
            }
        }
    }
}

private class LifecycleCompositionFactory(
    private val fixture: LifecycleFixture,
) : AndroidAppCompositionFactory {
    override fun create(
        context: Context,
        scope: CoroutineScope,
        activity: () -> Activity,
    ): AndroidAppComposition {
        fixture.compositions++
        val dependencies = SaqzPlatformDependencies(
    notifications = object : br.com.saqz.groups.domain.communication.NativeNotificationPort {
        override fun device(done: (br.com.saqz.groups.domain.communication.NotificationDevice?) -> Unit) = done(null)
        override fun clear(done: (Boolean) -> Unit) = done(true)
        override fun observe(changed: () -> Unit) = br.com.saqz.groups.domain.communication.NotificationSubscription { }
    },
    financialDocuments = object : br.com.saqz.receivables.domain.port.ReceiptDocumentPicker {
        override fun choose(done: br.com.saqz.receivables.domain.port.ReceiptFileCallback): br.com.saqz.receivables.domain.port.ReceiptFileCancellation {
            done.onFileSelected(br.com.saqz.receivables.domain.port.ReceiptFileSelection.Cancelled)
            return br.com.saqz.receivables.domain.port.ReceiptFileCancellation {}
        }
    },
            analytics = object : br.com.saqz.composeapp.analytics.AnalyticsSink {
                override fun track(name: String, params: Map<String, String>) = Unit
                override fun setUserId(id: String?) = Unit
                override fun log(message: String) = Unit
                override fun setUserProperty(name: String, value: String?) = Unit
            },
            environment = "dev",
            apiBaseUrl = "http://127.0.0.1:1",
            access = AccessRuntimeDependencies(
                auth = fixture.auth,
                links = fixture.links,
                localState = fixture.local,
                share = fixture.share,
                profilePhoto = LifecycleProfilePhotoPort,
                profilePhotoSelection = LifecycleProfilePhotoPort,
            ),
            groups = GroupsRuntimeDependencies(
                map = br.com.saqz.groups.domain.map.GroupMapPort { _, done -> done.complete(true) },
                attendanceShare = LifecycleAttendanceSharePort,
                photos = lifecycleGroupPhotos,
                links = fixture.links,
                state = fixture.local,
                inviteUrlStore = LifecycleInviteUrlStorePort,
                inviteShare = LifecycleInviteSharePort,
                inviteClipboard = LifecycleInviteClipboardPort,
            ),
            drafts = SaqzDraftStores(
                groupDrafts = LifecycleGroupDraftStore,
                gameDrafts = LifecycleGameDraftStore,
                monthlyChargeDrafts = LifecycleMonthlyChargeDraftStore,
                expenseDrafts = LifecycleExpenseDraftStore,
            ),
        )
        fixture.lastEnvironment = dependencies.environment.toNetworkEnvironment()
        return AndroidAppComposition(dependencies, fixture.links)
    }
}

private class LifecycleFixture {
    var compositions = 0
    var lastEnvironment: NetworkEnvironment? = null
    val auth = LifecycleAuthPort()
    val links = LifecycleLinkPort()
    val local = LifecycleLocalState(RESTORED_INVITE)
    val share = LifecycleSharePort()

    companion object {
        const val RESTORED_INVITE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val NEW_INVITE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE"
    }
}

private object LifecycleInviteUrlStorePort : GroupInviteUrlStorePort {
    override fun read(groupId: String, done: GroupInviteUrlReadCallback) =
        done.complete(GroupInviteUrlReadResult.Success(null))

    override fun write(groupId: String, cache: br.com.saqz.groups.port.GroupInviteUrlCache?, done: GroupInviteUrlWriteCallback) =
        done.complete(GroupInviteUrlWriteResult.Success)
}

private object LifecycleInviteSharePort : NativeInviteSharePort {
    override fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)

    override fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)

    override fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)
}

private object LifecycleInviteClipboardPort : NativeInviteClipboardPort {
    override fun copyText(text: String, done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Success)
}

private class LifecycleAuthPort : NativeAuthPort {
    private var listener: AuthStateListener? = null
    var observeCalls = 0
    var activeObservers = 0
    var passwordCalls = 0
    var googleCalls = 0
    var registrationCalls = 0
    var failPassword = false

    override fun observe(listener: AuthStateListener): Cancelable {
        observeCalls++
        activeObservers++
        this.listener = listener
        listener.onStateChanged(AuthState.SignedOut)
        return object : Cancelable {
            override fun cancel() {
                if (this@LifecycleAuthPort.listener === listener) {
                    this@LifecycleAuthPort.listener = null
                    activeObservers--
                }
            }
        }
    }

    fun emit(state: AuthState) {
        listener?.onStateChanged(state)
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) {
        registrationCalls++
    }

    override fun signInWithPassword(email: String, password: String, done: AuthCallback) {
        passwordCalls++
        if (failPassword) done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
    }

    override fun signInWithGoogle(done: AuthCallback) {
        googleCalls++
    }

    override fun sendVerification(done: ResultCallback) = done.complete(OperationResult.Success)

    override fun reloadUser(done: AuthCallback) = done.complete(
        AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE),
    )

    override fun updateDisplayName(name: String, done: AuthCallback) = done.complete(
        AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE),
    )

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) = done.complete(
        TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE),
    )

    override fun signOut(done: ResultCallback) {
        emit(AuthState.SignedOut)
        done.complete(OperationResult.Success)
    }
}

private class LifecycleLinkPort : AndroidIntentLinkPort, NativeGroupLinkPort {
    private var listener: InviteCodeListener? = null
    private val groupListeners = mutableSetOf<GroupLinkEventListener>()
    var startCalls = 0
    var activeSubscriptions = 0
    val coldUrls = mutableListOf<String?>()
    val warmUrls = mutableListOf<String?>()

    override fun start(listener: InviteCodeListener): Cancelable {
        startCalls++
        activeSubscriptions++
        this.listener = listener
        return object : Cancelable {
            override fun cancel() {
                if (this@LifecycleLinkPort.listener === listener) {
                    this@LifecycleLinkPort.listener = null
                    activeSubscriptions--
                }
            }
        }
    }

    override fun start(listener: GroupLinkEventListener): GroupCancelable {
        startCalls++
        activeSubscriptions++
        groupListeners += listener
        return object : GroupCancelable {
            override fun cancel() {
                if (groupListeners.remove(listener)) {
                    activeSubscriptions--
                }
            }
        }
    }

    override fun onColdStart(url: String?) {
        coldUrls += url
    }

    override fun onWarmIntent(url: String?) {
        warmUrls += url
    }

    override fun onNotificationOpen(groupId: String?) {
        if (groupListeners.isNotEmpty()) groupListeners.forEach { it.onEvent(GroupLinkEvent.NotificationOpen(groupId)) }
    }

    fun emit(code: String) {
        if (groupListeners.isNotEmpty()) groupListeners.forEach { it.onEvent(GroupLinkEvent.Invite(code)) }
        else listener?.onInviteCode(code)
    }
}

private class LifecycleLocalState(
    var pending: String?,
) : LocalAccessStatePort, LocalGroupStatePort {
    var selected: String? = null
    var pendingReads = 0
    val pendingWrites = mutableListOf<String?>()

    override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(selected))

    override fun writeSelectedGroupId(value: String?, done: ResultCallback) {
        selected = value
        done.complete(OperationResult.Success)
    }

    override fun readPendingInvite(done: ValueCallback) {
        pendingReads++
        done.complete(ValueResult.Success(pending))
    }

    override fun writePendingInvite(value: String?, done: ResultCallback) {
        pending = value
        pendingWrites += value
        done.complete(OperationResult.Success)
    }

    override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(selected))

    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) {
        selected = value
        done.complete(GroupOperationResult.Success)
    }

    override fun readPendingInvite(done: GroupValueCallback) {
        pendingReads++
        done.complete(GroupValueResult.Success(pending))
    }

    override fun writePendingInvite(value: String?, done: GroupResultCallback) {
        pending = value
        pendingWrites += value
        done.complete(GroupOperationResult.Success)
    }

    override fun readPendingAttendanceLink(done: GroupValueCallback) {
        done.complete(GroupValueResult.Success(null))
    }

    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) {
        done.complete(GroupOperationResult.Success)
    }
}

private object LifecycleProfilePhotoPort : NativeProfilePhotoPort,
    br.com.saqz.profile.domain.ProfilePhotoSelectionPort {
    override fun chooseCamera(done: ProfilePhotoCallback) = failed(done)
    override fun chooseLibrary(done: ProfilePhotoCallback) = failed(done)

    override fun chooseCamera(done: br.com.saqz.profile.domain.ProfilePhotoSelectionCallback) =
        profileFailed(done)

    override fun chooseLibrary(done: br.com.saqz.profile.domain.ProfilePhotoSelectionCallback) =
        profileFailed(done)

    private fun failed(done: ProfilePhotoCallback): Cancelable {
        done.complete(ProfilePhotoResult.Failed)
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

private class LifecycleSharePort : NativeSharePort {
    val shared = mutableListOf<String>()

    override fun share(text: String, done: ResultCallback) {
        shared += text
        done.complete(OperationResult.Success)
    }
}
