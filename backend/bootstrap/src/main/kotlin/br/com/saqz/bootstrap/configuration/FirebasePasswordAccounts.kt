package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.passwordreset.PasswordAccounts
import br.com.saqz.access.application.passwordreset.PasswordAccountsUnavailable
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserRecord

/**
 * O Admin SDK só é dependência do bootstrap, e é ele quem guarda a senha — o Saqz
 * nunca a persiste.
 *
 * Só `USER_NOT_FOUND` significa "não existe conta". Erro transitório, de autorização
 * ou de configuração sobe como [PasswordAccountsUnavailable]: tratá-los como ausência
 * faria o `request` engolir um provedor fora do ar, e o `confirm` responder "token
 * inválido" para um token que era perfeitamente válido.
 */
class FirebasePasswordAccounts(private val firebaseApp: FirebaseApp) : PasswordAccounts {
    override fun exists(email: String): Boolean = uid(email) != null

    override fun updatePassword(email: String, newPassword: String): Boolean {
        val uid = uid(email) ?: return false
        try {
            FirebaseAuth.getInstance(firebaseApp)
                .updateUser(UserRecord.UpdateRequest(uid).setPassword(newPassword))
        } catch (failure: Exception) {
            throw unavailable(failure)
        }
        return true
    }

    private fun uid(email: String): String? = try {
        FirebaseAuth.getInstance(firebaseApp).getUserByEmail(email).uid
    } catch (failure: FirebaseAuthException) {
        if (failure.authErrorCode == AuthErrorCode.USER_NOT_FOUND) null else throw unavailable(failure)
    } catch (failure: Exception) {
        throw unavailable(failure)
    }

    private fun unavailable(failure: Exception): PasswordAccountsUnavailable =
        failure as? PasswordAccountsUnavailable ?: PasswordAccountsUnavailable(failure)
}
