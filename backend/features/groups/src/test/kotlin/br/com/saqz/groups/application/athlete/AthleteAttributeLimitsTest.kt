package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.domain.group.GroupModality
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Limites exatos dos campos do atleta e a mensagem que cada recusa devolve ao app. */
class AthleteAttributeLimitsTest {
    private fun validate(nickname: String? = null, monthlyFeeCents: Long? = null, monthlyDueDay: Int? = null) =
        AthleteAttributeValidator.validate(
            GroupModality.COURT_VOLLEYBALL, nickname, null, null, null, null, null, monthlyFeeCents, monthlyDueDay,
        )

    @Test fun `dia de vencimento da mensalidade vai de 1 a 28`() {
        assertEquals(mapOf("monthlyDueDay" to listOf("must be between 1 and 28")), validate(monthlyDueDay = 0))
        assertTrue(validate(monthlyDueDay = 1).isEmpty())
        assertTrue(validate(monthlyDueDay = 28).isEmpty())
        assertEquals(setOf("monthlyDueDay"), validate(monthlyDueDay = 29).keys)
    }

    @Test fun `mensalidade precisa ser maior que zero`() {
        assertEquals(mapOf("monthlyFeeCents" to listOf("must be greater than 0")), validate(monthlyFeeCents = 0))
        assertEquals(setOf("monthlyFeeCents"), validate(monthlyFeeCents = -1).keys)
        assertTrue(validate(monthlyFeeCents = 1).isEmpty())
    }

    @Test fun `apelido recusado diz o motivo`() {
        assertEquals(mapOf("nickname" to listOf("must not have leading or trailing spaces")), validate(nickname = " Ana"))
        assertEquals(mapOf("nickname" to listOf("must be between 2 and 40 characters")), validate(nickname = "A"))
        assertEquals(mapOf("nickname" to listOf("must be between 2 and 40 characters")), validate(nickname = "a".repeat(41)))
        assertEquals(mapOf("nickname" to listOf("must not contain control characters")), validate(nickname = "A\u0000B"))
        assertEquals(mapOf("nickname" to listOf("must not contain control characters")), validate(nickname = "A\tB"))
    }

    @Test fun `apelido de 2 e de 40 caracteres passa, e espaço no meio também`() {
        assertTrue(validate(nickname = "Al").isEmpty())
        assertTrue(validate(nickname = "a".repeat(40)).isEmpty())
        assertTrue(validate(nickname = "Ana Paula").isEmpty())
    }
}
