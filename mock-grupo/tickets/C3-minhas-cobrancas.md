# C3 · Minhas cobranças enxutas

**Onda 2 · depende de: S, B, T, V1 · bloqueia: D · paralelo com: V2, C1, C2**

## Objetivo

Trocar a seção antiga de cobranças do detalhe do grupo (pendentes + histórico + card de Pix, tudo aberto) pelo desenho enxuto do mock:

1. **Quem deve** vê, logo abaixo do hero, UM ticket no molde da Início (`OwnChargeTicket`, do ticket B): competência e vencimento da pendência mais antiga, soma, as linhas das pendências, o recebedor + a chave Pix e o único verbo — copiar a chave. O histórico fica recolhido atrás de "Ver histórico (N)".
2. **Quem está em dia** não vê nada no topo: no fim da tela entra a linha "Tudo em dia", que abre o mesmo histórico.
3. Carregando e falha continuam isolados na seção (o resto do detalhe segue na tela).

Fatos do código que esta receita já resolveu (não reabrir):

- O rótulo "Copiar chave Pix" é a chave `group_cashbox_pix_copy` (`strings_caixa_grupo.xml:16`) — a mesma que `PRES/ui/finance/PixCard.kt:37` e `PRES/ui/home/HomeOwnChargesSection.kt:134` usam.
- `OwnChargeTicket` (ticket B) tem dois parâmetros além dos que o desenho cita: `contentDescription: String?` (obrigatório; aqui `null`, porque `onClick` é `null`) e `footnote: String?` (a nota depois do botão — é por ele que entram `own_charges_note` e `group_details_own_charges_no_pix`).
- `receiverLabel = null` no ticket: o recebedor é desenhado por este bloco dentro do contêiner `OwnChargesPix`, junto com a chave (o componente só desenha o recebedor; a chave como `Text` próprio é contrato do e2e).
- **Não existe flag de "vencida" por linha** em `OwnChargeUi` (`id, title, dueLabel, amountLabel, status` — `PRES/details/GroupDetailsContract.kt:132-138`). O único `overdue` do estado é `GroupOwnDebtUi.overdue`, da pendência mais antiga, e ele já pinta o chip do ticket. Por isso **toda linha usa `compactMeta` em `textSecondary`**, sem âmbar — diferente do PNG; vale a receita. Destacar só a primeira linha mentiria quando há duas vencidas.
- `SaqzIcons` não tem chevron vertical (só `ChevronLeft`/`ChevronRight` — `SaqzIcons.kt:73-74`): o botão do histórico é texto puro, e o `trailing` da linha "Tudo em dia" é o mesmo texto do botão.
- O PNG `MembroDeveUma` não mostra linha nenhuma com uma pendência só; aqui **sempre há uma linha por pendência, com o chip "Em aberto"** — a tag `group-details-own-charge-<id>` e o status descendente são contrato do e2e (CONTRATO §4).
- Ordem dentro do bloco, como no PNG `MembroHistorico`: ticket → card do histórico (quando aberto) → botão.
- `GroupOwnChargesSettledBlock` não tem intent para emitir, mas a assinatura é fixa (CONTRATO §2): o `@Suppress("UnusedParameter")` que o T deixou **fica** nesse bloco (não é `@Suppress` novo); só o KDoc de andaime sai. No `GroupOwnDebtBlock` não há `@Suppress`.
- `GroupDetailsPreviewData.member` (dono: D) tem pendência **sem** `debt` — combinação que o ViewModel nunca produz depois do V1. Com ela este bloco não desenha o ticket. Por isso as cenas e os testes daqui usam `GroupOwnDebtPreviewData` (arquivo novo), e nenhum teste fora deste ticket confere cobranças com `member`.
- Os PNGs de `screenshots/` são ignorados pelo git (`.gitignore:51`): os quatro PNGs antigos de `own-debt/` são só locais.

## Fora do escopo

- `DET/GroupOwnChargesSection.kt` **não é editado nem apagado**: Perfil → Mensalidades (`PRES/monthlypayments/OwnMonthlyPaymentsRoot.kt`) a reutiliza e o e2e `monthly-history` confere 12 linhas lá.
- `GroupDetailsScreen.kt`, `GroupDetailsPreviewData.kt`, Contract/ViewModel, qualquer `strings*.xml`, `E2E/`, `PRES/ui/components/`, `GroupDetailsTags.kt`, `GroupDetailsTestSupport.kt`, `GroupDetailsScreenshotSupport.kt`.
- Toast de "chave copiada": é do C1 (`GroupToastBlock`).
- Âmbar por linha vencida: depende de um campo novo em `OwnChargeUi` (não existe; ver dúvidas no PR).

## Arquivos

| Ação | Arquivo |
|---|---|
| reescrever | `DET/GroupOwnDebtBlock.kt` |
| criar | `DET/GroupOwnDebtPreviewData.kt` |
| reescrever | `TDET/GroupOwnDebtBlockTest.kt` |
| reescrever | `SDET/GroupOwnDebtScreenshotTest.kt` |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Passo a passo

### 1. Criar `DET/GroupOwnDebtPreviewData.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.GroupOwnDebtUi

/**
 * Os estados do bloco "Minhas cobranças", derivados de [GroupDetailsPreviewData]. Regra de
 * sempre: só o que o `GroupDetailsViewModel` produz — com pendência, `debt` chega JUNTO;
 * sem chave Pix, `pix` e `receiverLabel` somem juntos.
 */
internal object GroupOwnDebtPreviewData {
    private val charges = GroupDetailsPreviewData.ownCharges

    private val debt = GroupOwnDebtUi(
        eyebrow = "Mensalidade · Agosto",
        totalLabel = "R$ 95,00",
        dueLabel = "Venceu em 10/08",
        overdue = true,
        countLabel = "2 cobranças em aberto",
        receiverLabel = "Pix de Lucas Prado",
    )

    /** Duas pendências: a mais antiga vencida, a outra a vencer. */
    val owesTwo = GroupDetailsPreviewData.member.copy(ownCharges = charges.copy(debt = debt))

    /** Uma pendência só, ainda no prazo: chip neutro e sem contagem. */
    val owesOne = GroupDetailsPreviewData.member.copy(
        ownCharges = charges.copy(
            pending = charges.pending.drop(1),
            debt = GroupOwnDebtUi(
                eyebrow = "Jogo avulso",
                totalLabel = "R$ 25,00",
                dueLabel = "Vence em 28/08",
                overdue = false,
                receiverLabel = "Pix de Lucas Prado",
            ),
        ),
    )

    val pixCopied = owesTwo.copy(pixCopied = true)

    /** Grupo sem chave Pix: sem recebedor, sem botão, com a nota de combinar com o admin. */
    val noPix = GroupDetailsPreviewData.member.copy(
        ownCharges = charges.copy(pix = null, debt = debt.copy(receiverLabel = null)),
    )

    val settled = GroupDetailsPreviewData.memberOwnChargesSettled
    val loading = GroupDetailsPreviewData.memberOwnChargesLoading
    val failed = GroupDetailsPreviewData.memberOwnChargesFailed

    /** Dono também joga e também deve (papel de admin não é o contrário de atleta). */
    val adminOwes = GroupDetailsPreviewData.admin.copy(ownCharges = owesTwo.ownCharges)
}
```

### 2. Reescrever `DET/GroupOwnDebtBlock.kt` (arquivo completo — substitui o andaime do T)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupOwnDebtUi
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicket
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicketTags
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_pix_copy
import br.com.saqz.groups.resources.group_details_own_charges_history_hide
import br.com.saqz.groups.resources.group_details_own_charges_history_show
import br.com.saqz.groups.resources.group_details_own_charges_no_pix
import br.com.saqz.groups.resources.group_details_own_charges_settled_meta
import br.com.saqz.groups.resources.group_details_own_charges_settled_title
import br.com.saqz.groups.resources.home_own_charge_copied
import br.com.saqz.groups.resources.own_charges_failure
import br.com.saqz.groups.resources.own_charges_note
import br.com.saqz.groups.resources.own_charges_retry
import br.com.saqz.groups.resources.own_charges_status_cancelled
import br.com.saqz.groups.resources.own_charges_status_paid
import br.com.saqz.groups.resources.own_charges_status_pending
import br.com.saqz.groups.resources.own_charges_status_waived
import br.com.saqz.groups.resources.own_charges_title
import org.jetbrains.compose.resources.stringResource

// Medidas do mock fora da grade de 4/8.
private val ChargeLineVerticalPadding = 10.dp
private val FailureIconSize = 20.dp
private val PixReceiverIconSize = 16.dp
private val SkeletonAmountHeight = 34.dp
private val SkeletonCountHeight = 14.dp

/**
 * "O que eu devo neste grupo", logo abaixo do jogo. Só existe enquanto há o que dizer:
 * carregando, falha ou pendência. Em dia, quem fala é [GroupOwnChargesSettledBlock], no fim
 * da tela — os dois nunca emitem juntos, e é isso que mantém cada tag uma vez só na árvore.
 *
 * Pagar é manual (decisão do fluxo 5): o único verbo é copiar a chave Pix. A seção antiga
 * (`GroupOwnChargesSection`) continua existindo para Perfil → Mensalidades.
 */
@Composable
internal fun GroupOwnDebtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownCharges = state.ownCharges ?: return
    val debt = ownCharges.debt?.takeIf { ownCharges.pending.isNotEmpty() }
    if (!ownCharges.isLoading && !ownCharges.failed && debt == null) return
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.OwnCharges),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.own_charges_title))
        when {
            ownCharges.isLoading -> OwnDebtSkeleton()
            ownCharges.failed -> OwnDebtFailure(onIntent = onIntent)
            debt != null -> OwnDebtContent(
                ownCharges = ownCharges,
                debt = debt,
                pixCopied = state.pixCopied,
                onIntent = onIntent,
            )
        }
    }
}

/**
 * A linha "Tudo em dia" do fim da tela: só com cobranças carregadas, nenhuma pendência e
 * algum histórico — que ela abre e fecha. Sem histórico não há o que mostrar.
 *
 * [onIntent] fica sem uso de propósito: a assinatura é a de todo bloco do detalhe e este
 * não tem intent para emitir (abrir o histórico é estado visual).
 */
@Suppress("UnusedParameter")
@Composable
internal fun GroupOwnChargesSettledBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownCharges = state.ownCharges ?: return
    val settled = !ownCharges.isLoading && !ownCharges.failed &&
        ownCharges.pending.isEmpty() && ownCharges.history.isNotEmpty()
    if (!settled) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(Res.string.group_details_own_charges_settled_title)
    val meta = stringResource(Res.string.group_details_own_charges_settled_meta)
    val toggleLabel = historyToggleLabel(expanded = expanded, count = ownCharges.history.size)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.own_charges_title))
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            SaqzCard(padded = false) {
                WaitingRow(
                    icon = SaqzIcons.Check,
                    title = title,
                    meta = meta,
                    contentDescription = "$title. $meta",
                    onClick = { expanded = !expanded },
                    tag = GroupDetailsTags.OwnChargesSettled,
                ) {
                    Text(
                        text = toggleLabel,
                        style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                        color = SaqzTheme.colors.primary,
                    )
                }
            }
            if (expanded) {
                OwnChargesHistoryCard(history = ownCharges.history)
            }
        }
    }
}

@Composable
private fun historyToggleLabel(expanded: Boolean, count: Int): String = if (expanded) {
    stringResource(Res.string.group_details_own_charges_history_hide)
} else {
    stringResource(Res.string.group_details_own_charges_history_show, count)
}

@Composable
private fun OwnDebtContent(
    ownCharges: OwnChargesUi,
    debt: GroupOwnDebtUi,
    pixCopied: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
    val pix = ownCharges.pix
    OwnChargeTicket(
        eyebrow = debt.eyebrow,
        amountLabel = debt.totalLabel,
        dueChipLabel = debt.dueLabel,
        dueChipOverdue = debt.overdue,
        headerChip = null,
        countLabel = debt.countLabel,
        receiverLabel = null,
        copied = pixCopied,
        copyLabel = stringResource(Res.string.group_cashbox_pix_copy),
        copiedLabel = stringResource(Res.string.home_own_charge_copied),
        onCopy = if (pix != null) {
            { onIntent(GroupDetailsIntent.CopyPix) }
        } else {
            null
        },
        onClick = null,
        contentDescription = null,
        tags = OwnChargeTicketTags(card = GroupDetailsTags.OwnDebt, copy = GroupDetailsTags.OwnChargesPixCopy),
        footnote = stringResource(
            if (pix != null) Res.string.own_charges_note else Res.string.group_details_own_charges_no_pix,
        ),
    ) {
        OwnDebtPendingLines(pending = ownCharges.pending)
        if (pix != null) {
            OwnDebtPix(key = pix.key, receiverLabel = debt.receiverLabel)
        }
    }
    OwnDebtHistory(history = ownCharges.history)
}

/** Uma linha por pendência, entre divisórias. A tag de cada linha é contrato do e2e. */
@Composable
private fun OwnDebtPendingLines(pending: List<OwnChargeUi>) = Column(
    modifier = Modifier.fillMaxWidth().testTag(GroupDetailsTags.OwnChargesPending),
) {
    SaqzDivider()
    pending.forEach { charge ->
        OwnChargeLine(charge = charge, padding = PaddingValues(vertical = ChargeLineVerticalPadding))
        SaqzDivider()
    }
}

/**
 * Recebedor + chave. A chave é um `Text` PRÓPRIO com o texto exato dela: o e2e
 * `monthly-history` a procura por `onNodeWithText`.
 */
@Composable
private fun OwnDebtPix(key: String, receiverLabel: String?) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GroupDetailsTags.OwnChargesPix),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2),
    ) {
        if (receiverLabel != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(metrics.grid),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaqzIcon(SaqzIcons.CreditCard, tint = colors.textSecondary, size = PixReceiverIconSize)
                Text(text = receiverLabel, style = SaqzTheme.typography.support, color = colors.textSecondary)
            }
        }
        Text(
            text = key,
            style = SaqzTheme.typography.compactTitle,
            color = colors.textPrimary,
            // Alinha a chave com o texto do recebedor: ícone de 16 + respiro de 8.
            modifier = if (receiverLabel != null) Modifier.padding(start = metrics.grid * 3) else Modifier,
        )
    }
}

/** Histórico recolhido: o card entra ACIMA do botão, como no mock. Sem histórico, nada. */
@Composable
private fun ColumnScope.OwnDebtHistory(history: List<OwnChargeUi>) {
    if (history.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (expanded) {
        OwnChargesHistoryCard(history = history)
    }
    SaqzButton(
        label = historyToggleLabel(expanded = expanded, count = history.size),
        onClick = { expanded = !expanded },
        variant = SaqzButtonVariant.Ghost,
        size = SaqzButtonSize.Sm,
        fullWidth = true,
        modifier = Modifier
            .heightIn(min = SaqzTheme.metrics.minimumTouchTarget)
            .testTag(GroupDetailsTags.OwnChargesHistoryToggle),
    )
}

@Composable
private fun OwnChargesHistoryCard(history: List<OwnChargeUi>) = SaqzCard(
    padded = false,
    modifier = Modifier.testTag(GroupDetailsTags.OwnChargesHistory),
) {
    val metrics = SaqzTheme.metrics
    history.forEachIndexed { index, charge ->
        if (index > 0) {
            SaqzDivider()
        }
        OwnChargeLine(
            charge = charge,
            padding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        )
    }
}

/**
 * Título + vencimento à esquerda, valor + status à direita — os quatro são DESCENDENTES da
 * tag da linha na árvore não mesclada (contrato do e2e). O vencimento é sempre neutro: o
 * estado não diz qual linha venceu, só o chip do ticket (`GroupOwnDebtUi.overdue`).
 */
@Composable
private fun OwnChargeLine(charge: OwnChargeUi, padding: PaddingValues) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GroupDetailsTags.ownCharge(charge.id))
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2),
        ) {
            Text(text = charge.title, style = SaqzTheme.typography.compactTitle, color = colors.textPrimary)
            Text(text = charge.dueLabel, style = SaqzTheme.typography.compactMeta, color = colors.textSecondary)
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text(text = charge.amountLabel, style = SaqzTheme.typography.compactTitle, color = colors.textPrimary)
            OwnChargeStatusChip(status = charge.status)
        }
    }
}

@Composable
private fun OwnChargeStatusChip(status: OwnChargeStatusUi) = when (status) {
    OwnChargeStatusUi.Pending -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_pending),
        tone = SaqzChipTone.Warning,
        dot = true,
    )
    OwnChargeStatusUi.Paid -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_paid),
        tone = SaqzChipTone.Success,
        dot = true,
    )
    OwnChargeStatusUi.Waived -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_waived),
        tone = SaqzChipTone.Neutral,
    )
    OwnChargeStatusUi.Cancelled -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_cancelled),
        tone = SaqzChipTone.Neutral,
    )
}

/** O esqueleto tem a silhueta do ticket: eyebrow, valor, contagem e o botão em pílula. */
@Composable
private fun OwnDebtSkeleton() {
    val metrics = SaqzTheme.metrics
    SaqzCard(
        cornerRadius = metrics.blockRadius,
        modifier = Modifier.testTag(GroupDetailsTags.OwnChargesSkeleton),
    ) {
        SaqzSkeleton(width = metrics.grid * 11, height = metrics.blockGap)
        SaqzSkeleton(width = metrics.grid * 20, height = SkeletonAmountHeight)
        SaqzSkeleton(width = metrics.grid * 31, height = SkeletonCountHeight)
        SaqzSkeleton(height = metrics.buttonHeight, radius = metrics.buttonHeight / 2)
    }
}

@Composable
private fun OwnDebtFailure(onIntent: (GroupDetailsIntent) -> Unit) = SaqzCard(
    modifier = Modifier.testTag(GroupDetailsTags.OwnChargesFailure),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.errorForeground, size = FailureIconSize)
        Text(
            text = stringResource(Res.string.own_charges_failure),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
    SaqzButton(
        label = stringResource(Res.string.own_charges_retry),
        onClick = { onIntent(GroupDetailsIntent.RetryOwnCharges) },
        variant = SaqzButtonVariant.Secondary,
        size = SaqzButtonSize.Sm,
        modifier = Modifier.testTag(GroupDetailsTags.OwnChargesRetry),
    )
}

@Preview
@Composable
private fun GroupOwnDebtBlockPreview() = SaqzTheme {
    GroupOwnDebtBlock(state = GroupOwnDebtPreviewData.owesTwo, onIntent = {})
}

@Preview
@Composable
private fun GroupOwnChargesSettledBlockPreview() = SaqzTheme {
    GroupOwnChargesSettledBlock(state = GroupOwnDebtPreviewData.settled, onIntent = {})
}
```

Conferência mecânica depois de colar (os dois comandos têm de imprimir o esperado):

```sh
grep -c "GroupOwnChargesSection\|Andaime" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details/GroupOwnDebtBlock.kt   # 1 (só a menção no KDoc a `GroupOwnChargesSection`)
git status --short mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details/GroupOwnChargesSection.kt                     # vazio
```

### 3. Reescrever `TDET/GroupOwnDebtBlockTest.kt` — conteúdo completo na seção Testes

### 4. Reescrever `SDET/GroupOwnDebtScreenshotTest.kt` — conteúdo completo na seção Cenas

Antes de gravar, apagar os quatro PNGs locais que o andaime gerou com os nomes antigos (são ignorados pelo git):

```sh
rm -f mobile/features/groups/presentation/screenshots/own-debt/group-details-own-charges*.png
```

## Testes

Arquivo `TDET/GroupOwnDebtBlockTest.kt` (arquivo completo — 10 testes, o teto do detekt para funções não privadas por arquivo; rodam em `iosSimulatorArm64Test`):

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PixKey = "ceret@volei.com.br"
private const val NoPixNote =
    "O grupo ainda não cadastrou uma chave Pix. Combine o pagamento com o admin — a baixa acontece no caixa do grupo."

@OptIn(ExperimentalTestApi::class)
class GroupOwnDebtBlockTest {
    @Test
    fun debtShowsTheTicketWithOneLinePerPendingChargeAndThePixKey() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.owesTwo)

        onNodeWithTag(GroupDetailsTags.OwnCharges).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnDebt).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPending).assertExists()
        onNodeWithText("R$ 95,00").assertExists()
        onNodeWithText("2 cobranças em aberto").assertExists()
        // O mesmo formato que o e2e usa: status DESCENDENTE da tag da linha, árvore não mesclada.
        onNode(
            hasText("Em aberto") and hasAnyAncestor(hasTestTag(GroupDetailsTags.ownCharge("c-1"))),
            useUnmergedTree = true,
        ).assertExists()
        onNode(
            hasText("R$ 25,00") and hasAnyAncestor(hasTestTag(GroupDetailsTags.ownCharge("c-2"))),
            useUnmergedTree = true,
        ).assertExists()
    }

    // Gestor que deve: o mesmo bloco. Com o histórico aberto, cada tag contratual conta 1 —
    // o harness do e2e (`waitTag`) espera EXATAMENTE um nó por tag.
    @Test
    fun historyStaysOutOfTheTreeUntilTheToggleIsTappedAndEveryTagExistsOnce() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.adminOwes)

        onAllNodesWithTag(GroupDetailsTags.OwnChargesHistory).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.ownCharge("c-3")).assertCountEquals(0)
        onNodeWithText("Ver histórico (3)").assertExists()

        onNodeWithTag(GroupDetailsTags.OwnChargesHistoryToggle).performScrollTo().performClick()

        onNode(
            hasText("Paga") and hasAnyAncestor(hasTestTag(GroupDetailsTags.ownCharge("c-3"))),
            useUnmergedTree = true,
        ).assertExists()
        onNodeWithText("Isenta").assertExists()
        onNodeWithText("Cancelada").assertExists()
        onNodeWithText("Ocultar histórico").assertExists()
        val tags = listOf(
            GroupDetailsTags.OwnCharges,
            GroupDetailsTags.OwnDebt,
            GroupDetailsTags.OwnChargesPending,
            GroupDetailsTags.OwnChargesPix,
            GroupDetailsTags.OwnChargesPixCopy,
            GroupDetailsTags.OwnChargesHistoryToggle,
            GroupDetailsTags.OwnChargesHistory,
        ) + listOf("c-1", "c-2", "c-3", "c-4", "c-5").map(GroupDetailsTags::ownCharge)
        tags.forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
        onAllNodesWithText("Minhas cobranças").assertCountEquals(1)

        onNodeWithTag(GroupDetailsTags.OwnChargesHistoryToggle).performScrollTo().performClick()

        onAllNodesWithTag(GroupDetailsTags.OwnChargesHistory).assertCountEquals(0)
    }

    // Contrato do e2e `monthly-history`: a chave é um Text próprio, achado por texto exato e único.
    @Test
    fun pixKeyIsItsOwnExactTextAndCopyAsksForIt() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupOwnDebtPreviewData.owesTwo) { intents += it }

        onNodeWithTag(GroupDetailsTags.OwnChargesPix).assertExists()
        onNodeWithText(PixKey).assertExists()
        onNode(hasText(PixKey) and hasAnyAncestor(hasTestTag(GroupDetailsTags.OwnChargesPix))).assertExists()
        onNode(hasText("Pix de Lucas Prado") and hasAnyAncestor(hasTestTag(GroupDetailsTags.OwnChargesPix))).assertExists()
        onNodeWithText("Copiar chave Pix").assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPixCopy).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.CopyPix, intents.single())
    }

    @Test
    fun copiedKeySwapsTheButtonLabel() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.pixCopied)

        onNodeWithText("Chave copiada").assertExists()
        onAllNodesWithText("Copiar chave Pix").assertCountEquals(0)
    }

    @Test
    fun groupWithoutPixHidesTheButtonAndExplainsWhatToDo() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.noPix)

        onNodeWithTag(GroupDetailsTags.OwnDebt).assertExists()
        onNodeWithTag(GroupDetailsTags.ownCharge("c-1")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPix).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPixCopy).assertCountEquals(0)
        onNodeWithText(NoPixNote).assertExists()
    }

    @Test
    fun singleChargeStillOnTimeHasNoCountAndKeepsItsLine() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.owesOne)

        onNodeWithTag(GroupDetailsTags.ownCharge("c-2")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.ownCharge("c-1")).assertCountEquals(0)
        onAllNodesWithText("2 cobranças em aberto").assertCountEquals(0)
    }

    @Test
    fun settledShowsOnlyTheRowAtTheEndAndTheTapOpensTheHistory() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.settled)

        onNodeWithTag(GroupDetailsTags.OwnChargesSettled).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnCharges).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnDebt).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPix).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesHistory).assertCountEquals(0)
        onNode(
            hasText("Tudo em dia") and hasAnyAncestor(hasTestTag(GroupDetailsTags.OwnChargesSettled)),
            useUnmergedTree = true,
        ).assertExists()

        onNodeWithTag(GroupDetailsTags.OwnChargesSettled).performScrollTo().performClick()

        onNodeWithTag(GroupDetailsTags.OwnChargesHistory).assertExists()
        onNodeWithText("Paga").assertExists()
    }

    @Test
    fun loadingShowsOnlyTheSkeleton() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.loading)

        onNodeWithTag(GroupDetailsTags.OwnChargesSkeleton).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnDebt).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
    }

    // A seção falha sozinha: o resto do detalhe continua na tela, com retry só dela.
    @Test
    fun failureKeepsTheScreenAndOffersRetry() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupOwnDebtPreviewData.failed) { intents += it }

        onNodeWithTag(GroupDetailsTags.Mural).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesFailure).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.OwnChargesRetry).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.RetryOwnCharges, intents.single())
    }

    @Test
    fun memberWithoutChargesHasNoOwnChargesAnywhere() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member.copy(ownCharges = null))

        onAllNodesWithTag(GroupDetailsTags.OwnCharges).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
        onAllNodesWithText("Minhas cobranças").assertCountEquals(0)
    }
}
```

### Prova de que o e2e continua válido (nenhum arquivo de `E2E/` muda)

| Onde | O que o e2e faz | Por que continua verde |
|---|---|---|
| `E2E/FinancialFlowsE2eTest.kt:178-182` (`charge-lifecycle`) | atleta com **2 cobranças PENDING** abre o grupo e faz `waitTag("group-details-own-charge-<id da 1ª>")` — `waitTag` exige `size == 1` na árvore fundida (`E2E/E2eSupport.kt:43-45`) | Com pendência, V1 garante `debt != null`; `OwnDebtPendingLines` emite uma `Row` com `GroupDetailsTags.ownCharge(id)` por pendência, sem depender do histórico. O ticket tem `onClick = null` → nenhum ancestral funde descendentes → a tag aparece na árvore fundida. O bloco "em dia" não emite junto → a tag existe uma vez. |
| `E2E/FinancialFlowsE2eTest.kt:183` | `group-details-cashbox` não existe para atleta | Não é deste bloco (C2). |
| `E2E/FinancialFlowsE2eTest.kt:185-199, 207-213`, `:66-73`, `E2E/MonthlyGenerationE2eTest.kt:47-53`, `E2E/ProfileFlowsE2eTest.kt:196-207, 253-268` | linhas `group-details-own-charge-*` com "Em aberto"/"Paga"/"Isenta"/"Cancelada" e as 12 linhas | Todas rodam em **Perfil → Mensalidades** (`click("own-profile-monthly-payments")`), que usa `GroupOwnChargesSection` — arquivo que este ticket não toca. |
| `E2E/ProfileFlowsE2eTest.kt:221-225` (`monthly-history`) | abre o grupo, `waitTag("group-details")`, `waitTag("group-details-own-charges-pix")`, `onNodeWithText(<groupPix>).performScrollTo().assertIsDisplayed()` e a chave do 2º grupo não existe | O atleta tem mensalidade PENDING (`:240` confere os quatro status) e o grupo tem `pixKey` (`:227`) → `GroupDetailsViewModel.kt:326-327` monta `pix` → `OwnDebtPix` emite o contêiner `OwnChargesPix` uma vez, com `Text(text = pix.key)` (a chave já chega com `trim()`). `onNodeWithText` é casamento exato e pede nó único: "Pix de <rótulo>" e a nota não são iguais à chave, e o toast só existe depois de `CopyPix`, que o cenário não toca. O nó está dentro do `Column` com `verticalScroll` da tela → `performScrollTo` funciona. |
| `E2E/ProfileFlowsE2eTest.kt:172-174` (`member-privacy`) | na tela de perfil de OUTRO membro não há `group-details-own-charge-*` nem "Minhas cobranças" | Tela diferente; não é afetada. |

Cenários a rodar localmente antes do PR (emulador `Saqz_API_30` ligado, Docker/Colima de pé, `SAQZ_E2E_FIREBASE_BIN` exportado como em `tests/e2e/android/README.md` § Executar; trocar o serial pelo que `adb devices` mostrar):

```sh
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario finance
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario payments
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario charge-lifecycle
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario monthly-history
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario member-privacy
```

Cada um tem de terminar em `PASS` com 1 caso executado. Falhou? Antes de chamar de regressão, rodar o mesmo cenário em `origin/main` (lição registrada do projeto) e colar os dois resultados no PR.

## Cenas de screenshot e prints do PR

Arquivo `SDET/GroupOwnDebtScreenshotTest.kt` (arquivo completo). As duas cenas de histórico aberto precisam de um toque antes da captura — `captureDetails` (fechado) não toca em nada, então este arquivo tem o próprio `captureDetailsAfterTap`:

```kotlin
package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val Directory = "own-debt"

// Os dez estados de "Minhas cobranças" na tela inteira. Estado fora da cena é estado não
// conferido (AGENTS.md §11). A altura de 2400dp deixa a tela toda sem rolagem: o toque do
// histórico não desloca a captura.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupOwnDebtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun owesTwo() = compose.captureDetails("own-debt-owes-two", GroupOwnDebtPreviewData.owesTwo, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun owesOne() = compose.captureDetails("own-debt-owes-one", GroupOwnDebtPreviewData.owesOne, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun pixCopied() = compose.captureDetails("own-debt-pix-copied", GroupOwnDebtPreviewData.pixCopied, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun noPix() = compose.captureDetails("own-debt-no-pix", GroupOwnDebtPreviewData.noPix, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun historyOpen() = compose.captureDetailsAfterTap(
        name = "own-debt-history-open",
        state = GroupOwnDebtPreviewData.owesTwo,
        tag = GroupDetailsTags.OwnChargesHistoryToggle,
    )

    @Test
    @Config(qualifiers = "+h2400dp")
    fun settled() = compose.captureDetails("own-debt-settled", GroupOwnDebtPreviewData.settled, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun settledHistoryOpen() = compose.captureDetailsAfterTap(
        name = "own-debt-settled-history-open",
        state = GroupOwnDebtPreviewData.settled,
        tag = GroupDetailsTags.OwnChargesSettled,
    )

    @Test
    @Config(qualifiers = "+h2400dp")
    fun loading() = compose.captureDetails("own-debt-loading", GroupOwnDebtPreviewData.loading, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun failed() = compose.captureDetails("own-debt-failed", GroupOwnDebtPreviewData.failed, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun adminOwes() = compose.captureDetails("own-debt-admin-owes", GroupOwnDebtPreviewData.adminOwes, Directory)
}

/** `captureDetails` com um toque antes da captura: o histórico é estado visual, não do ViewModel. */
private fun ComposeContentTestRule.captureDetailsAfterTap(name: String, state: GroupDetailsState, tag: String) {
    setContent {
        SaqzTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SaqzTheme.colors.background),
            ) {
                GroupDetailsScreen(state = state, onBack = {}, onIntent = {}, photoFailed = false)
            }
        }
    }
    onNodeWithTag(tag).performClick()
    onRoot().captureRoboImage("screenshots/$Directory/$name.png")
}
```

Lista fechada das capturas (em `mobile/features/groups/presentation/screenshots/own-debt/`) e a referência do mock de cada uma:

| PNG | Estado | Mock |
|---|---|---|
| `own-debt-owes-two.png` | deve 2: vencida + a vencer | `MembroDeve` |
| `own-debt-owes-one.png` | deve 1, no prazo (chip neutro, sem contagem, COM a linha) | `MembroDeveUma` |
| `own-debt-pix-copied.png` | botão "Chave copiada" | `MembroChaveCopiada` |
| `own-debt-no-pix.png` | sem chave Pix | `MembroSemPix` |
| `own-debt-history-open.png` | histórico aberto acima do botão | `MembroHistorico` |
| `own-debt-settled.png` | "Tudo em dia" no fim da tela, nada no topo | `Main` |
| `own-debt-settled-history-open.png` | "Tudo em dia" com histórico aberto | `chargesSettled({ open: true })` do `build.mjs` |
| `own-debt-loading.png` | esqueleto do ticket | `MembroCobrancasCarregando` |
| `own-debt-failed.png` | falha com retry | `MembroCobrancasFalha` |
| `own-debt-admin-owes.png` | gestor que deve | `GestorTambemDeve` |

Diferenças DELIBERADAS em relação aos PNGs do mock (não "consertar"): linha vencida sem âmbar; chip "Em aberto" em cada pendência; linha presente com uma pendência só; botão do histórico e "Tudo em dia" sem chevron (texto "Ver histórico (3)" no lugar); círculo do "Tudo em dia" no tom neutro do `WaitingRow`. Enquanto C1/C2 não entrarem, o resto da tela nas capturas é o do andaime — só o bloco de cobranças é conferido aqui.

**Abrir e olhar os dez PNGs** antes do PR. Conferir em cada um: título "Minhas cobranças" uma vez; raio 20 no ticket e no esqueleto; divisórias acima, entre e abaixo das linhas; chave Pix alinhada com o texto "Pix de …"; nota em `caption` depois do botão; no `settled`, nada entre o hero e o próximo bloco.

Prints obrigatórios no corpo do PR (branch órfã `screenshots`, pasta `vul-XXX/`, embutir o raw): `own-debt-owes-two.png`, `own-debt-history-open.png`, `own-debt-no-pix.png`, `own-debt-settled.png`, `own-debt-settled-history-open.png`, `own-debt-failed.png`. Legenda única: "Minhas cobranças enxutas: ticket só com pendência, histórico recolhido, 'Tudo em dia' no fim."

## Gates

Rodar da raiz do worktree, nesta ordem:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.ui.details.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
```

Depois, os cinco cenários de e2e da seção Testes (`finance`, `payments`, `charge-lifecycle`, `monthly-history`, `member-privacy`). Este ticket não edita `E2E/`, então não há gate de compilação do source set e2e além do que o runner já faz.

## Critérios de aceite

- [ ] Diff só com os quatro arquivos da lista; `GroupOwnChargesSection.kt` intacto.
- [ ] `GroupOwnDebtBlock` não emite nada sem `ownCharges`, e nada quando não está carregando, não falhou e não há dívida; `GroupOwnChargesSettledBlock` só emite com cobranças carregadas, sem pendência e com histórico.
- [ ] Os dois blocos nunca emitem no mesmo estado: `OwnCharges`, `OwnChargesHistory` e cada `ownCharge(id)` contam no máximo 1 (teste `historyStaysOutOfTheTreeUntilTheToggleIsTappedAndEveryTagExistsOnce`).
- [ ] A chave Pix é um `Text` próprio com o texto exato, dentro de `OwnChargesPix`; o ticket recebe `receiverLabel = null`.
- [ ] Sem Pix: sem botão, sem contêiner `OwnChargesPix`, nota `group_details_own_charges_no_pix`.
- [ ] Histórico fora da árvore até o toque; estado em `rememberSaveable`; nada disso no ViewModel.
- [ ] KDoc "Andaime (T)" removido dos dois blocos; nenhum `@Suppress` no `GroupOwnDebtBlock`; o único `@Suppress("UnusedParameter")` do arquivo é o que já existia em `GroupOwnChargesSettledBlock`.
- [ ] Nenhum `dp` cru fora dos cinco `private val` do topo; nenhuma chave de string nova; nenhuma tag nova.
- [ ] 10 testes de `GroupOwnDebtBlockTest` verdes (não acrescentar o 11º: teto do detekt); os demais testes de `ui.details` verdes sem edição.
- [ ] `GroupOwnDebtScreenshotTest` fica com exatamente 10 cenas (teto do detekt: 10 funções não privadas por arquivo; o `captureDetailsAfterTap` é privado e não conta).
- [ ] Dez PNGs em `screenshots/own-debt/`, abertos e conferidos; seis embutidos no PR.
- [ ] Cinco cenários de e2e com `PASS`, saída colada no PR.
- [ ] Diff ≤ 2000 linhas (estimativa: ~830 — bloco +330/−30, preview data +55, testes +200/−95, cenas +115/−45).

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-minhas-cobrancas-enxutas origin/main` (XXX = número do ticket no Linear).
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): …`, `test(groups): …`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR. Ticket de UI: **abrir e olhar os PNGs gravados** antes de abrir o PR e embutir os prints obrigatórios.
4. PR contra `main`, aberto como ready (não draft), título `feat(groups): minhas cobranças enxutas no detalhe do grupo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
