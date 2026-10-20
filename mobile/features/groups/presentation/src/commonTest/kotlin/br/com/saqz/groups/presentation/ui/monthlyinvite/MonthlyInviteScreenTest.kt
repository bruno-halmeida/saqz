package br.com.saqz.groups.presentation.ui.monthlyinvite

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.membership.MonthlyFeeDefault
import br.com.saqz.groups.domain.membership.MonthlyInvitePlanLimit
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteIntent
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteMatch
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInvitePersonUi
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInvitePhoneError
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteSheet
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MonthlyInviteScreenTest {
    private val ready = MonthlyInviteState(
        isLoading = false,
        groupName = "Vôlei da Quinta",
        senderName = "Lucas",
        defaultFee = MonthlyFeeDefault(8_000, 10),
    )
    private val three = listOf(
        MonthlyInvitePersonUi("Larissa Prado", "11911122233"),
        MonthlyInvitePersonUi("Diego Farias", "11982223344"),
        MonthlyInvitePersonUi("Marcos Vieira", "11973334455"),
    )

    @Test fun `empty list explains why send is off`() = runComposeUiTest {
        content(ready)
        onNodeWithText("Ninguém na lista ainda").assertExists()
        onNodeWithText("Adicione pelo menos uma pessoa para enviar.").assertExists()
        onNodeWithTag(MonthlyInviteTags.Send).assertIsNotEnabled()
        onNodeWithText("Vence todo dia 10, para todos que entrarem.").assertExists()
    }

    @Test fun `three people show the count on the button and the pace note`() = runComposeUiTest {
        var intent: MonthlyInviteIntent? = null
        content(ready.copy(people = three)) { intent = it }
        onNodeWithText("Enviar 3 convites").assertExists()
        onNodeWithTag(MonthlyInviteTags.Send).assertIsEnabled().performClick()
        assertEquals(MonthlyInviteIntent.Send, intent)
        onNodeWithText("(11) 91112-2233").assertExists()
        onNodeWithText("Os convites saem um por vez, com uns segundos entre eles. 3 pessoas levam menos de 1 min.").assertExists()
    }

    @Test fun `group without fee is blocked with configure`() = runComposeUiTest {
        var intent: MonthlyInviteIntent? = null
        content(ready.copy(defaultFee = null)) { intent = it }
        onNodeWithText("Defina o valor e o dia da mensalidade do grupo").assertExists()
        onNodeWithText("Configurar mensalidade").performClick()
        assertEquals(MonthlyInviteIntent.ConfigureFee, intent)
    }

    @Test fun `plan full and batch over the free slots show their notices`() = runComposeUiTest {
        content(ready.copy(people = three.take(2), planLimit = MonthlyInvitePlanLimit(10, 10, 2, 0)))
        onNodeWithText("Seu grupo chegou ao limite de pessoas").assertExists()
        onNodeWithText("O plano atual comporta 10 atletas e você já tem 10. Para colocar mais gente, faça o upgrade do plano.").assertExists()
        onNodeWithText("Faça o upgrade ou libere vagas para enviar.").assertExists()
        onNodeWithTag(MonthlyInviteTags.Plans).assertExists()
    }

    @Test fun `batch bigger than the slots asks to remove people`() = runComposeUiTest {
        val four = three + MonthlyInvitePersonUi("Bianca Torres", "11964445566")
        content(ready.copy(people = four, planLimit = MonthlyInvitePlanLimit(10, 8, 4, 2)))
        onNodeWithText("Faltam vagas para esse lote").assertExists()
        onNodeWithText("Tire 2 pessoas da lista ou faça o upgrade para enviar.").assertExists()
        onNodeWithTag(MonthlyInviteTags.Send).assertIsNotEnabled()
    }

    @Test fun `whatsapp unavailable offers copy message and copy link`() = runComposeUiTest {
        content(ready.copy(whatsAppAvailable = false))
        onNodeWithText("Não dá para enviar pelo Saqz agora").assertExists()
        onNodeWithText("Copiar mensagem").assertExists()
        onNodeWithText("Copiar só o link").assertExists()
    }

    @Test fun `sheet shows the incomplete number error`() = runComposeUiTest {
        content(ready.copy(sheet = MonthlyInviteSheet(name = "Marcos", phone = "119645678", phoneError = MonthlyInvitePhoneError.Incomplete)))
        onNodeWithText("Faltam dígitos. Use o DDD e os 9 dígitos, por exemplo (11) 98765-4321.").assertExists()
    }

    @Test fun `sheet with an avulso turns add into promote`() = runComposeUiTest {
        var intent: MonthlyInviteIntent? = null
        content(ready.copy(sheet = MonthlyInviteSheet(name = "Camila", phone = "11988880002", match = MonthlyInviteMatch.Avulso("Camila Alves", "u2")))) {
            intent = it
        }
        onNodeWithText("Camila Alves já está no grupo como avulso. Se promover, vira mensalista agora e não recebe mensagem.").assertExists()
        onNodeWithTag(MonthlyInviteTags.SheetPromote).performClick()
        assertEquals(MonthlyInviteIntent.Promote, intent)
        onNodeWithTag(MonthlyInviteTags.SheetAddMore).assertDoesNotExist()
    }

    @Test fun `sheet with a mensalista blocks add`() = runComposeUiTest {
        content(ready.copy(sheet = MonthlyInviteSheet(name = "Thiago", phone = "11988880001", match = MonthlyInviteMatch.Mensalista("Thiago Melo"))))
        onNodeWithText("Thiago Melo já é mensalista do grupo. Não precisa de convite.").assertExists()
        onNodeWithTag(MonthlyInviteTags.SheetAdd).assertIsNotEnabled()
    }

    private fun ComposeUiTest.content(state: MonthlyInviteState, onIntent: (MonthlyInviteIntent) -> Unit = {}) = setContent {
        SaqzTheme { MonthlyInviteScreen(state = state, onIntent = onIntent, onBack = {}) }
    }
}
