package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.emailverification.VerificationLinkGenerator
import br.com.saqz.access.application.emailverification.VerificationLinksUnavailable
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

/**
 * O Firebase manda o e-mail feio se o app chama `sendEmailVerification`. Daqui a gente
 * só pede o link (`returnOobLink`) e o SMTP nosso veste o HTML.
 */
class FirebaseVerificationLinks(private val firebaseApp: FirebaseApp) : VerificationLinkGenerator {
    override fun generate(email: String): String? = try {
        FirebaseAuth.getInstance(firebaseApp).generateEmailVerificationLink(email)
    } catch (failure: FirebaseAuthException) {
        if (failure.authErrorCode == AuthErrorCode.USER_NOT_FOUND) null else throw unavailable(failure)
    } catch (failure: Exception) {
        throw unavailable(failure)
    }

    private fun unavailable(failure: Exception): VerificationLinksUnavailable =
        failure as? VerificationLinksUnavailable ?: VerificationLinksUnavailable(failure)
}
