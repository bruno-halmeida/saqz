package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.PositionSummary
import br.com.saqz.groups.presentation.ui.details.GroupDetailsPreviewData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-109: a meia quadra do 5-1 no cartão do próximo jogo de quem administra. */
class GroupPositionCourtTest {
    @Test fun frontAndBackFollowTheFiveOneWithTheLiberoInTheBackCentralSpot() {
        val court = GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions())!!
        assertEquals(listOf(AthletePosition.PONTA, AthletePosition.CENTRAL, AthletePosition.OPOSTO), court.front.map { it.position })
        assertEquals(listOf(AthletePosition.PONTA, AthletePosition.LIBERO, AthletePosition.LEVANTADOR), court.back.map { it.position })
    }

    @Test fun withoutLiberoTheBackMiddleIsCentralAgain() {
        val court = GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions(libero = 0))!!
        assertEquals(AthletePosition.CENTRAL, court.back[1].position)
        // A contagem aparece só na primeira vez da posição.
        assertTrue(court.front[1].first)
        assertFalse(court.back[1].first)
        assertFalse(court.back[0].first)
    }

    @Test fun missingAndCompleteness() {
        val court = GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions())!!
        val central = court.front[1]
        assertEquals(1, central.missing)
        assertFalse(central.complete)
        assertTrue(court.front[0].complete)
        assertFalse(court.allComplete)
        assertTrue(GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions(complete = true))!!.allComplete)
    }

    @Test fun positionWithoutSlotsIsShownAsNotOffered() {
        val positions = GroupDetailsPreviewData.courtPositions(libero = 0).map {
            if (it.position == AthletePosition.OPOSTO) PositionSummary(AthletePosition.OPOSTO, 0, 0, 0, 0) else it
        }
        val oposto = GroupPositionCourt.from(positions)!!.front[2]
        assertFalse(oposto.offered)
        assertFalse(oposto.complete)
    }

    @Test fun waitingListsEachPositionWithPeopleInTheCourtOrder() {
        val positions = GroupDetailsPreviewData.courtPositions().map {
            if (it.position == AthletePosition.LEVANTADOR) it.copy(waitlisted = 2) else it
        }
        assertEquals(
            listOf(AthletePosition.PONTA to 1, AthletePosition.LEVANTADOR to 2),
            GroupPositionCourt.from(positions)!!.waiting,
        )
    }

    @Test fun gameWithoutPositionsHasNoCourt() {
        assertNull(GroupPositionCourt.from(emptyList()))
    }

    @Test fun agendaMissingNamesTheOpenPositionsInTheFixedOrderOrAll() {
        val positions = GroupDetailsPreviewData.courtPositions()
        assertEquals(AgendaMissing.Some(listOf(AthletePosition.CENTRAL, AthletePosition.OPOSTO)), agendaMissing(positions))
        assertNull(agendaMissing(GroupDetailsPreviewData.courtPositions(complete = true)))
        val none = positions.map { it.copy(confirmed = 0, missing = it.slots) }
        assertEquals(AgendaMissing.All, agendaMissing(none))
        assertNull(agendaMissing(emptyList()))
    }
}
