-- Seed de exploração geral: 1 dono, 20 atletas, 1 grupo e um financeiro com movimento.
--
-- O dono precisa EXISTIR ANTES: ele é o único que faz login, e login passa pelo Firebase,
-- que este script não alcança. Crie a conta pelo app (ou pelo Firebase Console) e passe o
-- e-mail dela aqui. Os 20 atletas são fabricados direto no banco, com `firebase_subject`
-- que não corresponde a conta nenhuma — eles aparecem no app, mas não entram.
--
-- NADA RODA ISTO SOZINHO. Não é executado ao subir o compose, e não teria como ser: o
-- `postgres` só executa o que está em /docker-entrypoint-initdb.d/ na inicialização do
-- volume, e nesse momento as tabelas ainda não existem — quem aplica as migrações é o
-- backend no startup, depois. Some-se a isso o dono precisar de conta no Firebase, e a
-- ordem obrigatória vira: subir o compose, criar a conta do dono pelo app, rodar isto.
--
-- Uso:
--   psql "$URL" -v owner_email=voce@exemplo.com -f seed-exploracao.sql
--   psql "$URL" -f seed-exploracao.sql            # usa o default abaixo
--
-- Rodar de novo apaga o seed anterior e refaz: os UUIDs são fixos de propósito.

\set ON_ERROR_STOP on
\if :{?owner_email}
\else
  \set owner_email 'owner@saqz.local'
\endif

BEGIN;

CREATE TEMP TABLE seed_owner ON COMMIT DROP AS
SELECT id, display_name
FROM access_users
WHERE email = :'owner_email' AND deleted_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seed_owner) THEN
        RAISE EXCEPTION
            'Nenhum usuário ativo com esse e-mail. Crie a conta do dono pelo app primeiro — o login depende do Firebase e este script não cria conta lá.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Limpeza do seed anterior. Ordem: eventos antes dos donos, filhos antes dos pais.
-- ---------------------------------------------------------------------------

DELETE FROM group_charge_events WHERE group_id = '9a000000-0000-4000-8000-000000000001';
DELETE FROM group_charges       WHERE group_id = '9a000000-0000-4000-8000-000000000001';
DELETE FROM group_expense_events WHERE group_id = '9a000000-0000-4000-8000-000000000001';
DELETE FROM group_expenses      WHERE group_id = '9a000000-0000-4000-8000-000000000001';
DELETE FROM group_memberships   WHERE group_id = '9a000000-0000-4000-8000-000000000001';
DELETE FROM access_groups       WHERE id       = '9a000000-0000-4000-8000-000000000001';
DELETE FROM access_users        WHERE firebase_subject LIKE 'seed-atleta-%';

-- ---------------------------------------------------------------------------
-- 20 atletas. Nome, apelido, telefone celular BR válido (DDD + 9 + 8 dígitos).
-- `email_verified` fica true: o seed não é o lugar de exercitar a faixa de aviso.
-- ---------------------------------------------------------------------------

INSERT INTO access_users (
    id, firebase_subject, email, email_verified, display_name, nickname, phone, city,
    created_at, updated_at
)
SELECT
    ('a71e7e00-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
    'seed-atleta-' || lpad(n::text, 2, '0'),
    'atleta' || lpad(n::text, 2, '0') || '@saqz.local',
    true,
    nome,
    split_part(nome, ' ', 1),
    '+55119' || lpad((70000000 + n)::text, 8, '0'),
    'São Paulo',
    now() - (n || ' days')::interval,
    now() - (n || ' days')::interval
FROM (
    SELECT n, nome
    FROM unnest(ARRAY[
        'Ana Ribeiro', 'Bruno Tavares', 'Carla Mendes', 'Diego Barbosa', 'Elisa Fontes',
        'Felipe Andrade', 'Gabriela Lima', 'Henrique Sales', 'Isabela Moura', 'João Peixoto',
        'Karina Duarte', 'Lucas Vasques', 'Mariana Cordeiro', 'Nuno Ferraz', 'Olívia Bastos',
        'Pedro Quintana', 'Renata Siqueira', 'Sérgio Antunes', 'Tatiana Reis', 'Vitor Camargo'
    ]) WITH ORDINALITY AS t(nome, n)
) AS atletas;

-- ---------------------------------------------------------------------------
-- O grupo. `modality` e `composition` preenchidos porque o CHECK exige os dois
-- para o perfil poder ser COMPLETE.
-- ---------------------------------------------------------------------------

INSERT INTO access_groups (
    id, owner_user_id, creation_key, name, time_zone, created_at, updated_at,
    profile_status, modality, composition, description, city, level, play_style,
    default_capacity, default_confirmation_lead_minutes,
    default_game_fee_cents, monthly_fee_cents, monthly_due_day,
    entry_requires_approval, mensalista_priority, promotion_mode, auto_confirm_enabled,
    pix_key, pix_label
)
SELECT
    '9a000000-0000-4000-8000-000000000001',
    owner.id,
    '9a000000-0000-4000-8000-0000000000ce',
    'Vôlei de Quinta',
    'America/Sao_Paulo',
    now() - interval '120 days',
    now(),
    'COMPLETE',
    'COURT_VOLLEYBALL',
    'MIXED',
    'Grupo de exploração criado por seed. Quinta-feira, 20h, quadra coberta.',
    'São Paulo',
    'INTERMEDIATE',
    'FIVE_ONE',
    18,
    120,
    2500,
    8000,
    10,
    false,
    true,
    'FIFO',
    false,
    'volei.quinta@saqz.local',
    'Vôlei de Quinta'
FROM seed_owner AS owner;

-- ---------------------------------------------------------------------------
-- Vínculos: o dono como ADMIN, os 20 como ATHLETE.
-- Os 12 primeiros são MENSALISTA (são eles que geram cobrança mensal), o resto AVULSO.
-- Um está inativo, para a lista ter o caso.
-- ---------------------------------------------------------------------------

INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at, membership_type, active)
SELECT '9a000000-0000-4000-8000-000000000001', owner.id, 'ADMIN', now() - interval '120 days', now(), 'MENSALISTA', true
FROM seed_owner AS owner;

INSERT INTO group_memberships (
    group_id, user_id, role, created_at, updated_at,
    position, membership_type, active, level, preferred_side, height_cm, nickname
)
SELECT
    '9a000000-0000-4000-8000-000000000001',
    users.id,
    'ATHLETE',
    now() - interval '100 days',
    now(),
    (ARRAY['PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR', 'LIBERO'])[1 + (n % 5)],
    CASE WHEN n <= 12 THEN 'MENSALISTA' ELSE 'AVULSO' END,
    n <> 20,
    -- Atenção: o nível do VÍNCULO é em PT-BR, o do GRUPO é em inglês. Não é engano daqui.
    (ARRAY['INICIANTE', 'INTERMEDIARIO', 'AVANCADO'])[1 + (n % 3)],
    (ARRAY['DIREITA', 'ESQUERDA', 'TANTO_FAZ'])[1 + (n % 3)],
    165 + (n % 25),
    users.nickname
FROM access_users AS users
CROSS JOIN LATERAL (
    SELECT (right(users.firebase_subject, 2))::int AS n
) AS idx
WHERE users.firebase_subject LIKE 'seed-atleta-%';

-- ---------------------------------------------------------------------------
-- Financeiro. Mensalidade de R$ 80,00 com vencimento no dia 10, para os 12 mensalistas,
-- em dois meses: o passado quase todo quitado (com dois inadimplentes) e o corrente em
-- movimento. `paid_method` só existe em cobrança PAID — é o que o CHECK exige.
-- ---------------------------------------------------------------------------

INSERT INTO group_charges (
    id, group_id, member_user_id, member_display_name, kind, billing_month,
    amount_cents, due_date, status, paid_method,
    created_by_user_id, changed_by_user_id, created_at, updated_at
)
SELECT
    ('c8a46e00-' || lpad(mes.offset_meses::text, 4, '0') || '-4000-8000-' || lpad(idx.n::text, 12, '0'))::uuid,
    '9a000000-0000-4000-8000-000000000001',
    users.id,
    users.display_name,
    'MONTHLY',
    (date_trunc('month', current_date) - (mes.offset_meses || ' months')::interval)::date,
    8000,
    ((date_trunc('month', current_date) - (mes.offset_meses || ' months')::interval)::date + 9),
    status.valor,
    CASE WHEN status.valor = 'PAID' THEN (ARRAY['PIX', 'CASH'])[1 + (idx.n % 2)] END,
    owner.id,
    owner.id,
    now() - interval '30 days',
    now()
FROM access_users AS users
CROSS JOIN seed_owner AS owner
CROSS JOIN LATERAL (SELECT (right(users.firebase_subject, 2))::int AS n) AS idx
CROSS JOIN (VALUES (1), (0)) AS mes(offset_meses)
CROSS JOIN LATERAL (
    SELECT CASE
        WHEN mes.offset_meses = 1 AND idx.n <= 10 THEN 'PAID'
        WHEN mes.offset_meses = 1                 THEN 'PENDING'
        WHEN idx.n <= 6                            THEN 'PAID'
        WHEN idx.n = 12                            THEN 'WAIVED'
        ELSE 'PENDING'
    END AS valor
) AS status
WHERE users.firebase_subject LIKE 'seed-atleta-%'
  AND idx.n <= 12;

-- ---------------------------------------------------------------------------
-- Despesas e uma entrada. `OTHER` exige `custom_category`; as demais exigem que ela seja nula.
-- ---------------------------------------------------------------------------

INSERT INTO group_expenses (
    id, group_id, description, amount_cents, expense_date, category, custom_category,
    notes, direction, created_by_user_id, changed_by_user_id, created_at, updated_at
)
SELECT
    despesa.id::uuid,
    '9a000000-0000-4000-8000-000000000001',
    despesa.descricao,
    despesa.valor,
    current_date - despesa.dias,
    despesa.categoria,
    despesa.custom,
    despesa.nota,
    despesa.direcao,
    owner.id,
    owner.id,
    now() - (despesa.dias || ' days')::interval,
    now() - (despesa.dias || ' days')::interval
FROM seed_owner AS owner
CROSS JOIN (VALUES
    ('de5be5a0-0000-4000-8000-000000000001', 'Aluguel da quadra — mês corrente', 40000, 12, 'VENUE',     NULL,                'Quadra coberta, 4 quintas.', 'OUT'),
    ('de5be5a0-0000-4000-8000-000000000002', 'Bolas novas (3 unidades)',          25000, 40, 'EQUIPMENT', NULL,                NULL,                         'OUT'),
    ('de5be5a0-0000-4000-8000-000000000003', 'Arbitragem do amistoso',            12000,  8, 'REFEREE',   NULL,                NULL,                         'OUT'),
    ('de5be5a0-0000-4000-8000-000000000004', 'Confraternização do grupo',         30000, 25, 'OTHER',     'Confraternização',  'Rateado à parte.',           'OUT'),
    ('de5be5a0-0000-4000-8000-000000000005', 'Venda de camisetas do grupo',       18000, 18, 'OTHER',     'Camisetas',         'Entrada de caixa.',          'IN')
) AS despesa(id, descricao, valor, dias, categoria, custom, nota, direcao);

COMMIT;

-- ---------------------------------------------------------------------------
-- Conferência
-- ---------------------------------------------------------------------------

\echo ''
\echo '=== seed aplicado ==='
SELECT
    (SELECT count(*) FROM access_users WHERE firebase_subject LIKE 'seed-atleta-%')                                    AS atletas,
    (SELECT count(*) FROM group_memberships WHERE group_id = '9a000000-0000-4000-8000-000000000001')                   AS vinculos,
    (SELECT count(*) FROM group_charges WHERE group_id = '9a000000-0000-4000-8000-000000000001')                       AS cobrancas,
    (SELECT count(*) FROM group_expenses WHERE group_id = '9a000000-0000-4000-8000-000000000001')                      AS lancamentos;

SELECT billing_month, status, count(*), sum(amount_cents) / 100.0 AS reais
FROM group_charges
WHERE group_id = '9a000000-0000-4000-8000-000000000001'
GROUP BY billing_month, status
ORDER BY billing_month, status;
