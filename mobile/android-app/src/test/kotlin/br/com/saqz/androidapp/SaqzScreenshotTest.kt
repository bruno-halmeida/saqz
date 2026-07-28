package br.com.saqz.androidapp

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.saqz.access.presentation.login.LoginState
import br.com.saqz.access.ui.LoginScreen
import br.com.saqz.designsystem.SaqzAttendance
import br.com.saqz.designsystem.SaqzAttendanceSelector
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzChoiceChipDefaults
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzGameSummaryCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzNavItem
import br.com.saqz.designsystem.SaqzOfflineBanner
import br.com.saqz.designsystem.SaqzProgressBar
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzAccessibilityPreferences
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots de tela em JVM (Roborazzi + Robolectric) — sem emulador.
 *
 * Gravar:   ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:    android-app/screenshots/ — ignorada pelo git. Os PNGs vivem na branch
 *           órfã `screenshots`; o PR embute o raw. Seção 11 do AGENTS.md.
 *
 * A task regrava **todas** as cenas deste arquivo, não só a que você mexeu.
 *
 * ponytail: sem verify/CI por enquanto — só o loop visual. Gate de regressão
 * visual entra quando os goldens estabilizarem.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application puro: o SaqzApplication real inicia Koin/Firebase, que são estáticos
// na JVM e quebram a partir do segundo teste. Screenshot não precisa de DI.
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class SaqzScreenshotTest {

    private companion object {
        // Fase do obturador. Transições de entrada (sheet 320ms, toast) já
        // terminaram, e o arco do CircularProgressIndicator indeterminado está
        // aberto — o ciclo dele é de 1332ms.
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        // autoAdvance desligado: o relógio só anda quando eu mando, então o quadro
        // capturado é sempre o mesmo. Com ele ligado, o indeterminado é fotografado
        // no início do ciclo — varredura ~0 — e o spinner vira um ponto.
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    // Página do catálogo: fundo canvas, margem de 16 e o gap de bloco entre peças.
    private fun gallery(name: String, content: @Composable ColumnScope.() -> Unit) = capture(name) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaqzTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }

    @Test
    fun login() = capture("login") {
        LoginScreen(state = LoginState(email = "ana@saqz.app"), onIntent = {})
    }

    @Test
    fun buttons() = gallery("ds-acoes") {
        SaqzButton("Confirmar presença", onClick = {}, fullWidth = true)
        SaqzButton("Editar", onClick = {}, variant = SaqzButtonVariant.Secondary, fullWidth = true)
        SaqzButton("Excluir grupo", onClick = {}, variant = SaqzButtonVariant.Danger, fullWidth = true)
        SaqzButton("Cancelar", onClick = {}, variant = SaqzButtonVariant.Ghost, fullWidth = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzButton("Criar jogo", onClick = {}, size = SaqzButtonSize.Sm)
            SaqzButton("Criar grupo", onClick = {}, enabled = false)
        }
        // Ícone da frente e loading em largura cheia: são as duas linhas que o export
        // desenha assim, e num Row de três o desabilitado quebrava o rótulo em duas.
        SaqzButton(
            "Criar próximo jogo",
            onClick = {},
            fullWidth = true,
            leadingContent = { SaqzIcon(SaqzIcons.Plus, tint = it) },
        )
        SaqzButton("Criando grupo", onClick = {}, loading = true, fullWidth = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzIconButton({}, "Voltar") { SaqzIcon(SaqzIcons.ChevronLeft) }
            SaqzIconButton({}, "Notificações", dot = true) { SaqzIcon(SaqzIcons.Bell) }
            SaqzIconButton({}, "Buscar", soft = true) { SaqzIcon(SaqzIcons.Search) }
            SaqzIconButton({}, "Adicionar", filled = true) {
                SaqzIcon(SaqzIcons.Plus, tint = SaqzTheme.colors.onPrimary)
            }
            SaqzIconButton({}, "Excluir", enabled = false) {
                SaqzIcon(SaqzIcons.Trash, tint = SaqzTheme.colors.disabledForeground)
            }
        }
        // O voltar solto do fluxo 1 (`outlined`): círculo branco de 44 com linha de 1px,
        // sem barra em volta. Sobre o canvas da galeria a borda aparece; sobre o card
        // branco ela é a única coisa que separa o botão do fundo, e é esse o caso que o
        // print precisa mostrar. Ao lado, o mesmo botão com a seta do primário.
        SaqzCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzIconButton({}, "Voltar", outlined = true) { SaqzIcon(SaqzIcons.ChevronLeft) }
                SaqzIconButton({}, "Voltar", outlined = true, enabled = false) {
                    SaqzIcon(SaqzIcons.ChevronLeft, tint = SaqzTheme.colors.disabledForeground)
                }
                SaqzIconButton({}, "Fechar", outlined = true) { SaqzIcon(SaqzIcons.Close) }
            }
        }
        SaqzButton(
            "Continuar",
            onClick = {},
            fullWidth = true,
            trailingContent = { SaqzIcon(SaqzIcons.ArrowRight, tint = it, size = 20.dp) },
        )
    }

    // Os seis glifos que o fluxo 1 acrescentou, no tamanho em que as telas os usam.
    // Sem cena, um glifo trocado (o badge da foto virar relógio) só apareceria na tela.
    @Test
    fun icons() = gallery("ds-icones") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SaqzIcon(SaqzIcons.Phone)
            SaqzIcon(SaqzIcons.ArrowRight)
            SaqzIcon(SaqzIcons.Camera)
            SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.errorForeground)
            SaqzIcon(SaqzIcons.Clock, tint = SaqzTheme.colors.warningForeground)
            SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.success)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SaqzIcon(SaqzIcons.Home)
            SaqzIcon(SaqzIcons.Calendar)
            SaqzIcon(SaqzIcons.Users)
            SaqzIcon(SaqzIcons.User)
            SaqzIcon(SaqzIcons.Bell)
            SaqzIcon(SaqzIcons.Search)
            SaqzIcon(SaqzIcons.Megaphone)
            SaqzIcon(SaqzIcons.Pin)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SaqzIcon(SaqzIcons.Mail)
            SaqzIcon(SaqzIcons.Lock)
            SaqzIcon(SaqzIcons.Trash)
            SaqzIcon(SaqzIcons.Eye)
            SaqzIcon(SaqzIcons.EyeOff)
            SaqzIcon(SaqzIcons.ChevronLeft)
            SaqzIcon(SaqzIcons.ChevronRight)
            SaqzIcon(SaqzIcons.Close)
            SaqzIcon(SaqzIcons.Plus)
            SaqzIcon(SaqzIcons.Minus)
        }
    }

    @Test
    fun forms() = gallery("ds-formularios") {
        // O ícone da frente é do chamador (`leadingContent`), então sem ele na cena o
        // slot não estava sendo conferido — e é ele que o export mostra em três dos
        // quatro campos do 10f.
        SaqzInput(
            TextFieldValue("ana@saqz.app"),
            {},
            label = "E-mail",
            kind = SaqzInputKind.Email,
            leadingContent = { SaqzIcon(SaqzIcons.Mail, tint = SaqzTheme.colors.primary) },
        )
        SaqzInput(
            TextFieldValue("segredo"),
            {},
            label = "Senha",
            kind = SaqzInputKind.Password,
            leadingContent = { SaqzIcon(SaqzIcons.Lock, tint = SaqzTheme.colors.primary) },
        )
        SaqzInput(
            TextFieldValue(""),
            {},
            label = "Endereço da quadra",
            placeholder = "R. Canuto Abreu, s/n",
            errorText = "Não encontramos esse endereço. Confira a rua e o bairro.",
            leadingContent = { SaqzIcon(SaqzIcons.Pin, tint = SaqzTheme.colors.primary) },
        )
        SaqzInput(TextFieldValue("CERET-8K2P"), {}, label = "Código do grupo")
        // Apoio e desabilitado: os dois estados do slot de mensagem que a cena não tinha,
        // e é neles que o tamanho do texto de apoio aparece ao lado do de erro acima.
        // Os dois com valor ficam vizinhos de propósito: é a comparação do VUL-63, em que
        // o travado era indistinguível do editável.
        SaqzInput(
            TextFieldValue("Quinta, 20h"),
            {},
            label = "Horário",
            helperText = "Todo mundo do grupo recebe o aviso.",
        )
        SaqzInput(
            TextFieldValue("Vôlei do CERET"),
            {},
            label = "Nome do grupo",
            enabled = false,
            helperText = "Só o administrador edita.",
        )
        // Desabilitado sem valor: o único estado travado que já recuava antes do VUL-63,
        // na cena para provar que o placeholder não regrediu junto.
        SaqzInput(
            TextFieldValue(""),
            {},
            label = "Quadra",
            placeholder = "A definir com o grupo",
            enabled = false,
        )
        // Travado + erro: a combinação que faltava, e por faltar deixou o halo vermelho de
        // 3dp passar pela revisão do #50 mesmo com a linha de 1px já travada.
        SaqzInput(
            TextFieldValue("ana"),
            {},
            label = "E-mail do convite",
            enabled = false,
            errorText = "Não confirmado. Só o administrador reenvia.",
        )
    }

    // Cena própria pelo mesmo motivo de `ds-controles`: com o sexto campo do VUL-63 o
    // seletor desabilitado saía do quadro, e estado fora do quadro não está sendo
    // conferido — foi exatamente assim que o 1,01:1 do VUL-45 passou.
    @Test
    fun attendance() = gallery("ds-presenca") {
        // Os três preenchimentos sólidos, a linha em repouso e a desabilitada.
        SaqzAttendanceSelector(value = SaqzAttendance.Going, onSelect = {})
        SaqzAttendanceSelector(value = SaqzAttendance.Maybe, onSelect = {})
        SaqzAttendanceSelector(value = SaqzAttendance.Out, onSelect = {})
        SaqzAttendanceSelector(value = null, onSelect = {})
        SaqzAttendanceSelector(value = SaqzAttendance.Maybe, onSelect = {}, enabled = false)
    }

    // Cena própria para os quatro controles do 10h (SaqzControls.kt): dentro de
    // `ds-formularios` os estados de limite do stepper empurravam segmented e chips
    // para fora do quadro — e estado fora do quadro não está sendo conferido.
    @Test
    fun controls() = gallery("ds-controles") {
        SaqzSwitch(checked = true, onCheckedChange = {}, label = "Jogo toda semana")
        SaqzSwitch(checked = false, onCheckedChange = {}, label = "Avisar por push")
        SaqzSwitch(checked = false, onCheckedChange = {}, label = "Bloqueado", enabled = false)
        SaqzStepper(value = 12, onValueChange = {}, min = 4, max = 24, label = "Vagas")
        // Os dois limites: sem eles o ± desabilitado nunca aparece na foto.
        SaqzStepper(value = 4, onValueChange = {}, min = 4, max = 24, label = "No mínimo")
        SaqzStepper(value = 24, onValueChange = {}, min = 4, max = 24, label = "No máximo")
        SaqzSegmented(listOf("Masculino", "Feminino", "Misto"), selected = 2, onSelect = {})
        SaqzSegmented(listOf("Semanal", "Avulso"), selected = 0, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzChoiceChip("Todos · 26", selected = true, onClick = {})
            SaqzChoiceChip("Admins · 2", selected = false, onClick = {})
            SaqzChoiceChip("Pendentes · 2", selected = false, onClick = {})
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzChoiceChipDefaults.CompactSpacing),
        ) {
            listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEachIndexed { index, day ->
                SaqzChoiceChip(
                    label = day,
                    selected = index == 2,
                    onClick = {},
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Test
    fun data() = gallery("ds-dados") {
        SaqzGameSummaryCard(
            eyebrow = "PRÓXIMO JOGO",
            title = "Ter, 28/07 · 19h30",
            venue = "CERET — Quadra 2",
            address = "Tatuapé, São Paulo",
            going = 12,
            maybe = 3,
            out = 2,
        ) {
            SaqzButton("Confirmar presença", onClick = {}, fullWidth = true)
        }
        // O local sem endereço e o card sem local nenhum: os dois estados que o pin
        // e a coluna do VUL-57 mudam e que a cena cheia não mostrava.
        SaqzGameSummaryCard(
            eyebrow = "PRÓXIMO JOGO",
            title = "Ter, 28/07 · 19h30",
            venue = "CERET — Quadra 2",
            going = 12,
            maybe = 3,
            out = 2,
        )
        SaqzGameSummaryCard(eyebrow = "PRÓXIMO JOGO", title = "Sem jogo marcado")
        SaqzCard {
            SaqzSectionHeader(title = "Confirmados", action = "Ver todos", onAction = {})
            // Os três tamanhos avulsos do 10k (iniciais navy sobre ice) e o avatar com
            // foto, que é onde o anel de 1px encosta na imagem.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaqzAvatar("Lucas Pereira", size = 30.dp)
                SaqzAvatar("Bruna Silva", size = 40.dp)
                SaqzAvatar("Tiago Moraes", size = 44.dp)
                SaqzAvatar("Camila Alves", size = 44.dp) {
                    Box(modifier = Modifier.size(44.dp).background(SaqzTheme.colors.accent))
                }
            }
            // Pilha com e sem excedente: o "+N" azul só existe acima de `max`.
            SaqzAvatarStack(listOf("Lucas Pereira", "Bruna Silva", "Tiago Moraes", "A", "B", "C"))
            SaqzAvatarStack(listOf("Lucas Pereira", "Bruna Silva"))
        }
        SaqzCard(padded = false) {
            SaqzMemberRow(
                "Lucas Pereira",
                meta = "Mensalista · desde 2024",
                trailing = { SaqzStatusChip("Admin", tone = SaqzChipTone.Brand) },
            )
            SaqzDivider()
            SaqzMemberRow(
                "Bruna Silva",
                meta = "Avulsa",
                trailing = { SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary) },
            )
            SaqzDivider()
            // Com foto: as iniciais azuis do MemberRow saem de cena, o anel fica.
            SaqzMemberRow(
                "Camila Alves",
                meta = "Levantadora · avulso",
                photo = { Box(modifier = Modifier.size(40.dp).background(SaqzTheme.colors.accent)) },
                onClick = {},
                trailing = { SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary) },
            )
        }
        SaqzCard(tone = SaqzCardTone.Soft) {
            // Os seis tons, na mesma peça e na mesma nomenclatura do 10j. Em uma linha
            // só o último chip saía cortado pela margem — duas linhas mostram todos.
            // As duas primeiras linhas são sem ponto, as duas últimas com: o ponto acende
            // no primeiro plano, então cada tom tem dois estados para conferir.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzStatusChip("Pendente", tone = SaqzChipTone.Neutral)
                SaqzStatusChip("Admin", tone = SaqzChipTone.Brand)
                SaqzStatusChip("Mensalista", tone = SaqzChipTone.Accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzStatusChip("Vou", tone = SaqzChipTone.Success)
                SaqzStatusChip("Talvez", tone = SaqzChipTone.Warning)
                SaqzStatusChip("Não vou", tone = SaqzChipTone.Error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzStatusChip("Pendente", tone = SaqzChipTone.Neutral, dot = true)
                SaqzStatusChip("Admin", tone = SaqzChipTone.Brand, dot = true)
                SaqzStatusChip("Mensalista", tone = SaqzChipTone.Accent, dot = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzStatusChip("Vou", tone = SaqzChipTone.Success, dot = true)
                SaqzStatusChip("Talvez", tone = SaqzChipTone.Warning, dot = true)
                SaqzStatusChip("Não vou", tone = SaqzChipTone.Error, dot = true)
            }
        }
    }

    @Test
    fun feedback() = gallery("ds-feedback") {
        SaqzToast(visible = true, onDismiss = {}) {
            SaqzToastText("Presença confirmada. Bom jogo!")
        }
        SaqzOfflineBanner()
        SaqzProgressBar(value = 0.4f)
        SaqzProgressBar()
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzSpinner(size = 16.dp)
            SaqzSpinner(size = 20.dp)
            SaqzSpinner(size = 30.dp)
            // "sobre azul" do 10o: `onDark` só existe para este fundo, e fora dele o
            // spinner branco some no canvas — sem a caixa azul não dá para conferir.
            Box(
                modifier = Modifier.background(SaqzTheme.colors.primary, CircleShape).padding(8.dp),
            ) {
                SaqzSpinner(size = 30.dp, onDark = true)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzSkeleton(width = 40.dp, height = 40.dp, circle = true)
            SaqzSkeleton(width = 180.dp, height = 14.dp)
        }
        SaqzSkeleton(height = 72.dp, radius = 12.dp)
        SaqzEmptyState(
            title = "Nenhum jogo marcado",
            description = "Crie o próximo jogo e a galera recebe o convite na hora.",
            icon = SaqzIcons.Plus,
            action = "Criar jogo",
            onAction = {},
        )
    }

    @Test
    fun navigation() = capture("ds-navegacao") {
        Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            Column {
                SaqzTopAppBar(
                    title = "Configurações do grupo",
                    onBack = {},
                    actions = {
                        SaqzIconButton({}, "Notificações", dot = true) { SaqzIcon(SaqzIcons.Bell) }
                    },
                )
                // A outra ramificação do 10p: sem voltar, com a marca. Sem ela o print não
                // mostra que só o chevron é azul — o resto da barra continua navy.
                SaqzTopAppBar(
                    logo = {
                        Text(
                            "saqz",
                            style = SaqzTheme.typography.title,
                            color = SaqzTheme.colors.primary,
                        )
                    },
                    actions = {
                        SaqzIconButton({}, "Buscar") { SaqzIcon(SaqzIcons.Search) }
                    },
                )
                Text(
                    "conteúdo da tela",
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            SaqzBottomNav(
                items = listOf(
                    SaqzNavItem("home", "Início", SaqzIcons.Home),
                    SaqzNavItem("games", "Jogos", SaqzIcons.Calendar),
                    SaqzNavItem("people", "Grupos", SaqzIcons.Users),
                    SaqzNavItem("me", "Perfil", SaqzIcons.User),
                ),
                activeId = "games",
                onSelect = {},
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }

    // Um sheet ocupa a tela inteira, então cada variante é uma cena: as três abaixo cobrem
    // o cabeçalho com fechar e as duas divisórias, o rodapé de um botão só e o painel sem
    // cabeçalho — que é o único caso em que o export omite o botão e a divisória de cima.
    @Test
    fun sheet() = capture("ds-sheet") {
        Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzBottomSheet(
                open = true,
                onClose = {},
                title = "Sair da conta?",
                description = "Você volta para a tela de entrada e precisa entrar de novo.",
                content = {
                    Text(
                        "Os jogos marcados continuam lá quando você voltar.",
                        style = SaqzTheme.typography.body,
                        color = SaqzTheme.colors.textSecondary,
                    )
                },
                splitFooter = {
                    SaqzButton(
                        "Cancelar",
                        onClick = {},
                        variant = SaqzButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    SaqzButton(
                        "Confirmar saída",
                        onClick = {},
                        variant = SaqzButtonVariant.Danger,
                        modifier = Modifier.weight(1f),
                    )
                },
            )
        }
    }

    @Test
    fun sheetSingleFooter() = capture("ds-sheet-rodape-simples") {
        Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzBottomSheet(
                open = true,
                onClose = {},
                title = "Convidar para o grupo",
                description = "Quem receber o link entra direto na lista de atletas.",
                content = {
                    SaqzInput(
                        value = TextFieldValue("saqz.app/g/quarta-19h"),
                        onValueChange = {},
                        label = "Link do convite",
                    )
                },
                footer = { SaqzButton("Copiar link", onClick = {}, fullWidth = true) },
            )
        }
    }

    // As duas cenas do achado do Codex no PR #48: com alça, cabeçalho e rodapé fixos, era
    // o conteúdo que empurrava o rodapé para fora da tela. O rodapé visível nas duas é a
    // prova de que o conteúdo agora rola em vez de esticar o painel.
    @Test
    fun sheetWithLongContent() = capture("ds-sheet-conteudo-longo") {
        Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzBottomSheet(
                open = true,
                onClose = {},
                title = "Regras do grupo",
                description = "Quem entra concorda com tudo isto.",
                // 30 regras não cabem em tela nenhuma: é o caso que antes empurrava o
                // rodapé para fora. Aqui o conteúdo é cortado no limite da rolagem.
                content = { repeat(30) { RegraLonga(it) } },
                footer = { SaqzButton("Aceitar e entrar", onClick = {}, fullWidth = true) },
            )
        }
    }

    @Test
    fun sheetWithLargeFont() = capture("ds-sheet-fonte-ampliada") {
        // Fonte a 2×, o pior caso de acessibilidade: o cabeçalho cresce, o rodapé cresce,
        // e o que sobra para o conteúdo encolhe.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 2f),
        ) {
            Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                SaqzBottomSheet(
                    open = true,
                    onClose = {},
                    title = "Regras do grupo",
                    description = "Quem entra concorda com tudo isto.",
                    content = { repeat(12) { RegraLonga(it) } },
                    footer = { SaqzButton("Aceitar e entrar", onClick = {}, fullWidth = true) },
                )
            }
        }
    }

    @Composable
    private fun RegraLonga(index: Int) = Text(
        "${index + 1}. Confirme presença até a véspera; quem desmarca depois entra no fim da fila.",
        style = SaqzTheme.typography.body,
        color = SaqzTheme.colors.textSecondary,
    )

    // VUL-52: o ajuste "Remover animações" do Android chegando na composição. Animação
    // parada não se fotografa — o que a foto mostra são as durações vigentes em
    // `SaqzTheme.motion`, as mesmas que o catálogo imprime, com o sinal do sistema lido
    // pelo `rememberReduceMotion()` de verdade. Os dois estados, um PNG cada.
    @Test
    fun motionComAnimacoes() = captureUnderSystemMotion("ds-motion-normal")

    @Test
    fun motionSemAnimacoes() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        captureUnderSystemMotion("ds-motion-reduzido")
    }

    private fun captureUnderSystemMotion(name: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SaqzTheme(SaqzAccessibilityPreferences(reduceMotion = rememberReduceMotion())) {
                MotionSpecimens()
            }
        }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    // Todos os campos em que `Normal` e `Reduced` divergem, mais os controles que eles
    // animam — as peças ficam idênticas, é só o tempo que cai a zero.
    @Composable
    private fun MotionSpecimens() {
        val motion = SaqzTheme.motion
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaqzTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SaqzSectionHeader(
                title = if (motion == SaqzMotionPolicy.Reduced) {
                    "Remover animações: ligado"
                } else {
                    "Remover animações: desligado"
                },
            )
            Text(
                "sheet ${motion.sheetDurationMillis}ms · thumb do segmented ${motion.thumbDurationMillis}ms · " +
                    "switch ${motion.switchDurationMillis}ms · toast ${motion.toastDwellMillis}ms",
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            Text(
                "press: escala ${motion.pressScale} · deslocamento ${motion.pressOffset.value}dp",
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            SaqzSwitch(checked = true, onCheckedChange = {}, label = "Jogo toda semana")
            SaqzSwitch(checked = false, onCheckedChange = {}, label = "Avisar por push")
            SaqzSegmented(listOf("Masculino", "Feminino", "Misto"), selected = 2, onSelect = {})
            SaqzButton("Confirmar presença", onClick = {}, fullWidth = true)
        }
    }

    @Test
    fun sheetWithoutHeader() = capture("ds-sheet-sem-cabecalho") {
        Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzBottomSheet(open = true, onClose = {}) {
                SaqzMemberRow(name = "Lucas Pereira", meta = "Organizador")
                SaqzMemberRow(name = "Bruna Silva", meta = "Atleta")
            }
        }
    }

}
