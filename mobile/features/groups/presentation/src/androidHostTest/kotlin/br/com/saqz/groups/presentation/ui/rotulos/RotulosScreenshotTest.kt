package br.com.saqz.groups.presentation.ui.rotulos

import br.com.saqz.designsystem.SaqzDashedAddButton
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoard
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoardTags
import br.com.saqz.groups.presentation.ui.components.GroupVenueRow
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicket
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicketTags
import br.com.saqz.groups.presentation.ui.gameeditor.GameEditorScreen
import br.com.saqz.groups.presentation.ui.gamedetail.GameDetailPreviewData
import br.com.saqz.groups.presentation.ui.gamedetail.GameWaitingList
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Prova visual dos rótulos centralizados: tela estreita (320 dp) e fonte 1.3, onde o rótulo quebra
 * de linha. `ROTULOS_FASE=antes` grava em `screenshots/rotulos/antes`; o padrão é `depois`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h900dp-xhdpi", application = Application::class)
class RotulosScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val fase = System.getenv("ROTULOS_FASE") ?: "depois"

    @Test
    fun conflitoDoEditor() = capture("conflito-do-editor") {
        GameEditorScreen(
            state = GameEditorState(
                groupName = "Vôlei do CERET",
                zoneId = "America/Sao_Paulo",
                form = GameEditorFields(
                    localDate = "2026-08-04",
                    localTime = "19:30",
                    durationMinutes = 120,
                    venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
                    capacity = 12,
                    confirmationLeadMinutes = 360,
                ),
                hasConflict = true,
                conflictGameId = "game-existing",
            ),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun galeria() = capture("galeria") {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SaqzButton("Escolher outro horário", {}, Modifier.weight(1f), SaqzButtonVariant.Secondary, SaqzButtonSize.Sm)
                SaqzButton("Abrir o jogo que já existe", {}, Modifier.weight(1f), size = SaqzButtonSize.Sm)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SaqzButton("Cancelar mesmo assim", {}, Modifier.weight(1f), SaqzButtonVariant.Secondary)
                SaqzButton("Salvar e avisar a galera", {}, Modifier.weight(1f))
            }
            SaqzButton("Entrar", {}, fullWidth = true)
            SaqzButton(
                label = "Compartilhar o convite do grupo com a galera",
                onClick = {},
                fullWidth = true,
                leadingContent = { SaqzIcon(SaqzIcons.Plus, tint = it) },
            )
            SaqzButton(
                label = "Criando o grupo e chamando todo mundo…",
                onClick = {},
                fullWidth = true,
                loading = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SaqzButton(
                    label = "Adicionar atleta ao grupo",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    size = SaqzButtonSize.Sm,
                    leadingContent = { SaqzIcon(SaqzIcons.Plus, tint = it) },
                )
                SaqzButton("Sair", {}, Modifier.weight(1f), SaqzButtonVariant.Danger, SaqzButtonSize.Sm)
            }
            SaqzStepper(value = 12, onValueChange = {}, min = 1, max = 40, label = "Vagas")
            SaqzStatusChip(text = "Administrador do grupo e do financeiro", tone = SaqzChipTone.Brand)
        }
    }

    @Test
    fun listaDeEspera() = capture("lista-de-espera") {
        Column { GameWaitingList(GameDetailPreviewData.admin, {}) }
    }

    @Test
    fun placarEControlesDaJornada() = capture("placar-e-controles") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.background(SaqzTheme.colors.primary)) {
                AttendanceScoreBoard(
                    going = 3,
                    out = 2,
                    waiting = 1,
                    pending = 5,
                    onClick = {},
                    tags = AttendanceScoreBoardTags("going", "out", "waiting", "pending"),
                )
            }
            SaqzDashedAddButton("Adicionar dia e horário", {})
            GroupVenueRow("CERET", "R. Canuto Abreu", "Trocar a quadra do grupo", {})
        }
    }

    @Test
    @Config(qualifiers = "w375dp-h900dp-xhdpi")
    fun minhasCobrancasFonte1() = capture("minhas-cobrancas-375dp-fonte-1-0", fontScale = 1f) { cobranca() }

    @Test
    @Config(qualifiers = "w375dp-h900dp-xhdpi")
    fun minhasCobrancasFonte1ponto3() = capture("minhas-cobrancas-375dp-fonte-1-3") { cobranca() }

    @Composable
    private fun cobranca() = Box(Modifier.padding(16.dp)) {
        OwnChargeTicket(
            eyebrow = "Minhas cobranças",
            amountLabel = "R$ 150,00",
            dueChipLabel = "Venceu em 10/10",
            dueChipOverdue = true,
            headerChip = "Vôlei do CERET",
            countLabel = "1 cobrança em aberto",
            receiverLabel = "Pix de Lucas Prado",
            copied = false,
            copyLabel = "Copiar chave Pix",
            copiedLabel = "Chave copiada",
            onCopy = {},
            onClick = null,
            contentDescription = null,
            tags = OwnChargeTicketTags(card = "card", copy = "copy"),
        )
    }

    private fun capture(nome: String, fontScale: Float = 1.3f, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                SaqzTheme { Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) { content() } }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/rotulos/$fase/$nome.png")
    }
}
