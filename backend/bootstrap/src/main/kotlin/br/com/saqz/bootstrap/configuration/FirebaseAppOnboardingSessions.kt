package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.session.AppOnboardingIdentitySessions
import br.com.saqz.access.application.session.AppOnboardingOwner
import br.com.saqz.access.application.session.AppOnboardingIdentityUnavailable
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class FirebaseAppOnboardingSessions(
    private val findUser: (String) -> Boolean?,
    private val mint: (String) -> String,
) : AppOnboardingIdentitySessions {
    constructor(firebaseApp: FirebaseApp) : this(
        findUser = firebaseUserLookup(firebaseApp),
        mint = { subject -> FirebaseAuth.getInstance(firebaseApp).createCustomToken(subject) },
    )

    override fun customTokenFor(owner: AppOnboardingOwner): String? = try {
        val disabled = findUser(owner.firebaseSubject) ?: return null
        if (disabled) return null
        mint(owner.firebaseSubject)
    } catch (_: AppOnboardingIdentityUnavailable) {
        throw AppOnboardingIdentityUnavailable()
    } catch (_: Exception) {
        throw AppOnboardingIdentityUnavailable()
    }

    private companion object {
        fun firebaseUserLookup(firebaseApp: FirebaseApp): (String) -> Boolean? {
            val auth = FirebaseAuth.getInstance(firebaseApp)
            return { subject ->
                try {
                    auth.getUser(subject).isDisabled
                } catch (failure: FirebaseAuthException) {
                    when (failure.authErrorCode) {
                        AuthErrorCode.USER_NOT_FOUND,
                        AuthErrorCode.USER_DISABLED,
                        -> null
                        else -> throw failure
                    }
                }
            }
        }
    }
}
