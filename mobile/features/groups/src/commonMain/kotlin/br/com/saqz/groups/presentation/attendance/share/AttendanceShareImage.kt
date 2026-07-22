package br.com.saqz.groups.presentation.attendance.share

import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage as DomainAttendanceShareImage
import br.com.saqz.groups.domain.attendance.share.AttendanceShareImageSection
import br.com.saqz.groups.domain.attendance.share.AttendanceSharePerson
import br.com.saqz.groups.domain.attendance.share.AttendanceShareSnapshot
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

typealias AttendanceShareImageModel = DomainAttendanceShareImage

object AttendanceShareImage {
    fun from(snapshot: AttendanceShareSnapshot): AttendanceShareImageModel {
        val localDateTime = Instant.parse(snapshot.startsAt)
            .toLocalDateTime(TimeZone.of(snapshot.timeZone))
        val sections = listOf(
            section("Confirmados", snapshot.confirmed, "Ninguem confirmado"),
            section("Lista de espera", snapshot.waitlisted, "Ninguem na fila"),
            section("Fora", snapshot.declined, "Ninguem fora"),
        )
        return AttendanceShareImageModel(
            title = snapshot.title,
            scheduleLine = buildString {
                append(localDateTime.dayOfMonth.toString().padStart(2, '0'))
                append('/')
                append(localDateTime.monthNumber.toString().padStart(2, '0'))
                append('/')
                append(localDateTime.year)
                append(" às ")
                append(localDateTime.hour.toString().padStart(2, '0'))
                append(':')
                append(localDateTime.minute.toString().padStart(2, '0'))
            },
            venueLine = snapshot.venue,
            capacityLine = "Capacidade: ${snapshot.capacity}",
            sections = sections,
            heightUnits = HEADER_UNITS + sections.sumOf(::sectionHeight),
        )
    }

    private fun section(
        title: String,
        people: List<AttendanceSharePerson>,
        emptyLabel: String,
    ): AttendanceShareImageSection = AttendanceShareImageSection(
        title = title,
        countLabel = people.size.toString(),
        emptyLabel = emptyLabel,
        entries = if (people.isEmpty()) emptyList() else people.map(::entryLabel),
    )

    private fun entryLabel(person: AttendanceSharePerson): String =
        if (person.waitlistPosition == null) person.displayName else "${person.waitlistPosition}. ${person.displayName}"

    private fun sectionHeight(section: AttendanceShareImageSection): Int =
        SECTION_HEADER_UNITS + if (section.entries.isEmpty()) 1 else section.entries.sumOf(::wrappedRows)

    private fun wrappedRows(value: String): Int = ((value.length.coerceAtLeast(1) - 1) / MAX_ROW_CHARACTERS) + 1

    private const val HEADER_UNITS = 6
    private const val SECTION_HEADER_UNITS = 2
    private const val MAX_ROW_CHARACTERS = 28
}
