ALTER TABLE access_groups
    ADD COLUMN pix_key varchar(140),
    ADD COLUMN pix_label varchar(80);

-- Texto livre com trim: o Pix aceita CPF, CNPJ, e-mail, telefone e chave aleatória,
-- e a decisão de produto de 2026-08-03 é não validar formato.
ALTER TABLE access_groups
    ADD CONSTRAINT ck_access_groups_pix_key CHECK (
        pix_key IS NULL OR (
            pix_key = btrim(pix_key)
            AND char_length(pix_key) BETWEEN 2 AND 140
            AND pix_key !~ '[[:cntrl:]]'
        )
    ),
    ADD CONSTRAINT ck_access_groups_pix_label CHECK (
        pix_label IS NULL OR (
            pix_label = btrim(pix_label)
            AND char_length(pix_label) BETWEEN 2 AND 80
            AND pix_label !~ '[[:cntrl:]]'
        )
    );
