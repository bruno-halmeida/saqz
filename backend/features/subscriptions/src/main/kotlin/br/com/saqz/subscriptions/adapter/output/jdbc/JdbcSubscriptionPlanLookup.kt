package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.EntitlingSubscription
import br.com.saqz.subscriptions.application.SubscriptionPlanLookup
import br.com.saqz.subscriptions.domain.Plan
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcSubscriptionPlanLookup(dataSource: DataSource) : SubscriptionPlanLookup {
    private val jdbc = JdbcClient.create(dataSource)

    override fun findPendingCheckoutPlan(ownerId: UUID): Plan? = jdbc.sql(
        """SELECT plan FROM subscriptions WHERE owner_user_id=:owner
            AND status='PAST_DUE' AND first_confirmed_at IS NULL""",
    ).param("owner", ownerId).query { result, _ -> Plan.valueOf(result.getString("plan")) }.optional().orElse(null)

    /**
     * Web (Asaas), App Store, Google Play ou cortesia da administração; com mais de uma, vale o plano maior (enum do Postgres ordena pela
     * declaração: TITULAR < ORGANIZADOR < ILIMITADO).
     */
    override fun findEntitlingPlan(ownerId: UUID): EntitlingSubscription? = jdbc.sql(
        """
        SELECT plan, pending_plan FROM (
            SELECT plan, pending_plan
            FROM subscriptions
            WHERE owner_user_id = :ownerId
              AND (
                status = 'ACTIVE'
                -- Espelho de Subscription.isEntitlingAt/pastDueFreezeAt: inadimplente perde o acesso na meia-noite
                -- que fecha o 7o dia depois do vencimento (23h59 de Sao Paulo). past_due_since nulo = linha legada, preservada.
                OR (
                  status = 'PAST_DUE'
                  AND first_confirmed_at IS NOT NULL
                  AND (past_due_since IS NULL OR now() < (date_trunc('day', past_due_since AT TIME ZONE 'America/Sao_Paulo') + interval '8 days') AT TIME ZONE 'America/Sao_Paulo')
                )
                OR (status = 'CANCELED' AND first_confirmed_at IS NOT NULL AND current_period_end > now())
              )
            UNION ALL
            -- Espelho de AppStoreSubscription.isEntitlingAt/pendingPlan.
            SELECT plan,
                   CASE WHEN auto_renew IS DISTINCT FROM false AND auto_renew_plan <> plan
                        THEN auto_renew_plan END AS pending_plan
            FROM app_store_subscriptions
            WHERE owner_user_id = :ownerId
              AND revoked_at IS NULL
              AND (expires_at > now() OR grace_period_expires_at > now())
            UNION ALL
            -- Espelho de GooglePlaySubscription.isEntitlingAt/pendingPlan.
            SELECT plan, pending_plan
            FROM google_play_subscriptions
            WHERE owner_user_id = :ownerId
              AND superseded_at IS NULL
              AND state IN ('ACTIVE', 'CANCELED', 'IN_GRACE_PERIOD')
              AND expires_at > now()
            UNION ALL
            -- Cortesia de plano (DEB-097), dada pela administracao; sem fim = sem limite de data. Espelho de PlanCourtesy.isActiveAt.
            SELECT plan, NULL::subscription_plan AS pending_plan
            FROM plan_courtesies
            WHERE owner_user_id = :ownerId
              AND revoked_at IS NULL
              AND (ends_at IS NULL OR ends_at > now())
        ) entitling
        ORDER BY plan DESC
        LIMIT 1
        """.trimIndent(),
    )
        .param("ownerId", ownerId)
        .query { result, _ ->
            EntitlingSubscription(
                plan = Plan.valueOf(result.getString("plan")),
                pendingPlan = result.getString("pending_plan")?.let(Plan::valueOf),
            )
        }
        .optional()
        .orElse(null)
}
