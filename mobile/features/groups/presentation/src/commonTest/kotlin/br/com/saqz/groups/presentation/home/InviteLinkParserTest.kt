package br.com.saqz.groups.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinkParserTest {
    private val code = "a".repeat(42) + "A"

    @Test
    fun `accepts the production link with the invite parameter`() {
        assertEquals(code, InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=$code"))
    }

    @Test
    fun `accepts the native scheme and ignores other parameters and the fragment`() {
        assertEquals(code, InviteLinkParser.parse("saqz://open?utm=x&saqz_invite=$code&y=1#frag"))
    }

    @Test
    fun `accepts a bare code with surrounding whitespace`() {
        assertEquals(code, InviteLinkParser.parse("  $code\n"))
    }

    @Test
    fun `accepts the short group code in any spelling and normalizes it`() {
        listOf("44DF-8SA8", "44df-8sa8", "44DF8SA8", " 44df 8sa8\n", "44-DF-8S-A8").forEach {
            assertEquals("44DF8SA8", InviteLinkParser.parse(it), it)
        }
    }

    @Test
    fun `accepts the short code inside the link with or without hyphen`() {
        assertEquals("44DF8SA8", InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=44DF8SA8"))
        assertEquals("44DF8SA8", InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=44df-8sa8"))
        assertEquals("44DF8SA8", InviteLinkParser.parse("saqz://open?saqz_invite=44DF-8SA8#frag"))
    }

    @Test
    fun `rejects short codes with ambiguous characters or the wrong size`() {
        listOf("44DF-8SA0", "44DF-8SAO", "44DF-8SA1", "44DF-8SAI", "44DF-8SAL", "44DF-8SA", "44DF-8SA88").forEach {
            assertNull(InviteLinkParser.parse(it), it)
            assertNull(InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=$it"), it)
        }
        assertNull(InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=44DF8SA8&saqz_invite=44DF8SA8"))
    }

    @Test
    fun `shows the short code with a hyphen`() {
        assertEquals("44DF-8SA8", InviteLinkParser.display("44DF8SA8"))
        assertEquals(code, InviteLinkParser.display(code))
    }

    @Test
    fun `rejects empty text links without the parameter and malformed codes`() {
        assertNull(InviteLinkParser.parse(""))
        assertNull(InviteLinkParser.parse("https://saqz.app/"))
        assertNull(InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=curto"))
        assertNull(InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=" + "a".repeat(42) + "B"))
    }

    @Test
    fun `rejects attendance and onboarding links and duplicated codes`() {
        assertNull(InviteLinkParser.parse("https://links.saqz.app/?saqz_attendance=$code"))
        assertNull(InviteLinkParser.parse("https://links.saqz.app/?saqz_onboarding=$code"))
        assertNull(InviteLinkParser.parse("https://links.saqz.app/?saqz_invite=$code&saqz_invite=$code"))
    }
}
