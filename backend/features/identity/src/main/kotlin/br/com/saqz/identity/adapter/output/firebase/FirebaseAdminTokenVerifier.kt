package br.com.saqz.identity.adapter.output.firebase

import br.com.saqz.identity.application.IdentityTokenVerifier
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.sharedkernel.RequestIdentity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import java.net.ConnectException
import java.net.SocketTimeoutException

class FirebaseAdminTokenVerifier internal constructor(
    private val decode: (String) -> DecodedFirebaseToken,
) : IdentityTokenVerifier {
    constructor(firebaseApp: FirebaseApp) : this(firebaseTokenDecoder(firebaseApp))

    override fun verify(token: RawIdentityToken): TokenVerification =
        try {
            val decoded = decode(token.value)
            TokenVerification.Verified(
                RequestIdentity(decoded.subject, decoded.email, decoded.emailVerified, decoded.displayName),
            )
        } catch (_: InvalidFirebaseToken) {
            TokenVerification.Rejected
        } catch (_: ExpiredFirebaseToken) {
            TokenVerification.Rejected
        } catch (_: RevokedFirebaseToken) {
            TokenVerification.Rejected
        } catch (_: Exception) {
            TokenVerification.ProviderUnavailable
        }
}

internal data class DecodedFirebaseToken(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean?,
    val displayName: String? = null,
)

internal class InvalidFirebaseToken(cause: Throwable? = null) : Exception(cause)
internal class ExpiredFirebaseToken(cause: Throwable? = null) : Exception(cause)
internal class RevokedFirebaseToken(cause: Throwable? = null) : Exception(cause)
internal class FirebaseProviderFailure(cause: Throwable? = null) : Exception(cause)

internal fun firebaseTokenDecoder(firebaseApp: FirebaseApp): (String) -> DecodedFirebaseToken {
    val auth = FirebaseAuth.getInstance(firebaseApp)
    return { rawToken ->
        try {
            val token = auth.verifyIdToken(rawToken, true)
            DecodedFirebaseToken(
                subject = token.uid,
                email = token.email,
                emailVerified = token.claims["email_verified"] as? Boolean,
                displayName = token.name,
            )
        } catch (failure: FirebaseAuthException) {
            throw classify(failure)
        } catch (failure: SocketTimeoutException) {
            throw FirebaseProviderFailure(failure)
        } catch (failure: ConnectException) {
            throw FirebaseProviderFailure(failure)
        }
    }
}

private fun classify(failure: FirebaseAuthException): Exception {
    val unavailableCause = generateSequence<Throwable>(failure) { it.cause }
        .any { it is SocketTimeoutException || it is ConnectException }
    if (unavailableCause) return FirebaseProviderFailure(failure)

    return when (failure.authErrorCode?.name) {
        "EXPIRED_ID_TOKEN" -> ExpiredFirebaseToken(failure)
        "REVOKED_ID_TOKEN" -> RevokedFirebaseToken(failure)
        "INVALID_ID_TOKEN" -> InvalidFirebaseToken(failure)
        else -> FirebaseProviderFailure(failure)
    }
}
