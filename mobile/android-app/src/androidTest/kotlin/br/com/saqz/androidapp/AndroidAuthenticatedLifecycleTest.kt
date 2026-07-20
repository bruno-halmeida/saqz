package br.com.saqz.androidapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
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
import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
import br.com.saqz.androidapp.access.AndroidIntentLinkPort
import br.com.saqz.composeapp.SaqzAppDependencies
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupInviteCodeListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
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
        assertEquals("dev", state.lastEnvironment)
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
    fun recreationRetainsOneLinkSubscriptionAndOnePendingRestore() {
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(1, state.links.startCalls)
        assertEquals(1, state.links.activeSubscriptions)
        assertEquals(1, state.local.pendingReads)
        assertEquals(LifecycleFixture.RESTORED_INVITE, state.local.pending)
    }

    @Test
    fun backgroundForegroundDoesNotDuplicateSubscriptionsOrColdStart() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertEquals(1, state.auth.observeCalls)
        assertEquals(1, state.links.startCalls)
        assertEquals(listOf<String?>(null), state.links.coldUrls)
    }

    @Test
    fun warmIntentIsForwardedAndBecomesCurrentActivityIntent() {
        val url = "https://saqz.test-app.link/invite?saqz_invite=${LifecycleFixture.NEW_INVITE}"
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
    fun inviteArrivingBeforeAuthenticationIsPersisted() {
        state.links.emit(LifecycleFixture.NEW_INVITE)
        compose.waitUntil(timeoutMillis = 5_000) { state.local.pending == LifecycleFixture.NEW_INVITE }

        assertEquals(LifecycleFixture.NEW_INVITE, state.local.pending)
        assertEquals(LifecycleFixture.NEW_INVITE, state.local.pendingWrites.last())
        assertEquals(0, state.auth.passwordCalls)
    }

    @Test
    fun restoredInviteRemainsPendingAcrossRecreation() {
        assertEquals(LifecycleFixture.RESTORED_INVITE, state.local.pending)

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(LifecycleFixture.RESTORED_INVITE, state.local.pending)
        assertEquals(1, state.local.pendingReads)
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
        compose.onNodeWithText("Criar conta").performClick()
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]
            .performTextInput("Athlete")
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1]
            .performTextInput("athlete@example.test")
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[2]
            .performTextInput("password")
        compose.onNodeWithTag("registration-submit").performClick()
        assertEquals(1, state.auth.registrationCalls)

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals(1, state.auth.registrationCalls)
        compose.onNodeWithTag("registration-submit").assertIsDisplayed()
    }

    @Test
    fun restoredUnverifiedSessionSkipsLoginAndSurvivesRecreation() {
        state.auth.emit(
            AuthState.SignedIn(
                NativeUser("subject-1", "athlete@example.test", false, "Athlete"),
            ),
        )
        compose.waitForIdle()
        compose.onNodeWithTag("identity-verify").assertIsDisplayed()
        compose.onNodeWithTag("login-submit").assertDoesNotExist()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithTag("identity-verify").assertIsDisplayed()
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

        compose.onNodeWithText("O login esta indisponivel. Tente novamente").assertIsDisplayed()
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
        val dependencies = SaqzAppDependencies(
            environment = "dev",
            apiBaseUrl = "http://127.0.0.1:1",
            auth = fixture.auth,
            links = fixture.links,
            localState = fixture.local,
            share = fixture.share,
            groupPhotos = GroupPhotoRuntimeDependencies.Unconfigured,
            groupLinks = fixture.links,
            groupState = fixture.local,
        )
        fixture.lastEnvironment = dependencies.environment
        return AndroidAppComposition(dependencies, fixture.links)
    }
}

private class LifecycleFixture {
    var compositions = 0
    var lastEnvironment: String? = null
    val auth = LifecycleAuthPort()
    val links = LifecycleLinkPort()
    val local = LifecycleLocalState(RESTORED_INVITE)
    val share = LifecycleSharePort()

    companion object {
        const val RESTORED_INVITE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val NEW_INVITE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE"
    }
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

    override fun sendPasswordReset(email: String, done: ResultCallback) =
        done.complete(OperationResult.Success)

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
    private var groupListener: GroupInviteCodeListener? = null
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

    override fun start(listener: GroupInviteCodeListener): GroupCancelable {
        startCalls++
        activeSubscriptions++
        groupListener = listener
        return object : GroupCancelable {
            override fun cancel() {
                if (this@LifecycleLinkPort.groupListener === listener) {
                    this@LifecycleLinkPort.groupListener = null
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

    fun emit(code: String) {
        groupListener?.onInviteCode(code) ?: listener?.onInviteCode(code)
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
}

private class LifecycleSharePort : NativeSharePort {
    val shared = mutableListOf<String>()

    override fun share(text: String, done: ResultCallback) {
        shared += text
        done.complete(OperationResult.Success)
    }
}
