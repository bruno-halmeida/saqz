-- Seed de exploração geral: grupo, vínculos e financeiro para 1 dono e 20 atletas.
--
-- AS 21 CONTAS PRECISAM EXISTIR ANTES. Quem as cria é o `seed-usuarios.sh`, que passa pelo
-- Firebase (conta logável, com senha) e pelo bootstrap `PUT /api/session` (linha em
-- `access_users`). SQL não alcança o Firebase, então este arquivo só **procura** as
-- pessoas — por e-mail — e monta o mundo em volta delas.
--
--   ./seed-usuarios.sh <local|server>   primeiro
--   ./seed-exploracao.sh <local|server> depois
--
-- É SQL puro, sem meta-comando de psql: dá para colar inteiro no editor do Supabase, no
-- DBeaver ou em qualquer console. O `seed-exploracao.sh` é só conveniência de conexão.
--
-- NADA RODA ISTO SOZINHO. Não é executado ao subir o compose, e não teria como ser: o
-- `postgres` só executa o que está em /docker-entrypoint-initdb.d/ na inicialização do
-- volume, e nesse momento as tabelas ainda não existem — quem aplica as migrações é o
-- backend no startup, depois. Some-se a isso as contas dependerem do Firebase, e a ordem
-- obrigatória vira: subir o ambiente, rodar o seed-usuarios.sh, rodar isto.
--
-- Rodar de novo apaga o seed anterior e refaz: os UUIDs são fixos de propósito.

BEGIN;

-- O dono. É o `owner@saqz.local` que o seed-usuarios.sh cria; se você quiser pendurar o
-- cenário em outra conta, troque AQUI e em mais lugar nenhum.
CREATE TEMP TABLE seed_owner ON COMMIT DROP AS
SELECT id, display_name
FROM access_users
WHERE email = 'owner@saqz.local' AND deleted_at IS NULL;

-- Os 20 atletas, na ordem do e-mail: atleta01@saqz.local … atleta20@saqz.local.
-- A ordem importa — é ela que decide quem é mensalista e quem entra na cobrança.
CREATE TEMP TABLE seed_atletas ON COMMIT DROP AS
SELECT
    id,
    display_name,
    (regexp_replace(email, '^atleta0*(\d+)@saqz\.local$', '\1'))::int AS n
FROM access_users
WHERE email ~ '^atleta\d{2}@saqz\.local$' AND deleted_at IS NULL;

DO $$
DECLARE
    total int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seed_owner) THEN
        RAISE EXCEPTION
            'Nenhum usuário ativo com esse e-mail. Rode ./seed-usuarios.sh antes — só ele cria conta no Firebase e faz o bootstrap.';
    END IF;

    SELECT count(*) INTO total FROM seed_atletas;
    IF total <> 20 THEN
        RAISE EXCEPTION
            'Esperava 20 atletas (atleta01..20@saqz.local) e achei %. Rode ./seed-usuarios.sh contra ESTE backend: a conta existe no Firebase, mas a linha em access_users é por ambiente e só nasce no bootstrap.', total;
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
-- As pessoas NÃO são apagadas: elas têm conta no Firebase e são reaproveitadas entre
-- rodadas. Apagar a linha aqui deixaria a conta órfã, logando e caindo num bootstrap
-- que recria o usuário sem nada em volta.

-- ---------------------------------------------------------------------------
-- O perfil que o bootstrap não preenche: ele grava só e-mail e nome, vindos do token.
-- Telefone é celular BR válido (DDD + 9 + 8 dígitos), como o app exige.
-- ---------------------------------------------------------------------------

UPDATE access_users AS users
SET nickname = split_part(atletas.display_name, ' ', 1),
    phone    = '+55119' || lpad((70000000 + atletas.n)::text, 8, '0'),
    city     = 'São Paulo',
    updated_at = now()
FROM seed_atletas AS atletas
WHERE users.id = atletas.id;

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
    atletas.id,
    'ATHLETE',
    now() - interval '100 days',
    now(),
    (ARRAY['PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR', 'LIBERO'])[1 + (atletas.n % 5)],
    CASE WHEN atletas.n <= 12 THEN 'MENSALISTA' ELSE 'AVULSO' END,
    atletas.n <> 20,
    -- Atenção: o nível do VÍNCULO é em PT-BR, o do GRUPO é em inglês. Não é engano daqui.
    (ARRAY['INICIANTE', 'INTERMEDIARIO', 'AVANCADO'])[1 + (atletas.n % 3)],
    (ARRAY['DIREITA', 'ESQUERDA', 'TANTO_FAZ'])[1 + (atletas.n % 3)],
    165 + (atletas.n % 25),
    split_part(atletas.display_name, ' ', 1)
FROM seed_atletas AS atletas;

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
    ('c8a46e00-' || lpad(mes.offset_meses::text, 4, '0') || '-4000-8000-' || lpad(atletas.n::text, 12, '0'))::uuid,
    '9a000000-0000-4000-8000-000000000001',
    atletas.id,
    atletas.display_name,
    'MONTHLY',
    (date_trunc('month', current_date) - (mes.offset_meses || ' months')::interval)::date,
    8000,
    ((date_trunc('month', current_date) - (mes.offset_meses || ' months')::interval)::date + 9),
    status.valor,
    CASE WHEN status.valor = 'PAID' THEN (ARRAY['PIX', 'CASH'])[1 + (atletas.n % 2)] END,
    owner.id,
    owner.id,
    now() - interval '30 days',
    now()
FROM seed_atletas AS atletas
CROSS JOIN seed_owner AS owner
CROSS JOIN (VALUES (1), (0)) AS mes(offset_meses)
CROSS JOIN LATERAL (
    SELECT CASE
        WHEN mes.offset_meses = 1 AND atletas.n <= 10 THEN 'PAID'
        WHEN mes.offset_meses = 1                     THEN 'PENDING'
        WHEN atletas.n <= 6                           THEN 'PAID'
        WHEN atletas.n = 12                           THEN 'WAIVED'
        ELSE 'PENDING'
    END AS valor
) AS status
WHERE atletas.n <= 12;

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
-- Conferência. Num editor que só mostra o resultado da última consulta, rode estas duas
-- separado — elas não alteram nada.
-- ---------------------------------------------------------------------------

SELECT
    (SELECT count(*) FROM access_users WHERE email ~ '^atleta\d{2}@saqz\.local$' AND deleted_at IS NULL) AS atletas,
    (SELECT count(*) FROM group_memberships WHERE group_id = '9a000000-0000-4000-8000-000000000001')                   AS vinculos,
    (SELECT count(*) FROM group_charges WHERE group_id = '9a000000-0000-4000-8000-000000000001')                       AS cobrancas,
    (SELECT count(*) FROM group_expenses WHERE group_id = '9a000000-0000-4000-8000-000000000001')                      AS lancamentos;

SELECT billing_month, status, count(*), sum(amount_cents) / 100.0 AS reais
FROM group_charges
WHERE group_id = '9a000000-0000-4000-8000-000000000001'
GROUP BY billing_month, status
ORDER BY billing_month, status;
