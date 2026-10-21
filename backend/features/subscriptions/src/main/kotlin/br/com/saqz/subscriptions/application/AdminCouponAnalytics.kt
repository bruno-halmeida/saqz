package br.com.saqz.subscriptions.application

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

fun interface AdminCouponAnalytics {
    fun report(): CouponAnalyticsReport
}

data class CouponAnalyticsReport(
    val asOf: Instant,
    val summary: CouponConversionMetrics,
    val coupons: List<CouponAnalyticsRow>,
)

data class CouponConversionMetrics(
    val users: Int,
    val payingUsers: Int,
    val conversionPercent: BigDecimal?,
    val payments: Int,
    val revenueCents: Long,
    val incompleteUsers: Int,
    /** Pagantes e receita por plataforma (`SITE`, `APP_STORE`, `GOOGLE_PLAY`); só as que têm pagamento com valor. */
    val platforms: List<CouponPlatformMetrics> = emptyList(),
)

data class CouponPlatformMetrics(
    val source: String,
    val payingUsers: Int,
    val payments: Int,
    val revenueCents: Long,
)

data class CouponAnalyticsRow(
    val id: UUID,
    val type: String,
    val code: String,
    val campaign: String?,
    val trialDays: Int?,
    val discountPercent: Int?,
    val status: String,
    val metrics: CouponConversionMetrics,
    val ongoingTrials: Int?,
    val endedTrials: Int?,
    val endedConversionPercent: BigDecimal?,
    /** Usos do cupom de desconto por plataforma (WEB, ANDROID, IOS). */
    val usesByPlatform: Map<String, Int> = emptyMap(),
)
