package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.net.HttpURLConnection
import java.net.URL

/** Real launcher + production composition. No setContent, fake gateway or auth override. */
internal abstract class InstalledE2e(private val scenarioName: String) {
    protected val ui = createAndroidComposeRule<MainActivity>()
    private val environment = TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                check(BuildConfig.APPLICATION_ID == "app.saqz.e2e")
                check(BuildConfig.FIREBASE_USE_EMULATOR && BuildConfig.FIREBASE_PROJECT_ID == "saqz-local")
                check(BuildConfig.API_BASE_URL == "http://10.0.2.2:18080")
                assertNull("E2E must use production composition", MainActivityComposition.factoryOverride)
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(environment).around(SignedOutAccessRule()).around(ui)

    private val fixture by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("e2e-fixture.json")
            .bufferedReader().use { JSONObject(it.readText()) }
    }
    protected val data: JSONObject get() = fixture.getJSONObject("scenarios").getJSONObject(scenarioName)
    protected val group: String get() = data.getString("group")
    protected val secondGroup: String get() = data.getString("secondGroup")
    protected fun actor(role: String) = data.getJSONObject(role)
    protected val auth get() = AndroidFirebaseBootstrap.initialize(InstrumentationRegistry.getInstrumentation().targetContext)

    protected fun waitTag(tag: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
    }

    protected fun click(tag: String, scroll: Boolean = false) {
        waitTag(tag)
        val node = ui.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }

    protected fun input(tag: String, text: String) {
        waitTag(tag)
        ui.onNodeWithTag(tag).performScrollTo()
        ui.onNode(
            hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
            useUnmergedTree = true,
        ).performTextReplacement(text)
    }

    protected fun login(role: String, password: String = fixture.getString("password")) {
        enterCredentials(role, password)
        waitTag("saqz-shell-content")
        assertEquals(actor(role).getString("uid"), auth.currentUser?.uid)
    }

    protected fun enterCredentials(role: String, password: String) {
        input("login-email", actor(role).getString("email"))
        input("login-password", password)
        click("login-submit", scroll = true)
    }

    protected fun tab(label: String) {
        waitTag("saqz-shell-content")
        ui.onNode(hasText(label) and isSelectable()).assertIsDisplayed().performClick()
    }

    protected fun groups() = tab("Grupos")

    protected fun openGroup(id: String = group) {
        groups()
        click("group-list-group-$id")
        waitTag("group-details")
    }

    protected fun back() {
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
    }

    protected fun logout() {
        tab("Perfil")
        click("own-profile-sign-out", scroll = true)
        click("profile-exit-logout")
        waitTag("login-submit")
        assertNull(auth.currentUser)
        ui.onNodeWithTag("saqz-shell-content").assertDoesNotExist()
    }

    protected fun api(role: String, path: String, method: String = "GET", body: JSONObject? = null, status: Int = 200): JSONObject {
        val identity = http(
            "http://10.0.2.2:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-saqz-local-api-key",
            "POST", JSONObject().put("email", actor(role).getString("email"))
                .put("password", fixture.getString("password")).put("returnSecureToken", true),
        )
        return http("${BuildConfig.API_BASE_URL}$path", method, body, identity.getString("idToken"), status)
    }

    protected fun membershipIds(role: String): Set<String> {
        val memberships = api(role, "/api/session", "PUT").getJSONArray("memberships")
        return (0 until memberships.length()).map { memberships.getJSONObject(it).getString("groupId") }.toSet()
    }
}

/** Independent HTTP oracle: validates persisted server state, never invokes app gateways. */
private fun http(url: String, method: String, body: JSONObject?, token: String? = null, status: Int = 200): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Content-Type", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        body?.let {
            connection.doOutput = true
            connection.outputStream.use { stream -> stream.write(it.toString().toByteArray()) }
        }
        assertEquals("$method ${URL(url).path}", status, connection.responseCode)
        val stream = if (status < 400) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    } finally {
        connection.disconnect()
    }
}
