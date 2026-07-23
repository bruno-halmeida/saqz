package br.com.saqz.access.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhoneNumberTest {
    @Test
    fun `normalizes a masked mobile number without country code`() {
        assertEquals("+5511987654321", PhoneNumber.from("(11) 98765-4321").value)
    }

    @Test
    fun `normalizes a masked landline number without country code`() {
        assertEquals("+551134567890", PhoneNumber.from("(11) 3456-7890").value)
    }

    @Test
    fun `normalizes digits only input with explicit plus country code`() {
        assertEquals("+5511987654321", PhoneNumber.from("+55 11 98765-4321").value)
    }

    @Test
    fun `normalizes digits only input with country code and no plus`() {
        assertEquals("+5511987654321", PhoneNumber.from("5511987654321").value)
    }

    @Test
    fun `accepts area code fifty five as a domestic ddd when no country code is present`() {
        assertEquals("+5555991234567", PhoneNumber.from("55991234567").value)
    }

    @Test
    fun `rejects numbers with too few digits`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber.from("987654321") }
    }

    @Test
    fun `rejects numbers with too many digits`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber.from("119876543210123") }
    }

    @Test
    fun `rejects an implausible area code`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber.from("00987654321") }
    }

    @Test
    fun `rejects a non brazilian country code`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber.from("+1 202 555 0123") }
    }

    @Test
    fun `rejects blank input`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber.from("   ") }
    }

    @Test
    fun `toString does not leak the full number`() {
        val phone = PhoneNumber.from("11987654321")
        assertEquals(false, phone.toString().contains("987654321"))
    }
}
