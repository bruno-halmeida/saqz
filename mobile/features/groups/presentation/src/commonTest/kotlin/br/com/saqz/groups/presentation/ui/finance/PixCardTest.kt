package br.com.saqz.groups.presentation.ui.finance

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PixCardTest {
    @Test
    fun cpfShowsMaskedKeyAndTheTypeButCopiesTheDigits() = runComposeUiTest {
        val pix = PixUi(key = "52998224725", label = "CPF")
        setContent { SaqzTheme { PixCard(pix, onCopy = {}, cardTag = "card", copyTag = "copy") } }

        onNodeWithText("529.982.247-25").assertExists()
        onNodeWithText("Chave Pix · CPF").assertExists()
        assertEquals("52998224725", pix.key)
    }

    @Test
    fun phoneShowsMaskedKey() = runComposeUiTest {
        setContent { SaqzTheme { PixCard(PixUi("11987654321", "Telefone"), {}, "card", "copy") } }

        onNodeWithText("(11) 98765-4321").assertExists()
        onNodeWithText("Chave Pix · Telefone").assertExists()
    }

    @Test
    fun legacyFreeTextLabelShowsAsBefore() = runComposeUiTest {
        setContent { SaqzTheme { PixCard(PixUi("chave antiga", "Lucas Prado · Nubank"), {}, "card", "copy") } }

        onNodeWithText("chave antiga").assertExists()
        onNodeWithText("Lucas Prado · Nubank").assertExists()
    }
}
