-- O mesmo request_id pode voltar em respostas diferentes (o link do WhatsApp deriva o id do próprio código:
-- "Vou → Não vou → Vou" repete o id do primeiro "Vou"). O índice único da V29 impedia gravar o evento novo.
-- Agora a repetição só vale quando a resposta é a última da pessoa no jogo; a execução dupla é barrada pelo
-- lock do jogo (FOR UPDATE) somado à conferência dentro do lock. O índice segue para a busca ficar rápida.
DROP INDEX uq_attendance_events_promotion_request;

CREATE INDEX ix_attendance_events_request
    ON attendance_events (group_id, game_id, actor_user_id, request_id)
    WHERE request_id IS NOT NULL;

-- Ordem estável entre eventos do mesmo instante (occurred_at e o uuid não garantem a ordem de gravação).
ALTER TABLE attendance_events
    ADD COLUMN seq bigint GENERATED ALWAYS AS IDENTITY;

CREATE INDEX ix_attendance_events_member_seq
    ON attendance_events (game_id, member_user_id, guest_seq, seq);
