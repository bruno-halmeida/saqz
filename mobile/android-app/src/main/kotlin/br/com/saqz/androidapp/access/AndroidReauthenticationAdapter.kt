package br.com.saqz.androidapp.access

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeReauthentication
import br.com.saqz.access.domain.port.NativeReauthenticationPort
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider

internal sealed interface AndroidReauthentication {
    class Password(val password: String) : AndroidReauthentication
    class Google(val idToken: String) : AndroidReauthentication
}

internal interface AndroidFirebaseReauthenticationClient {
    val currentSubject: String? get() = null
    fun reauthenticate(
        subject: String, credential: AndroidReauthentication, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit,
    ) = done(AndroidProviderResult.Failure(AndroidProviderFailure.UNAVAILABLE))
}

internal class AndroidReauthenticationAdapter(
    private val firebase: AndroidFirebaseReauthenticationClient,
    private val google: AndroidGoogleCredentialClient,
) : NativeReauthenticationPort {
    override fun reauthenticate(request: NativeReauthentication, done: AuthCallback) {
        val subject = firebase.currentSubject
            ?: return done.complete(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))
        when (request) {
            is NativeReauthentication.Password ->
                firebase.reauthenticate(subject, AndroidReauthentication.Password(request.password)) {
                    done.complete(it.toAuthResult())
                }
            NativeReauthentication.Google -> google.requestIdToken { credential ->
                when (credential) {
                    is AndroidProviderResult.Success ->
                        firebase.reauthenticate(subject, AndroidReauthentication.Google(credential.value)) {
                            done.complete(it.toAuthResult())
                        }
                    AndroidProviderResult.Cancelled -> done.complete(AuthResult.Cancelled)
                    is AndroidProviderResult.Failure -> done.complete(AuthResult.Failure(credential.code.toNative()))
                }
            }
        }
    }

}

internal class FirebaseSdkReauthenticationClient(private val auth: FirebaseAuth) : AndroidFirebaseReauthenticationClient {
    override val currentSubject: String? get() = auth.currentUser?.uid

    override fun reauthenticate(
        subject: String, credential: AndroidReauthentication, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit,
    ) {
        val user = auth.currentUser?.takeIf { it.uid == subject } ?: return done(missingUser())
        val sdkCredential = when (credential) {
            is AndroidReauthentication.Password -> {
                val email = user.email ?: return done(missingUser())
                if (credential.password.isBlank()) return done(missingUser())
                EmailAuthProvider.getCredential(email, credential.password)
            }
            is AndroidReauthentication.Google -> GoogleAuthProvider.getCredential(credential.idToken, null)
        }
        user.reauthenticate(sdkCredential).addOnCompleteListener { task ->
            if (!task.isSuccessful) done(task.exception.toFailure())
            else if (auth.currentUser?.uid != subject) done(missingUser())
            else user.getIdToken(true).addOnCompleteListener { tokenTask ->
                when {
                    auth.currentUser?.uid != subject -> done(missingUser())
                    !tokenTask.isSuccessful -> done(tokenTask.exception.toFailure())
                    tokenTask.result?.token.isNullOrBlank() -> done(missingUser())
                    else -> done(AndroidProviderResult.Success(user.toProvider()))
                }
            }
        }
    }

}
