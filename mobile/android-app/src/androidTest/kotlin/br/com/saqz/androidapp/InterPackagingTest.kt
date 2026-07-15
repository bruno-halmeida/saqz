package br.com.saqz.androidapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.test.platform.app.InstrumentationRegistry
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.theme.saqzFontFamily
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.security.MessageDigest

class InterPackagingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // Reads the bytes actually packaged in the APK, not a source copy.
    private fun packagedFont(name: String): ByteArray =
        runBlocking { Res.readBytes("font/$name") }

    private fun packagedFamily(): List<Font> {
        lateinit var fonts: List<Font>
        composeRule.setContent {
            @Suppress("UNCHECKED_CAST")
            fonts = saqzFontFamily() as List<Font>
        }
        composeRule.waitForIdle()
        return fonts
    }

    private fun assertWeightMappedOnce(weight: FontWeight) {
        val fonts = packagedFamily()
        assertEquals(4, fonts.size)
        assertEquals(1, fonts.count { it.weight == weight })
    }

    @Test
    fun fourFilesMatchChecksums() {
        assertEquals(
            "164414f0aacbe98a7e64addc43f7b3bfd2e32f7b90e101feeab227f14c371bda",
            sha256(packagedFont("inter_light.ttf")),
        )
        assertEquals(
            "40d692fce188e4471e2b3cba937be967878f631ad3ebbbdcd587687c7ebe0c82",
            sha256(packagedFont("inter_regular.ttf")),
        )
        assertEquals(
            "78a843fade9d4612a5567302fb595b56976eb5fcebf4fea5a5912d638bafcde3",
            sha256(packagedFont("inter_semibold.ttf")),
        )
        assertEquals(
            "288316099b1e0a47a4716d159098005eef7c0066921f34e3200393dbdb01947f",
            sha256(packagedFont("inter_bold.ttf")),
        )
    }

    @Test
    fun licenseMatchesChecksum() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("Inter-OFL-1.1.txt").use { it.readBytes() }
        assertEquals(
            "262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a",
            sha256(bytes),
        )
    }

    @Test
    fun lightMapsTo300() = assertWeightMappedOnce(FontWeight(300))

    @Test
    fun regularMapsTo400() = assertWeightMappedOnce(FontWeight(400))

    @Test
    fun semiboldMapsTo600() = assertWeightMappedOnce(FontWeight(600))

    @Test
    fun boldMapsTo700() = assertWeightMappedOnce(FontWeight(700))

    @Test
    fun apkRendersAllFourWeights() {
        composeRule.setContent {
            val family = saqzFontFamily()
            Column {
                Text("weight-300", style = TextStyle(fontFamily = family, fontWeight = FontWeight(300)))
                Text("weight-400", style = TextStyle(fontFamily = family, fontWeight = FontWeight(400)))
                Text("weight-600", style = TextStyle(fontFamily = family, fontWeight = FontWeight(600)))
                Text("weight-700", style = TextStyle(fontFamily = family, fontWeight = FontWeight(700)))
            }
        }
        composeRule.onNodeWithText("weight-300").assertIsDisplayed()
        composeRule.onNodeWithText("weight-400").assertIsDisplayed()
        composeRule.onNodeWithText("weight-600").assertIsDisplayed()
        composeRule.onNodeWithText("weight-700").assertIsDisplayed()
    }
}
