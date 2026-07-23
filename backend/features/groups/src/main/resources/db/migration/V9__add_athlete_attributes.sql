ALTER TABLE group_memberships
    ADD COLUMN position varchar(16),
    ADD COLUMN membership_type varchar(16) NOT NULL DEFAULT 'AVULSO',
    ADD COLUMN active boolean NOT NULL DEFAULT true,
    ADD CONSTRAINT ck_group_memberships_position
        CHECK (position IS NULL OR position IN ('LIBERO', 'PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR')),
    ADD CONSTRAINT ck_group_memberships_membership_type
        CHECK (membership_type IN ('MENSALISTA', 'AVULSO'));

-- Every group owner needs a persisted membership row so athlete attributes
-- have somewhere to live; GroupRole.resolve already prioritizes ownership,
-- so this backfill never changes the owner's effective role.
INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at)
SELECT groups.id, groups.owner_user_id, 'ADMIN', groups.created_at, groups.updated_at
FROM access_groups groups
WHERE NOT EXISTS (
    SELECT 1 FROM group_memberships existing
    WHERE existing.group_id = groups.id AND existing.user_id = groups.owner_user_id
);

-- Repoint off group_memberships so a hard DELETE of a membership (the
-- removal design) no longer fails on historical attendance rows.
ALTER TABLE game_attendance DROP CONSTRAINT fk_game_attendance_member;
ALTER TABLE game_attendance
    ADD CONSTRAINT fk_game_attendance_member FOREIGN KEY (member_user_id) REFERENCES access_users (id);

ALTER TABLE attendance_events DROP CONSTRAINT fk_attendance_events_member;
ALTER TABLE attendance_events
    ADD CONSTRAINT fk_attendance_events_member FOREIGN KEY (member_user_id) REFERENCES access_users (id);

ALTER TABLE game_attendance ADD COLUMN member_display_name varchar(80);
UPDATE game_attendance ga
SET member_display_name = au.display_name
FROM access_users au
WHERE au.id = ga.member_user_id;
ALTER TABLE game_attendance
    ALTER COLUMN member_display_name SET NOT NULL,
    ADD CONSTRAINT ck_game_attendance_member_display_name CHECK (
        member_display_name = btrim(member_display_name)
        AND char_length(member_display_name) BETWEEN 2 AND 80
        AND member_display_name !~ '[[:cntrl:]]'
    );

ALTER TABLE group_charges ADD COLUMN member_display_name varchar(80);
UPDATE group_charges gc
SET member_display_name = au.display_name
FROM access_users au
WHERE au.id = gc.member_user_id;
ALTER TABLE group_charges
    ALTER COLUMN member_display_name SET NOT NULL,
    ADD CONSTRAINT ck_group_charges_member_display_name CHECK (
        member_display_name = btrim(member_display_name)
        AND char_length(member_display_name) BETWEEN 2 AND 80
        AND member_display_name !~ '[[:cntrl:]]'
    );
