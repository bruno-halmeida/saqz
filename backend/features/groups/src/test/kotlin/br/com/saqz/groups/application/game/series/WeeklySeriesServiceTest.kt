package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Série semanal: quem pode criar e ler, e os jogos que nascem em rascunho no momento da criação. */
class WeeklySeriesServiceTest {
    private val group = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val repository = MemorySeriesRepository()
    private val minted = mutableListOf<UUID>()
    private val ids = GameIdFactory { UUID.randomUUID().also(minted::add) }

    private fun service(
        writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
        horizon: GameCreationHorizon = GameCreationHorizon.Unlimited,
    ) = WeeklySeriesService(repository, ids, SERIES_CLOCK, writeAccess, horizon)

    @Test fun `só dono e admin organizam a série, atleta não e quem é de fora nem vê o grupo`() {
        repository.roles[actor to group] = GroupRole.OWNER
        assertNull(service().authorizeOrganizer(actor, group))
        repository.roles[actor to group] = GroupRole.ADMIN
        assertNull(service().authorizeOrganizer(actor, group))
        repository.roles[actor to group] = GroupRole.ATHLETE
        assertEquals(WeeklySeriesResult.Forbidden, service().authorizeOrganizer(actor, group))
        repository.roles.clear()
        assertEquals(WeeklySeriesResult.NotFound, service().authorizeOrganizer(actor, group))
    }

    @Test fun `criar série, quem não é do grupo não encontra, atleta é barrado, e nada é gravado`() {
        val rule = seriesRule(group)
        assertEquals(WeeklySeriesResult.NotFound, service().create(actor, rule))
        repository.roles[actor to group] = GroupRole.ATHLETE
        assertEquals(WeeklySeriesResult.Forbidden, service().create(actor, rule))
        assertTrue(repository.created.isEmpty())
    }

    @Test fun `grupo sem assinatura ativa não cria série`() {
        repository.roles[actor to group] = GroupRole.OWNER
        val blocked = GroupWriteAccess { false }
        assertFailsWith<SubscriptionRequiredException> { service(writeAccess = blocked).create(actor, seriesRule(group)) }
        assertTrue(repository.created.isEmpty())
    }

    @Test fun `regra inválida volta com os erros e não grava`() {
        repository.roles[actor to group] = GroupRole.OWNER
        val result = service().create(actor, seriesRule(group, slots = emptyList()))
        assertEquals(listOf("slots"), assertIs<WeeklySeriesResult.Invalid>(result).errors.map { it.field })
        assertTrue(repository.created.isEmpty())
    }

    @Test fun `série criada nasce com 12 semanas de rascunhos futuros, cada um com id novo`() {
        repository.roles[actor to group] = GroupRole.ADMIN
        val rule = seriesRule(group)
        val result = assertIs<WeeklySeriesResult.Success>(service().create(actor, rule))
        assertEquals(false, result.replay)
        assertEquals(rule, result.series.rule)
        val (savedRule, occurrences, adopted) = repository.created.single()
        assertEquals(rule, savedRule)
        assertTrue(adopted.isEmpty())
        assertEquals(12, occurrences.size)
        assertEquals(minted, occurrences.map { it.id })
        assertEquals(setOf(GameStatus.DRAFT), occurrences.map { it.status }.toSet())
        assertEquals(setOf(SERIES_NOW), occurrences.map { it.createdAt }.toSet())
        assertEquals(Instant.parse("2026-01-07T22:30:00Z"), occurrences.first().occurrence.startsAt)
    }

    @Test fun `o horizonte do plano limita quantos jogos nascem, e sem plano nenhum nasce`() {
        repository.roles[actor to group] = GroupRole.OWNER
        // até 15/01 12:00Z: entram as quartas de 07/01 e 14/01
        service(horizon = { Instant.parse("2026-01-15T12:00:00Z") }).create(actor, seriesRule(group))
        assertEquals(2, repository.created.last().second.size)
        service(horizon = { null }).create(actor, seriesRule(group))
        assertEquals(0, repository.created.last().second.size)
    }

    @Test fun `pedido repetido da mesma série devolve o que existe, marcado como repetição`() {
        repository.roles[actor to group] = GroupRole.OWNER
        repository.createReturns = false
        assertEquals(true, assertIs<WeeklySeriesResult.Success>(service().create(actor, seriesRule(group))).replay)
    }

    @Test fun `ler série, de fora do grupo ou série que não existe não é encontrada, senão volta a série`() {
        val rule = seriesRule(group)
        assertEquals(WeeklySeriesResult.NotFound, service().read(actor, group, rule.seriesId))
        repository.roles[actor to group] = GroupRole.ATHLETE
        assertEquals(WeeklySeriesResult.NotFound, service().read(actor, group, rule.seriesId))
        val view = WeeklySeriesView(rule, 3, 7, emptyList())
        repository.stored[group to rule.seriesId] = view
        assertEquals(WeeklySeriesResult.Success(view), service().read(actor, group, rule.seriesId))
    }
}
