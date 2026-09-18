# B · Extração — peças da Início viram componentes do módulo

**Onda 1 · depende de: nada · bloqueia: C1, C2, C3, C4, C5 · paralelo com: S, A, V, T**

## Objetivo

As peças de UI da Início nova (hero azul, placar, "Próximos jogos", "Esperando você", ticket de cobrança) vivem em `ui/home/`, privadas e presas a `HomeIntent`/`HomeNextGameUi`/`HomeTags`. O detalhe do grupo é o **segundo uso concreto** (AD-031): este PR as move para `ui/components/` do próprio módulo (NÃO para o design system), com parâmetros planos — primitivos, lambdas, textos e `testTag` por parâmetro — e faz a Início passar a usá-las.

**Nada visual muda.** A Início fica pixel-idêntica, com as mesmas `testTag` e os mesmos textos; nenhum teste existente muda de expectativa.

Decisões já tomadas (fatos, não opções):
- `HeroAttendanceControls` continua extensão de `ColumnScope` sem `modifier`, como o `HomeAttendanceControls` de hoje: emite irmãos na coluna do `SaqzHeroCard` e herda o espaçamento dela.
- `HomeAttendanceControls`, `HomeWaitingRow` e `HomeOwnChargeTicket` **ficam** na Início como adaptadores finos (traduzem `Home*Ui`/`HomeIntent` para os parâmetros planos). `HomeDeadlineLine`, `HomeResponseRow`, `HomeResponseVariant`, `HomeAnsweredStatus`, `HomeResponseButton`, `AdminScoreBoard`, `ScoreDivider`, `AdminScoreColumn`, `HomeUpcomingRow` e todas as constantes `Hero*`/`Score*`/`DateBoxSize`/`Pix*Size` são **apagadas** da Início.
- `HomeWaitlistSections.kt` não é tocado: as tags `HomeWaitlistTags.*` são compartilhadas pelas duas telas (elas nunca estão na mesma árvore de semântica).
- A Início chama `WaitingRow` com `metaMaxLines = Int.MAX_VALUE`: hoje a meta dela não tem limite, e o default de 1 linha é do detalhe.
- `OwnChargeTicket` ganhou dois parâmetros além do pedido original: `contentDescription: String?` (o rótulo do clique, que hoje é o nome do grupo) e `footnote: String? = null` (a legenda do mock do detalhe, dentro do card depois do botão). Sem `headerChip`, o chip de vencimento sobe para a linha do eyebrow — é o desenho do mock do detalhe.
- O `1.dp` cru do traço do placar virou `ScoreDividerWidth` (mesmo valor).

## Fora do escopo

- **PROIBIDO tocar:** `PRES/home/HomeViewModel.kt`, `PRES/home/HomeContract.kt` (ticket V), qualquer arquivo em `PRES/details/` ou `PRES/ui/details/` (tickets V e T), qualquer arquivo de `composeResources`, `mobile/core/design-system`, `PRES/ui/home/HomeWaitlistSections.kt`, `PRES/ui/home/HomeRoot.kt`.
- `HomeShortcutCard`, `HomeGroupRow`, `HomeHeader`, a faixa `HomeOwnChargesBanner`: ficam onde estão.
- Nenhum teste existente é editado.
- **Cortado pelo teto de 2000 linhas:** `@Preview` das peças novas e uma suíte Roborazzi das variantes que só o detalhe usa (linha sem clique com botão, `leading`, ticket sem chip do grupo/sem Pix/com `details`, aviso com ação). Cada ticket C fotografa a variante que usar.

## Arquivos

Base: `PRES = mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation` e `TEST = mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation`.

| Ação | Arquivo |
|---|---|
| criar | `PRES/ui/components/HeroAttendance.kt` |
| criar | `PRES/ui/components/AttendanceScoreBoard.kt` |
| criar | `PRES/ui/components/UpcomingGameRow.kt` |
| criar | `PRES/ui/components/WaitingRow.kt` |
| criar | `PRES/ui/components/OwnChargeTicket.kt` |
| editar | `PRES/ui/home/HomeScreen.kt` |
| editar | `PRES/ui/home/HomeAdminSections.kt` |
| editar | `PRES/ui/home/HomeUpcomingSection.kt` |
| editar | `PRES/ui/home/HomeOwnChargesSection.kt` |
| criar | `TEST/ui/components/HeroAttendanceTest.kt` |
| criar | `TEST/ui/components/WaitingRowTest.kt` |
| criar | `TEST/ui/components/OwnChargeTicketTest.kt` |
| apagar | nenhum |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Contrato exportado

Assinaturas finais (copiadas do código desta receita). Tudo `internal`, pacote `br.com.saqz.groups.presentation.ui.components`.

```kotlin
// ui/components/HeroAttendance.kt
internal const val HeroTextAlpha = 0.9f
internal const val HeroMutedAlpha = 0.7f
internal const val HeroSummaryAlpha = 0.88f
internal const val HeroPanelAlpha = 0.12f
internal const val HeroDotMutedAlpha = 0.45f
internal const val HeroOutlineAlpha = 0.45f
internal val HeroIconGap = 6.dp
internal val HeroLineIconSize = 14.dp
internal val HeroInlineIconSize = 16.dp
internal val HeroCheckSize = 18.dp
internal val HeroStatusDot = 10.dp

@Immutable internal data class HeroAttendanceTexts(
    val yes: String, val no: String, val confirmed: String, val declined: String,
    val change: String, val cancel: String, val error: String,
)
@Immutable internal data class HeroAttendanceTags(
    val yes: String, val no: String, val change: String, val cancel: String, val error: String,
)

@Composable internal fun HeroDeadlineLine(text: String, open: Boolean, modifier: Modifier = Modifier)

@Composable internal fun HeroAlertLine(
    text: String,
    tag: String?,                                   // null = linha sem testTag
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,       // à direita; o texto fica com weight(1f)
)

@Composable internal fun HeroRosterRow(
    names: List<String>,
    summary: String,
    spotsChip: String?,                             // null esconde o chip
    modifier: Modifier = Modifier,
)

// Extensão de ColumnScope: emite irmãos na coluna do SaqzHeroCard (sem modifier, de propósito).
@Composable internal fun ColumnScope.HeroAttendanceControls(
    status: AttendanceStatus?,
    confirmationOpen: Boolean,
    responding: Boolean,
    responseFailed: Boolean,
    waitlistKind: HomeWaitlistKind?,                // null em espera = Reserva
    waitlistPosition: Long?,
    onRespond: (AttendanceIntent) -> Unit,
    onViewGame: () -> Unit,
    texts: HeroAttendanceTexts,
    tags: HeroAttendanceTags,
)

// ui/components/AttendanceScoreBoard.kt
@Immutable internal data class AttendanceScoreBoardTags(val going: String, val out: String, val pending: String)
@Composable internal fun AttendanceScoreBoard(
    going: Int, out: Int, pending: Int,
    onClick: () -> Unit,
    tags: AttendanceScoreBoardTags,
    modifier: Modifier = Modifier,
)

// ui/components/UpcomingGameRow.kt
@Composable internal fun DateTile(day: String, month: String, modifier: Modifier = Modifier)
@Composable internal fun UpcomingGameRow(
    day: String, month: String, title: String, meta: String,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
)

// ui/components/WaitingRow.kt
@Composable internal fun WaitingRow(
    icon: ImageVector,                              // ignorado quando `leading` != null
    title: String,
    meta: String?,                                  // null esconde a 2ª linha
    contentDescription: String,
    onClick: (() -> Unit)?,                         // null = sem clique; vira um item fundido para o leitor de tela
    tag: String,
    modifier: Modifier = Modifier,
    metaMaxLines: Int = 1,                          // corta com reticências
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
)

// ui/components/OwnChargeTicket.kt
@Immutable internal data class OwnChargeTicketTags(val card: String, val copy: String)
@Composable internal fun OwnChargeTicket(
    eyebrow: String,                                // o componente aplica uppercase()
    amountLabel: String,
    dueChipLabel: String,
    dueChipOverdue: Boolean,                        // true = Warning com ponto; false = Neutral sem ponto
    headerChip: String?,                            // != null: chip Brand no eyebrow e vencimento ao lado do valor; null: vencimento sobe para o eyebrow
    countLabel: String?,
    receiverLabel: String?,
    copied: Boolean,
    copyLabel: String,
    copiedLabel: String,
    onCopy: (() -> Unit)?,                          // null esconde recebedor + botão
    onClick: (() -> Unit)?,                         // null = card sem clique
    contentDescription: String?,                    // rótulo do clique + descrição do card; null quando onClick é null
    tags: OwnChargeTicketTags,
    modifier: Modifier = Modifier,
    footnote: String? = null,                       // caption depois do botão (ACRESCENTADO ao pedido: o mock tem)
    details: (@Composable ColumnScope.() -> Unit)? = null, // entre countLabel e recebedor
)
```

Fatos que os tickets C podem assumir:
- As tags da lista de espera dentro de `HeroAttendanceControls` são as de `HomeWaitlistTags` (`home-reserva-chip`, `home-reserva-leave`, `home-reserva-view-game`, `home-avulso-*`), **compartilhadas** pela Início e pelo detalhe. Não há colisão: o `NavDisplay` usa cena de painel único e o detalhe é destino empilhado acima do shell — as duas telas nunca ficam na mesma árvore de semântica. `HomeWaitlistSections.kt` **não é tocado**.
- Filho de linha clicável (`WaitingRow` com `onClick`, `UpcomingGameRow`, `OwnChargeTicket` com `onClick`) só é achado em teste com `useUnmergedTree = true`; botão (`SaqzButton`) dentro delas é alvo próprio e aparece na árvore fundida.
- Nenhuma peça tem `@Preview` nem cena Roborazzi própria neste PR (teto de linhas); as variantes que só o detalhe usa ganham cena no ticket C que as usar.

## Passo a passo

Os números de linha abaixo são os do arquivo **intocado** em `origin/main` (`9a865e65`). Dentro de cada arquivo editado, aplique as edições **de baixo para cima** (a última da lista primeiro) para a numeração continuar valendo; o bloco de imports é sempre o último. Se o trecho citado não estiver na linha indicada, o arquivo mudou: parar e avisar o orquestrador.

### 0. Antes de editar qualquer coisa: gravar o "antes"

Execute agora o bloco **"Antes"** da seção *Prova de não-regressão visual*. Só depois siga para o passo 1.

### 1. Criar `PRES/ui/components/HeroAttendance.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistActions
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistChip
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistInfoBox

// Opacidades do branco sobre o azul do hero (VUL-218). Só `onPrimary` com alfa: não há
// token de branco translúcido no contrato, e derivar do sólido é o padrão dos chips.
internal const val HeroTextAlpha = 0.9f
internal const val HeroMutedAlpha = 0.7f
internal const val HeroSummaryAlpha = 0.88f
internal const val HeroPanelAlpha = 0.12f
internal const val HeroDotMutedAlpha = 0.45f
internal const val HeroOutlineAlpha = 0.45f
internal val HeroIconGap = 6.dp
internal val HeroLineIconSize = 14.dp
internal val HeroInlineIconSize = 16.dp
internal val HeroCheckSize = 18.dp
internal val HeroStatusDot = 10.dp

/** Textos do seletor de presença: cada tela passa a própria cópia (a do detalhe do grupo é contrato de e2e). */
@Immutable
internal data class HeroAttendanceTexts(
    val yes: String,
    val no: String,
    val confirmed: String,
    val declined: String,
    val change: String,
    val cancel: String,
    val error: String,
)

/** `testTag` por parâmetro, como no `PixCard`: cada tela responde pelo próprio inventário de tags. */
@Immutable
internal data class HeroAttendanceTags(
    val yes: String,
    val no: String,
    val change: String,
    val cancel: String,
    val error: String,
)

/** Linha do prazo do RSVP: relógio + texto; com o prazo encerrado fica mais apagada. */
@Composable
internal fun HeroDeadlineLine(text: String, open: Boolean, modifier: Modifier = Modifier) {
    val color = SaqzTheme.colors.onPrimary.copy(alpha = if (open) HeroTextAlpha else HeroMutedAlpha)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.Clock, tint = color, size = HeroLineIconSize)
        Text(
            text = text,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

/**
 * Aviso sobre o azul. Não há token de erro legível ali: o aviso é branco com o ícone de alerta.
 * [tag] nulo não marca a linha; [action] entra à direita e o texto fica com a largura que sobra.
 */
@Composable
internal fun HeroAlertLine(
    text: String,
    tag: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val color = SaqzTheme.colors.onPrimary.copy(alpha = HeroTextAlpha)
    Row(
        modifier = if (tag != null) modifier.testTag(tag) else modifier,
        horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = color, size = HeroInlineIconSize)
        Text(
            text = text,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = if (action != null) Modifier.weight(1f) else Modifier,
        )
        action?.invoke()
    }
}

/**
 * Quem vai, sobre o azul: pilha de avatares com anel azul e "+N" lima, o resumo e, quando a
 * tela manda, o chip de escassez ("Restam N vagas"). [spotsChip] nulo esconde o chip.
 */
@Composable
internal fun HeroRosterRow(
    names: List<String>,
    summary: String,
    spotsChip: String?,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzAvatarStack(
            names = names,
            ring = colors.primary,
            overflowContainer = colors.accent,
            overflowContent = colors.textPrimary,
        )
        Text(
            text = summary,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onPrimary.copy(alpha = HeroSummaryAlpha),
            modifier = Modifier.weight(1f),
        )
        if (spotsChip != null) {
            SaqzStatusChip(text = spotsChip, tone = SaqzChipTone.Inverse, dot = true)
        }
    }
}

/**
 * Seletor de presença sobre o hero azul. Nasceu na Início (VUL-191/218); o detalhe do grupo é
 * o segundo uso, por isso textos e `testTag` chegam por parâmetro.
 *
 * Sem resposta é o momento do toque: "Vou" (lima) e "Não vou" (contorno) grandes. Depois de
 * respondido os botões somem e fica o painel com a resposta e "Alterar". Em espera entram o
 * chip, a caixa e as ações da fila (`HomeWaitlist*`, com as tags de `HomeWaitlistTags`, que as
 * duas telas compartilham). [responseFailed] acrescenta o aviso branco no fim.
 *
 * É extensão de `ColumnScope` porque emite irmãos direto na coluna do `SaqzHeroCard`: o
 * espaço entre eles é o do hero.
 */
@Composable
internal fun ColumnScope.HeroAttendanceControls(
    status: AttendanceStatus?,
    confirmationOpen: Boolean,
    responding: Boolean,
    responseFailed: Boolean,
    waitlistKind: HomeWaitlistKind?,
    waitlistPosition: Long?,
    onRespond: (AttendanceIntent) -> Unit,
    onViewGame: () -> Unit,
    texts: HeroAttendanceTexts,
    tags: HeroAttendanceTags,
) {
    when {
        status == AttendanceStatus.Waitlisted -> {
            val kind = waitlistKind ?: HomeWaitlistKind.Reserva
            HomeWaitlistChip(kind = kind, position = waitlistPosition)
            HomeWaitlistInfoBox(kind = kind)
            HomeWaitlistActions(
                kind = kind,
                responding = responding,
                confirmationOpen = confirmationOpen,
                onLeave = { onRespond(AttendanceIntent.Decline) },
                onViewGame = onViewGame,
            )
        }
        // Pendente é o momento do toque: botões grandes, pergunta aberta.
        status == null -> HeroResponseRow(
            status = null,
            confirmationOpen = confirmationOpen,
            responding = responding,
            size = SaqzButtonSize.Md,
            onRespond = onRespond,
            texts = texts,
            tags = tags,
        )
        // Depois de respondido, os botões somem — dois botões habilitados num jogo já
        // confirmado pareciam uma pergunta sem resposta e confundiam o atleta.
        else -> HeroAnsweredStatus(
            status = status,
            responding = responding,
            changeEnabled = confirmationOpen,
            onRespond = onRespond,
            texts = texts,
            tags = tags,
        )
    }
    if (responseFailed) {
        HeroAlertLine(text = texts.error, tag = tags.error)
    }
}

/**
 * "Vou" é sempre o CTA lima; "Não vou" é contorno branco. Com resposta marcada (modo
 * alterar) a marcada leva o check: "Vou" continua lima, "Não vou" marcado vira branco sólido
 * e o outro cai para contorno.
 */
@Composable
private fun HeroResponseRow(
    status: AttendanceStatus?,
    confirmationOpen: Boolean,
    responding: Boolean,
    size: SaqzButtonSize,
    onRespond: (AttendanceIntent) -> Unit,
    texts: HeroAttendanceTexts,
    tags: HeroAttendanceTags,
) {
    val declined = status == AttendanceStatus.Declined
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        HeroResponseButton(
            label = texts.yes,
            variant = if (declined) HeroResponseVariant.Outline else HeroResponseVariant.Accent,
            selected = status == AttendanceStatus.Confirmed,
            loading = responding && status == AttendanceStatus.Confirmed,
            enabled = confirmationOpen && !responding,
            size = size,
            onClick = { onRespond(AttendanceIntent.Confirm) },
            modifier = Modifier.weight(1f).testTag(tags.yes),
        )
        HeroResponseButton(
            label = texts.no,
            variant = if (declined) HeroResponseVariant.Inverse else HeroResponseVariant.Outline,
            selected = declined,
            loading = responding && declined,
            enabled = confirmationOpen && !responding,
            size = size,
            onClick = { onRespond(AttendanceIntent.Decline) },
            modifier = Modifier.weight(1f).testTag(tags.no),
        )
    }
}

private enum class HeroResponseVariant { Accent, Inverse, Outline }

/**
 * Estado de quem já respondeu: painel único com a resposta e "Alterar" — os botões só
 * reaparecem a pedido (`editing`), porque dois botões habilitados num jogo já
 * confirmado não dizem que a resposta foi registrada e parecem exigir nova ação.
 * `editing` vive na composição e morre quando o status muda: responder de novo
 * (com sucesso) volta para o painel automaticamente.
 */
@Composable
private fun HeroAnsweredStatus(
    status: AttendanceStatus,
    responding: Boolean,
    changeEnabled: Boolean,
    onRespond: (AttendanceIntent) -> Unit,
    texts: HeroAttendanceTexts,
    tags: HeroAttendanceTags,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    var editing by remember(status) { mutableStateOf(false) }
    if (editing) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            HeroResponseRow(
                status = status,
                confirmationOpen = changeEnabled,
                responding = responding,
                size = SaqzButtonSize.Sm,
                onRespond = onRespond,
                texts = texts,
                tags = tags,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaqzButton(
                    label = texts.cancel,
                    onClick = { editing = false },
                    variant = SaqzButtonVariant.Ghost,
                    contentColor = colors.onPrimary,
                    size = SaqzButtonSize.Sm,
                    enabled = !responding,
                    modifier = Modifier.testTag(tags.cancel),
                )
            }
        }
    } else {
        val (color, text) = when (status) {
            AttendanceStatus.Confirmed -> colors.success to texts.confirmed
            else -> colors.onPrimary.copy(alpha = HeroDotMutedAlpha) to texts.declined
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.onPrimary.copy(alpha = HeroPanelAlpha), RoundedCornerShape(metrics.inputRadius))
                .padding(
                    start = metrics.blockGap,
                    end = metrics.subGrid,
                    top = metrics.subGrid,
                    bottom = metrics.subGrid,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid * 2),
        ) {
            Box(
                modifier = Modifier
                    .size(HeroStatusDot)
                    .background(color, CircleShape),
            )
            Text(
                text = text,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = texts.change,
                onClick = { editing = true },
                variant = SaqzButtonVariant.Ghost,
                contentColor = colors.onPrimary,
                size = SaqzButtonSize.Sm,
                enabled = changeEnabled && !responding,
                loading = responding,
                modifier = Modifier.testTag(tags.change),
            )
        }
    }
}

@Composable
private fun HeroResponseButton(
    label: String,
    variant: HeroResponseVariant,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    size: SaqzButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val saqzVariant = when (variant) {
        HeroResponseVariant.Accent -> SaqzButtonVariant.Accent
        HeroResponseVariant.Inverse -> SaqzButtonVariant.Inverse
        HeroResponseVariant.Outline -> SaqzButtonVariant.Ghost
    }
    val outline = variant == HeroResponseVariant.Outline
    SaqzButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        variant = saqzVariant,
        size = size,
        fullWidth = true,
        enabled = enabled,
        loading = loading,
        contentColor = if (outline) colors.onPrimary else null,
        borderColor = if (outline) colors.onPrimary.copy(alpha = HeroOutlineAlpha) else null,
        leadingContent = if (selected) {
            { tint -> SaqzIcon(SaqzIcons.Check, tint = tint, size = HeroCheckSize) }
        } else {
            null
        },
    )
}
```

### 2. Criar `PRES/ui/components/AttendanceScoreBoard.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_cd_score_going
import br.com.saqz.groups.resources.home_admin_cd_score_out
import br.com.saqz.groups.resources.home_admin_cd_score_pending
import br.com.saqz.groups.resources.home_admin_cd_scoreboard_open
import br.com.saqz.groups.resources.home_admin_score_going
import br.com.saqz.groups.resources.home_admin_score_out
import br.com.saqz.groups.resources.home_admin_score_pending
import br.com.saqz.groups.resources.home_admin_score_value
import org.jetbrains.compose.resources.stringResource

/** `testTag` das três colunas do placar: cada tela responde pelo próprio inventário. */
@Immutable
internal data class AttendanceScoreBoardTags(
    val going: String,
    val out: String,
    val pending: String,
)

/**
 * Placar em 3 colunas sobre o hero azul: painel branco a 10%, traços brancos a 18%,
 * números em `display` 28sp — Vão em lima, Não vão em branco a 70%, Sem resposta em
 * warning. SEM Talvez (decisão do projeto). Nasceu no hero do gestor da Início (VUL-192);
 * rótulos e descrições continuam nas chaves `home_admin_score_*`/`home_admin_cd_*`.
 */
@Composable
internal fun AttendanceScoreBoard(
    going: Int,
    out: Int,
    pending: Int,
    onClick: () -> Unit,
    tags: AttendanceScoreBoardTags,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.inputRadius))
            .background(colors.onPrimary.copy(alpha = ScorePanelAlpha))
            .clickable(
                onClickLabel = stringResource(Res.string.home_admin_cd_scoreboard_open),
                role = Role.Button,
                onClick = onClick,
            )
            // Funde as descrições das três colunas num nó só: o placar inteiro é um botão.
            .semantics(mergeDescendants = true) {}
            .height(IntrinsicSize.Min)
            .padding(vertical = metrics.blockGap),
    ) {
        ScoreColumn(
            value = going,
            label = stringResource(Res.string.home_admin_score_going),
            color = colors.accent,
            contentDescription = stringResource(Res.string.home_admin_cd_score_going, going),
            modifier = Modifier.testTag(tags.going),
        )
        ScoreDivider()
        ScoreColumn(
            value = out,
            label = stringResource(Res.string.home_admin_score_out),
            color = colors.onPrimary.copy(alpha = ScoreOutAlpha),
            contentDescription = stringResource(Res.string.home_admin_cd_score_out, out),
            modifier = Modifier.testTag(tags.out),
        )
        ScoreDivider()
        ScoreColumn(
            value = pending,
            label = stringResource(Res.string.home_admin_score_pending),
            color = colors.warning,
            contentDescription = stringResource(Res.string.home_admin_cd_score_pending, pending),
            modifier = Modifier.testTag(tags.pending),
        )
    }
}

@Composable
private fun ScoreDivider() = Box(
    modifier = Modifier
        .width(ScoreDividerWidth)
        .fillMaxHeight()
        .background(SaqzTheme.colors.onPrimary.copy(alpha = ScoreDividerAlpha)),
)

@Composable
private fun RowScope.ScoreColumn(
    value: Int,
    label: String,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .weight(1f)
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(
            text = stringResource(Res.string.home_admin_score_value, value),
            style = SaqzTheme.typography.display.copy(fontSize = ScoreValueSize, lineHeight = ScoreValueSize),
            color = color,
        )
        Text(
            text = label,
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.onPrimary.copy(alpha = ScoreLabelAlpha),
        )
    }
}

private const val ScorePanelAlpha = 0.10f
private const val ScoreDividerAlpha = 0.18f
private const val ScoreOutAlpha = 0.7f
private const val ScoreLabelAlpha = 0.72f
private val ScoreValueSize = 28.sp
private val ScoreDividerWidth = 1.dp
```

### 3. Criar `PRES/ui/components/UpcomingGameRow.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme

/** Tile de data ice 44dp: dia em `dateDay`, mês em `dateMonth` azul. */
@Composable
internal fun DateTile(day: String, month: String, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    Column(
        modifier = modifier
            .size(DateTileSize)
            .background(colors.surfaceSoft, RoundedCornerShape(SaqzTheme.metrics.inputRadius)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = day, style = SaqzTheme.typography.dateDay, color = colors.textPrimary)
        Text(text = month, style = SaqzTheme.typography.dateMonth, color = colors.primary)
    }
}

/**
 * Linha de "Próximos jogos" (VUL-221): tile de data, título, meta e o que a tela quiser à
 * direita em [trailing] — na Início é o chip do estado do próprio usuário. A linha inteira é
 * um botão; [contentDescription] é o que o leitor de tela anuncia.
 */
@Composable
internal fun UpcomingGameRow(
    day: String,
    month: String,
    title: String,
    meta: String,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(tag)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        DateTile(day = day, month = month)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = SaqzTheme.typography.compactTitle, color = colors.textPrimary)
            Text(text = meta, style = SaqzTheme.typography.compactMeta, color = colors.textSecondary)
        }
        trailing()
    }
}

private val DateTileSize = 44.dp
```

### 4. Criar `PRES/ui/components/WaitingRow.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Linha de "Esperando você" (VUL-192/219): círculo ice 40dp com ícone cinza, título em
 * `compactTitle`, meta em `compactMeta` e o que a tela quiser à direita em [trailing]
 * (chip, chevron, botão). Vive dentro de um `SaqzCard(padded = false)`, separada por `SaqzDivider`.
 *
 * [onClick] nulo = linha sem clique: o único alvo é o que estiver em [trailing]. Para o leitor
 * de tela ela continua sendo um item só, descrito por [contentDescription]; um botão no
 * [trailing] segue como alvo próprio. [leading] substitui o círculo com ícone ([icon] é
 * ignorado). [meta] nulo esconde a segunda linha; [metaMaxLines] corta a meta com reticências.
 */
@Composable
internal fun WaitingRow(
    icon: ImageVector,
    title: String,
    meta: String?,
    contentDescription: String,
    onClick: (() -> Unit)?,
    tag: String,
    modifier: Modifier = Modifier,
    metaMaxLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .semantics { this.contentDescription = contentDescription }
            .testTag(tag)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        if (leading != null) {
            leading()
        } else {
            Box(
                modifier = Modifier
                    .size(metrics.grid * 5)
                    .clip(CircleShape)
                    .background(colors.surfaceSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SaqzIcon(icon = icon, tint = colors.textSecondary)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2)) {
            Text(
                text = title,
                style = SaqzTheme.typography.compactTitle,
                color = colors.textPrimary,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = SaqzTheme.typography.compactMeta,
                    color = colors.textSecondary,
                    maxLines = metaMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}
```

### 5. Criar `PRES/ui/components/OwnChargeTicket.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme

/** `testTag` do ticket e do botão de copiar: cada tela responde pelo próprio inventário. */
@Immutable
internal data class OwnChargeTicketTags(
    val card: String,
    val copy: String,
)

/**
 * Ticket de "o que eu devo" (VUL-220): eyebrow azul em caixa alta, valor em `display`, chip do
 * vencimento, recebedor do Pix e UM verbo — copiar a chave. Pagar é manual (decisão do fluxo 5).
 *
 * - [headerChip] é o chip Brand ao lado do eyebrow (na Início, o nome do grupo). Com ele, o
 *   chip do vencimento fica ao lado do valor; sem ele (detalhe do grupo), o chip do vencimento
 *   sobe para a linha do eyebrow e o valor fica sozinho.
 * - [dueChipOverdue] escolhe o tom: vencida é âmbar com ponto — lembrete, não alarme (decisão
 *   do VUL-202); no prazo é neutro, sem ponto.
 * - [onCopy] nulo (grupo sem chave Pix) esconde a linha do recebedor e o botão.
 * - [onClick] nulo = card sem clique. Com [onClick], [contentDescription] é o rótulo do clique e
 *   a descrição do card (na Início, o nome do grupo).
 * - [details] entra entre o valor e o recebedor (o detalhe lista ali as cobranças em aberto) e
 *   [footnote] fecha o card em `caption`, depois do botão.
 */
@Composable
internal fun OwnChargeTicket(
    eyebrow: String,
    amountLabel: String,
    dueChipLabel: String,
    dueChipOverdue: Boolean,
    headerChip: String?,
    countLabel: String?,
    receiverLabel: String?,
    copied: Boolean,
    copyLabel: String,
    copiedLabel: String,
    onCopy: (() -> Unit)?,
    onClick: (() -> Unit)?,
    contentDescription: String?,
    tags: OwnChargeTicketTags,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzCard(
        cornerRadius = metrics.blockRadius,
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .testTag(tags.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Eyebrow em caixa alta no próprio texto: a escala não transforma.
            Text(
                text = eyebrow.uppercase(),
                style = SaqzTheme.typography.eyebrow,
                color = colors.primary,
                modifier = Modifier.weight(1f),
            )
            if (headerChip != null) {
                SaqzStatusChip(text = headerChip, tone = SaqzChipTone.Brand)
            } else {
                DueChip(label = dueChipLabel, overdue = dueChipOverdue)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = amountLabel,
                style = SaqzTheme.typography.display,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (headerChip != null) {
                DueChip(label = dueChipLabel, overdue = dueChipOverdue)
            }
        }
        countLabel?.let {
            Text(text = it, style = SaqzTheme.typography.support, color = colors.textSecondary)
        }
        details?.invoke(this)
        if (onCopy != null) {
            receiverLabel?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(metrics.grid),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaqzIcon(SaqzIcons.CreditCard, tint = colors.textSecondary, size = PixIconSize)
                    Text(text = it, style = SaqzTheme.typography.support, color = colors.textSecondary)
                }
            }
            SaqzButton(
                label = if (copied) copiedLabel else copyLabel,
                onClick = onCopy,
                variant = if (copied) SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,
                fullWidth = true,
                leadingContent = if (copied) {
                    { tint -> SaqzIcon(SaqzIcons.Check, tint = tint, size = PixCheckSize) }
                } else {
                    null
                },
                modifier = Modifier.testTag(tags.copy),
            )
        }
        footnote?.let {
            Text(text = it, style = SaqzTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun DueChip(label: String, overdue: Boolean) = SaqzStatusChip(
    text = label,
    tone = if (overdue) SaqzChipTone.Warning else SaqzChipTone.Neutral,
    dot = overdue,
)

private val PixIconSize = 16.dp
private val PixCheckSize = 18.dp
```

### 6. Editar `PRES/ui/home/HomeScreen.kt` (2 edições; 914 → 677 linhas)

**6.2 (aplicar primeiro) — linhas 335–651.** O intervalo começa na linha `@Composable` imediatamente acima de `private fun HomeHero(` e termina no `}` que fecha `HomeResponseButton` — a linha logo acima da linha em branco que antecede o KDoc `/**` / ` * Seções extras que aparecem abaixo do hero só quando o membro está em espera:`. Dentro dele estão, nesta ordem: `HomeHero`, `HomeDeadlineLine`, `SpotsLeftMax`, as 11 constantes `Hero*`, `HomeAttendanceControls`, `HomeResponseRow`, `HomeResponseVariant`, `HomeAnsweredStatus`, `HomeResponseButton`. Substitua o intervalo inteiro por:

```kotlin
@Composable
private fun HomeHero(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = game.display,
        meta = game.meta,
        trailing = { SaqzStatusChip(text = game.groupName, tone = SaqzChipTone.Inverse) },
        modifier = Modifier.testTag(HomeTags.NextGame),
    ) {
        HeroDeadlineLine(text = game.deadline, open = game.confirmationOpen)
        HomeAttendanceControls(
            game = game,
            responding = responding,
            responseFailed = responseFailed,
            onIntent = onIntent,
        )
        // Em espera, as seções abaixo do hero já mostram o roster: a linha some.
        if (game.ownAttendance != AttendanceStatus.Waitlisted) {
            val spotsLeft = game.capacity - game.confirmedCount
            HeroRosterRow(
                names = game.rosterNames,
                summary = game.confirmedSummary,
                spotsChip = if (spotsLeft in 1..SpotsLeftMax) {
                    stringResource(Res.string.home_spots_left, spotsLeft)
                } else {
                    null
                },
            )
        }
    }
}

/** Escassez que muda comportamento: abaixo disso o "9 de 12" vira "restam N". */
private const val SpotsLeftMax = 3

/**
 * Seletor de presença do hero: o adaptador da Início para o `HeroAttendanceControls` — traduz
 * `HomeNextGameUi`/`HomeIntent` e passa os textos e as tags desta tela. Vive fora do `HomeHero`
 * porque o hero do admin (VUL-192) usa o mesmo bloco: dono e admin são atletas do grupo e
 * respondem presença no mesmo lugar que todo mundo.
 */
@Composable
internal fun ColumnScope.HomeAttendanceControls(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    HeroAttendanceControls(
        status = game.ownAttendance,
        confirmationOpen = game.confirmationOpen,
        responding = responding,
        responseFailed = responseFailed,
        waitlistKind = game.waitlistKind,
        waitlistPosition = game.waitlistPosition,
        onRespond = { onIntent(HomeIntent.Respond(it)) },
        onViewGame = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
        texts = HeroAttendanceTexts(
            yes = stringResource(Res.string.home_response_yes),
            no = stringResource(Res.string.home_response_no),
            confirmed = stringResource(Res.string.home_status_confirmed),
            declined = stringResource(Res.string.home_status_declined),
            change = stringResource(Res.string.home_attendance_change),
            cancel = stringResource(Res.string.home_attendance_cancel),
            error = stringResource(Res.string.home_response_error),
        ),
        tags = HomeHeroAttendanceTags,
    )
}

private val HomeHeroAttendanceTags = HeroAttendanceTags(
    yes = HomeTags.ResponseYes,
    no = HomeTags.ResponseNo,
    change = HomeTags.ResponseChange,
    cancel = HomeTags.ResponseCancel,
    error = HomeTags.ResponseError,
)
```

**6.1 — linhas 3–92 (bloco de imports inteiro, de `import androidx.compose.foundation.Image` até `import org.jetbrains.compose.resources.stringResource`).** Substitua por (saem `CircleShape`, `getValue`, `mutableStateOf`, `remember`, `setValue`, `SaqzAvatarStack`, `SaqzButtonSize`, `AttendanceIntent`; entram os 6 de `ui.components`):

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.saqzInitials
import br.com.saqz.designsystem.resources.Res as DsRes
import br.com.saqz.designsystem.resources.saqz_mark
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeToast
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceControls
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTags
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTexts
import br.com.saqz.groups.presentation.ui.components.HeroDeadlineLine
import br.com.saqz.groups.presentation.ui.components.HeroOutlineAlpha
import br.com.saqz.groups.presentation.ui.components.HeroRosterRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_attendance_cancel
import br.com.saqz.groups.resources.home_attendance_change
import br.com.saqz.groups.resources.home_error_message
import br.com.saqz.groups.resources.home_error_title
import br.com.saqz.groups.resources.home_game_next
import br.com.saqz.groups.resources.home_admin_group_chip
import br.com.saqz.groups.resources.home_groups_title
import br.com.saqz.groups.resources.home_groups_view_all
import br.com.saqz.groups.resources.home_greeting
import br.com.saqz.groups.resources.home_no_game_action
import br.com.saqz.groups.resources.home_no_game_description
import br.com.saqz.groups.resources.home_no_game_hero_title
import br.com.saqz.groups.resources.home_notifications_cd
import br.com.saqz.groups.resources.home_response_error
import br.com.saqz.groups.resources.home_response_no
import br.com.saqz.groups.resources.home_response_yes
import br.com.saqz.groups.resources.home_retry
import br.com.saqz.groups.resources.home_spots_left
import br.com.saqz.groups.resources.home_status_confirmed
import br.com.saqz.groups.resources.home_status_declined
import br.com.saqz.groups.resources.home_toast_confirmed
import br.com.saqz.groups.resources.home_toast_declined
import br.com.saqz.groups.resources.home_toast_pix_copied
import br.com.saqz.groups.resources.home_toast_waitlisted
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
```

`HomeNoGame` não muda: o `HeroOutlineAlpha` que ele usa agora vem do import.

### 7. Editar `PRES/ui/home/HomeAdminSections.kt` (4 edições; 528 → 400 linhas)

**7.4 (aplicar primeiro) — linhas 404–452: a função `HomeWaitingRow` inteira** (de `@Composable` acima de `private fun HomeWaitingRow(` até o `}` que a fecha, logo antes do KDoc `* Atalhos rápidos do gestor`). Substitua por:

```kotlin
@Composable
private fun HomeWaitingRow(
    item: HomeWaitingItem,
    onIntent: (HomeIntent) -> Unit,
) {
    WaitingRow(
        icon = item.icon,
        title = item.title,
        meta = item.meta,
        contentDescription = item.a11y,
        onClick = { onIntent(item.action) },
        tag = item.tag,
        // A Início nunca limitou a meta; o default de 1 linha é do detalhe do grupo.
        metaMaxLines = Int.MAX_VALUE,
    ) {
        when (item.trailing) {
            is WaitingTrailing.WarningChip -> SaqzStatusChip(
                text = item.trailing.text,
                tone = SaqzChipTone.Warning,
                dot = true,
            )
            WaitingTrailing.Chevron -> SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
        }
    }
}
```

**7.3 — linhas 177–273:** do KDoc `/**` / ` * Placar em 3 colunas sobre o hero azul: painel branco a 10%, traços brancos a 18%,` até a linha `private val ScoreValueSize = 28.sp`, inclusive (`AdminScoreBoard`, `ScoreDivider`, `AdminScoreColumn` e as 5 constantes `Score*`). Substitua por:

```kotlin
private val HomeScoreBoardTags = AttendanceScoreBoardTags(
    going = HomeAdminTags.ScoreGoing,
    out = HomeAdminTags.ScoreOut,
    pending = HomeAdminTags.ScorePending,
)
```

**7.2 — linhas 119–127, dentro de `HomeAdminHero`.** Trecho antigo:

```kotlin
        AdminScoreBoard(
            going = game.confirmedCount,
            out = game.declinedCount,
            pending = game.pendingCount,
            // O placar é o dado que o admin mais quer detalhar — tocar abre o jogo,
            // onde mora a lista de quem respondeu (e a cobrança de presença).
            onClick = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
        )
        HomeDeadlineLine(text = game.deadline, open = game.confirmationOpen)
```

Trecho novo:

```kotlin
        AttendanceScoreBoard(
            going = game.confirmedCount,
            out = game.declinedCount,
            pending = game.pendingCount,
            // O placar é o dado que o admin mais quer detalhar — tocar abre o jogo,
            // onde mora a lista de quem respondeu (e a cobrança de presença).
            onClick = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
            tags = HomeScoreBoardTags,
        )
        HeroDeadlineLine(text = game.deadline, open = game.confirmationOpen)
```

**7.1 — linhas 3–73 (bloco de imports inteiro).** Substitua por:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeAdminGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminReadModelUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoard
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoardTags
import br.com.saqz.groups.presentation.ui.components.HeroDeadlineLine
import br.com.saqz.groups.presentation.ui.components.HeroOutlineAlpha
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_cd_shortcut_create_game
import br.com.saqz.groups.resources.home_admin_cd_shortcut_invite
import br.com.saqz.groups.resources.home_admin_cd_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_cd_waiting_monthly
import br.com.saqz.groups.resources.home_admin_cd_waiting_settle
import br.com.saqz.groups.resources.home_game_next
import br.com.saqz.groups.resources.home_admin_shortcuts_create_game
import br.com.saqz.groups.resources.home_admin_shortcuts_invite
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_waiting_entry_chip
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests_meta
import br.com.saqz.groups.resources.home_admin_waiting_monthly
import br.com.saqz.groups.resources.home_admin_waiting_monthly_meta
import br.com.saqz.groups.resources.home_admin_waiting_settle
import br.com.saqz.groups.resources.home_admin_waiting_settle_meta
import br.com.saqz.groups.resources.home_admin_waiting_title
import br.com.saqz.groups.resources.home_no_game_description
import br.com.saqz.groups.resources.home_no_game_hero_title
import org.jetbrains.compose.resources.stringResource
```

`HomeAdminNoGame` e `HomeShortcutCard` não mudam.

### 8. Substituir `PRES/ui/home/HomeUpcomingSection.kt` pelo arquivo completo abaixo (108 → 70 linhas)

Somem `HomeUpcomingRow` e `DateBoxSize`; o chip de status vira o slot `trailing`.

```kotlin
package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeUpcomingGameUi
import br.com.saqz.groups.presentation.home.HomeUpcomingStatus
import br.com.saqz.groups.presentation.ui.components.UpcomingGameRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_upcoming_title
import org.jetbrains.compose.resources.stringResource

internal object HomeUpcomingTags {
    const val Section = "home-upcoming"

    fun row(gameId: String) = "home-upcoming-$gameId"
}

/**
 * VUL-221 — os jogos depois do hero, em todos os grupos: data, grupo e hora, quantos
 * confirmaram e o estado do próprio usuário. Toque abre o jogo. Some sem jogos: a Home
 * é do "agora", e uma seção vazia não é informação.
 */
@Composable
internal fun HomeUpcomingSection(
    games: List<HomeUpcomingGameUi>,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) return
    Column(
        modifier = modifier.testTag(HomeUpcomingTags.Section),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_upcoming_title))
        SaqzCard(padded = false) {
            games.forEachIndexed { index, game ->
                if (index > 0) SaqzDivider()
                UpcomingGameRow(
                    day = game.day,
                    month = game.month,
                    title = game.title,
                    meta = game.meta,
                    contentDescription = game.contentDescription,
                    onClick = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
                    tag = HomeUpcomingTags.row(game.gameId),
                ) {
                    SaqzStatusChip(
                        text = game.statusLabel,
                        tone = when (game.status) {
                            HomeUpcomingStatus.Going -> SaqzChipTone.Success
                            HomeUpcomingStatus.Waitlisted -> SaqzChipTone.Warning
                            HomeUpcomingStatus.Pending, HomeUpcomingStatus.Out -> SaqzChipTone.Neutral
                        },
                        dot = game.status == HomeUpcomingStatus.Going || game.status == HomeUpcomingStatus.Waitlisted,
                    )
                }
            }
        }
    }
}
```

### 9. Editar `PRES/ui/home/HomeOwnChargesSection.kt` (2 edições; 260 → 204 linhas)

**9.2 (aplicar primeiro) — linhas 71–150:** de `@Composable` acima de `private fun HomeOwnChargeTicket(` até a linha `private val PixCheckSize = 18.dp`, inclusive. Substitua por:

```kotlin
@Composable
private fun HomeOwnChargeTicket(
    group: HomeOwnChargeGroupUi,
    copied: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val copyPix = { onIntent(HomeIntent.CopyPix(group.groupId)) }
    OwnChargeTicket(
        eyebrow = group.competence,
        amountLabel = group.amountLabel,
        dueChipLabel = group.dueLabel,
        dueChipOverdue = group.overdue,
        headerChip = group.groupName,
        countLabel = group.countLabel,
        receiverLabel = group.pix?.let { stringResource(Res.string.home_own_charge_pix_receiver, it.label ?: it.key) },
        copied = copied,
        copyLabel = stringResource(Res.string.group_cashbox_pix_copy),
        copiedLabel = stringResource(Res.string.home_own_charge_copied),
        // Sem chave Pix o ticket não tem verbo: somem a linha do recebedor e o botão.
        onCopy = copyPix.takeIf { group.pix != null },
        onClick = { onIntent(HomeIntent.OpenGroup(group.groupId)) },
        contentDescription = group.groupName,
        tags = OwnChargeTicketTags(
            card = HomeTags.ownCharge(group.groupId),
            copy = HomeTags.ownChargePixCopy(group.groupId),
        ),
    )
}
```

**9.1 — linhas 3–39 (bloco de imports inteiro).** Substitua por:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeOwnChargeGroupUi
import br.com.saqz.groups.presentation.home.HomeOwnChargesUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicket
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicketTags
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_pix_copy
import br.com.saqz.groups.resources.home_own_charge_copied
import br.com.saqz.groups.resources.home_own_charge_pix_receiver
import br.com.saqz.groups.resources.own_charges_title
import org.jetbrains.compose.resources.stringResource
```

O KDoc e o corpo de `HomeOwnChargesSection`, a faixa `HomeOwnChargesBanner`, os previews e `previewOwnCharges*` não mudam.

### 10. Conferência mecânica

```sh
git diff --stat origin/main -- mobile/features/groups/presentation/src/commonMain
```

Esperado nos 4 arquivos editados: `HomeScreen.kt` 914→677, `HomeAdminSections.kt` 528→400, `HomeUpcomingSection.kt` 108→70, `HomeOwnChargesSection.kt` 260→204 linhas (`wc -l`). Número diferente = edição errada; refaça o passo do arquivo.

## Testes

### (a) O que prova que a Início não mudou — nenhum destes arquivos é editado

- `TEST/ui/home/HomeScreenTest.kt` (27 testes: hero, RSVP, alterar/cancelar, prazo encerrado, espera, placar do gestor, "Esperando você", ticket, chave copiada, faixa)
- `TEST/ui/home/HomeUpcomingScreenTest.kt` (2), `TEST/ui/home/HomeOwnChargesBannerPriorityTest.kt`
- `TEST/home/HomeViewModelTest.kt`, `TEST/home/HomeUpcomingViewModelTest.kt`
- Roborazzi: `src/androidHostTest/.../ui/home/HomeScreenshotTest.kt` (23 cenas), `HomeJourneyScreenshotTest.kt` (8), `HomeUpcomingScreenshotTest.kt` (3) — ver a seção seguinte.

Rodam com os comandos da seção *Gates*. Se algum quebrar, o erro está neste PR.

### (b) Testes novos (rodam em `iosSimulatorArm64Test`)

#### `TEST/ui/components/HeroAttendanceTest.kt` (arquivo completo — 9 testes)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HeroAttendanceTest {
    // De propósito a cópia do detalhe do grupo, não a da Início: prova que nada está cravado.
    private val texts = HeroAttendanceTexts(
        yes = "Vou",
        no = "Não vou",
        confirmed = "Sua presença está confirmada.",
        declined = "Você não vai jogar.",
        change = "Alterar",
        cancel = "Cancelar",
        error = "Não foi possível salvar sua resposta. Tente novamente.",
    )
    private val tags = HeroAttendanceTags(
        yes = "t-yes",
        no = "t-no",
        change = "t-change",
        cancel = "t-cancel",
        error = "t-error",
    )

    @Test
    fun unansweredShowsBothBigButtonsWithTheGivenTagsAndTexts() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        setControls(status = null, onRespond = responses::add)

        onNodeWithText("Vou").assertIsDisplayed()
        onNodeWithText("Não vou").assertIsDisplayed()
        onNodeWithTag("t-yes").assertHeightIsEqualTo(52.dp).performClick()
        onNodeWithTag("t-no").assertHeightIsEqualTo(52.dp).performClick()
        onAllNodesWithTag("t-change").assertCountEquals(0)
        onAllNodesWithTag("t-error").assertCountEquals(0)

        assertEquals(listOf(AttendanceIntent.Confirm, AttendanceIntent.Decline), responses)
    }

    @Test
    fun confirmedShowsTheGivenConfirmedTextAndTheChangeButton() = runComposeUiTest {
        setControls(status = AttendanceStatus.Confirmed)

        onNodeWithText("Sua presença está confirmada.").assertIsDisplayed()
        onNodeWithTag("t-change").assertIsDisplayed()
        onNodeWithText("Alterar").assertIsDisplayed()
        onAllNodesWithTag("t-yes").assertCountEquals(0)
        onAllNodesWithTag("t-no").assertCountEquals(0)
    }

    @Test
    fun changeRevealsTheSmallButtonsAndCancelGoesBackToThePanel() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        setControls(status = AttendanceStatus.Confirmed, onRespond = responses::add)

        onNodeWithTag("t-change").performClick()

        onNodeWithTag("t-yes").assertHeightIsEqualTo(44.dp)
        onNodeWithTag("t-no").assertHeightIsEqualTo(44.dp)
        onNodeWithText("Cancelar").assertIsDisplayed()
        onAllNodesWithTag("t-change").assertCountEquals(0)

        onNodeWithTag("t-cancel").performClick()

        onNodeWithTag("t-change").assertIsDisplayed()
        onAllNodesWithTag("t-yes").assertCountEquals(0)
        assertEquals(emptyList<AttendanceIntent>(), responses)
    }

    @Test
    fun changingTheAnswerEmitsTheNewChoice() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        setControls(status = AttendanceStatus.Confirmed, onRespond = responses::add)

        onNodeWithTag("t-change").performClick()
        onNodeWithTag("t-no").performClick()

        assertEquals(listOf(AttendanceIntent.Decline), responses)
    }

    @Test
    fun closedConfirmationDisablesBothButtons() = runComposeUiTest {
        setControls(status = null, confirmationOpen = false)

        onNodeWithTag("t-yes").assertIsNotEnabled()
        onNodeWithTag("t-no").assertIsNotEnabled()
    }

    @Test
    fun closedConfirmationDisablesChange() = runComposeUiTest {
        setControls(status = AttendanceStatus.Confirmed, confirmationOpen = false)

        onNodeWithTag("t-change").assertIsNotEnabled()
    }

    @Test
    fun waitlistedShowsTheQueueBlockInsteadOfTheYesButton() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        var gameViews = 0
        setControls(
            status = AttendanceStatus.Waitlisted,
            waitlistKind = HomeWaitlistKind.Reserva,
            waitlistPosition = 1,
            onRespond = responses::add,
            onViewGame = { gameViews += 1 },
        )

        onAllNodesWithTag("t-yes").assertCountEquals(0)
        onAllNodesWithTag("t-no").assertCountEquals(0)
        onAllNodesWithTag("t-change").assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.ReservaChip).assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.ReservaViewGame).performClick()
        onNodeWithTag(HomeWaitlistTags.ReservaLeave).performClick()

        assertEquals(1, gameViews)
        assertEquals(listOf(AttendanceIntent.Decline), responses)
    }

    @Test
    fun responseFailureRendersTheGivenErrorUnderTheGivenTag() = runComposeUiTest {
        setControls(status = null, responseFailed = true)

        onNodeWithTag("t-error").assertIsDisplayed()
        onNodeWithText("Não foi possível salvar sua resposta. Tente novamente.").assertIsDisplayed()
    }

    @Test
    fun alertLineComposesTheActionSlot() = runComposeUiTest {
        setContent {
            SaqzTheme {
                HeroAlertLine(text = "Não foi possível abrir o mapa.", tag = null) {
                    Box(modifier = Modifier.size(24.dp).testTag("t-action"))
                }
            }
        }

        onNodeWithText("Não foi possível abrir o mapa.").assertIsDisplayed()
        onNodeWithTag("t-action").assertIsDisplayed()
    }

    private fun ComposeUiTest.setControls(
        status: AttendanceStatus?,
        confirmationOpen: Boolean = true,
        responding: Boolean = false,
        responseFailed: Boolean = false,
        waitlistKind: HomeWaitlistKind? = null,
        waitlistPosition: Long? = null,
        onRespond: (AttendanceIntent) -> Unit = {},
        onViewGame: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            Column {
                HeroAttendanceControls(
                    status = status,
                    confirmationOpen = confirmationOpen,
                    responding = responding,
                    responseFailed = responseFailed,
                    waitlistKind = waitlistKind,
                    waitlistPosition = waitlistPosition,
                    onRespond = onRespond,
                    onViewGame = onViewGame,
                    texts = texts,
                    tags = tags,
                )
            }
        }
    }
}
```

#### `TEST/ui/components/WaitingRowTest.kt` (arquivo completo — 3 testes)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WaitingRowTest {
    @Test
    fun nullOnClickHasNoClickActionAndTheTrailingButtonIsTheOnlyTarget() = runComposeUiTest {
        var reminders = 0
        setContent {
            SaqzTheme {
                WaitingRow(
                    icon = SaqzIcons.Megaphone,
                    title = "2 pessoas sem resposta",
                    meta = "Encerra hoje às 18h",
                    contentDescription = "2 pessoas sem resposta. Encerra hoje às 18h.",
                    onClick = null,
                    tag = "row",
                ) {
                    SaqzButton(
                        label = "Avisar",
                        onClick = { reminders += 1 },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                        modifier = Modifier.testTag("trailing"),
                    )
                }
            }
        }

        onNodeWithTag("row").assertIsDisplayed().assertHasNoClickAction()
        onNodeWithTag("trailing").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, reminders)
    }

    @Test
    fun clickableRowForwardsTheClickAndCarriesTheDescription() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                WaitingRow(
                    icon = SaqzIcons.CreditCard,
                    title = "2 mensalidades a receber",
                    meta = "R$ 140,00 · agosto",
                    contentDescription = "2 mensalidades a receber em Vôlei do CERET",
                    onClick = { clicks += 1 },
                    tag = "row",
                ) {
                    Box(modifier = Modifier.size(22.dp).testTag("trailing"))
                }
            }
        }

        onNodeWithContentDescription("2 mensalidades a receber em Vôlei do CERET").assertHasClickAction()
        // A linha clicável funde os filhos: o trailing só existe na árvore não fundida.
        onNodeWithTag("trailing", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("row").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun leadingReplacesTheIconCircleAndNullMetaHidesTheSecondLine() = runComposeUiTest {
        setContent {
            SaqzTheme {
                WaitingRow(
                    icon = SaqzIcons.Users,
                    title = "26 pessoas",
                    meta = null,
                    contentDescription = "26 pessoas no grupo",
                    onClick = {},
                    tag = "row",
                    leading = { Box(modifier = Modifier.size(40.dp).testTag("leading")) },
                ) {
                    Box(modifier = Modifier.size(22.dp))
                }
            }
        }

        onNodeWithTag("leading", useUnmergedTree = true).assertIsDisplayed()
        // Sem meta, o título é o único texto que a linha (clicável, logo fundida) carrega.
        val texts = onNodeWithTag("row").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.Text) { null }
        assertEquals(listOf("26 pessoas"), texts?.map { it.text })
    }
}
```

#### `TEST/ui/components/OwnChargeTicketTest.kt` (arquivo completo — 6 testes)

```kotlin
package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OwnChargeTicketTest {
    @Test
    fun withoutOnCopyNeitherTheReceiverNorTheButtonIsComposed() = runComposeUiTest {
        setTicket(onCopy = null)

        onNodeWithTag("card").assertIsDisplayed()
        onAllNodesWithTag("copy").assertCountEquals(0)
        onAllNodesWithText("Copiar chave Pix").assertCountEquals(0)
        onAllNodesWithText("Pix de Lucas Prado").assertCountEquals(0)
    }

    @Test
    fun copyButtonForwardsTheClick() = runComposeUiTest {
        var copies = 0
        setTicket(onCopy = { copies += 1 })

        onNodeWithText("Pix de Lucas Prado").assertIsDisplayed()
        onNodeWithText("Copiar chave Pix").assertIsDisplayed()
        onNodeWithTag("copy").performClick()

        assertEquals(1, copies)
    }

    @Test
    fun copiedSwapsTheButtonLabel() = runComposeUiTest {
        setTicket(copied = true)

        onNodeWithText("Chave copiada").assertIsDisplayed()
        onAllNodesWithText("Copiar chave Pix").assertCountEquals(0)
    }

    @Test
    fun detailsSlotAndFootnoteAreComposed() = runComposeUiTest {
        setTicket(footnote = "Pagamento é manual: depois de pagar, o admin dá baixa no caixa do grupo.") {
            Text(text = "Mensalidade · Agosto", modifier = Modifier.testTag("details"))
        }

        onNodeWithTag("details").assertIsDisplayed()
        onNodeWithText("Pagamento é manual: depois de pagar, o admin dá baixa no caixa do grupo.").assertIsDisplayed()
    }

    @Test
    fun dueChipRendersOnceWithOrWithoutTheHeaderChip() = runComposeUiTest {
        setTicket(headerChip = null)

        onAllNodesWithText("Venceu em 10/08").assertCountEquals(1)
        onAllNodesWithText("Vôlei do CERET").assertCountEquals(0)
    }

    @Test
    fun nullOnClickLeavesTheCardWithoutClickAction() = runComposeUiTest {
        setTicket(onClick = null)

        onNodeWithTag("card").assertHasNoClickAction()
    }

    private fun ComposeUiTest.setTicket(
        headerChip: String? = null,
        copied: Boolean = false,
        onCopy: (() -> Unit)? = {},
        onClick: (() -> Unit)? = null,
        contentDescription: String? = null,
        footnote: String? = null,
        details: (@Composable ColumnScope.() -> Unit)? = null,
    ) = setContent {
        SaqzTheme {
            OwnChargeTicket(
                eyebrow = "Em aberto",
                amountLabel = "R$ 95,00",
                dueChipLabel = "Venceu em 10/08",
                dueChipOverdue = true,
                headerChip = headerChip,
                countLabel = null,
                receiverLabel = "Pix de Lucas Prado",
                copied = copied,
                copyLabel = "Copiar chave Pix",
                copiedLabel = "Chave copiada",
                onCopy = onCopy,
                onClick = onClick,
                contentDescription = contentDescription,
                tags = OwnChargeTicketTags(card = "card", copy = "copy"),
                footnote = footnote,
                details = details,
            )
        }
    }
}
```

`AttendanceScoreBoard`, `UpcomingGameRow` e `DateTile` não ganham teste próprio: as tags, os textos e os cliques deles já são cobertos por `HomeScreenTest`/`HomeUpcomingScreenTest` através da Início.

## Prova de não-regressão visual

O `verifyRoborazzi*` tolera até 1% de pixels diferentes, então **a prova é por hash**: os 34 PNGs da Início antes e depois têm de ter o mesmo SHA-256. As cenas da Início caem em `mobile/features/groups/presentation/screenshots/{vul-191,vul-192,vul-202,vul-221,vul-222}/` (pasta ignorada pelo git). O `testAndroidHostTest --rerun` força a suíte a executar mesmo com cache do Gradle — sem isso um cache hit não regrava os PNGs.

**Antes** (passo 0, na branch recém-criada, sem nenhuma edição), da raiz do worktree:

```sh
SHOTS=mobile/features/groups/presentation/screenshots
rm -rf "$SHOTS"/vul-191 "$SHOTS"/vul-192 "$SHOTS"/vul-202 "$SHOTS"/vul-221 "$SHOTS"/vul-222 "$SHOTS"/_antes-B
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest :features:groups:presentation:testAndroidHostTest --rerun
mkdir -p "$SHOTS/_antes-B"
for d in vul-191 vul-192 vul-202 vul-221 vul-222; do cp -R "$SHOTS/$d" "$SHOTS/_antes-B/$d"; done
( cd "$SHOTS/_antes-B" && find vul-191 vul-192 vul-202 vul-221 vul-222 -name '*.png' | sort | xargs shasum -a 256 ) > "$SHOTS/_antes-B.sha256"
wc -l < "$SHOTS/_antes-B.sha256"
```

O último comando tem de imprimir `34`. Se imprimir outro número (ou o `cp` reclamar de pasta inexistente), rode o mesmo bloco trocando a linha do gradle por `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest --rerun-tasks`. Se ainda assim não der 34: parar e avisar o orquestrador.

**Depois** (com todos os passos aplicados e os gates verdes):

```sh
SHOTS=mobile/features/groups/presentation/screenshots
rm -rf "$SHOTS"/vul-191 "$SHOTS"/vul-192 "$SHOTS"/vul-202 "$SHOTS"/vul-221 "$SHOTS"/vul-222
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest :features:groups:presentation:testAndroidHostTest --rerun
( cd "$SHOTS" && find vul-191 vul-192 vul-202 vul-221 vul-222 -name '*.png' | sort | xargs shasum -a 256 ) > "$SHOTS/_depois-B.sha256"
diff "$SHOTS/_antes-B.sha256" "$SHOTS/_depois-B.sha256" && echo "DIFF ZERO: $(wc -l < "$SHOTS/_depois-B.sha256") PNGs da Início idênticos"
```

Tem de imprimir `DIFF ZERO: 34 PNGs da Início idênticos`. Cole essa linha **e** o conteúdo de `_depois-B.sha256` no corpo do PR (dentro de um bloco `<details>`). Se o `diff` listar qualquer arquivo: NÃO abra o PR; compare o passo do componente correspondente com esta receita caractere a caractere, corrija a transcrição e repita o bloco "Depois". Se a diferença persistir com o código igual ao da receita: parar e avisar o orquestrador com a lista de arquivos do `diff`.

O PR não leva print de tela: nada visual mudou, e o corpo tem de dizer isso explicitamente, com a prova de hash no lugar.

## Gates

Da raiz do worktree, nesta ordem; colar o resumo (BUILD SUCCESSFUL + contagem de testes) no corpo do PR:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.ui.components.*" --tests "br.com.saqz.groups.presentation.ui.home.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
```

O quarto gate é o bloco **"Depois"** da seção anterior (ele roda o `recordRoborazziAndroidHostTest` do módulo inteiro e compila o alvo Android). A API pública do módulo (`HomeScreen`, `HomeRoot`, `HomeOwnChargesBannerRoot`) não muda, então nenhum outro módulo é afetado.

## Critérios de aceite

- [ ] `git diff --name-only origin/main` lista exatamente os 12 arquivos da seção *Arquivos*.
- [ ] Nenhum arquivo de `home/`, `details/`, `ui/details/`, `composeResources/` ou `core/design-system` no diff; `HomeWaitlistSections.kt` e `HomeRoot.kt` intactos.
- [ ] Nenhum teste existente editado; `HomeScreenTest`, `HomeUpcomingScreenTest`, `HomeViewModelTest` verdes.
- [ ] `HeroAttendanceTest` (9), `WaitingRowTest` (3) e `OwnChargeTicketTest` (6) verdes.
- [ ] `DIFF ZERO: 34 PNGs da Início idênticos` no corpo do PR, com os hashes.
- [ ] `grep -rn "HomeDeadlineLine\|HomeResponseRow\|HomeAnsweredStatus\|HomeResponseButton\|AdminScoreBoard\|HomeUpcomingRow" mobile/features/groups/presentation/src` não devolve nada.
- [ ] `detektAll` do módulo verde, sem baseline novo e sem `@Suppress` novo.
- [ ] Diff ≤ 2000 linhas (medido nesta receita: **1958** = 1286 de arquivos novos + 672 nos 4 editados). A margem é de 42 linhas: não acrescente nada que não esteja aqui.

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-extrai-pecas-da-home origin/main`.
2. Commits pequenos em PT-BR no padrão do repo (`refactor(home): ...`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR.
4. PR contra `main`, aberto como ready (não draft), título `refactor(groups): peças do hero e das listas da Início viram componentes do módulo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções — esta receita mede 1958; os `@Preview` e a suíte Roborazzi das variantes novas ficaram de fora por isso.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
