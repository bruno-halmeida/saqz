# C5 · Esperando você (gestor)

**Onda 3 · depende de: S, B, T, V2 · bloqueia: D · paralelo com: C4, H**

## Objetivo

Trocar o andaime de `GroupWaitingBlock` (botão solto "Avisar quem falta confirmar" + textos soltos) pelo bloco **"Esperando você"** do gestor: header + um card flush com até quatro linhas `WaitingRow`, separadas por `SaqzDivider`, nesta ordem:

1. **quórum** — "N pessoas sem resposta" + prazo + botão "Avisar" (e o retorno do aviso na própria linha);
2. **pedidos para entrar** — `state.waiting.entryRequests`;
3. **mensalidades a receber** — `state.waiting.monthly`;
4. **acerto de jogo** — `state.waiting.settle`.

Conserta um defeito de hoje: com as confirmações encerradas o botão "Avisar" continua na tela, ativo, e o toque não faz nada (`GroupDetailsViewModel.notifyPending` sai cedo em `!game.confirmationOpen`, linha 194). No bloco novo a linha de quórum só existe quando o aviso pode acontecer — ou quando o retorno de um aviso já enviado precisa continuar visível.

Fatos do contrato que esta receita usa (já conferidos):

- A linha de quórum **não tem campo próprio**: lê `attendance.pending`, `nextGame.confirmationOpen`, `nextGame.deadlineShort` (V1), `notifying`, `notificationFailed`, `notifiedCount` (CONTRATO §3).
- `state.waiting: GroupWaitingUi?` vem do V2; `contentDescription` das três linhas já chega pronto (`"$title. $meta"`); atleta recebe sempre `null`.
- `WaitingRow` (B) com `onClick = null` vira UM item fundido (`semantics(mergeDescendants = true)`); um `SaqzButton` no `trailing` continua alvo próprio na árvore fundida.
- A tag `group-details-notify-pending` e o texto "Lembrete enviado no Saqz para N pessoa(s)." são contrato do e2e e **não mudam** (prova na seção Testes).

## Fora do escopo

- Nenhum e2e muda. Nenhuma string é criada, renomeada ou editada (`group_details_notify_pending` fica órfã depois deste PR — quem remove é o D).
- Não tocar em `WaitingRow.kt` (B), `GroupDetailsTags.kt`, `GroupDetailsPreviewData.kt`, `GroupDetailsScreen.kt`, Contract nem ViewModel.
- A tag `GroupDetailsTags.Cashbox` é única e mora na Gestão (C2): a linha de mensalidades usa `WaitingMonthly`, nunca `Cashbox`.
- `GroupDetailsTags.NotifyFeedback` **não é usada** neste PR: `WaitingRow` não expõe modificador para o `Text` da meta. O retorno fica identificável pelo texto dentro da linha `WaitingQuorum` (é assim que os testes e o e2e o acham). Divergência registrada para o orquestrador.
- A meta de falha sai em `textSecondary` (cor fixa da meta do `WaitingRow`), não em vermelho como no PNG `GestorAvisoFalhou`. Vale a receita.
- O botão é `SaqzButtonSize.Sm` (44dp), não os 36px do mock: alvo de toque da casa.

## Arquivos

| Ação | Arquivo |
|---|---|
| editar (arquivo inteiro) | `DET/GroupWaitingBlock.kt` |
| criar | `DET/GroupWaitingPreviewData.kt` |
| editar (arquivo inteiro) | `TDET/GroupWaitingBlockTest.kt` |
| criar | `SDET/GroupWaitingScreenshotTest.kt` |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Passo a passo

### 1. Substituir `DET/GroupWaitingBlock.kt` pelo arquivo completo abaixo

Âncora: o arquivo atual é o do passo 8 do ticket T — começa com o KDoc `* Andaime (T): o botão "Avisar quem falta confirmar" e o retorno dele, fora do card de` e o corpo é `if (!state.isAdmin || state.nextGame == null) return` seguido de um `Column` com `SaqzButton(... variant = SaqzButtonVariant.Ghost, fullWidth = true ...)`. Apague tudo e cole:

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.communication_failure
import br.com.saqz.groups.resources.communication_reminded
import br.com.saqz.groups.resources.group_details_waiting_notify
import br.com.saqz.groups.resources.group_details_waiting_quorum
import br.com.saqz.groups.resources.group_details_waiting_quorum_one
import br.com.saqz.groups.resources.home_admin_waiting_entry_chip
import br.com.saqz.groups.resources.home_admin_waiting_title
import org.jetbrains.compose.resources.stringResource

// Mesmo véu do `SaqzStatusChip` no tom Success.
private const val SentBadgeAlpha = 0.12f

/**
 * "Esperando você": o que ESTE grupo espera do gestor. Um card flush com até quatro linhas —
 * quórum (com o botão "Avisar" e o retorno dele), pedidos de entrada, mensalidades a receber e
 * acerto de jogo. Só gestor; sem nenhuma linha o bloco não emite nada.
 *
 * O texto de sucesso (`communication_reminded`) e a tag `NotifyPending` são contrato do e2e
 * (`ReminderE2eTest`). A linha de mensalidades usa `WaitingMonthly`: a tag `Cashbox` é única e
 * mora na Gestão.
 */
@Composable
internal fun GroupWaitingBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isAdmin) return
    val chevron: @Composable () -> Unit = { SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary) }
    val rows = buildList<@Composable () -> Unit> {
        if (state.showsQuorum()) add { QuorumRow(state = state, onIntent = onIntent) }
        state.waiting?.entryRequests?.let { row ->
            add {
                WaitingRow(
                    icon = SaqzIcons.Users,
                    title = row.title,
                    meta = row.meta.ifEmpty { null },
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.InviteByLink) },
                    tag = GroupDetailsTags.WaitingEntryRequests,
                    trailing = {
                        SaqzStatusChip(
                            text = stringResource(Res.string.home_admin_waiting_entry_chip, row.count),
                            tone = SaqzChipTone.Warning,
                            dot = true,
                        )
                    },
                )
            }
        }
        state.waiting?.monthly?.let { row ->
            add {
                WaitingRow(
                    icon = SaqzIcons.CreditCard,
                    title = row.title,
                    meta = row.meta.ifEmpty { null },
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.OpenCashbox) },
                    tag = GroupDetailsTags.WaitingMonthly,
                    trailing = chevron,
                )
            }
        }
        state.waiting?.settle?.let { row ->
            add {
                WaitingRow(
                    icon = SaqzIcons.Calendar,
                    title = row.title,
                    meta = row.meta.ifEmpty { null },
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.OpenSettlement(row.gameId)) },
                    tag = GroupDetailsTags.WaitingSettle,
                    trailing = chevron,
                )
            }
        }
    }
    if (rows.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Waiting),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_admin_waiting_title))
        SaqzCard(padded = false) {
            rows.forEachIndexed { index, row ->
                if (index > 0) SaqzDivider()
                row()
            }
        }
    }
}

/**
 * A linha existe enquanto dá para avisar (confirmações abertas e alguém sem resposta) e
 * continua depois de um aviso enviado, mesmo que o pendente zere: o retorno é contrato do e2e.
 */
private fun GroupDetailsState.showsQuorum(): Boolean =
    notifiedCount != null || (nextGame?.confirmationOpen == true && (attendance?.pending ?: 0) > 0)

@Composable
private fun QuorumRow(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = state.attendance?.pending ?: 0
    val title =
        if (pending == 1) stringResource(Res.string.group_details_waiting_quorum_one)
        else stringResource(Res.string.group_details_waiting_quorum, pending)
    val notified = state.notifiedCount
    val meta = when {
        notified != null -> stringResource(Res.string.communication_reminded, notified)
        state.notificationFailed -> stringResource(Res.string.communication_failure)
        else -> state.nextGame?.deadlineShort?.ifEmpty { null }
    }
    val sentBadge: (@Composable () -> Unit)? = if (notified != null) { { QuorumSentBadge() } } else null
    WaitingRow(
        icon = if (notified != null) SaqzIcons.Check else SaqzIcons.Megaphone,
        title = title,
        meta = meta,
        contentDescription = listOfNotNull(title, meta).joinToString(". "),
        onClick = null,
        tag = GroupDetailsTags.WaitingQuorum,
        modifier = modifier,
        metaMaxLines = 2,
        leading = sentBadge,
        trailing = {
            if (notified == null) {
                SaqzButton(
                    label = stringResource(Res.string.group_details_waiting_notify),
                    onClick = { onIntent(GroupDetailsIntent.NotifyPending) },
                    modifier = Modifier.testTag(GroupDetailsTags.NotifyPending),
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    enabled = !state.notifying,
                    loading = state.notifying,
                )
            }
        },
    )
}

/** Aviso enviado: o círculo do ícone vira verde com o check (mock `GestorAvisado`). */
@Composable
private fun QuorumSentBadge(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    Box(
        modifier = modifier
            .size(SaqzTheme.metrics.grid * 5)
            .clip(CircleShape)
            .background(colors.success.copy(alpha = SentBadgeAlpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        SaqzIcon(icon = SaqzIcons.Check, tint = colors.success)
    }
}
```

Notas para quem executa (nada a decidir, só para não "consertar" o que está certo):

- O `@Suppress("UnusedParameter")` e o KDoc "Andaime (T)" saíram junto com o arquivo antigo. Não há `@Suppress` no arquivo novo.
- `SaqzSpinner`, `Text` e `group_details_notify_pending` deixaram de ser importados de propósito.
- `metrics.grid * 5` = 40dp, a mesma medida do círculo do `WaitingRow`.

### 2. Criar `DET/GroupWaitingPreviewData.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.GroupSettleRowUi
import br.com.saqz.groups.presentation.details.GroupWaitingRowUi
import br.com.saqz.groups.presentation.details.GroupWaitingUi

/**
 * Os estados do bloco "Esperando você" para os testes e as capturas. Derivam de
 * [GroupDetailsPreviewData.admin]; só entra aqui o que o `GroupDetailsViewModel` produz.
 */
internal object GroupWaitingPreviewData {
    private val nextGame = GroupDetailsPreviewData.nextGame.copy(deadlineShort = "Encerra 28/07 · 18h00")

    val entryRequests = GroupWaitingRowUi(
        title = "3 pedidos para entrar",
        meta = "Rafa, Júlia e mais 1",
        contentDescription = "3 pedidos para entrar. Rafa, Júlia e mais 1",
        count = 3,
    )

    val waiting = GroupWaitingUi(
        entryRequests = entryRequests,
        monthly = GroupWaitingRowUi(
            title = "2 mensalidades a receber",
            meta = "R$ 140,00 · AGO",
            contentDescription = "2 mensalidades a receber. R$ 140,00 · AGO",
            count = 2,
        ),
        settle = GroupSettleRowUi(
            gameId = "game-0",
            title = "Acertar o jogo de 21/07",
            meta = "4 avulsos · R$ 100,00 a receber",
            contentDescription = "Acertar o jogo de 21/07. 4 avulsos · R$ 100,00 a receber",
        ),
    )

    /** As quatro linhas, aviso parado. */
    val idle = GroupDetailsPreviewData.admin.copy(nextGame = nextGame, waiting = waiting)

    val notifying = idle.copy(notifying = true)

    val notified = idle.copy(notifiedCount = "2")

    val failed = idle.copy(notificationFailed = true)

    /** Confirmações encerradas com gente sem resposta: a linha de quórum some. */
    val closed = idle.copy(nextGame = nextGame.copy(confirmationOpen = false))

    /** Nenhuma pendência do V2: o card fica só com o quórum. */
    val quorumOnly = idle.copy(waiting = null)

    /** Finanças falharam: o V2 só entrega os pedidos de entrada (mock `GestorFinancasFalha`). */
    val financeFailed = idle.copy(waiting = GroupWaitingUi(entryRequests = entryRequests))
}
```

### 3. Substituir `TDET/GroupWaitingBlockTest.kt` pelo arquivo completo da seção Testes

Âncora: o arquivo atual é o do passo 18 do ticket T (quatro testes: `adminWithNextGameCanNotifyWhoIsPending`, `memberAndAdminWithoutGameNeverSeeTheNotifyAction`, `adminWithoutGameHasNothingToNotify`, `reminderFeedbackStaysOnScreen`). Apague tudo e cole o arquivo da seção **Testes**.

### 4. Criar `SDET/GroupWaitingScreenshotTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Os sete estados do bloco "Esperando você". Estado fora da cena é estado não conferido
// (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupWaitingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun idle() = compose.captureDetails("group-details-waiting-idle", GroupWaitingPreviewData.idle, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun notifying() = compose.captureDetails("group-details-waiting-notifying", GroupWaitingPreviewData.notifying, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun notified() = compose.captureDetails("group-details-waiting-notified", GroupWaitingPreviewData.notified, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun failed() = compose.captureDetails("group-details-waiting-failed", GroupWaitingPreviewData.failed, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun closed() = compose.captureDetails("group-details-waiting-closed", GroupWaitingPreviewData.closed, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun quorumOnly() = compose.captureDetails("group-details-waiting-quorum-only", GroupWaitingPreviewData.quorumOnly, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun financeFailed() =
        compose.captureDetails("group-details-waiting-finance-failed", GroupWaitingPreviewData.financeFailed, "waiting")
}
```

## Testes

### `TDET/GroupWaitingBlockTest.kt` (arquivo completo — 10 testes, no teto de 10 funções não privadas por arquivo do detekt; rodam em `iosSimulatorArm64Test`)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupWaitingBlockTest {
    @Test
    fun memberNeverSeesTheBlock() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member.copy(waiting = GroupWaitingPreviewData.waiting))

        onAllNodesWithTag(GroupDetailsTags.Waiting).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun adminWithNothingPendingDoesNotSeeTheBlock() = runComposeUiTest {
        val nobodyPending = GroupDetailsPreviewData.attendance.copy(pending = 0)
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(attendance = nobodyPending))

        onAllNodesWithTag(GroupDetailsTags.Waiting).assertCountEquals(0)
    }

    @Test
    fun quorumShowsPluralTitleAndDeadline() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.idle)

        onNodeWithTag(GroupDetailsTags.WaitingQuorum)
            .assertTextContains("2 pessoas sem resposta")
            .assertTextContains("Encerra 28/07 · 18h00")
    }

    @Test
    fun quorumShowsSingularTitle() = runComposeUiTest {
        val onePending = GroupDetailsPreviewData.attendance.copy(pending = 1)
        setDetailsScreen(GroupWaitingPreviewData.idle.copy(attendance = onePending))

        onNodeWithTag(GroupDetailsTags.WaitingQuorum).assertTextContains("1 pessoa sem resposta")
    }

    @Test
    fun notifyButtonEmitsNotifyPending() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupWaitingPreviewData.idle) { intents += it }

        onNodeWithTag(GroupDetailsTags.NotifyPending).performScrollTo().assertIsEnabled().performClick()

        assertEquals(GroupDetailsIntent.NotifyPending, intents.single())
    }

    @Test
    fun notifyingDisablesTheButton() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.notifying)

        onNodeWithTag(GroupDetailsTags.NotifyPending).assertIsNotEnabled()
    }

    // O texto é contrato do e2e (`ReminderE2eTest`): um nó só, na árvore fundida, para sempre.
    @Test
    fun reminderFeedbackStaysOnScreenWithoutTheButton() = runComposeUiTest {
        val nobodyPending = GroupDetailsPreviewData.attendance.copy(pending = 0)
        setDetailsScreen(GroupWaitingPreviewData.notified.copy(attendance = nobodyPending))

        onNodeWithText("Lembrete enviado no Saqz para 2 pessoa(s).").assertExists()
        onNodeWithTag(GroupDetailsTags.WaitingQuorum).assertTextContains("Lembrete enviado no Saqz para 2 pessoa(s).")
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun failureShowsTheErrorAndKeepsTheButton() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.failed)

        onNodeWithTag(GroupDetailsTags.WaitingQuorum).assertTextContains("Não foi possível concluir. Tente novamente.")
        onNodeWithTag(GroupDetailsTags.NotifyPending).assertIsEnabled()
    }

    // O defeito de hoje: botão ativo com as confirmações encerradas, e o toque não fazia nada.
    @Test
    fun closedConfirmationsHideTheQuorumRow() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.closed)

        onNodeWithTag(GroupDetailsTags.Waiting).assertExists()
        onAllNodesWithTag(GroupDetailsTags.WaitingQuorum).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun eachRowEmitsItsIntentAndCashboxTagStaysOutOfTheBlock() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupWaitingPreviewData.idle) { intents += it }

        onNodeWithTag(GroupDetailsTags.WaitingEntryRequests).performScrollTo().performClick()
        onNodeWithTag(GroupDetailsTags.WaitingMonthly).performScrollTo().performClick()
        onNodeWithTag(GroupDetailsTags.WaitingSettle).performScrollTo().performClick()

        assertEquals(
            listOf(
                GroupDetailsIntent.InviteByLink,
                GroupDetailsIntent.OpenCashbox,
                GroupDetailsIntent.OpenSettlement("game-0"),
            ),
            intents,
        )
        onAllNodes(
            hasTestTag(GroupDetailsTags.Cashbox) and hasAnyAncestor(hasTestTag(GroupDetailsTags.Waiting)),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }
}
```

Mapa pedido → teste: membro nunca vê (`memberNeverSeesTheBlock`) · gestor sem nada não vê (`adminWithNothingPendingDoesNotSeeTheBlock`) · plural e singular (`quorumShowsPluralTitleAndDeadline`, `quorumShowsSingularTitle`) · "Avisar" emite `NotifyPending` (`notifyButtonEmitsNotifyPending`) · `notifying` desabilita (`notifyingDisablesTheButton`) · `notifiedCount = "2"` mostra o texto e nenhum botão, inclusive com o pendente zerado (`reminderFeedbackStaysOnScreenWithoutTheButton`) · falha mostra o erro e o botão continua (`failureShowsTheErrorAndKeepsTheButton`) · encerradas sem retorno → sem quórum (`closedConfirmationsHideTheQuorumRow`) · cada linha emite o intent certo e `Cashbox` conta 0 dentro do bloco (`eachRowEmitsItsIntentAndCashboxTagStaysOutOfTheBlock`).

### Prova de que `CommunicationDetailsE2eTest` continua válido (nenhum e2e muda)

O trecho é `E2E/CommunicationDetailsE2eTest.kt:274-276` (classe `ReminderE2eTest`, cenário `reminders`):

1. **`:274` `click("group-details-notify-pending", scroll = true)`** — `E2eSupport.kt:58-65`: espera EXATAMENTE um nó com a tag (`:43-45`), rola até ele, espera habilitado (`:67-70`) e clica. No bloco novo a tag está no `Modifier` do `SaqzButton`, que é o nó do `clickable` (`SaqzButton.kt:161-183`) — alvo próprio na árvore fundida mesmo dentro da linha fundida. A linha existe nesse cenário: o e2e afirma em `:268-269` que `peer` e `owner` não responderam, e o `pending_count` do backend conta todo membro ativo sem resposta, dono incluído (`JdbcAttendanceCommandRepository.kt:455-463`) → `attendance.pending ≥ 2`; o jogo está com confirmação aberta (o próprio e2e espera 422 para jogo encerrado em `:297-299`, e o ViewModel já exigia `confirmationOpen` em `GroupDetailsViewModel.kt:194`). Parado, o botão está `enabled`.
2. **`:275` `waitText("Lembrete enviado no Saqz para 1 pessoa(s).")`** — `E2eSupport.kt:47-56`: `hasText` exato, na árvore fundida, exatamente UM nó, sem ação de `SetText`. O sucesso liga `notifiedCount = "1"` (`GroupDetailsViewModel.kt:205`); a meta da linha vira `communication_reminded("1")` (`strings_communication.xml:24`). A linha é um nó fundido cujo `Text` é a lista `[título, meta]`, e `hasText` casa com qualquer item da lista → um nó só. O texto não aparece em mais nenhum lugar da tela (não há toast para o aviso).
3. **`:276` `onNodeWithText(...).performScrollTo().assertIsDisplayed()`** — o nó fundido da linha está dentro do `Column(verticalScroll)` da tela; rola e está visível. O retorno é **persistente**: `showsQuorum()` mantém a linha enquanto `notifiedCount != null`.

**Antes de abrir o PR, rodar os dois cenários** (comando literal do `tests/e2e/android/README.md`; trocar o serial pelo AVD do `adb devices`):

```sh
SAQZ_E2E_TOOLS="$(mktemp -d)"
npm install --prefix "$SAQZ_E2E_TOOLS" firebase-tools@15.25.1
export SAQZ_E2E_FIREBASE_BIN="$SAQZ_E2E_TOOLS/node_modules/firebase-tools/lib/bin/firebase.js"
adb devices
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario reminders
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario communication
```

Os dois têm de dar `PASS`. Falhou? Aplicar a lição da casa antes de chamar de regressão: rodar o MESMO cenário em `origin/main` (~10 min) e colar as duas saídas no PR; se falhar só na branch, parar e avisar o orquestrador.

## Cenas de screenshot e prints do PR

Capturas Roborazzi (lista fechada, pasta `screenshots/waiting/` do módulo, tela inteira):

| Arquivo | Estado | O que conferir |
|---|---|---|
| `group-details-waiting-idle.png` | 4 linhas, parado | megafone cinza, "2 pessoas sem resposta", "Encerra 28/07 · 18h00", botão "Avisar" contornado; chip laranja "3" com ponto; dois chevrons; divisórias entre as linhas |
| `group-details-waiting-notifying.png` | avisando | botão com spinner e desabilitado; as outras linhas iguais |
| `group-details-waiting-notified.png` | avisado | círculo verde com check, "Lembrete enviado no Saqz para 2 pessoa(s).", SEM botão |
| `group-details-waiting-failed.png` | falha | "Não foi possível concluir. Tente novamente." em até 2 linhas, botão "Avisar" ativo |
| `group-details-waiting-closed.png` | encerradas | card com 3 linhas, sem quórum e sem divisória sobrando no topo |
| `group-details-waiting-quorum-only.png` | só quórum | card de uma linha, sem divisória |
| `group-details-waiting-finance-failed.png` | finanças falharam | quórum + pedidos, só isso |

Referência: `_mock-grupo/png/GestorPendencias.png`, `GestorAvisando.png`, `GestorAvisado.png`, `GestorAvisoFalhou.png`, `GestorEncerradas.png`, `GestorFinancasFalha.png` (e `GestorSemPendencias.png` = bloco ausente, coberto por teste). Divergências deliberadas do mock: botão de 44dp (não 36) e meta de falha em cinza.

**Abrir e olhar os sete PNGs** antes do PR. Prints obrigatórios no corpo do PR: os sete, recortados no bloco "Esperando você" ou inteiros, na branch órfã `screenshots`, pasta `vul-XXX/`:

```sh
git fetch origin screenshots
git worktree add /tmp/shots-vul-XXX screenshots
mkdir -p /tmp/shots-vul-XXX/vul-XXX
cp mobile/features/groups/presentation/screenshots/waiting/*.png /tmp/shots-vul-XXX/vul-XXX/
git -C /tmp/shots-vul-XXX add . && git -C /tmp/shots-vul-XXX commit -m "screenshots: VUL-XXX"
git -C /tmp/shots-vul-XXX push origin screenshots
git worktree remove /tmp/shots-vul-XXX
```

Embutir: `![avisado](https://raw.githubusercontent.com/bruno-halmeida/saqz/screenshots/vul-XXX/group-details-waiting-notified.png)` (um por arquivo).

## Gates

Ciclo rápido:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.ui.details.GroupWaitingBlockTest"
```

Antes do PR (todos, da raiz do worktree):

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
```

Mais os cenários e2e `reminders` e `communication` (comandos na seção Testes). Este ticket não edita `E2E/`, então não há compilação extra do source set e2e além da que o runner já faz.

## Critérios de aceite

- [ ] Diff só nos quatro arquivos da lista. Estimativa: **~600 linhas** de adições+remoções (bloco +185/−55, preview +60, teste +155/−47, capturas +55) — longe do teto de 2000.
- [ ] Membro nunca vê o bloco; gestor sem nenhuma linha não vê nem o header.
- [ ] Linha de quórum só com confirmações abertas e `pending > 0`, ou com `notifiedCount != null`; com confirmações encerradas não existe botão "Avisar" na tela.
- [ ] `group-details-notify-pending` aparece no máximo uma vez na árvore; o texto "Lembrete enviado no Saqz para N pessoa(s)." fica na tela depois do aviso, sem botão.
- [ ] `GroupDetailsTags.Cashbox` não aparece dentro de `GroupDetailsTags.Waiting`.
- [ ] Nenhum arquivo passa de 10 funções não privadas (bloco: 1; preview: 0; teste: 10; capturas: 7). Nenhum `@Suppress`, nenhum `dp` cru, nenhuma string nova, nenhuma tag nova.
- [ ] 10 testes de `GroupWaitingBlockTest` verdes; 7 PNGs gravados, olhados e embutidos no PR.
- [ ] Cenários e2e `reminders` e `communication` com `PASS`, saída colada no PR.
- [ ] Os quatro gates verdes.

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-esperando-voce origin/main` (XXX = número do ticket no Linear). Conferir antes de começar que S, B, T e V2 já estão na `origin/main`: `git grep -n "class GroupWaitingUi" origin/main -- mobile/features/groups/presentation` e `git grep -n "fun WaitingRow" origin/main -- mobile/features/groups/presentation` têm de imprimir uma linha cada; se não, parar e avisar o orquestrador.
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): bloco esperando você no detalhe do grupo`, `test(groups): …`).
3. Rodar TODOS os gates e os dois cenários e2e antes de abrir o PR; colar a saída resumida no corpo do PR. **Abrir e olhar os PNGs gravados** antes de abrir o PR e embutir os sete prints.
4. PR contra `main`, aberto como ready (não draft), título `feat(groups): esperando você no detalhe do grupo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
