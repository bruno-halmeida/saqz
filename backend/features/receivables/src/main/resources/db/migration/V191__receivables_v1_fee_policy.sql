-- Absorver ou repassar a taxa, por grupo (FIN-010). ABSORB é o padrão ao ativar. fee_snapshot guarda as tarifas do Asaas
-- da conta lidas na ativação ou na troca da política: o valor do repasse é calculado na criação da cobrança, dentro da
-- transação dos grupos, sem consulta de rede. A comissão vem sempre da tabela publicada no adm.
ALTER TABLE receivable_group_links
    ADD COLUMN fee_policy varchar(8) NOT NULL DEFAULT 'ABSORB' CHECK (fee_policy IN ('ABSORB', 'PASS')),
    ADD COLUMN fee_snapshot jsonb;
