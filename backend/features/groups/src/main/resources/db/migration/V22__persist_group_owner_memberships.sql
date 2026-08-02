-- Groups created after V9 also need a persisted membership for their owner.
INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at)
SELECT groups.id, groups.owner_user_id, 'ADMIN', groups.created_at, groups.updated_at
FROM access_groups groups
WHERE NOT EXISTS (
    SELECT 1
    FROM group_memberships existing
    WHERE existing.group_id = groups.id
      AND existing.user_id = groups.owner_user_id
);

CREATE INDEX ix_group_memberships_user_id
    ON group_memberships (user_id);
