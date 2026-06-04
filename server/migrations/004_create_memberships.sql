-- Group memberships table
CREATE TABLE IF NOT EXISTS group_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id        UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_memberships_user ON group_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_memberships_group ON group_memberships(group_id);
