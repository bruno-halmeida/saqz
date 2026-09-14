package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.*
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.sql.DataSource

class JdbcTrialCampaignStore(dataSource: DataSource, private val clock: Clock) : TrialCampaignStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val projection = """SELECT c.*, (SELECT count(*) FROM organizer_trials t WHERE t.coupon_id=c.id) AS uses FROM trial_coupons c"""

    override fun mode() = readMode(false)
    private fun readMode(lock: Boolean) = TrialOfferMode.valueOf(jdbc.sql(
        "SELECT mode FROM trial_offer_settings WHERE id=true" + if (lock) " FOR SHARE" else "",
    ).query(String::class.java).single())

    override fun setMode(mode: TrialOfferMode) {
        jdbc.sql("UPDATE trial_offer_settings SET mode=:mode, updated_at=:now WHERE id=true")
            .param("mode", mode.name).param("now", Timestamp.from(clock.instant())).update()
    }

    override fun list(): List<TrialCoupon> = jdbc.sql("$projection ORDER BY c.created_at DESC,c.id")
        .query { rs, _ -> rs.coupon() }.list()

    override fun create(code: String, campaign: String?, trialDays: Int, validUntil: Instant?, maxUses: Int?): TrialCoupon? {
        val normalized = code.trim().uppercase(Locale.ROOT)
        require(normalized.matches(Regex("[A-Z0-9]{1,32}")))
        require(trialDays in 1..365 && (maxUses == null || maxUses > 0))
        require(campaign == null || campaign.length <= 120)
        val id = UUID.randomUUID()
        val inserted = jdbc.sql("""INSERT INTO trial_coupons (id,code,campaign,trial_days,valid_until,max_uses,created_at)
            VALUES (:id,:code,:campaign,:days,:until,:max,:now) ON CONFLICT(code) DO NOTHING""")
            .param("id", id).param("code", normalized).param("campaign", campaign).param("days", trialDays)
            .param("until", validUntil?.let(Timestamp::from)).param("max", maxUses)
            .param("now", Timestamp.from(clock.instant())).update()
        return if (inserted == 1) TrialCoupon(id, normalized, campaign, trialDays, validUntil, maxUses, true, 0) else null
    }

    override fun deactivate(id: UUID) = jdbc.sql("UPDATE trial_coupons SET active=false WHERE id=:id")
        .param("id", id).update() == 1

    override fun selected(ownerId: UUID): TrialCoupon? = selected(ownerId, false)?.takeIf { it.isAvailable(clock.instant()) }
    private fun selected(ownerId: UUID, lock: Boolean): TrialCoupon? {
        val id = jdbc.sql("SELECT coupon_id FROM trial_coupon_selections WHERE owner_user_id=:owner")
            .param("owner", ownerId).query(UUID::class.java).optional().orElse(null) ?: return null
        if (lock) lockCoupon(id)
        // Separate statement after acquiring the lock: sees the preceding redeemer's committed use.
        return jdbc.sql("$projection WHERE c.id=:id").param("id", id).query { rs, _ -> rs.coupon() }.optional().orElse(null)
    }

    override fun isAvailable(ownerId: UUID) = when (mode()) {
        TrialOfferMode.ON -> true
        TrialOfferMode.OFF -> false
        TrialOfferMode.COUPON_ONLY -> selected(ownerId) != null
    }

    override fun grant(ownerId: UUID, start: (Int) -> Unit): Boolean {
        val mode = readMode(true)
        if (mode == TrialOfferMode.OFF) return false
        val coupon = selected(ownerId, true)?.takeIf { it.isAvailable(clock.instant()) }
        if (mode == TrialOfferMode.COUPON_ONLY && coupon == null) return false
        start(coupon?.trialDays ?: 14)
        if (coupon != null) {
            jdbc.sql("""UPDATE organizer_trials SET coupon_id=:coupon,coupon_code=:code,campaign=:campaign
                WHERE owner_user_id=:owner AND coupon_id IS NULL""")
                .param("coupon", coupon.id).param("code", coupon.code).param("campaign", coupon.campaign)
                .param("owner", ownerId).update()
        }
        return true
    }

    override fun select(ownerId: UUID, code: String, eligible: () -> Boolean): TrialCouponSelection =
        requireNotNull(transaction.execute {
            jdbc.sql("SELECT id FROM access_users WHERE id=:owner FOR UPDATE").param("owner", ownerId).query(UUID::class.java).single()
            if (readMode(true) == TrialOfferMode.OFF || !eligible()) return@execute TrialCouponSelection.INELIGIBLE
            val id = jdbc.sql("SELECT id FROM trial_coupons WHERE code=:code")
                .param("code", code.trim().uppercase(Locale.ROOT)).query(UUID::class.java).optional().orElse(null)
                ?: return@execute TrialCouponSelection.UNAVAILABLE
            lockCoupon(id)
            val coupon = jdbc.sql("$projection WHERE c.id=:id").param("id", id).query { rs, _ -> rs.coupon() }.single()
            if (!coupon.isAvailable(clock.instant())) return@execute TrialCouponSelection.UNAVAILABLE
            jdbc.sql("""INSERT INTO trial_coupon_selections(owner_user_id,coupon_id,selected_at) VALUES (:owner,:coupon,:now)
                ON CONFLICT(owner_user_id) DO UPDATE SET coupon_id=EXCLUDED.coupon_id,selected_at=EXCLUDED.selected_at""")
                .param("owner", ownerId).param("coupon", id).param("now", Timestamp.from(clock.instant())).update()
            TrialCouponSelection.APPLIED
        })

    private fun lockCoupon(id: UUID) {
        jdbc.sql("SELECT id FROM trial_coupons WHERE id=:id FOR UPDATE").param("id", id).query(UUID::class.java).single()
    }

    private fun ResultSet.coupon() = TrialCoupon(
        getObject("id", UUID::class.java), getString("code"), getString("campaign"), getInt("trial_days"),
        getTimestamp("valid_until")?.toInstant(), getObject("max_uses")?.let { (it as Number).toInt() }, getBoolean("active"), getLong("uses"),
    )
}
