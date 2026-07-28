package br.com.saqz.access.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * As nove rotas do fluxo 1, uma por tela do export (VUL-84).
 *
 * `Verification` saiu junto com a trava de e-mail, que o VUL-76 tirou do backend; e
 * `NameCompletion`/`PhoneCompletion` viraram [IdentityCompletion], porque o export junta
 * nome, telefone e foto na tela 1c.
 *
 * `:features:access` depende só do artefato leve `navigation3-runtime` pelo contrato
 * [NavKey]; nunca de `:navigation` nem do Navigation Compose 3 UI (`navigation3-ui`).
 */
@Serializable
sealed interface AccessRoute : NavKey {

    /** Antes de observar a autenticação: sem tela própria. */
    @Serializable
    data object Starting : AccessRoute

    /** 1a, e 1i quando o login recusa. */
    @Serializable
    data object Login : AccessRoute

    /** 1b, e 1j quando o cadastro recusa. */
    @Serializable
    data object Register : AccessRoute

    /** 1c: nome, telefone e foto numa tela só. */
    @Serializable
    data object IdentityCompletion : AccessRoute

    /** 1d. */
    @Serializable
    data object ForgotPassword : AccessRoute

    /**
     * 1e, 1f (reenvio) e 1k (recusa). Carrega o e-mail porque o passo é anônimo — não há
     * sessão de onde tirá-lo, e o subtítulo da 1e o mostra.
     */
    @Serializable
    data class ResetCode(val email: String) : AccessRoute

    /**
     * 1g. O token vem do `PasswordResetTicket` que a 1e trocou pelo código; o e-mail
     * segue junto para a 1e reaparecer intacta no voltar.
     */
    @Serializable
    data class NewPassword(val email: String, val token: String) : AccessRoute

    /** 1h. */
    @Serializable
    data object PasswordChanged : AccessRoute

    /** Carregando a sessão, ou o erro com repetir: sem tela do export. */
    @Serializable
    data object Bootstrap : AccessRoute
}
