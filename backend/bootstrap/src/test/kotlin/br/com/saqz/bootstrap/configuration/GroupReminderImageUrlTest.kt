package br.com.saqz.bootstrap.configuration

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GroupReminderImageUrlTest {
    private val default = "https://links.saqz.app/assets/whatsapp-lembrete.jpg"

    @Test fun `unset property derives the image from the links domain without a double slash`() {
        assertEquals(default, groupReminderImageUrl("https://links.saqz.app", null))
        assertEquals(default, groupReminderImageUrl("https://links.saqz.app/", null))
    }

    @Test fun `an explicitly empty property turns the image off`() {
        assertNull(groupReminderImageUrl("https://links.saqz.app", ""))
        assertNull(groupReminderImageUrl("https://links.saqz.app", "  "))
    }

    @Test fun `an explicit https url wins over the links domain`() {
        assertEquals(
            "https://cdn.saqz.app/lembrete.jpg",
            groupReminderImageUrl("https://links.saqz.app", " https://cdn.saqz.app/lembrete.jpg "),
        )
    }

    @Test fun `a non https url is rejected`() {
        assertFailsWith<IllegalArgumentException> { groupReminderImageUrl("https://links.saqz.app", "http://cdn.saqz.app/a.jpg") }
        assertFailsWith<IllegalArgumentException> { groupReminderImageUrl("https://links.saqz.app", "/assets/a.jpg") }
        assertFailsWith<IllegalArgumentException> { groupReminderImageUrl("https://links.saqz.app", "https://user:pw@cdn.saqz.app/a.jpg") }
        assertFailsWith<IllegalArgumentException> { groupReminderImageUrl("http://links.local", null) }
    }
}
