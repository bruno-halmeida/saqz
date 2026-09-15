-- These links select a game; authentication and membership still authorize confirmation.
CREATE TABLE notification_attendance_links (
    message_id uuid PRIMARY KEY REFERENCES group_messages(id),
    code varchar(43) NOT NULL UNIQUE DEFAULT rtrim(translate(
        encode(uuid_send(gen_random_uuid()) || uuid_send(gen_random_uuid()), 'base64'), '+/', '-_'), '=')
);
