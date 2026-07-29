package br.com.saqz.access.presentation.identitycompletion

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import br.com.saqz.designsystem.UiText

/**
 * A 1c: foto, nome e telefone numa tela só.
 *
 * A tela é uma, mas o momento não — `SessionAccessState.CompletingIdentity` pode chegar
 * sem sessão (antes do bootstrap, quando o provedor não deu nome) ou com ela (depois,
 * quando falta o telefone). A tela não distingue os dois: os mesmos campos, o mesmo botão.
 *
 * [photo] já vem decodificada porque quem escolhe a imagem é a porta nativa, que devolve
 * bytes: decodificar na tela custaria um decode por recomposição.
 */
@Immutable
data class IdentityCompletionState(
    val name: String = "",
    val phone: String = "",
    val photo: ImageBitmap? = null,
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val invalidName: Boolean = false,
    val invalidPhone: Boolean = false,
    val photoFailed: Boolean = false,
)

sealed interface IdentityCompletionIntent {
    data class UpdateName(val value: String) : IdentityCompletionIntent

    data class UpdatePhone(val value: String) : IdentityCompletionIntent

    data object PickPhoto : IdentityCompletionIntent

    data object Submit : IdentityCompletionIntent

    /**
     * O voltar do canto. Não há tela atrás — o gate de rota colapsa o stack numa entrada
     * só enquanto a identidade está incompleta —, então voltar daqui é desistir da conta
     * recém-autenticada e cair no login.
     */
    data object Back : IdentityCompletionIntent
}

/** A 1c não tem efeito de uma vez só: quem troca de tela é o estado de sessão. */
sealed interface IdentityCompletionEffect
