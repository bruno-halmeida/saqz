package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.AdminCouponAnalytics
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Guarded by the platform admin filter, like the coupon management endpoints. */
@RestController
class AdminCouponAnalyticsController(private val analytics: AdminCouponAnalytics) {
    @GetMapping("/admin/coupon-analytics")
    fun report() = analytics.report()
}
