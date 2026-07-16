package br.com.saqz.bootstrap

import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class TestIdentityConfiguration {
    @Bean
    @Primary
    fun testVerifyRequestIdentity(): VerifyRequestIdentity = VerifyRequestIdentity {
        when (it.value) {
            "verified-token" -> TokenVerification.Verified(
                RequestIdentity("verified-subject", "verified@example.test", true),
            )
            else -> TokenVerification.Rejected
        }
    }
}
