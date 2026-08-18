package br.com.saqz.bootstrap.configuration

import br.com.saqz.subscriptions.application.CheckoutIdentitySessions
import br.com.saqz.subscriptions.application.CheckoutIdentityUnavailable
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * Custom token só no bootstrap: o feature de assinatura não conhece o Admin SDK.
 * O uid é o `firebase_subject` da conta dona do e-mail, nunca o UUID interno.
 */
class FirebaseCheckoutSessions(
    private val firebaseApp: FirebaseApp,
    dataSource: DataSource,
) : CheckoutIdentitySessions {
    private val jdbc = JdbcClient.create(dataSource)

    override fun customTokenFor(ownerUserId: UUID): String? {
        val subject = firebaseSubject(ownerUserId) ?: return null
        return try {
            FirebaseAuth.getInstance(firebaseApp).createCustomToken(subject)
        } catch (failure: Exception) {
            throw CheckoutIdentityUnavailable(failure)
        }
    }

    private fun firebaseSubject(ownerUserId: UUID): String? = jdbc.sql(
        """
        SELECT firebase_subject
        FROM access_users
        WHERE id = :ownerUserId
          AND deleted_at IS NULL
          AND suspended_at IS NULL
        """.trimIndent(),
    )
        .param("ownerUserId", ownerUserId)
        .query(String::class.java)
        .optional()
        .orElse(null)
}
