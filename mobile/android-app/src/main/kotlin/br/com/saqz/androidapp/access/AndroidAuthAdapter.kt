package br.com.saqz.androidapp.access

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class AndroidProviderUser(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
)

internal enum class AndroidProviderFailure {
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    AUTH_METHOD_CONFLICT,
    NETWORK,
    UNAVAILABLE,
    UNKNOWN,
}

internal enum class FirebaseFailureFamily {
    INVALID_CREDENTIALS,
    INVALID_USER,
    USER_COLLISION,
    WEAK_PASSWORD,
    NETWORK,
    OTHER,
}

internal sealed interface AndroidProviderResult<out T> {
    data class Success<T>(val value: T) : AndroidProviderResult<T>
    data object Cancelled : AndroidProviderResult<Nothing>
    data class Failure(val code: AndroidProviderFailure) : AndroidProviderResult<Nothing>
}

internal interface AndroidFirebaseAuthClient {
    fun observe(listener: (AndroidProviderUser?) -> Unit): Cancelable
    fun createAccount(name: String, email: String, password: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit)
    fun signInWithPassword(email: String, password: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit)
    fun signInWithGoogle(idToken: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit)
    fun sendVerification(done: (AndroidProviderResult<Unit>) -> Unit)
    fun reloadUser(done: (AndroidProviderResult<AndroidProviderUser>) -> Unit)
    fun sendPasswordReset(email: String, done: (AndroidProviderResult<Unit>) -> Unit)
    fun updateDisplayName(name: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit)
    fun idToken(forceRefresh: Boolean, done: (AndroidProviderResult<String>) -> Unit)
    fun signOut(done: (AndroidProviderResult<Unit>) -> Unit)
}

internal interface AndroidGoogleCredentialClient {
    fun requestIdToken(done: (AndroidProviderResult<String>) -> Unit)
    fun clearCredentialState(done: (AndroidProviderResult<Unit>) -> Unit)
}

internal class AndroidAuthAdapter(
    private val firebase: AndroidFirebaseAuthClient,
    private val google: AndroidGoogleCredentialClient,
) : NativeAuthPort {
    override fun observe(listener: AuthStateListener): Cancelable = firebase.observe { user ->
        listener.onStateChanged(user?.let { AuthState.SignedIn(it.toNative()) } ?: AuthState.SignedOut)
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) =
        firebase.createAccount(name, email, password) { done.complete(it.toAuthResult()) }

    override fun signInWithPassword(email: String, password: String, done: AuthCallback) =
        firebase.signInWithPassword(email, password) { done.complete(it.toAuthResult()) }

    override fun signInWithGoogle(done: AuthCallback) {
        google.requestIdToken { credential ->
            when (credential) {
                is AndroidProviderResult.Success -> firebase.signInWithGoogle(credential.value) {
                    done.complete(it.toAuthResult())
                }
                AndroidProviderResult.Cancelled -> done.complete(AuthResult.Cancelled)
                is AndroidProviderResult.Failure -> done.complete(AuthResult.Failure(credential.code.toNative()))
            }
        }
    }

    override fun sendVerification(done: ResultCallback) =
        firebase.sendVerification { done.complete(it.toOperationResult()) }

    override fun reloadUser(done: AuthCallback) =
        firebase.reloadUser { done.complete(it.toAuthResult()) }

    override fun sendPasswordReset(email: String, done: ResultCallback) =
        firebase.sendPasswordReset(email) { done.complete(it.toOperationResult()) }

    override fun updateDisplayName(name: String, done: AuthCallback) =
        firebase.updateDisplayName(name) { done.complete(it.toAuthResult()) }

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
        firebase.idToken(forceRefresh) { result ->
            done.complete(
                when (result) {
                    is AndroidProviderResult.Success -> TokenResult.Success(result.value)
                    AndroidProviderResult.Cancelled -> TokenResult.Failure(NativeFailureCode.UNKNOWN)
                    is AndroidProviderResult.Failure -> TokenResult.Failure(result.code.toNative())
                },
            )
        }

    override fun signOut(done: ResultCallback) {
        firebase.signOut { firebaseResult ->
            when (firebaseResult) {
                is AndroidProviderResult.Success -> google.clearCredentialState {
                    done.complete(it.toOperationResult())
                }
                AndroidProviderResult.Cancelled -> done.complete(OperationResult.Failure(NativeFailureCode.UNKNOWN))
                is AndroidProviderResult.Failure -> done.complete(OperationResult.Failure(firebaseResult.code.toNative()))
            }
        }
    }
}

internal class FirebaseSdkAuthClient(
    private val auth: FirebaseAuth,
) : AndroidFirebaseAuthClient {
    override fun observe(listener: (AndroidProviderUser?) -> Unit): Cancelable {
        val sdkListener = FirebaseAuth.AuthStateListener { listener(it.currentUser?.toProvider()) }
        auth.addAuthStateListener(sdkListener)
        return object : Cancelable {
            override fun cancel() = auth.removeAuthStateListener(sdkListener)
        }
    }

    override fun createAccount(
        name: String,
        email: String,
        password: String,
        done: (AndroidProviderResult<AndroidProviderUser>) -> Unit,
    ) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            val user = task.result?.user
            if (!task.isSuccessful || user == null) {
                done(task.exception.toFailure())
                return@addOnCompleteListener
            }
            val profile = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            user.updateProfile(profile).addOnCompleteListener { profileTask ->
                if (profileTask.isSuccessful) done(AndroidProviderResult.Success(user.toProvider()))
                else done(profileTask.exception.toFailure())
            }
        }
    }

    override fun signInWithPassword(
        email: String,
        password: String,
        done: (AndroidProviderResult<AndroidProviderUser>) -> Unit,
    ) {
        auth.signInWithEmailAndPassword(email, password).completeWithUser(done)
    }

    override fun signInWithGoogle(
        idToken: String,
        done: (AndroidProviderResult<AndroidProviderUser>) -> Unit,
    ) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).completeWithUser(done)
    }

    override fun sendVerification(done: (AndroidProviderResult<Unit>) -> Unit) {
        val user = auth.currentUser ?: return done(missingUser())
        user.sendEmailVerification().completeWithUnit(done)
    }

    override fun reloadUser(done: (AndroidProviderResult<AndroidProviderUser>) -> Unit) {
        val user = auth.currentUser ?: return done(missingUser())
        user.reload().addOnCompleteListener { task ->
            if (!task.isSuccessful) done(task.exception.toFailure())
            else {
                val refreshed = auth.currentUser
                if (refreshed == null) done(missingUser())
                else done(AndroidProviderResult.Success(refreshed.toProvider()))
            }
        }
    }

    override fun sendPasswordReset(email: String, done: (AndroidProviderResult<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email).completeWithUnit(done)
    }

    override fun updateDisplayName(name: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit) {
        val user = auth.currentUser ?: return done(missingUser())
        val profile = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        user.updateProfile(profile).addOnCompleteListener { task ->
            if (task.isSuccessful) done(AndroidProviderResult.Success(user.toProvider()))
            else done(task.exception.toFailure())
        }
    }

    override fun idToken(forceRefresh: Boolean, done: (AndroidProviderResult<String>) -> Unit) {
        val user = auth.currentUser ?: return done(missingUser())
        user.getIdToken(forceRefresh).addOnCompleteListener { task ->
            val token = task.result?.token
            if (task.isSuccessful && token != null) done(AndroidProviderResult.Success(token))
            else done(task.exception.toFailure())
        }
    }

    override fun signOut(done: (AndroidProviderResult<Unit>) -> Unit) {
        runCatching { auth.signOut() }
            .fold(
                onSuccess = { done(AndroidProviderResult.Success(Unit)) },
                onFailure = { done(it.toFailure()) },
            )
    }

    private fun com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult>.completeWithUser(
        done: (AndroidProviderResult<AndroidProviderUser>) -> Unit,
    ) = addOnCompleteListener { task ->
        val user = task.result?.user
        if (task.isSuccessful && user != null) done(AndroidProviderResult.Success(user.toProvider()))
        else done(task.exception.toFailure())
    }

    private fun com.google.android.gms.tasks.Task<Void>.completeWithUnit(
        done: (AndroidProviderResult<Unit>) -> Unit,
    ) = addOnCompleteListener { task ->
        if (task.isSuccessful) done(AndroidProviderResult.Success(Unit))
        else done(task.exception.toFailure())
    }
}

internal class CredentialManagerGoogleClient(
    context: Context,
    private val serverClientId: String,
    private val scope: CoroutineScope,
    private val credentialManager: CredentialManager = CredentialManager.create(context),
    private val activityContext: Context = context,
) : AndroidGoogleCredentialClient {
    override fun requestIdToken(done: (AndroidProviderResult<String>) -> Unit) {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        scope.launch {
            try {
                val credential = credentialManager.getCredential(activityContext, request).credential
                val token = if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else {
                    null
                }
                done(token?.let { AndroidProviderResult.Success(it) } ?: unavailable())
            } catch (_: GetCredentialCancellationException) {
                done(AndroidProviderResult.Cancelled)
            } catch (_: NoCredentialException) {
                done(unavailable())
            } catch (_: GetCredentialException) {
                done(unavailable())
            } catch (_: RuntimeException) {
                done(AndroidProviderResult.Failure(AndroidProviderFailure.UNKNOWN))
            }
        }
    }

    override fun clearCredentialState(done: (AndroidProviderResult<Unit>) -> Unit) {
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                done(AndroidProviderResult.Success(Unit))
            } catch (_: Exception) {
                done(unavailable())
            }
        }
    }
}

internal fun mapFirebaseFailure(error: Throwable?): AndroidProviderFailure = mapFirebaseFailure(
    family = when (error) {
        is FirebaseAuthWeakPasswordException -> FirebaseFailureFamily.WEAK_PASSWORD
        is FirebaseAuthUserCollisionException -> FirebaseFailureFamily.USER_COLLISION
        is FirebaseAuthInvalidCredentialsException -> FirebaseFailureFamily.INVALID_CREDENTIALS
        is FirebaseAuthInvalidUserException -> FirebaseFailureFamily.INVALID_USER
        is FirebaseNetworkException -> FirebaseFailureFamily.NETWORK
        else -> FirebaseFailureFamily.OTHER
    },
    errorCode = (error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode,
)

internal fun mapFirebaseFailure(
    family: FirebaseFailureFamily,
    errorCode: String? = null,
): AndroidProviderFailure = when (family) {
    FirebaseFailureFamily.WEAK_PASSWORD -> AndroidProviderFailure.WEAK_PASSWORD
    FirebaseFailureFamily.USER_COLLISION -> when (errorCode) {
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AndroidProviderFailure.AUTH_METHOD_CONFLICT
        else -> AndroidProviderFailure.EMAIL_IN_USE
    }
    FirebaseFailureFamily.INVALID_CREDENTIALS,
    FirebaseFailureFamily.INVALID_USER,
    -> AndroidProviderFailure.INVALID_CREDENTIALS
    FirebaseFailureFamily.NETWORK -> AndroidProviderFailure.NETWORK
    FirebaseFailureFamily.OTHER -> AndroidProviderFailure.UNKNOWN
}

private fun Throwable?.toFailure() = AndroidProviderResult.Failure(mapFirebaseFailure(this))
private fun missingUser() = AndroidProviderResult.Failure(AndroidProviderFailure.INVALID_CREDENTIALS)
private fun unavailable() = AndroidProviderResult.Failure(AndroidProviderFailure.UNAVAILABLE)

private fun FirebaseUser.toProvider() = AndroidProviderUser(uid, email, isEmailVerified, displayName)
private fun AndroidProviderUser.toNative() = NativeUser(subject, email, emailVerified, displayName)

private fun AndroidProviderFailure.toNative() = when (this) {
    AndroidProviderFailure.INVALID_CREDENTIALS -> NativeFailureCode.INVALID_CREDENTIALS
    AndroidProviderFailure.EMAIL_IN_USE -> NativeFailureCode.EMAIL_IN_USE
    AndroidProviderFailure.WEAK_PASSWORD -> NativeFailureCode.WEAK_PASSWORD
    AndroidProviderFailure.AUTH_METHOD_CONFLICT -> NativeFailureCode.AUTH_METHOD_CONFLICT
    AndroidProviderFailure.NETWORK -> NativeFailureCode.NETWORK_UNAVAILABLE
    AndroidProviderFailure.UNAVAILABLE -> NativeFailureCode.PROVIDER_UNAVAILABLE
    AndroidProviderFailure.UNKNOWN -> NativeFailureCode.UNKNOWN
}

private fun AndroidProviderResult<AndroidProviderUser>.toAuthResult() = when (this) {
    is AndroidProviderResult.Success -> AuthResult.Success(value.toNative())
    AndroidProviderResult.Cancelled -> AuthResult.Cancelled
    is AndroidProviderResult.Failure -> AuthResult.Failure(code.toNative())
}

private fun AndroidProviderResult<Unit>.toOperationResult() = when (this) {
    is AndroidProviderResult.Success -> OperationResult.Success
    AndroidProviderResult.Cancelled -> OperationResult.Failure(NativeFailureCode.UNKNOWN)
    is AndroidProviderResult.Failure -> OperationResult.Failure(code.toNative())
}
