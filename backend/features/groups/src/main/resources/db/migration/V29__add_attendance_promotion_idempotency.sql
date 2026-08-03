ALTER TABLE attendance_events
    ADD COLUMN request_id uuid;

CREATE UNIQUE INDEX uq_attendance_events_promotion_request
    ON attendance_events (group_id, game_id, actor_user_id, request_id)
    WHERE request_id IS NOT NULL;
