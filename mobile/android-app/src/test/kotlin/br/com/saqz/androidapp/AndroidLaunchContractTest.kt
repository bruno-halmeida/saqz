package br.com.saqz.androidapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Contract over the native launch resources: the launch screen is a system window
// (core-splashscreen on 23-30, platform splash on 31+), never a Compose screen.
// Parses the real module XML so a regression in theme/manifest/color/icon fails here.
class AndroidLaunchContractTest {
    private val moduleRoot: File = run {
        var dir = File(System.getProperty("user.dir")!!)
        while (!File(dir, "src/main/AndroidManifest.xml").isFile) {
            dir = dir.parentFile ?: error("android-app module root not found")
        }
        dir
    }

    private fun parse(relativePath: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newDocumentBuilder().parse(File(moduleRoot, relativePath)).documentElement
    }

    private fun styleItems(resources: Element, styleName: String): Map<String, String> {
        val styles = resources.getElementsByTagName("style")
        for (i in 0 until styles.length) {
            val style = styles.item(i) as Element
            if (style.getAttribute("name") == styleName) {
                val items = style.getElementsByTagName("item")
                return buildMap {
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        put(item.getAttribute("name"), item.textContent.trim())
                    }
                }
            }
        }
        error("style $styleName not found")
    }

    private fun styleParent(resources: Element, styleName: String): String {
        val styles = resources.getElementsByTagName("style")
        for (i in 0 until styles.length) {
            val style = styles.item(i) as Element
            if (style.getAttribute("name") == styleName) return style.getAttribute("parent")
        }
        error("style $styleName not found")
    }

    @Test
    fun manifestUsesStartingTheme() {
        val manifest = parse("src/main/AndroidManifest.xml")
        val activity = manifest.getElementsByTagName("activity").item(0) as Element
        assertEquals("@style/Theme.Saqz.Starting", activity.getAttribute("android:theme"))
    }

    @Test
    fun legacyThemeUsesCoreSplash() {
        val resources = parse("src/main/res/values/themes.xml")
        assertEquals("Theme.SplashScreen", styleParent(resources, "Theme.Saqz.Starting"))
        val items = styleItems(resources, "Theme.Saqz.Starting")
        assertEquals("@color/saqz_launch_background", items["windowSplashScreenBackground"])
        assertEquals("@style/Theme.Saqz.App", items["postSplashScreenTheme"])
    }

    @Test
    fun v31ThemeUsesSystemSplash() {
        val resources = parse("src/main/res/values-v31/themes.xml")
        val items = styleItems(resources, "Theme.Saqz.Starting")
        assertEquals("@color/saqz_launch_background", items["android:windowSplashScreenBackground"])
        assertEquals("@style/Theme.Saqz.App", items["postSplashScreenTheme"])
    }

    @Test
    fun backgroundMatchesSpec() {
        val resources = parse("src/main/res/values/colors.xml")
        val colors = resources.getElementsByTagName("color")
        var background: String? = null
        for (i in 0 until colors.length) {
            val color = colors.item(i) as Element
            if (color.getAttribute("name") == "saqz_launch_background") background = color.textContent.trim()
        }
        assertEquals("#F5F5F7", background)
    }

    @Test
    fun symbolIsLocal() {
        val drawable = File(moduleRoot, "src/main/res/drawable/saqz_launch_symbol.xml")
        assertTrue("launch symbol drawable must be a local resource", drawable.isFile)
        val body = drawable.readText()
        assertTrue("launch symbol must be a local vector", body.contains("<vector"))
        val legacy = styleItems(parse("src/main/res/values/themes.xml"), "Theme.Saqz.Starting")
        assertEquals("@drawable/saqz_launch_symbol", legacy["windowSplashScreenAnimatedIcon"])
    }
}
