package br.com.saqz.groups.presentation.game

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class GameLabelsTest {
    // 04/08/2026 é uma terça-feira.
    private val start = LocalDateTime(2026, 8, 4, 19, 30)
    private val deadline = LocalDateTime(2026, 8, 4, 12, 0)

    @Test
    fun `hero display capitalizes the weekday and uses the h time`() = runTest {
        assertEquals("Terça, 19h30", start.gameHeroDisplay())
    }

    @Test
    fun `hero meta writes the month in lowercase before the place`() = runTest {
        assertEquals("4 de agosto · CERET — Quadra 2", start.gameHeroMeta("CERET — Quadra 2"))
    }

    @Test
    fun `deadline sentence is relative to today in the game zone`() = runTest {
        assertEquals("As confirmações encerram hoje às 12h00.", deadline.gameDeadlineSentence(LocalDate(2026, 8, 4)))
        assertEquals("As confirmações encerram amanhã às 12h00.", deadline.gameDeadlineSentence(LocalDate(2026, 8, 3)))
        assertEquals("As confirmações encerram em 04/08 às 12h00.", deadline.gameDeadlineSentence(LocalDate(2026, 8, 1)))
    }

    @Test
    fun `short deadline and bell reuse the home keys`() = runTest {
        assertEquals("Encerra 04/08 · 12h00", deadline.gameDeadlineShort())
        assertEquals("Avisamos você se abrir vaga até 12h00 de 04/08.", deadline.gameBellLabel())
    }

    @Test
    fun `short month and date labels`() = runTest {
        assertEquals("AGO", gameShortMonthLabel(8))
        assertEquals("04/08", start.date.gameDateLabel())
    }

    @Test
    fun `unknown zone falls back to utc instead of throwing`() {
        assertEquals(TimeZone.UTC, gameTimeZone("Marte/Olympus"))
    }
}
