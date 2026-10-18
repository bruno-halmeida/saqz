package br.com.saqz.groups.domain.finance.charge

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Fronteiras exatas do valor da cobrança (R$ 0,01 a R$ 999.999,99, JOGO-008) e da observação da baixa (2 a 500 caracteres). */
class ChargeLimitsTest {
    @Test
    fun `valor vai de 1 centavo a 99999999 centavos e versao comeca em 1`() {
        assertEquals(1L, charge(amount = 1).amountCents)
        assertEquals(99_999_999L, charge(amount = 99_999_999).amountCents)
        assertEquals(1L, charge(version = 1).version)
        assertFailsWith<IllegalArgumentException> { charge(amount = 0) }
        assertFailsWith<IllegalArgumentException> { charge(amount = 100_000_000) }
        assertFailsWith<IllegalArgumentException> { charge(version = 0) }
    }

    @Test
    fun `observacao aceita de 2 a 500 caracteres depois de aparada`() {
        assertEquals("ok", apply("ok").event.note)
        assertEquals("a".repeat(500), apply("a".repeat(500)).event.note)
        assertEquals("ok", apply("  ok  ").event.note)
        assertFailsWith<InvalidChargeNote> { apply("a") }
        assertFailsWith<InvalidChargeNote> { apply(" a ") }
        assertFailsWith<InvalidChargeNote> { apply("a".repeat(501)) }
        assertNull(apply(null).event.note)
    }

    @Test
    fun `desfazer e permitido so de recebida ou dispensada e so sem ordem eletronica`() {
        listOf(ChargeStatus.PAID, ChargeStatus.WAIVED).forEach {
            assertEquals(ChargeStatus.PENDING, undo(charge(status = it)).charge.status)
        }
        listOf(ChargeStatus.PENDING, ChargeStatus.CANCELLED).forEach {
            assertFailsWith<InvalidChargeTransition> { undo(charge(status = it)) }
        }
        assertFailsWith<InvalidChargeTransition> { undo(charge(status = ChargeStatus.PAID).copy(electronicOrder = true)) }
    }

    @Test
    fun `dar baixa so sai de pendente e desfazer ja usa a mesma versao seguinte`() {
        val done = ChargeTransitions.apply(charge(), ChargeStatusCommand(ChargeStatus.PAID), UUID.randomUUID(), NOW, UUID.randomUUID())
        assertEquals(2L, done.charge.version)
        assertEquals(3L, undo(done.charge).charge.version)
        listOf(ChargeStatus.PAID, ChargeStatus.WAIVED, ChargeStatus.CANCELLED).forEach { from ->
            ChargeStatus.entries.filter { it != ChargeStatus.PENDING }.forEach { target ->
                assertFailsWith<InvalidChargeTransition>("$from -> $target") {
                    ChargeTransitions.apply(charge(status = from), ChargeStatusCommand(target), UUID.randomUUID(), NOW, UUID.randomUUID())
                }
            }
        }
    }

    private fun apply(note: String?) =
        ChargeTransitions.apply(charge(), ChargeStatusCommand(ChargeStatus.PAID, note), UUID.randomUUID(), NOW, UUID.randomUUID())

    private fun undo(charge: Charge) =
        ChargeTransitions.apply(charge, ChargeStatusCommand(ChargeStatus.PENDING), UUID.randomUUID(), NOW, UUID.randomUUID())

    private fun charge(amount: Long = 2500, version: Long = 1, status: ChargeStatus = ChargeStatus.PENDING) =
        Charge(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ChargeIdentity.Game(UUID.randomUUID()), amount, LocalDate.of(2026, 8, 10), status, version)

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z")
    }
}
