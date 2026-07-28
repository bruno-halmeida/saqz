package br.com.saqz.androidapp

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
        // Os três preenchimentos sólidos, a linha em repouso e a desabilitada. A última
        // é a que faltava: o contraste de 1,01:1 do VUL-45 passou justamente por não
        // estar em nenhuma cena.
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

    @Test
    fun sheet() = capture("ds-sheet") {
        Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzBottomSheet(
                open = true,
                onClose = {},
                title = "Sair da conta?",
                description = "Você volta para a tela de entrada e precisa entrar de novo.",
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
                content = {},
            )
        }
    }

}
