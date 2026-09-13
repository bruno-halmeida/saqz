package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object FinancialOnboardingTags {
    const val Screen = "financial-onboarding"
    const val Create = "financial-onboarding-create"
    const val Accept = "financial-onboarding-accept"
    const val Recover = "financial-onboarding-recover"
    const val Upload = "financial-onboarding-upload"
    const val Company = "financial-onboarding-company"
    fun field(field: OnboardingField) = "financial-onboarding-${field.name}"
    fun document(id: String) = "financial-onboarding-document-$id"
}
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FinancialOnboardingRoot(onBack: () -> Unit, onChange: () -> Unit,
    viewModel: FinancialOnboardingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    BackHandler { if (!state.pending) onBack() }
    LifecycleResumeEffect(viewModel) { viewModel.onIntent(FinancialOnboardingIntent.Refresh); onPauseOrDispose { } }
    ObserveAsEvents(viewModel.effects) { effect -> if (viewModel.validEffect(effect)) when (effect) {
        is FinancialOnboardingEffect.Changed -> onChange()
        is FinancialOnboardingEffect.Open -> runCatching { uri.openUri(effect.url) }
            .onFailure { viewModel.onIntent(FinancialOnboardingIntent.OpenFailed) }
    } }
    FinancialOnboardingScreen(state, viewModel::onIntent, onBack)
}
@Composable
fun FinancialOnboardingScreen(state: FinancialOnboardingState, onIntent: (FinancialOnboardingIntent) -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier) = PaymentPage(stringResource(Res.string.onboarding_title),
    FinancialOnboardingTags.Screen, { if (!state.pending) onBack() }, modifier) {
    if (state.loading || state.picking) SaqzSpinner()
    state.error?.let { PaymentError(it) }
    if (state.pending) {
        Text(stringResource(Res.string.onboarding_pending)); Text(stringResource(Res.string.onboarding_pending_help))
        SaqzButton(stringResource(Res.string.onboarding_recover), { onIntent(FinancialOnboardingIntent.Recover) },
            enabled = !state.loading, fullWidth = true, modifier = Modifier.testTag(FinancialOnboardingTags.Recover))
    }
    if (state.account == null && state.discovered && !state.pending) {
        Text(stringResource(Res.string.onboarding_empty)); Text(stringResource(Res.string.onboarding_intro))
        if (!state.available && !state.loading && state.error == null) Text(stringResource(Res.string.onboarding_unavailable))
        if (state.available) OnboardingRegistration(state, onIntent)
    }
    state.account?.let { account ->
        Text(stringResource(registrationLabel(account.registration)), style = SaqzTheme.typography.body)
        Text(stringResource(Res.string.onboarding_account_help))
        if (account.registration == AccountRegistration.INCOMPLETE && !state.pending) {
            SaqzButton(stringResource(Res.string.onboarding_provision), { onIntent(FinancialOnboardingIntent.Recover) },
                enabled = !state.loading, fullWidth = true)
        }
        OnboardingDocuments(state, onIntent)
    }
    SaqzButton(stringResource(Res.string.onboarding_refresh), { onIntent(FinancialOnboardingIntent.Refresh) },
        enabled = !state.loading && !state.picking && state.error != ReceiptError.SIGNED_OUT, fullWidth = true,
        variant = SaqzButtonVariant.Secondary)
}
@Composable
private fun OnboardingRegistration(state: FinancialOnboardingState, onIntent: (FinancialOnboardingIntent) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
    SaqzSwitch(state.form.company, { onIntent(FinancialOnboardingIntent.Company(it)) },
        label = stringResource(Res.string.onboarding_company), enabled = state.canEdit,
            modifier = Modifier.testTag(FinancialOnboardingTags.Company))
    val fields = OnboardingField.entries.filter {
        it != OnboardingField.COMPANY_TYPE && (it != OnboardingField.BIRTH_DATE || !state.form.company)
    }
    for (field in fields) {
        SaqzInput(state.form[field], { onIntent(FinancialOnboardingIntent.Edit(field, it)) }, stringResource(fieldLabel(field)),
            enabled = state.canEdit, modifier = Modifier.testTag(FinancialOnboardingTags.field(field)), keyboardType = fieldKeyboard(field))
    }
    if (state.form.company) {
        Text(stringResource(Res.string.onboarding_company_type))
        for ((value, label) in listOf("MEI" to Res.string.onboarding_mei, "LIMITED" to Res.string.onboarding_limited,
            "INDIVIDUAL" to Res.string.onboarding_individual, "ASSOCIATION" to Res.string.onboarding_association)) {
            SaqzSwitch(state.form[OnboardingField.COMPANY_TYPE] == value,
                { onIntent(FinancialOnboardingIntent.Edit(OnboardingField.COMPANY_TYPE, value)) }, label = stringResource(label),
                    enabled = state.canEdit)
        }
    }
    incomeCents(state.form[OnboardingField.INCOME])?.let { Text(stringResource(Res.string.onboarding_income_review, formatBrl(it))) }
    state.terms?.let { Text(stringResource(Res.string.receipt_terms, it.version)); Text(it.content) }
    SaqzSwitch(state.accepted, { onIntent(FinancialOnboardingIntent.Accept(it)) }, label = stringResource(Res.string.onboarding_accept),
        enabled = state.canAccept, modifier = Modifier.testTag(FinancialOnboardingTags.Accept))
    SaqzButton(stringResource(Res.string.onboarding_create), { onIntent(FinancialOnboardingIntent.Create) },
        enabled = state.canCreate, fullWidth = true, modifier = Modifier.testTag(FinancialOnboardingTags.Create))
}
@Composable
private fun OnboardingDocuments(state: FinancialOnboardingState, onIntent: (FinancialOnboardingIntent) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
    Text(stringResource(Res.string.onboarding_documents))
    if (state.uploaded) Text(stringResource(Res.string.onboarding_uploaded))
    if (!state.loading && state.error == null && state.documents.isEmpty()) Text(stringResource(Res.string.onboarding_no_documents))
    state.documents.forEach { d -> SaqzCard {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(d.description ?: stringResource(Res.string.onboarding_document_kind, d.type))
            Text(stringResource(documentLabel(d.status)))
            val label = if (d.onboardingUrl == null) Res.string.onboarding_choose else Res.string.onboarding_open
            val intent = if (d.onboardingUrl == null) FinancialOnboardingIntent.ChooseFile(d.id) else FinancialOnboardingIntent.Open(d.id)
            if (d.needsSubmission) SaqzButton(stringResource(label), { onIntent(intent) },
                enabled = state.canUseDocuments && !state.picking, fullWidth = true,
                    modifier = Modifier.testTag(FinancialOnboardingTags.document(d.id)))
        }
    } }
    if (state.documents.any { it.onboardingUrl == null && it.needsSubmission }) Text(stringResource(Res.string.onboarding_file_help))
    state.selectedFile?.let { file ->
        Text(stringResource(Res.string.onboarding_file_selected, file.contentType, file.bytes.size.toString()))
        SaqzButton(stringResource(Res.string.onboarding_upload), { onIntent(FinancialOnboardingIntent.Upload) },
            enabled = state.canUseDocuments, fullWidth = true, modifier = Modifier.testTag(FinancialOnboardingTags.Upload))
        SaqzButton(stringResource(Res.string.onboarding_discard), { onIntent(FinancialOnboardingIntent.DiscardFile) }, fullWidth = true,
            enabled = !state.pending, variant = SaqzButtonVariant.Secondary)
    }
}
private fun fieldLabel(field: OnboardingField): StringResource = when (field) {
    OnboardingField.NAME -> Res.string.onboarding_name; OnboardingField.EMAIL -> Res.string.onboarding_email
    OnboardingField.DOCUMENT -> Res.string.onboarding_document; OnboardingField.PHONE -> Res.string.onboarding_phone
    OnboardingField.INCOME -> Res.string.onboarding_income; OnboardingField.STREET -> Res.string.onboarding_street
    OnboardingField.NUMBER -> Res.string.onboarding_number; OnboardingField.PROVINCE -> Res.string.onboarding_province
    OnboardingField.POSTAL_CODE -> Res.string.onboarding_postal; OnboardingField.BIRTH_DATE -> Res.string.onboarding_birth
    OnboardingField.COMPANY_TYPE -> Res.string.onboarding_company_type
}
private fun fieldKeyboard(field: OnboardingField) = when (field) {
    OnboardingField.INCOME -> KeyboardType.Decimal
    OnboardingField.EMAIL -> KeyboardType.Email
    OnboardingField.DOCUMENT, OnboardingField.PHONE, OnboardingField.POSTAL_CODE -> KeyboardType.Number
    else -> KeyboardType.Text
}
private fun registrationLabel(status: AccountRegistration) = when (status) {
    AccountRegistration.INCOMPLETE -> Res.string.onboarding_incomplete
    AccountRegistration.UNDER_REVIEW -> Res.string.onboarding_under_review
    AccountRegistration.CORRECTION_REQUIRED -> Res.string.onboarding_correction
    AccountRegistration.APPROVED -> Res.string.onboarding_approved
    AccountRegistration.REJECTED -> Res.string.onboarding_rejected
}
private fun documentLabel(status: String) = when (status) {
    "PENDING" -> Res.string.onboarding_document_pending; "AWAITING_APPROVAL" -> Res.string.onboarding_document_review
    "APPROVED" -> Res.string.onboarding_document_approved; "REJECTED" -> Res.string.onboarding_document_rejected
    else -> Res.string.onboarding_document_unknown
}
@Preview @Composable private fun OnboardingPreview() = SaqzTheme {
    FinancialOnboardingScreen(FinancialOnboardingState(loading = false, discovered = true), {}, {})
}
