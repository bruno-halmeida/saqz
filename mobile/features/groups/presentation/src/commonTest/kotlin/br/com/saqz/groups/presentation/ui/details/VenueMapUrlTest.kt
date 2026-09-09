package br.com.saqz.groups.presentation.ui.details

import kotlin.test.Test
import kotlin.test.assertEquals

class VenueMapUrlTest {
    @Test
    fun addressIsOneEncodedQueryIncludingAccentsAndUrlDelimiters() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=S%C3%A3o%20Paulo%20%26%20Rua%20A%2F2%20%23%201",
            venueMapUrl("São Paulo & Rua A/2 # 1"),
        )
    }
}
