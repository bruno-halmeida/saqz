package br.com.saqz.groups.presentation.attendance

import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.PositionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PositionSuggestionTest {
    private val positions = listOf(
        PositionSummary(AthletePosition.LIBERO, 0, 0, 0, 0),
        PositionSummary(AthletePosition.PONTA, 4, 4, 0, 1),
        PositionSummary(AthletePosition.CENTRAL, 4, 3, 1, 0),
    )

    @Test fun profilePositionOfferedByTheGameIsTheSuggestion() {
        assertEquals(AthletePosition.CENTRAL, PositionSuggestion.suggested(AthletePosition.CENTRAL, positions))
        assertEquals(AthletePosition.PONTA, PositionSuggestion.suggested(AthletePosition.PONTA, positions))
    }

    @Test fun positionWithoutSlotsOrNoProfileIsNoSuggestion() {
        assertNull(PositionSuggestion.suggested(AthletePosition.LIBERO, positions))
        assertNull(PositionSuggestion.suggested(AthletePosition.OPOSTO, positions))
        assertNull(PositionSuggestion.suggested(null, positions))
    }

    @Test fun onlyASuggestionWithSpotsComesSelected() {
        assertEquals(AthletePosition.CENTRAL, PositionSuggestion.initial(AthletePosition.CENTRAL, positions))
        assertNull(PositionSuggestion.initial(AthletePosition.PONTA, positions))
        assertNull(PositionSuggestion.initial(null, positions))
    }
}
