package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import br.com.saqz.groups.domain.group.GroupModality
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AthleteAttributeValidatorTest {
    @Test
    fun `court accepts position secondary position level and height`() {
        val errors = validate(
            modality = GroupModality.COURT_VOLLEYBALL,
            position = AthletePosition.PONTA,
            secondaryPosition = AthletePosition.CENTRAL,
            level = AthleteLevel.AVANCADO,
            heightCm = 250,
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `beach and footvolley accept preferred side`() {
        listOf(GroupModality.BEACH_VOLLEYBALL, GroupModality.FOOTVOLLEY).forEach { modality ->
            assertTrue(
                validate(modality = modality, preferredSide = AthletePreferredSide.TANTO_FAZ).isEmpty(),
            )
        }
    }

    @Test
    fun `modality rejects conditional attributes with a field map`() {
        val errors = validate(
            modality = GroupModality.BEACH_VOLLEYBALL,
            position = AthletePosition.PONTA,
            secondaryPosition = AthletePosition.CENTRAL,
            heightCm = 180,
        )

        assertEquals(setOf("position", "secondaryPosition", "heightCm"), errors.keys)
    }

    @Test
    fun `undefined modality rejects every conditional attribute`() {
        val errors = validate(
            modality = null,
            position = AthletePosition.PONTA,
            secondaryPosition = AthletePosition.CENTRAL,
            preferredSide = AthletePreferredSide.DIREITA,
            heightCm = 180,
        )

        assertEquals(setOf("position", "secondaryPosition", "preferredSide", "heightCm"), errors.keys)
    }

    @Test
    fun `secondary position must differ from primary position`() {
        val errors = validate(
            modality = GroupModality.COURT_VOLLEYBALL,
            position = AthletePosition.CENTRAL,
            secondaryPosition = AthletePosition.CENTRAL,
        )

        assertEquals(setOf("secondaryPosition"), errors.keys)
    }

    @Test
    fun `mirrors database bounds for height fee and due day`() {
        assertTrue(validate(modality = GroupModality.COURT_VOLLEYBALL, heightCm = 100).isEmpty())
        assertTrue(validate(modality = GroupModality.COURT_VOLLEYBALL, heightCm = 250).isEmpty())
        val errors = validate(
            modality = GroupModality.COURT_VOLLEYBALL,
            heightCm = 251,
            monthlyFeeCents = 0,
            monthlyDueDay = 29,
        )

        assertEquals(setOf("heightCm", "monthlyFeeCents", "monthlyDueDay"), errors.keys)
    }

    @Test
    fun `nickname counts code points and allows emoji plus combining characters`() {
        assertTrue(validate(nickname = "😀a").isEmpty())
        assertTrue(validate(nickname = "a\u0301").isEmpty())
        assertTrue(validate(nickname = "😀".repeat(40)).isEmpty())
        assertEquals(setOf("nickname"), validate(nickname = "😀".repeat(41)).keys)
        assertEquals(setOf("nickname"), validate(nickname = "😀").keys)
    }

    @Test
    fun `nickname mirrors trimming control and length checks`() {
        listOf(" a", "a ", "a\n", "a\u0000", "a", "a".repeat(41)).forEach { nickname ->
            assertEquals(setOf("nickname"), validate(nickname = nickname).keys, nickname)
        }
    }

    private fun validate(
        modality: GroupModality? = GroupModality.COURT_VOLLEYBALL,
        nickname: String? = null,
        position: AthletePosition? = null,
        secondaryPosition: AthletePosition? = null,
        level: AthleteLevel? = null,
        preferredSide: AthletePreferredSide? = null,
        heightCm: Int? = null,
        monthlyFeeCents: Long? = null,
        monthlyDueDay: Int? = null,
    ) = AthleteAttributeValidator.validate(
        modality,
        nickname,
        position,
        secondaryPosition,
        level,
        preferredSide,
        heightCm,
        monthlyFeeCents,
        monthlyDueDay,
    )
}
