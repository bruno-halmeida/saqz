ALTER TABLE group_charges ADD COLUMN electronic_order_id uuid UNIQUE;
CREATE TABLE group_charge_payment_effects (
    order_id uuid NOT NULL,
    effect varchar(16) NOT NULL CHECK (effect IN ('PAYMENT','REVERSAL')),
    charge_id uuid NOT NULL REFERENCES group_charges(id),
    created_at timestamptz NOT NULL,
    PRIMARY KEY(order_id,effect)
);
