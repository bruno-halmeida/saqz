-- Código de pagamento do atleta (FIN-011, DEB-140). Um código cobre cobranças PENDING de um atleta num grupo, com o valor
-- somado de payable_cents. Pix: QR estático da subconta com valor (allowsMultiplePayments=false). Cartão: cobrança
-- CREDIT_CARD com a divisão da comissão. externalReference = id do código nos dois.

-- Chave aleatória (EVP) da subconta, garantida na ativação. Nunca sai na API: só serve para gerar QR com valor.
ALTER TABLE receivable_accounts
    ADD COLUMN pix_key_id varchar(128),
    ADD COLUMN pix_key varchar(128),
    ADD COLUMN pix_key_ready boolean NOT NULL DEFAULT false;

-- CPF do atleta, pedido uma vez para o cartão e válido em todos os grupos. Cifrado por usuário; o digest é o de setembro.
CREATE TABLE receivable_payer_documents (
    user_id uuid PRIMARY KEY,
    document_encrypted text NOT NULL,
    document_digest varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE payment_codes (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    group_id uuid NOT NULL,
    payer_user_id uuid NOT NULL,
    method varchar(8) NOT NULL CHECK (method IN ('PIX', 'CARD')),
    amount_cents bigint NOT NULL CHECK (amount_cents > 0),
    commission_cents bigint NOT NULL CHECK (commission_cents >= 0),
    fee_schedule_id uuid,
    charges_key text NOT NULL,
    status varchar(12) NOT NULL CHECK (status IN ('CREATING', 'PENDING', 'PAID', 'EXPIRED', 'CANCELLED')),
    provider_qr_id varchar(128),
    provider_payment_id varchar(128),
    payload_encrypted text,
    created_at timestamptz NOT NULL,
    paid_at timestamptz,
    UNIQUE (account_id, provider_qr_id),
    UNIQUE (account_id, provider_payment_id)
);
CREATE INDEX payment_codes_live ON payment_codes(group_id, payer_user_id) WHERE status IN ('CREATING', 'PENDING');

CREATE TABLE payment_code_charges (
    code_id uuid NOT NULL REFERENCES payment_codes(id),
    charge_id uuid NOT NULL,
    PRIMARY KEY (code_id, charge_id)
);
CREATE INDEX payment_code_charges_charge ON payment_code_charges(charge_id);

-- Cada requestId do app aponta para um código; reaproveitar o código PENDING não gasta outro.
CREATE TABLE payment_code_requests (
    payer_user_id uuid NOT NULL,
    request_id uuid NOT NULL,
    request_digest varchar(64) NOT NULL,
    code_id uuid NOT NULL REFERENCES payment_codes(id),
    PRIMARY KEY (payer_user_id, request_id)
);
