package br.com.saqz.androidapp.groups.attendance.share

import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage
import br.com.saqz.groups.domain.attendance.share.AttendanceShareImageSection
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAttendanceShareLayoutTest {
    @Test fun widthIsFixedAt1080Pixels() {
        val layout = AndroidAttendanceShareLayoutCalculator.calculate(image(heightUnits = 1))
        assertEquals(1080, layout.width)
    }

    @Test fun heightIsHeightUnitsTimesRowHeight() {
        val layout = AndroidAttendanceShareLayoutCalculator.calculate(image(heightUnits = 10))
        assertEquals(560, layout.height)
    }

    @Test fun singleHeightUnitProducesOneRowHeight() {
        val layout = AndroidAttendanceShareLayoutCalculator.calculate(image(heightUnits = 1))
        assertEquals(56, layout.height)
    }

    @Test fun zeroHeightUnitsProducesZeroHeightBitmap() {
        val layout = AndroidAttendanceShareLayoutCalculator.calculate(image(heightUnits = 0))
        assertEquals(0, layout.height)
    }

    @Test fun largeHeightUnitsScalesLinearly() {
        val layout = AndroidAttendanceShareLayoutCalculator.calculate(image(heightUnits = 100))
        assertEquals(5600, layout.height)
    }

    @Test fun calculatorUsesConsistentWidthAndRowHeight() {
        assertEquals(1080, AndroidAttendanceShareLayoutCalculator.WIDTH)
        assertEquals(56, AndroidAttendanceShareLayoutCalculator.ROW_HEIGHT)
    }

    private fun image(heightUnits: Int): AttendanceShareImage = AttendanceShareImage(
        title = "Treino",
        scheduleLine = "12/08/2026 às 22:30",
        venueLine = "Arena",
        capacityLine = "Capacidade: 12",
        sections = listOf(
            AttendanceShareImageSection("Confirmados", "0", "Ninguem", emptyList()),
        ),
        heightUnits = heightUnits,
    )
}