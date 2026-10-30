package br.com.saqz.groups.presentation.ui.finance.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSelectPill
import br.com.saqz.designsystem.SaqzSelectPillTone
import br.com.saqz.designsystem.SaqzSheetAction
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTextLink
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.ReviewDecision
import br.com.saqz.groups.presentation.ui.finance.CashListRow
import br.com.saqz.groups.presentation.ui.finance.CashMoneyField
import br.com.saqz.groups.presentation.ui.finance.CashRowText
import br.com.saqz.groups.presentation.ui.finance.formatBrlShort
import br.com.saqz.groups.resources.*
import br.com.saqz.core.common.formatting.parseBrlToCents
import org.jetbrains.compose.resources.stringResource

internal object UnchargedReviewTags {
    const val Screen = "uncharged-review"
    const val Amount = "uncharged-review-amount"
    const val Summary = "uncharged-review-summary"
    const val Confirm = "uncharged-review-confirm"
    const val Failure = "uncharged-review-failure"

    fun pill(key: String) = "uncharged-review-pill-$key"
    fun method(key: String) = "uncharged-review-method-$key"
    fun option(decision: ReviewDecision) = "uncharged-review-option-${decision.name}"
    fun methodOption(method: PaidMethod) = "uncharged-review-method-option-${method.name}"
}

@Composable
internal fun UnchargedReviewScreen(
    state: UnchargedReviewState,
    onBack: () -> Unit,
    onIntent: (UnchargedReviewIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(UnchargedReviewTags.Screen)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            SaqzTopAppBar(title = stringResource(Res.string.retro_title), onBack = onBack)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
                state.loadFailed -> SaqzEmptyState(
                    title = stringResource(Res.string.retro_load_failed),
                    description = null,
                    icon = SaqzIcons.CircleAlert,
                    action = stringResource(Res.string.retro_retry),
                    onAction = { onIntent(UnchargedReviewIntent.Retry) },
                    modifier = Modifier.fillMaxSize(),
                )
                state.rows.isEmpty() -> SaqzEmptyState(
                    title = stringResource(Res.string.retro_empty),
                    description = null,
                    icon = SaqzIcons.Users,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.subGrid),
                        verticalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
                    ) {
                        Heading(state)
                        SaqzCard {
                            CashMoneyField(
                                value = state.amountInput,
                                onValueChange = { onIntent(UnchargedReviewIntent.AmountChanged(it)) },
                                label = stringResource(Res.string.retro_amount_label),
                                helperText = stringResource(
                                    if (state.suggested) Res.string.retro_amount_hint_game else Res.string.retro_amount_hint_empty,
                                ),
                                errorText = if (state.amountInvalid) stringResource(Res.string.retro_amount_invalid) else null,
                                modifier = Modifier.fillMaxWidth().testTag(UnchargedReviewTags.Amount),
                            )
                        }
                        SaqzCard(padded = false) {
                            state.rows.forEachIndexed { index, row -> ReviewRow(state, row, index == 0, onIntent) }
                        }
                        Text(
                            text = stringResource(Res.string.retro_monthly_note),
                            style = SaqzTheme.typography.support,
                            color = SaqzTheme.colors.textSecondary,
                        )
                    }
                    BottomBar(state, onIntent)
                }
            }
        }
        DecisionMenu(state, onIntent)
        MethodMenu(state, onIntent)
    }
}

@Composable
private fun Heading(state: UnchargedReviewState) = Column(
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
) {
    Text(
        text = state.venueName?.let { stringResource(Res.string.retro_heading, state.gameDay, it) }
            ?: stringResource(Res.string.retro_heading_plain, state.gameDay),
        style = SaqzTheme.typography.title,
        color = SaqzTheme.colors.textPrimary,
    )
    Text(
        text = stringResource(Res.string.retro_subtitle),
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.textSecondary,
    )
}

@Composable
private fun ReviewRow(
    state: UnchargedReviewState,
    row: ReviewRowUi,
    first: Boolean,
    onIntent: (UnchargedReviewIntent) -> Unit,
) = CashListRow(
    first = first,
    below = if (row.decision == ReviewDecision.Paid) {
        {
            Row(
                modifier = Modifier.padding(start = SaqzTheme.metrics.grid * 6),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            ) {
                SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.successForeground, size = SaqzTheme.metrics.horizontalPadding)
                Text(
                    text = stringResource(Res.string.retro_paid_line, state.gameDate),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
                SaqzTextLink(
                    label = stringResource(row.method.label()),
                    onClick = { onIntent(UnchargedReviewIntent.OpenMethodMenu(row.key)) },
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.testTag(UnchargedReviewTags.method(row.key)),
                )
            }
        }
    } else null,
) {
    SaqzAvatar(name = row.name)
    CashRowText(row.name, row.meta(), muted = row.noShow)
    SaqzSelectPill(
        label = stringResource(row.decision.label()),
        onClick = { onIntent(UnchargedReviewIntent.OpenMenu(row.key)) },
        tone = when (row.decision) {
            ReviewDecision.Charge -> SaqzSelectPillTone.Brand
            ReviewDecision.Paid -> SaqzSelectPillTone.Success
            ReviewDecision.Skip -> SaqzSelectPillTone.Neutral
        },
        clickDescription = stringResource(Res.string.retro_state_menu, row.name),
        enabled = !state.saving,
        modifier = Modifier.testTag(UnchargedReviewTags.pill(row.key)),
    )
}

@Composable
private fun ReviewRowUi.meta(): String = when {
    leftGroup -> stringResource(Res.string.retro_left)
    else -> listOfNotNull(
        hostName?.let { stringResource(Res.string.retro_kind_guest, it) } ?: stringResource(Res.string.retro_kind_avulso),
        stringResource(Res.string.retro_no_show).takeIf { noShow },
    ).joinToString(" · ")
}

/**
 * O resumo antes de confirmar: "2 cobranças · 1 já pagou · 2 sem cobrança", com Confirmar.
 * Só as partes diferentes de zero (roteiro 13, R5); a primeira que aparece fica em destaque.
 */
@Composable
private fun BottomBar(state: UnchargedReviewState, onIntent: (UnchargedReviewIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    val charges = when (state.chargeCount) {
        0 -> null
        1 -> stringResource(Res.string.retro_summary_charge_one)
        else -> stringResource(Res.string.retro_summary_charges, state.chargeCount)
    }
    val paid = when (state.paidCount) {
        0 -> null
        1 -> stringResource(Res.string.retro_summary_paid, state.paidCount)
        else -> stringResource(Res.string.retro_summary_paid_many, state.paidCount)
    }
    val skip = state.skipCount.takeIf { it > 0 }?.let { stringResource(Res.string.retro_summary_skip, it) }
    val parts = listOfNotNull(charges, paid, skip)
    SaqzDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaqzTheme.colors.surface)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        if (state.saveFailed) {
            Text(
                text = stringResource(Res.string.retro_failed),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(UnchargedReviewTags.Failure),
            )
        }
        Text(
            text = buildAnnotatedString {
                parts.forEachIndexed { index, part ->
                    if (index == 0) {
                        withStyle(SpanStyle(color = SaqzTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)) { append(part) }
                    } else {
                        append(" · $part")
                    }
                }
            },
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(UnchargedReviewTags.Summary),
        )
        SaqzButton(
            label = stringResource(Res.string.retro_confirm),
            onClick = { onIntent(UnchargedReviewIntent.Confirm) },
            fullWidth = true,
            loading = state.saving,
            enabled = !state.saving,
            modifier = Modifier.testTag(UnchargedReviewTags.Confirm),
        )
    }
}

/** O menu do estado: Cobrar · Já pagou · Não cobrar, com o que cada um faz. */
@Composable
private fun DecisionMenu(state: UnchargedReviewState, onIntent: (UnchargedReviewIntent) -> Unit) {
    val row = state.rows.firstOrNull { it.key == state.menuKey }
    val amount = parseBrlToCents(state.amountInput)?.takeIf { it > 0 }?.let(::formatBrlShort)
    SaqzBottomSheet(
        open = row != null,
        onClose = { onIntent(UnchargedReviewIntent.DismissMenu) },
        title = row?.name,
    ) {
        if (row == null) return@SaqzBottomSheet
        ReviewDecision.entries.forEachIndexed { index, decision ->
            SaqzSheetAction(
                icon = if (decision == row.decision) SaqzIcons.Check else decision.icon(),
                label = stringResource(decision.label()),
                hint = when (decision) {
                    ReviewDecision.Charge -> amount?.let { stringResource(Res.string.retro_menu_charge_hint, it) }
                        ?: stringResource(Res.string.retro_menu_charge_hint_plain)
                    ReviewDecision.Paid -> stringResource(Res.string.retro_menu_paid_hint)
                    ReviewDecision.Skip -> stringResource(Res.string.retro_menu_skip_hint)
                },
                onClick = { onIntent(UnchargedReviewIntent.Choose(row.key, decision)) },
                divider = index > 0,
                modifier = Modifier.testTag(UnchargedReviewTags.option(decision)),
            )
        }
    }
}

/** O menu "Como pagou" do Já pagou (DEB-152), no mesmo desenho do menu do estado: a marca na opção atual. */
@Composable
private fun MethodMenu(state: UnchargedReviewState, onIntent: (UnchargedReviewIntent) -> Unit) {
    val row = state.rows.firstOrNull { it.key == state.methodMenuKey }
    SaqzBottomSheet(
        open = row != null,
        onClose = { onIntent(UnchargedReviewIntent.DismissMethodMenu) },
        title = stringResource(Res.string.retro_method_title),
    ) {
        if (row == null) return@SaqzBottomSheet
        PaidMethod.entries.forEachIndexed { index, method ->
            SaqzSheetAction(
                icon = if (method == row.method) SaqzIcons.Check else method.icon(),
                label = stringResource(method.label()),
                onClick = { onIntent(UnchargedReviewIntent.ChooseMethod(row.key, method)) },
                divider = index > 0,
                modifier = Modifier.testTag(UnchargedReviewTags.methodOption(method)),
            )
        }
    }
}

private fun ReviewDecision.label() = when (this) {
    ReviewDecision.Charge -> Res.string.retro_state_charge
    ReviewDecision.Paid -> Res.string.retro_state_paid
    ReviewDecision.Skip -> Res.string.retro_state_skip
}

private fun ReviewDecision.icon() = when (this) {
    ReviewDecision.Charge -> SaqzIcons.Clock
    ReviewDecision.Paid -> SaqzIcons.Banknote
    ReviewDecision.Skip -> SaqzIcons.Ban
}

private fun PaidMethod.icon() = when (this) {
    PaidMethod.Pix -> SaqzIcons.Wallet
    PaidMethod.Cash -> SaqzIcons.Banknote
    PaidMethod.Other -> SaqzIcons.MoreHorizontal
}

private fun PaidMethod.label() = when (this) {
    PaidMethod.Pix -> Res.string.retro_method_pix
    PaidMethod.Cash -> Res.string.retro_method_cash
    PaidMethod.Other -> Res.string.retro_method_other
}

@Preview
@Composable
private fun UnchargedReviewPreview() = SaqzTheme {
    UnchargedReviewScreen(
        state = UnchargedReviewState(
            isLoading = false,
            gameDay = "Terça, 20/10",
            venueName = "CERET",
            gameDate = "20/10",
            amountInput = "20,00",
            suggested = true,
            rows = listOf(
                ReviewRowUi("a/0", "a", 0, "Caio Lima", null, false, false, ReviewDecision.Charge),
                ReviewRowUi("b/0", "b", 0, "Duda Alves", null, false, false, ReviewDecision.Paid),
                ReviewRowUi("c/1", "c", 1, "Júlia Rocha", "Bia", false, false, ReviewDecision.Charge),
                ReviewRowUi("d/0", "d", 0, "Rafa Nunes", null, false, true, ReviewDecision.Skip),
                ReviewRowUi("e/0", "e", 0, "Marcos Lima", null, true, false, ReviewDecision.Skip),
            ),
        ),
        onBack = {},
        onIntent = {},
    )
}
