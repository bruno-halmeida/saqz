package br.com.saqz.groups.presentation.ui.finance

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.ReviewDecision
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.newentry.NewEntryCategory
import br.com.saqz.groups.presentation.newentry.NewEntryDirection
import br.com.saqz.groups.presentation.newentry.NewEntryScreen
import br.com.saqz.groups.presentation.newentry.NewEntryState
import br.com.saqz.groups.presentation.statement.StatementItemUi
import br.com.saqz.groups.presentation.statement.StatementScreen
import br.com.saqz.groups.presentation.statement.StatementState
import br.com.saqz.groups.presentation.statement.StatementSummaryUi
import br.com.saqz.groups.presentation.ui.details.GroupDetailsPreviewData
import br.com.saqz.groups.presentation.ui.details.GroupDetailsScreen
import br.com.saqz.groups.presentation.ui.finance.groupcash.CashboxTab
import br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxScreen
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxState
import br.com.saqz.groups.presentation.ui.finance.groupcash.LedgerUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.UnchargedPendingUi
import br.com.saqz.groups.presentation.ui.finance.review.ReviewRowUi
import br.com.saqz.groups.presentation.ui.finance.review.UnchargedReviewScreen
import br.com.saqz.groups.presentation.ui.finance.review.UnchargedReviewState
import br.com.saqz.groups.presentation.ui.finance.settlement.GameSettlementDiaristUi
import br.com.saqz.groups.presentation.ui.finance.settlement.GameSettlementHeaderUi
import br.com.saqz.groups.presentation.ui.finance.settlement.GameSettlementScreen
import br.com.saqz.groups.presentation.ui.finance.settlement.GameSettlementState
import br.com.saqz.groups.presentation.ui.finance.settlement.NotPlayedReason
import br.com.saqz.groups.presentation.ui.finance.settlement.UnchargedRowUi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas do caixa novo, da Entrada e Saída, do extrato, do acerto, da revisão da diária retroativa e do Pix na
 * página do grupo (DEB-147, DEB-148), com os dados de exemplo do canvas do financeiro. Saem em
 * `build/screenshots/diaria-caixa/` deste módulo; as de 360 e fonte grande levam o sufixo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class DiariaCaixaScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun caixaFaltaReceber() = capture("caixa-falta-receber") { GroupCashboxScreen(caixa, {}, {}) }

    @Test fun caixaSaiu() = capture("caixa-saiu") { GroupCashboxScreen(caixa.copy(tab = CashboxTab.Out), {}, {}) }

    @Test fun caixaEntrou() = capture("caixa-entrou") { GroupCashboxScreen(caixa.copy(tab = CashboxTab.In), {}, {}) }

    @Test fun caixaMenu() = capture("caixa-menu") { GroupCashboxScreen(caixa.copy(menuOpen = true), {}, {}) }

    @Test fun caixaDevedorMenu() = capture("caixa-devedor-menu") {
        GroupCashboxScreen(caixa.copy(debtorMenuChargeId = "c-camila"), {}, {})
    }

    @Test fun caixaZerado() = capture("caixa-zerado") { GroupCashboxScreen(zerado, {}, {}) }

    @Test fun caixaSaldoInicial() = capture("caixa-saldo-inicial") {
        GroupCashboxScreen(caixa.copy(openingSheetOpen = true, openingInput = "250,00"), {}, {})
    }

    @Test fun caixa360() = capture("caixa-falta-receber-360", narrow = true) { GroupCashboxScreen(caixa, {}, {}) }

    @Test fun caixaFonteGrande() = capture("caixa-falta-receber-fonte-grande", bigFont = true) {
        GroupCashboxScreen(caixa, {}, {})
    }

    @Test fun entrada() = capture("entrada") {
        NewEntryScreen(
            NewEntryState(date = "2026-10-26", today = "2026-10-27", amountText = "120,00",
                description = "Venda de camisas", category = NewEntryCategory.Sale),
            {}, {},
        )
    }

    @Test fun saida() = capture("saida") { NewEntryScreen(saida, {}, {}) }

    @Test fun saida360FonteGrande() = capture("saida-360-fonte-grande", narrow = true, bigFont = true) {
        NewEntryScreen(saida, {}, {})
    }

    @Test fun saidaCalendario() = capture("saida-calendario") { NewEntryScreen(saida.copy(datePickerOpen = true), {}, {}) }

    @Test fun extrato() = capture("extrato") { StatementScreen(extrato, {}, {}) }

    @Test fun extrato360() = capture("extrato-360", narrow = true) { StatementScreen(extrato, {}, {}) }

    @Test fun acerto() = capture("acerto") { GameSettlementScreen(acerto, {}, {}) }

    @Test fun acertoRetroativa() = capture("acerto-retroativa") { GameSettlementScreen(acertoRetro, {}, {}) }

    @Test fun acertoAntecipado() = capture("acerto-antecipado") { GameSettlementScreen(acertoAntecipado, {}, {}) }

    @Test fun acertoFonteGrande() = capture("acerto-fonte-grande", bigFont = true) { GameSettlementScreen(acerto, {}, {}) }

    @Test fun revisao() = capture("revisao") { UnchargedReviewScreen(revisao, {}, {}) }

    @Test fun revisaoMenu() = capture("revisao-menu") { UnchargedReviewScreen(revisao.copy(menuKey = "duda/0"), {}, {}) }

    @Test fun revisao360FonteGrande() = capture("revisao-360-fonte-grande", narrow = true, bigFont = true) {
        UnchargedReviewScreen(revisao, {}, {})
    }

    @Test fun grupoPix() = capture("grupo-pix") { GroupDetailsScreen(grupoPix, {}, {}) }

    private fun capture(
        name: String,
        narrow: Boolean = false,
        bigFont: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (bigFont) BIG_FONT else density.fontScale),
            ) {
                SaqzTheme {
                    Box(
                        (if (narrow) Modifier.fillMaxHeight().width(NARROW) else Modifier.fillMaxSize())
                            .background(SaqzTheme.colors.background),
                    ) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/diaria-caixa/$name.png")
    }

    private companion object {
        const val BIG_FONT = 1.3f
        val NARROW = 360.dp

        fun debtor(id: String, name: String, meta: String, month: String?, cents: Long) = DebtorUi(
            chargeId = id, memberId = id, name = name, dueLabel = "", amountLabel = "", amountCents = cents,
            chargeVersion = 1L, month = month, cashMeta = meta,
        )

        val caixa = GroupCashboxState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            monthKey = "2026-10",
            balanceLabel = "R$ 490,00",
            balanceCents = 49_000L,
            paidMonthlyCount = 9,
            monthlyTotalCount = 12,
            monthInLabel = "R$ 880",
            monthOutLabel = "R$ 640",
            dueLabel = "R$ 280",
            openingBalanceCents = 25_000L,
            openingVersion = "\"2\"",
            openingLoaded = true,
            pix = PixUi("ceret@volei.com.br", "E-mail"),
            uncharged = UnchargedPendingUi(6, 2, "outubro", null, null),
            debtors = listOf(
                debtor("c-camila", "Camila Alves", "Venceu 10/10 · R$ 80", "2026-10", 8_000L),
                debtor("c-pedro", "Pedro Henrique", "Venceu 10/10 · R$ 80", "2026-10", 8_000L),
                debtor("c-thiago", "Thiago Melo", "Venceu 10/10 · R$ 80", "2026-10", 8_000L),
                debtor("c-caio", "Caio Lima", "Terça, 27/10 · R$ 20", null, 2_000L),
                debtor("c-bia", "Bia Souza", "Convite · Júlia · R$ 20", null, 2_000L),
            ),
            monthOut = listOf("27", "20", "13", "06").map {
                LedgerUi("q$it", "Quadra", "Terça, $it/10", "−R$ 160", out = true)
            },
            monthIn = listOf(
                LedgerUi("i1", "Diária · Bia Souza", "Terça, 27/10", "+R$ 20", out = false),
                LedgerUi("i2", "Mensalidade · Igor Santos", "Segunda, 26/10", "+R$ 80", out = false),
                LedgerUi("i3", "Venda de camisas", "Segunda, 26/10", "+R$ 120", out = false),
            ),
        )

        val zerado = GroupCashboxState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            monthKey = "2026-10",
            balanceLabel = "R$ 0,00",
            cashboxEmpty = true,
            openingLoaded = true,
            openingVersion = "\"0\"",
            openingInput = "250,00",
        )

        val saida = NewEntryState(
            date = "2026-10-27",
            direction = NewEntryDirection.Out,
            amountText = "160,00",
            description = "Quadra de terça",
            category = NewEntryCategory.Court,
        )

        val extrato = StatementState(
            isLoading = false,
            summary = StatementSummaryUi(totalInCents = 88_000L, totalOutCents = 64_000L, monthName = "outubro"),
            openingLoaded = true,
            openingBalanceCents = 25_000L,
            openingVersion = "\"2\"",
            items = listOf(
                StatementItemUi("e1", br.com.saqz.groups.domain.finance.FinanceDirection.Out, "Quadra", "Saída · Quadra",
                    "−R$ 160", manual = true, dayKey = "2026-10-27"),
                StatementItemUi("c1", br.com.saqz.groups.domain.finance.FinanceDirection.In, "Diária · Bia Souza", "Pix",
                    "+R$ 20", dayKey = "2026-10-27"),
                StatementItemUi("c2", br.com.saqz.groups.domain.finance.FinanceDirection.In, "Diária · Duda Alves", "Dinheiro",
                    "+R$ 20", dayKey = "2026-10-27"),
                StatementItemUi("c3", br.com.saqz.groups.domain.finance.FinanceDirection.In, "Mensalidade · Igor Santos", "Pix",
                    "+R$ 80", dayKey = "2026-10-26"),
                StatementItemUi("e2", br.com.saqz.groups.domain.finance.FinanceDirection.In, "Venda de camisas", "Entrada · Venda",
                    "+R$ 120", manual = true, dayKey = "2026-10-26"),
            ),
        )

        fun diary(id: String, name: String, status: ChargeStatus, meta: String) = GameSettlementDiaristUi(
            chargeId = id, memberId = id, name = name, meta = meta, amountLabel = "R$ 20,00", amountCents = 2_000L,
            dueDate = "2026-10-27", chargeVersion = 1L, status = status, referenceLabel = "", cashMeta = meta,
        )

        val header = GameSettlementHeaderUi(
            "27/10/2026 · 19h30", "CERET — Quadra 2", 12,
            title = "Terça, 27/10", meta = "19h30 · CERET — Quadra 2", completed = true,
        )

        val acerto = GameSettlementState(
            isLoading = false,
            header = header,
            monthlyMemberCount = 8,
            diarists = listOf(
                diary("bia", "Bia Souza", ChargeStatus.Paid, "Avulso · Pix"),
                diary("julia", "Júlia Rocha", ChargeStatus.Pending, "Convite · paga Bia"),
                diary("caio", "Caio Lima", ChargeStatus.Pending, "Avulso"),
                diary("duda", "Duda Alves", ChargeStatus.Paid, "Avulso · Dinheiro"),
            ),
            debtors = listOf(
                debtor("julia", "Júlia Rocha", "", null, 2_000L),
                debtor("caio", "Caio Lima", "", null, 2_000L),
            ),
        )

        val acertoRetro = GameSettlementState(
            isLoading = false,
            header = header.copy(title = "Terça, 20/10", playersCount = 11),
            monthlyMemberCount = 7,
            gameHadNoFee = true,
            uncharged = listOf(
                UnchargedRowUi("caio", "Caio Lima", "Avulso", noShow = false, skipped = false),
                UnchargedRowUi("duda", "Duda Alves", "Avulso", noShow = false, skipped = false),
                UnchargedRowUi("julia", "Júlia Rocha", "Convite de Bia Souza", noShow = false, skipped = false),
                UnchargedRowUi("rafa", "Rafa Nunes", "Avulso · saiu do grupo", noShow = false, skipped = false),
                UnchargedRowUi("marcos", "Marcos Lima", "Avulso", noShow = true, skipped = false),
            ),
        )

        val acertoAntecipado = GameSettlementState(
            isLoading = false,
            header = header.copy(title = "Quinta, 29/10", meta = "20h · CERET — Quadra 2"),
            diarists = listOf(
                diary("caio", "Caio Lima", ChargeStatus.Paid, "Avulso · Pix"),
                diary("duda", "Duda Alves", ChargeStatus.Paid, "Avulso · Pix"),
                diary("rafa", "Rafa Nunes", ChargeStatus.Paid, "Avulso · Pix")
                    .copy(notPlayed = NotPlayedReason.NotReleased, canUndo = true),
                diary("marcos", "Marcos Lima", ChargeStatus.Pending, "Avulso").copy(notPlayed = NotPlayedReason.Withdrew),
            ),
            debtors = listOf(debtor("marcos", "Marcos Lima", "", null, 2_000L)),
        )

        val revisao = UnchargedReviewState(
            isLoading = false,
            gameDay = "Terça, 20/10",
            venueName = "CERET",
            gameDate = "20/10",
            amountInput = "20,00",
            suggested = true,
            rows = listOf(
                ReviewRowUi("caio/0", "caio", 0, "Caio Lima", null, false, false, ReviewDecision.Charge),
                ReviewRowUi("duda/0", "duda", 0, "Duda Alves", null, false, false, ReviewDecision.Paid, PaidMethod.Pix),
                ReviewRowUi("julia/1", "bia", 1, "Júlia Rocha", "Bia", false, false, ReviewDecision.Charge),
                ReviewRowUi("rafa/0", "rafa", 0, "Rafa Nunes", null, false, true, ReviewDecision.Skip),
                ReviewRowUi("marcos/0", "marcos", 0, "Marcos Lima", null, true, false, ReviewDecision.Skip),
            ),
        )

        val grupoPix: GroupDetailsState = GroupDetailsPreviewData.member.copy(
            ownCharges = null,
            groupPix = PixUi("ceret@volei.com.br", "E-mail"),
        )
    }
}
