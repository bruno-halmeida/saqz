-- Seed de VOLUME para teste de carga: envelhece o grupo do seed-exploracao.sql em 2 anos.
--
--   ./seed-usuarios.sh <alvo>  ->  ./seed-exploracao.sh <alvo>  ->  este arquivo
--
-- O seed de exploração tem ~24 cobranças e nenhum jogo; com isso /games e /charges (que devolvem
-- o histórico inteiro) parecem rápidos. Aqui entram ~210 jogos (2 por semana), ~2.900 presenças,
-- ~290 mensalidades, ~1.650 cobranças de jogo, os eventos de quem pagou e ~210 despesas de quadra.
--
-- NUNCA rode contra ambiente com gente de verdade. Uma statement só (bloco DO), pelo mesmo motivo
-- do seed de exploração: atravessa pooler em modo transaction. Aplica uma vez só (ver abaixo);
-- os UUIDs são determinísticos (prefixo 10ad). Depois dele o seed-exploracao.sh não roda mais
-- neste banco, pelo mesmo motivo.
DO $volume$
DECLARE
    c_group  constant uuid := '9a000000-0000-4000-8000-000000000001';
    c_weeks  constant int  := 104;
    v_owner  uuid;
BEGIN
    SELECT owner_user_id INTO v_owner FROM access_groups WHERE id = c_group AND deleted_at IS NULL;
    IF v_owner IS NULL THEN
        RAISE EXCEPTION 'Grupo do seed não existe. Rode ./seed-exploracao.sh antes.';
    END IF;

    -- Eventos financeiros são append-only (trigger): não há como apagar e refazer. Aplica uma vez;
    -- para refazer, recrie o banco do ambiente de carga.
    IF EXISTS (SELECT 1 FROM games WHERE group_id = c_group AND id::text LIKE '10ad%') THEN
        RAISE NOTICE 'Volume já aplicado neste banco; nada a fazer.';
        RETURN;
    END IF;

    -- Jogos: terça e quinta, 20h. Os das 2 semanas à frente ficam PUBLISHED; o resto COMPLETED.
    INSERT INTO games (
        id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
        confirmation_deadline, venue_name, venue_address, capacity, game_fee_cents, status,
        created_at, updated_at
    )
    SELECT
        ('10ad0001-0000-4000-8000-' || lpad(((w + 2) * 2 + d)::text, 12, '0'))::uuid,
        c_group,
        CASE d WHEN 0 THEN 'Vôlei de Terça' ELSE 'Vôlei de Quinta' END,
        dia.data, time '20:00', 'America/Sao_Paulo',
        (dia.data + time '20:00') AT TIME ZONE 'America/Sao_Paulo', 120,
        (dia.data + time '18:00') AT TIME ZONE 'America/Sao_Paulo',
        'Quadra Coberta', 'Rua do Seed, 100 - São Paulo', 18, 2500,
        (CASE WHEN dia.data >= current_date THEN 'PUBLISHED' ELSE 'COMPLETED' END)::game_status,
        dia.data - 14, dia.data - 14
    FROM generate_series(-2, c_weeks - 1) AS w
    CROSS JOIN generate_series(0, 1) AS d
    CROSS JOIN LATERAL (
        SELECT (date_trunc('week', current_date)::date - w * 7 + CASE d WHEN 0 THEN 1 ELSE 3 END) AS data
    ) AS dia;

    -- Presença: 14 dos 19 atletas ativos confirmam cada jogo, em rodízio.
    INSERT INTO game_attendance (game_id, group_id, member_user_id, status, responded_at, updated_at, member_display_name)
    SELECT g.id, c_group, u.id, 'CONFIRMED', g.starts_at - interval '2 days', g.starts_at - interval '2 days',
           coalesce(u.nickname, u.display_name)
    FROM games g
    JOIN LATERAL (
        SELECT users.id, users.nickname, users.display_name,
               row_number() OVER (ORDER BY users.email) AS n
        FROM group_memberships m JOIN access_users users ON users.id = m.user_id
        WHERE m.group_id = c_group AND m.active AND m.role = 'ATHLETE'
    ) AS u ON (u.n + extract(doy FROM g.local_date)::int) % 19 < 14
    WHERE g.group_id = c_group AND g.id::text LIKE '10ad%';

    -- Mensalidades dos 12 mensalistas nos 22 meses ANTES dos 2 que o seed de exploração já cobre.
    INSERT INTO group_charges (
        id, group_id, member_user_id, member_display_name, kind, billing_month, amount_cents,
        due_date, status, paid_method, created_by_user_id, changed_by_user_id, created_at, updated_at
    )
    SELECT
        ('10ad0002-' || lpad(mes::text, 4, '0') || '-4000-8000-' || lpad(a.n::text, 12, '0'))::uuid,
        c_group, a.id, a.display_name, 'MONTHLY', ref.inicio, 8000, ref.inicio + 9,
        'PAID', (ARRAY['PIX', 'CASH'])[1 + (a.n % 2)::int]::charge_paid_method,
        v_owner, v_owner, ref.inicio, ref.inicio + 9
    FROM generate_series(2, 23) AS mes
    CROSS JOIN LATERAL (SELECT (date_trunc('month', current_date) - (mes || ' months')::interval)::date AS inicio) AS ref
    CROSS JOIN (
        SELECT users.id, users.display_name, row_number() OVER (ORDER BY users.email) AS n
        FROM group_memberships m JOIN access_users users ON users.id = m.user_id
        WHERE m.group_id = c_group AND m.membership_type = 'MENSALISTA' AND m.role = 'ATHLETE'
    ) AS a
    -- O seed de exploração já cria mensalidade dos meses recentes: onde os dois se encontram,
    -- a dele fica. O predicado tem que ser repetido porque o índice é parcial.
    ON CONFLICT (group_id, billing_month, member_user_id) WHERE kind = 'MONTHLY' DO NOTHING;

    -- Cobrança de jogo para cada avulso que confirmou jogo já realizado; os 30 dias recentes ficam pendentes.
    INSERT INTO group_charges (
        id, group_id, member_user_id, member_display_name, kind, game_id, amount_cents,
        due_date, status, paid_method, created_by_user_id, changed_by_user_id, created_at, updated_at
    )
    SELECT
        ('10ad0003-' || substr(md5(att.game_id::text || att.member_user_id::text), 1, 4) || '-4000-8000-'
            || substr(md5(att.member_user_id::text || att.game_id::text), 1, 12))::uuid,
        c_group, att.member_user_id, att.member_display_name, 'GAME', att.game_id, 2500, g.local_date,
        (CASE WHEN g.local_date < current_date - 30 THEN 'PAID' ELSE 'PENDING' END)::charge_status,
        (CASE WHEN g.local_date < current_date - 30 THEN 'PIX' END)::charge_paid_method,
        v_owner, v_owner, g.starts_at, g.starts_at
    FROM game_attendance att
    JOIN games g ON g.id = att.game_id AND g.status = 'COMPLETED'
    JOIN group_memberships m ON m.group_id = c_group AND m.user_id = att.member_user_id AND m.membership_type = 'AVULSO'
    WHERE att.group_id = c_group AND att.game_id::text LIKE '10ad%'
    ON CONFLICT (group_id, game_id, member_user_id) WHERE kind = 'GAME' DO NOTHING;

    -- Um evento PENDING -> PAID por cobrança paga: é o que a listagem carrega junto.
    INSERT INTO group_charge_events (id, charge_id, group_id, actor_user_id, old_status, new_status, occurred_at)
    SELECT ('10ad0004-' || substr(c.id::text, 10))::uuid, c.id, c_group, v_owner, 'PENDING', 'PAID', c.updated_at
    FROM group_charges c
    WHERE c.group_id = c_group AND c.id::text LIKE '10ad%' AND c.status = 'PAID';

    -- Quadra paga a cada jogo realizado.
    INSERT INTO group_expenses (
        id, group_id, description, amount_cents, expense_date, category, direction,
        created_by_user_id, changed_by_user_id, created_at, updated_at
    )
    SELECT ('10ad0005-' || substr(g.id::text, 10))::uuid, c_group, 'Quadra ' || to_char(g.local_date, 'DD/MM'),
           18000, g.local_date, 'VENUE', 'OUT', v_owner, v_owner, g.starts_at, g.starts_at
    FROM games g WHERE g.group_id = c_group AND g.id::text LIKE '10ad%' AND g.status = 'COMPLETED';

    RAISE NOTICE 'Volume aplicado: % jogos, % presenças, % cobranças, % eventos, % despesas.',
        (SELECT count(*) FROM games WHERE group_id = c_group),
        (SELECT count(*) FROM game_attendance WHERE group_id = c_group),
        (SELECT count(*) FROM group_charges WHERE group_id = c_group),
        (SELECT count(*) FROM group_charge_events WHERE group_id = c_group),
        (SELECT count(*) FROM group_expenses WHERE group_id = c_group);
END
$volume$;
