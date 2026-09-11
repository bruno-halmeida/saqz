package br.com.saqz.androidapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** APP-P01: UI write, remote persistence, return to Profile, and member/group isolation. */
@RunWith(AndroidJUnit4::class)
internal class SportsProfileE2eTest : ProfileFlowE2e("sports-profile") {
    @Test
    fun editingCourtPositionPersistsWithoutChangingTheBeachOrAnotherMember() {
        login("athlete")
        val before = memberships("athlete")
        val otherBefore = memberships("other")
        assertEquals(setOf(group, secondGroup), before.keys)
        assertEquals("PONTA", before.getValue(group).getString("position"))
        assertEquals("LEVANTADOR", otherBefore.getValue(group).getString("position"))
        tab("Perfil")
        openRegistration(group)
        assertCourtRegistration("PONTA")
        click("athlete-registration-position-CENTRAL", scroll = true)
        ui.onNodeWithTag("athlete-registration-position-CENTRAL").assertIsSelected()
        click("athlete-registration-save", scroll = true)

        waitTag("own-profile")
        val row = "own-profile-group-$group"
        scrollIn("own-profile", row)
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasTestTag(row) and hasText("Central", substring = true)).fetchSemanticsNodes().size == 1
        }
        ui.onNodeWithTag(row).assertTextContains("Central", substring = true)
        ui.onNodeWithTag("group-details").assertDoesNotExist()
        assertSavedAndIsolated(before, otherBefore)

        openRegistration(group)
        assertCourtRegistration("CENTRAL")
        back()
        ui.activityRule.scenario.recreate()
        tab("Perfil")
        openRegistration(secondGroup)
        fieldText("athlete-registration-nickname", "Atleta Praia")
        ui.onNodeWithTag("athlete-registration-side-ESQUERDA").assertIsSelected()
        ui.onNodeWithTag("athlete-registration-level-INICIANTE").assertIsSelected()
        ui.onNodeWithTag("athlete-registration-position").assertDoesNotExist()
        back()
        logout()

        login("athlete")
        tab("Perfil")
        openRegistration(group)
        assertCourtRegistration("CENTRAL")
        assertSavedAndIsolated(before, otherBefore)
    }

    private fun openRegistration(id: String) {
        waitTag("own-profile")
        scrollIn("own-profile", "own-profile-group-$id")
        click("own-profile-group-$id")
        waitTag("athlete-registration-screen")
    }

    private fun assertCourtRegistration(position: String) {
        fieldText("athlete-registration-nickname", "Atleta Quadra")
        fieldText("athlete-registration-height", "181")
        ui.onNodeWithTag("athlete-registration-position-$position").assertIsSelected()
        ui.onNodeWithTag("athlete-registration-level-INTERMEDIARIO").assertIsSelected()
        ui.onNodeWithTag("athlete-registration-position-LEVANTADOR").assertIsNotSelected()
        ui.onNodeWithText("Par Reservado").assertDoesNotExist()
    }

    private fun assertSavedAndIsolated(before: Map<String, JSONObject>, otherBefore: Map<String, JSONObject>) {
        val after = memberships("athlete")
        assertEquals(before.keys, after.keys)
        assertEquals(before.getValue(group).fields() + ("position" to "CENTRAL"), after.getValue(group).fields())
        assertEquals(before.getValue(secondGroup).fields(), after.getValue(secondGroup).fields())
        assertEquals(otherBefore.mapValues { it.value.fields() }, memberships("other").mapValues { it.value.fields() })
    }
}

/** APP-P02: real 403 on another athlete's stats, with NOBODY phone policy for both viewers. */
@RunWith(AndroidJUnit4::class)
internal class MemberPrivacyE2eTest : ProfileFlowE2e("member-privacy") {
    @Test
    fun forbiddenStatisticsPreserveTheOtherMembersBasicProfileAndPhonePrivacy() {
        login("athlete")
        val otherId = actor("other").getString("id")
        val statsPath = "/api/groups/$group/athletes/$otherId/stats"
        val basic = member("athlete", otherId)
        assertTrue(basic.isNull("phone"))
        assertEquals(data.getString("privatePhone"), api("other", "/api/athletes/me").getString("phone"))
        api("athlete", statsPath, status = 403)
        api("athlete", "/api/groups/$group/charges", status = 403)
        assertEquals(0, api("athlete", "/api/groups/$group/charges/me").getJSONArray("charges").length())
        val privateCharges = api("other", "/api/groups/$group/charges/me").getJSONArray("charges").objects()
        assertEquals(listOf(data.getString("privateCharge")), privateCharges.map { it.getString("id") })

        openMember(otherId)
        assertBasicProfile(basic)
        ui.onNodeWithText("Jogos:", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Presença:", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Faltas:", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Tentar carregar estatísticas novamente").assertDoesNotExist()
        back()
        back()
        back()
        logout()

        login("owner")
        val authorized = api("owner", statsPath)
        assertEquals(1, authorized.getInt("games"))
        assertEquals(0, authorized.getInt("attendanceRate"))
        assertEquals(1, authorized.getInt("absences"))
        val self = api("owner", "/api/groups/$group/athletes/${actor("athlete").getString("id")}/stats")
        assertEquals(0, self.getInt("games"))
        assertEquals(0, self.getInt("absences"))
        assertTrue(self.isNull("attendanceRate"))
        val ownerBasic = member("owner", otherId)
        assertTrue(ownerBasic.isNull("phone"))
        openMember(otherId, owner = true)
        assertBasicProfile(ownerBasic)
        for (text in listOf(
            "Jogos: ${authorized.getInt("games")}",
            "Presença: ${authorized.getInt("attendanceRate")}%",
            "Faltas: ${authorized.getInt("absences")}",
        )) ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Tentar carregar estatísticas novamente").assertDoesNotExist()
    }

    private fun openMember(id: String, owner: Boolean = false) {
        openGroup()
        if (owner) {
            waitTag("group-details-manage-members")
            val manageMembers = ui.onNodeWithTag("group-details-manage-members")
            // Cashbox and own charges load after the detail and can move this row after scrolling.
            ui.waitUntil(20_000) { manageMembers.performScrollTo().isDisplayed() }
            manageMembers.assertIsDisplayed().assertIsEnabled().performClick()
        } else {
            waitTag("group-details-view-all-members")
            ui.onNode(
                hasClickAction() and hasAnyAncestor(hasTestTag("group-details-view-all-members")),
            ).performScrollTo().performClick()
        }
        click("group-members-member-$id", scroll = true)
        click("group-members-action-ViewProfile")
        waitTag("member-profile")
        waitText("E2E member-privacy-other")
    }

    private fun member(role: String, id: String) = api(role, "/api/groups/$group/athletes?includeInactive=true")
        .getJSONArray("athletes").objects().single { it.getString("userId") == id }

    private fun assertBasicProfile(basic: JSONObject) {
        assertEquals("Par Reservado", basic.getString("nickname"))
        assertEquals("LEVANTADOR", basic.getString("position"))
        assertEquals("LIBERO", basic.getString("secondaryPosition"))
        assertEquals("AVANCADO", basic.getString("level"))
        assertEquals(198, basic.getInt("heightCm"))
        for (text in listOf(basic.getString("displayName"), "Par Reservado", "Levantador", "Líbero", "Avançado", "198 cm")) {
            ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        ui.onNodeWithTag("member-profile-phone").assertDoesNotExist()
        ui.onNodeWithText(data.getString("privatePhone")).assertDoesNotExist()
        ui.onNodeWithText("E2E member-privacy-athlete").assertDoesNotExist()
        ui.onNodeWithText("Atleta Quadra").assertDoesNotExist()
        ui.onNodeWithText("181 cm").assertDoesNotExist()
        ui.onNodeWithTag("group-details-own-charge-${data.getString("privateCharge")}").assertDoesNotExist()
        ui.onNodeWithText("R$\u00A0987,65").assertDoesNotExist()
        ui.onNodeWithText("Minhas cobranças").assertDoesNotExist()
    }
}

/** APP-F01/F02 subsets: all 12 months, four statuses, exclusion, empty G2 and payment destination. */
@RunWith(AndroidJUnit4::class)
internal class MonthlyHistoryE2eTest : ProfileFlowE2e("monthly-history") {
    @Test
    fun twelveMonthlyChargesRemainReadableWithoutOtherMembersOrGameCharges() {
        login("athlete")
        val expected = data.getJSONArray("monthlyCharges").objects()
        val before = charges("athlete", group)
        assertOwnHistory(expected, before)
        api("athlete", "/api/groups/$group/charges", status = 403)
        val organizerBefore = charges("owner", group, organizer = true)
        assertEquals(14, organizerBefore.size)
        val excludedOther = organizerBefore.single { it.getString("id") == data.getString("otherCharge") }
        assertEquals(actor("other").getString("id"), excludedOther.getString("memberId"))
        assertEquals(98765L, excludedOther.getLong("amountCents"))
        assertEquals(0, charges("athlete", secondGroup).size)
        tab("Perfil")
        scrollIn("own-profile", "own-profile-monthly-payments")
        click("own-profile-monthly-payments")
        waitTag("own-monthly-payments")
        for (charge in expected) assertMonthlyRow(charge)

        val firstTag = "group-details-own-charge-${expected.first().getString("id")}"
        scrollIn("own-monthly-payments", firstTag)
        val monthlyRow = SemanticsMatcher("monthly charge row") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("group-details-own-charge-")
        }
        val renderedIds = ui.onAllNodes(monthlyRow).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(expected.map { "group-details-own-charge-${it.getString("id")}" }.toSet(), renderedIds.toSet())
        assertEquals(12, renderedIds.size)
        assertExcludedRows()

        val secondButton = "monthly-payments-group-$secondGroup"
        scrollIn("own-monthly-payments", secondButton)
        ui.onNode(hasText("Nenhuma mensalidade registrada.") and hasAnySibling(hasTestTag(secondButton)))
            .assertIsDisplayed()
        ui.onNode(hasText("Praia monthly-history") and hasAnySibling(hasTestTag(secondButton))).assertIsDisplayed()
        assertExcludedRows()
        assertEquals(before.map { it.fields() }, charges("athlete", group).map { it.fields() })
        assertEquals(organizerBefore.map { it.fields() }, charges("owner", group, organizer = true).map { it.fields() })

        val firstButton = "monthly-payments-group-$group"
        scrollIn("own-monthly-payments", firstButton)
        click(firstButton)
        waitTag("group-details")
        waitTag("group-details-own-charges-pix")
        ui.onNodeWithText(data.getString("groupPix")).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(data.getString("secondGroupPix")).assertDoesNotExist()
        val destination = api("athlete", "/api/groups/$group")
        assertEquals(group, destination.getString("id"))
        assertEquals(data.getString("groupPix"), destination.getJSONObject("profile").getString("pixKey"))
        assertEquals(0, charges("athlete", secondGroup).size)
    }

    private fun assertOwnHistory(expected: List<JSONObject>, actual: List<JSONObject>) {
        assertEquals(12, expected.size)
        assertEquals(13, actual.size)
        assertTrue(actual.all {
            it.getString("memberId") == actor("athlete").getString("id") && it.getString("groupId") == group
        })
        val monthly = actual.filter { it.getString("kind") == "MONTHLY" }.associateBy { it.getString("id") }
        assertEquals(expected.map { it.getString("id") }.toSet(), monthly.keys)
        assertEquals(setOf("PENDING", "PAID", "WAIVED", "CANCELLED"), monthly.values.map { it.getString("status") }.toSet())
        for (charge in expected) {
            val saved = monthly.getValue(charge.getString("id"))
            for (key in listOf("month", "dueDate", "amountCents", "status")) {
                assertEquals("${charge.getString("id")} $key", charge.get(key), saved.get(key))
            }
        }
        val game = actual.single { it.getString("kind") == "GAME" }
        assertEquals(data.getString("gameCharge"), game.getString("id"))
        assertEquals(65432L, game.getLong("amountCents"))
        assertTrue(actual.none { it.getString("id") == data.getString("otherCharge") })
    }

    private fun assertMonthlyRow(charge: JSONObject) {
        val tag = "group-details-own-charge-${charge.getString("id")}"
        scrollIn("own-monthly-payments", tag)
        val status = when (charge.getString("status")) {
            "PENDING" -> "Em aberto"
            "PAID" -> "Paga"
            "WAIVED" -> "Isenta"
            "CANCELLED" -> "Cancelada"
            else -> error("Unexpected fixture status")
        }
        val title = "Mensalidade · ${charge.getString("month").split('-').reversed().joinToString("/")}"
        val due = "Vencimento ${charge.getString("dueDate").split('-').reversed().joinToString("/")}"
        val cents = charge.getLong("amountCents")
        for (text in listOf(title, due, "R$\u00A0${cents / 100},${(cents % 100).toString().padStart(2, '0')}", status)) {
            ui.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun assertExcludedRows() {
        for (key in listOf("otherCharge", "gameCharge")) {
            ui.onNodeWithTag("group-details-own-charge-${data.getString(key)}").assertDoesNotExist()
        }
        ui.onNodeWithText("Jogo avulso").assertDoesNotExist()
        ui.onNodeWithText("R$\u00A0987,65").assertDoesNotExist()
        ui.onNodeWithText("R$\u00A0654,32").assertDoesNotExist()
    }

    private fun charges(role: String, id: String, organizer: Boolean = false) =
        api(role, "/api/groups/$id/charges${if (organizer) "" else "/me"}").getJSONArray("charges").objects()
}

internal abstract class ProfileFlowE2e(scenario: String) : InstalledE2e(scenario) {
    protected fun scrollIn(screen: String, tag: String) {
        // OwnProfile keeps its LazyColumn while loading; its existence alone is not readiness.
        val scrollable = hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(screen)) and
            !hasAnyDescendant(hasTestTag("$screen-loading"))
        ui.waitUntil(20_000) {
            ui.onAllNodes(scrollable).fetchSemanticsNodes().size == 1
        }
        ui.onNode(scrollable).performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).assertIsDisplayed()
    }

    protected fun fieldText(tag: String, expected: String) {
        ui.onNode(hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))), useUnmergedTree = true)
            .assertTextEquals(expected)
    }

    protected fun memberships(role: String): Map<String, JSONObject> {
        val profile = api(role, "/api/athletes/me")
        assertEquals(actor(role).getString("id"), profile.getString("userId"))
        return profile.getJSONArray("memberships").objects().associateBy { it.getString("groupId") }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

private fun JSONObject.fields(): Map<String, String> = keys().asSequence().associateWith { get(it).toString() }
