-- Comissao do parceiro (quadra) sobre o bruto pago pelos clientes que ele trouxe (customer_acquisition.partner_id),
-- em qualquer plataforma (subscription_payments, sem teste). Regra por parceiro; nula = sem comissao.
--   commission_percent  0,01 a 100, duas casas.
--   commission_duration FIRST_PAYMENT (so o primeiro pagamento do cliente), MONTHS (pagamentos dos primeiros
--                       commission_months meses, contados do primeiro pagamento) ou LIFETIME (enquanto pagar).
-- Mudar a regra so afeta meses ainda nao fechados: o extrato fechado guarda a regra usada.
ALTER TABLE partners
    ADD COLUMN commission_percent numeric(5, 2) CHECK (commission_percent IS NULL OR commission_percent BETWEEN 0.01 AND 100),
    ADD COLUMN commission_duration varchar(16) CHECK (commission_duration IN ('FIRST_PAYMENT', 'MONTHS', 'LIFETIME')),
    ADD COLUMN commission_months integer CHECK (commission_months IS NULL OR commission_months BETWEEN 1 AND 60),
    ADD CONSTRAINT ck_partners_commission_rule CHECK (
        (commission_percent IS NULL AND commission_duration IS NULL AND commission_months IS NULL)
        OR (commission_percent IS NOT NULL AND commission_duration IS NOT NULL
            AND (commission_duration = 'MONTHS') = (commission_months IS NOT NULL))
    );

-- Extrato mensal fechado (mes no fuso America/Sao_Paulo, gravado como o dia 1). Nao muda depois de gravado,
-- exceto a marcacao de pago (data e observacao; o dono paga por fora).
CREATE TABLE partner_commission_statements (
    id uuid PRIMARY KEY,
    partner_id uuid NOT NULL REFERENCES partners (id),
    month date NOT NULL CHECK (extract(day FROM month) = 1),
    commission_percent numeric(5, 2),
    commission_duration varchar(16),
    commission_months integer,
    total_cents bigint NOT NULL,
    closed_at timestamptz NOT NULL DEFAULT now(),
    paid_on date,
    paid_note varchar(500),
    CONSTRAINT uq_partner_commission_month UNIQUE (partner_id, month)
);

-- Linhas do extrato: PAYMENT (comissao de um pagamento) ou REFUND (desconto de um pagamento ja fechado e estornado
-- depois). O UNIQUE garante que um pagamento entra uma vez e e descontado no maximo uma vez.
CREATE TABLE partner_commission_lines (
    statement_id uuid NOT NULL REFERENCES partner_commission_statements (id),
    partner_id uuid NOT NULL REFERENCES partners (id),
    kind varchar(8) NOT NULL CHECK (kind IN ('PAYMENT', 'REFUND')),
    source varchar(16) NOT NULL,
    payment_ref varchar(200) NOT NULL,
    owner_user_id uuid NOT NULL,
    paid_at timestamptz NOT NULL,
    gross_cents bigint NOT NULL,
    commission_cents bigint NOT NULL,
    CONSTRAINT uq_partner_commission_line UNIQUE (partner_id, source, payment_ref, kind)
);
CREATE INDEX partner_commission_lines_statement ON partner_commission_lines (statement_id);
