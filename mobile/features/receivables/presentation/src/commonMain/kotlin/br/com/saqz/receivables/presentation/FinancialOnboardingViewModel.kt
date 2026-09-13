package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.domain.port.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FinancialOnboardingViewModel(private val gateway: FinancialOnboardingGateway,
    private val availability: ReceivablesAvailabilityGateway, private val session: ReceivablesSessionContext,
    private val identity: ReceivablesRecoveryIdentity, private val picker: ReceiptDocumentPicker, private val saved: SavedStateHandle) :
    MviViewModel<FinancialOnboardingState, FinancialOnboardingIntent, FinancialOnboardingEffect>(FinancialOnboardingState()) {
    private val actor = identity.currentUserId()
    private val key = session.currentKey()
    private var generation = 0
    private var command: ReceiptRegistrationCommand? = null
    private var selection: ReceiptFileCancellation? = null
    init {
        val marker = saved.get<String>("onboarding.attempt")?.let {
            runCatching { Json.decodeFromString<OnboardingAttempt>(it) }.getOrNull()
        }
        if (marker != null && marker.actor == actor && marker.kind in setOf("CREATE", "RECOVER", "UPLOAD")) {
            update { it.copy(attempt = marker) }
        }
        else saved.remove<String>("onboarding.attempt")
        refresh()
    }
    override fun onIntent(intent: FinancialOnboardingIntent) {
        if (!validSession()) return
        if (intent == FinancialOnboardingIntent.Refresh) { refreshIfIdle(); return }
        val s = state.value
        if (s.loading || s.picking) return
        when (intent) {
            FinancialOnboardingIntent.Refresh -> Unit
            is FinancialOnboardingIntent.Edit -> edit(intent)
            is FinancialOnboardingIntent.Company -> company(intent.value)
            is FinancialOnboardingIntent.Accept -> accept(intent.value)
            FinancialOnboardingIntent.Create -> create()
            FinancialOnboardingIntent.Recover -> recover()
            is FinancialOnboardingIntent.Open -> open(intent.documentId)
            is FinancialOnboardingIntent.ChooseFile -> choose(intent.documentId)
            FinancialOnboardingIntent.DiscardFile -> if (!s.pending) update { it.copy(selectedFile = null, selectedDocument = null) }
            FinancialOnboardingIntent.Upload -> upload()
            FinancialOnboardingIntent.OpenFailed -> failed(ReceiptError.UNAVAILABLE)
        }
    }
    private fun refreshIfIdle() { if (!state.value.loading && !state.value.picking) refresh() }
    private fun edit(intent: FinancialOnboardingIntent.Edit) {
        if (state.value.canEdit) update { it.copy(form = it.form.copy(
            values = it.form.values + (intent.field to intent.value.take(200))), accepted = false) }
    }
    private fun company(value: Boolean) {
        if (state.value.canEdit) update { it.copy(form = OnboardingForm(company = value), accepted = false) }
    }
    private fun accept(value: Boolean) { if (state.value.canAccept) update { it.copy(accepted = value) } }
    private fun refresh(replay: Boolean = false) {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, error = null, accepted = false, terms = null) }
        viewModelScope.launch {
            val own = gateway.mine(actor ?: return@launch)
            if (!current(expected)) return@launch
            if (own is SaqzResult.Failure) { failed(own.error); return@launch }
            val account = (own as SaqzResult.Success).value
            update { it.copy(account = account, discovered = true) }
            if (account == null) {
                if (state.value.pending) {
                    update { it.copy(loading = false) }
                    if (replay && state.value.attempt?.kind == "CREATE" && command != null) writeCreate()
                } else loadTerms(expected)
            } else {
                if (state.value.attempt?.kind == "CREATE") { clearAttempt(); update { it.copy(form = OnboardingForm()) }; changed() }
                if (replay && state.value.attempt?.kind == "RECOVER") writeRecovery()
                else loadDocuments(expected)
            }
        }
    }
    private suspend fun loadTerms(expected: Int) {
        val available = availability.get()
        if (!current(expected)) return
        if (available is SaqzResult.Failure) { failed(ReceiptError.UNAVAILABLE); return }
        val enabled = (available as SaqzResult.Success).value.newJourneysAvailable
        update { it.copy(available = enabled) }
        if (!enabled) { update { it.copy(loading = false) }; return }
        when (val terms = gateway.currentTerms()) {
            is SaqzResult.Failure -> if (current(expected)) failed(terms.error)
            is SaqzResult.Success -> if (current(expected)) update { it.copy(loading = false, terms = terms.value) }
        }
    }
    private suspend fun loadDocuments(expected: Int) {
        val documents = gateway.documents()
        if (!current(expected)) return
        if (documents is SaqzResult.Failure) { failed(documents.error); return }
        val list = (documents as SaqzResult.Success).value
        val account = gateway.mine(actor!!)
        if (!current(expected)) return
        if (account !is SaqzResult.Success || account.value == null) { failed(ReceiptError.UNAVAILABLE); return }
        val pending = state.value.attempt
        if (pending?.kind == "UPLOAD" && list.none { it.id == pending.documentId && it.status == pending.documentStatus }) clearAttempt()
        if (pending?.kind == "RECOVER" && account.value!!.registration != AccountRegistration.INCOMPLETE) clearAttempt()
        update { it.copy(loading = false, account = account.value, documents = list, selectedFile = null, selectedDocument = null) }
    }
    private fun create() {
        val s = state.value
        if (!s.canCreate) return
        val registration = s.form.registration() ?: return
        val id = Uuid.random().toString()
        command = ReceiptRegistrationCommand(id, s.terms!!.version, true, registration)
        save(OnboardingAttempt(actor!!, id, "CREATE")); writeCreate()
    }
    private fun writeCreate() {
        val request = command ?: return
        val expected = ++generation
        update { it.copy(loading = true, accepted = false, error = null) }
        viewModelScope.launch {
            when (val result = gateway.create(actor!!, request)) {
                is SaqzResult.Failure -> if (current(expected)) writeFailed(result.error)
                is SaqzResult.Success -> if (current(expected)) {
                    clearAttempt(); update { it.copy(account = result.value, form = OnboardingForm(), terms = null) }; changed()
                    loadDocuments(expected)
                }
            }
        }
    }
    private fun recover() {
        if (state.value.pending) { refresh(replay = true); return }
        if (state.value.account?.registration != AccountRegistration.INCOMPLETE) return
        save(OnboardingAttempt(actor!!, Uuid.random().toString(), "RECOVER")); writeRecovery()
    }
    private fun writeRecovery() {
        val attempt = state.value.attempt ?: return
        val expected = ++generation
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = gateway.recover(actor!!, attempt.requestId)) {
                is SaqzResult.Failure -> if (current(expected)) writeFailed(result.error)
                is SaqzResult.Success -> if (current(expected)) {
                    clearAttempt(); update { it.copy(account = result.value) }; changed(); loadDocuments(expected)
                }
            }
        }
    }
    private fun open(id: String) {
        val d = state.value.documents.singleOrNull { it.id == id } ?: return
        val url = d.onboardingUrl ?: return
        if (state.value.canUseDocuments && d.needsSubmission && safeOnboardingUrl(url)) {
            emit(FinancialOnboardingEffect.Open(id, url, generation))
        }
    }
    private fun choose(id: String) {
        val d = state.value.documents.singleOrNull { it.id == id } ?: return
        if (!state.value.canUseDocuments || !d.needsSubmission || d.onboardingUrl != null) return
        val expected = ++generation
        update { it.copy(picking = true, selectedDocument = id, selectedFile = null, uploaded = false) }
        selection = picker.choose { result -> viewModelScope.launch {
            if (!current(expected)) return@launch
            update { it.copy(picking = false) }
            when (result) {
                ReceiptFileSelection.Cancelled -> update { it.copy(selectedDocument = null) }
                ReceiptFileSelection.Invalid -> failed(ReceiptError.INVALID)
                is ReceiptFileSelection.Selected -> if (result.file.valid()) update { it.copy(selectedFile = result.file) }
                    else failed(ReceiptError.INVALID)
            }
        } }
    }
    private fun upload() {
        val s = state.value; val file = s.selectedFile ?: return
        val d = s.documents.singleOrNull { it.id == s.selectedDocument } ?: return
        if (!s.canUseDocuments || !file.valid() || !d.needsSubmission || d.onboardingUrl != null) return
        val id = Uuid.random().toString()
        save(OnboardingAttempt(actor!!, id, "UPLOAD", d.id, d.status))
        val expected = ++generation
        update { it.copy(loading = true, selectedFile = null) }
        viewModelScope.launch {
            when (val result = gateway.upload(d, id, file)) {
                is SaqzResult.Failure -> if (current(expected)) writeFailed(result.error)
                is SaqzResult.Success -> if (current(expected)) {
                    clearAttempt(); update { it.copy(uploaded = true) }; changed(); loadDocuments(expected)
                }
            }
        }
    }
    private fun save(attempt: OnboardingAttempt) { saved["onboarding.attempt"] = Json.encodeToString(attempt);
        update { it.copy(attempt = attempt) } }
    private fun clearAttempt() { saved.remove<String>("onboarding.attempt"); command = null; update { it.copy(attempt = null) } }
    private fun writeFailed(error: ReceiptError) {
        if (error !in setOf(ReceiptError.UNCERTAIN, ReceiptError.NETWORK, ReceiptError.UNAVAILABLE)) clearAttempt()
        failed(error)
    }
    private fun failed(error: ReceiptError) {
        if (error == ReceiptError.SIGNED_OUT) clearSession()
        else update { it.copy(loading = false, picking = false, error = error) }
    }
    private fun changed() = emit(FinancialOnboardingEffect.Changed(generation))
    private fun current(expected: Int) = validSession() && expected == generation
    fun validEffect(effect: FinancialOnboardingEffect): Boolean {
        if (!current(effect.generation)) return false
        return effect !is FinancialOnboardingEffect.Open || (state.value.canUseDocuments && state.value.documents.any {
            it.id == effect.documentId && it.onboardingUrl == effect.url && it.needsSubmission && safeOnboardingUrl(effect.url)
        })
    }
    fun validSession(): Boolean {
        if (actor != null && key != null && key == session.currentKey() && actor == identity.currentUserId()) return true
        clearSession(); return false
    }
    private fun clearSession() {
        generation++; selection?.cancel(); selection = null; clearAttempt()
        update { FinancialOnboardingState(loading = false, error = ReceiptError.SIGNED_OUT) }
    }
    override fun onCleared() { selection?.cancel(); command = null; super.onCleared() }
}
