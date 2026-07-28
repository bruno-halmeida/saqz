package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.passwordreset.PasswordAccounts
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserRecord

/**
 * O Admin SDK só é dependência do bootstrap, e é ele quem guarda a senha — o Saqz
 * nunca a persiste. `getUserByEmail` lança quando não há conta, e é assim que o
 * `request` decide se manda ou não o e-mail sem contar isso para quem pediu.
 */
class FirebasePasswordAccounts(private val firebaseApp: FirebaseApp) : PasswordAccounts {
    override fun exists(email: String): Boolean = uid(email) != null

    override fun updatePassword(email: String, newPassword: String): Boolean {
        val uid = uid(email) ?: return false
        FirebaseAuth.getInstance(firebaseApp)
            .updateUser(UserRecord.UpdateRequest(uid).setPassword(newPassword))
        return true
    }

    private fun uid(email: String): String? = try {
        FirebaseAuth.getInstance(firebaseApp).getUserByEmail(email).uid
    } catch (_: FirebaseAuthException) {
        null
    }
}
