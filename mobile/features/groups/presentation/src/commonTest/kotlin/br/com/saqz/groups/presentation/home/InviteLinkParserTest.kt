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
