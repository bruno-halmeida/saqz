package br.com.saqz.bootstrap.configuration

import br.com.saqz.identity.adapter.output.firebase.FirebaseAdminTokenVerifier
import br.com.saqz.identity.application.DefaultVerifyRequestIdentity
import br.com.saqz.identity.application.IdentityTokenVerifier
import br.com.saqz.identity.application.VerifyRequestIdentity
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.util.Date
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class FirebaseConfiguration {
    @Bean(destroyMethod = "delete")
    fun firebaseApp(environment: Environment): FirebaseApp {
        val profiles = environment.activeProfiles.toSet()
        val emulatorProfile = profiles.isNotEmpty() && profiles.all { it == "local" || it == "test" }
        val environmentSwitch = !environment.getProperty("FIREBASE_AUTH_EMULATOR_HOST").isNullOrBlank()
        val propertySwitch = environment.getProperty("saqz.firebase.emulator.enabled", Boolean::class.java, false)

        if (!emulatorProfile && (environmentSwitch || propertySwitch)) {
            error("Firebase Auth Emulator is forbidden outside local and test profiles")
        }

        val options = if (emulatorProfile) {
            check(environmentSwitch || propertySwitch) {
                "Firebase Auth Emulator configuration is required for local and test profiles"
            }
            FirebaseOptions.builder()
                .setProjectId("saqz-local")
                .setCredentials(emulatorCredentials())
                .build()
        } else {
            val projectId = environment.getProperty("saqz.firebase.project-id")
                ?: error("Firebase project ID and application-default credentials are required")
            FirebaseOptions.builder()
                .setProjectId(projectId)
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
        }

        return FirebaseApp.initializeApp(options, "saqz-${UUID.randomUUID()}")
    }

    @Bean
    fun identityTokenVerifier(firebaseApp: FirebaseApp): IdentityTokenVerifier =
        FirebaseAdminTokenVerifier(firebaseApp)

    @Bean
    fun verifyRequestIdentity(identityTokenVerifier: IdentityTokenVerifier): VerifyRequestIdentity =
        DefaultVerifyRequestIdentity(identityTokenVerifier)

    private fun emulatorCredentials(): GoogleCredentials =
        GoogleCredentials.create(AccessToken("owner", Date(Long.MAX_VALUE)))
}
