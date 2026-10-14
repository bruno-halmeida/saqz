package br.com.saqz.sharedkernel.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClientAppTest {
    @Test
    fun `the three headers identify the platform version and build`() {
        val app = ClientApp.fromHeaders("ios", "1.0.3", "14")

        assertEquals(ClientApp.Identified(ClientPlatform.IOS, AppVersion(1, 0, 3), 14), app)
        assertEquals("ios/1.0.3(14)", app.label)
        assertFalse(app.isLegacy)
    }

    @Test
    fun `a missing build keeps the platform and version`() {
        val app = ClientApp.fromHeaders("web", "1.0.3", null)

        assertEquals(ClientApp.Identified(ClientPlatform.WEB, AppVersion(1, 0, 3), null), app)
        assertEquals("web/1.0.3", app.label)
    }

    @Test
    fun `platform alone is enough to identify the app`() {
        val app = ClientApp.fromHeaders("android", null, null)

        assertEquals(ClientApp.Identified(ClientPlatform.ANDROID), app)
        assertEquals("android", app.label)
    }

    @Test
    fun `no header at all is the legacy client`() {
        val app = ClientApp.fromHeaders(null, null, null)

        assertSame(ClientApp.Legacy, app)
        assertEquals("-", app.label)
        assertTrue(app.isLegacy)
    }

    @Test
    fun `an unknown platform makes the call legacy even with a valid version`() {
        listOf("windows", "", " ", "iOS 17", "ios;android", "androidx").forEach { platform ->
            assertSame(ClientApp.Legacy, ClientApp.fromHeaders(platform, "1.0.3", "14"), platform)
        }
    }

    @Test
    fun `platform is accepted regardless of case and surrounding spaces`() {
        assertEquals(ClientPlatform.IOS, (ClientApp.fromHeaders(" IOS ", null, null) as ClientApp.Identified).platform)
        assertEquals(ClientPlatform.ANDROID, (ClientApp.fromHeaders("Android", null, null) as ClientApp.Identified).platform)
    }

    @Test
    fun `a version outside X dot Y dot Z is dropped and the platform survives`() {
        listOf(
            "web", "web (dev)", "1.0", "1", "1.0.3.4", "v1.0.3", "1.0.3-beta", "1.0.3 ", " 1.0.3", "", "1..3",
            "1.0.x", "-1.0.3", "1.0.3\n", "１.０.３", "99999999999.0.0", "1.0.3" + "0".repeat(40),
        ).forEach { version ->
            val app = ClientApp.fromHeaders("android", version, "7")

            assertEquals(ClientApp.Identified(ClientPlatform.ANDROID, null, 7), app, "version=[$version]")
        }
    }

    @Test
    fun `a version longer than 32 characters is dropped even when it matches the pattern`() {
        val tooLong = "1." + "0".repeat(30) + ".3"
        assertTrue(tooLong.length > AppVersion.MAX_LENGTH)
        assertNull(AppVersion.parse(tooLong))

        val limit = "1." + "0".repeat(AppVersion.MAX_LENGTH - 4) + ".3"
        assertEquals(AppVersion.MAX_LENGTH, limit.length)
        assertEquals(AppVersion(1, 0, 3), AppVersion.parse(limit))
    }

    @Test
    fun `a build that is not only digits is dropped`() {
        listOf("", "abc", "14a", "1.4", "-1", " 14", "14 ", "+14", "1234567890", "٣٤").forEach { build ->
            val app = ClientApp.fromHeaders("ios", "1.0.3", build)

            assertEquals(ClientApp.Identified(ClientPlatform.IOS, AppVersion(1, 0, 3), null), app, "build=[$build]")
        }
    }

    @Test
    fun `a build with up to nine digits is kept`() {
        assertEquals(0, (ClientApp.fromHeaders("ios", null, "0") as ClientApp.Identified).build)
        assertEquals(14, (ClientApp.fromHeaders("ios", null, "014") as ClientApp.Identified).build)
        assertEquals(999_999_999, (ClientApp.fromHeaders("ios", null, "999999999") as ClientApp.Identified).build)
    }

    @Test
    fun `a build without a version is still logged`() {
        assertEquals("ios(14)", ClientApp.fromHeaders("ios", null, "14").label)
    }

    @Test
    fun `versions compare numerically, not as text`() {
        assertTrue(AppVersion(1, 0, 10) > AppVersion(1, 0, 9))
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 99))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertEquals(0, AppVersion(1, 0, 3).compareTo(AppVersion.parse("1.0.3")!!))
        assertEquals("1.0.3", AppVersion(1, 0, 3).toString())
    }

    @Test
    fun `leading zeros are normalized`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("01.002.3"))
        assertEquals("ios/1.2.3(14)", ClientApp.fromHeaders("ios", "01.002.3", "14").label)
    }

    @Test
    fun `parse never throws on hostile input`() {
        listOf(null, "", "\u0000", "..", "9".repeat(5_000), "1.0.3\r\nX-Injected: 1").forEach { raw ->
            assertNull(AppVersion.parse(raw))
        }
    }
}
