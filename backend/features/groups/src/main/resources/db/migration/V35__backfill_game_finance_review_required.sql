UPDATE games
SET finance_review_required = true
WHERE EXISTS (
    SELECT 1
    FROM group_charges c
    WHERE c.group_id = games.group_id
      AND c.game_id = games.id
      AND c.status IN ('PAID', 'WAIVED')
      AND c.review_required
);
