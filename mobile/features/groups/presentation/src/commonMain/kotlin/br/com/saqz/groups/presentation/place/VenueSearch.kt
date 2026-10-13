package br.com.saqz.groups.presentation.place

import androidx.compose.runtime.Immutable
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.place.PlaceDetails
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.domain.place.PlacesGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * O que a UI de um campo de quadra precisa: [available] vem do backend (nunca de constante no
 * app) e, com ele desligado, o campo é só texto livre.
 */
@Immutable
data class VenueSearchState(
    val available: Boolean = false,
    val suggestions: List<PlaceSuggestion> = emptyList(),
)

/**
 * Busca de endereço dos campos de quadra (Google Places pelo backend). Só reage ao que a pessoa
 * digita — nada de prefetch, e nada é guardado: a sugestão escolhida vira o texto do campo.
 *
 * A sessão de busca é a do Google: o mesmo token em todas as sugestões e no `details` que a
 * fecha; a seguinte começa com token novo. O campo continua aceitando texto livre sempre.
 */
class VenueSearch(
    private val scope: CoroutineScope,
    private val gateway: PlacesGateway?,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val newSession: () -> String = { Uuid.random().toString() },
) {
    private val mutableState = MutableStateFlow(VenueSearchState())
    val state: StateFlow<VenueSearchState> = mutableState.asStateFlow()

    private var session: String? = null
    private var searching: Job? = null

    fun refreshAvailability() {
        val gateway = gateway ?: return
        scope.launch {
            val available = (gateway.availability() as? SaqzResult.Success)?.value == true
            mutableState.update { it.copy(available = available, suggestions = if (available) it.suggestions else emptyList()) }
        }
    }

    /** O texto que a pessoa digitou no campo. Com menos de [MIN_QUERY] caracteres, nada é buscado. */
    fun onQuery(text: String) {
        searching?.cancel()
        val query = text.trim()
        val gateway = gateway
        if (gateway == null || !mutableState.value.available || query.length < MIN_QUERY) {
            clearSuggestions()
            return
        }
        searching = scope.launch {
            delay(debounceMillis)
            val token = session ?: newSession().also { session = it }
            when (val result = gateway.autocomplete(query, token)) {
                is SaqzResult.Success -> mutableState.update { it.copy(suggestions = result.value) }
                is SaqzResult.Failure -> mutableState.update { it.copy(suggestions = emptyList()) }
            }
        }
    }

    /** Resolve a sugestão e fecha a sessão. Falha deixa tudo como está: a pessoa segue digitando. */
    fun select(suggestion: PlaceSuggestion, onPicked: (PlaceDetails) -> Unit) {
        val gateway = gateway ?: return
        searching?.cancel()
        val token = session ?: newSession().also { session = it }
        scope.launch {
            when (val result = gateway.details(suggestion.placeId, token)) {
                is SaqzResult.Success -> {
                    session = null
                    clearSuggestions()
                    onPicked(result.value)
                }
                is SaqzResult.Failure -> Unit
            }
        }
    }

    /** Fecha a lista sem escolher (foco saiu do campo, ou a pessoa preferiu digitar). */
    fun dismiss() {
        searching?.cancel()
        clearSuggestions()
    }

    private fun clearSuggestions() {
        mutableState.update { if (it.suggestions.isEmpty()) it else it.copy(suggestions = emptyList()) }
    }

    companion object {
        const val DEBOUNCE_MILLIS = 350L
        const val MIN_QUERY = 3
    }
}
