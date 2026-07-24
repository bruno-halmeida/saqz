package br.com.saqz.androidapp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.NativeUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * T14 journey coverage (VUL-5): phone-completion gate (ATH-01), completion -> deferred
 * invite redeem ordering, position onboarding (ATH-02), and roster entry points/controls
 * per role (ATH-03/04), all driven end to end through MainActivity, the real Koin graph,
 * and the real Ktor gateways against a scripted loopback API (no product code faked).
 */
@RunWith(AndroidJUnit4::class)
class AndroidAthleteJourneyTest {
    private val fixture = JourneyFixture()
    private val injection = JourneyInjectionRule(fixture)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(injection).around(compose)

    @Test
    fun phoneGateBlocksEntryOnceAndNeverReturnsAfterCompletion() {
        var phoneCompleted = false
        fixture.api.route = { request ->
            when {
                request.method == "PUT" && request.path == "/api/session" ->
                    json(sessionJson(phoneRequired = !phoneCompleted))
                request.method == "PATCH" && request.path == "/api/session/profile" -> {
                    phoneCompleted = true
                    json(sessionJson(phoneRequired = false))
                }
                else -> FakeSaqzApi.Reply(404)
            }
        }

        signIn()
        waitForTag(PHONE_SUBMIT)
        compose.onNodeWithText("Qual seu telefone?").assertIsDisplayed()
        compose.onNodeWithTag(HOME).assertDoesNotExist()

        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]
            .performTextInput(FAKE_PHONE_INPUT)
        compose.onNodeWithTag(PHONE_SUBMIT).performClick()
        waitForTag(HOME)
        compose.onNodeWithTag(PHONE_SUBMIT).assertDoesNotExist()
        assertTrue(fixture.api.requests.any { it.method == "PATCH" && it.path == "/api/session/profile" })

        // Next entry: the provider re-emits the restored session and bootstrap re-runs.
        signIn()
        compose.waitUntil(TIMEOUT) { fixture.api.count("PUT", "/api/session") == 2 }
        waitForTag(HOME)
        compose.onNodeWithTag(PHONE_SUBMIT).assertDoesNotExist()
        assertEquals(1, fixture.api.count("PATCH", "/api/session/profile"))
    }

    @Test
    fun pendingInviteSurvivesPhoneGateAndRedeemsOnlyAfterCompletion() {
        var phoneCompleted = false
        fixture.api.route = { request ->
            when {
                request.method == "PUT" && request.path == "/api/session" ->
                    json(sessionJson(phoneRequired = !phoneCompleted))
                request.method == "PATCH" && request.path == "/api/session/profile" -> {
                    phoneCompleted = true
                    json(sessionJson(phoneRequired = false))
                }
                request.method == "POST" && request.path == "/api/invites/redeem" ->
                    json("""{"groupId":"$GROUP_ID","role":"ATHLETE"}""")
                request.method == "GET" && request.path == "/api/groups/$GROUP_ID" ->
                    json(groupJson("ATHLETE"), mapOf("ETag" to "\"1\""))
                else -> FakeSaqzApi.Reply(404)
            }
        }

        signIn()
        waitForTag(PHONE_SUBMIT)
        fixture.links.emit(INVITE_CODE)
        compose.waitUntil(TIMEOUT) { fixture.local.pending == INVITE_CODE }
        compose.waitForIdle()
        assertEquals(0, fixture.api.count("POST", "/api/invites/redeem"))

        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]
            .performTextInput(FAKE_PHONE_INPUT)
        compose.onNodeWithTag(PHONE_SUBMIT).performClick()

        compose.waitUntil(TIMEOUT) { fixture.api.count("POST", "/api/invites/redeem") == 1 }
        val order = fixture.api.requests.map { "${it.method} ${it.path}" }
        assertTrue(
            "expected completion before redeem, got $order",
            order.indexOf("PATCH /api/session/profile") < order.indexOf("POST /api/invites/redeem"),
        )
        // The resumed redeem lands the athlete in the group: onboarding step appears.
        waitForTag(ONBOARDING_SHEET)
        compose.waitUntil(TIMEOUT) { fixture.local.pending == null }
    }

    @Test
    fun positionOnboardingShowsOnceSkipsWithoutRequestAndSkipsReRedeem() {
        fixture.api.route = { request ->
            when {
                request.method == "PUT" && request.path == "/api/session" ->
                    json(sessionJson(phoneRequired = false))
                request.method == "POST" && request.path == "/api/invites/redeem" ->
                    json("""{"groupId":"$GROUP_ID","role":"ATHLETE"}""")
                request.method == "GET" && request.path == "/api/groups/$GROUP_ID" ->
                    json(groupJson("ATHLETE"), mapOf("ETag" to "\"1\""))
                else -> FakeSaqzApi.Reply(404)
            }
        }

        signIn()
        waitForTag(HOME)
        fixture.links.emit(INVITE_CODE)
        waitForTag(ONBOARDING_SHEET)

        compose.onNodeWithText("Qual sua posição?").assertIsDisplayed()
        compose.onNodeWithText("Levantador").assertIsDisplayed()
        compose.onNodeWithText("Líbero").assertIsDisplayed()
        compose.onAllNodesWithText("LEVANTADOR").assertCountEquals(0)

        compose.onNodeWithTag(ONBOARDING_SKIP).performClick()
        waitForGone(ONBOARDING_SHEET)
        assertTrue(fixture.api.requests.none { it.method == "PATCH" && it.path.endsWith("/athletes/me") })

        // Re-redeem by the now-existing member: no onboarding step again.
        fixture.links.emit(INVITE_CODE)
        compose.waitUntil(TIMEOUT) { fixture.api.count("POST", "/api/invites/redeem") == 2 }
        compose.waitForIdle()
        compose.onNodeWithTag(ONBOARDING_SHEET).assertDoesNotExist()
        assertTrue(fixture.api.requests.none { it.method == "PATCH" && it.path.endsWith("/athletes/me") })
    }

    @Test
    fun ownerReachesRosterWithManagementControlsAndPtBrLabels() {
        fixture.api.route = { request ->
            when {
                request.method == "PUT" && request.path == "/api/session" ->
                    json(sessionJson(phoneRequired = false, memberships = membershipJson("OWNER")))
                request.method == "GET" && request.path == "/api/groups/$GROUP_ID" ->
                    json(groupJson("OWNER"), mapOf("ETag" to "\"1\""))
                request.method == "GET" && request.path.startsWith("/api/groups/$GROUP_ID/athletes") ->
                    json(ROSTER_JSON)
                else -> FakeSaqzApi.Reply(404)
            }
        }

        signIn()
        waitForTag(HOME)
        compose.onNodeWithText("Grupos").performClick()
        waitForTag(PEOPLE_SHORTCUT)
        compose.onNodeWithTag(PEOPLE_SHORTCUT).performClick()
        waitForTag(ROSTER_LIST)

        // pt-BR semantics, never enum names (chips and badges).
        assertTrue(compose.onAllNodesWithText("Mensalista").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Avulso").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Levantador").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Pago").assertIsDisplayed()
        compose.onAllNodesWithText("MENSALISTA").assertCountEquals(0)
        compose.onAllNodesWithText("AVULSO").assertCountEquals(0)
        compose.onAllNodesWithText("EM_DIA").assertCountEquals(0)
        compose.onAllNodesWithText("PENDENTE").assertCountEquals(0)

        // OWNER manages: opening an athlete exposes the edit sheet with removal.
        compose.onNodeWithTag("athlete-roster-entry-user-ana").performClick()
        waitForTag(ROSTER_EDIT_SHEET)
        compose.onNodeWithText("Editar atleta").assertIsDisplayed()
        compose.onNodeWithText("Remover do grupo").assertIsDisplayed()
    }

    @Test
    fun athleteHasNoRosterEntryPointsAndNeverFetchesRoster() {
        fixture.api.route = { request ->
            when {
                request.method == "PUT" && request.path == "/api/session" ->
                    json(sessionJson(phoneRequired = false, memberships = membershipJson("ATHLETE")))
                request.method == "GET" && request.path == "/api/groups/$GROUP_ID" ->
                    json(groupJson("ATHLETE"), mapOf("ETag" to "\"1\""))
                request.method == "GET" && request.path == "/api/athletes/me" ->
                    json(OWN_PROFILE_JSON)
                else -> FakeSaqzApi.Reply(404)
            }
        }

        signIn()
        waitForTag(HOME)
        compose.onNodeWithText("Grupos").performClick()
        waitForTag("groups-shortcut-games")
        compose.onNodeWithTag(PEOPLE_SHORTCUT).assertDoesNotExist()

        // Group-scoped chrome has no tab bar (MENU-08): back to Início, then Mais.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag(HOME)
        compose.onNodeWithText("Mais").performClick()
        waitForTag("groups-more")
        compose.onNodeWithTag("groups-more-people").assertDoesNotExist()
        assertEquals(0, fixture.api.count("GET", "/api/groups/$GROUP_ID/athletes"))
    }

    private fun signIn() {
        fixture.auth.emit(
            AuthState.SignedIn(NativeUser("user-journey", "atleta@exemplo.test", true, "Atleta Teste")),
        )
    }

    private fun waitForTag(tag: String) {
        try {
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true)
                .printToLog("JourneyTree", maxDepth = 12)
            throw error
        }
    }

    private fun waitForGone(tag: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun json(body: String, headers: Map<String, String> = emptyMap()) =
        FakeSaqzApi.Reply(200, body, headers)

    private fun sessionJson(phoneRequired: Boolean, memberships: String = "") = buildString {
        append("""{"user":{"id":"user-journey","email":"atleta@exemplo.test","displayName":"Atleta Teste"""")
        if (!phoneRequired) append(""","phone":"$FAKE_PHONE_E164"""")
        append(""","phoneRequired":$phoneRequired},"memberships":[$memberships]}""")
    }

    private fun membershipJson(role: String) =
        """{"groupId":"$GROUP_ID","groupName":"$GROUP_NAME","role":"$role"}"""

    private fun groupJson(role: String) =
        """{"id":"$GROUP_ID","name":"$GROUP_NAME","timeZone":"America/Sao_Paulo","version":1,"role":"$role"}"""

    private companion object {
        const val TIMEOUT = 10_000L
        const val HOME = "authenticated-home"
        const val PHONE_SUBMIT = "identity-phone-submit"
        const val ONBOARDING_SHEET = "position-onboarding-sheet"
        const val ONBOARDING_SKIP = "position-onboarding-skip"
        const val PEOPLE_SHORTCUT = "groups-shortcut-people"
        const val ROSTER_LIST = "athlete-roster-list"
        const val ROSTER_EDIT_SHEET = "athlete-roster-edit"
        const val GROUP_ID = "grupo-jornada"
        const val GROUP_NAME = "Vôlei de Quinta"
        const val INVITE_CODE = "JRNYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        // Structurally valid BR mobile input, obviously synthetic (never a real number).
        const val FAKE_PHONE_INPUT = "11999990001"
        const val FAKE_PHONE_E164 = "+5511999990001"

        val ROSTER_JSON = """
            {"athletes":[
              {"userId":"user-ana","displayName":"Ana Fictícia","position":"LEVANTADOR",
               "membershipType":"MENSALISTA","active":true,"financialStatus":"EM_DIA"},
              {"userId":"user-bia","displayName":"Bia Fictícia",
               "membershipType":"AVULSO","active":true,"financialStatus":"PENDENTE"}
            ]}
        """.trimIndent()

        val OWN_PROFILE_JSON = """
            {"userId":"user-journey","displayName":"Atleta Teste","memberships":[
              {"groupId":"$GROUP_ID","groupName":"$GROUP_NAME","role":"ATHLETE",
               "membershipType":"AVULSO","active":true}
            ]}
        """.trimIndent()
    }
}
