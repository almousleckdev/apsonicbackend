-- One-time links for choosing a password: sent when an account is created (invitation) and when someone asks to
-- reset a forgotten password. Only the SHA-256 hash of the token is stored, so a database leak does not expose
-- working links. A token is spent by setting used_at in a single UPDATE, which makes it single-use.
CREATE TABLE password_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    purpose    VARCHAR(20)  NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_tokens_user_id ON password_tokens (user_id);
CREATE INDEX idx_password_tokens_expires_at ON password_tokens (expires_at);
