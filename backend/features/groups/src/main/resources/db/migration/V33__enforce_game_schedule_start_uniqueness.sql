-- Existing installations may already contain overlapping active rows from the
-- pre-index race. Keep the earliest game (created_at, then id as a stable tie
-- breaker), preserve every row, and cancel only the later duplicates.
WITH ranked_games AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY group_id, starts_at
               ORDER BY created_at ASC, id ASC
           ) AS game_rank
    FROM games
    WHERE status <> 'CANCELLED'
)
UPDATE games AS game
SET status = 'CANCELLED',
    version = version + 1,
    updated_at = now()
FROM ranked_games AS ranked
WHERE game.id = ranked.id
  AND ranked.game_rank > 1;

CREATE UNIQUE INDEX uq_games_group_starts_at_active
    ON games (group_id, starts_at)
    WHERE status <> 'CANCELLED';
