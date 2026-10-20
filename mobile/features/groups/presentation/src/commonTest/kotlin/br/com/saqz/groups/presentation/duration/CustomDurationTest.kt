package br.com.saqz.groups.presentation.duration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomDurationTest {
    @Test
    fun `a grade anda de 30 em 30 ate 3h e de hora em hora depois ate 8h`() {
        assertEquals(listOf(60, 90, 120, 150, 180, 240, 300, 360, 420, 480), CustomDuration.Grid)
    }

    @Test
    fun `mais e menos andam para o vizinho da grade`() {
        assertEquals(90, CustomDuration.next(60))
        assertEquals(180, CustomDuration.next(150))
        assertEquals(240, CustomDuration.next(180))
        assertEquals(150, CustomDuration.previous(180))
        assertEquals(180, CustomDuration.previous(240))
        assertEquals(60, CustomDuration.previous(90))
    }

    @Test
    fun `nos limites o valor nao sai da grade`() {
        assertEquals(60, CustomDuration.previous(60))
        assertEquals(480, CustomDuration.next(480))
    }

    @Test
    fun `valor salvo fora da grade vai para o vizinho nos dois sentidos`() {
        assertEquals(240, CustomDuration.next(195))
        assertEquals(180, CustomDuration.previous(195))
        assertEquals(60, CustomDuration.next(45))
        assertEquals(480, CustomDuration.previous(540))
    }

    @Test
    fun `de 1h a 8h vale inclusive fora da grade e o resto nao`() {
        listOf(60, 195, 480).forEach { assertFalse(CustomDuration.isInvalid(it), "$it") }
        listOf(0, 45, 481, 540).forEach { assertTrue(CustomDuration.isInvalid(it), "$it") }
    }
}
