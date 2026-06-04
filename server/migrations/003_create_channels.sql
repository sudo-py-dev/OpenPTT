-- Channels table
CREATE TABLE IF NOT EXISTS channels (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    channel_type    VARCHAR(20) NOT NULL DEFAULT 'voice',
    max_users       INT NOT NULL DEFAULT 50,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(group_id, name)
);

CREATE INDEX IF NOT EXISTS idx_channels_group ON channels(group_id);
