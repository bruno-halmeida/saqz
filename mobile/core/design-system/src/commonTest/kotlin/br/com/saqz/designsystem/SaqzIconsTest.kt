package br.com.saqz.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `SaqzIcons` é uma linha por conceito, e o defeito plausível numa lista assim é a
 * troca silenciosa: `Camera = Lucide.Clock` compila e desenha um relógio no badge da
 * foto. O nome que a Lucide carrega no próprio `ImageVector` é o que separa um do
 * outro, então é ele que este teste confere — glifo a glifo, inclusive os que já
 * existiam.
 */
class SaqzIconsTest {
    private val mapa = mapOf(
        "Home" to Pair(SaqzIcons.Home, "house"),
        "Calendar" to Pair(SaqzIcons.Calendar, "calendar"),
        "Users" to Pair(SaqzIcons.Users, "users"),
        "User" to Pair(SaqzIcons.User, "user"),
        "Bell" to Pair(SaqzIcons.Bell, "bell"),
        "Search" to Pair(SaqzIcons.Search, "search"),
        "Megaphone" to Pair(SaqzIcons.Megaphone, "megaphone"),
        "Pin" to Pair(SaqzIcons.Pin, "map-pin"),
        "Mail" to Pair(SaqzIcons.Mail, "mail"),
        "Lock" to Pair(SaqzIcons.Lock, "lock"),
        "Trash" to Pair(SaqzIcons.Trash, "trash"),
        "Eye" to Pair(SaqzIcons.Eye, "eye"),
        "EyeOff" to Pair(SaqzIcons.EyeOff, "eye-off"),
        "ChevronRight" to Pair(SaqzIcons.ChevronRight, "chevron-right"),
        "ChevronLeft" to Pair(SaqzIcons.ChevronLeft, "chevron-left"),
        "ArrowRight" to Pair(SaqzIcons.ArrowRight, "arrow-right"),
        "Close" to Pair(SaqzIcons.Close, "x"),
        "Plus" to Pair(SaqzIcons.Plus, "plus"),
        "Minus" to Pair(SaqzIcons.Minus, "minus"),
        "Check" to Pair(SaqzIcons.Check, "check"),
        "Phone" to Pair(SaqzIcons.Phone, "phone"),
        "Camera" to Pair(SaqzIcons.Camera, "camera"),
        "CircleAlert" to Pair(SaqzIcons.CircleAlert, "circle-alert"),
        "Clock" to Pair(SaqzIcons.Clock, "clock"),
    )

    @Test
    fun everyConceptPointsAtTheIntendedGlyph() {
        mapa.forEach { (conceito, par) ->
            val (icon, lucide) = par
            assertEquals(lucide, icon.name, "SaqzIcons.$conceito")
        }
    }

    @Test
    fun theFlowOneGlyphsAreMapped() {
        // Os seis do fluxo 1: telefone, seta do primário, badge da foto, alerta de erro,
        // alerta de expiração e o check do sucesso — este último já existia.
        val fluxo1 = listOf("Phone", "ArrowRight", "Camera", "CircleAlert", "Clock", "Check")
        fluxo1.forEach { nome ->
            assertEquals(true, nome in mapa, "o fluxo 1 usa $nome e ele não está mapeado")
        }
    }

    @Test
    fun everyGlyphSharesTheLucideCanvas() {
        // 24×24 em todos: é o que deixa `SaqzIcon(size = 22.dp)` render o mesmo peso
        // óptico para qualquer conceito. Um glifo fora dessa caixa entraria maior ou
        // menor que os vizinhos sem ninguém mexer no `size`.
        mapa.forEach { (conceito, par) ->
            val icon = par.first
            assertEquals(24.dp, icon.defaultWidth, "SaqzIcons.$conceito largura")
            assertEquals(24.dp, icon.defaultHeight, "SaqzIcons.$conceito altura")
        }
    }
}
