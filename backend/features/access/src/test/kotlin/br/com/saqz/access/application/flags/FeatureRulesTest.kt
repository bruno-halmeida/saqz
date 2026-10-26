package br.com.saqz.access.application.flags

import br.com.saqz.sharedkernel.client.AppVersion
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureRulesTest {
    private val user = UUID.fromString("5d0f3c1e-8c6a-4f8e-9a51-0b7cbe1c2d01")
    private fun person(
        id: UUID = user,
        plan: AudiencePlan = AudiencePlan.NONE,
        status: AudienceSubscriptionStatus? = null,
        role: AudienceRole = AudienceRole.NONE,
        groups: Set<String> = emptySet(),
    ) = AudienceProfile(id, plan, status, role, groups)
    private fun android(profile: AudienceProfile? = person(), version: String? = "1.0.12") =
        FeatureSubject(FeaturePlatform.ANDROID, version?.let(AppVersion::parse), profile)
    private fun ios(profile: AudienceProfile? = person(), version: String? = "1.0.12") =
        FeatureSubject(FeaturePlatform.IOS, version?.let(AppVersion::parse), profile)
    private val definition = FeatureFlagDefinition(
        key = "teste",
        description = "Flag de teste",
        backendEnforced = false,
        parameters = listOf(
            FeatureParameter("dias", FeatureParameterType.NUMBER, 7L, "Dias", min = 1, max = 60),
            FeatureParameter("paywall", FeatureParameterType.CHOICE, "MENSAL", "Paywall", choices = listOf("MENSAL", "ANUAL")),
        ),
        initialRule = FeatureRule(),
    )

    @Test
    fun `platform switch wins over the always-on list`() {
        val rule = FeatureRule(ios = false, alwaysOn = FeatureMembers(users = listOf(user)), state = FeatureState.ALL)
        assertFalse(FeatureRules.enabled("teste", rule, ios()))
        assertTrue(FeatureRules.enabled("teste", rule, android()))
    }

    @Test
    fun `lists win over the state and always-off wins over always-on through a group`() {
        val off = FeatureRule(state = FeatureState.OFF, alwaysOn = FeatureMembers(groups = listOf("testadores")))
        assertTrue(FeatureRules.enabled("teste", off, android(person(groups = setOf("testadores")))))
        assertFalse(FeatureRules.enabled("teste", off, android(person())))

        val all = FeatureRule(state = FeatureState.ALL, alwaysOff = FeatureMembers(users = listOf(user)))
        assertFalse(FeatureRules.enabled("teste", all, android()))
        assertTrue(FeatureRules.enabled("teste", all, android(person(id = UUID.randomUUID()))))

        val both = FeatureRule(state = FeatureState.ALL, alwaysOn = FeatureMembers(users = listOf(user)),
            alwaysOff = FeatureMembers(groups = listOf("revisores")))
        assertFalse(FeatureRules.enabled("teste", both, android(person(groups = setOf("revisores")))))
    }

    @Test
    fun `audience criteria must all match and empty criteria do not filter`() {
        val owners = FeatureRule(state = FeatureState.AUDIENCE,
            audience = FeatureAudience(roles = listOf(AudienceRole.OWNER), plans = listOf(AudiencePlan.ORGANIZADOR)))
        assertTrue(FeatureRules.enabled("teste", owners, android(person(role = AudienceRole.OWNER, plan = AudiencePlan.ORGANIZADOR))))
        assertFalse(FeatureRules.enabled("teste", owners, android(person(role = AudienceRole.ATHLETE, plan = AudiencePlan.ORGANIZADOR))))
        assertFalse(FeatureRules.enabled("teste", owners, android(person(role = AudienceRole.OWNER, plan = AudiencePlan.TITULAR))))
        assertTrue(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.AUDIENCE), android()))

        val pastDue = FeatureRule(state = FeatureState.AUDIENCE,
            audience = FeatureAudience(subscriptionStatuses = listOf(AudienceSubscriptionStatus.PAST_DUE), platforms = listOf(FeaturePlatform.IOS)))
        assertTrue(FeatureRules.enabled("teste", pastDue, ios(person(status = AudienceSubscriptionStatus.PAST_DUE))))
        assertFalse(FeatureRules.enabled("teste", pastDue, android(person(status = AudienceSubscriptionStatus.PAST_DUE))))
        assertFalse(FeatureRules.enabled("teste", pastDue, ios(person(status = null))))
    }

    @Test
    fun `version range is inclusive and an unknown version never matches`() {
        val rule = FeatureRule(state = FeatureState.AUDIENCE, audience = FeatureAudience(minVersion = "1.0.12", maxVersion = "1.1.0"))
        assertFalse(FeatureRules.enabled("teste", rule, android(version = "1.0.11")))
        assertTrue(FeatureRules.enabled("teste", rule, android(version = "1.0.12")))
        assertTrue(FeatureRules.enabled("teste", rule, android(version = "1.1.0")))
        assertFalse(FeatureRules.enabled("teste", rule, android(version = "1.1.1")))
        assertFalse(FeatureRules.enabled("teste", rule, android(version = null)))
    }

    @Test
    fun `without the platform header platform and version criteria never match and the switch needs both platforms`() {
        val legacy = FeatureSubject(null, null, person())
        assertFalse(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.AUDIENCE,
            audience = FeatureAudience(platforms = listOf(FeaturePlatform.ANDROID))), legacy))
        assertFalse(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.AUDIENCE,
            audience = FeatureAudience(minVersion = "0.0.1")), legacy))
        assertTrue(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.ALL), legacy))
        assertFalse(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.ALL, android = false), legacy))
    }

    @Test
    fun `before login only all, the switch and the version count`() {
        assertTrue(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.ALL), android(profile = null)))
        assertFalse(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.ALL, android = false), android(profile = null)))
        assertFalse(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.AUDIENCE), android(profile = null)))
        assertFalse(FeatureRules.enabled("teste", FeatureRule(state = FeatureState.OFF,
            alwaysOn = FeatureMembers(users = listOf(user))), android(profile = null)))
    }

    @Test
    fun `percentage band is stable, grows without dropping anyone and is per flag`() {
        val people = (1..2000).map { UUID.nameUUIDFromBytes("pessoa-$it".toByteArray()) }
        fun inside(key: String, percentage: Int) = people.filter {
            FeatureRules.enabled(key, FeatureRule(state = FeatureState.AUDIENCE, percentage = percentage), android(person(id = it)))
        }.toSet()
        val ten = inside("chat", 10)
        val thirty = inside("chat", 30)
        assertTrue(thirty.containsAll(ten))
        assertTrue(ten.size in 140..260, "~10% de 2000, veio ${ten.size}")
        assertTrue(thirty.size in 500..700, "~30% de 2000, veio ${thirty.size}")
        assertEquals(ten, inside("chat", 10))
        assertTrue(inside("outra", 10) != ten, "cada flag tem a própria faixa")
        assertEquals(emptySet(), inside("chat", 0))
        assertEquals(people.toSet(), inside("chat", 100))
    }

    @Test
    fun `the same person gets the same result on android and iphone`() {
        val rule = FeatureRule(state = FeatureState.AUDIENCE, percentage = 50)
        (1..200).map { UUID.nameUUIDFromBytes("p$it".toByteArray()) }.forEach {
            assertEquals(FeatureRules.enabled("chat", rule, android(person(id = it))), FeatureRules.enabled("chat", rule, ios(person(id = it))))
        }
    }

    @Test
    fun `values come from the rule only for who receives the flag`() {
        val rule = FeatureRule(state = FeatureState.ALL, android = false, values = mapOf("dias" to 14L))
        assertEquals(FeatureValue(true, mapOf("dias" to 14L, "paywall" to "MENSAL")), FeatureRules.evaluate(definition, rule, ios()))
        assertEquals(FeatureValue(false, mapOf("dias" to 7L, "paywall" to "MENSAL")), FeatureRules.evaluate(definition, rule, android()))
    }

    @Test
    fun `validation refuses bad shapes`() {
        fun refuses(rule: FeatureRule) = assertFailsWith<FeatureFailure> { FeatureRules.validate(definition, rule) }
        refuses(FeatureRule(percentage = 101))
        refuses(FeatureRule(audience = FeatureAudience(minVersion = "1.2")))
        refuses(FeatureRule(audience = FeatureAudience(minVersion = "2.0.0", maxVersion = "1.0.0")))
        refuses(FeatureRule(alwaysOn = FeatureMembers(users = listOf(user)), alwaysOff = FeatureMembers(users = listOf(user))))
        refuses(FeatureRule(alwaysOn = FeatureMembers(groups = listOf("a")), alwaysOff = FeatureMembers(groups = listOf("a"))))
        refuses(FeatureRule(values = mapOf("dias" to 0L)))
        refuses(FeatureRule(values = mapOf("dias" to "7")))
        refuses(FeatureRule(values = mapOf("paywall" to "SEMANAL")))
        refuses(FeatureRule(values = mapOf("outro" to 1L)))
        refuses(FeatureRule(variants = listOf(FeatureVariant("A", 100))))
        FeatureRules.validate(definition, FeatureRule(values = mapOf("dias" to 30L, "paywall" to "ANUAL")))
    }

    @Test
    fun `catalog starts with the values of before the flags`() {
        fun subjects(rule: FeatureRule) = FeatureRules.enabled("k", rule, android()) to FeatureRules.enabled("k", rule, ios())
        val on = FeatureCatalog(whatsAppConfigured = true)
        assertEquals(false to false, subjects(on.find(FeatureKeys.CHAT)!!.initialRule))
        assertEquals(false to false, subjects(on.find(FeatureKeys.PURCHASES_ASAAS)!!.initialRule))
        assertEquals(false to true, subjects(on.find(FeatureKeys.PURCHASES_APP_STORE)!!.initialRule))
        assertEquals(true to false, subjects(on.find(FeatureKeys.PURCHASES_GOOGLE_PLAY)!!.initialRule))
        assertEquals(true to true, subjects(on.find(FeatureKeys.WHATSAPP)!!.initialRule))
        assertEquals(false to false, subjects(FeatureCatalog(whatsAppConfigured = false).find(FeatureKeys.WHATSAPP)!!.initialRule))
        assertTrue(on.find(FeatureKeys.CHAT)!!.backendEnforced && on.find(FeatureKeys.PURCHASES_ASAAS)!!.backendEnforced)
        assertFalse(on.find(FeatureKeys.PURCHASES_APP_STORE)!!.backendEnforced || on.find(FeatureKeys.PURCHASES_GOOGLE_PLAY)!!.backendEnforced)
    }
}
