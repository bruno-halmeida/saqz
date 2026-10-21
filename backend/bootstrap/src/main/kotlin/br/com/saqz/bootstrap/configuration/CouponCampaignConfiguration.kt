package br.com.saqz.bootstrap.configuration

import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.subscriptions.adapter.output.appstore.AppStoreConnectCredentials
import br.com.saqz.subscriptions.adapter.output.appstore.HttpAppStoreOffersApi
import br.com.saqz.subscriptions.adapter.output.googleplay.HttpGooglePlayOffersApi
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcCouponStoreOfferRepository
import br.com.saqz.subscriptions.application.AppStoreOffersApi
import br.com.saqz.subscriptions.application.CouponEligibilityLookup
import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.application.CouponStoreAttribution
import br.com.saqz.subscriptions.application.CouponStoreOfferRepository
import br.com.saqz.subscriptions.application.GooglePlayOffersApi
import br.com.saqz.subscriptions.application.PaidHistoryCouponEligibility
import br.com.saqz.subscriptions.application.PaidSubscriptionHistory
import br.com.saqz.subscriptions.application.PreviewCouponPrices
import br.com.saqz.subscriptions.application.SubscriptionPlanLookup
import br.com.saqz.subscriptions.application.SyncCouponStoreOffers
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcPaidSubscriptionHistory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import javax.sql.DataSource

/**
 * Cupom de campanha nas lojas: ofertas do Google Play e códigos de oferta da App Store criados pelo
 * job a partir do admin. `saqz.coupons.store-sync` (SAQZ_COUPON_STORE_SYNC) desligado = nenhuma
 * chamada às lojas, nem cliente montado.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class CouponCampaignConfiguration {
    @Bean
    fun couponStoreOfferRepository(dataSource: DataSource): CouponStoreOfferRepository = JdbcCouponStoreOfferRepository(dataSource)

    @Bean
    fun couponEligibilityLookup(dataSource: DataSource, plans: SubscriptionPlanLookup): CouponEligibilityLookup =
        PaidHistoryCouponEligibility(JdbcPaidSubscriptionHistory(dataSource) as PaidSubscriptionHistory, plans)

    @Bean
    fun couponStoreAttribution(offers: CouponStoreOfferRepository, coupons: CouponRepository) =
        CouponStoreAttribution(offers, coupons)

    @Bean
    fun couponStoreClients(
        @Value("\${saqz.coupons.store-sync:false}") enabled: Boolean,
        @Value("\${saqz.google-play.package-name}") packageName: String,
        @Value("\${saqz.app-store.app-apple-id:}") appAppleId: String,
        @Value("\${saqz.app-store-connect.issuer-id:}") issuerId: String,
        @Value("\${saqz.app-store-connect.key-id:}") keyId: String,
        @Value("\${saqz.app-store-connect.private-key:}") privateKey: String,
        clock: Clock,
    ) = CouponStoreClients(
        enabled = enabled,
        googlePlay = if (enabled) HttpGooglePlayOffersApi(packageName, GooglePlayAccessTokens()) else null,
        appStore = if (enabled && appAppleId.isNotBlank()) {
            HttpAppStoreOffersApi(appAppleId, AppStoreConnectCredentials(issuerId, keyId, privateKey), clock = clock)
        } else {
            null
        },
    )

    @Bean
    fun syncCouponStoreOffers(
        offers: CouponStoreOfferRepository,
        coupons: CouponRepository,
        clients: CouponStoreClients,
        clock: Clock,
    ) = SyncCouponStoreOffers(offers, coupons, clients.googlePlay, clients.appStore, clients.enabled, clock)

    /** Prévia de preços do admin; com a integração desligada, o preço da Apple sai estimado. */
    @Bean
    fun previewCouponPrices(clients: CouponStoreClients) = PreviewCouponPrices(clients.appStore)

    @Bean
    fun couponStoreOffersJob(sync: SyncCouponStoreOffers) = CouponStoreOffersJob(sync)
}

/** Clientes das lojas; nulos com a integração desligada (nada chama as lojas). */
class CouponStoreClients(
    val enabled: Boolean,
    val googlePlay: GooglePlayOffersApi?,
    val appStore: AppStoreOffersApi?,
)

class CouponStoreOffersJob(private val sync: SyncCouponStoreOffers) {
    @Scheduled(fixedDelayString = "\${saqz.coupons.store-sync-delay-ms:60000}", initialDelay = 30_000)
    fun run() {
        JobLog.frequent("coupon_store_offers", { sync.run() }) { mapOf("touched" to it) }
    }
}
