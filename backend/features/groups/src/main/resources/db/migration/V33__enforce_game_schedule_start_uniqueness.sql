CREATE UNIQUE INDEX uq_games_group_starts_at_active
    ON games (group_id, starts_at)
    WHERE status <> 'CANCELLED';
