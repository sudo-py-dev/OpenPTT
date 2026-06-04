-- Groups table
CREATE TABLE IF NOT EXISTS groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    avatar_url      TEXT,
    owner_id        UUID REFERENCES users(id) ON DELETE SET NULL,
    is_public       BOOLEAN NOT NULL DEFAULT true,
    invite_code     VARCHAR(20) UNIQUE,
    max_members     INT NOT NULL DEFAULT 200,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_groups_owner ON groups(owner_id);
CREATE INDEX IF NOT EXISTS idx_groups_invite ON groups(invite_code);
