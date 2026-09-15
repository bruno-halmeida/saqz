CREATE TABLE notification_push_deliveries (
    notification_id bigint NOT NULL REFERENCES group_notifications(sequence),
    installation_id uuid NOT NULL,
    PRIMARY KEY (notification_id, installation_id)
);
