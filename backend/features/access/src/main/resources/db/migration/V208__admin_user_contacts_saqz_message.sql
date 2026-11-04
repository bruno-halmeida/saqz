-- DEB-164: mensagem enviada pelo número do Saqz a partir do adm fica anotada sozinha em Contatos, com o resultado
-- SAQZ_MESSAGE. Só o servidor grava esse resultado: o registro manual continua com os seis de antes.
-- request_id é o requestId do envio, para o mesmo envio confirmado de novo não anotar duas vezes. Contato manual fica sem ele.
ALTER TABLE admin_user_contacts DROP CONSTRAINT ck_admin_user_contacts_result;
ALTER TABLE admin_user_contacts ADD CONSTRAINT ck_admin_user_contacts_result CHECK (
    result IN ('NO_ANSWER', 'WILL_SUBSCRIBE', 'TOO_EXPENSIVE', 'DID_NOT_USE', 'OTHER', 'DO_NOT_CONTACT', 'SAQZ_MESSAGE')
);

ALTER TABLE admin_user_contacts ADD COLUMN request_id uuid;
ALTER TABLE admin_user_contacts ADD CONSTRAINT uq_admin_user_contacts_request UNIQUE (request_id);
