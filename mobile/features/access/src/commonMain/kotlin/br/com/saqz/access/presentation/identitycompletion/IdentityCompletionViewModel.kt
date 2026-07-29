package br.com.saqz.access.presentation.identitycompletion

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.ProfilePhotoCallback
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.message
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Projeta a 1c da [SessionAccessStateMachine] compartilhada — nome, telefone e foto vivem
 * lá, porque é lá que o portão de identidade é decidido. Desta ViewModel são só a escolha
 * da imagem (porta nativa, callback) e a decodificação dos bytes para a tela.
 *
 * `Nothing` no lugar do efeito: a 1c não emite nenhum, e quem troca de tela nos dois
 * desfechos — desistiu, concluiu — é o estado de sessão que o gate de rota lê.
 */
class IdentityCompletionViewModel(
    private val session: SessionAccessStateMachine,
    private val photos: NativeProfilePhotoPort,
) : MviViewModel<IdentityCompletionState, IdentityCompletionIntent, Nothing>(
    // O primeiro quadro sai do estado que já existe, e não de um vazio: a máquina é
    // singleton e a 1c só é composta porque ela **já** está em `CompletingIdentity` — abrir
    // com os campos em branco piscaria o nome do provedor por um quadro.
    initialStateOf(session.state.value),
) {
    private var choosing: Cancelable? = null

    /**
     * A escolha que morreu na plataforma, antes de virar bytes. Vive aqui e não na máquina
     * de sessão porque nada saiu do aparelho — o mesmo aviso do envio recusado serve, e
     * some assim que a pessoa tenta de novo.
     */
    private var pickFailed = false

    // Decodificar é caro e o estado reemite a cada tecla: a imagem só é decodificada quando
    // a escolha muda de identidade, comparada por referência (a máquina copia o estado, não
    // a foto).
    private var decodedFrom: ProfilePhotoResult.Selected? = null
    private var decoded: ImageBitmap? = null

    init {
        session.state
            .onEach { current ->
                if (current is SessionAccessState.CompletingIdentity) {
                    update { current.project(decode(current.photo), pickFailed) }
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: IdentityCompletionIntent) {
        when (intent) {
            is IdentityCompletionIntent.UpdateName -> session.onIntent(SessionIntent.UpdateName(intent.value))
            is IdentityCompletionIntent.UpdatePhone -> session.onIntent(SessionIntent.UpdatePhone(intent.value))
            IdentityCompletionIntent.PickPhoto -> pickPhoto()
            IdentityCompletionIntent.Submit -> session.onIntent(SessionIntent.CompleteIdentity)
            IdentityCompletionIntent.Back -> session.onIntent(SessionIntent.Logout)
        }
    }

    override fun onCleared() {
        // A escolha aberta some com a tela: o adapter apaga o arquivo temporário dele.
        choosing?.cancel()
        choosing = null
    }

    /**
     * A galeria, e não a câmera: o export desenha um botão só, e nenhuma das 11 telas do
     * fluxo 1 tem a folha de escolha entre as duas origens.
     *
     * ponytail: quando a folha existir, é `chooseCamera` que entra aqui ao lado —
     * `NativeProfilePhotoPort` já expõe as duas.
     */
    private fun pickPhoto() {
        choosing?.cancel()
        pickFailed = false
        choosing = photos.chooseLibrary(object : ProfilePhotoCallback {
            override fun complete(result: ProfilePhotoResult) {
                choosing = null
                when (result) {
                    is ProfilePhotoResult.Selected -> session.onIntent(SessionIntent.UpdatePhoto(result))
                    // Desistir não é falha, e não deixa recado na tela.
                    ProfilePhotoResult.Cancelled -> Unit
                    ProfilePhotoResult.Failed -> {
                        pickFailed = true
                        update { it.copy(photoFailed = true) }
                    }
                }
            }
        })
    }

    private fun decode(photo: ProfilePhotoResult.Selected?): ImageBitmap? {
        if (photo == null) {
            decodedFrom = null
            decoded = null
            return null
        }
        if (photo !== decodedFrom) {
            decodedFrom = photo
            // Bytes que a plataforma recortou mas que o decodificador recusa: a tela mostra
            // o círculo vazio em vez de derrubar a composição.
            decoded = runCatching { photo.bytes.decodeToImageBitmap() }.getOrNull()
        }
        return decoded
    }
}

private fun initialStateOf(state: SessionAccessState): IdentityCompletionState =
    (state as? SessionAccessState.CompletingIdentity)?.project(photo = null, pickFailed = false)
        ?: IdentityCompletionState()

private fun SessionAccessState.CompletingIdentity.project(
    photo: ImageBitmap?,
    pickFailed: Boolean,
) = IdentityCompletionState(
    name = name,
    phone = phone,
    photo = photo,
    isLoading = isLoading,
    error = error?.message(),
    invalidName = invalidName,
    invalidPhone = invalidPhone,
    photoFailed = photoFailed || pickFailed,
)
