package br.com.saqz.groups.application.game.recurrence

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResolver
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResult
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** JOGO-010 e plano: o jogo da série só nasce no futuro e dentro do horizonte do plano, e nasce como rascunho. */
class MaterializeWeeklySeriesHorizonTest {
    private val groupId = UUID.randomUUID()
    private val rule = WeeklySeriesRule(
        groupId, UUID.randomUUID(), UUID.randomUUID(), "America/Sao_Paulo", DATE, slots = listOf(
            WeeklySlotRule(
                UUID.randomUUID(), DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90,
                GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2"), 24, 180, 2500, "Treino semanal",
            ),
        ),
    )
    private val occurrences = assertIs<WeeklyRecurrenceResult.Valid>(WeeklyRecurrenceResolver.resolve(rule, DATE)).occurrences
    private val inserted = mutableListOf<MaterializedGameOccurrence>()

    @Test
    fun `so entra o que comeca depois de agora e antes do limite, os dois extremos ficam de fora`() {
        val third = occurrences[2].startsAt
        val fifth = occurrences[4].startsAt

        val creatable = occurrences.creatableBetween(third, fifth)

        assertEquals(listOf(occurrences[3]), creatable)
        assertEquals(emptyList(), occurrences.creatableBetween(third, third))
        assertEquals(listOf(occurrences[3], occurrences[4]), occurrences.creatableBetween(third, fifth.plusSeconds(1)))
        assertEquals(listOf(occurrences[2], occurrences[3]), occurrences.creatableBetween(third.minusSeconds(1), fifth))
    }

    @Test
    fun `plano sem horizonte nao cria nada nem abre transacao`() {
        var transactions = 0
        val useCase = MaterializeWeeklySeries(
            object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T { transactions++; return block() } },
            { inserted += it; it.size },
            { UUID.randomUUID() },
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
            horizon = GameCreationHorizon { null },
        )

        assertEquals(MaterializeWeeklySeriesResult.Success(0, 0), useCase.execute(rule, DATE))

        assertEquals(0, transactions)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `horizonte curto limita as ocorrencias e cada uma nasce rascunho com o horario do servidor`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val useCase = MaterializeWeeklySeries(
            object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
            { inserted += it; it.size },
            { UUID.randomUUID() },
            Clock.fixed(now, ZoneOffset.UTC),
            horizon = GameCreationHorizon { occurrences[3].startsAt },
        )

        val result = assertIs<MaterializeWeeklySeriesResult.Success>(useCase.execute(rule, DATE))

        assertEquals(3, result.generated)
        assertEquals(3, result.inserted)
        assertEquals(occurrences.take(3), inserted.map { it.occurrence })
        assertTrue(inserted.all { it.status == GameStatus.DRAFT && it.createdAt == now })
        assertEquals(3, inserted.map { it.id }.distinct().size)
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 1, 7)
    }
}
