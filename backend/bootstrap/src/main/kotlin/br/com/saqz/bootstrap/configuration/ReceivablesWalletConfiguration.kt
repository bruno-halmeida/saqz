package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.input.http.WalletController
import br.com.saqz.receivables.adapter.input.http.WalletCursorCodec
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasWallet
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcWalletStore
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.net.URI
import java.time.Clock
import java.util.Base64
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("saqz.receivables.encryption-key")
class ReceivablesWalletConfiguration {
    @Bean
    fun receivablesGroups(directory: GroupAdministrationDirectory) = object : ReceivablesGroups {
        override fun isOwner(groupId: java.util.UUID, userId: java.util.UUID) = directory.ownerOf(groupId) == userId
        override fun isCurrentAdministratorOfOwner(userId: java.util.UUID, ownerUserId: java.util.UUID) =
            directory.isAdministrator(ownerUserId, userId)
    }

    @Bean fun walletStore(dataSource: DataSource, secrets: FinancialSecrets) = JdbcWalletStore(dataSource, secrets)

    @Bean fun walletProvider(environment: Environment) = HttpAsaasWallet(URI(
        environment.getProperty("saqz.receivables.asaas-base-url", "https://api-sandbox.asaas.com/v3")))

    @Bean fun manageWallet(accounts: FinancialAccountRepository, groups: ReceivablesGroups,
        onboarding: FinancialOnboardingStore, store: JdbcWalletStore, provider: HttpAsaasWallet) =
        ManageWallet(accounts, groups, onboarding, store, provider)

    @Bean fun walletCursorCodec(environment: Environment) = WalletCursorCodec(Base64.getDecoder().decode(
        environment.getRequiredProperty("saqz.receivables.encryption-key")))

    @Bean fun walletController(actors: SubscriptionActorResolver, wallet: ManageWallet,
        cursors: WalletCursorCodec, clock: Clock) =
        WalletController(FinancialActorResolver { actors.resolve(it) }, wallet, cursors, clock)
}
