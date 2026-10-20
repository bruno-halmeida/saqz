-- Plano efetivo do dono (DEB-099), so para leitura do painel administrativo (Grupos e Usuarios).
-- ESPELHA JdbcSubscriptionPlanLookup.findEntitlingPlan (fontes que dao acesso agora: site/Asaas, App Store, Google Play e
-- cortesia) e OrganizerTrial (teste ativo = ORGANIZADOR, so quando nao ha nenhuma das fontes). Quem mudar a regra la muda aqui.
-- Maior plano vence (enum subscription_plan: TITULAR < ORGANIZADOR < ILIMITADO); empate: SITE, APP_STORE, GOOGLE_PLAY, COURTESY.
CREATE VIEW owner_effective_plans (owner_user_id, plan, source, ends_at) AS
WITH entitling AS (
    SELECT owner_user_id, plan, 'SITE'::text AS source, NULL::timestamptz AS ends_at, 1 AS priority
    FROM subscriptions
    WHERE status = 'ACTIVE'
       OR (
            status = 'PAST_DUE'
            AND first_confirmed_at IS NOT NULL
            AND (past_due_since IS NULL OR now() < (date_trunc('day', past_due_since AT TIME ZONE 'America/Sao_Paulo') + interval '8 days') AT TIME ZONE 'America/Sao_Paulo')
          )
       OR (status = 'CANCELED' AND first_confirmed_at IS NOT NULL AND current_period_end > now())
    UNION ALL
    SELECT owner_user_id, plan, 'APP_STORE', NULL::timestamptz, 2
    FROM app_store_subscriptions
    WHERE revoked_at IS NULL
      AND (expires_at > now() OR grace_period_expires_at > now())
    UNION ALL
    SELECT owner_user_id, plan, 'GOOGLE_PLAY', NULL::timestamptz, 3
    FROM google_play_subscriptions
    WHERE superseded_at IS NULL
      AND state IN ('ACTIVE', 'CANCELED', 'IN_GRACE_PERIOD')
      AND expires_at > now()
    UNION ALL
    SELECT owner_user_id, plan, 'COURTESY', ends_at, 4
    FROM plan_courtesies
    WHERE revoked_at IS NULL
      AND (ends_at IS NULL OR ends_at > now())
),
best AS (
    SELECT DISTINCT ON (owner_user_id) owner_user_id, plan, source, ends_at
    FROM entitling
    ORDER BY owner_user_id, plan DESC, priority
)
SELECT owner_user_id, plan, source, ends_at FROM best
UNION ALL
SELECT t.owner_user_id, 'ORGANIZADOR'::subscription_plan, 'TRIAL', t.ends_at
FROM organizer_trials t
WHERE t.ends_at > now()
  AND NOT EXISTS (SELECT 1 FROM best b WHERE b.owner_user_id = t.owner_user_id);
