-- Existing installations may already contain overlapping active rows from the
-- pre-index race. Keep the earliest game (created_at, then id as a stable tie
-- breaker), preserve every row, and cancel only the later duplicates.
--
-- A migration has no request actor. The group owner is used as the historical
-- actor so the same charge audit semantics as GameMutation.CANCEL can be
-- retained while satisfying the actor/changer foreign keys.
CREATE TEMP TABLE v34_duplicate_games ON COMMIT DROP AS
SELECT id, group_id, owner_user_id
FROM (
    SELECT games.id,
           games.group_id,
           groups.owner_user_id,
           ROW_NUMBER() OVER (
               PARTITION BY games.group_id, games.starts_at
               ORDER BY games.created_at ASC, games.id ASC
           ) AS game_rank
    FROM games
    JOIN access_groups groups ON groups.id = games.group_id
    WHERE games.status <> 'CANCELLED'
) AS ranked_games
WHERE game_rank > 1;

-- GameMutation.CANCEL cancels pending charges and appends a PENDING ->
-- CANCELLED event, while preserving PAID/WAIVED charges and marking them for
-- finance review. Apply those effects before cancelling the duplicate game;
-- no charge or history row is deleted.
INSERT INTO group_charge_events (
    id,
    charge_id,
    group_id,
    actor_user_id,
    old_status,
    new_status,
    occurred_at
)
SELECT gen_random_uuid(),
       charges.id,
       charges.group_id,
       duplicates.owner_user_id,
       'PENDING',
       'CANCELLED',
       now()
FROM group_charges charges
JOIN v34_duplicate_games duplicates
  ON duplicates.group_id = charges.group_id
 AND duplicates.id = charges.game_id
WHERE charges.status = 'PENDING';

UPDATE group_charges AS charges
SET status = 'CANCELLED',
    changed_by_user_id = duplicates.owner_user_id,
    version = charges.version + 1,
    updated_at = now()
FROM v34_duplicate_games duplicates
WHERE duplicates.group_id = charges.group_id
  AND duplicates.id = charges.game_id
  AND charges.status = 'PENDING';

UPDATE group_charges AS charges
SET review_required = true,
    changed_by_user_id = duplicates.owner_user_id,
    version = charges.version + 1,
    updated_at = now()
FROM v34_duplicate_games duplicates
WHERE duplicates.group_id = charges.group_id
  AND duplicates.id = charges.game_id
  AND charges.status IN ('PAID', 'WAIVED')
  AND NOT charges.review_required;

UPDATE games AS game
SET status = 'CANCELLED',
    version = game.version + 1,
    updated_at = now()
FROM v34_duplicate_games duplicates
WHERE game.id = duplicates.id;

CREATE UNIQUE INDEX uq_games_group_starts_at_active
    ON games (group_id, starts_at)
    WHERE status <> 'CANCELLED';
