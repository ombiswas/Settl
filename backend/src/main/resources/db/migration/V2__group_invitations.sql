-- Group Invitations Table for inviting unregistered email addresses
CREATE TABLE group_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    invited_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    is_admin BOOLEAN DEFAULT false NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Indexes for lightning-fast token validation and group/email lookups
CREATE INDEX idx_group_invitations_token_hash ON group_invitations(token_hash);
CREATE INDEX idx_group_invitations_email_status ON group_invitations(email, status);
CREATE INDEX idx_group_invitations_group_status ON group_invitations(group_id, status);
