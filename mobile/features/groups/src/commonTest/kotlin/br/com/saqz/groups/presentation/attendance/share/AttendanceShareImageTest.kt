package br.com.saqz.groups.presentation.attendance.share

import br.com.saqz.groups.data.attendance.share.AttendanceShareSnapshotDto
import br.com.saqz.groups.data.attendance.share.AttendanceShareSnapshotPersonDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttendanceShareImageTest {
    @Test
    fun `image model keeps exact pt br header and three sections`() {
        val model = AttendanceShareImage.from(snapshot())

        assertEquals("Treino de quinta", model.title)
        assertEquals("12/08/2026 às 19:30", model.scheduleLine)
        assertEquals("Arena Central", model.venueLine)
        assertEquals("Capacidade: 12", model.capacityLine)
        assertEquals(listOf("Confirmados", "Lista de espera", "Fora"), model.sections.map { it.title })
    }

    @Test
    fun `waitlist entries include stable positions`() {
        val model = AttendanceShareImage.from(snapshot())

        assertEquals(listOf("1. Bruno"), model.sections[1].entries)
    }

    @Test
    fun `empty sections stay visible with explicit labels`() {
        val model = AttendanceShareImage.from(snapshot(confirmed = emptyList(), waitlisted = emptyList(), declined = emptyList()))

        assertEquals(emptyList(), model.sections[0].entries)
        assertEquals("Ninguem confirmado", model.sections[0].emptyLabel)
        assertEquals("0", model.sections[0].countLabel)
    }

    @Test
    fun `long and diacritic names increase deterministic height without clipping`() {
        val model = AttendanceShareImage.from(
            snapshot(
                confirmed = listOf(
                    AttendanceShareSnapshotPersonDto("Áurea Fernanda de Albuquerque e Souza"),
                    AttendanceShareSnapshotPersonDto("Áurea Fernanda de Albuquerque e Souza"),
                ),
            ),
        )

        assertTrue(model.heightUnits > 12)
        assertEquals(2, model.sections.first().entries.size)
    }

    private fun snapshot(
        confirmed: List<AttendanceShareSnapshotPersonDto> = listOf(AttendanceShareSnapshotPersonDto("Ana")),
        waitlisted: List<AttendanceShareSnapshotPersonDto> = listOf(AttendanceShareSnapshotPersonDto("Bruno", 1)),
        declined: List<AttendanceShareSnapshotPersonDto> = listOf(AttendanceShareSnapshotPersonDto("Carla")),
    ) = AttendanceShareSnapshotDto(
        title = "Treino de quinta",
        startsAt = "2026-08-12T22:30:00Z",
        timeZone = "America/Sao_Paulo",
        venue = "Arena Central",
        capacity = 12,
        confirmed = confirmed,
        waitlisted = waitlisted,
        declined = declined,
    )
}
